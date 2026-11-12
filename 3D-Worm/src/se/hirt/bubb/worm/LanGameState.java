package se.hirt.bubb.worm;

import java.util.List;

/** One host-authoritative snapshot of a LAN match. */
record LanGameState(
        List<Cell> hostWorm,
        List<Cell> guestWorm,
        int hostScore,
        int guestScore,
        List<ScoredCell> pills) {

    LanGameState {
        hostWorm = List.copyOf(hostWorm);
        guestWorm = List.copyOf(guestWorm);
        pills = List.copyOf(pills);
    }

    record ScoredCell(Cell cell, int points) { }
}
