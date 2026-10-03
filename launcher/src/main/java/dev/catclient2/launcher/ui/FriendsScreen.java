package dev.catclient2.launcher.ui;

import dev.catclient2.friends.protocol.AccountView;
import dev.catclient2.friends.protocol.FriendRequestView;
import dev.catclient2.friends.protocol.JoinSession;
import dev.catclient2.friends.protocol.RequestKind;
import dev.catclient2.friends.protocol.StateResponse;
import dev.catclient2.launcher.ui.theme.CatClientTheme;
import dev.catclient2.launcher.ui.theme.Icons;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.List;

/**
 * Simple friends screen like the Essential mod: add friends, see who's online,
 * click Join when a friend is in game – everything else happens automatically.
 */
public class FriendsScreen extends JPanel {
    public interface Listener {
        void addFriend(String name);
        void acceptRequest(FriendRequestView request);
        void declineRequest(FriendRequestView request);
        void cancelRequest(FriendRequestView request);
        void askToJoin(AccountView friend, JoinSession session);
        void joinNow(String requestId, AccountView host, JoinSession session);
        void publishAddress(String address);
        void refresh();
    }

    private final JLabel statusLabel = new JLabel(" ");

    private final JTextField addField = new JTextField();
    private final JButton addButton = new JButton("Add friend");
    private final JLabel hintLabel = new JLabel(" ", SwingConstants.LEFT);

    private final JTextField addressField = new JTextField();
    private final JButton publishButton = new JButton("Publish");
    private final JLabel sessionTitle = new JLabel(" ", SwingConstants.LEFT);
    private final JLabel addressHint = new JLabel(" ", SwingConstants.LEFT);

    private final JPanel content = new JPanel();
    private final JScrollPane scroll = new JScrollPane(content);

    private final JButton refreshButton = new JButton("Refresh");
    private final JButton backButton = new JButton("Back");

    private Listener listener;

    public FriendsScreen() {
        setLayout(new BorderLayout());
        setBackground(CatClientTheme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(22, 26, 16, 26));

        add(header(), BorderLayout.NORTH);

        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(12, 2, 8, 2));
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        add(scroll, BorderLayout.CENTER);

        add(footer(), BorderLayout.SOUTH);
    }

    private JComponent header() {
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);

        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        titleRow.setOpaque(false);
        titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleRow.add(new JLabel(Icons.friends(CatClientTheme.ACCENT, 22)));
        titleRow.add(CatClientTheme.pageTitle("Friends"));
        header.add(titleRow);
        header.add(Box.createVerticalStrut(6));

        statusLabel.setForeground(CatClientTheme.TEXT_SECONDARY);
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(statusLabel);
        header.add(Box.createVerticalStrut(14));

        header.add(addFriendSection());
        header.add(Box.createVerticalStrut(14));
        header.add(sessionSection());
        return header;
    }

    private JComponent addFriendSection() {
        JPanel inner = new JPanel();
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
        inner.setOpaque(false);
        inner.setBorder(CatClientTheme.cardBorder());
        inner.setAlignmentX(Component.LEFT_ALIGNMENT);

        inner.add(CatClientTheme.sectionHeading("Add a friend"));
        inner.add(Box.createVerticalStrut(10));
        inner.add(row(addField, addButton, 340));
        inner.add(Box.createVerticalStrut(4));

        hintLabel.setForeground(CatClientTheme.TEXT_SECONDARY);
        hintLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        inner.add(hintLabel);

        return inner;
    }

    /**
     * The host half of the screen: where the player's own address is published from, so friends get
     * something to connect to once a world is open.
     */
    private JComponent sessionSection() {
        JPanel inner = new JPanel();
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
        inner.setOpaque(false);
        inner.setBorder(CatClientTheme.cardBorder());
        inner.setAlignmentX(Component.LEFT_ALIGNMENT);

        inner.add(CatClientTheme.sectionHeading("Your session"));
        inner.add(Box.createVerticalStrut(6));

        sessionTitle.setForeground(CatClientTheme.TEXT_SECONDARY);
        sessionTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        inner.add(sessionTitle);
        inner.add(Box.createVerticalStrut(10));

        inner.add(row(addressField, publishButton, 340));
        inner.add(Box.createVerticalStrut(4));

        addressHint.setForeground(CatClientTheme.TEXT_SECONDARY);
        addressHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        inner.add(addressHint);

        return inner;
    }

    private JComponent row(JTextField field, JButton button, int fieldWidth) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        field.setPreferredSize(new Dimension(fieldWidth, 28));
        row.add(field);
        row.add(button);
        return row;
    }

    private JComponent footer() {
        refreshButton.setIcon(Icons.bell(CatClientTheme.TEXT_PRIMARY, 14));
        refreshButton.setIconTextGap(6);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 10));
        footer.setOpaque(false);
        footer.add(refreshButton);
        footer.add(backButton);
        return footer;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public JButton getBackButton() {
        return backButton;
    }

    public JButton getRefreshButton() {
        return refreshButton;
    }

    public JButton getAddButton() {
        return addButton;
    }

    public JTextField getAddField() {
        return addField;
    }

    public JButton getPublishButton() {
        return publishButton;
    }

    public JTextField getAddressField() {
        return addressField;
    }

    public void setAddress(String address) {
        if (!addressField.hasFocus()) addressField.setText(address);
    }

    public void setAddressHint(String text) {
        addressHint.setText(text);
    }

    public void setSessionInfo(String text) {
        sessionTitle.setText(text);
    }

    public void setStatus(String text) {
        statusLabel.setText(text);
    }

    public void setHint(String text) {
        hintLabel.setText(text);
    }

    public void clearHint() {
        hintLabel.setText(" ");
    }

    public void render(StateResponse state) {
        content.removeAll();

        // --- Friends (always on top) ---
        content.add(sectionTitle("Friends (" + state.friends().size() + ")"));
        if (state.friends().isEmpty()) {
            content.add(muted("No friends yet. Type a name above and press Add friend."));
            content.add(Box.createVerticalStrut(8));
        } else {
            for (AccountView friend : state.friends()) {
                content.add(friendRow(friend, state));
                content.add(Box.createVerticalStrut(6));
            }
        }

        // --- Requests (only when there are any) ---
        boolean hasIncoming = !state.incoming().isEmpty();
        boolean hasOutgoing = !state.outgoing().isEmpty();
        if (hasIncoming || hasOutgoing) {
            content.add(Box.createVerticalStrut(12));
            content.add(sectionTitle("Requests"));

            for (FriendRequestView request : state.incoming()) {
                content.add(incomingRow(request));
                content.add(Box.createVerticalStrut(6));
            }
            for (FriendRequestView request : state.outgoing()) {
                content.add(outgoingRow(request));
                content.add(Box.createVerticalStrut(6));
            }
        }

        // --- Accepted joins that can be started (only when not already in friends list) ---
        if (!state.acceptedJoins().isEmpty()) {
            content.add(Box.createVerticalStrut(12));
            content.add(sectionTitle("Ready to join"));
            for (FriendRequestView request : state.acceptedJoins()) {
                content.add(acceptedRow(request, state));
                content.add(Box.createVerticalStrut(6));
            }
        }

        content.add(Box.createVerticalGlue());
        scroll.getVerticalScrollBar().setValue(0);
        content.revalidate();
        content.repaint();
    }

    private JComponent friendRow(AccountView friend, StateResponse state) {
        JPanel row = card();

        final JoinSession session = sessionFor(state, friend.uuid());

        StringBuilder info = new StringBuilder();
        info.append("<html>").append(escape(friend.name())).append("<br><span style='color:#B8ADA1'>");
        if (session == null) {
            info.append("offline");
        } else {
            info.append("in game &middot; Minecraft ").append(session.minecraftVersion())
                .append(" &middot; ").append(session.loader()).append(' ').append(session.loaderVersion())
                .append(" &middot; ").append(session.modCount()).append(" mods");
            if (session.publicAddress() != null && !session.publicAddress().isBlank()
                && !session.publicAddress().equals(session.address())) {
                info.append("<br><span style='color:#6E655C'>internet: ")
                    .append(escape(session.publicAddress())).append("</span>");
            }
        }
        info.append("</span></html>");

        JLabel text = new JLabel(info.toString());
        row.add(text, BorderLayout.CENTER);

        // If friend is in game and joinable → Join button (direct install+launch)
        if (session != null && session.joinable()) {
            // Check if we already have an accepted join request for this friend
            boolean alreadyAccepted = state.acceptedJoins().stream()
                .anyMatch(r -> r.receiver().uuid().equalsIgnoreCase(friend.uuid()));
            String label = alreadyAccepted ? "Join" : "Ask to join";

            JButton join = new JButton(label);
            join.addActionListener(e -> {
                setEnabled(join, false);
                if (listener != null) {
                    if (alreadyAccepted) {
                        listener.joinNow(session.id(), friend, session);
                    } else {
                        listener.askToJoin(friend, session);
                    }
                }
            });
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
            buttons.setOpaque(false);
            buttons.add(join);
            row.add(buttons, BorderLayout.EAST);
        }
        return row;
    }

    private JComponent incomingRow(FriendRequestView request) {
        JPanel row = card();
        boolean join = request.kind() == RequestKind.JOIN;

        JLabel text = new JLabel("<html>" + escape(request.sender().name()) + (join
            ? " asks to join your game"
            : " wants to be your friend")
            + "<br><span style='color:#B8ADA1'>Accept = friends + auto-install on join</span></html>");
        row.add(text, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.setOpaque(false);
        JButton accept = new JButton("Accept");
        JButton decline = new JButton("Decline");

        accept.addActionListener(e -> {
            setEnabled(accept, false);
            setEnabled(decline, false);
            if (listener != null) listener.acceptRequest(request);
        });
        decline.addActionListener(e -> {
            setEnabled(accept, false);
            setEnabled(decline, false);
            if (listener != null) listener.declineRequest(request);
        });

        buttons.add(accept);
        buttons.add(decline);
        row.add(buttons, BorderLayout.EAST);
        return row;
    }

    private JComponent outgoingRow(FriendRequestView request) {
        JPanel row = card();
        String kind = request.kind() == RequestKind.JOIN ? "asking to join" : "wants to be friends";

        JLabel text = new JLabel("<html>Waiting for " + escape(request.receiver().name()) + "<br>"
            + "<span style='color:#B8ADA1'>you are " + kind + "</span></html>");
        row.add(text, BorderLayout.CENTER);

        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> {
            setEnabled(cancel, false);
            if (listener != null) listener.cancelRequest(request);
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.setOpaque(false);
        buttons.add(cancel);
        row.add(buttons, BorderLayout.EAST);
        return row;
    }

    private JComponent acceptedRow(FriendRequestView request, StateResponse state) {
        JPanel row = card();
        final JoinSession session = sessionFor(state, request.receiver().uuid());

        StringBuilder info = new StringBuilder("<html>").append(escape(request.receiver().name()))
            .append(" accepted – ready to join");
        if (session != null) {
            info.append("<br><span style='color:#B8ADA1'>Minecraft ").append(session.minecraftVersion())
                .append(" &middot; ").append(session.loader()).append(' ').append(session.loaderVersion())
                .append(" &middot; ").append(session.modCount()).append(" mods</span>");
        } else {
            info.append("<br><span style='color:#B8ADA1'>not in game right now</span>");
        }
        info.append("</html>");

        row.add(new JLabel(info.toString()), BorderLayout.CENTER);

        JButton join = new JButton("Join");
        join.setEnabled(session != null && session.joinable());
        if (!join.isEnabled()) join.setToolTipText("Friend is not in game yet");
        join.addActionListener(e -> {
            setEnabled(join, false);
            if (listener != null) listener.joinNow(request.id(), request.receiver(), session);
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.setOpaque(false);
        buttons.add(join);
        row.add(buttons, BorderLayout.EAST);
        return row;
    }

    /** The live session of one account, or null when that account is not in game. */
    private static JoinSession sessionFor(StateResponse state, String uuid) {
        for (JoinSession candidate : state.sessions()) {
            if (candidate.host().uuid().equalsIgnoreCase(uuid)) return candidate;
        }
        return null;
    }

    private JPanel card() {
        JPanel row = new RoundedPanel(new BorderLayout(10, 0), CatClientTheme.SURFACE, 12);
        row.setBorder(BorderFactory.createCompoundBorder(
            new RoundedBorder(CatClientTheme.DIVIDER, 12),
            BorderFactory.createEmptyBorder(10, 14, 10, 14)));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 76));
        return row;
    }

    /** A JPanel that fills itself with a rounded rectangle instead of Swing's default rectangle. */
    private static final class RoundedPanel extends JPanel {
        private final Color fill;
        private final int arc;

        RoundedPanel(java.awt.LayoutManager layout, Color fill, int arc) {
            super(layout);
            this.fill = fill;
            this.arc = arc;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(java.awt.Graphics g) {
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static final class RoundedBorder implements javax.swing.border.Border {
        private final Color color;
        private final int arc;

        RoundedBorder(Color color, int arc) {
            this.color = color;
            this.arc = arc;
        }

        @Override
        public void paintBorder(Component c, java.awt.Graphics g, int x, int y, int width, int height) {
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.drawRoundRect(x, y, width - 1, height - 1, arc, arc);
            g2.dispose();
        }

        @Override
        public java.awt.Insets getBorderInsets(Component c) {
            return new java.awt.Insets(1, 1, 1, 1);
        }

        @Override
        public boolean isBorderOpaque() {
            return false;
        }
    }

    private JComponent sectionTitle(String text) {
        JLabel label = new JLabel(text, SwingConstants.LEFT);
        label.setForeground(CatClientTheme.ACCENT);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 13.5f));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private JComponent muted(String text) {
        JLabel label = new JLabel(text, SwingConstants.LEFT);
        label.setForeground(CatClientTheme.TEXT_SECONDARY);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private static void setEnabled(JButton button, boolean enabled) {
        button.setEnabled(enabled);
        if (!enabled) button.setForeground(new Color(0x6E655C));
    }

    private static String escape(String value) {
        if (value == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '&') sb.append("&");
            else if (c == '<') sb.append("<");
            else if (c == '>') sb.append(">");
            else sb.append(c);
        }
        return sb.toString();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(720, 520);
    }
} 