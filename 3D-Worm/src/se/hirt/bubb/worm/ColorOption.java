package se.hirt.bubb.worm;

import javafx.scene.paint.Color;

/** A worm color available in the customization shop. */
final class ColorOption {
    final String id;
    final String name;
    final int cost;
    final Color color;

    ColorOption(String id, String name, int cost, Color color) {
        this.id = id;
        this.name = name;
        this.cost = cost;
        this.color = color;
    }

    String id() {
        return id;
    }
}
