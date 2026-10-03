package dev.catclient2.launcher.ui;

import java.awt.CardLayout;
import java.awt.Dimension;
import javax.swing.JFrame;

public class LauncherFrame extends JFrame {
    public static final String CARD_LOGIN = "login";
    public static final String CARD_HOME = "home";
    public static final String CARD_SETTINGS = "settings";
    public static final String CARD_PROGRESS = "progress";
    public static final String CARD_FRIENDS = "friends";
    public static final String CARD_JOIN = "join";
    public static final String CARD_COSMETICS = "cosmetics";

    private final CardLayout cardLayout = new CardLayout();
    private final javax.swing.JPanel cards = new javax.swing.JPanel(cardLayout);
    private String currentCard = CARD_LOGIN;

    private final LoginScreen loginScreen = new LoginScreen();
    private final HomeScreen homeScreen = new HomeScreen();
    private final SettingsScreen settingsScreen = new SettingsScreen();
    private final ProgressScreen progressScreen = new ProgressScreen();
    private final FriendsScreen friendsScreen = new FriendsScreen();
    private final JoinScreen joinScreen = new JoinScreen();
    private final CosmeticsScreen cosmeticsScreen = new CosmeticsScreen();

    public LauncherFrame() {
        super("Cat Client 2");

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(new Dimension(900, 620));
        setMinimumSize(new Dimension(760, 520));
        setLocationRelativeTo(null);

        cards.add(loginScreen, CARD_LOGIN);
        cards.add(homeScreen, CARD_HOME);
        cards.add(settingsScreen, CARD_SETTINGS);
        cards.add(progressScreen, CARD_PROGRESS);
        cards.add(friendsScreen, CARD_FRIENDS);
        cards.add(joinScreen, CARD_JOIN);
        cards.add(cosmeticsScreen, CARD_COSMETICS);

        add(cards);

        settingsScreen.getBackButton().addActionListener(e -> showCard(CARD_HOME));
        homeScreen.getSettingsButton().addActionListener(e -> showCard(CARD_SETTINGS));
        friendsScreen.getBackButton().addActionListener(e -> showCard(CARD_HOME));
        cosmeticsScreen.getBackButton().addActionListener(e -> showCard(CARD_HOME));

        showCard(CARD_LOGIN);
    }

    public void showCard(String name) {
        currentCard = name;
        cardLayout.show(cards, name);
    }

    /** Which screen is up, so background updates know whether they are visible. */
    public String currentCard() {
        return currentCard;
    }

    public LoginScreen getLoginScreen() {
        return loginScreen;
    }

    public HomeScreen getHomeScreen() {
        return homeScreen;
    }

    public SettingsScreen getSettingsScreen() {
        return settingsScreen;
    }

    public ProgressScreen getProgressScreen() {
        return progressScreen;
    }

    public FriendsScreen getFriendsScreen() {
        return friendsScreen;
    }

    public JoinScreen getJoinScreen() {
        return joinScreen;
    }

    public CosmeticsScreen getCosmeticsScreen() {
        return cosmeticsScreen;
    }
}
