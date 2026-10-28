package se.hirt.bubb;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    public static void main(String[] args) {
        configureNativeAwtRuntime();

        try {
            // Set System L&F
            UIManager.setLookAndFeel(
                    UIManager.getSystemLookAndFeelClassName());
        }
        catch (UnsupportedLookAndFeelException e) {
            // handle exception
        }
        catch (ClassNotFoundException e) {
            // handle exception
        }
        catch (InstantiationException e) {
            // handle exception
        }
        catch (IllegalAccessException e) {
            // handle exception
        }

        JFrame frame = new JFrame("Test");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600);
        frame.setResizable(true);
        frame.getContentPane().setLayout(new BorderLayout(5, 5));
        frame.getContentPane().add(BorderLayout.NORTH, getMyStuff(frame));
        frame.setVisible(true);
    }

    private static void configureNativeAwtRuntime() {
        if (System.getProperty("java.home") != null) {
            return;
        }

        String graalvmHome = System.getenv("GRAALVM_HOME");
        if (graalvmHome == null || graalvmHome.isBlank()) {
            return;
        }

        Path home = Path.of(graalvmHome).toAbsolutePath().normalize();
        if (home.getFileName() != null && home.getFileName().toString().equalsIgnoreCase("bin")) {
            home = home.getParent();
        }
        if (home != null) {
            System.setProperty("java.home", home.toString());
        }
    }

    private static Component getMyStuff(JFrame frame) {
        JPanel panel = new JPanel();
        UIManager.LookAndFeelInfo[] installedLookAndFeels = UIManager.getInstalledLookAndFeels();
        panel.setLayout(new GridLayout(installedLookAndFeels.length,1));
        for (UIManager.LookAndFeelInfo info : installedLookAndFeels) {
            panel.add(createLookAndFeelPanel(frame, info));
        }
        return panel;
    }

    private static Component createLookAndFeelPanel(final JFrame frame, UIManager.LookAndFeelInfo info) {
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout(5,5));
        panel.add(new JLabel(info.getName()), BorderLayout.WEST);
        JButton button = new JButton("Switch!");
        button.addActionListener(e -> {
            try {
                UIManager.setLookAndFeel(info.getClassName());
            } catch (ClassNotFoundException ex) {
                throw new RuntimeException(ex);
            } catch (InstantiationException ex) {
                throw new RuntimeException(ex);
            } catch (IllegalAccessException ex) {
                throw new RuntimeException(ex);
            } catch (UnsupportedLookAndFeelException ex) {
                throw new RuntimeException(ex);
            }
            SwingUtilities.updateComponentTreeUI(frame);
        });
        panel.add(button, BorderLayout.CENTER);
        return panel;
    }

    private static Component createButtonPanel(int i) {
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout(5,5));
        panel.add(new JLabel("Button " + i), BorderLayout.WEST);
        panel.add(new JButton("Click me!"), BorderLayout.CENTER);
        return panel;
    }
}
