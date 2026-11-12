package se.hirt.bubb.worm;

import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SubScene;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.Sphere;
import javafx.scene.transform.Rotate;

/** Builds and updates the worm's 3D appearance and customization preview. */
final class WormAppearance {
    private WormAppearance() { }

    static Materials materialsFor(String colorId) {
        ColorOption color = findColor(colorId);
        return new Materials(
                material(color.color, color.color.brighter()),
                material(color.color.interpolate(Color.WHITE, 0.17), Color.WHITE));
    }

    static void populateHat(Group target, String hatId) {
        target.getChildren().clear();
        switch (hatId) {
            case "cap" -> {
                Sphere cap = new Sphere(0.25, 20);
                cap.setTranslateY(-0.48);
                cap.setMaterial(material(Color.web("#e95c52"), Color.web("#ffc1a8")));
                Box brim = new Box(0.55, 0.08, 0.36);
                brim.setTranslateY(-0.36);
                brim.setMaterial(cap.getMaterial());
                target.getChildren().addAll(cap, brim);
            }
            case "crown" -> {
                PhongMaterial gold = material(Color.web("#ffc83d"), Color.WHITE);
                Box base = new Box(0.46, 0.16, 0.40);
                base.setTranslateY(-0.48);
                base.setMaterial(gold);
                target.getChildren().add(base);
                for (double x : new double[]{-0.16, 0, 0.16}) {
                    Sphere point = new Sphere(0.10, 16);
                    point.setTranslateX(x);
                    point.setTranslateY(-0.61);
                    point.setMaterial(gold);
                    target.getChildren().add(point);
                }
            }
            case "antenna" -> {
                javafx.scene.shape.Cylinder stem = new javafx.scene.shape.Cylinder(0.045, 0.34, 12);
                stem.setTranslateY(-0.57);
                stem.setMaterial(material(Color.web("#d9e4ff"), Color.WHITE));
                Sphere ball = new Sphere(0.13, 18);
                ball.setTranslateY(-0.79);
                ball.setMaterial(material(Color.web("#ff79cf"), Color.WHITE));
                target.getChildren().addAll(stem, ball);
            }
            case "wizard" -> {
                PhongMaterial purple = material(Color.web("#8050c9"), Color.web("#d9c4ff"));
                javafx.scene.shape.Cylinder crown = new javafx.scene.shape.Cylinder(0.20, 0.40, 20);
                crown.setTranslateY(-0.57);
                crown.setMaterial(purple);
                javafx.scene.shape.Cylinder brim = new javafx.scene.shape.Cylinder(0.31, 0.07, 24);
                brim.setTranslateY(-0.36);
                brim.setMaterial(purple);
                Sphere star = new Sphere(0.07, 12);
                star.setTranslateX(0.12);
                star.setTranslateY(-0.72);
                star.setMaterial(material(Color.web("#ffe46b"), Color.WHITE));
                target.getChildren().addAll(crown, brim, star);
            }
            case "pirate" -> {
                PhongMaterial black = material(Color.web("#252333"), Color.web("#9b91ad"));
                Box bandana = new Box(0.54, 0.12, 0.40);
                bandana.setTranslateY(-0.42);
                bandana.setMaterial(black);
                Sphere knot = new Sphere(0.11, 14);
                knot.setTranslateX(0.28);
                knot.setTranslateY(-0.39);
                knot.setMaterial(material(Color.web("#d94b57"), Color.WHITE));
                Box skull = new Box(0.25, 0.13, 0.08);
                skull.setTranslateY(-0.50);
                skull.setTranslateZ(-0.22);
                skull.setMaterial(material(Color.web("#f4e4c1"), Color.WHITE));
                target.getChildren().addAll(bandana, knot, skull);
            }
            case "flower" -> {
                PhongMaterial petals = material(Color.web("#ff8bbd"), Color.WHITE);
                for (int i = 0; i < 5; i++) {
                    double angle = Math.PI * 2 * i / 5;
                    Sphere petal = new Sphere(0.12, 14);
                    petal.setTranslateX(Math.cos(angle) * 0.15);
                    petal.setTranslateY(-0.47);
                    petal.setTranslateZ(Math.sin(angle) * 0.15);
                    petal.setMaterial(petals);
                    target.getChildren().add(petal);
                }
                Sphere center = new Sphere(0.10, 14);
                center.setTranslateY(-0.49);
                center.setMaterial(material(Color.web("#ffd84a"), Color.WHITE));
                target.getChildren().add(center);
            }
            default -> { }
        }
    }

    static SubScene createPreview(String colorId, String hatId) {
        Group previewWorld = new Group();
        ColorOption color = findColor(colorId);
        Sphere head = new Sphere(0.52, 24);
        head.setTranslateX(0.75);
        head.setTranslateY(-0.08);
        head.setMaterial(material(color.color.interpolate(Color.WHITE, 0.17), Color.WHITE));
        previewWorld.getChildren().add(head);

        PhongMaterial bodyMaterial = material(color.color, color.color.brighter());
        for (int i = 0; i < 4; i++) {
            Sphere segment = new Sphere(0.43, 20);
            segment.setTranslateX(0.05 - i * 0.72);
            segment.setTranslateY(-0.03);
            segment.setMaterial(bodyMaterial);
            previewWorld.getChildren().add(segment);
        }
        addEyes(previewWorld);

        Group hat = new Group();
        hat.setTranslateX(0.75);
        hat.setTranslateY(-0.08);
        populateHat(hat, hatId);
        previewWorld.getChildren().addAll(hat, new javafx.scene.AmbientLight(Color.color(0.72, 0.75, 0.9)));

        javafx.scene.PointLight light = new javafx.scene.PointLight(Color.WHITE);
        light.setTranslateX(-2);
        light.setTranslateY(-4);
        light.setTranslateZ(-5);
        previewWorld.getChildren().add(light);

        PerspectiveCamera camera = new PerspectiveCamera(true);
        camera.setNearClip(0.1);
        camera.setFarClip(50);
        camera.setFieldOfView(34);
        camera.getTransforms().add(new Rotate(-16, Rotate.X_AXIS));
        camera.setTranslateX(-0.25);
        camera.setTranslateY(-2.2);
        camera.setTranslateZ(-8.5);

        SubScene preview = new SubScene(previewWorld, 360, 190, true, SceneAntialiasing.BALANCED);
        preview.setFill(Color.web("#10172a"));
        preview.setCamera(camera);
        return preview;
    }

    private static void addEyes(Group world) {
        for (double side : new double[]{-0.20, 0.20}) {
            Sphere eye = new Sphere(0.20, 18);
            eye.setTranslateX(1.03);
            eye.setTranslateY(-0.20);
            eye.setTranslateZ(side);
            eye.setMaterial(material(Color.WHITE, Color.WHITE));
            Sphere pupil = new Sphere(0.09, 16);
            pupil.setTranslateX(1.17);
            pupil.setTranslateY(-0.27);
            pupil.setTranslateZ(side);
            pupil.setMaterial(material(Color.web("#17233d"), Color.web("#8cb9ff")));
            world.getChildren().addAll(eye, pupil);
        }
    }

    private static ColorOption findColor(String id) {
        return CustomizationCatalog.COLORS.stream()
                .filter(color -> color.id.equals(id))
                .findFirst()
                .orElse(CustomizationCatalog.COLORS.getFirst());
    }

    static PhongMaterial material(Color color, Color specular) {
        PhongMaterial material = new PhongMaterial(color);
        material.setSpecularColor(specular);
        material.setSpecularPower(32);
        return material;
    }

    static final class Materials {
        final PhongMaterial body;
        final PhongMaterial head;

        private Materials(PhongMaterial body, PhongMaterial head) {
            this.body = body;
            this.head = head;
        }
    }
}
