package se.hirt.bubb.worm;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.Sphere;

import java.util.ArrayList;
import java.util.List;

/** Owns the 3D scene graph and turns grid positions into visible worm geometry. */
final class GameWorldView {
    private final Group world = new Group();
    private final List<Sphere> playerSegments = new ArrayList<>();
    private final List<Sphere> aiSegments = new ArrayList<>();
    private final Group hat = new Group();

    private final PhongMaterial eyeMaterial = WormAppearance.material(Color.WHITE, Color.WHITE);
    private final PhongMaterial pupilMaterial = WormAppearance.material(Color.web("#17233d"), Color.web("#8cb9ff"));
    private final PhongMaterial aiMaterial = WormAppearance.material(Color.web("#ff9c46"), Color.web("#ffe2a8"));

    private Sphere playerHead;
    private Sphere leftEye;
    private Sphere rightEye;
    private Sphere leftPupil;
    private Sphere rightPupil;
    private Sphere aiHead;
    private Sphere aiLeftEye;
    private Sphere aiRightEye;
    private Sphere aiLeftPupil;
    private Sphere aiRightPupil;
    private PhongMaterial bodyMaterial;
    private PhongMaterial headMaterial;

    Group root() {
        return world;
    }

    void buildBoard(int boardSize) {
        Box floor = new Box(boardSize, 0.35, boardSize);
        floor.setTranslateY(0.35);
        floor.setMaterial(WormAppearance.material(Color.web("#202b45"), Color.web("#364767")));
        world.getChildren().add(floor);

        PhongMaterial lineMaterial = WormAppearance.material(Color.web("#293755"), Color.web("#293755"));
        double edge = boardSize / 2.0;
        for (int i = 0; i <= boardSize; i++) {
            double position = -edge + i;
            Box vertical = new Box(0.025, 0.025, boardSize);
            vertical.setTranslateX(position);
            vertical.setTranslateY(0.16);
            vertical.setMaterial(lineMaterial);
            Box horizontal = new Box(boardSize, 0.025, 0.025);
            horizontal.setTranslateZ(position);
            horizontal.setTranslateY(0.16);
            horizontal.setMaterial(lineMaterial);
            world.getChildren().addAll(vertical, horizontal);
        }

        javafx.scene.AmbientLight ambient = new javafx.scene.AmbientLight(Color.color(0.68, 0.72, 0.9));
        javafx.scene.PointLight light = new javafx.scene.PointLight(Color.WHITE);
        light.setTranslateX(-8);
        light.setTranslateY(-12);
        light.setTranslateZ(-10);
        world.getChildren().addAll(ambient, light);
    }

    void resetPlayer(List<Cell> cells, String colorId, String hatId) {
        clearPlayer();
        WormAppearance.Materials materials = WormAppearance.materialsFor(colorId);
        bodyMaterial = materials.body;
        headMaterial = materials.head;

        playerHead = new Sphere(0.48, 24);
        playerHead.setMaterial(headMaterial);
        leftEye = eye();
        rightEye = eye();
        leftPupil = pupil();
        rightPupil = pupil();
        world.getChildren().addAll(playerHead, leftEye, rightEye, leftPupil, rightPupil);
        if (hat.getParent() == null) world.getChildren().add(hat);
        WormAppearance.populateHat(hat, hatId);
        ensurePlayerSegments(cells.size() - 1);
    }

    void clearPlayer() {
        if (playerHead != null) world.getChildren().removeAll(playerHead, leftEye, rightEye, leftPupil, rightPupil);
        world.getChildren().removeAll(playerSegments);
        playerSegments.clear();
        playerHead = null;
    }

    void renderPlayer(List<Cell> current, List<Cell> previous, Cell direction,
                      Cell previousDirection, double progress, Cell pillTarget) {
        if (playerHead == null || current.isEmpty()) return;
        ensurePlayerSegments(current.size() - 1);

        Cell headCell = current.getFirst();
        Cell oldHead = previous.size() == current.size() ? previous.getFirst() : headCell;
        double headX = interpolate(oldHead.x, headCell.x, progress);
        double headZ = interpolate(oldHead.z, headCell.z, progress);
        playerHead.setTranslateX(headX);
        playerHead.setTranslateY(-0.30);
        playerHead.setTranslateZ(headZ);
        hat.setTranslateX(headX);
        hat.setTranslateY(-0.30);
        hat.setTranslateZ(headZ);

        double facingX = interpolate(previousDirection.x, direction.x, progress);
        double facingZ = interpolate(previousDirection.z, direction.z, progress);
        double length = Math.hypot(facingX, facingZ);
        if (length == 0) length = 1;
        facingX /= length;
        facingZ /= length;

        double sideX = -facingZ * 0.25;
        double sideZ = facingX * 0.25;
        double forwardX = facingX * 0.32;
        double forwardZ = facingZ * 0.32;
        place(leftEye, headX + forwardX + sideX, -0.30, headZ + forwardZ + sideZ);
        place(rightEye, headX + forwardX - sideX, -0.30, headZ + forwardZ - sideZ);

        double gazeX = facingX * 0.055 + pillTarget.x * 0.035;
        double gazeZ = facingZ * 0.055 + pillTarget.z * 0.035;
        place(leftPupil, headX + forwardX + sideX + gazeX, -0.40, headZ + forwardZ + sideZ + gazeZ);
        place(rightPupil, headX + forwardX - sideX + gazeX, -0.40, headZ + forwardZ - sideZ + gazeZ);

        for (int i = 1; i < current.size(); i++) {
            Cell cell = current.get(i);
            Cell oldCell = i < previous.size() ? previous.get(i) : cell;
            Sphere segment = playerSegments.get(i - 1);
            segment.setTranslateX(interpolate(oldCell.x, cell.x, progress));
            segment.setTranslateY(-0.25);
            segment.setTranslateZ(interpolate(oldCell.z, cell.z, progress));
        }
    }

    void updatePlayerColor(String colorId) {
        WormAppearance.Materials materials = WormAppearance.materialsFor(colorId);
        bodyMaterial = materials.body;
        headMaterial = materials.head;
        if (playerHead != null) playerHead.setMaterial(headMaterial);
        for (Sphere segment : playerSegments) segment.setMaterial(bodyMaterial);
    }

    void updateHat(String hatId) {
        WormAppearance.populateHat(hat, hatId);
    }

    void resetAi(List<Cell> cells) {
        clearAi();
        aiHead = new Sphere(0.48, 24);
        aiHead.setMaterial(aiMaterial);
        aiLeftEye = eye();
        aiRightEye = eye();
        aiLeftPupil = pupil();
        aiRightPupil = pupil();
        world.getChildren().addAll(aiHead, aiLeftEye, aiRightEye, aiLeftPupil, aiRightPupil);
        ensureAiSegments(cells.size() - 1);
        renderAi(cells, null);
    }

    void clearAi() {
        if (aiHead != null) {
            world.getChildren().removeAll(aiHead, aiLeftEye, aiRightEye, aiLeftPupil, aiRightPupil);
        }
        world.getChildren().removeAll(aiSegments);
        aiSegments.clear();
        aiHead = null;
    }

    void renderAi(List<Cell> cells, Cell gazeTarget) {
        if (aiHead == null || cells.isEmpty()) return;
        ensureAiSegments(cells.size() - 1);

        Cell head = cells.getFirst();
        Cell neck = cells.size() > 1 ? cells.get(1) : new Cell(head.x - 1, head.z);
        double facingX = Math.signum(head.x - neck.x);
        double facingZ = Math.signum(head.z - neck.z);
        double sideX = -facingZ * 0.25;
        double sideZ = facingX * 0.25;
        double forwardX = facingX * 0.32;
        double forwardZ = facingZ * 0.32;

        place(aiLeftEye, head.x + forwardX + sideX, -0.30, head.z + forwardZ + sideZ);
        place(aiRightEye, head.x + forwardX - sideX, -0.30, head.z + forwardZ - sideZ);

        double gazeX = facingX * 0.055;
        double gazeZ = facingZ * 0.055;
        if (gazeTarget != null) {
            double targetX = gazeTarget.x - head.x;
            double targetZ = gazeTarget.z - head.z;
            double targetLength = Math.hypot(targetX, targetZ);
            if (targetLength > 0) {
                gazeX = targetX / targetLength * 0.08;
                gazeZ = targetZ / targetLength * 0.08;
            }
        }
        place(aiLeftPupil, head.x + forwardX + sideX + gazeX, -0.40,
                head.z + forwardZ + sideZ + gazeZ);
        place(aiRightPupil, head.x + forwardX - sideX + gazeX, -0.40,
                head.z + forwardZ - sideZ + gazeZ);

        for (int i = 0; i < cells.size(); i++) {
            Cell cell = cells.get(i);
            Sphere sphere = i == 0 ? aiHead : aiSegments.get(i - 1);
            sphere.setTranslateX(cell.x);
            sphere.setTranslateY(i == 0 ? -0.30 : -0.25);
            sphere.setTranslateZ(cell.z);
        }
    }

    void addObject(Node node) {
        world.getChildren().add(node);
    }

    void removeObject(Node node) {
        world.getChildren().remove(node);
    }

    private void ensurePlayerSegments(int count) {
        while (playerSegments.size() < count) {
            Sphere segment = new Sphere(0.43, 20);
            segment.setMaterial(bodyMaterial);
            playerSegments.add(segment);
            world.getChildren().add(segment);
        }
    }

    private void ensureAiSegments(int count) {
        while (aiSegments.size() < count) {
            Sphere segment = new Sphere(0.43, 20);
            segment.setMaterial(aiMaterial);
            aiSegments.add(segment);
            world.getChildren().add(segment);
        }
    }

    private Sphere eye() {
        Sphere eye = new Sphere(0.22, 20);
        eye.setMaterial(eyeMaterial);
        return eye;
    }

    private Sphere pupil() {
        Sphere pupil = new Sphere(0.105, 16);
        pupil.setMaterial(pupilMaterial);
        return pupil;
    }

    private void place(Sphere sphere, double x, double y, double z) {
        sphere.setTranslateX(x);
        sphere.setTranslateY(y);
        sphere.setTranslateZ(z);
    }

    private double interpolate(double from, double to, double amount) {
        return from + (to - from) * amount;
    }
}
