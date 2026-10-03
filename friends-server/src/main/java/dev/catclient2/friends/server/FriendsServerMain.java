package dev.catclient2.friends.server;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.catclient2.friends.protocol.AccountView;
import dev.catclient2.friends.protocol.Api;
import dev.catclient2.friends.protocol.EventView;
import dev.catclient2.friends.protocol.FriendRequestView;
import dev.catclient2.friends.protocol.JoinSession;
import dev.catclient2.friends.protocol.RequestKind;
import dev.catclient2.friends.protocol.StateResponse;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * The Cat Client 2 friend service.
 *
 * <p>It only brokers small pieces of information - who is friends with whom, who may join which
 * session, and which modpack a session needs. Every launcher authenticates with its own Minecraft
 * access token (validated against Mojang), so nobody can act as another account, and sessions are
 * only ever handed out to confirmed friends.
 *
 * <p>Run it with {@code java -jar cat-friends-server.jar --port=8765 --data=friends-data.json}.
 */
public class FriendsServerMain {
    private static final int MAX_MODS_PER_SESSION = 500;
    private static final long MAX_EVENT_WAIT_MILLIS = 60_000;

    private final FriendStore store;
    private final MojangAuth mojangAuth = new MojangAuth();
    private final SessionTokens tokens = new SessionTokens();

    private FriendsServerMain(FriendStore store) {
        this.store = store;
    }

    public static void main(String[] args) throws IOException {
        int port = 8765;
        String bind = "0.0.0.0";
        Path data = Paths.get("friends-data.json");

        for (String arg : args) {
            if (arg.startsWith("--port=")) port = Integer.parseInt(arg.substring(7));
            else if (arg.startsWith("--bind=")) bind = arg.substring(7);
            else if (arg.startsWith("--data=")) data = Paths.get(arg.substring(7));
            else {
                System.err.println("Unknown argument: " + arg);
                System.err.println("Usage: java -jar cat-friends-server.jar [--port=8765] [--bind=0.0.0.0] [--data=friends-data.json]");
                return;
            }
        }

        HttpServer server = start(port, bind, data);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[cat-friends] Shutting down...");
            server.stop(1);
        }));
    }

    /**
     * Starts the HTTP server and returns immediately; used both by the standalone CLI entry point
     * and by the launcher, which embeds a server in its own process so nobody has to run one by hand.
     */
    public static HttpServer start(int port, String bind, Path data) throws IOException {
        FriendStore store = new FriendStore(data);
        store.load();

        HttpServer server = HttpServer.create(new InetSocketAddress(bind, port), 64);
        // One thread per poll would cap the number of connected launchers at the pool size, so the
        // pool grows on demand instead: launchers spend nearly all of their time parked in a poll.
        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "cat-friends-http");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(executor);

        FriendsServerMain app = new FriendsServerMain(store);
        server.createContext(Api.PATH_HEALTH, exchange -> app.guard(exchange, () -> app.handleHealth(exchange)));
        server.createContext(Api.PATH_AUTH, app::handleAuth);
        server.createContext(Api.PATH_STATE, app::handleState);
        server.createContext(Api.PATH_EVENTS, app::handleEvents);
        server.createContext(Api.PATH_REQUESTS, app::handleCreateRequest);
        server.createContext(Api.PATH_REQUEST_RESPOND, app::handleRespond);
        server.createContext(Api.PATH_REQUEST_CANCEL, app::handleCancel);
        server.createContext(Api.PATH_SESSION, app::handleSession);
        // Anything else gets a JSON error, because the launchers parse the error field of a
        // response and the JDK's default 404 page would show up as raw HTML in a message dialog.
        server.createContext("/", exchange -> JsonHttp.sendJson(exchange, 404,
            Map.of("error", "Unknown endpoint " + exchange.getRequestURI().getPath())));

        server.start();
        System.out.println("[cat-friends] Listening on http://" + bind + ":" + port + " (data: " + data.toAbsolutePath() + ")");
        return server;
    }

    // ---------------------------------------------------------------- handlers

    private void handleHealth(HttpExchange exchange) throws IOException {
        JsonHttp.requireMethod(exchange, "GET");
        JsonObject body = new JsonObject();
        body.addProperty("status", "ok");
        body.addProperty("cursor", store.cursor());
        JsonHttp.sendJson(exchange, 200, body);
    }

    private void handleAuth(HttpExchange exchange) throws IOException {
        guard(exchange, () -> {
            JsonHttp.requireMethod(exchange, "POST");
            JsonObject body = JsonHttp.readObject(exchange);
            String accessToken = JsonHttp.requireString(body, "accessToken");
            if (accessToken == null) throw new ApiException(400, "Missing 'accessToken'");

            AccountView account = mojangAuth.validate(accessToken);
            if (account == null) throw new ApiException(401, "Mojang rejected that Minecraft access token");

            AccountView self = store.upsertAccount(account.uuid(), account.name());
            tokens.revokeAll(self.uuid());

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("token", tokens.issue(self.uuid()));
            response.put("self", self);
            JsonHttp.sendJson(exchange, 200, response);
        });
    }

    private void handleState(HttpExchange exchange) throws IOException {
        guard(exchange, () -> {
            JsonHttp.requireMethod(exchange, "GET");
            AccountView self = authenticate(exchange);
            JsonHttp.sendJson(exchange, 200, store.stateFor(self));
        });
    }

    private void handleEvents(HttpExchange exchange) throws IOException {
        guard(exchange, () -> {
            JsonHttp.requireMethod(exchange, "GET");
            AccountView self = authenticate(exchange);

            long since = parseLong(JsonHttp.queryParam(exchange, "since"), 0);
            long wait = Math.min(parseLong(JsonHttp.queryParam(exchange, "wait"), 25_000), MAX_EVENT_WAIT_MILLIS);

            List<EventView> events = store.awaitEvents(self.uuid(), since, wait);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("cursor", store.cursor());
            response.put("events", events);
            JsonHttp.sendJson(exchange, 200, response);
        });
    }

    private void handleCreateRequest(HttpExchange exchange) throws IOException {
        guard(exchange, () -> {
            JsonHttp.requireMethod(exchange, "POST");
            AccountView self = authenticate(exchange);
            JsonObject body = JsonHttp.readObject(exchange);

            String targetUuid = JsonHttp.requireString(body, "targetUuid");
            if (targetUuid == null) throw new ApiException(400, "Missing 'targetUuid'");

            RequestKind kind = parseKind(JsonHttp.requireString(body, "kind"));
            FriendRequestView request = store.createRequest(self, targetUuid,
                JsonHttp.requireString(body, "targetName"), kind,
                JsonHttp.requireString(body, "message"),
                JsonHttp.requireString(body, "targetSessionId"));

            JsonHttp.sendJson(exchange, 201, request);
        });
    }

    private void handleRespond(HttpExchange exchange) throws IOException {
        guard(exchange, () -> {
            JsonHttp.requireMethod(exchange, "POST");
            AccountView self = authenticate(exchange);
            JsonObject body = JsonHttp.readObject(exchange);

            String requestId = JsonHttp.requireString(body, "requestId");
            if (requestId == null) throw new ApiException(400, "Missing 'requestId'");
            boolean accept = body.has("accept") && body.get("accept").getAsBoolean();

            JsonHttp.sendJson(exchange, 200, store.respond(self, requestId, accept));
        });
    }

    private void handleCancel(HttpExchange exchange) throws IOException {
        guard(exchange, () -> {
            JsonHttp.requireMethod(exchange, "POST");
            AccountView self = authenticate(exchange);
            JsonObject body = JsonHttp.readObject(exchange);

            String requestId = JsonHttp.requireString(body, "requestId");
            if (requestId == null) throw new ApiException(400, "Missing 'requestId'");

            JsonHttp.sendJson(exchange, 200, store.cancel(self, requestId));
        });
    }

    private void handleSession(HttpExchange exchange) throws IOException {
        guard(exchange, () -> {
            AccountView self = authenticate(exchange);
            String path = exchange.getRequestURI().getPath();

            if (path.length() > Api.PATH_SESSION.length()) {
                JsonHttp.requireMethod(exchange, "GET");
                String hostUuid = path.substring(Api.PATH_SESSION.length() + 1);
                Map<String, Object> response = new LinkedHashMap<>();
                response.put("session", store.sessionForHost(self, hostUuid));
                JsonHttp.sendJson(exchange, 200, response);
                return;
            }

            if ("DELETE".equalsIgnoreCase(exchange.getRequestMethod())) {
                store.clearSession(self.uuid());
                JsonHttp.sendJson(exchange, 200, Map.of("cleared", true));
                return;
            }

            JsonHttp.requireMethod(exchange, "POST");
            JoinSession session = JsonHttp.GSON.fromJson(JsonHttp.readObject(exchange), JoinSession.class);
            if (session == null) throw new ApiException(400, "Missing session body");
            if (session.modCount() > MAX_MODS_PER_SESSION) {
                throw new ApiException(400, "A session can advertise at most " + MAX_MODS_PER_SESSION + " mods");
            }

            // The host identity always comes from the token, never from the payload.
            store.publishSession(self, session);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("session", store.sessionOf(self.uuid()));
            JsonHttp.sendJson(exchange, 200, response);
        });
    }

    // ----------------------------------------------------------------- helpers

    private AccountView authenticate(HttpExchange exchange) {
        String token = exchange.getRequestHeaders().getFirst(Api.HEADER_TOKEN);
        if (token == null || token.isBlank()) token = JsonHttp.queryParam(exchange, Api.PARAM_TOKEN);

        String uuid = tokens.resolve(token);
        if (uuid == null) throw new ApiException(401, "Not authenticated - log in through the launcher first");

        AccountView account = store.account(uuid);
        if (account == null) throw new ApiException(401, "Unknown account, log in again");
        return account;
    }

    private void guard(HttpExchange exchange, ExchangeTask task) throws IOException {
        try {
            task.run();
        } catch (ApiException e) {
            JsonHttp.sendError(exchange, e.status(), e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            JsonHttp.sendError(exchange, 500, "Interrupted");
        } catch (Exception e) {
            e.printStackTrace();
            JsonHttp.sendError(exchange, 500, "Internal error: " + e);
        } finally {
            exchange.close();
        }
    }

    private static RequestKind parseKind(String value) {
        if (value == null) return RequestKind.FRIEND;
        try {
            return RequestKind.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ApiException(400, "Unknown request kind '" + value + "'");
        }
    }

    private static long parseLong(String value, long fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @FunctionalInterface
    private interface ExchangeTask {
        void run() throws IOException, InterruptedException;
    }
}
