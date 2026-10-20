package com.euphoriapatches.euphoria_patcher;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.net.URI;
import java.util.Locale;
import java.util.Objects;

public class GUIScreen {
    private static final String URL = "https://www.euphoriapatches.com/how-to-install/";

    private static final Color BACKGROUND_COLOR = new Color(43, 43, 43);
    private static final Color TEXT_COLOR = new Color(220, 220, 220);
    private static final Color ACCENT_COLOR = new Color(232, 69, 198);
    private static final Color BUTTON_COLOR = new Color(75, 75, 75);
    private static final Color BUTTON_HOVER_COLOR = new Color(90, 90, 90);

    public static void launch() {
        if (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("mac")) {
            System.setProperty("apple.laf.useScreenMenuBar", "true");
        }

        if (!System.getProperty("java.version").startsWith("1.8")) {
            System.setProperty("sun.java2d.uiScale", "1.0");
        }

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        JFrame dialog = new JFrame("Euphoria Patcher");
        dialog.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        dialog.setSize(350, 200);
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(null);

        ImageIcon icon = null;
        try {
            icon = new ImageIcon(Objects.requireNonNull(GUIScreen.class.getResource("/icon32x.png")));
            dialog.setIconImage(icon.getImage());
        } catch (Exception ignored) {}

        JPanel mainPanel = new JPanel(new BorderLayout(15, 15));
        mainPanel.setBackground(BACKGROUND_COLOR);
        mainPanel.setBorder(new EmptyBorder(20, 20, 5, 20));

        JPanel contentPanel = new JPanel(new BorderLayout(0, 10));
        contentPanel.setBackground(BACKGROUND_COLOR);

        if (icon != null) {
            JLabel iconLabel = new JLabel(icon);
            iconLabel.setVerticalAlignment(JLabel.TOP);
            mainPanel.add(iconLabel, BorderLayout.WEST);
        }

        JLabel message = getJLabel(dialog);
        message.setFont(new Font(Font.DIALOG, Font.PLAIN, 14));
        contentPanel.add(message, BorderLayout.CENTER);

        // Create a separate panel just for the button that spans the full width
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        bottomPanel.setBackground(BACKGROUND_COLOR);
        bottomPanel.setPreferredSize(new Dimension(0, 40));

        JButton okButton = getJButton(dialog);
        okButton.setFont(new Font(Font.DIALOG, Font.PLAIN, 14));
        bottomPanel.add(okButton);

        // Add panels to main layout
        mainPanel.add(contentPanel, BorderLayout.CENTER);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        dialog.add(mainPanel);
        dialog.setVisible(true);
    }

    private static JLabel getJLabel(JFrame dialog) {
        JLabel message = getMessage();
        message.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                try {
                    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                        Desktop.getDesktop().browse(new URI(URL));
                    } else {
                        JOptionPane.showMessageDialog(dialog,
                            "Could not open browser. Please visit: " + URL,
                            "Error", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(dialog,
                        "Could not open website. Please visit: " + URL,
                        "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        return message;
    }

    private static JLabel getMessage() {
        JLabel message = new JLabel(
            "<html>" +
            "<div style='color: rgb(220,220,220); line-height: 1.5;'>" +
            "This is a Minecraft mod!<br>" +
            "Please put it in your mods folder.<br>" +
            "<span style='color: " + String.format("rgb(%d,%d,%d)", ACCENT_COLOR.getRed(), ACCENT_COLOR.getGreen(), ACCENT_COLOR.getBlue()) + ";'><u>Click here for installation instructions</u></span>" +
            "</div>" +
            "</html>"
        );
        message.setForeground(TEXT_COLOR);

        message.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return message;
    }

    private static JButton getJButton(JFrame dialog) {
        JButton okButton = new JButton("OK") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                if (getModel().isPressed()) {
                    g2d.setColor(BUTTON_COLOR.darker());
                } else if (getModel().isRollover()) {
                    g2d.setColor(BUTTON_HOVER_COLOR);
                } else {
                    g2d.setColor(BUTTON_COLOR);
                }

                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);

                g2d.setColor(TEXT_COLOR);
                FontMetrics fm = g2d.getFontMetrics();
                int x = (getWidth() - fm.stringWidth(getText())) / 2;
                int y = ((getHeight() - fm.getHeight()) / 2) + fm.getAscent();
                g2d.drawString(getText(), x, y);

                g2d.dispose();
            }
        };

        okButton.setPreferredSize(new Dimension(80, 30));
        okButton.setForeground(TEXT_COLOR);
        okButton.setBackground(BUTTON_COLOR);
        okButton.setFocusPainted(false);
        okButton.setBorderPainted(false);
        okButton.setContentAreaFilled(false);

        okButton.addActionListener(e -> dialog.dispose());
        return okButton;
    }
}
