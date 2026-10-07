package dev.catclient2.launcher.friends;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.catclient2.launcher.util.OperatingSystem;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Launcher-side preferences for the friend system. Stored next to the instance root so it survives
 * re-installing the game.
 */
public class FriendSettings {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();

    private String serverUrl = "http://localhost:8765";
    private String lastServerAddress = "";
    private boolean publishSession = true;
    private boolean autoJoinAccepted = true;
    private List<String> handledJoinRequests = new ArrayList<>();
    /** "railway" (the shared friends-relay service) or "own-server" (a Minecraft server running relay-forge-mod). */
    private String relayMode = "railway";
    private String ownRelayHost = "";
    private int ownRelayPort = 25565;

    private transient final Path file = OperatingSystem.instanceRoot().resolve("friends-settings.json");

    public static FriendSettings load() {
        FriendSettings settings = new FriendSettings();
        if (!Files.exists(settings.file)) return settings;

        try {
            String content = Files.readString(settings.file, StandardCharsets.UTF_8);
            FriendSettings stored = GSON.fromJson(content, FriendSettings.class);
            if (stored != null) {
                if (stored.serverUrl != null && !stored.serverUrl.isBlank()) settings.serverUrl = stored.serverUrl;
                settings.lastServerAddress = stored.lastServerAddress == null ? "" : stored.lastServerAddress;
                settings.publishSession = stored.publishSession;
                settings.autoJoinAccepted = stored.autoJoinAccepted;
                settings.handledJoinRequests = stored.handledJoinRequests == null ? new ArrayList<>() : stored.handledJoinRequests;
                if (stored.relayMode != null && !stored.relayMode.isBlank()) settings.relayMode = stored.relayMode;
                settings.ownRelayHost = stored.ownRelayHost == null ? "" : stored.ownRelayHost;
                settings.ownRelayPort = stored.ownRelayPort > 0 ? stored.ownRelayPort : 25565;
            }
        } catch (Exception e) {
            System.err.println("[cat-friends] Could not read " + settings.file + ": " + e.getMessage());
        }
        return settings;
    }

    /** Last serialization failure, so callers can surface it instead of silently losing the write. */
    private volatile String lastSaveError;

    public void save() {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(this), StandardCharsets.UTF_8);
            lastSaveError = null;
        } catch (IOException e) {
            lastSaveError = e.getMessage();
            System.err.println("[cat-friends] Could not write " + file + ": " + e.getMessage());
        } catch (RuntimeException e) {
            // Gson throws JsonIOException, which is unchecked: it used to escape save() and abort
            // whatever the caller was doing (publishing a session, for one). It is reported instead
            // of propagated, but never hidden - the stack trace names the offending field.
            lastSaveError = e.getMessage();
            System.err.println("[cat-friends] Could not serialize " + file + ": " + e);
            e.printStackTrace();
        }
    }

    /** The message of the last failed {@link #save()}, or null when the last write succeeded. */
    public String lastSaveError() {
        return lastSaveError;
    }

    public String serverUrl() {
        return serverUrl;
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = FriendApi.stripTrailingSlash(serverUrl);
    }

    public String lastServerAddress() {
        return lastServerAddress;
    }

    public void setLastServerAddress(String lastServerAddress) {
        this.lastServerAddress = lastServerAddress == null ? "" : lastServerAddress.trim();
    }

    public boolean publishSession() {
        return publishSession;
    }

    public void setPublishSession(boolean publishSession) {
        this.publishSession = publishSession;
    }

    public boolean autoJoinAccepted() {
        return autoJoinAccepted;
    }

    public void setAutoJoinAccepted(boolean autoJoinAccepted) {
        this.autoJoinAccepted = autoJoinAccepted;
    }

    public boolean relayViaOwnServer() {
        return "own-server".equals(relayMode);
    }

    public void setRelayMode(String relayMode) {
        this.relayMode = relayMode == null || relayMode.isBlank() ? "railway" : relayMode;
    }

    public String ownRelayHost() {
        return ownRelayHost;
    }

    public int ownRelayPort() {
        return ownRelayPort;
    }

    public void setOwnRelay(String host, int port) {
        this.ownRelayHost = host == null ? "" : host.trim();
        this.ownRelayPort = port > 0 ? port : 25565;
    }

    /**
     * Join requests already acted on, so a launcher restart does not install the same pack twice.
     */
    public synchronized boolean markJoinHandled(String requestId) {
        Set<String> handled = new LinkedHashSet<>(handledJoinRequests);
        if (!handled.add(requestId)) return false;
        handledJoinRequests = new ArrayList<>(handled);
        save();
        return true;
    }

    public synchronized boolean isJoinHandled(String requestId) {
        return handledJoinRequests.contains(requestId);
    }
}
