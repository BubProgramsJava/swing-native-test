package se.hirt.bubb.worm;

import java.util.Objects;

/** Immutable position on the square game board. */
final class Cell {
    final int x;
    final int z;

    Cell(int x, int z) {
        this.x = x;
        this.z = z;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Cell cell && x == cell.x && z == cell.z;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, z);
    }
}
