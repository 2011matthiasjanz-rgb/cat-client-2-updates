package dev.catclient2.launcher.ui.theme;

import com.formdev.flatlaf.FlatDarkLaf;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.Border;
import java.awt.Color;
import java.awt.Font;

public class CatClientTheme {
    public static final Color BACKGROUND = new Color(0x1A1714);
    public static final Color SURFACE = new Color(0x27221E);
    public static final Color SURFACE_ALT = new Color(0x302A25);
    public static final Color ACCENT = new Color(0xFFA94D);
    public static final Color ACCENT_HOVER = new Color(0xFFC078);
    public static final Color ACCENT_PRESSED = new Color(0xE8934A);
    public static final Color TEXT_PRIMARY = new Color(0xF5EDE4);
    public static final Color TEXT_SECONDARY = new Color(0xAA9F92);
    public static final Color SUCCESS = new Color(0x8FCB7A);
    public static final Color ERROR = new Color(0xE8615A);
    public static final Color DIVIDER = new Color(0x3D362F);

    private CatClientTheme() {
    }

    public static void install() {
        UIManager.put("Component.accentColor", ACCENT);
        UIManager.put("Component.focusColor", ACCENT);
        UIManager.put("Component.borderColor", DIVIDER);
        UIManager.put("Component.focusedBorderColor", ACCENT);
        UIManager.put("Button.default.background", ACCENT);
        UIManager.put("Button.default.hoverBackground", ACCENT_HOVER);
        UIManager.put("Button.default.pressedBackground", ACCENT_PRESSED);
        UIManager.put("Button.default.foreground", BACKGROUND);
        UIManager.put("Button.background", SURFACE_ALT);
        UIManager.put("Button.hoverBackground", DIVIDER);
        UIManager.put("ProgressBar.foreground", ACCENT);
        UIManager.put("ProgressBar.selectionForeground", BACKGROUND);
        UIManager.put("ProgressBar.arc", 999);
        UIManager.put("ScrollBar.thumb", SURFACE_ALT);
        UIManager.put("ScrollBar.track", BACKGROUND);
        UIManager.put("ScrollBar.width", 10);
        UIManager.put("TabbedPane.selectedBackground", SURFACE_ALT);
        UIManager.put("Component.arc", 14);
        UIManager.put("Button.arc", 14);
        UIManager.put("TextComponent.arc", 14);
        UIManager.put("ScrollBar.thumbArc", 999);

        UIManager.put("@background", String.format("#%06X", BACKGROUND.getRGB() & 0xFFFFFF));
        UIManager.put("@foreground", String.format("#%06X", TEXT_PRIMARY.getRGB() & 0xFFFFFF));

        FlatDarkLaf.setup();
    }

    /** Rounded, slightly lighter panel used to group related controls, consistent across screens. */
    public static Border cardBorder() {
        return BorderFactory.createCompoundBorder(
            new RoundedLineBorder(DIVIDER, 16),
            BorderFactory.createEmptyBorder(20, 24, 20, 24));
    }

    /** A left-aligned section heading, used above every group of controls. */
    public static JLabel sectionHeading(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(TEXT_PRIMARY);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 15f));
        label.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        return label;
    }

    /** A dim helper/description line, used under section headings and next to fields. */
    public static JLabel hint(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(TEXT_SECONDARY);
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 12.5f));
        label.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        return label;
    }

    public static JLabel pageTitle(String text) {
        JLabel label = new JLabel(text, SwingConstants.LEFT);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 24f));
        label.setForeground(ACCENT);
        label.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        return label;
    }

    private static final class RoundedLineBorder implements Border {
        private final Color color;
        private final int arc;

        RoundedLineBorder(Color color, int arc) {
            this.color = color;
            this.arc = arc;
        }

        @Override
        public void paintBorder(java.awt.Component c, java.awt.Graphics g, int x, int y, int width, int height) {
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.drawRoundRect(x, y, width - 1, height - 1, arc, arc);
            g2.dispose();
        }

        @Override
        public java.awt.Insets getBorderInsets(java.awt.Component c) {
            return new java.awt.Insets(1, 1, 1, 1);
        }

        @Override
        public boolean isBorderOpaque() {
            return false;
        }
    }
}
