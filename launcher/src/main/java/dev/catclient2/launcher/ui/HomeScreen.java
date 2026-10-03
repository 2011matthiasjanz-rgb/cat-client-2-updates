package dev.catclient2.launcher.ui;

import dev.catclient2.launcher.home.NewsItem;
import dev.catclient2.launcher.home.QuickServer;
import dev.catclient2.launcher.ui.skin.SkinPreviewPanel;
import dev.catclient2.launcher.ui.theme.CatClientTheme;
import dev.catclient2.launcher.ui.theme.Icons;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.function.Consumer;

public class HomeScreen extends JPanel {
    private final JLabel accountLabel = new JLabel(" ");
    private final JButton logoutButton = new JButton("Log out");
    private final SkinPreviewPanel skinPreview = new SkinPreviewPanel();
    private final JButton playButton = new JButton("PLAY");
    private final JButton settingsButton = new JButton("Settings");
    private final JButton friendsButton = new JButton("Friends");
    private final JButton cosmeticsButton = new JButton("Cosmetics");
    private final JLabel versionLabel = new JLabel(" ", SwingConstants.CENTER);
    private final JLabel noticeLabel = new JLabel(" ", SwingConstants.CENTER);

    private final JPanel newsList = new JPanel();
    private final JPanel serverList = new JPanel();
    private final JTextField newServerName = new JTextField();
    private final JTextField newServerAddress = new JTextField();
    private final JButton addServerButton = new JButton("Add");

    private final JPanel updateBannerWrapper = new JPanel(new BorderLayout());
    private final JPanel updateBannerContent = new JPanel(new BorderLayout(10, 0));
    private final JLabel updateLabel = new JLabel(" ");
    private final JButton updateButton = new JButton("Update");

    private Consumer<QuickServer> quickConnectListener;
    private Consumer<QuickServer> removeServerListener;

    public HomeScreen() {
        setLayout(new BorderLayout());
        setBackground(CatClientTheme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(22, 26, 22, 26));

        JPanel north = new JPanel(new BorderLayout());
        north.setOpaque(false);
        north.add(topBar(), BorderLayout.NORTH);
        north.add(updateBanner(), BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);

        JPanel columns = new JPanel(new GridLayout(1, 2, 24, 0));
        columns.setOpaque(false);
        columns.add(playColumn());
        columns.add(sideColumn());
        add(columns, BorderLayout.CENTER);

        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        bottomBar.setOpaque(false);
        bottomBar.setBorder(BorderFactory.createEmptyBorder(14, 0, 0, 0));
        bottomBar.add(iconButton(friendsButton, Icons.friends(CatClientTheme.TEXT_PRIMARY, 16)));
        bottomBar.add(iconButton(cosmeticsButton, Icons.cosmetics(CatClientTheme.TEXT_PRIMARY, 16)));
        bottomBar.add(iconButton(settingsButton, Icons.settings(CatClientTheme.TEXT_PRIMARY, 16)));
        add(bottomBar, BorderLayout.SOUTH);
    }

    private JPanel topBar() {
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        topBar.setOpaque(false);
        accountLabel.setForeground(CatClientTheme.TEXT_PRIMARY);
        accountLabel.setFont(accountLabel.getFont().deriveFont(Font.BOLD, 13f));
        topBar.add(accountLabel);
        topBar.add(iconButton(logoutButton, Icons.logout(CatClientTheme.TEXT_PRIMARY, 16)));
        return topBar;
    }

    private JPanel updateBanner() {
        updateBannerContent.setOpaque(true);
        updateBannerContent.setBackground(CatClientTheme.SURFACE_ALT);
        updateBannerContent.setBorder(BorderFactory.createCompoundBorder(
            CatClientTheme.cardBorder(), BorderFactory.createEmptyBorder(8, 14, 8, 14)));

        updateLabel.setForeground(CatClientTheme.TEXT_PRIMARY);
        updateLabel.setFont(updateLabel.getFont().deriveFont(Font.BOLD, 12.5f));
        updateBannerContent.add(updateLabel, BorderLayout.CENTER);

        updateButton.putClientProperty("JButton.buttonType", "roundRect");
        updateBannerContent.add(updateButton, BorderLayout.EAST);

        updateBannerWrapper.setOpaque(false);
        updateBannerWrapper.setBorder(BorderFactory.createEmptyBorder(0, 0, 14, 0));
        updateBannerWrapper.add(updateBannerContent, BorderLayout.CENTER);
        updateBannerWrapper.setVisible(false);
        return updateBannerWrapper;
    }

    private JPanel playColumn() {
        playButton.setFont(playButton.getFont().deriveFont(Font.BOLD, 26f));
        playButton.setPreferredSize(new Dimension(220, 64));
        playButton.setMaximumSize(new Dimension(220, 64));
        playButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        playButton.putClientProperty("JButton.buttonType", "roundRect");
        playButton.setIcon(Icons.play(CatClientTheme.BACKGROUND, 18));
        playButton.setIconTextGap(10);

        skinPreview.setPreferredSize(new Dimension(160, 200));
        skinPreview.setMaximumSize(new Dimension(160, 200));
        skinPreview.setAlignmentX(Component.CENTER_ALIGNMENT);

        versionLabel.setForeground(CatClientTheme.TEXT_SECONDARY);
        versionLabel.setFont(versionLabel.getFont().deriveFont(12f));
        versionLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        noticeLabel.setForeground(CatClientTheme.ACCENT);
        noticeLabel.setFont(noticeLabel.getFont().deriveFont(Font.BOLD, 12.5f));
        noticeLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel centerWrapper = new JPanel(new GridBagLayout());
        centerWrapper.setOpaque(false);
        JPanel playColumn = new JPanel();
        playColumn.setLayout(new BoxLayout(playColumn, BoxLayout.Y_AXIS));
        playColumn.setOpaque(false);
        playColumn.add(skinPreview);
        playColumn.add(Box.createVerticalStrut(6));
        playColumn.add(dragHint());
        playColumn.add(Box.createVerticalStrut(12));
        playColumn.add(playButton);
        playColumn.add(Box.createVerticalStrut(14));
        playColumn.add(versionLabel);
        playColumn.add(Box.createVerticalStrut(8));
        playColumn.add(noticeLabel);
        centerWrapper.add(playColumn);
        return centerWrapper;
    }

    private JPanel sideColumn() {
        JPanel column = new JPanel();
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        column.setOpaque(false);

        column.add(newsCard());
        column.add(Box.createVerticalStrut(16));
        column.add(serverCard());
        column.add(Box.createVerticalGlue());
        return column;
    }

    private JPanel newsCard() {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setOpaque(false);
        card.setBorder(CatClientTheme.cardBorder());
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel heading = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        heading.setOpaque(false);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        heading.add(new JLabel(Icons.bell(CatClientTheme.ACCENT, 16)));
        heading.add(CatClientTheme.sectionHeading("What's new"));
        card.add(heading);
        card.add(Box.createVerticalStrut(10));

        newsList.setLayout(new BoxLayout(newsList, BoxLayout.Y_AXIS));
        newsList.setOpaque(false);
        newsList.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(newsList);

        return card;
    }

    private JPanel serverCard() {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setOpaque(false);
        card.setBorder(CatClientTheme.cardBorder());
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel heading = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        heading.setOpaque(false);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        heading.add(new JLabel(Icons.server(CatClientTheme.ACCENT, 16)));
        heading.add(CatClientTheme.sectionHeading("Quick connect"));
        card.add(heading);
        card.add(Box.createVerticalStrut(10));

        serverList.setLayout(new BoxLayout(serverList, BoxLayout.Y_AXIS));
        serverList.setOpaque(false);
        serverList.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(serverList);
        card.add(Box.createVerticalStrut(8));

        JPanel addRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        addRow.setOpaque(false);
        addRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        newServerName.setPreferredSize(new Dimension(90, 26));
        newServerAddress.setPreferredSize(new Dimension(130, 26));
        addRow.add(newServerName);
        addRow.add(newServerAddress);
        addRow.add(addServerButton);
        card.add(addRow);

        return card;
    }

    private JLabel dragHint() {
        JLabel hint = new JLabel("drag to rotate", SwingConstants.CENTER);
        hint.setForeground(CatClientTheme.TEXT_SECONDARY);
        hint.setFont(hint.getFont().deriveFont(10.5f));
        hint.setAlignmentX(Component.CENTER_ALIGNMENT);
        return hint;
    }

    private JButton iconButton(JButton button, Icon icon) {
        button.setIcon(icon);
        button.setIconTextGap(8);
        return button;
    }

    public JButton getFriendsButton() {
        return friendsButton;
    }

    public JButton getCosmeticsButton() {
        return cosmeticsButton;
    }

    /** Shown on the home screen, for example "2 requests waiting". */
    public void setNotice(String text) {
        noticeLabel.setText(text);
    }

    public JButton getPlayButton() {
        return playButton;
    }

    public JButton getSettingsButton() {
        return settingsButton;
    }

    public JButton getLogoutButton() {
        return logoutButton;
    }

    public JTextField getNewServerNameField() {
        return newServerName;
    }

    public JTextField getNewServerAddressField() {
        return newServerAddress;
    }

    public JButton getAddServerButton() {
        return addServerButton;
    }

    public void setQuickConnectListener(Consumer<QuickServer> listener) {
        this.quickConnectListener = listener;
    }

    public void setRemoveServerListener(Consumer<QuickServer> listener) {
        this.removeServerListener = listener;
    }

    public void setAccountName(String name) {
        accountLabel.setText(name);
    }

    /** Shown above the PLAY button as a rotatable 3D preview; pass null to clear it. */
    public void setSkin(BufferedImage skin, boolean slim) {
        skinPreview.setSkin(skin, slim);
    }

    public void setVersionText(String text) {
        versionLabel.setText(text);
    }

    public void setFriendsBadge(int pending) {
        friendsButton.setText(pending > 0 ? "Friends (" + pending + ")" : "Friends");
    }

    /** Shows a dismiss-free "update available" banner with a clickable action button. */
    public void showUpdateAvailable(String version, Runnable onInstallClicked) {
        updateLabel.setText("Cat Client 2 " + version + " is available");
        for (var listener : updateButton.getActionListeners()) updateButton.removeActionListener(listener);
        updateButton.addActionListener(e -> onInstallClicked.run());
        updateButton.setEnabled(true);
        updateButton.setText("Update");
        updateBannerWrapper.setVisible(true);
    }

    public void setUpdateStatus(String text) {
        updateLabel.setText(text);
    }

    public void setUpdateButtonEnabled(boolean enabled) {
        updateButton.setEnabled(enabled);
    }

    public void hideUpdateBanner() {
        updateBannerWrapper.setVisible(false);
    }

    public void setNews(List<NewsItem> items) {
        newsList.removeAll();
        if (items.isEmpty()) {
            newsList.add(CatClientTheme.hint("Nothing new right now."));
        } else {
            for (int i = 0; i < items.size(); i++) {
                NewsItem item = items.get(i);
                JLabel title = new JLabel(item.title());
                title.setForeground(CatClientTheme.TEXT_PRIMARY);
                title.setFont(title.getFont().deriveFont(Font.BOLD, 13f));
                title.setAlignmentX(Component.LEFT_ALIGNMENT);
                newsList.add(title);
                newsList.add(Box.createVerticalStrut(2));
                newsList.add(CatClientTheme.hint(item.body()));
                if (i < items.size() - 1) newsList.add(Box.createVerticalStrut(10));
            }
        }
        newsList.revalidate();
        newsList.repaint();
    }

    public void setServers(List<QuickServer> servers) {
        serverList.removeAll();
        if (servers.isEmpty()) {
            serverList.add(CatClientTheme.hint("No servers yet - add one below."));
        } else {
            for (QuickServer server : servers) {
                serverList.add(serverRow(server));
                serverList.add(Box.createVerticalStrut(6));
            }
        }
        serverList.revalidate();
        serverList.repaint();
    }

    private JPanel serverRow(QuickServer server) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));

        JLabel label = new JLabel(server.name() + "  " + server.address());
        label.setForeground(CatClientTheme.TEXT_SECONDARY);
        row.add(label, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        buttons.setOpaque(false);
        JButton connect = new JButton("Connect");
        connect.addActionListener(e -> {
            if (quickConnectListener != null) quickConnectListener.accept(server);
        });
        JButton remove = new JButton("x");
        remove.setToolTipText("Remove");
        remove.addActionListener(e -> {
            if (removeServerListener != null) removeServerListener.accept(server);
        });
        buttons.add(connect);
        buttons.add(remove);
        row.add(buttons, BorderLayout.EAST);
        return row;
    }
}
