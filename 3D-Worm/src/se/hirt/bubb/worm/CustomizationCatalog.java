package se.hirt.bubb.worm;

import javafx.scene.paint.Color;

import java.util.List;

/** Available cosmetic items and their in-game prices. */
final class CustomizationCatalog {
    static final List<HatOption> HATS = List.of(
            new HatOption("cap", "Cap", 70),
            new HatOption("crown", "Crown", 150),
            new HatOption("antenna", "Antenna", 60),
            new HatOption("wizard", "Wizard Hat", 120),
            new HatOption("pirate", "Pirate Hat", 135),
            new HatOption("flower", "Flower", 90));

    static final List<ColorOption> COLORS = List.of(
            new ColorOption("mint", "Mint", 0, Color.web("#27c98b")),
            new ColorOption("sky", "Sky", 12, Color.web("#54b9ff")),
            new ColorOption("ruby", "Ruby", 20, Color.web("#fa4f76")),
            new ColorOption("violet", "Violet", 28, Color.web("#aa72ff")));

    private CustomizationCatalog() { }
}
