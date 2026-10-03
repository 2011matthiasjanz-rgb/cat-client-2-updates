package dev.catclient2.launcher.friends;

import dev.catclient2.friends.protocol.AccountView;
import dev.catclient2.friends.protocol.JoinSession;
import dev.catclient2.friends.protocol.RequestKind;
import dev.catclient2.friends.protocol.RequestState;
import dev.catclient2.friends.protocol.StateResponse;
import dev.catclient2.launcher.auth.AccountSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Everything the launcher does with the friend service: authenticate with the Minecraft account,
 * keep a cached view of friends / requests / live sessions up to date through long-polling, and
 * expose the actions the friends screen offers.
 *
 * <p>Every method that touches the network blocks and must be called off the Swing event thread.
 * Listeners are notified on whichever thread produced the change, so the UI has to hop back to the
 * EDT itself.
 */
public class FriendService {
    private final FriendApi api = new FriendApi();
    private final FriendSettings settings;
    private final MojangProfileApi mojang = new MojangProfileApi();
    private final List<Consumer<StateResponse>> listeners = new CopyOnWriteArrayList<>();

    private volatile String status = "Not connected";
    private volatile boolean connected;
    private volatile String lastError;
    private volatile AccountView self;
    private volatile long cursor;
    private volatile StateResponse state = emptyState(null);

    private volatile boolean polling;
    private Thread pollThread;

    public FriendService(FriendSettings settings) {
        this.settings = settings;
        this.api.configure(settings.serverUrl());
    }

    private static StateResponse emptyState(AccountView self) {
        return new StateResponse(self, 0, List.of(), List.of(), List.of(), List.of(), List.of());
    }

    public void addListener(Consumer<StateResponse> listener) {
        listeners.add(listener);
    }

    public FriendSettings settings() {
        return settings;
    }

    public FriendApi api() {
        return api;
    }

    public String serverUrl() {
        return settings.serverUrl();
    }

    /**
     * Points the launcher at a different friend service. The next {@link #connect} call picks it up.
     */
    public void setServerUrl(String url) {
        settings.setServerUrl(url);
        settings.save();
        api.configure(settings.serverUrl());
    }

    public String status() {
        return status;
    }

    public boolean connected() {
        return connected;
    }

    public String lastError() {
        return lastError;
    }

    public AccountView self() {
        return self;
    }

    public StateResponse state() {
        return state;
    }

    // ------------------------------------------------------------- connection

    public void connect(AccountSession session) {
        if (!api.isConfigured()) {
            status = "No friend service URL configured";
            connected = false;
            return;
        }

        status = "Connecting to " + api.baseUrl() + "...";
        try {
            var response = api.auth(session.accessToken());
            AccountView account = new AccountView(
                response.getAsJsonObject("self").get("uuid").getAsString().replace("-", "").toLowerCase(),
                response.getAsJsonObject("self").get("name").getAsString());

            api.setToken(response.get("token").getAsString());
            self = account;
            connected = true;
            lastError = null;
            status = "Connected as " + account.name();
            cursor = 0;

            refresh();
            startPolling();
        } catch (IOException e) {
            connected = false;
            api.clearToken();
            lastError = e.getMessage();
            status = "Friend service unavailable: " + e.getMessage();
        }
    }

    public void disconnect() {
        polling = false;
        Thread thread = pollThread;
        if (thread != null) thread.interrupt();

        pollThread = null;
        connected = false;
        api.clearToken();
        self = null;
        cursor = 0;
        state = emptyState(null);
        status = "Not connected";
    }

    private void startPolling() {
        if (polling) return;
        polling = true;

        pollThread = new Thread(() -> {
            while (polling) {
                try {
                    FriendApi.PollResult result = api.poll(cursor, 25_000);
                    cursor = result.cursor();

                    if (!result.events().isEmpty()) {
                        refreshQuietly();
                    }
                } catch (FriendApiException e) {
                    if (e.isAuthError()) {
                        status = "Session expired, log in again";
                        connected = false;
                    } else {
                        status = "Friend service error: " + e.getMessage();
                    }
                    sleep(5_000);
                } catch (IOException e) {
                    status = "Friend service unreachable: " + e.getMessage();
                    sleep(5_000);
                }
            }
        }, "friend-events");
        pollThread.setDaemon(true);
        pollThread.start();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ------------------------------------------------------------------ state

    public void refresh() throws IOException {
        StateResponse next = api.state();
        this.state = next;
        this.cursor = next.cursor();
        fireStateChanged();
    }

    public void refreshQuietly() {
        try {
            refresh();
        } catch (IOException e) {
            lastError = e.getMessage();
        }
    }

    private void fireStateChanged() {
        StateResponse snapshot = state;
        for (Consumer<StateResponse> listener : listeners) {
            listener.accept(snapshot);
        }
    }

    // ---------------------------------------------------------------- actions

    public AccountView lookupByName(String name) throws IOException {
        AccountView account = mojang.byName(name);
        if (account == null) throw new IOException("No Minecraft player is called '" + name.trim() + "'");
        return account;
    }

    public void sendFriendRequest(AccountView target) throws IOException {
        api.createRequest(target.uuid(), target.name(), RequestKind.FRIEND.name(), null, null);
        refresh();
    }

    public void sendJoinRequest(AccountView target, JoinSession targetSession) throws IOException {
        api.createRequest(target.uuid(), target.name(), RequestKind.JOIN.name(), null,
            targetSession == null ? null : targetSession.id());
        refresh();
    }

    public void respond(String requestId, boolean accept) throws IOException {
        api.respond(requestId, accept);
        refresh();
    }

    public void cancel(String requestId) throws IOException {
        api.cancel(requestId);
        refresh();
    }

    public void publishSession(JoinSession session) throws IOException {
        api.publishSession(session);
        refresh();
    }

    public void unpublishSession() {
        try {
            api.clearSession();
        } catch (IOException e) {
            lastError = e.getMessage();
        }
        refreshQuietly();
    }

    /** Live session of a friend, from the cache when possible. */
    public JoinSession liveSessionOf(String hostUuid) {
        for (JoinSession session : state.sessions()) {
            if (session.host().uuid().equalsIgnoreCase(hostUuid)) return session;
        }
        return null;
    }

    /** Always asks the service, used right before a join so a stale cache cannot be acted on. */
    public JoinSession fetchSession(String hostUuid) throws IOException {
        return api.sessionOf(hostUuid);
    }

    /**
     * Accepted join requests that still need a modpack install, newest first.
     */
    public List<PendingJoin> pendingJoins() {
        List<PendingJoin> out = new ArrayList<>();
        for (var request : state.acceptedJoins()) {
            if (request.state() != RequestState.ACCEPTED) continue;
            if (settings.isJoinHandled(request.id())) continue;

            JoinSession session = liveSessionOf(request.receiver().uuid());
            out.add(new PendingJoin(request.id(), request.receiver(), session));
        }
        return out;
    }

    public record PendingJoin(String requestId, AccountView host, JoinSession session) {
    }
}
