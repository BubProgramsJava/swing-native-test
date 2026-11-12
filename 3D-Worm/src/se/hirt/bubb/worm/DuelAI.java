package se.hirt.bubb.worm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Chooses safe moves for the opponent worm while avoiding both worms' occupied cells. */
final class DuelAI {
    private static final List<Cell> DIRECTIONS = List.of(
            new Cell(1, 0), new Cell(-1, 0), new Cell(0, 1), new Cell(0, -1));

    private final Random random = new Random();

    Cell chooseDirection(Deque<Cell> snake, Deque<Cell> opponent,
                         List<Pill> pills, int boardSize, Difficulty difficulty) {
        if (snake.isEmpty() || pills.isEmpty()) return null;

        List<Cell> safeMoves = new ArrayList<>();
        Cell head = snake.peekFirst();
        for (Cell direction : DIRECTIONS) {
            Cell next = new Cell(head.x + direction.x, head.z + direction.z);
            if (isSafe(snake, opponent, next, boardSize)) safeMoves.add(direction);
        }
        if (safeMoves.isEmpty()) return null;

        return switch (difficulty) {
            case EASY -> random.nextDouble() < 0.7
                    ? randomMove(safeMoves) : greedyMove(snake, pills, safeMoves);
            case MEDIUM -> random.nextDouble() < 0.25
                    ? randomMove(safeMoves) : greedyMove(snake, pills, safeMoves);
            case NORMAL, HARD -> {
                Cell pathMove = shortestPathMove(snake, opponent, pills, safeMoves, boardSize);
                yield pathMove == null ? randomMove(safeMoves) : pathMove;
            }
        };
    }

    double stepSeconds(Difficulty difficulty) {
        return switch (difficulty) {
            case EASY -> 0.34;
            case MEDIUM -> 0.25;
            case NORMAL -> 0.19;
            case HARD -> 0.13;
        };
    }

    private Cell randomMove(List<Cell> moves) {
        return moves.get(random.nextInt(moves.size()));
    }

    private Cell greedyMove(Deque<Cell> snake, List<Pill> pills, List<Cell> options) {
        Cell head = snake.peekFirst();
        Cell target = pills.stream()
                .min(Comparator.comparingInt(pill -> distance(pill.cell, head)))
                .map(pill -> pill.cell)
                .orElse(head);

        int bestDistance = Integer.MAX_VALUE;
        List<Cell> bestMoves = new ArrayList<>();
        for (Cell direction : options) {
            Cell next = new Cell(head.x + direction.x, head.z + direction.z);
            int distance = distance(target, next);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestMoves.clear();
                bestMoves.add(direction);
            } else if (distance == bestDistance) {
                bestMoves.add(direction);
            }
        }
        return randomMove(bestMoves);
    }

    private Cell shortestPathMove(Deque<Cell> snake, Deque<Cell> opponent,
                                  List<Pill> pills, List<Cell> safeMoves, int boardSize) {
        Cell start = snake.peekFirst();
        Set<Cell> targets = new HashSet<>();
        for (Pill pill : pills) targets.add(pill.cell);

        Deque<Cell> queue = new ArrayDeque<>();
        Map<Cell, Cell> previous = new HashMap<>();
        queue.add(start);
        previous.put(start, start);

        Cell target = null;
        int edge = boardSize / 2;
        while (!queue.isEmpty()) {
            Cell current = queue.removeFirst();
            if (!current.equals(start) && targets.contains(current)) {
                target = current;
                break;
            }
            for (Cell direction : DIRECTIONS) {
                Cell next = new Cell(current.x + direction.x, current.z + direction.z);
                if (Math.abs(next.x) > edge || Math.abs(next.z) > edge || previous.containsKey(next)) continue;
                if (!isSafe(snake, opponent, next, boardSize)) continue;
                previous.put(next, current);
                queue.addLast(next);
            }
        }
        if (target == null) return null;
        while (!previous.get(target).equals(start)) target = previous.get(target);
        Cell firstMove = new Cell(target.x - start.x, target.z - start.z);
        return safeMoves.contains(firstMove) ? firstMove : null;
    }

    private boolean isSafe(Deque<Cell> snake, Deque<Cell> opponent, Cell cell, int boardSize) {
        int edge = boardSize / 2;
        if (Math.abs(cell.x) > edge || Math.abs(cell.z) > edge) return false;
        if (opponent.contains(cell)) return false;

        for (Cell segment : snake) {
            if (segment.equals(cell)) return false;
        }
        return true;
    }

    private boolean isPillAt(List<Pill> pills, Cell cell) {
        return pills.stream().anyMatch(pill -> pill.cell.equals(cell));
    }

    private int distance(Cell a, Cell b) {
        return Math.abs(a.x - b.x) + Math.abs(a.z - b.z);
    }
}
