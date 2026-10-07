package dev.catclient2.launcher.ui;

import dev.catclient2.launcher.ui.theme.CatClientTheme;
import dev.catclient2.launcher.ui.theme.Icons;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;

public class SettingsScreen extends JPanel {
    private final JSlider memorySlider = new JSlider(1, 16, 4);
    private final JLabel memoryLabel = new JLabel("Memory: 4 GB");
    private final JLabel instanceDirLabel = new JLabel(" ");
    private final JButton openFolderButton = new JButton("Open folder");
    private final JButton verifyButton = new JButton("Verify / reinstall files");
    private final JTextField serverUrlField = new JTextField();
    private final JButton applyServerButton = new JButton("Apply");
    private final JCheckBox ownRelayCheckBox = new JCheckBox("Use my own server as a relay instead");
    private final JTextField ownRelayField = new JTextField();
    private final JButton applyRelayButton = new JButton("Apply");
    private final JButton logoutButton = new JButton("Log out");
    private final JButton backButton = new JButton("Back");
    private final JLabel footerLabel = new JLabel(" ", SwingConstants.CENTER);

    public SettingsScreen() {
        setLayout(new BorderLayout());
        setBackground(CatClientTheme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(28, 36, 20, 36));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);

        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        titleRow.setOpaque(false);
        titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleRow.add(new JLabel(Icons.settings(CatClientTheme.ACCENT, 22)));
        titleRow.add(CatClientTheme.pageTitle("Settings"));
        content.add(titleRow);
        content.add(Box.createVerticalStrut(20));

        content.add(memoryCard());
        content.add(Box.createVerticalStrut(16));
        content.add(instanceCard());
        content.add(Box.createVerticalStrut(16));
        content.add(friendServiceCard());
        content.add(Box.createVerticalStrut(16));
        content.add(relayCard());

        JScrollPane scroll = new JScrollPane(content);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        add(scroll, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.setBorder(BorderFactory.createEmptyBorder(14, 0, 0, 0));

        footerLabel.setForeground(CatClientTheme.TEXT_SECONDARY);
        footerLabel.setHorizontalAlignment(SwingConstants.LEFT);
        footer.add(footerLabel, BorderLayout.NORTH);

        logoutButton.setIcon(Icons.logout(CatClientTheme.TEXT_PRIMARY, 14));
        logoutButton.setIconTextGap(6);

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        buttonRow.setOpaque(false);
        buttonRow.add(backButton);
        buttonRow.add(logoutButton);
        footer.add(buttonRow, BorderLayout.SOUTH);

        add(footer, BorderLayout.SOUTH);
    }

    private JPanel card(JPanel inner) {
        inner.setOpaque(false);
        inner.setBorder(CatClientTheme.cardBorder());
        inner.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.add(inner, BorderLayout.CENTER);
        return wrapper;
    }

    private JPanel memoryCard() {
        JPanel inner = new JPanel();
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));

        inner.add(CatClientTheme.sectionHeading("Memory"));
        inner.add(Box.createVerticalStrut(4));
        memoryLabel.setForeground(CatClientTheme.TEXT_SECONDARY);
        memoryLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        inner.add(memoryLabel);
        inner.add(Box.createVerticalStrut(6));

        memorySlider.setOpaque(false);
        memorySlider.setAlignmentX(Component.LEFT_ALIGNMENT);
        memorySlider.setMajorTickSpacing(4);
        memorySlider.setPaintTicks(true);
        memorySlider.setPaintLabels(true);
        memorySlider.addChangeListener(e -> memoryLabel.setText("Memory: " + memorySlider.getValue() + " GB"));
        inner.add(memorySlider);

        return card(inner);
    }

    private JPanel instanceCard() {
        JPanel inner = new JPanel();
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));

        inner.add(CatClientTheme.sectionHeading("Installation"));
        inner.add(Box.createVerticalStrut(4));
        instanceDirLabel.setForeground(CatClientTheme.TEXT_SECONDARY);
        instanceDirLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        inner.add(instanceDirLabel);
        inner.add(Box.createVerticalStrut(10));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.setOpaque(false);
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttons.add(openFolderButton);
        buttons.add(verifyButton);
        inner.add(buttons);

        return card(inner);
    }

    private JPanel friendServiceCard() {
        JPanel inner = new JPanel();
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));

        inner.add(CatClientTheme.sectionHeading("Friend service"));
        inner.add(Box.createVerticalStrut(4));
        inner.add(CatClientTheme.hint("Advanced - leave as is unless you run your own."));
        inner.add(Box.createVerticalStrut(10));

        JPanel serverUrlRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        serverUrlRow.setOpaque(false);
        serverUrlRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        serverUrlField.setPreferredSize(new Dimension(320, 28));
        serverUrlRow.add(serverUrlField);
        serverUrlRow.add(applyServerButton);
        inner.add(serverUrlRow);

        return card(inner);
    }

    private JPanel relayCard() {
        JPanel inner = new JPanel();
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));

        inner.add(CatClientTheme.sectionHeading("Internet relay"));
        inner.add(Box.createVerticalStrut(4));
        inner.add(CatClientTheme.hint("Lets friends join without any port forwarding. By default this"
            + " uses Cat Client 2's own relay - only change this if you run your own Minecraft server"
            + " with the relay-forge-mod add-on installed."));
        inner.add(Box.createVerticalStrut(10));

        ownRelayCheckBox.setOpaque(false);
        ownRelayCheckBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        ownRelayCheckBox.setForeground(CatClientTheme.TEXT_PRIMARY);
        inner.add(ownRelayCheckBox);
        inner.add(Box.createVerticalStrut(6));

        JPanel relayRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        relayRow.setOpaque(false);
        relayRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        ownRelayField.setPreferredSize(new Dimension(320, 28));
        ownRelayField.setToolTipText("host:port of your Minecraft server, e.g. myserver.example.com:25565");
        relayRow.add(ownRelayField);
        relayRow.add(applyRelayButton);
        inner.add(relayRow);

        return card(inner);
    }

    public JButton getBackButton() {
        return backButton;
    }

    public JButton getOpenFolderButton() {
        return openFolderButton;
    }

    public JButton getVerifyButton() {
        return verifyButton;
    }

    public JButton getLogoutButton() {
        return logoutButton;
    }

    public JSlider getMemorySlider() {
        return memorySlider;
    }

    public JTextField getServerUrlField() {
        return serverUrlField;
    }

    public JButton getApplyServerButton() {
        return applyServerButton;
    }

    public void setServerUrl(String url) {
        serverUrlField.setText(url);
    }

    public JCheckBox getOwnRelayCheckBox() {
        return ownRelayCheckBox;
    }

    public JTextField getOwnRelayField() {
        return ownRelayField;
    }

    public JButton getApplyRelayButton() {
        return applyRelayButton;
    }

    public void setOwnRelay(boolean enabled, String hostPort) {
        ownRelayCheckBox.setSelected(enabled);
        ownRelayField.setText(hostPort);
    }

    public void setInstanceDirText(String text) {
        instanceDirLabel.setText(text);
    }

    public void setFooterText(String text) {
        footerLabel.setText(text);
    }
}
