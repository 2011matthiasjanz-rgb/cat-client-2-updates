package dev.catclient2.launcher.ui.notification;

import dev.catclient2.launcher.ui.theme.CatClientTheme;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;

/**
 * A small, undecorated, always-on-top window in the bottom-right corner of the screen, styled like a
 * Cat Client 2 notification. Used by background processes (the update-checker) that have no other UI
 * and therefore cannot rely on real Windows toast notifications, which would otherwise require either
 * registry protocol handlers or an unmaintained third-party PowerShell module - both too fragile for
 * a one-shot background process to depend on.
 */
public class ToastNotification {
    private static final int WIDTH = 340;
    private static final int MARGIN = 20;
    private static final int AUTO_DISMISS_MILLIS = 15_000;

    private final JFrame frame = new JFrame();

    private ToastNotification(String title, String message, String actionLabel, Runnable onAction) {
        frame.setUndecorated(true);
        frame.setAlwaysOnTop(true);
        frame.setType(JFrame.Type.UTILITY);
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.getContentPane().setBackground(CatClientTheme.SURFACE);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(CatClientTheme.SURFACE);
        content.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(CatClientTheme.DIVIDER, 1),
            BorderFactory.createEmptyBorder(16, 18, 16, 18)));

        JPanel titleRow = new JPanel(new java.awt.BorderLayout());
        titleRow.setOpaque(false);

        JLabel titleLabel = new JLabel(title);
        titleLabel.setForeground(CatClientTheme.ACCENT);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
        titleRow.add(titleLabel, java.awt.BorderLayout.CENTER);

        JButton closeButton = new JButton("x");
        closeButton.setFocusable(false);
        closeButton.setMargin(new java.awt.Insets(0, 6, 0, 0));
        closeButton.putClientProperty("JButton.buttonType", "borderless");
        closeButton.addActionListener(e -> dismiss());
        titleRow.add(closeButton, java.awt.BorderLayout.EAST);

        content.add(titleRow);
        content.add(Box.createVerticalStrut(8));

        JLabel messageLabel = new JLabel("<html>" + message + "</html>", SwingConstants.LEFT);
        messageLabel.setForeground(CatClientTheme.TEXT_PRIMARY);
        messageLabel.setFont(messageLabel.getFont().deriveFont(12.5f));
        content.add(messageLabel);

        if (actionLabel != null && onAction != null) {
            content.add(Box.createVerticalStrut(12));
            JButton actionButton = new JButton(actionLabel);
            actionButton.putClientProperty("JButton.buttonType", "roundRect");
            actionButton.addActionListener(e -> {
                dismiss();
                onAction.run();
            });
            JPanel buttonRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 0, 0));
            buttonRow.setOpaque(false);
            buttonRow.add(actionButton);
            content.add(buttonRow);
        }

        frame.setContentPane(content);
        frame.setSize(new Dimension(WIDTH, frame.getPreferredSize().height));
        frame.pack();
        positionBottomRight();
    }

    private void positionBottomRight() {
        Rectangle bounds = GraphicsEnvironment.getLocalGraphicsEnvironment()
            .getDefaultScreenDevice().getDefaultConfiguration().getBounds();
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int x = bounds.x + bounds.width - frame.getWidth() - MARGIN;
        int y = bounds.y + bounds.height - frame.getHeight() - MARGIN;
        frame.setLocation(new Point(x, Math.min(y, screen.height - frame.getHeight() - MARGIN)));
    }

    private void dismiss() {
        frame.dispose();
    }

    /** Shows a notification with no action button - just informational, auto-dismisses. */
    public static void show(String title, String message) {
        show(title, message, null, null);
    }

    /**
     * Shows a notification, optionally with one action button. Must be called on the Swing event
     * thread (wrap in {@code SwingUtilities.invokeLater} otherwise). Auto-dismisses after a timeout
     * if the user does not interact with it.
     */
    public static void show(String title, String message, String actionLabel, Runnable onAction) {
        ToastNotification toast = new ToastNotification(title, message, actionLabel, onAction);
        toast.frame.setVisible(true);

        Timer autoDismiss = new Timer(AUTO_DISMISS_MILLIS, e -> toast.dismiss());
        autoDismiss.setRepeats(false);
        autoDismiss.start();
    }

    /** Convenience for callers running off the Swing event thread (the usual case for a background process). */
    public static void showLater(String title, String message, String actionLabel, Runnable onAction) {
        SwingUtilities.invokeLater(() -> show(title, message, actionLabel, onAction));
    }
}
