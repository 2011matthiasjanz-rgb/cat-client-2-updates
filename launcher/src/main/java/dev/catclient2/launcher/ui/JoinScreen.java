package dev.catclient2.launcher.ui;

import dev.catclient2.friends.protocol.JoinSession;
import dev.catclient2.friends.protocol.ModRequirement;
import dev.catclient2.launcher.ui.theme.CatClientTheme;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shows what has to happen before the joiner is in the host's world: which Minecraft version and
 * mod loader are used, and one line per mod with the version that is being installed. This is the
 * screen the "all mods are installed automatically" promise is made visible on.
 */
public class JoinScreen extends JPanel {
    private final JLabel titleLabel = new JLabel("Joining", SwingConstants.CENTER);
    private final JLabel detailLabel = new JLabel(" ", SwingConstants.CENTER);
    private final JLabel statusLabel = new JLabel(" ", SwingConstants.CENTER);
    private final JButton retryButton = new JButton("Try again");
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private final DefaultListModel<String> modListModel = new DefaultListModel<>();
    private final JList<String> modList = new JList<>(modListModel);
    private final Map<String, Integer> modRows = new HashMap<>();
    private final List<String> modLabels = new ArrayList<>();

    public JoinScreen() {
        setLayout(new BorderLayout());
        setBackground(CatClientTheme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(24, 40, 24, 40));

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(0, 0, 16, 0));

        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 24f));
        titleLabel.setForeground(CatClientTheme.ACCENT);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        header.add(titleLabel);
        header.add(Box.createVerticalStrut(8));

        detailLabel.setForeground(CatClientTheme.TEXT_SECONDARY);
        detailLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        header.add(detailLabel);
        add(header, BorderLayout.NORTH);

        modList.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        modList.setBackground(CatClientTheme.SURFACE);
        modList.setForeground(CatClientTheme.TEXT_PRIMARY);
        modList.setFixedCellHeight(22);
        modList.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        modList.setSelectionBackground(CatClientTheme.SURFACE_ALT);

        JScrollPane scroll = new JScrollPane(modList);
        scroll.setBorder(CatClientTheme.cardBorder());
        scroll.getViewport().setBackground(CatClientTheme.SURFACE);
        add(scroll, BorderLayout.CENTER);

        JPanel footer = new JPanel();
        footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));
        footer.setOpaque(false);
        footer.add(Box.createVerticalStrut(16));

        progressBar.setAlignmentX(Component.CENTER_ALIGNMENT);
        progressBar.setPreferredSize(new Dimension(460, 10));
        progressBar.setMaximumSize(new Dimension(460, 10));
        progressBar.setStringPainted(false);
        footer.add(progressBar);
        footer.add(Box.createVerticalStrut(10));

        statusLabel.setForeground(CatClientTheme.TEXT_PRIMARY);
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        footer.add(statusLabel);
        footer.add(Box.createVerticalStrut(10));

        retryButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        retryButton.setVisible(false);
        footer.add(retryButton);
        add(footer, BorderLayout.SOUTH);
    }

    public JButton getRetryButton() {
        return retryButton;
    }

    /** Only shown when the install failed, so the join can be retried without new requests. */
    public void setRetryVisible(boolean visible) {
        retryButton.setVisible(visible);
    }

    /** Fills in the header and the checklist before the install starts. */
    public void setSession(JoinSession session) {
        titleLabel.setText("Joining " + session.host().name());

        StringBuilder detail = new StringBuilder();
        detail.append("Minecraft ").append(session.minecraftVersion())
            .append(" &middot; ").append(session.loader());
        if (session.loaderVersion() != null && !session.loaderVersion().isBlank()) {
            detail.append(' ').append(session.loaderVersion());
        }
        if (session.address() != null && !session.address().isBlank()) {
            detail.append(" &middot; ").append(session.address());
            if (session.port() > 0) detail.append(':').append(session.port());
        }
        if (session.publicAddress() != null && !session.publicAddress().isBlank()
            && !session.publicAddress().equals(session.address())) {
            detail.append("<br><span style='color:#B8ADA1'>internet: ")
                .append(session.publicAddress());
            if (session.publicPort() > 0) detail.append(':').append(session.publicPort());
            detail.append("</span>");
        }
        detailLabel.setText("<html>" + detail + "</html>");

        modListModel.clear();
        modRows.clear();
        modLabels.clear();
        List<ModRequirement> mods = session.mods();
        if (mods == null || mods.isEmpty()) {
            modListModel.addElement("(this session has no extra mods)");
            return;
        }

        int row = 0;
        for (ModRequirement requirement : mods) {
            String name = requirement.name() == null || requirement.name().isBlank()
                ? requirement.modId()
                : requirement.name();
            String label = name + " " + requirement.version();
            modListModel.addElement("... " + label);
            modLabels.add(label);
            modRows.put(requirement.modId(), row++);
        }
    }

    public void setStatus(String text) {
        statusLabel.setText(text);
    }

    public void setProgress(int percent) {
        progressBar.setIndeterminate(percent < 0);
        if (percent >= 0) progressBar.setValue(percent);
    }

    /**
     * Rewrites the checklist line of a single mod.
     *
     * @param symbol what happened, for example "OK", "==" (already there) or "!!" (failed)
     * @param state  the detail shown after the mod name and version
     */
    public void setModState(String modId, String symbol, String state) {
        Integer row = modRows.get(modId);
        if (row == null || row >= modListModel.size()) {
            modListModel.addElement(symbol + " " + modId + " - " + state);
            return;
        }

        modListModel.set(row, symbol + " " + modLabels.get(row) + " - " + state);
        modList.ensureIndexIsVisible(row);
    }
}
