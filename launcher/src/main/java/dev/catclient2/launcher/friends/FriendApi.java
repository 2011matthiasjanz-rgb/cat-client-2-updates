package dev.catclient2.launcher.friends;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.catclient2.friends.protocol.Api;
import dev.catclient2.friends.protocol.EventView;
import dev.catclient2.friends.protocol.JoinSession;
import dev.catclient2.friends.protocol.StateResponse;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * HTTP client for the friend service.
 *
 * <p>Everything here is blocking and must be called off the Swing event thread; {@code FriendService}
 * takes care of the threading.
 */
public class FriendApi {
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();
    private static final String USER_AGENT = "cat-client-2-launcher/1.0";

    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    private String baseUrl;
    private String token;

    public void configure(String baseUrl) {
        this.baseUrl = stripTrailingSlash(baseUrl);
    }

    public String baseUrl() {
        return baseUrl;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String token() {
        return token;
    }

    public boolean isConfigured() {
        return baseUrl != null && !baseUrl.isBlank();
    }

    public boolean isAuthenticated() {
        return token != null && !token.isBlank();
    }

    public void clearToken() {
        this.token = null;
    }

    public static String stripTrailingSlash(String url) {
        if (url == null) return "";
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) trimmed = trimmed.substring(0, trimmed.length() - 1);
        return trimmed;
    }

    // ------------------------------------------------------------------ calls

    public JsonObject auth(String accessToken) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("accessToken", accessToken);
        return post(Api.PATH_AUTH, body, false);
    }

    public StateResponse state() throws IOException {
        return request("GET", Api.PATH_STATE, null, true, StateResponse.class);
    }

    public PollResult poll(long since, long waitMillis) throws IOException {
        JsonObject json = get(Api.PATH_EVENTS + "?since=" + since + "&wait=" + waitMillis);

        List<EventView> events = List.of();
        if (json.has("events") && json.get("events").isJsonArray()) {
            events = Arrays.asList(GSON.fromJson(json.getAsJsonArray("events"), EventView[].class));
        }
        long cursor = json.has("cursor") ? json.get("cursor").getAsLong() : since;
        return new PollResult(cursor, events);
    }

    public JsonObject createRequest(String targetUuid, String targetName, String kind, String message,
                                    String targetSessionId) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("targetUuid", targetUuid);
        if (targetName != null) body.addProperty("targetName", targetName);
        body.addProperty("kind", kind);
        if (message != null) body.addProperty("message", message);
        if (targetSessionId != null) body.addProperty("targetSessionId", targetSessionId);
        return post(Api.PATH_REQUESTS, body, true);
    }

    public JsonObject respond(String requestId, boolean accept) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("requestId", requestId);
        body.addProperty("accept", accept);
        return post(Api.PATH_REQUEST_RESPOND, body, true);
    }

    public JsonObject cancel(String requestId) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("requestId", requestId);
        return post(Api.PATH_REQUEST_CANCEL, body, true);
    }

    public void publishSession(JoinSession session) throws IOException {
        post(Api.PATH_SESSION, GSON.toJsonTree(session).getAsJsonObject(), true);
    }

    public void clearSession() throws IOException {
        request("DELETE", Api.PATH_SESSION, null, true);
    }

    public JoinSession sessionOf(String hostUuid) throws IOException {
        JsonObject json = request("GET", Api.PATH_SESSION + "/" + hostUuid, null, true, JsonObject.class);
        if (!json.has("session") || json.get("session").isJsonNull()) return null;
        return GSON.fromJson(json.get("session"), JoinSession.class);
    }

    // ---------------------------------------------------------------- helpers

    private JsonObject get(String path) throws IOException {
        return request("GET", path, null, true, JsonObject.class);
    }

    private JsonObject post(String path, JsonObject body, boolean authenticated) throws IOException {
        return request("POST", path, body, authenticated, JsonObject.class);
    }

    /** Convenience overload for calls whose answer is only checked for errors. */
    private void request(String method, String path, JsonObject body, boolean authenticated) throws IOException {
        request(method, path, body, authenticated, JsonObject.class);
    }

    private <T> T request(String method, String path, JsonObject body, boolean authenticated, Class<T> type) throws IOException {
        if (!isConfigured()) throw new FriendApiException(0, "No friend service URL configured");
        if (authenticated && !isAuthenticated()) throw new FriendApiException(401, "Not logged in to the friend service");

        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(90))
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT);

            if (token != null && !token.isBlank()) builder.header(Api.HEADER_TOKEN, token);

            if (body == null) {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                builder.header("Content-Type", "application/json");
                builder.method(method, HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8));
            }

            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            String text = response.body();

            if (response.statusCode() >= 400) {
                throw new FriendApiException(response.statusCode(), errorMessage(text, response.statusCode()));
            }
            if (text == null || text.isBlank()) return null;

            return type.cast(GSON.fromJson(text, type));
        } catch (FriendApiException e) {
            throw e;
        } catch (IOException e) {
            // A refused connection reports a null message, which would show up as "is unreachable: null".
            String reason = e.getMessage() == null || e.getMessage().isBlank()
                ? e.getClass().getSimpleName()
                : e.getMessage();
            throw new IOException("Friend service at " + baseUrl + " is unreachable (" + reason + ")", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while talking to the friend service", e);
        }
    }

    private static String errorMessage(String body, int status) {
        if (body != null && !body.isBlank()) {
            try {
                JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                if (json.has("error") && json.get("error").isJsonPrimitive()) {
                    return json.get("error").getAsString();
                }
            } catch (RuntimeException ignored) {
                // Fall through to the generic message below.
            }
        }
        return "Friend service returned status " + status;
    }

    public static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public record PollResult(long cursor, List<EventView> events) {
    }
}
