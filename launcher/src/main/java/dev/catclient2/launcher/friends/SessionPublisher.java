package dev.catclient2.launcher.friends;

import dev.catclient2.friends.protocol.AccountView;
import dev.catclient2.friends.protocol.JoinSession;
import dev.catclient2.launcher.LauncherConfig;
import dev.catclient2.launcher.instance.LanAddressDetector;
import dev.catclient2.launcher.instance.PortForwarder;
import dev.catclient2.launcher.instance.PublicAddressResolver;
import dev.catclient2.launcher.mods.JoinPackBuilder;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Host side of a live session: while Minecraft runs it publishes the modpack the friends
 * screen shows, keeps the address fresh (a world opened to LAN announces its port in the
 * game log), asks the router to forward that port, and heartbeats so the service can tell
 * that the host is still in game.
 */
public class SessionPublisher {
    private static final long HEARTBEAT_MILLIS = 45_000;
    private static final long POLL_MILLIS = 4_000;

    private final FriendService friends;
    private final Path instanceDir;
    private final AtomicBoolean running = new AtomicBoolean();

    private volatile String address;
    private volatile JoinSession current;
    private volatile int hostedPort = -1;
    private volatile boolean portForwarded;
    private Thread thread;

    public SessionPublisher(FriendService friends, Path instanceDir) {
        this.friends = friends;
        this.instanceDir = instanceDir;
        this.address = friends.settings().lastServerAddress();
    }

    public JoinSession current() {
        return current;
    }

    public String address() {
        return address;
    }

    public boolean isRunning() {
        return running.get();
    }

    /**
     * Changes the address friends are told about and republishes the session immediately.
     */
    public void setAddress(String address) {
        String cleaned = address == null ? "" : address.trim();
        this.address = cleaned;
        friends.settings().setLastServerAddress(cleaned);
        friends.settings().save();
        republish();
    }

    /** Builds the session and starts publishing it. Never throws, reports through the callback. */
    public void start(Consumer<String> onStatus) {
        if (!running.compareAndSet(false, true)) return;

        thread = new Thread(() -> {
            AccountView self = friends.self();
            if (self == null) {
                running.set(false);
                return;
            }

            try {
                buildAndPublish(self, onStatus);
            } catch (IOException e) {
                onStatus.accept("Could not build the session: " + e.getMessage());
            }

            long lastHeartbeat = System.currentTimeMillis();
            while (running.get()) {
                sleep(POLL_MILLIS);
                if (!running.get()) break;

                try {
                    if (hostedPortChanged()) {
                        buildAndPublish(self, onStatus);
                        lastHeartbeat = System.currentTimeMillis();
                    } else if (System.currentTimeMillis() - lastHeartbeat >= HEARTBEAT_MILLIS) {
                        // The service marks sessions stale after a while, so a quiet game still
                        // looks joinable.
                        republish();
                        lastHeartbeat = System.currentTimeMillis();
                    }
                } catch (IOException e) {
                    onStatus.accept("Session update failed: " + e.getMessage());
                }
            }

            stopQuietly();
        }, "session-publisher");
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        running.set(false);
        Thread worker = thread;
        if (worker != null) worker.interrupt();
    }

    private void stopQuietly() {
        JoinSession session = current;
        current = null;
        hostedPort = -1;
        if (portForwarded && session != null) {
            PortForwarder.close(session.port(), "TCP");
        }
        if (session != null) friends.unpublishSession();
    }

    private void buildAndPublish(AccountView host, Consumer<String> onStatus) throws IOException {
        int port = hostedPort;
        String entered = address == null ? "" : address.trim();

        // The field may carry a port, which is exactly what a port forward or a tunnel needs:
        // friends connect to the forwarded port, not to the random one vanilla picked.
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
        if (explicitPort > 0) port = explicitPort;

        if (port <= 0) {
            port = LanAddressDetector.hostedPort(instanceDir);
            if (port > 0) hostedPort = port;
        }

        String localAddress = entered;
        boolean addressSetByUs = false;
        if (localAddress.isBlank() && port > 0) {
            localAddress = LanAddressDetector.localAddress();
            addressSetByUs = true;
        }

        // UPnP: ask the router to forward the port. Fails gracefully everywhere else,
        // and it is only attempted once per session (the mapping stays open).
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

        // When a public address exists the session carries both; JoinScreen and the joiner
        // pick whichever one actually reaches the host.
        JoinSession session = JoinPackBuilder.build(host, LauncherConfig.MINECRAFT_VERSION, "fabric",
            LauncherConfig.LOADER_VERSION, instanceDir, localAddress, port,
            publicAddress, publicPort, onStatus);

        if (addressSetByUs && localAddress != null && !localAddress.isBlank()) {
            this.address = localAddress + ":" + port;
        }

        this.current = session;
        friends.publishSession(session);
    }

    private boolean hostedPortChanged() {
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

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
