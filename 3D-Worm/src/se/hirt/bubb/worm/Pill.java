package se.hirt.bubb.worm;

import javafx.scene.shape.Sphere;

/** A collectible with a board position, point value, and visible sphere. */
final class Pill {
    final Cell cell;
    final int points;
    final Sphere sphere;

    Pill(Cell cell, int points, Sphere sphere) {
        this.cell = cell;
        this.points = points;
        this.sphere = sphere;
    }

    Cell cell() {
        return cell;
    }
}
