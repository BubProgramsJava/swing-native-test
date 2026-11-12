package se.hirt.bubb.worm;

/** A hat available in the customization shop. */
final class HatOption {
    final String id;
    final String name;
    final int cost;

    HatOption(String id, String name, int cost) {
        this.id = id;
        this.name = name;
        this.cost = cost;
    }

    String id() {
        return id;
    }
}
