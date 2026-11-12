package se.hirt.bubb.worm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

/** Reads and writes the profile, claimed names, and leaderboard. */
final class GameDataStore {
    private static final Path DATA_DIRECTORY = Path.of(System.getProperty("user.home"), ".3d-worm");
    private static final Path HIGH_SCORE_FILE = DATA_DIRECTORY.resolve("highscores.txt");
    private static final Path LAN_HIGH_SCORE_FILE = DATA_DIRECTORY.resolve("lan-highscores.txt");
    private static final Path PROFILE_FILE = DATA_DIRECTORY.resolve("profile.properties");
    private static final Path NAME_REGISTRY_FILE = DATA_DIRECTORY.resolve("names.properties");
    private static final int HIGH_SCORE_LIMIT = 10;

    List<HighScore> loadHighScores() {
        return loadHighScores(HIGH_SCORE_FILE);
    }

    List<HighScore> loadLanHighScores() {
        return loadHighScores(LAN_HIGH_SCORE_FILE);
    }

    private List<HighScore> loadHighScores(Path scoreFile) {
        List<HighScore> scores = new ArrayList<>();
        if (!Files.exists(scoreFile)) return scores;

        try {
            for (String line : Files.readAllLines(scoreFile)) {
                try {
                    int separator = line.lastIndexOf('|');
                    if (separator < 0) {
                        scores.add(new HighScore("Player", Integer.parseInt(line.trim())));
                        continue;
                    }
                    String name = line.substring(0, separator).strip();
                    int score = Integer.parseInt(line.substring(separator + 1).trim());
                    scores.add(new HighScore(name.isEmpty() ? "Player" : name, score));
                } catch (NumberFormatException ignored) {
                    // Ignore individual damaged leaderboard entries.
                }
            }
            sortAndLimit(scores);
        } catch (IOException exception) {
            System.err.println("Could not read high scores: " + exception.getMessage());
        }
        return scores;
    }

    void saveHighScores(List<HighScore> scores) {
        saveHighScores(HIGH_SCORE_FILE, scores);
    }

    void saveLanHighScores(List<HighScore> scores) {
        saveHighScores(LAN_HIGH_SCORE_FILE, scores);
    }

    private void saveHighScores(Path scoreFile, List<HighScore> scores) {
        sortAndLimit(scores);
        List<String> lines = scores.stream().map(score -> score.name + "|" + score.score).toList();
        writeLines(scoreFile, lines, "high scores");
    }

    Properties loadProfile() {
        return loadProperties(PROFILE_FILE, "worm profile");
    }

    void saveProfile(Properties profile) {
        saveProperties(PROFILE_FILE, profile, "3D Worm profile");
    }

    Properties loadNameRegistry() {
        return loadProperties(NAME_REGISTRY_FILE, "claimed names");
    }

    void saveNameRegistry(Properties names) {
        saveProperties(NAME_REGISTRY_FILE, names, "Permanently claimed 3D Worm names");
    }

    private void sortAndLimit(List<HighScore> scores) {
        scores.sort(Comparator.comparingInt(HighScore::score).reversed());
        if (scores.size() > HIGH_SCORE_LIMIT) {
            scores.subList(HIGH_SCORE_LIMIT, scores.size()).clear();
        }
    }

    private Properties loadProperties(Path path, String description) {
        Properties properties = new Properties();
        if (!Files.exists(path)) return properties;
        try (var input = Files.newInputStream(path)) {
            properties.load(input);
        } catch (IOException exception) {
            System.err.println("Could not read " + description + ": " + exception.getMessage());
        }
        return properties;
    }

    private void saveProperties(Path path, Properties properties, String comment) {
        try {
            Files.createDirectories(DATA_DIRECTORY);
            try (var output = Files.newOutputStream(path)) {
                properties.store(output, comment);
            }
        } catch (IOException exception) {
            System.err.println("Could not save " + comment.toLowerCase() + ": " + exception.getMessage());
        }
    }

    private void writeLines(Path path, List<String> lines, String description) {
        try {
            Files.createDirectories(DATA_DIRECTORY);
            Files.write(path, lines);
        } catch (IOException exception) {
            System.err.println("Could not save " + description + ": " + exception.getMessage());
        }
    }
}
