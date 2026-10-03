package dev.catclient2.launcher.ui;

import dev.catclient2.launcher.ui.theme.CatClientTheme;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;

public class LoginScreen extends JPanel {
    private final JLabel titleLabel = new JLabel("Cat Client 2", SwingConstants.CENTER);
    private final JLabel subtitleLabel = new JLabel("Sign in with your Microsoft account to continue.", SwingConstants.CENTER);
    private final JLabel codeLabel = new JLabel(" ", SwingConstants.CENTER);
    private final JButton copyCodeButton = new JButton("Copy code");
    private final JButton loginButton = new JButton("Sign in with Microsoft");
    private final JLabel statusLabel = new JLabel(" ", SwingConstants.CENTER);

    public LoginScreen() {
        setLayout(new BorderLayout());
        setBackground(CatClientTheme.BACKGROUND);

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(CatClientTheme.SURFACE);
        card.setBorder(CatClientTheme.cardBorder());
        card.setAlignmentX(Component.CENTER_ALIGNMENT);

        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 34f));
        titleLabel.setForeground(CatClientTheme.ACCENT);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        subtitleLabel.setForeground(CatClientTheme.TEXT_SECONDARY);
        subtitleLabel.setFont(subtitleLabel.getFont().deriveFont(13f));
        subtitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        codeLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 30));
        codeLabel.setForeground(CatClientTheme.TEXT_PRIMARY);
        codeLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        codeLabel.setVisible(false);

        copyCodeButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        copyCodeButton.setVisible(false);

        loginButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        loginButton.putClientProperty("JButton.buttonType", "roundRect");
        loginButton.setFont(loginButton.getFont().deriveFont(Font.BOLD, 14f));

        statusLabel.setForeground(CatClientTheme.TEXT_SECONDARY);
        statusLabel.setFont(statusLabel.getFont().deriveFont(12.5f));
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        card.add(titleLabel);
        card.add(Box.createVerticalStrut(10));
        card.add(subtitleLabel);
        card.add(Box.createVerticalStrut(28));
        card.add(codeLabel);
        card.add(Box.createVerticalStrut(10));
        card.add(copyCodeButton);
        card.add(Box.createVerticalStrut(28));
        card.add(loginButton);
        card.add(Box.createVerticalStrut(16));
        card.add(statusLabel);

        copyCodeButton.addActionListener(e -> {
            java.awt.datatransfer.StringSelection selection = new java.awt.datatransfer.StringSelection(codeLabel.getText());
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
        });

        JPanel centerWrapper = new JPanel(new java.awt.GridBagLayout());
        centerWrapper.setBackground(CatClientTheme.BACKGROUND);
        centerWrapper.add(card);

        add(centerWrapper, BorderLayout.CENTER);
    }

    public JButton getLoginButton() {
        return loginButton;
    }

    public JButton getCopyCodeButton() {
        return copyCodeButton;
    }

    public void showDeviceCode(String userCode, String verificationUri) {
        codeLabel.setText(userCode);
        codeLabel.setVisible(true);
        copyCodeButton.setVisible(true);
        statusLabel.setText("Enter this code at " + verificationUri);
    }

    public void setStatus(String text) {
        statusLabel.setText(text);
    }
}
