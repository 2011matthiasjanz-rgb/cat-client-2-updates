package dev.catclient2.friends.server;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.catclient2.friends.protocol.AccountView;
import dev.catclient2.friends.protocol.Api;
import dev.catclient2.friends.protocol.EventView;
import dev.catclient2.friends.protocol.FriendRequestView;
import dev.catclient2.friends.protocol.JoinSession;
import dev.catclient2.friends.protocol.RequestKind;
import dev.catclient2.friends.protocol.RequestState;
import dev.catclient2.friends.protocol.StateResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * All friend state: known accounts, friendships, requests, published sessions and a bounded event
 * log used for long-polling. Everything is guarded by this object's monitor; the HTTP handlers are
 * short and the file write is the only slow part.
 */
public class FriendStore {
    /** A single account cannot spam more than this many open requests. */
    private static final int MAX_OPEN_REQUESTS = 25;
    private static final int MAX_REQUESTS = 2000;
    private static final int MAX_EVENTS = 500;

    private final Path file;
    private final Gson gson = JsonHttp.GSON;

    private final Map<String, AccountView> accounts = new LinkedHashMap<>();
    private final Map<String, Set<String>> friends = new LinkedHashMap<>();
    private final List<FriendRequestView> requests = new ArrayList<>();
    private final Map<String, JoinSession> sessions = new LinkedHashMap<>();
    private final List<EventView> events = new ArrayList<>();

    private long cursor;

    public FriendStore(Path file) {
        this.file = file;
    }

    public synchronized void load() {
        if (!Files.exists(file)) return;

        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            Persisted data = gson.fromJson(content, new TypeToken<Persisted>() {}.getType());
            if (data == null) return;

            if (data.accounts != null) accounts.putAll(data.accounts);
            if (data.friends != null) {
                data.friends.forEach((uuid, list) -> friends.put(uuid, new LinkedHashSet<>(list)));
            }
            if (data.requests != null) requests.addAll(data.requests);
            if (data.sessions != null) sessions.putAll(data.sessions);
        } catch (Exception e) {
            System.err.println("[cat-friends] Could not read " + file + ": " + e.getMessage());
        }
    }

    private synchronized void save() {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Persisted data = new Persisted(new LinkedHashMap<>(accounts), copyFriends(), new ArrayList<>(requests), new LinkedHashMap<>(sessions));
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, gson.toJson(data), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            System.err.println("[cat-friends] Could not write " + file + ": " + e.getMessage());
        }
    }

    private Map<String, List<String>> copyFriends() {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        friends.forEach((uuid, set) -> copy.put(uuid, new ArrayList<>(set)));
        return copy;
    }

    // ---------------------------------------------------------------- accounts

    public synchronized AccountView upsertAccount(String uuid, String name) {
        String id = JsonHttp.normalizeUuid(uuid);
        AccountView existing = accounts.get(id);
        AccountView account = existing != null && existing.name().equals(name)
            ? existing
            : new AccountView(id, name);

        if (!account.equals(existing)) {
            accounts.put(id, account);
            save();
            addEvent("account", id, id);
        }
        return account;
    }

    public synchronized AccountView account(String uuid) {
        return accounts.get(JsonHttp.normalizeUuid(uuid));
    }

    // ------------------------------------------------------------ friendships

    public synchronized boolean areFriends(String a, String b) {
        Set<String> set = friends.get(JsonHttp.normalizeUuid(a));
        return set != null && set.contains(JsonHttp.normalizeUuid(b));
    }

    public synchronized List<AccountView> friendsOf(AccountView self) {
        List<AccountView> out = new ArrayList<>();
        for (String uuid : friendUuids(self.uuid())) {
            AccountView friend = accounts.get(uuid);
            if (friend != null) out.add(friend);
        }
        out.sort(Comparator.comparing(AccountView::name, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    private Set<String> friendUuids(String uuid) {
        Set<String> set = friends.get(JsonHttp.normalizeUuid(uuid));
        return set == null ? Set.of() : new LinkedHashSet<>(set);
    }

    private void addFriendship(String a, String b) {
        friends.computeIfAbsent(JsonHttp.normalizeUuid(a), key -> new LinkedHashSet<>()).add(JsonHttp.normalizeUuid(b));
        friends.computeIfAbsent(JsonHttp.normalizeUuid(b), key -> new LinkedHashSet<>()).add(JsonHttp.normalizeUuid(a));
    }

    // ---------------------------------------------------------------- requests

    public synchronized FriendRequestView createRequest(AccountView sender, String targetUuid, String targetName,
                                                        RequestKind kind, String message, String targetSessionId) {
        String target = JsonHttp.normalizeUuid(targetUuid);
        if (target.equals(sender.uuid())) throw new ApiException(400, "You cannot send a request to yourself");

        AccountView receiver = accounts.get(target);
        if (receiver == null) {
            throw new ApiException(404, "No known account for " + (targetName == null ? target : targetName)
                + " - they have to launch Cat Client 2 once so the service learns about them");
        }
        if (targetName != null && !receiver.name().equals(targetName)) receiver = new AccountView(receiver.uuid(), targetName);

        if (areFriends(sender.uuid(), target) && kind == RequestKind.FRIEND) {
            throw new ApiException(409, "You are already friends with " + receiver.name());
        }

        for (FriendRequestView existing : requests) {
            if (!existing.isPending()) continue;
            if (!samePair(existing, sender.uuid(), target)) continue;
            if (existing.kind() == kind) {
                throw new ApiException(409, "There is already an open " + kind.name().toLowerCase()
                    + " request between you and " + receiver.name());
            }
        }

        long open = requests.stream().filter(FriendRequestView::isPending).count();
        if (open >= MAX_OPEN_REQUESTS) {
            throw new ApiException(429, "Too many open requests right now, try again later");
        }

        long now = System.currentTimeMillis();
        FriendRequestView request = new FriendRequestView(UUID.randomUUID().toString(), sender, receiver,
            kind, RequestState.PENDING, message, targetSessionId, now, now);
        requests.add(request);
        while (requests.size() > MAX_REQUESTS) requests.remove(0);

        save();
        addEvent("request", sender.uuid(), target);
        return request;
    }

    private boolean samePair(FriendRequestView request, String a, String b) {
        return request.sender().uuid().equals(a) && request.receiver().uuid().equals(b)
            || request.sender().uuid().equals(b) && request.receiver().uuid().equals(a);
    }

    public synchronized FriendRequestView respond(AccountView self, String requestId, boolean accept) {
        FriendRequestView request = requireRequest(requestId);
        if (!request.receiver().uuid().equals(self.uuid())) {
            throw new ApiException(403, "Only the receiver can answer this request");
        }
        if (!request.isPending()) {
            throw new ApiException(409, "This request was already " + request.state().name().toLowerCase());
        }

        RequestState state = accept ? RequestState.ACCEPTED : RequestState.DECLINED;
        FriendRequestView updated = withState(request, state);
        replace(updated);

        if (accept) {
            addFriendship(request.sender().uuid(), request.receiver().uuid());
        }

        save();
        addEvent("request", self.uuid(), request.sender().uuid());
        return updated;
    }

    public synchronized FriendRequestView cancel(AccountView self, String requestId) {
        FriendRequestView request = requireRequest(requestId);
        if (!request.sender().uuid().equals(self.uuid())) {
            throw new ApiException(403, "Only the sender can cancel this request");
        }
        if (!request.isPending()) {
            throw new ApiException(409, "This request was already " + request.state().name().toLowerCase());
        }

        FriendRequestView updated = withState(request, RequestState.CANCELLED);
        replace(updated);
        save();
        addEvent("request", self.uuid(), request.receiver().uuid());
        return updated;
    }

    private FriendRequestView requireRequest(String requestId) {
        for (FriendRequestView request : requests) {
            if (request.id().equals(requestId)) return request;
        }
        throw new ApiException(404, "Unknown request " + requestId);
    }

    private FriendRequestView withState(FriendRequestView request, RequestState state) {
        return new FriendRequestView(request.id(), request.sender(), request.receiver(), request.kind(),
            state, request.message(), request.targetSessionId(), request.createdAt(), System.currentTimeMillis());
    }

    private void replace(FriendRequestView updated) {
        for (int i = 0; i < requests.size(); i++) {
            if (requests.get(i).id().equals(updated.id())) {
                requests.set(i, updated);
                return;
            }
        }
    }

    // ---------------------------------------------------------------- sessions

    public synchronized void publishSession(AccountView host, JoinSession session) {
        JoinSession stored = new JoinSession(session.id(), host, session.minecraftVersion(), session.loader(),
            session.loaderVersion(), session.address(), session.port(), session.mods(),
            session.launcherMinecraftVersion(), session.joinable(), System.currentTimeMillis(), session.note(),
            session.publicAddress(), session.publicPort(), session.relayAddress(), session.relayPort());
        sessions.put(host.uuid(), stored);
        save();
        addEvent("session", host.uuid(), host.uuid());
    }

    public synchronized void clearSession(String uuid) {
        if (sessions.remove(JsonHttp.normalizeUuid(uuid)) != null) {
            save();
            addEvent("session", uuid, uuid);
        }
    }

    public synchronized JoinSession sessionOf(String uuid) {
        JoinSession session = sessions.get(JsonHttp.normalizeUuid(uuid));
        if (session == null) return null;
        if (isStale(session)) return null;
        return session;
    }

    /** Live sessions of this account and all of its friends. */
    public synchronized List<JoinSession> sessionsFor(AccountView self) {
        List<JoinSession> out = new ArrayList<>();
        JoinSession own = sessionOf(self.uuid());
        if (own != null) out.add(own);

        for (String friendUuid : friendUuids(self.uuid())) {
            JoinSession session = sessionOf(friendUuid);
            if (session != null) out.add(session);
        }
        return out;
    }

    private boolean isStale(JoinSession session) {
        return System.currentTimeMillis() - session.updatedAt() > Api.SESSION_STALE_MILLIS;
    }

    // ------------------------------------------------------------------ state

    public synchronized StateResponse stateFor(AccountView self) {
        List<FriendRequestView> incoming = new ArrayList<>();
        List<FriendRequestView> outgoing = new ArrayList<>();
        List<FriendRequestView> acceptedJoins = new ArrayList<>();

        for (FriendRequestView request : requests) {
            boolean toMe = request.receiver().uuid().equals(self.uuid());
            boolean fromMe = request.sender().uuid().equals(self.uuid());
            if (!toMe && !fromMe) continue;

            if (toMe && request.isPending()) incoming.add(request);
            if (fromMe && request.isPending()) outgoing.add(request);
            if (fromMe && request.kind() == RequestKind.JOIN && request.state() == RequestState.ACCEPTED) {
                acceptedJoins.add(request);
            }
        }

        Comparator<FriendRequestView> byNewest = Comparator.comparingLong(FriendRequestView::updatedAt).reversed();
        incoming.sort(byNewest);
        outgoing.sort(byNewest);
        acceptedJoins.sort(byNewest);

        return new StateResponse(self, cursor, friendsOf(self), incoming, outgoing, acceptedJoins, sessionsFor(self));
    }

    /**
     * A live session of the given host, visible to the host itself and to its friends. Returns
     * null when the host is not currently in game.
     */
    public synchronized JoinSession sessionForHost(AccountView viewer, String hostUuid) {
        String host = JsonHttp.normalizeUuid(hostUuid);
        if (!host.equals(viewer.uuid()) && !areFriends(host, viewer.uuid())) {
            throw new ApiException(403, "You are not allowed to see this session");
        }
        return sessionOf(host);
    }

    // ----------------------------------------------------------------- events

    private void addEvent(String type, String actorUuid, String targetUuid) {
        cursor++;
        events.add(new EventView(cursor, type, JsonHttp.normalizeUuid(actorUuid), JsonHttp.normalizeUuid(targetUuid)));
        while (events.size() > MAX_EVENTS) events.remove(0);
        notifyAll();
    }

    /**
     * Blocks until an event newer than {@code since} concerns this account, or the timeout
     * expires. Returns an empty list on timeout, which the client treats as "nothing happened".
     */
    public synchronized List<EventView> awaitEvents(String uuid, long since, long timeoutMillis)
        throws InterruptedException {
        String self = JsonHttp.normalizeUuid(uuid);
        long deadline = System.currentTimeMillis() + timeoutMillis;

        while (true) {
            List<EventView> relevant = relevantEvents(self, since);
            if (!relevant.isEmpty()) return relevant;

            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) return List.of();
            wait(remaining);
        }
    }

    private List<EventView> relevantEvents(String uuid, long since) {
        Set<String> known = new HashSet<>(friendUuids(uuid));
        known.add(uuid);

        List<EventView> out = new ArrayList<>();
        for (EventView event : events) {
            if (event.cursor() <= since) continue;
            if (!known.contains(event.actorUuid()) && !known.contains(event.targetUuid())) continue;
            out.add(event);
        }
        return out;
    }

    public synchronized long cursor() {
        return cursor;
    }

    private record Persisted(
        Map<String, AccountView> accounts,
        Map<String, List<String>> friends,
        List<FriendRequestView> requests,
        Map<String, JoinSession> sessions
    ) {
    }
}
