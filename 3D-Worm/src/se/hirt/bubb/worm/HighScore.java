package se.hirt.bubb.worm;

/** One player's completed game result. */
final class HighScore {
    final String name;
    final int score;

    HighScore(String name, int score) {
        this.name = name;
        this.score = score;
    }

    int score() {
        return score;
    }
}
