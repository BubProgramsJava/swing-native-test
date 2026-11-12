package se.hirt.bubb.worm;

import java.net.InetAddress;

/** A LAN game advertised by another computer. */
record LanSession(String id, String playerName, InetAddress address, int port) { }
