package dev.catclient2.launcher;

import dev.catclient2.friends.protocol.AccountView;
import dev.catclient2.friends.protocol.FriendRequestView;
import dev.catclient2.friends.protocol.JoinSession;
import dev.catclient2.friends.protocol.StateResponse;
import dev.catclient2.launcher.auth.AccountSession;
import dev.catclient2.launcher.auth.AuthManager;
import dev.catclient2.launcher.friends.EmbeddedFriendServer;
import dev.catclient2.launcher.friends.FriendService;
import dev.catclient2.launcher.friends.FriendSettings;
import dev.catclient2.launcher.friends.PresetSkin;
import dev.catclient2.launcher.friends.PresetSkins;
import dev.catclient2.launcher.friends.SessionPublisher;
import dev.catclient2.launcher.friends.SkinFetcher;
import dev.catclient2.launcher.friends.SkinUploader;
import dev.catclient2.launcher.home.NewsFeed;
import dev.catclient2.launcher.home.QuickServer;
import dev.catclient2.launcher.home.QuickServers;
import dev.catclient2.launcher.install.FabricInstaller;
import dev.catclient2.launcher.install.VanillaInstaller;
import dev.catclient2.launcher.install.VersionMeta;
import dev.catclient2.launcher.instance.InstanceManager;
import dev.catclient2.launcher.instance.LanAddressDetector;
import dev.catclient2.launcher.instance.ModJarProvisioner;
import dev.catclient2.launcher.launch.GameLauncher;
import dev.catclient2.launcher.mods.JoinInstaller;
import dev.catclient2.launcher.ui.CosmeticsScreen;
import dev.catclient2.launcher.ui.FriendsScreen;
import dev.catclient2.launcher.ui.LauncherFrame;
import dev.catclient2.launcher.ui.theme.CatClientTheme;
import dev.catclient2.launcher.update.StartupRegistration;
import dev.catclient2.launcher.update.UpdateChecker;
import dev.catclient2.launcher.update.UpdateInfo;
import dev.catclient2.launcher.update.UpdateInstaller;

import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class Launcher {
    private final Path instanceDir = InstanceManager.defaultInstanceDir();
    private final AuthManager authManager = new AuthManager(instanceDir);
    private final FriendSettings friendSettings = FriendSettings.load();
    private final FriendService friends = new FriendService(friendSettings);
    private final SessionPublisher publisher = new SessionPublisher(friends, instanceDir);
    private final EmbeddedFriendServer embeddedServer = new EmbeddedFriendServer();
    private final QuickServers quickServers = QuickServers.load();

    private LauncherFrame frame;
    private AccountSession currentSession;

    /** The session currently being installed, so two joins cannot run at once. */
    private volatile JoinSession joining;
    private volatile boolean gameRunning;

    public static void main(String[] args) {
        CatClientTheme.install();
        SwingUtilities.invokeLater(() -> {
            Launcher launcher = new Launcher();
            Runtime.getRuntime().addShutdownHook(new Thread(launcher.embeddedServer::stop));
            launcher.start();
        });
    }

    private void start() {
        frame = new LauncherFrame();
        frame.setVisible(true);

        frame.getLoginScreen().getLoginButton().addActionListener(e -> startLogin());
        frame.getHomeScreen().getLogoutButton().addActionListener(e -> logOut());
        frame.getSettingsScreen().getLogoutButton().addActionListener(e -> logOut());
        frame.getHomeScreen().getPlayButton().addActionListener(e -> startPlay());
        frame.getHomeScreen().getFriendsButton().addActionListener(e -> openFriends());
        frame.getHomeScreen().getCosmeticsButton().addActionListener(e -> openCosmetics());
        frame.getCosmeticsScreen().setListener(new CosmeticsUi());

        frame.getFriendsScreen().getRefreshButton().addActionListener(e -> background("Refreshing...", friends::refreshQuietly, () -> {
        }));
        frame.getSettingsScreen().getApplyServerButton().addActionListener(e -> {
            friends.setServerUrl(frame.getSettingsScreen().getServerUrlField().getText());
            connectFriends();
        });
        frame.getFriendsScreen().getAddButton().addActionListener(e ->
            addFriend(frame.getFriendsScreen().getAddField().getText()));
        frame.getFriendsScreen().getPublishButton().addActionListener(e ->
            publishAddress(frame.getFriendsScreen().getAddressField().getText()));
        frame.getJoinScreen().getRetryButton().addActionListener(e -> retryJoin());
        frame.getFriendsScreen().setListener(new FriendUi());

        frame.getHomeScreen().setVersionText("Minecraft " + LauncherConfig.MINECRAFT_VERSION + " / Fabric " + LauncherConfig.LOADER_VERSION);
        frame.getHomeScreen().setNews(NewsFeed.list());
        frame.getHomeScreen().setServers(quickServers.list());
        frame.getHomeScreen().setQuickConnectListener(this::startQuickConnect);
        frame.getHomeScreen().setRemoveServerListener(server -> {
            quickServers.remove(server);
            frame.getHomeScreen().setServers(quickServers.list());
        });
        frame.getHomeScreen().getAddServerButton().addActionListener(e -> {
            String name = frame.getHomeScreen().getNewServerNameField().getText().trim();
            String address = frame.getHomeScreen().getNewServerAddressField().getText().trim();
            if (name.isEmpty() || address.isEmpty()) return;

            quickServers.add(name, address);
            frame.getHomeScreen().setServers(quickServers.list());
            frame.getHomeScreen().getNewServerNameField().setText("");
            frame.getHomeScreen().getNewServerAddressField().setText("");
        });
        frame.getSettingsScreen().setServerUrl(friendSettings.serverUrl());
        frame.getFriendsScreen().setAddress(friendSettings.lastServerAddress());
        frame.getSettingsScreen().setInstanceDirText(instanceDir.toString());
        frame.getSettingsScreen().setFooterText("Cat Client 2 launcher");

        friends.addListener(state -> SwingUtilities.invokeLater(() -> onFriendsState(state)));

        frame.getLoginScreen().setStatus("Checking for saved session...");
        new Thread(() -> {
            AccountSession session = authManager.tryRestoreSession();
            SwingUtilities.invokeLater(() -> {
                if (session != null) {
                    onLoggedIn(session);
                } else {
                    frame.getLoginScreen().setStatus(" ");
                }
            });
        }, "session-restore").start();

        checkForUpdate();
    }

    // ----------------------------------------------------------------- update

    private void checkForUpdate() {
        new Thread(() -> {
            StartupRegistration.ensureRegistered();
            UpdateChecker.checkForUpdate().ifPresent(info ->
                SwingUtilities.invokeLater(() -> frame.getHomeScreen().showUpdateAvailable(info.version(), () -> startUpdate(info))));
        }, "update-check").start();
    }

    private void startUpdate(UpdateInfo info) {
        frame.getHomeScreen().setUpdateButtonEnabled(false);
        frame.getHomeScreen().setUpdateStatus("Downloading Cat Client 2 " + info.version() + "...");

        new Thread(() -> {
            try {
                SwingUtilities.invokeLater(() ->
                    frame.getHomeScreen().setUpdateStatus("Installing - the launcher will close, reopen it once it's done."));
                UpdateInstaller.install(info);
                // The installer now runs independently of this process - safe to exit immediately,
                // our own files are about to be overwritten anyway.
                System.exit(0);
            } catch (Exception e) {
                e.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    frame.getHomeScreen().setUpdateStatus("Update failed: " + e.getMessage());
                    frame.getHomeScreen().setUpdateButtonEnabled(true);
                });
            }
        }, "update-install").start();
    }

    // ------------------------------------------------------------------ login

    private void startLogin() {
        frame.getLoginScreen().getLoginButton().setEnabled(false);
        frame.getLoginScreen().setStatus("Requesting device code...");

        new Thread(() -> {
            try {
                AccountSession session = authManager.login(deviceCode ->
                    SwingUtilities.invokeLater(() ->
                        frame.getLoginScreen().showDeviceCode(deviceCode.getUserCode(), deviceCode.getVerificationUri())
                    )
                );

                SwingUtilities.invokeLater(() -> onLoggedIn(session));
            } catch (Exception ex) {
                ex.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    frame.getLoginScreen().setStatus("Login failed: " + ex.getMessage());
                    frame.getLoginScreen().getLoginButton().setEnabled(true);
                });
            }
        }, "msa-login").start();
    }

    private void onLoggedIn(AccountSession session) {
        this.currentSession = session;
        frame.getLoginScreen().getLoginButton().setEnabled(true);
        frame.getHomeScreen().setAccountName(session.username());
        frame.showCard(LauncherFrame.CARD_HOME);
        connectFriends();
        loadSkin(session);
    }

    private void loadSkin(AccountSession session) {
        new Thread(() -> {
            var raw = SkinFetcher.fetchRawSkin(session.uuid());
            if (raw != null) SwingUtilities.invokeLater(() -> {
                frame.getHomeScreen().setSkin(raw, false);
                frame.getCosmeticsScreen().setCurrentSkin(raw, false);
            });
        }, "skin-fetch").start();
    }

    // --------------------------------------------------------------- cosmetics

    private void openCosmetics() {
        frame.showCard(LauncherFrame.CARD_COSMETICS);
        frame.getCosmeticsScreen().setSkinStatus(" ");
        frame.getCosmeticsScreen().setChosenFile(null);

        List<CosmeticsScreen.PresetSkin> presets = PresetSkins.list().stream()
            .map(skin -> new CosmeticsScreen.PresetSkin(skin.id(), skin.displayName(), skin.slim(), PresetSkins.faceIcon(skin)))
            .toList();
        frame.getCosmeticsScreen().setPresetSkins(presets);

        AccountSession session = currentSession;
        if (session != null) {
            new Thread(() -> {
                var raw = SkinFetcher.fetchRawSkin(session.uuid());
                if (raw != null) SwingUtilities.invokeLater(() -> frame.getCosmeticsScreen().setCurrentSkin(raw, false));
            }, "skin-fetch").start();
        }
    }

    private void chooseSkinFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Minecraft skin (PNG)", "png"));
        int result = chooser.showOpenDialog(frame);
        if (result == JFileChooser.APPROVE_OPTION) {
            Path file = chooser.getSelectedFile().toPath();
            frame.getCosmeticsScreen().setChosenFile(file);
            try {
                frame.getCosmeticsScreen().setCurrentSkin(javax.imageio.ImageIO.read(file.toFile()), false);
            } catch (IOException ignored) {
                // Not a valid image - the upload call below will surface a clearer error.
            }
        }
    }

    private void selectPresetSkin(String skinId, boolean slim) {
        PresetSkin skin = PresetSkins.list().stream().filter(s -> s.id().equals(skinId)).findFirst().orElse(null);
        if (skin == null) return;

        frame.getCosmeticsScreen().setCurrentSkin(PresetSkins.fullImage(skin), slim);
        try {
            uploadSkin(PresetSkins.extractToTemp(skin), slim);
        } catch (IOException e) {
            frame.getCosmeticsScreen().setSkinStatus("Failed: " + e.getMessage());
        }
    }

    private void uploadSkin(Path file, boolean slim) {
        AccountSession session = currentSession;
        if (session == null) return;

        frame.getCosmeticsScreen().setUploadEnabled(false);
        frame.getCosmeticsScreen().setSkinStatus("Uploading...");

        new Thread(() -> {
            String error = null;
            try {
                SkinUploader.upload(session.accessToken(), file,
                    slim ? SkinUploader.Variant.SLIM : SkinUploader.Variant.CLASSIC);
            } catch (Exception e) {
                e.printStackTrace();
                error = e.getMessage();
            }

            String failure = error;
            SwingUtilities.invokeLater(() -> {
                frame.getCosmeticsScreen().setUploadEnabled(true);
                if (failure != null) {
                    frame.getCosmeticsScreen().setSkinStatus("Failed: " + failure);
                } else {
                    frame.getCosmeticsScreen().setSkinStatus("Skin updated!");
                    // Crafatar caches by UUID for a while, so the old face may still show up briefly.
                    loadSkin(session);
                }
            });
        }, "skin-upload").start();
    }

    private void logOut() {
        publisher.stop();
        friends.disconnect();
        authManager.logOut();
        currentSession = null;
        joining = null;
        gameRunning = false;
        frame.getLoginScreen().setStatus(" ");
        frame.getFriendsScreen().setStatus(" ");
        frame.getHomeScreen().setFriendsBadge(0);
        frame.getHomeScreen().setNotice(" ");
        frame.getHomeScreen().setSkin(null, false);
        frame.showCard(LauncherFrame.CARD_LOGIN);
    }

    // ----------------------------------------------------------------- playing

    private void startPlay() {
        startPlay(List.of());
    }

    /** Quick-connecting to a server is otherwise the exact same install+launch flow as PLAY. */
    private void startQuickConnect(QuickServer server) {
        startPlay(List.of("--quickPlayMultiplayer", server.address()));
    }

    private void startPlay(List<String> extraGameArgs) {
        if (currentSession == null) return;

        frame.getHomeScreen().getPlayButton().setEnabled(false);
        frame.showCard(LauncherFrame.CARD_PROGRESS);
        frame.getProgressScreen().setProgress(-1);

        int memoryGb = frame.getSettingsScreen().getMemorySlider().getValue();

        new Thread(() -> {
            publisher.stop();
            try {
                VanillaInstaller vanillaInstaller = new VanillaInstaller(instanceDir);
                VersionMeta vanillaMeta = vanillaInstaller.install(LauncherConfig.MINECRAFT_VERSION,
                    status -> SwingUtilities.invokeLater(() -> frame.getProgressScreen().setStatus(status)));

                FabricInstaller fabricInstaller = new FabricInstaller(instanceDir);
                String fabricVersionId = fabricInstaller.install(LauncherConfig.MINECRAFT_VERSION, LauncherConfig.LOADER_VERSION,
                    status -> SwingUtilities.invokeLater(() -> frame.getProgressScreen().setStatus(status)));

                SwingUtilities.invokeLater(() -> frame.getProgressScreen().setStatus("Installing Cat Client 2..."));
                ModJarProvisioner.provision(instanceDir);

                SwingUtilities.invokeLater(() -> frame.getProgressScreen().setStatus("Launching Minecraft..."));
                GameLauncher gameLauncher = new GameLauncher(instanceDir);
                Process game = gameLauncher.launch(LauncherConfig.MINECRAFT_VERSION, vanillaMeta, fabricVersionId, currentSession, memoryGb, extraGameArgs);

                gameRunning = true;
                SwingUtilities.invokeLater(this::startPublishing);

                int exitCode = game.waitFor();
                gameRunning = false;
                publisher.stop();
                SwingUtilities.invokeLater(this::updateSessionUi);
                if (exitCode != 0) {
                    throw new IOException("Minecraft exited with code " + exitCode + " (see the log above)");
                }

                SwingUtilities.invokeLater(() -> {
                    frame.getHomeScreen().getPlayButton().setEnabled(true);
                    frame.showCard(LauncherFrame.CARD_HOME);
                });
            } catch (Exception ex) {
                ex.printStackTrace();
                gameRunning = false;
                SwingUtilities.invokeLater(() -> {
                    frame.getProgressScreen().setStatus("Failed: " + ex.getMessage());
                    frame.getHomeScreen().getPlayButton().setEnabled(true);
                });
            }
        }, "play").start();
    }

    /** Publishes this session so friends can ask to join and get the modpack installed. */
    private void startPublishing() {
        if (!friendSettings.publishSession() || !friends.connected()) {
            updateSessionUi();
            return;
        }

        publisher.start(status -> SwingUtilities.invokeLater(() -> frame.getFriendsScreen().setStatus(status)));
        updateSessionUi();
    }

    // ---------------------------------------------------------------- friends

    private void connectFriends() {
        AccountSession session = currentSession;
        if (session == null) return;

        frame.getHomeScreen().setNotice("Connecting to the friend service...");

        new Thread(() -> {
            embeddedServer.startIfLocal(friends.serverUrl());
            friends.connect(session);
            SwingUtilities.invokeLater(() -> {
                frame.getFriendsScreen().setStatus(friends.status());
                updateSessionUi();
                onFriendsState(friends.state());
            });
        }, "friend-connect").start();
    }

    private void openFriends() {
        frame.showCard(LauncherFrame.CARD_FRIENDS);
        frame.getFriendsScreen().clearHint();
        updateSessionUi();
        frame.getFriendsScreen().setStatus(friends.status());
        frame.getFriendsScreen().render(friends.state());
    }

    private void onFriendsState(StateResponse state) {
        int pending = state.incoming().size();
        frame.getHomeScreen().setFriendsBadge(pending);
        if (pending > 0) {
            frame.getHomeScreen().setNotice(pending + " friend request(s) waiting - open the friends screen");
        }

        if (LauncherFrame.CARD_FRIENDS.equals(frame.currentCard())) {
            frame.getFriendsScreen().render(state);
            frame.getFriendsScreen().setStatus(friends.status());
            updateSessionUi();
        }

        maybeAutoJoin();
    }

    private void updateSessionUi() {
        FriendsScreen screen = frame.getFriendsScreen();
        screen.setAddress(publisher.address());

        JoinSession current = publisher.current();
        if (current == null) {
            screen.setSessionInfo("Your session: not in game - start Minecraft so friends can join you");
        } else {
            StringBuilder info = new StringBuilder("Your session: Minecraft ")
                .append(current.minecraftVersion()).append(" / ").append(current.loader())
                .append(' ').append(current.loaderVersion()).append(" / ")
                .append(current.modCount()).append(" mods");
            if (current.joinable()) {
                info.append(" - joinable at ").append(current.address()).append(':').append(current.port());
                if (current.publicAddress() != null && !current.publicAddress().isBlank()) {
                    info.append(" (internet: ").append(current.publicAddress()).append(':').append(current.publicPort()).append(')');
                }
            }
            screen.setSessionInfo(info.toString());
        }

        if (LanAddressDetector.behindNat()) {
            screen.setAddressHint("You are behind a router: friends outside your network need a port forward or a tunnel.");
        } else {
            screen.setAddressHint("Friends on your network can use this address directly.");
        }
        if (publisher.isRunning() && publisher.current() != null && publisher.current().publicAddress() != null
            && !publisher.current().publicAddress().isBlank()) {
            screen.setAddressHint("UPnP opened the port automatically - friends outside your network can connect");
        }
    }

    private void addFriend(String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty()) return;

        frame.getFriendsScreen().clearHint();
        background("Looking up " + name + "...", () -> {
            AccountView account = friends.lookupByName(name);
            friends.sendFriendRequest(account);
        }, () -> {
            frame.getFriendsScreen().getAddField().setText("");
            frame.getFriendsScreen().setHint("Friend request sent to " + name);
        });
    }

    private void publishAddress(String address) {
        background("Publishing your address...", () -> publisher.setAddress(address), this::updateSessionUi);
    }

    // -------------------------------------------------------------------- join

    private void maybeAutoJoin() {
        if (!friendSettings.autoJoinAccepted() || joining != null || !friends.connected()) return;

        List<FriendService.PendingJoin> pending = friends.pendingJoins();
        if (pending.isEmpty()) return;

        FriendService.PendingJoin next = pending.get(0);
        if (next.session() == null) {
            frame.getHomeScreen().setNotice(next.host().name() + " allowed you to join but is not in game right now");
            return;
        }
        if (gameRunning) {
            frame.getHomeScreen().setNotice(next.host().name() + " allowed you to join - quit Minecraft, then join from the friends screen");
            return;
        }

        startJoin(next.requestId(), next.host(), next.session());
    }

    private void startJoin(String requestId, AccountView host, JoinSession session) {
        if (joining != null) return;
        if (!session.joinable()) {
            frame.getHomeScreen().setNotice(host.name() + " has not opened a world yet");
            frame.getFriendsScreen().setHint(host.name() + " has to open a world before you can connect");
            return;
        }
        joining = session;
        // Marked as handled up front so a failing install is not retried by the next poll; the
        // join screen has its own retry button.
        friendSettings.markJoinHandled(requestId);

        SwingUtilities.invokeLater(() -> {
            frame.showCard(LauncherFrame.CARD_JOIN);
            frame.getJoinScreen().setSession(session);
            frame.getJoinScreen().setProgress(0);
            frame.getJoinScreen().setStatus("Talking to " + host.name() + "...");
            frame.getJoinScreen().setRetryVisible(false);
        });

        runJoin(session);
    }

    private void retryJoin() {
        JoinSession session = joining;
        if (session == null) return;

        SwingUtilities.invokeLater(() -> {
            frame.getJoinScreen().setRetryVisible(false);
            frame.getJoinScreen().setStatus("Retrying...");
        });
        runJoin(session);
    }

    private void runJoin(JoinSession session) {
        // Read on the event thread: the install runs in the background, and reading a Swing
        // component from there is not allowed (and could hand the game a stale memory size).
        AccountSession account = currentSession;
        int memoryGb = frame.getSettingsScreen().getMemorySlider().getValue();
        if (account == null) {
            SwingUtilities.invokeLater(() -> {
                joining = null;
                frame.showCard(LauncherFrame.CARD_JOIN);
                frame.getJoinScreen().setStatus("Failed: log in again before joining");
                frame.getJoinScreen().setRetryVisible(false);
            });
            return;
        }

        new Thread(() -> {
            Path target = InstanceManager.friendInstanceDir(session.host().uuid(), session.host().name());

            try {
                JoinInstaller.Prepared prepared = JoinInstaller.prepare(session, target, new JoinInstaller.Listener() {
                    @Override
                    public void status(String message) {
                        SwingUtilities.invokeLater(() -> frame.getJoinScreen().setStatus(message));
                    }

                    @Override
                    public void progress(int percent) {
                        SwingUtilities.invokeLater(() -> frame.getJoinScreen().setProgress(percent));
                    }

                    @Override
                    public void modState(String modId, String state) {
                        SwingUtilities.invokeLater(() -> frame.getJoinScreen().setModState(modId, modSymbol(state), state));
                    }
                });

                SwingUtilities.invokeLater(() -> {
                    frame.showCard(LauncherFrame.CARD_PROGRESS);
                    frame.getProgressScreen().setStatus(prepared.complete()
                        ? "Launching Minecraft for " + session.host().name() + "..."
                        : "Launching for " + session.host().name() + " (missing: " + String.join(", ", prepared.missing()) + ")");
                    frame.getProgressScreen().setProgress(-1);
                });

                GameLauncher gameLauncher = new GameLauncher(prepared.instanceDir());
                Process game = gameLauncher.launch(prepared.minecraftVersion(), prepared.vanillaMeta(),
                    prepared.fabricVersionId(), account, memoryGb, JoinInstaller.quickPlayArgs(session));

                gameRunning = true;
                int exitCode = game.waitFor();
                gameRunning = false;

                SwingUtilities.invokeLater(() -> {
                    joining = null;
                    frame.showCard(LauncherFrame.CARD_HOME);
                    if (exitCode != 0) {
                        frame.getHomeScreen().setNotice("Minecraft exited with code " + exitCode);
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    joining = null;
                    frame.showCard(LauncherFrame.CARD_JOIN);
                    frame.getJoinScreen().setStatus("Failed: " + e.getMessage());
                    frame.getJoinScreen().setRetryVisible(true);
                });
            }
        }, "join-" + session.id()).start();
    }

    private static String modSymbol(String state) {
        if (state.startsWith("already installed")) return "==";
        if (state.startsWith("replacing")) return "->";
        if (state.startsWith("installed")) return "OK";
        if (state.startsWith("not available") || state.startsWith("failed")) return "!!";
        return "  ";
    }

    // ----------------------------------------------------------------- helpers

    /** Runs a blocking friend-service call off the EDT and reports the outcome in the UI. */
    private void background(String busyMessage, IoAction action, Runnable onSuccess) {
        frame.getFriendsScreen().setStatus(busyMessage);

        new Thread(() -> {
            String failure = null;
            try {
                action.run();
            } catch (Exception e) {
                e.printStackTrace();
                failure = e.getMessage();
            }

            String error = failure;
            SwingUtilities.invokeLater(() -> {
                if (error != null) {
                    frame.getFriendsScreen().setStatus("Failed: " + error);
                    frame.getFriendsScreen().setHint(error);
                } else {
                    frame.getFriendsScreen().setStatus(friends.status());
                    onSuccess.run();
                }
                updateSessionUi();
            });
        }, "friend-action").start();
    }

    private class CosmeticsUi implements CosmeticsScreen.Listener {
        @Override
        public void selectPresetSkin(String skinId, boolean slim) {
            Launcher.this.selectPresetSkin(skinId, slim);
        }

        @Override
        public void chooseSkinFile() {
            Launcher.this.chooseSkinFile();
        }

        @Override
        public void uploadSkin(Path file, boolean slim) {
            Launcher.this.uploadSkin(file, slim);
        }
    }

    private class FriendUi implements FriendsScreen.Listener {
        @Override
        public void addFriend(String name) {
            Launcher.this.addFriend(name);
        }

        @Override
        public void acceptRequest(FriendRequestView request) {
            String name = request.sender().name();
            background("Accepting the request from " + name + "...", () -> friends.respond(request.id(), true),
                () -> frame.getFriendsScreen().setHint("You and " + name + " are now friends"));
        }

        @Override
        public void declineRequest(FriendRequestView request) {
            background("Declining...", () -> friends.respond(request.id(), false), () -> {
            });
        }

        @Override
        public void cancelRequest(FriendRequestView request) {
            background("Cancelling...", () -> friends.cancel(request.id()), () -> {
            });
        }

        @Override
        public void askToJoin(AccountView friend, JoinSession session) {
            String name = friend.name();
            background("Asking " + name + " for permission...", () -> friends.sendJoinRequest(friend, session),
                () -> frame.getFriendsScreen().setHint("Asked " + name + " - the request shows up in their launcher"));
        }

        @Override
        public void joinNow(String requestId, AccountView host, JoinSession session) {
            if (session == null) {
                frame.getFriendsScreen().setHint(host.name() + " is not in game right now");
                return;
            }
            startJoin(requestId, host, session);
        }

        @Override
        public void publishAddress(String address) {
            Launcher.this.publishAddress(address);
        }

        @Override
        public void refresh() {
            background("Refreshing...", friends::refreshQuietly, () -> {
            });
        }
    }

    @FunctionalInterface
    private interface IoAction {
        void run() throws Exception;
    }
}
