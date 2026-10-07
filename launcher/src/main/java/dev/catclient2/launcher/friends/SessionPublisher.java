package dev.catclient2.launcher.friends;

import dev.catclient2.friends.protocol.AccountView;
import dev.catclient2.friends.protocol.JoinSession;
import dev.catclient2.launcher.LauncherConfig;
import dev.catclient2.launcher.instance.GameStateReader;
import dev.catclient2.launcher.instance.LanAddressDetector;
import dev.catclient2.launcher.instance.PortForwarder;
import dev.catclient2.launcher.instance.PublicAddressResolver;
import dev.catclient2.launcher.instance.RelayClient;
import dev.catclient2.launcher.mods.JoinPackBuilder;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Host side of a live session.
 *
 * <p>What counts as "in game" is decided by {@link GameStateReader}, which reads the state file the
 * Fabric mod writes from the real client fields ({@code world != null && player != null &&
 * getNetworkHandler() != null}). The LAN port scraped from the game log is used only as address
 * information, never as the in-game signal.
 *
 * <p>The worker keeps exactly one session alive:
 * <ul>
 *   <li>not in game -> nothing published, {@link #current()} is null;</li>
 *   <li>in game -> published once and kept fresh (world change, new LAN port, heartbeat);</li>
 *   <li>in game -> not in game -> the session is withdrawn and friends see the host go offline.</li>
 * </ul>
 *
 * <p>Every reason why nothing is published is exposed through {@link #unavailableReason()} instead of
 * being swallowed, so the UI can say what is actually wrong.
 */
public class SessionPublisher {
    private static final long HEARTBEAT_MILLIS = 45_000;
    private static final long POLL_MILLIS = 2_000;
    /** Upper bound for joining the old worker in {@link #stop()} before a new one may take over. */
    private static final long STOP_JOIN_MILLIS = 5_000;

    private static final String RELAY_HOST = "cat-client-2-relay.up.railway.app";
    private static final int RELAY_CONTROL_PORT = 8766;

    private final FriendService friends;
    private final Path instanceDir;
    private final GameStateReader gameState;

    private final AtomicBoolean running = new AtomicBoolean();
    /** Bumped per worker so a stale worker can never touch a newer session. */
    private final AtomicInteger epoch = new AtomicInteger();

    private volatile String address;
    private volatile JoinSession current;
    private volatile int hostedPort = -1;
    private volatile boolean portForwarded;
    private volatile RelayClient relayClient;
    private volatile String sessionToken;
    private volatile boolean rebuildRequested;
    private volatile boolean publishing;
    private volatile String unavailableReason;
    private volatile GameStateReader.Mode gameMode = GameStateReader.Mode.NOT_IN_GAME;
    private volatile boolean inGame;

    private volatile Runnable onStateChanged = () -> { };
    private volatile Consumer<String> onStatus = message -> { };
    private volatile Thread worker;

    public SessionPublisher(FriendService friends, Path instanceDir) {
        this.friends = friends;
        this.instanceDir = instanceDir;
        this.gameState = new GameStateReader(instanceDir);
        this.address = friends.settings().lastServerAddress();
    }

    // ------------------------------------------------------------------ queries

    /** The published session, or null while not in game or while publishing failed. */
    public JoinSession current() {
        return current;
    }

    public String address() {
        return address;
    }

    public boolean isRunning() {
        return running.get();
    }

    /** True while a session is being built or refreshed, for a "publishing" UI state. */
    public boolean publishing() {
        return publishing;
    }

    /** What the mod's state file currently says about the game, independent of publishing. */
    public boolean inGame() {
        return inGame;
    }

    public GameStateReader.Mode gameMode() {
        return gameMode;
    }

    /** Non-null while nothing can be published, explaining what is blocking it. */
    public String unavailableReason() {
        return unavailableReason;
    }

    /**
     * Records why nothing is published, for callers that decide not to start the worker at all.
     * Purely a flag: it never touches the network, so it is safe to call from the EDT.
     */
    public void setUnavailableReason(String reason) {
        this.unavailableReason = reason;
        notifyStateChanged();
    }

    // ------------------------------------------------------------------ control

    /**
     * Changes the address friends are told about. The session is rebuilt on the worker's next poll
     * when the player is in a world; otherwise the address is only remembered, and the session is
     * built once a world is entered.
     */
    public void setAddress(String address) {
        String cleaned = address == null ? "" : address.trim();
        this.address = cleaned;
        friends.settings().setLastServerAddress(cleaned);
        // Must never abort the publish flow, so save() reports its own failures internally.
        friends.settings().save();

        rebuildRequested = true;
        notifyStateChanged();
    }

    /**
     * Starts the publishing worker. Safe to call repeatedly; only one worker ever runs.
     *
     * @param onStateChanged run whenever the published session or the in-game state changed, so the
     *                       UI can redraw; called from the worker thread
     * @param onStatus      progress and failure messages
     */
    public synchronized void start(Runnable onStateChanged, Consumer<String> onStatus) {
        this.onStateChanged = onStateChanged == null ? () -> { } : onStateChanged;
        this.onStatus = onStatus == null ? message -> { } : onStatus;

        if (running.get()) {
            onStatus.accept("A publishing worker is already running");
            return;
        }

        int myEpoch = epoch.incrementAndGet();
        running.set(true);

        Thread thread = new Thread(() -> run(myEpoch), "session-publisher");
        thread.setDaemon(true);
        worker = thread;
        thread.start();
    }

    /**
     * Stops the worker and waits for it, so a new one cannot start while the old one is still
     * withdrawing its session.
     */
    public void stop() {
        running.set(false);

        Thread thread = worker;
        if (thread == null) return;
        thread.interrupt();

        try {
            thread.join(STOP_JOIN_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (worker == thread) worker = null;
        }
    }

    // ------------------------------------------------------------------- worker

    private void run(int myEpoch) {
        try {
            long lastHeartbeat = 0;

            while (running.get()) {
                try {
                    AccountView self = friends.self();
                    if (self == null) {
                        // Previously a silent early return, which left the UI saying
                        // "not in game" for the whole session.
                        block("Not signed in to the friend service, so there is no account to publish as");
                        sleep(POLL_MILLIS);
                        continue;
                    }

                    if (!friends.connected()) {
                        block("Friend service is not connected (" + friends.status() + ")");
                        sleep(POLL_MILLIS);
                        continue;
                    }

                    unavailableReason = null;

                    GameStateReader.State state = gameState.read();

    // Must be compared before gameMode is overwritten, otherwise the world-change rebuild
                    // (singleplayer <-> multiplayer) would never fire.
                    boolean modeChanged = state.mode() != gameMode;
                    inGame = state.inGame();
                    gameMode = state.mode();

                    if (!state.inGame()) {
                        // Main menu, still loading, or the world was left: withdraw whatever is up.
                        if (current != null) withdraw(myEpoch);
                        sleep(POLL_MILLIS);
                        continue;
                    }

                    boolean portChanged = hostedPortChanged();
                    long now = System.currentTimeMillis();

                    if (current == null || rebuildRequested || modeChanged || portChanged) {
                        rebuildRequested = false;
                        buildAndPublish(self, state, myEpoch);
                        lastHeartbeat = now;
                    } else if (now - lastHeartbeat >= HEARTBEAT_MILLIS) {
                        // The service marks sessions stale after a while, so a quiet game still
                        // looks joinable.
                        republish();
                        lastHeartbeat = now;
                    }
                } catch (IOException e) {
                    onStatus.accept("Session update failed: " + e.getMessage());
                    if (current == null) unavailableReason = "Could not publish: " + e.getMessage();
                    notifyStateChanged();
                }

                sleep(POLL_MILLIS);
            }
        } finally {
            stopQuietly(myEpoch);
        }
    }

    /** Records why nothing is published and makes sure nothing stale stays published. */
    private void block(String reason) {
        unavailableReason = reason;
        inGame = false;
        if (current != null) withdraw(epoch.get());
        notifyStateChanged();
    }

    /**
     * Withdraws the session: friends must see the host go offline as soon as the world is left.
     * Refuses to act when this worker has already been superseded.
     */
    private void withdraw(int myEpoch) {
        if (epoch.get() != myEpoch) return;

        JoinSession session = current;
        current = null;
        hostedPort = -1;
        portForwarded = false;
        closeRelay();

        if (session != null) {
            if (wasForwarded(session)) PortForwarder.close(session.port(), "TCP");
            friends.unpublishSession();
        }
        notifyStateChanged();
    }

    /** A session with a routable address had a router mapping opened for it. */
    private boolean wasForwarded(JoinSession session) {
        return !session.publicAddress().isBlank() && session.publicPort() > 0;
    }

    /** Final cleanup; a superseded worker must not touch the session a newer one published. */
    private void stopQuietly(int myEpoch) {
        running.set(false);
        if (epoch.get() != myEpoch) return;

        JoinSession session = current;
        current = null;
        hostedPort = -1;
        portForwarded = false;
        closeRelay();
        publishing = false;

        if (session != null) {
            if (wasForwarded(session)) PortForwarder.close(session.port(), "TCP");
            friends.unpublishSession();
        }
        notifyStateChanged();
    }

    private void closeRelay() {
        RelayClient client = relayClient;
        relayClient = null;
        sessionToken = null;
        if (client != null) client.close();
    }

    // ------------------------------------------------------------------ publish

    private void buildAndPublish(AccountView host, GameStateReader.State state, int myEpoch) throws IOException {
        if (epoch.get() != myEpoch) return;

        publishing = true;
        notifyStateChanged();

        try {
            String entered = address == null ? "" : address.trim();

            // The field may carry a port, which is exactly what a port forward or a tunnel needs.
            int explicitPort = 0;
            int colon = entered.lastIndexOf(':');
            if (colon > 0 && colon < entered.length() - 1) {
                String tail = entered.substring(colon + 1);
                boolean numeric = !tail.isEmpty() && tail.length() <= 5;
                for (int i = 0; numeric && i < tail.length(); i++) {
                    numeric = Character.isDigit(tail.charAt(i));
                }
                if (numeric) {
                    explicitPort = Integer.parseInt(tail);
                    entered = entered.substring(0, colon);
                }
            }

            // Only a local world can be opened to LAN. On a remote server the game log's port
            // belongs to someone else, so it must never become this session's address.
            int port = 0;
            if (state.lanHost()) {
                port = explicitPort;
                if (port <= 0) {
                    port = LanAddressDetector.hostedPort(instanceDir);
                    if (port > 0) hostedPort = port;
                }
            }

            String localAddress = entered;
            boolean addressSetByUs = false;
            if (localAddress.isBlank() && port > 0) {
                localAddress = LanAddressDetector.localAddress();
                addressSetByUs = true;
            }

            // UPnP: ask the router to forward the port. Fails gracefully everywhere else, and it
            // is only attempted once per session (the mapping stays open).
            String publicAddress = "";
            int publicPort = 0;
            if (!portForwarded && port > 0) {
                PortForwarder.Mapping mapping = PortForwarder.open(port, "TCP", localAddress, onStatus);
                if (mapping.mapped()) {
                    portForwarded = true;
                    publicAddress = PublicAddressResolver.resolve();
                    publicPort = publicAddress.isEmpty() ? 0 : port;
                } else {
                    onStatus.accept("No router supports UPnP - friends need a manual port forward");
                }
            }

            // Attempted in parallel with (not instead of) UPnP: whichever ends up usable, the LAN/
            // UPnP path is always preferred by the joiner (see JoinInstaller.quickPlayArgs), so this
            // is purely a fallback for when neither LAN nor UPnP works.
            if (port > 0 && (relayClient == null || !relayClient.isAlive())) {
                sessionToken = UUID.randomUUID().toString();
                boolean ownServer = friends.settings().relayViaOwnServer();
                String relayHost = ownServer ? friends.settings().ownRelayHost() : RELAY_HOST;
                int relayControlPort = ownServer ? friends.settings().ownRelayPort() : RELAY_CONTROL_PORT;

                if (!ownServer || !relayHost.isBlank()) {
                    relayClient = RelayClient.start(relayHost, relayControlPort, sessionToken, port, ownServer, onStatus);
                }
            }

            String relayAddress = "";
            int relayPort = 0;
            RelayClient.Connected relayInfo = relayClient == null ? null : relayClient.info();
            if (relayInfo != null) {
                relayAddress = relayInfo.externalHost();
                relayPort = relayInfo.externalPort();
            }

            JoinSession session = JoinPackBuilder.build(host, LauncherConfig.MINECRAFT_VERSION, "fabric",
                LauncherConfig.LOADER_VERSION, instanceDir, localAddress, port,
                publicAddress, publicPort, relayAddress, relayPort, onStatus);

            if (addressSetByUs && localAddress != null && !localAddress.isBlank()) {
                this.address = localAddress + ":" + port;
            }

            // Assigned before publishing so a failing HTTP call still leaves a truthful UI state.
            this.current = session;
            unavailableReason = null;
            friends.publishSession(session);
        } finally {
            publishing = false;
            notifyStateChanged();
        }
    }

    private boolean hostedPortChanged() {
        if (!inGame || gameMode != GameStateReader.Mode.SINGLEPLAYER) return false;

        int port = LanAddressDetector.hostedPort(instanceDir);
        if (port <= 0 || port == hostedPort) return false;

        hostedPort = port;
        return true;
    }

    private void republish() {
        JoinSession session = current;
        if (session == null || !friends.connected()) return;

        try {
            friends.publishSession(session);
        } catch (IOException e) {
            // Heartbeat failures are not worth interrupting the game for; the next one retries.
        }
    }

    private void notifyStateChanged() {
        try {
            onStateChanged.run();
        } catch (RuntimeException e) {
            // A broken UI listener must never kill the publishing loop.
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}