package dev.catclient2.launcher.friends;

import com.sun.net.httpserver.HttpServer;
import dev.catclient2.friends.server.FriendsServerMain;
import dev.catclient2.launcher.util.OperatingSystem;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;

/**
 * Starts a friend service in this launcher's own process when the configured URL points at
 * localhost, so nobody has to run {@code cat-friends-server.jar} by hand to use Friends. Whoever
 * a group of friends designates as the "host" just keeps their launcher open; everyone else points
 * their friend-service setting at that person's address instead.
 */
public class EmbeddedFriendServer {
    private static final int PORT = 8765;

    private HttpServer server;

    /** No-op if {@code url} is not a localhost address or a server is already running. */
    public synchronized void startIfLocal(String url) {
        if (server != null || !isLocal(url)) return;

        try {
            Path data = OperatingSystem.instanceRoot().resolve("friends-data.json");
            server = FriendsServerMain.start(PORT, "0.0.0.0", data);
        } catch (IOException e) {
            // Most commonly: another launcher on this machine already owns the port, which is
            // fine - that instance is acting as the host and this one will just connect to it.
            System.err.println("[cat-friends] Could not start the embedded server: " + e.getMessage());
        }
    }

    public synchronized void stop() {
        if (server == null) return;
        server.stop(1);
        server = null;
    }

    private static boolean isLocal(String url) {
        if (url == null || url.isBlank()) return false;
        try {
            String host = URI.create(url).getHost();
            return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
