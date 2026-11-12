package se.hirt.bubb.worm;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.PerspectiveCamera;
import javafx.scene.Scene;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SubScene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Sphere;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.transform.Rotate;
import javafx.stage.Stage;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** JavaFX entry point and coordinator for the game, menus, and saved player state. */
public class Main extends Application {
    private static final int BOARD_SIZE = 19;
    private static final int PILL_COUNT = 6;
    private static final double STEP_SECONDS = 0.17;
    private static final Random RANDOM = new Random();
    private static final long NAME_CHANGE_COOLDOWN_MILLIS = 10L * 60 * 1000;
    private final GameWorldView worldView = new GameWorldView();
    private final Deque<Cell> worm = new ArrayDeque<>();
    private final Deque<Cell> aiWorm = new ArrayDeque<>();
    private final List<Pill> pills = new ArrayList<>();
    private final List<HighScore> highScores = new ArrayList<>();
    private final List<HighScore> lanHighScores = new ArrayList<>();
    private final DuelAI duelAI = new DuelAI();
    private final GameDataStore dataStore = new GameDataStore();
    private final Set<String> ownedHats = new HashSet<>();
    private final Set<String> ownedColors = new HashSet<>();
    private Properties nameRegistry = new Properties();
    private PerspectiveCamera camera;
    private SubScene gameView;
    private final Rotate cameraYawTransform = new Rotate(0, Rotate.Y_AXIS);
    private final Rotate cameraPitchTransform = new Rotate(-27, Rotate.X_AXIS);
    private Stage stage;
    private Cell direction = new Cell(1, 0);
    private Cell queuedDirection = direction;
    private Cell previousDirection = direction;
    private List<Cell> previousWorm = new ArrayList<>();
    private boolean running;
    private boolean gameOver;
    private boolean duelPlayerCrashed;
    private boolean duelAiCrashed;
    private boolean menuOpen;
    private boolean resumeAfterMenu;
    private int score;
    private int aiScore;
    private GameMode gameMode = GameMode.SINGLE;
    private Difficulty difficulty = Difficulty.NORMAL;
    private int pillWallet;
    private String lastPlayerName = "";
    private String profileId = UUID.randomUUID().toString();
    private long lastNameChangeMillis;
    private String selectedHat = "none";
    private String selectedColor = "mint";
    private String shopMessage = "";
    private String settingsMessage = "";
    private GameAction remappingAction;
    private LanMultiplayerService lanService;
    private List<LanSession> lanSessions = new ArrayList<>();
    private VBox lanSessionList;
    private Label lanStatusLabel;
    private Button lanReadyButton;
    private boolean lanScreenOpen;
    private boolean lanLobbyOpen;
    private boolean lanHost;
    private boolean lanLocalReady;
    private boolean lanRemoteReady;
    private String lanLocalName = "";
    private String lanRemoteName = "";
    private String lanStatus = "";
    private LanMatchResult lastLanResult;
    private Cell remoteDirection = new Cell(-1, 0);
    private Cell queuedRemoteDirection = remoteDirection;
    private int lanCountdown;
    private KeyCode keyAway = KeyCode.W;
    private KeyCode keyToward = KeyCode.S;
    private KeyCode keyLeft = KeyCode.A;
    private KeyCode keyRight = KeyCode.D;
    private double elapsed;
    private double aiElapsed;
    private long lastFrame;
    private Text scoreText;
    private Text walletText;
    private Text controlsText;
    private Text highScoreText;
    private Text messageText;
    private StackPane menuLayer;
    private StackPane mainMenuLayer;
    private VBox menuPanel;
    private Canvas menuSnakeCanvas;
    private TextField playerNameField;
    private Label nameStatusLabel;
    private Button claimNameButton;
    private Button playButton;
    private long nextNameRefresh;
    private double cameraDistance = 30;
    private double orbitZoom = 1;
    private double cameraYaw;
    private double cameraPitch = 27;
    private double lastCameraMouseX;
    private double lastCameraMouseY;
    private boolean orbitCamera;
    private boolean orbitDragging;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        worldView.buildBoard(BOARD_SIZE);
        setupWorm();
        loadHighScores();
        loadLanHighScores();
        loadProfile();
        loadNameRegistry();

        camera = new PerspectiveCamera(true);
        camera.setNearClip(0.1);
        camera.setFarClip(200);
        camera.setFieldOfView(36);
        camera.getTransforms().setAll(cameraPitchTransform, cameraYawTransform);
        camera.setTranslateX(0);
        camera.setTranslateY(-17);
        camera.setTranslateZ(-30);

        gameView = new SubScene(worldView.root(), 900, 650, true, SceneAntialiasing.BALANCED);
        gameView.setFill(Color.web("#10172a"));
        gameView.setCamera(camera);
        gameView.widthProperty().addListener((obs, oldValue, newValue) -> fitCamera(camera, gameView));
        gameView.heightProperty().addListener((obs, oldValue, newValue) -> fitCamera(camera, gameView));
        gameView.setOnScroll(event -> {
            if (!orbitCamera) return;
            orbitZoom = clamp(orbitZoom * Math.exp(-event.getDeltaY() * 0.0015), 0.5, 3.5);
            fitCamera(camera, gameView);
            event.consume();
        });
        gameView.setOnMousePressed(event -> {
            if (orbitCamera && event.getButton() == MouseButton.SECONDARY) {
                orbitDragging = true;
                lastCameraMouseX = event.getSceneX();
                lastCameraMouseY = event.getSceneY();
                event.consume();
            }
        });
        gameView.setOnMouseReleased(event -> {
            if (event.getButton() == MouseButton.SECONDARY) orbitDragging = false;
        });
        gameView.setOnMouseDragged(event -> {
            if (!orbitCamera || !orbitDragging || !event.isSecondaryButtonDown()) return;
            cameraYaw += (event.getSceneX() - lastCameraMouseX) * 0.35;
            cameraPitch = clamp(cameraPitch + (event.getSceneY() - lastCameraMouseY) * 0.25, 8, 82);
            lastCameraMouseX = event.getSceneX();
            lastCameraMouseY = event.getSceneY();
            fitCamera(camera, gameView);
            event.consume();
        });

        scoreText = new Text("SCORE  0");
        scoreText.setFill(Color.WHITE);
        scoreText.setFont(Font.font("System", FontWeight.BOLD, 18));
        walletText = new Text("PILLS  " + pillWallet);
        walletText.setFill(Color.web("#ffe49a"));
        walletText.setFont(Font.font("System", FontWeight.BOLD, 14));
        controlsText = new Text();
        controlsText.setFill(Color.web("#aab6d2"));
        controlsText.setFont(Font.font("System", 12));
        updateControlsHint();
        BorderPane hud = new BorderPane();
        hud.setTop(new VBox(6, scoreText, walletText));
        hud.setBottom(controlsText);
        highScoreText = new Text();
        highScoreText.setFill(Color.web("#d3def7"));
        highScoreText.setFont(Font.font("System", 13));
        hud.setRight(highScoreText);
        BorderPane.setAlignment(scoreText, Pos.TOP_LEFT);
        BorderPane.setAlignment(controlsText, Pos.BOTTOM_CENTER);
        hud.setStyle("-fx-padding: 22px 26px 18px 26px;");
        hud.setMouseTransparent(true);

        messageText = new Text();
        messageText.setFill(Color.WHITE);
        messageText.setFont(Font.font("System", FontWeight.BOLD, 30));
        messageText.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        StackPane overlay = new StackPane(messageText);
        overlay.setMouseTransparent(true);
        menuLayer = new StackPane();
        menuLayer.setVisible(false);
        mainMenuLayer = new StackPane();
        StackPane root = new StackPane(gameView, hud, overlay, menuLayer, mainMenuLayer);
        gameView.widthProperty().bind(root.widthProperty());
        gameView.heightProperty().bind(root.heightProperty());
        controlsText.wrappingWidthProperty().bind(root.widthProperty().subtract(52));
        updateHighScoreDisplay();
        spawnPills();
        showMainMenu();
        Scene scene = new Scene(root, 900, 650, true);
        scene.setOnKeyPressed(event -> handleKey(event.getCode()));

        stage.setTitle("3D Worm");
        stage.setMinWidth(620);
        stage.setMinHeight(500);
        stage.setResizable(true);
        stage.setFullScreenExitHint("The Main Menu is full screen");
        stage.setFullScreenExitKeyCombination(KeyCombination.NO_MATCH);
        stage.setScene(scene);
        stage.show();
        stage.setFullScreen(true);
        stage.setOnCloseRequest(event -> {
            if (lanService != null) lanService.close();
        });
        fitCamera(camera, gameView);
        startGameLoop();
    }

    private void setupWorm() {
        worm.clear();
        worm.addLast(new Cell(-2, 0));
        worm.addLast(new Cell(-3, 0));
        worm.addLast(new Cell(-4, 0));
        direction = new Cell(1, 0);
        queuedDirection = direction;
        previousDirection = direction;
        previousWorm = new ArrayList<>(worm);
        worldView.resetPlayer(List.copyOf(worm), selectedColor, selectedHat);
        positionWorm(1);
    }

    private void setupAiWorm() {
        clearAiWorm();
        aiWorm.addLast(new Cell(3, 0));
        aiWorm.addLast(new Cell(4, 0));
        aiWorm.addLast(new Cell(5, 0));
        worldView.resetAi(List.copyOf(aiWorm));
    }

    private void clearAiWorm() {
        aiWorm.clear();
        worldView.clearAi();
    }

    private void positionAiWorm() {
        worldView.renderAi(List.copyOf(aiWorm), worm.peekFirst());
    }

    private void beginLanMatch() {
        if (gameMode != GameMode.LAN) return;
        stage.setFullScreen(false);
        mainMenuLayer.setVisible(false);
        menuLayer.setVisible(false);
        lanCountdown = 0;
        clearPills();
        worldView.clearPlayer();
        clearAiWorm();
        worm.clear();
        score = 0;
        aiScore = 0;
        elapsed = 0;
        gameOver = false;
        running = true;

        if (lanHost) {
            worm.addAll(List.of(new Cell(-2, 0),
                    new Cell(-3, 0), new Cell(-4, 0)));
            aiWorm.addAll(List.of(new Cell(3, 0),
                    new Cell(4, 0), new Cell(5, 0)));
            direction = new Cell(1, 0);
            remoteDirection = new Cell(-1, 0);
        } else {
            worm.addAll(List.of(new Cell(3, 0),
                    new Cell(4, 0), new Cell(5, 0)));
            aiWorm.addAll(List.of(new Cell(-2, 0),
                    new Cell(-3, 0), new Cell(-4, 0)));
            direction = new Cell(-1, 0);
            remoteDirection = new Cell(1, 0);
        }
        queuedDirection = direction;
        previousDirection = direction;
        queuedRemoteDirection = remoteDirection;
        previousWorm = new ArrayList<>(worm);
        worldView.resetPlayer(List.copyOf(worm), selectedColor, selectedHat);
        worldView.resetAi(List.copyOf(aiWorm));
        positionWorm(1);
        positionAiWorm();
        updateScoreDisplay();
        updateHighScoreDisplay();
        messageText.setText("");

        if (lanHost) {
            spawnPills();
            broadcastLanState();
        }
    }

    private void queueGuestDirection(Cell requested) {
        if (!lanHost || gameMode != GameMode.LAN) return;
        if (requested.x == -remoteDirection.x && requested.z == -remoteDirection.z) return;
        queuedRemoteDirection = requested;
    }

    /** Checks both LAN moves together so update order cannot decide who loses a head-on crash. */
    private boolean finishLanCollisionIfNeeded() {
        if (gameMode != GameMode.LAN || !lanHost || gameOver
                || worm.isEmpty() || aiWorm.isEmpty()) return false;

        Cell playerNext = nextCell(worm.peekFirst(), queuedDirection);
        Cell guestNext = nextCell(aiWorm.peekFirst(), queuedRemoteDirection);
        int edge = BOARD_SIZE / 2;
        boolean playerCrashed = outsideBoard(playerNext, edge) || worm.contains(playerNext)
                || aiWorm.contains(playerNext);
        boolean guestCrashed = outsideBoard(guestNext, edge) || aiWorm.contains(guestNext)
                || worm.contains(guestNext);

        // Both worms moving into the same empty cell is a simultaneous head collision.
        if (playerNext.equals(guestNext)) {
            playerCrashed = true;
            guestCrashed = true;
        }
        if (!playerCrashed && !guestCrashed) return false;

        finishLanCrash(playerCrashed, guestCrashed);
        return true;
    }

    private Cell nextCell(Cell head, Cell travelDirection) {
        return new Cell(head.x + travelDirection.x, head.z + travelDirection.z);
    }

    private boolean outsideBoard(Cell cell, int edge) {
        return Math.abs(cell.x) > edge || Math.abs(cell.z) > edge;
    }

    private void moveGuestWorm() {
        if (aiWorm.isEmpty() || gameOver) return;
        remoteDirection = queuedRemoteDirection;
        Cell head = aiWorm.peekFirst();
        Cell next = new Cell(
                head.x + remoteDirection.x, head.z + remoteDirection.z);
        int edge = BOARD_SIZE / 2;
        if (Math.abs(next.x) > edge || Math.abs(next.z) > edge) {
            finishLanCrash(false, true);
            return;
        }

        Pill eaten = pills.stream()
                .filter(pill -> pill.cell.equals(next))
                .findFirst()
                .orElse(null);
        for (Cell segment : aiWorm) {
            if (segment.equals(next)) {
                finishLanCrash(false, true);
                return;
            }
        }

        aiWorm.addFirst(next);
        if (eaten == null) {
            aiWorm.removeLast();
        } else {
            pills.remove(eaten);
            worldView.removeObject(eaten.sphere);
            aiScore += eaten.points;
            spawnPills();
        }
        positionAiWorm();
    }

    private void broadcastLanState() {
        if (!lanHost || lanService == null || gameMode != GameMode.LAN) return;
        List<LanGameState.ScoredCell> scoredPills = pills.stream()
                .map(pill -> new LanGameState.ScoredCell(pill.cell, pill.points))
                .toList();
        lanService.sendGameState(new LanGameState(
                List.copyOf(worm), List.copyOf(aiWorm), score, aiScore, scoredPills));
    }

    private void applyLanGameState(LanGameState state) {
        if (lanHost || gameMode != GameMode.LAN || gameOver) return;

        List<Cell> oldWorm = new ArrayList<>(worm);
        Cell oldHead = worm.peekFirst();
        previousWorm = oldWorm;
        worm.clear();
        worm.addAll(state.guestWorm());
        aiWorm.clear();
        aiWorm.addAll(state.hostWorm());

        if (!worm.isEmpty() && oldHead != null) {
            Cell newHead = worm.peekFirst();
            int deltaX = newHead.x - oldHead.x;
            int deltaZ = newHead.z - oldHead.z;
            if (Math.abs(deltaX) + Math.abs(deltaZ) == 1) {
                previousDirection = direction;
                direction = new Cell(deltaX, deltaZ);
            }
        }
        if (previousWorm.size() + 1 == worm.size() && !previousWorm.isEmpty()) {
            previousWorm.add(previousWorm.getLast());
        }
        queuedDirection = direction;
        score = state.guestScore();
        aiScore = state.hostScore();
        elapsed = 0;
        reconcileLanPills(state.pills());
        positionAiWorm();
        updateScoreDisplay();
    }

    private void reconcileLanPills(List<LanGameState.ScoredCell> incoming) {
        List<Pill> unmatched = new ArrayList<>(pills);
        pills.clear();
        for (LanGameState.ScoredCell item : incoming) {
            Pill existing = unmatched.stream()
                    .filter(pill -> pill.cell.equals(item.cell()) && pill.points == item.points())
                    .findFirst()
                    .orElse(null);
            if (existing != null) {
                unmatched.remove(existing);
                pills.add(existing);
            } else {
                Sphere sphere = createPillSphere(item.cell(), item.points());
                worldView.addObject(sphere);
                pills.add(new Pill(item.cell(), item.points(), sphere));
            }
        }
        for (Pill obsolete : unmatched) worldView.removeObject(obsolete.sphere);
    }

    private void finishLanCrash(boolean localCrashed, boolean remoteCrashed) {
        if (!lanHost || gameOver) return;
        String winnerName = localCrashed == remoteCrashed
                ? ""
                : localCrashed ? lanRemoteName : lanLocalName;
        int winnerScore = localCrashed == remoteCrashed
                ? 0
                : localCrashed ? aiScore : score;
        finishLanMatch(winnerName, winnerScore);
    }

    private void finishLanByScore() {
        String winnerName = score == aiScore ? "" : score > aiScore ? lanLocalName : lanRemoteName;
        int winnerScore = winnerName.isBlank() ? 0 : Math.max(score, aiScore);
        finishLanMatch(winnerName, winnerScore);
    }

    private void finishLanMatch(String winnerName, int winnerScore) {
        if (!lanHost || gameOver) return;
        gameOver = true;
        running = false;
        LanMatchResult result = new LanMatchResult(
                winnerName, winnerScore, score, aiScore);
        lastLanResult = result;
        broadcastLanState();
        lanService.sendMatchFinished(result);
        recordLanWinner(result);
        messageText.setText(lanResultText(result));
    }

    private void acceptLanMatchResult(LanMatchResult result) {
        if (lanHost || gameOver) return;
        score = result.guestScore();
        aiScore = result.hostScore();
        gameOver = true;
        running = false;
        lastLanResult = result;
        recordLanWinner(result);
        messageText.setText(lanResultText(result));
    }

    private void recordLanWinner(LanMatchResult result) {
        if (!result.isTie() && !result.winnerName().isBlank()) {
            lanHighScores.add(new HighScore(result.winnerName(), result.winnerScore()));
            saveLanHighScores();
        }
        updateHighScoreDisplay();
    }

    private String lanResultText(LanMatchResult result) {
        if (result.isTie()) return "LAN MATCH DRAW\n" + result.hostScore() + " – " + result.guestScore()
                + "\nPress ENTER to return to menu";
        return result.winnerName() + " WINS!\n" + result.hostScore() + " – " + result.guestScore()
                + "\nPress ENTER to return to menu";
    }

    private void positionWorm(double progress) {
        Cell target = nearestPillCell(worm.peekFirst());
        worldView.renderPlayer(List.copyOf(worm), previousWorm, direction, previousDirection, progress, target);
    }

    private Cell nearestPillCell(Cell from) {
        return pills.stream()
                .min(Comparator.comparingInt(pill -> Math.abs(pill.cell.x - from.x) + Math.abs(pill.cell.z - from.z)))
                .map(pill -> new Cell((int) Math.signum(pill.cell.x - from.x), (int) Math.signum(pill.cell.z - from.z)))
                .orElse(from);
    }

    private void spawnPills() {
        List<Cell> empty = new ArrayList<>();
        int edge = BOARD_SIZE / 2;
        for (int x = -edge; x <= edge; x++) {
            for (int z = -edge; z <= edge; z++) {
                Cell cell = new Cell(x, z);
                if (!worm.contains(cell) && !aiWorm.contains(cell)
                        && pills.stream().noneMatch(pill -> pill.cell.equals(cell))) empty.add(cell);
            }
        }
        while (!empty.isEmpty() && pills.size() < PILL_COUNT) {
            Cell location = empty.remove(RANDOM.nextInt(empty.size()));
            int points = randomPillPoints();
            Sphere sphere = createPillSphere(location, points);
            worldView.addObject(sphere);
            pills.add(new Pill(location, points, sphere));
        }
        if (pills.isEmpty()) {
            if (gameMode == GameMode.LAN) finishLanByScore();
            else finishGame(true);
        }
    }

    private Sphere createPillSphere(Cell cell, int points) {
        double radius = switch (points) {
            case 5 -> 0.47;
            case 3 -> 0.35;
            default -> 0.23;
        };
        Color color = switch (points) {
            case 5 -> Color.web("#ffb52e");
            case 3 -> Color.web("#ff587a");
            default -> Color.web("#67c8ff");
        };
        Sphere sphere = new Sphere(radius, 24);
        sphere.setMaterial(WormAppearance.material(color, color.brighter()));
        sphere.setTranslateX(cell.x);
        sphere.setTranslateY(-radius + 0.17);
        sphere.setTranslateZ(cell.z);
        return sphere;
    }

    private void clearPills() {
        for (Pill pill : pills) worldView.removeObject(pill.sphere);
        pills.clear();
    }

    private int randomPillPoints() {
        int roll = RANDOM.nextInt(100);
        return roll < 55 ? 1 : roll < 85 ? 3 : 5;
    }

    private void move() {
        Cell oldDirection = direction;
        direction = queuedDirection;
        Cell oldHead = worm.peekFirst();
        Cell next = new Cell(oldHead.x + direction.x, oldHead.z + direction.z);
        int edge = BOARD_SIZE / 2;
        if (Math.abs(next.x) > edge || Math.abs(next.z) > edge) {
            endGame();
            return;
        }
        Pill eaten = pills.stream().filter(pill -> pill.cell.equals(next)).findFirst().orElse(null);
        if (gameMode == GameMode.DUEL && aiWorm.contains(next)) {
            endGame();
            return;
        }
        boolean ate = eaten != null;
        for (Cell segment : worm) {
            if (segment.equals(next)) {
                endGame();
                return;
            }
        }

        previousWorm = new ArrayList<>(worm);
        previousDirection = oldDirection;
        worm.addFirst(next);
        if (ate) {
            previousWorm.add(previousWorm.getLast());
            pills.remove(eaten);
            worldView.removeObject(eaten.sphere);
            score += eaten.points;
            if (gameMode != GameMode.LAN) pillWallet += eaten.points;
            updateScoreDisplay();
            walletText.setText("PILLS  " + pillWallet);
            if (gameMode != GameMode.LAN) saveProfile();
            spawnPills();
        } else {
            worm.removeLast();
        }
    }

    private void takeAiTurn() {
        if (aiWorm.isEmpty() || pills.isEmpty()) return;
        Cell currentHead = aiWorm.peekFirst();
        Cell chosen = duelAI.chooseDirection(aiWorm, worm, pills, BOARD_SIZE, difficulty);
        if (chosen == null) {
            finishDuelAiCrash();
            return;
        }

        Cell next = new Cell(currentHead.x + chosen.x, currentHead.z + chosen.z);
        int edge = BOARD_SIZE / 2;
        if (outsideBoard(next, edge) || aiWorm.contains(next) || worm.contains(next)) {
            finishDuelAiCrash();
            return;
        }
        Pill eaten = pills.stream().filter(pill -> pill.cell.equals(next)).findFirst().orElse(null);
        aiWorm.addFirst(next);
        if (eaten == null) {
            aiWorm.removeLast();
        } else {
            pills.remove(eaten);
            worldView.removeObject(eaten.sphere);
            aiScore += eaten.points;
            spawnPills();
            updateScoreDisplay();
        }
        positionAiWorm();
    }

    private void updateScoreDisplay() {
        if (gameMode == GameMode.DUEL) {
            scoreText.setText("DUEL " + difficulty.label + "   YOU  " + score + "     AI  " + aiScore);
        } else if (gameMode == GameMode.LAN) {
            scoreText.setText("LAN   " + lanLocalName + "  " + score + "     " + lanRemoteName + "  " + aiScore);
        } else {
            scoreText.setText("SCORE  " + score);
        }
    }

    private void endGame() {
        if (gameMode == GameMode.LAN) {
            finishLanCrash(true, false);
            return;
        }
        if (gameMode == GameMode.DUEL) duelPlayerCrashed = true;
        finishGame(false);
    }

    private void finishDuelAiCrash() {
        if (gameMode != GameMode.DUEL || gameOver) return;
        duelAiCrashed = true;
        finishGame(true);
    }

    private void finishGame(boolean won) {
        if (gameMode == GameMode.LAN) {
            finishLanCrash(true, false);
            return;
        }
        if (gameOver) return;
        gameOver = true;
        running = false;
        highScores.add(new HighScore(lastPlayerName.isBlank() ? "Player" : lastPlayerName, score));
        saveHighScores();
        saveProfile();
        updateHighScoreDisplay();
        if (gameMode == GameMode.DUEL) {
            messageText.setText(duelSummary());
        } else {
            messageText.setText((won ? "YOU WIN!" : "GAME OVER") + "\nScore: " + score + "\nPress ENTER to play again");
        }
    }

    private String duelSummary() {
        String result = duelPlayerCrashed ? "YOU CRASHED - THE AI WINS!"
                : duelAiCrashed ? "THE AI CRASHED - YOU WIN!"
                : score > aiScore ? "YOU BEAT THE AI!" : score < aiScore ? "THE AI WINS!" : "IT'S A TIE!";
        return "DUEL OVER\n" + result + "\nYOU " + score + "   AI " + aiScore + "\nPress ENTER to play again";
    }

    private void restart() {
        worldView.clearPlayer();
        clearAiWorm();
        clearPills();
        score = 0;
        aiScore = 0;
        gameOver = false;
        duelPlayerCrashed = false;
        duelAiCrashed = false;
        running = true;
        elapsed = 0;
        aiElapsed = 0;
        messageText.setText("");
        setupWorm();
        if (gameMode == GameMode.DUEL) setupAiWorm();
        spawnPills();
        updateScoreDisplay();
    }

    private void handleKey(KeyCode key) {
        if (remappingAction != null) {
            if (key == KeyCode.ESCAPE) {
                remappingAction = null;
                settingsMessage = "Key change cancelled.";
            } else if (key == KeyCode.UP || key == KeyCode.DOWN || key == KeyCode.LEFT || key == KeyCode.RIGHT) {
                remappingAction = null;
                settingsMessage = "Arrow keys are reserved. Choose another key.";
            } else if (key == KeyCode.ENTER || key == KeyCode.SPACE || key == KeyCode.C) {
                remappingAction = null;
                settingsMessage = "Enter, Space, and C are reserved. Choose another key.";
            } else {
                setControlKey(remappingAction, key);
                remappingAction = null;
                settingsMessage = "Controls saved.";
                updateControlsHint();
                saveProfile();
            }
            showSettingsMenu();
            return;
        }
        if (key == KeyCode.ESCAPE) {
            if (gameMode == GameMode.LAN && !gameOver) return;
            if (mainMenuLayer.isVisible()) return;
            if (!menuOpen) openPauseMenu();
            else closeMenu();
            return;
        }
        if (mainMenuLayer.isVisible()) return;
        if (menuOpen) return;
        if (key == KeyCode.C) {
            toggleOrbitCamera();
            return;
        }
        if (key == KeyCode.ENTER && gameOver) {
            if (gameMode == GameMode.LAN) {
                leaveLanSession();
                showMainMenu();
            } else {
                restart();
            }
            return;
        }
        if (key == KeyCode.SPACE && !gameOver && gameMode != GameMode.LAN) {
            running = !running;
            messageText.setText(running ? "" : "PAUSED");
            return;
        }
        if (gameOver) return;
        Cell requested = null;
        if (key == keyAway || key == KeyCode.UP) requested = new Cell(0, 1);
        else if (key == keyToward || key == KeyCode.DOWN) requested = new Cell(0, -1);
        else if (key == keyLeft || key == KeyCode.LEFT) requested = new Cell(-1, 0);
        else if (key == keyRight || key == KeyCode.RIGHT) requested = new Cell(1, 0);
        if (requested != null && !(requested.x == -direction.x && requested.z == -direction.z)) {
            if (gameMode == GameMode.LAN && !lanHost) {
                direction = requested;
                queuedDirection = requested;
                if (lanService != null) lanService.sendInput(requested);
            } else {
                queuedDirection = requested;
            }
        }
    }

    private void openPauseMenu() {
        if (!menuOpen) resumeAfterMenu = running;
        menuOpen = true;
        running = false;
        messageText.setText("");

        menuPanel = createMenuPanel();
        Label title = menuTitle("PAUSED");
        Button resume = menuButton("Resume");
        resume.setOnAction(event -> closeMenu());
        Button returnToMain = menuButton("Return to Main Menu");
        returnToMain.setOnAction(event -> returnToMainMenu());
        menuPanel.getChildren().setAll(title, resume, returnToMain);
        menuLayer.getChildren().setAll(menuPanel);
        menuLayer.setVisible(true);
    }

    private void returnToMainMenu() {
        leaveLanSession();
        menuOpen = false;
        running = false;
        messageText.setText("");
        menuLayer.setVisible(false);
        showMainMenu();
    }

    private void playGame() {
        if (!claimPlayerName()) return;
        if (gameMode != GameMode.SINGLE || gameOver) {
            gameMode = GameMode.SINGLE;
            restart();
        }
        mainMenuLayer.setVisible(false);
        menuLayer.setVisible(false);
        stage.setFullScreen(false);
        running = true;
        menuOpen = false;
        messageText.setText("");
    }

    private void startDuel() {
        if (!claimPlayerName()) return;
        gameMode = GameMode.DUEL;
        restart();
        mainMenuLayer.setVisible(false);
        menuLayer.setVisible(false);
        stage.setFullScreen(false);
        running = true;
    }

    private void showMainMenu() {
        menuOpen = false;
        menuLayer.setVisible(false);
        mainMenuLayer.setVisible(true);
        stage.setFullScreen(true);
        menuPanel = createMenuPanel();

        Label title = menuTitle("3D WORM");
        menuSnakeCanvas = new Canvas(330, 165);
        drawMenuSnake(System.nanoTime());
        playerNameField = new TextField(lastPlayerName);
        playerNameField.setPromptText("Enter your name");
        playerNameField.setMaxWidth(240);
        playerNameField.textProperty().addListener((observable, oldValue, newValue) -> updateNameControls());
        playerNameField.setOnAction(event -> claimPlayerName());
        claimNameButton = menuButton("Claim Name");
        claimNameButton.setOnAction(event -> claimPlayerName());
        nameStatusLabel = new Label();
        nameStatusLabel.setFont(Font.font("System", 13));
        HBox nameRow = new HBox(8, playerNameField, claimNameButton);
        nameRow.setAlignment(Pos.CENTER);
        playButton = menuButton("Play");
        playButton.setOnAction(event -> playGame());
        Button customize = menuButton("Customize");
        customize.setOnAction(event -> openCustomizeMenu());
        Button duel = menuButton("Duel");
        duel.setOnAction(event -> showDuelMenu());
        Button lan = menuButton("LAN Multiplayer");
        lan.setOnAction(event -> showLanMenu());
        Button settings = menuButton("Settings");
        settings.setOnAction(event -> showSettingsMenu());
        menuPanel.getChildren().setAll(title, menuSnakeCanvas, nameRow, nameStatusLabel,
                playButton, duel, lan, customize, settings);
        mainMenuLayer.getChildren().setAll(menuPanel);
        updateNameControls();
    }

    private void updateNameControls() {
        if (playerNameField == null || nameStatusLabel == null || claimNameButton == null || playButton == null) return;
        String candidate = playerNameField.getText() == null ? "" : playerNameField.getText().strip();
        String key = normalizeName(candidate);
        boolean currentName = !lastPlayerName.isBlank() && key.equals(normalizeName(lastPlayerName));
        String owner = nameRegistry.getProperty("name." + key);
        boolean takenByOther = owner != null && !owner.equals(profileId);
        playerNameField.setStyle(takenByOther ? "-fx-text-fill: #ff5c66;" : "");
        claimNameButton.setText(lastPlayerName.isBlank() ? "Claim Name" : "Change Name");

        if (!isValidName(candidate)) {
            nameStatusLabel.setText("Enter a name (1 to 16 characters).");
            nameStatusLabel.setTextFill(Color.web("#ff7373"));
            claimNameButton.setDisable(true);
            playButton.setDisable(true);
            return;
        }
        if (takenByOther) {
            nameStatusLabel.setText("That name is already taken.");
            nameStatusLabel.setTextFill(Color.web("#ff5c66"));
            claimNameButton.setDisable(true);
            playButton.setDisable(true);
            return;
        }
        if (currentName) {
            long remaining = NAME_CHANGE_COOLDOWN_MILLIS - (System.currentTimeMillis() - lastNameChangeMillis);
            nameStatusLabel.setText(remaining > 0
                    ? "Playing as " + lastPlayerName + ". Name change in " + cooldownText(remaining) + "."
                    : "Playing as " + lastPlayerName + ". You may change your name now.");
            nameStatusLabel.setTextFill(Color.web("#8fe0a2"));
            claimNameButton.setDisable(true);
            playButton.setDisable(false);
            return;
        }
        long remaining = lastPlayerName.isBlank() ? 0
                : NAME_CHANGE_COOLDOWN_MILLIS - (System.currentTimeMillis() - lastNameChangeMillis);
        if (remaining > 0) {
            nameStatusLabel.setText("You can change your name in " + cooldownText(remaining) + ".");
            nameStatusLabel.setTextFill(Color.web("#ffd166"));
            claimNameButton.setDisable(true);
            playButton.setDisable(true);
            return;
        }
        nameStatusLabel.setText("Name is available. Starting will claim it.");
        nameStatusLabel.setTextFill(Color.web("#8fe0a2"));
        claimNameButton.setDisable(false);
        playButton.setDisable(false);
    }

    private boolean claimPlayerName() {
        String candidate = requestedPlayerName();
        updateNameControls();
        if (!isValidName(candidate)) return false;
        String key = normalizeName(candidate);
        String owner = nameRegistry.getProperty("name." + key);
        if (owner != null && !owner.equals(profileId)) {
            updateNameControls();
            return false;
        }

        boolean sameAsCurrentName = !lastPlayerName.isBlank()
                && key.equals(normalizeName(lastPlayerName));
        long now = System.currentTimeMillis();
        if (!sameAsCurrentName && !lastPlayerName.isBlank()
                && now - lastNameChangeMillis < NAME_CHANGE_COOLDOWN_MILLIS) {
            updateNameControls();
            return false;
        }

        if (sameAsCurrentName) {
            // Reusing the current name must not restart the change cooldown.
            if (!candidate.equals(lastPlayerName)) {
                lastPlayerName = candidate;
                saveProfile();
            }
            updateNameControls();
            return true;
        }

        lastNameChangeMillis = now;
        lastPlayerName = candidate;
        nameRegistry.setProperty("name." + key, profileId);
        saveNameRegistry();
        saveProfile();
        if (playerNameField != null && !candidate.equals(playerNameField.getText())) {
            playerNameField.setText(candidate);
        }
        updateNameControls();
        return true;
    }

    private String requestedPlayerName() {
        if (playerNameField == null || playerNameField.getText() == null) return lastPlayerName;
        return playerNameField.getText().strip();
    }

    private boolean canStartWithRequestedName() {
        String candidate = requestedPlayerName();
        if (!isValidName(candidate)) return false;

        String key = normalizeName(candidate);
        String owner = nameRegistry.getProperty("name." + key);
        if (owner != null && !owner.equals(profileId)) return false;
        if (lastPlayerName.isBlank() || key.equals(normalizeName(lastPlayerName))) return true;

        return System.currentTimeMillis() - lastNameChangeMillis >= NAME_CHANGE_COOLDOWN_MILLIS;
    }

    private boolean isValidName(String name) {
        if (name == null || name.isBlank() || name.length() > 16 || name.indexOf('|') >= 0) return false;
        return name.chars().noneMatch(Character::isISOControl);
    }

    private String normalizeName(String name) {
        return name.strip().toLowerCase(Locale.ROOT);
    }

    private String cooldownText(long remainingMillis) {
        long minutes = Math.max(1, (remainingMillis + 59_999) / 60_000);
        long hours = minutes / 60;
        long leftoverMinutes = minutes % 60;
        if (hours == 0) return minutes + "m";
        return hours + "h " + leftoverMinutes + "m";
    }

    private void showSettingsMenu() {
        menuPanel = createMenuPanel();
        Label title = menuTitle("SETTINGS");
        Label explanation = new Label("Select a direction, then press the key you want to use. Arrow keys remain available.");
        explanation.setTextFill(Color.web("#d3def7"));
        explanation.setWrapText(true);
        explanation.setMaxWidth(430);

        Button away = controlButton(GameAction.AWAY, "Away", keyAway, "UP");
        Button toward = controlButton(GameAction.TOWARD, "Toward", keyToward, "DOWN");
        Button left = controlButton(GameAction.LEFT, "Left", keyLeft, "LEFT");
        Button right = controlButton(GameAction.RIGHT, "Right", keyRight, "RIGHT");
        Label status = new Label(remappingAction == null ? settingsMessage : "Press a key for " + actionName(remappingAction) + " (Escape cancels)");
        status.setTextFill(Color.web("#ffe49a"));
        Button back = menuButton("Back to Main Menu");
        back.setOnAction(event -> {
            remappingAction = null;
            showMainMenu();
        });
        menuPanel.getChildren().setAll(title, explanation, away, toward, left, right, status, back);
        mainMenuLayer.getChildren().setAll(menuPanel);
        mainMenuLayer.setVisible(true);
    }

    private void showDuelMenu() {
        menuPanel = createMenuPanel();
        Label title = menuTitle("DUEL");
        Label explanation = new Label("Race the AI to score more points. You both collect from the same pills.");
        explanation.setTextFill(Color.web("#d3def7"));
        explanation.setWrapText(true);
        explanation.setMaxWidth(440);
        Label difficultyLabel = menuSection("AI DIFFICULTY");
        HBox choices = new HBox(8);
        choices.setAlignment(Pos.CENTER);
        for (Difficulty option : Difficulty.values()) {
            Button button = menuButton(option.label);
            button.setMinWidth(110);
            if (difficulty == option) {
                button.setStyle("-fx-background-color: #267d6b; -fx-text-fill: white; -fx-font-size: 13px; -fx-background-radius: 8; -fx-padding: 9 12 9 12;");
            }
            button.setOnAction(event -> {
                difficulty = option;
                showDuelMenu();
            });
            choices.getChildren().add(button);
        }
        String requestedName = requestedPlayerName();
        boolean canStart = canStartWithRequestedName();
        Label status = new Label(canStart
                ? "This duel will be recorded as " + requestedName + "."
                : "Enter an available name on the Main Menu before starting.");
        status.setTextFill(canStart ? Color.web("#8fe0a2") : Color.web("#ff7373"));
        Button startDuel = menuButton("Start Duel");
        startDuel.setDisable(!canStart);
        startDuel.setOnAction(event -> startDuel());
        Button back = menuButton("Back to Main Menu");
        back.setOnAction(event -> showMainMenu());
        menuPanel.getChildren().setAll(title, explanation, difficultyLabel, choices, status, startDuel, back);
        mainMenuLayer.getChildren().setAll(menuPanel);
        mainMenuLayer.setVisible(true);
    }

    private void showLanMenu() {
        lanScreenOpen = true;
        lanLobbyOpen = false;
        lanStatusLabel = null;
        lanSessionList = new VBox(8);
        ensureLanService();
        lanService.startDiscovery();
        renderLanDiscoveryMenu();
    }

    private void renderLanDiscoveryMenu() {
        menuPanel = createMenuPanel();
        Label title = menuTitle("LAN MULTIPLAYER");
        Label explanation = new Label("Host a game or join a player on your local network. Both players must confirm before the match starts.");
        explanation.setTextFill(Color.web("#d3def7"));
        explanation.setWrapText(true);
        explanation.setMaxWidth(470);

        Button host = menuButton("Host a Game");
        host.setOnAction(event -> hostLanGame());
        Button refresh = menuButton("Refresh Players");
        refresh.setOnAction(event -> {
            lanStatus = "Looking for LAN games…";
            ensureLanService();
            lanService.startDiscovery();
            updateLanStatus();
        });
        Label available = menuSection("AVAILABLE GAMES");
        lanSessionList = new VBox(8);
        populateLanSessions();
        lanStatusLabel = new Label(lanStatus.isBlank() ? "Looking for LAN games…" : lanStatus);
        lanStatusLabel.setTextFill(Color.web("#ffe49a"));
        lanStatusLabel.setWrapText(true);
        Button back = menuButton("Back to Main Menu");
        back.setOnAction(event -> {
            leaveLanSession();
            showMainMenu();
        });
        menuPanel.getChildren().setAll(title, explanation, host, refresh, available,
                lanSessionList, lanStatusLabel, back);
        mainMenuLayer.getChildren().setAll(menuPanel);
        mainMenuLayer.setVisible(true);
    }

    private void populateLanSessions() {
        if (lanSessionList == null) return;
        lanSessionList.getChildren().clear();
        if (lanSessions.isEmpty()) {
            Label empty = new Label("No games found yet. Ask the other player to host.");
            empty.setTextFill(Color.web("#aab6d2"));
            lanSessionList.getChildren().add(empty);
            return;
        }

        for (LanSession session : lanSessions) {
            Button join = menuButton("Join " + session.playerName()
                    + "  ·  " + session.address().getHostAddress());
            join.setMaxWidth(Double.MAX_VALUE);
            join.setOnAction(event -> joinLanGame(session));
            lanSessionList.getChildren().add(join);
        }
    }

    private void hostLanGame() {
        if (!claimPlayerName()) {
            lanStatus = "Enter an available name before hosting.";
            updateLanStatus();
            return;
        }
        ensureLanService();
        gameMode = GameMode.LAN;
        lanHost = true;
        lanLocalName = lastPlayerName;
        lanRemoteName = "";
        lanLocalReady = false;
        lanRemoteReady = false;
        lanLobbyOpen = true;
        running = false;
        lanStatus = "Waiting for another player to join…";
        renderLanLobby();
        lanService.hostGame(lanLocalName);
    }

    private void joinLanGame(LanSession session) {
        if (!claimPlayerName()) {
            lanStatus = "Enter an available name before joining.";
            updateLanStatus();
            return;
        }
        ensureLanService();
        gameMode = GameMode.LAN;
        lanHost = false;
        lanLocalName = lastPlayerName;
        lanRemoteName = session.playerName();
        lanLocalReady = false;
        lanRemoteReady = false;
        lanLobbyOpen = true;
        running = false;
        lanStatus = "Connecting to " + session.playerName() + "…";
        renderLanLobby();
        lanService.join(session, lanLocalName);
    }

    private void renderLanLobby() {
        menuPanel = createMenuPanel();
        Label title = menuTitle("LAN GAME LOBBY");
        Label players = new Label(lanLocalName + (lanLocalReady ? "  ✓" : "  •")
                + "     vs     " + (lanRemoteName.isBlank() ? "Waiting for player…"
                : lanRemoteName + (lanRemoteReady ? "  ✓" : "  •")));
        players.setTextFill(Color.WHITE);
        players.setFont(Font.font("System", FontWeight.BOLD, 16));
        players.setWrapText(true);
        lanStatusLabel = new Label(lanStatus);
        lanStatusLabel.setTextFill(Color.web("#ffe49a"));
        lanStatusLabel.setWrapText(true);
        lanReadyButton = menuButton(lanLocalReady ? "Confirmed" : "Confirm Game");
        lanReadyButton.setDisable(lanRemoteName.isBlank() || lanLocalReady);
        lanReadyButton.setOnAction(event -> confirmLanReady());
        Label rule = new Label("You share pills and race for the higher score. The first player to crash ends the match.");
        rule.setTextFill(Color.web("#aab6d2"));
        rule.setWrapText(true);
        rule.setMaxWidth(460);
        Button back = menuButton("Leave Lobby");
        back.setOnAction(event -> {
            leaveLanSession();
            showMainMenu();
        });
        menuPanel.getChildren().setAll(title, players, rule, lanStatusLabel, lanReadyButton, back);
        mainMenuLayer.getChildren().setAll(menuPanel);
        mainMenuLayer.setVisible(true);
    }

    private void confirmLanReady() {
        if (lanService == null || lanRemoteName.isBlank() || lanLocalReady) return;
        lanLocalReady = true;
        lanStatus = "Waiting for the other player to confirm…";
        renderLanLobby();
        lanService.confirmReady();
    }

    private void updateLanStatus() {
        if (lanStatusLabel != null) lanStatusLabel.setText(lanStatus);
    }

    private void ensureLanService() {
        if (lanService != null) return;
        lanService = new LanMultiplayerService(new LanMultiplayerService.Listener() {
            @Override
            public void onDiscovered(List<LanSession> discovered) {
                runOnFx(() -> {
                    lanSessions = new ArrayList<>(discovered);
                    if (lanScreenOpen && !lanLobbyOpen) populateLanSessions();
                });
            }

            @Override
            public void onStatus(String message) {
                runOnFx(() -> {
                    lanStatus = message;
                    updateLanStatus();
                });
            }

            @Override
            public void onLobby(String hostName, String guestName, boolean hostReady, boolean guestReady) {
                runOnFx(() -> {
                    lanLocalName = lanHost ? hostName : guestName;
                    lanRemoteName = lanHost ? guestName : hostName;
                    lanLocalReady = lanHost ? hostReady : guestReady;
                    lanRemoteReady = lanHost ? guestReady : hostReady;
                    lanStatus = lanLocalReady && lanRemoteReady
                            ? "Both players confirmed. Starting in 5 seconds…"
                            : "Both players must confirm before the match starts.";
                    if (lanLobbyOpen) renderLanLobby();
                });
            }

            @Override
            public void onCountdown(int seconds) {
                runOnFx(() -> {
                    lanCountdown = seconds;
                    mainMenuLayer.setVisible(false);
                    messageText.setText("Starting in " + seconds + "…");
                });
            }

            @Override
            public void onGameStarted() {
                runOnFx(Main.this::beginLanMatch);
            }

            @Override
            public void onGuestInput(Cell guestDirection) {
                runOnFx(() -> queueGuestDirection(guestDirection));
            }

            @Override
            public void onGameState(LanGameState state) {
                runOnFx(() -> applyLanGameState(state));
            }

            @Override
            public void onMatchFinished(LanMatchResult result) {
                runOnFx(() -> acceptLanMatchResult(result));
            }

            @Override
            public void onError(String message) {
                runOnFx(() -> {
                    lanStatus = message;
                    updateLanStatus();
                    if (gameMode == GameMode.LAN && !gameOver && running) {
                        running = false;
                        gameOver = true;
                        messageText.setText("CONNECTION LOST\n" + message + "\nPress ENTER to return to menu");
                    }
                });
            }
        });
    }

    private void runOnFx(Runnable action) {
        if (Platform.isFxApplicationThread()) action.run();
        else Platform.runLater(action);
    }

    private void leaveLanSession() {
        if (lanService != null) {
            lanService.close();
            lanService = null;
        }
        lanScreenOpen = false;
        lanLobbyOpen = false;
        lanHost = false;
        lanLocalReady = false;
        lanRemoteReady = false;
        lanLocalName = "";
        lanRemoteName = "";
        lanSessions.clear();
        lanSessionList = null;
        lanStatusLabel = null;
        lanReadyButton = null;
        running = false;
        lanCountdown = 0;
        lastLanResult = null;
        if (gameMode == GameMode.LAN) {
            gameMode = GameMode.SINGLE;
            score = 0;
            aiScore = 0;
            gameOver = false;
            clearAiWorm();
            clearPills();
            setupWorm();
            spawnPills();
            updateHighScoreDisplay();
        }
    }

    private Button controlButton(GameAction action, String label, KeyCode key, String arrow) {
        String text = (remappingAction == action ? "Press a key for " + label : label + ": " + key.getName() + " / " + arrow);
        Button button = menuButton(text);
        button.setMinWidth(280);
        button.setOnAction(event -> {
            remappingAction = action;
            settingsMessage = "";
            showSettingsMenu();
        });
        return button;
    }

    private String actionName(GameAction action) {
        return switch (action) {
            case AWAY -> "Away";
            case TOWARD -> "Toward";
            case LEFT -> "Left";
            case RIGHT -> "Right";
        };
    }

    private KeyCode controlKey(GameAction action) {
        return switch (action) {
            case AWAY -> keyAway;
            case TOWARD -> keyToward;
            case LEFT -> keyLeft;
            case RIGHT -> keyRight;
        };
    }

    private void setControlKey(GameAction action, KeyCode key) {
        KeyCode previous = controlKey(action);
        for (GameAction other : GameAction.values()) {
            if (other != action && controlKey(other) == key) setControlKeyValue(other, previous);
        }
        setControlKeyValue(action, key);
    }

    private void setControlKeyValue(GameAction action, KeyCode key) {
        switch (action) {
            case AWAY -> keyAway = key;
            case TOWARD -> keyToward = key;
            case LEFT -> keyLeft = key;
            case RIGHT -> keyRight = key;
        }
    }

    private void updateControlsHint() {
        if (controlsText != null) {
            controlsText.setText(keyAway.getName() + " / UP: AWAY   " + keyToward.getName() + " / DOWN: TOWARD   "
                    + keyLeft.getName() + " / LEFT   " + keyRight.getName() + " / RIGHT   |   C: "
                    + (orbitCamera ? "FOLLOW CAMERA" : "ORBIT CAMERA (RMB + SCROLL)") + "   |   ESC: MENU");
        }
    }

    private void openCustomizeMenu() {
        menuOpen = true;
        running = false;
        menuPanel = createMenuPanel();

        Label title = menuTitle("CUSTOMIZE YOUR WORM");
        Label balance = new Label("Pills available: " + pillWallet);
        balance.setTextFill(Color.web("#ffe49a"));
        balance.setFont(Font.font("System", FontWeight.BOLD, 15));
        SubScene preview = createCustomizationPreview();
        Label hatsLabel = menuSection("HATS");
        FlowPane hatsRow = new FlowPane(8, 8);
        hatsRow.setAlignment(Pos.CENTER);
        hatsRow.setPrefWrapLength(490);
        Button removeHat = menuButton(selectedHat.equals("none") ? "No hat" : "Remove hat");
        removeHat.setMinWidth(110);
        removeHat.setOnAction(event -> {
            selectedHat = "none";
            shopMessage = "Hat removed.";
            saveProfile();
            updateHat();
            openCustomizeMenu();
        });
        hatsRow.getChildren().add(removeHat);
        for (HatOption hat : CustomizationCatalog.HATS) {
            String label;
            if (!ownedHats.contains(hat.id)) label = "Buy " + hat.name + " (" + hat.cost + " pts)";
            else label = (selectedHat.equals(hat.id) ? "Wearing " : "Wear ") + hat.name;
            Button button = menuButton(label);
            button.setMinWidth(145);
            button.setOnAction(event -> selectHat(hat));
            hatsRow.getChildren().add(button);
        }

        Label colorsLabel = menuSection("COLORS");
        FlowPane colorsRow = new FlowPane(8, 8);
        colorsRow.setAlignment(Pos.CENTER);
        colorsRow.setPrefWrapLength(490);
        for (ColorOption color : CustomizationCatalog.COLORS) {
            String label;
            if (!ownedColors.contains(color.id)) label = "Buy " + color.name + " (" + color.cost + " pts)";
            else label = (selectedColor.equals(color.id) ? "Using " : "Use ") + color.name;
            Button button = menuButton(label);
            button.setMinWidth(115);
            button.setOnAction(event -> selectColor(color));
            colorsRow.getChildren().add(button);
        }

        Label status = new Label(shopMessage);
        status.setTextFill(Color.web("#d3def7"));
        status.setFont(Font.font("System", 13));
        Button back = menuButton("Back to Main Menu");
        back.setOnAction(event -> showMainMenu());
        menuPanel.getChildren().setAll(title, balance, preview, hatsLabel, hatsRow, colorsLabel, colorsRow, status, back);
        mainMenuLayer.getChildren().setAll(menuPanel);
        mainMenuLayer.setVisible(true);
        menuLayer.setVisible(false);
        menuOpen = false;
    }

    private SubScene createCustomizationPreview() {
        return WormAppearance.createPreview(selectedColor, selectedHat);
    }

    private void selectHat(HatOption hat) {
        if (!ownedHats.contains(hat.id)) {
            if (pillWallet < hat.cost) {
                shopMessage = "You need " + (hat.cost - pillWallet) + " more pills for the " + hat.name + ".";
                openCustomizeMenu();
                return;
            }
            pillWallet -= hat.cost;
            ownedHats.add(hat.id);
        }
        selectedHat = hat.id;
        shopMessage = hat.name + " equipped.";
        walletText.setText("PILLS  " + pillWallet);
        saveProfile();
        updateHat();
        openCustomizeMenu();
    }

    private void selectColor(ColorOption color) {
        if (!ownedColors.contains(color.id)) {
            if (pillWallet < color.cost) {
                shopMessage = "You need " + (color.cost - pillWallet) + " more pills for " + color.name + ".";
                openCustomizeMenu();
                return;
            }
            pillWallet -= color.cost;
            ownedColors.add(color.id);
        }
        selectedColor = color.id;
        shopMessage = color.name + " color selected.";
        walletText.setText("PILLS  " + pillWallet);
        saveProfile();
        applyWormColor();
        openCustomizeMenu();
    }

    private void closeMenu() {
        menuOpen = false;
        menuLayer.setVisible(false);
        menuLayer.getChildren().clear();
        running = resumeAfterMenu && !gameOver;
        if (gameOver && gameMode == GameMode.LAN && lastLanResult != null) {
            messageText.setText(lanResultText(lastLanResult));
        } else if (gameOver) {
            messageText.setText(gameMode == GameMode.DUEL ? duelSummary()
                    : "GAME OVER\nScore: " + score + "\nPress ENTER to try again");
        }
        else messageText.setText(running ? "" : "PAUSED");
    }

    private VBox createMenuPanel() {
        VBox panel = new VBox(12);
        panel.setAlignment(Pos.CENTER);
        panel.setPadding(new Insets(22));
        panel.setMaxWidth(620);
        panel.setStyle("-fx-background-color: rgba(13, 20, 38, 0.97); -fx-background-radius: 16; -fx-border-color: #52668d; -fx-border-radius: 16;");
        return panel;
    }

    private Label menuTitle(String text) {
        Label label = new Label(text);
        label.setTextFill(Color.WHITE);
        label.setFont(Font.font("System", FontWeight.BOLD, 24));
        return label;
    }

    private Label menuSection(String text) {
        Label label = new Label(text);
        label.setTextFill(Color.web("#aab6d2"));
        label.setFont(Font.font("System", FontWeight.BOLD, 13));
        return label;
    }

    private Button menuButton(String text) {
        Button button = new Button(text);
        button.setWrapText(true);
        button.setStyle("-fx-background-color: #334568; -fx-text-fill: white; -fx-font-size: 13px; -fx-background-radius: 8; -fx-padding: 9 12 9 12;");
        return button;
    }

    private void drawMenuSnake(long now) {
        if (menuSnakeCanvas == null) return;
        GraphicsContext graphics = menuSnakeCanvas.getGraphicsContext2D();
        double width = menuSnakeCanvas.getWidth();
        double height = menuSnakeCanvas.getHeight();
        graphics.setFill(Color.web("#10172a"));
        graphics.fillRoundRect(0, 0, width, height, 16, 16);
        graphics.setStroke(Color.web("#293755"));
        graphics.setLineWidth(1);
        for (int x = 18; x < width; x += 22) graphics.strokeLine(x, 12, x, height - 12);
        for (int y = 12; y < height; y += 22) graphics.strokeLine(12, y, width - 12, y);
        graphics.setStroke(Color.web("#557095"));
        graphics.setLineWidth(2);
        graphics.strokeRoundRect(28, 24, width - 56, height - 48, 24, 24);

        double loopWidth = width - 70;
        double loopHeight = height - 62;
        double perimeter = 2 * (loopWidth + loopHeight);
        double headDistance = (now / 1_000_000_000.0 * 48) % perimeter;
        double headX = 35;
        double headY = 31;
        double tangentX = 1;
        double tangentY = 0;
        for (int i = 8; i >= 0; i--) {
            double distance = headDistance - i * 13;
            while (distance < 0) distance += perimeter;
            distance %= perimeter;
            double x;
            double y;
            if (distance < loopWidth) {
                x = 35 + distance;
                y = 31;
                if (i == 0) { tangentX = 1; tangentY = 0; }
            } else if (distance < loopWidth + loopHeight) {
                x = 35 + loopWidth;
                y = 31 + distance - loopWidth;
                if (i == 0) { tangentX = 0; tangentY = 1; }
            } else if (distance < 2 * loopWidth + loopHeight) {
                x = 35 + loopWidth - (distance - loopWidth - loopHeight);
                y = 31 + loopHeight;
                if (i == 0) { tangentX = -1; tangentY = 0; }
            } else {
                x = 35;
                y = 31 + loopHeight - (distance - 2 * loopWidth - loopHeight);
                if (i == 0) { tangentX = 0; tangentY = -1; }
            }
            double radius = i == 0 ? 10 : 8;
            graphics.setFill(i == 0 ? Color.web("#70ffd0") : Color.web("#27c98b"));
            graphics.fillOval(x - radius, y - radius, radius * 2, radius * 2);
            if (i == 0) {
                double frontX = x + tangentX * 5;
                double frontY = y + tangentY * 5;
                graphics.setFill(Color.WHITE);
                graphics.fillOval(frontX - 5, frontY - 5, 6, 6);
                graphics.fillOval(frontX + 1, frontY - 5, 6, 6);
                graphics.setFill(Color.web("#17233d"));
                graphics.fillOval(frontX - 3, frontY - 3, 3, 3);
                graphics.fillOval(frontX + 3, frontY - 3, 3, 3);
            }
        }
    }

    private void startGameLoop() {
        new AnimationTimer() {
            @Override public void handle(long now) {
                if (lastFrame == 0) lastFrame = now;
                double delta = Math.min((now - lastFrame) / 1_000_000_000.0, 0.1);
                lastFrame = now;
                followCamera(delta);
                if (mainMenuLayer.isVisible() && now >= nextNameRefresh) {
                    updateNameControls();
                    nextNameRefresh = now + 1_000_000_000L;
                }
                if (menuSnakeCanvas != null && menuSnakeCanvas.getParent() != null) drawMenuSnake(now);
                if (!running || gameOver) return;
                elapsed += delta;
                if (gameMode == GameMode.DUEL) {
                    aiElapsed += delta;
                    if (aiElapsed >= duelAI.stepSeconds(difficulty)) {
                        aiElapsed -= duelAI.stepSeconds(difficulty);
                        takeAiTurn();
                    }
                }
                for (Pill pill : pills) pill.sphere.setScaleY(0.88 + Math.sin(now / 180_000_000.0 + pill.cell.x) * 0.12);
                if (elapsed >= STEP_SECONDS) {
                    elapsed -= STEP_SECONDS;
                    if (gameMode == GameMode.LAN) {
                        if (lanHost) {
                            if (finishLanCollisionIfNeeded()) return;
                            if (RANDOM.nextBoolean()) {
                                move();
                                if (!gameOver) moveGuestWorm();
                            } else {
                                moveGuestWorm();
                                if (!gameOver) move();
                            }
                            if (!gameOver) {
                                updateScoreDisplay();
                                broadcastLanState();
                            }
                        }
                    } else {
                        move();
                    }
                }
                positionWorm(Math.min(1, elapsed / STEP_SECONDS));
            }
        }.start();
    }

    private void fitCamera(PerspectiveCamera camera, SubScene view) {
        if (view.getWidth() <= 0 || view.getHeight() <= 0) return;
        double aspect = view.getWidth() / view.getHeight();
        double fieldOfViewTangent = Math.tan(Math.toRadians(camera.getFieldOfView() / 2));
        double boardHalfSize = BOARD_SIZE / 2.0 + 1.5;
        double pitch = Math.toRadians(cameraPitch);
        double pitchCosine = Math.cos(pitch);
        double horizontalDistance = boardHalfSize * pitchCosine / (fieldOfViewTangent * aspect);
        double verticalHalfSize = boardHalfSize * Math.sin(pitch) + 1.2;
        double verticalDistance = verticalHalfSize * pitchCosine / fieldOfViewTangent;
        double fitDistance = Math.max(horizontalDistance, verticalDistance) * 1.08;
        cameraDistance = clamp(fitDistance * (orbitCamera ? orbitZoom : 1), 12, 90);
        setCameraPoseImmediately();
    }

    private void followCamera(double delta) {
        if (orbitCamera) return;
        Cell head = worm.peekFirst();
        setCameraOrientation();
        double pitch = Math.toRadians(cameraPitch);
        double horizontalOffset = cameraDistance;
        double desiredX = head.x + Math.sin(Math.toRadians(cameraYaw)) * horizontalOffset;
        double desiredY = -0.3 - horizontalOffset * Math.tan(pitch);
        double desiredZ = head.z - Math.cos(Math.toRadians(cameraYaw)) * horizontalOffset;
        double smoothing = 1 - Math.exp(-12 * delta);
        camera.setTranslateX(camera.getTranslateX() + (desiredX - camera.getTranslateX()) * smoothing);
        camera.setTranslateY(camera.getTranslateY() + (desiredY - camera.getTranslateY()) * smoothing);
        camera.setTranslateZ(camera.getTranslateZ() + (desiredZ - camera.getTranslateZ()) * smoothing);
    }

    private void toggleOrbitCamera() {
        orbitCamera = !orbitCamera;
        orbitDragging = false;
        if (orbitCamera) {
            cameraYaw = 0;
            orbitZoom = 1;
        } else {
            cameraYaw = 0;
            cameraPitch = 27;
            orbitZoom = 1;
            fitCamera(camera, gameView);
        }
        setCameraPoseImmediately();
        updateControlsHint();
    }

    private void setCameraPoseImmediately() {
        double targetX;
        double targetY;
        double targetZ;
        if (orbitCamera) {
            targetX = 0;
            targetY = 0;
            targetZ = 0;
        } else {
            Cell head = worm.peekFirst();
            targetX = head.x;
            targetY = -0.3;
            targetZ = head.z;
        }
        double pitch = Math.toRadians(cameraPitch);
        double yaw = Math.toRadians(cameraYaw);
        setCameraOrientation();
        camera.setTranslateX(targetX + Math.sin(yaw) * cameraDistance);
        camera.setTranslateY(targetY - cameraDistance * Math.tan(pitch));
        camera.setTranslateZ(targetZ - Math.cos(yaw) * cameraDistance);
    }

    private void setCameraOrientation() {
        cameraYawTransform.setAngle(-cameraYaw);
        cameraPitchTransform.setAngle(-cameraPitch);
    }

    private double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private void loadHighScores() {
        highScores.addAll(dataStore.loadHighScores());
    }

    private void loadLanHighScores() {
        lanHighScores.addAll(dataStore.loadLanHighScores());
    }

    private void saveHighScores() {
        dataStore.saveHighScores(highScores);
    }

    private void saveLanHighScores() {
        dataStore.saveLanHighScores(lanHighScores);
    }

    private void updateHighScoreDisplay() {
        boolean lanLeaderboard = gameMode == GameMode.LAN;
        List<HighScore> displayedScores = lanLeaderboard ? lanHighScores : highScores;
        StringBuilder text = new StringBuilder(lanLeaderboard ? "LAN HIGH SCORES\n" : "HIGH SCORES\n");
        if (displayedScores.isEmpty()) text.append("No scores yet");
        else {
            for (int i = 0; i < displayedScores.size(); i++) {
                HighScore entry = displayedScores.get(i);
                text.append(String.format("%2d  %-12s %d%n", i + 1, entry.name, entry.score));
            }
        }
        highScoreText.setText(text.toString());
    }

    private void loadProfile() {
        ownedColors.add("mint");
        Properties profile = dataStore.loadProfile();
        try {
            pillWallet = Math.max(0, Integer.parseInt(profile.getProperty("pillWallet", "0")));
        } catch (NumberFormatException ignored) {
            pillWallet = 0;
        }
        readOwned(profile.getProperty("ownedHats", ""), ownedHats,
                CustomizationCatalog.HATS.stream().map(HatOption::id).collect(java.util.stream.Collectors.toSet()));
        readOwned(profile.getProperty("ownedColors", "mint"), ownedColors,
                CustomizationCatalog.COLORS.stream().map(ColorOption::id).collect(java.util.stream.Collectors.toSet()));
        keyAway = readKey(profile, "keyAway", KeyCode.W);
        keyToward = readKey(profile, "keyToward", KeyCode.S);
        keyLeft = readKey(profile, "keyLeft", KeyCode.A);
        keyRight = readKey(profile, "keyRight", KeyCode.D);
        lastPlayerName = profile.getProperty("lastPlayerName", "");
        profileId = profile.getProperty("profileId", profileId);
        try {
            lastNameChangeMillis = Long.parseLong(profile.getProperty("lastNameChangeMillis", "0"));
        } catch (NumberFormatException ignored) {
            lastNameChangeMillis = 0;
        }
        String savedHat = profile.getProperty("selectedHat", "none");
        if (ownedHats.contains(savedHat)) selectedHat = savedHat;
        String savedColor = profile.getProperty("selectedColor", "mint");
        if (ownedColors.contains(savedColor)) selectedColor = savedColor;

        applyWormColor();
        updateHat();
    }

    private void readOwned(String value, Set<String> target, Set<String> validIds) {
        for (String id : value.split(",")) {
            String trimmed = id.trim();
            if (validIds.contains(trimmed)) target.add(trimmed);
        }
    }

    private KeyCode readKey(Properties profile, String property, KeyCode defaultKey) {
        try {
            return KeyCode.valueOf(profile.getProperty(property, defaultKey.name()));
        } catch (IllegalArgumentException ignored) {
            return defaultKey;
        }
    }

    private void saveProfile() {
        Properties profile = new Properties();
        profile.setProperty("pillWallet", Integer.toString(pillWallet));
        profile.setProperty("ownedHats", String.join(",", ownedHats.stream().sorted().toList()));
        profile.setProperty("ownedColors", String.join(",", ownedColors.stream().sorted().toList()));
        profile.setProperty("selectedHat", selectedHat);
        profile.setProperty("selectedColor", selectedColor);
        profile.setProperty("keyAway", keyAway.name());
        profile.setProperty("keyToward", keyToward.name());
        profile.setProperty("keyLeft", keyLeft.name());
        profile.setProperty("keyRight", keyRight.name());
        profile.setProperty("lastPlayerName", lastPlayerName);
        profile.setProperty("profileId", profileId);
        profile.setProperty("lastNameChangeMillis", Long.toString(lastNameChangeMillis));
        dataStore.saveProfile(profile);
    }

    private void loadNameRegistry() {
        nameRegistry = dataStore.loadNameRegistry();
        if (!lastPlayerName.isBlank()) {
            String key = "name." + normalizeName(lastPlayerName);
            String owner = nameRegistry.getProperty(key);
            boolean changed = false;
            if (owner == null) {
                nameRegistry.setProperty(key, profileId);
                changed = true;
            }
            if (lastNameChangeMillis == 0) {
                lastNameChangeMillis = System.currentTimeMillis();
                changed = true;
            }
            if (changed) {
                saveNameRegistry();
                saveProfile();
            }
        }
    }

    private void saveNameRegistry() {
        dataStore.saveNameRegistry(nameRegistry);
    }

    private void applyWormColor() {
        worldView.updatePlayerColor(selectedColor);
    }

    private void updateHat() {
        worldView.updateHat(selectedHat);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
