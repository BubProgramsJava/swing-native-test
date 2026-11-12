package se.hirt.bubb.worm;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Discovers LAN hosts over multicast and carries an authoritative match over TCP. */
final class LanMultiplayerService implements AutoCloseable {
    private static final String DISCOVERY_PREFIX = "3D-WORM|1|";
    private static final String MULTICAST_ADDRESS = "239.255.42.99";
    private static final int DISCOVERY_PORT = 47_821;
    private static final int MAX_WORM_LENGTH = 400;
    private static final int MAX_PILLS = 100;

    private static final byte JOIN = 1;
    private static final byte LOBBY = 2;
    private static final byte READY = 3;
    private static final byte COUNTDOWN = 4;
    private static final byte START = 5;
    private static final byte INPUT = 6;
    private static final byte GAME_STATE = 7;
    private static final byte MATCH_FINISHED = 8;

    private final Listener listener;
    private final Object outputLock = new Object();
    private final Map<String, SeenSession> sessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService networkScheduler = newScheduler("worm-lan-tcp");

    private volatile boolean closed;
    private volatile boolean host;
    private volatile String sessionId;
    private volatile String hostName;
    private volatile String guestName;
    private volatile boolean hostReady;
    private volatile boolean guestReady;
    private volatile boolean countdownStarted;

    private MulticastSocket discoverySocket;
    private ScheduledExecutorService discoveryScheduler;
    private ServerSocket serverSocket;
    private Socket connection;
    private DataInputStream input;
    private DataOutputStream output;

    LanMultiplayerService(Listener listener) {
        this.listener = listener;
    }

    void startDiscovery() {
        if (discoverySocket != null || closed) return;
        try {
            InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
            discoverySocket = new MulticastSocket(null);
            discoverySocket.setReuseAddress(true);
            discoverySocket.bind(new InetSocketAddress(DISCOVERY_PORT));
            discoverySocket.joinGroup(group);
            discoveryScheduler = newScheduler("worm-lan-discovery");
            discoveryScheduler.scheduleAtFixedRate(this::expireSessions, 1, 1, TimeUnit.SECONDS);
            Thread receiver = new Thread(this::receiveAnnouncements, "worm-lan-discovery-listener");
            receiver.setDaemon(true);
            receiver.start();
        } catch (IOException exception) {
            reportError("LAN discovery could not start: " + exception.getMessage());
            stopDiscovery();
        }
    }

    void hostGame(String playerName) {
        if (closed) return;
        try {
            startDiscovery();
            host = true;
            hostName = playerName;
            sessionId = UUID.randomUUID().toString();
            serverSocket = new ServerSocket(0);
            advertise();
            if (discoveryScheduler != null) {
                discoveryScheduler.scheduleAtFixedRate(this::advertise, 2, 2, TimeUnit.SECONDS);
            }
            Thread acceptor = new Thread(this::acceptGuest, "worm-lan-host");
            acceptor.setDaemon(true);
            acceptor.start();
            listener.onStatus("Waiting for another player to join…");
        } catch (IOException exception) {
            reportError("Could not host a LAN game: " + exception.getMessage());
        }
    }

    void join(LanSession session, String playerName) {
        if (closed) return;
        stopDiscovery();
        Thread connector = new Thread(() -> connectToHost(session, playerName), "worm-lan-join");
        connector.setDaemon(true);
        connector.start();
    }

    void confirmReady() {
        if (connection == null || !connection.isConnected()) return;
        if (host) {
            hostReady = true;
            publishLobby();
            maybeStartCountdown();
        } else {
            try {
                sendMessage(READY, stream -> stream.writeBoolean(true));
                listener.onLobby(hostName, guestName, hostReady, true);
            } catch (IOException exception) {
                reportError("Could not confirm readiness: " + exception.getMessage());
            }
        }
    }

    void sendInput(Cell direction) {
        if (host || connection == null || !connection.isConnected()) return;
        try {
            sendMessage(INPUT, stream -> {
                stream.writeInt(direction.x);
                stream.writeInt(direction.z);
            });
        } catch (IOException exception) {
            reportError("Could not send movement: " + exception.getMessage());
        }
    }

    void sendGameState(LanGameState state) {
        if (!host || connection == null || !connection.isConnected()) return;
        try {
            sendMessage(GAME_STATE, stream -> writeGameState(stream, state));
        } catch (IOException exception) {
            reportError("Could not send the match state: " + exception.getMessage());
        }
    }

    void sendMatchFinished(LanMatchResult result) {
        if (!host || connection == null || !connection.isConnected()) return;
        try {
            sendMessage(MATCH_FINISHED, stream -> {
                stream.writeUTF(result.winnerName());
                stream.writeInt(result.winnerScore());
                stream.writeInt(result.hostScore());
                stream.writeInt(result.guestScore());
            });
        } catch (IOException exception) {
            reportError("Could not send the final result: " + exception.getMessage());
        }
    }

    private void acceptGuest() {
        try {
            Socket accepted = serverSocket.accept();
            connection = accepted;
            prepareStreams(accepted);
            int message = input.readUnsignedByte();
            if (message != JOIN) throw new IOException("Invalid join request.");
            guestName = input.readUTF();
            stopAdvertising();
            publishLobby();
            listener.onStatus(guestName + " joined. Both players must confirm to start.");
            readMessages();
        } catch (IOException exception) {
            if (!closed) reportError("LAN connection failed: " + exception.getMessage());
        }
    }

    private void connectToHost(LanSession session, String playerName) {
        try {
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(session.address(), session.port()), 5_000);
            connection = socket;
            prepareStreams(socket);
            host = false;
            sessionId = session.id();
            guestName = playerName;
            sendMessage(JOIN, stream -> stream.writeUTF(playerName));
            listener.onStatus("Connected. Waiting for the host to confirm.");
            readMessages();
        } catch (IOException exception) {
            if (!closed) reportError("Could not join " + session.playerName() + ": " + exception.getMessage());
        }
    }

    private void prepareStreams(Socket socket) throws IOException {
        socket.setTcpNoDelay(true);
        input = new DataInputStream(socket.getInputStream());
        output = new DataOutputStream(socket.getOutputStream());
    }

    private void readMessages() throws IOException {
        while (!closed && connection != null && !connection.isClosed()) {
            byte type = input.readByte();
            switch (type) {
                case LOBBY -> readLobby(input);
                case READY -> readGuestReady(input);
                case COUNTDOWN -> listener.onCountdown(input.readInt());
                case START -> listener.onGameStarted();
                case INPUT -> readInput(input);
                case GAME_STATE -> listener.onGameState(readGameState(input));
                case MATCH_FINISHED -> listener.onMatchFinished(new LanMatchResult(
                        input.readUTF(), input.readInt(), input.readInt(), input.readInt()));
                default -> throw new IOException("Unknown LAN message: " + type);
            }
        }
    }

    private void readLobby(DataInputStream stream) throws IOException {
        hostName = stream.readUTF();
        guestName = stream.readUTF();
        hostReady = stream.readBoolean();
        guestReady = stream.readBoolean();
        listener.onLobby(hostName, guestName, hostReady, guestReady);
    }

    private void readGuestReady(DataInputStream stream) throws IOException {
        if (!host) throw new IOException("Only the host can receive readiness updates.");
        guestReady = stream.readBoolean();
        publishLobby();
        maybeStartCountdown();
    }

    private void readInput(DataInputStream stream) throws IOException {
        if (!host) throw new IOException("Only the host can receive movement input.");
        int x = stream.readInt();
        int z = stream.readInt();
        if (Math.abs(x) + Math.abs(z) == 1) listener.onGuestInput(new Cell(x, z));
    }

    private void publishLobby() {
        listener.onLobby(hostName, guestName, hostReady, guestReady);
        if (!host || connection == null || !connection.isConnected()) return;
        try {
            sendMessage(LOBBY, stream -> {
                stream.writeUTF(hostName);
                stream.writeUTF(guestName == null ? "" : guestName);
                stream.writeBoolean(hostReady);
                stream.writeBoolean(guestReady);
            });
        } catch (IOException exception) {
            reportError("Could not update the LAN lobby: " + exception.getMessage());
        }
    }

    private void maybeStartCountdown() {
        if (!host || countdownStarted || !hostReady || !guestReady || connection == null) return;
        countdownStarted = true;
        for (int seconds = 5; seconds >= 1; seconds--) {
            int remaining = seconds;
            networkScheduler.schedule(() -> {
                sendQuietly(COUNTDOWN, stream -> stream.writeInt(remaining));
                listener.onCountdown(remaining);
            }, 5 - seconds, TimeUnit.SECONDS);
        }
        networkScheduler.schedule(() -> {
            sendQuietly(START, stream -> { });
            listener.onGameStarted();
        }, 5, TimeUnit.SECONDS);
    }

    private void receiveAnnouncements() {
        byte[] buffer = new byte[512];
        while (!closed && discoverySocket != null && !discoverySocket.isClosed()) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            try {
                discoverySocket.receive(packet);
                String message = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
                if (!message.startsWith(DISCOVERY_PREFIX)) continue;
                String[] parts = message.split("\\|", 5);
                if (parts.length != 5) continue;
                String id = parts[2];
                if (id.equals(sessionId)) continue;
                int port = Integer.parseInt(parts[3]);
                String playerName = new String(Base64.getUrlDecoder().decode(parts[4]), StandardCharsets.UTF_8);
                sessions.put(id, new SeenSession(new LanSession(id, playerName, packet.getAddress(), port), System.currentTimeMillis()));
                publishSessions();
            } catch (SocketException exception) {
                if (!closed && discoverySocket != null) {
                    reportError("LAN discovery stopped: " + exception.getMessage());
                }
                return;
            } catch (IOException | IllegalArgumentException exception) {
                // Ignore malformed or transient discovery packets.
            }
        }
    }

    private void advertise() {
        MulticastSocket socket = discoverySocket;
        ServerSocket server = serverSocket;
        if (closed || socket == null || socket.isClosed() || !host || server == null || server.isClosed()) return;
        String encodedName = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(hostName.getBytes(StandardCharsets.UTF_8));
        String message = DISCOVERY_PREFIX + sessionId + "|" + server.getLocalPort() + "|" + encodedName;
        byte[] data = message.getBytes(StandardCharsets.UTF_8);
        try {
            InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
            socket.send(new DatagramPacket(data, data.length, group, DISCOVERY_PORT));
        } catch (IOException exception) {
            if (!closed) reportError("Could not advertise the LAN game: " + exception.getMessage());
        }
    }

    private void expireSessions() {
        long cutoff = System.currentTimeMillis() - 6_000;
        sessions.entrySet().removeIf(entry -> entry.getValue().seenAtMillis < cutoff);
        publishSessions();
    }

    private void publishSessions() {
        List<LanSession> visible = sessions.values().stream()
                .map(SeenSession::session)
                .sorted(Comparator.comparing(LanSession::playerName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        listener.onDiscovered(visible);
    }

    private void sendMessage(byte type, StreamWriter writer) throws IOException {
        synchronized (outputLock) {
            if (output == null) throw new IOException("Not connected.");
            output.writeByte(type);
            writer.write(output);
            output.flush();
        }
    }

    private void sendQuietly(byte type, StreamWriter writer) {
        try {
            sendMessage(type, writer);
        } catch (IOException exception) {
            if (!closed) reportError("LAN connection lost: " + exception.getMessage());
        }
    }

    private static void writeGameState(DataOutputStream stream, LanGameState state) throws IOException {
        writeCells(stream, state.hostWorm());
        writeCells(stream, state.guestWorm());
        stream.writeInt(state.hostScore());
        stream.writeInt(state.guestScore());
        stream.writeInt(state.pills().size());
        for (LanGameState.ScoredCell pill : state.pills()) {
            stream.writeInt(pill.cell().x);
            stream.writeInt(pill.cell().z);
            stream.writeInt(pill.points());
        }
    }

    private static LanGameState readGameState(DataInputStream stream) throws IOException {
        List<Cell> hostWorm = readCells(stream);
        List<Cell> guestWorm = readCells(stream);
        int hostScore = stream.readInt();
        int guestScore = stream.readInt();
        int pillCount = stream.readInt();
        if (pillCount < 0 || pillCount > MAX_PILLS) throw new IOException("Invalid pill count.");
        List<LanGameState.ScoredCell> pills = new ArrayList<>(pillCount);
        for (int index = 0; index < pillCount; index++) {
            pills.add(new LanGameState.ScoredCell(new Cell(stream.readInt(), stream.readInt()), stream.readInt()));
        }
        return new LanGameState(hostWorm, guestWorm, hostScore, guestScore, pills);
    }

    private static void writeCells(DataOutputStream stream, List<Cell> cells) throws IOException {
        stream.writeInt(cells.size());
        for (Cell cell : cells) {
            stream.writeInt(cell.x);
            stream.writeInt(cell.z);
        }
    }

    private static List<Cell> readCells(DataInputStream stream) throws IOException {
        int size = stream.readInt();
        if (size < 0 || size > MAX_WORM_LENGTH) throw new IOException("Invalid worm length.");
        List<Cell> cells = new ArrayList<>(size);
        for (int index = 0; index < size; index++) cells.add(new Cell(stream.readInt(), stream.readInt()));
        return cells;
    }

    private void stopAdvertising() {
        if (discoveryScheduler != null) {
            // Discovery remains active during the lobby, but the session is no longer public.
            sessions.remove(sessionId);
        }
        ServerSocket server = serverSocket;
        if (server != null && !server.isClosed()) {
            try {
                server.close();
            } catch (IOException ignored) { }
        }
    }

    private void stopDiscovery() {
        MulticastSocket socket = discoverySocket;
        discoverySocket = null;
        if (socket != null && !socket.isClosed()) {
            try {
                socket.leaveGroup(InetAddress.getByName(MULTICAST_ADDRESS));
            } catch (IOException ignored) { }
            socket.close();
        }
        ScheduledExecutorService scheduler = discoveryScheduler;
        discoveryScheduler = null;
        if (scheduler != null) scheduler.shutdownNow();
        sessions.clear();
    }

    private void reportError(String message) {
        listener.onError(message);
    }

    private static ScheduledExecutorService newScheduler(String name) {
        return Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, name);
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        stopDiscovery();
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) { }
        }
        if (connection != null) {
            try {
                connection.close();
            } catch (IOException ignored) { }
        }
        networkScheduler.shutdownNow();
    }

    interface Listener {
        void onDiscovered(List<LanSession> sessions);
        void onStatus(String message);
        void onLobby(String hostName, String guestName, boolean hostReady, boolean guestReady);
        void onCountdown(int seconds);
        void onGameStarted();
        void onGuestInput(Cell direction);
        void onGameState(LanGameState state);
        void onMatchFinished(LanMatchResult result);
        void onError(String message);
    }

    @FunctionalInterface
    private interface StreamWriter {
        void write(DataOutputStream stream) throws IOException;
    }

    private record SeenSession(LanSession session, long seenAtMillis) { }
}
