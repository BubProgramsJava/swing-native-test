package se.hirt.bubb.worm;

/** AI speed and decision level used in a duel. */
enum Difficulty {
    EASY("Easy"),
    MEDIUM("Medium"),
    NORMAL("Normal"),
    HARD("Hard");

    final String label;

    Difficulty(String label) {
        this.label = label;
    }
}
