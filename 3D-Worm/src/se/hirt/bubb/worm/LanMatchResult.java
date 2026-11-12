package se.hirt.bubb.worm;

/** Final score and winner announced by the host. */
record LanMatchResult(String winnerName, int winnerScore, int hostScore, int guestScore) {
    boolean isTie() {
        return winnerName.isBlank();
    }
}
