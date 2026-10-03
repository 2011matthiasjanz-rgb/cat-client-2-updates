package dev.catclient2.launcher.ui;

import dev.catclient2.launcher.ui.theme.CatClientTheme;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagLayout;

public class ProgressScreen extends JPanel {
    private final JLabel statusLabel = new JLabel("Preparing...", SwingConstants.CENTER);
    private final JProgressBar progressBar = new JProgressBar(0, 100);

    public ProgressScreen() {
        setLayout(new GridBagLayout());
        setBackground(CatClientTheme.BACKGROUND);

        JPanel column = new JPanel();
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        column.setOpaque(false);

        JLabel title = new JLabel("Cat Client 2", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 26f));
        title.setForeground(CatClientTheme.ACCENT);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        statusLabel.setForeground(CatClientTheme.TEXT_PRIMARY);
        statusLabel.setFont(statusLabel.getFont().deriveFont(13.5f));
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        progressBar.setAlignmentX(Component.CENTER_ALIGNMENT);
        progressBar.setPreferredSize(new Dimension(420, 10));
        progressBar.setMaximumSize(new Dimension(420, 10));
        progressBar.setStringPainted(false);

        column.add(title);
        column.add(Box.createVerticalStrut(36));
        column.add(progressBar);
        column.add(Box.createVerticalStrut(16));
        column.add(statusLabel);

        add(column);
    }

    public void setStatus(String text) {
        statusLabel.setText(text);
    }

    public void setProgress(int percent) {
        progressBar.setIndeterminate(percent < 0);
        if (percent >= 0) progressBar.setValue(percent);
    }
}
