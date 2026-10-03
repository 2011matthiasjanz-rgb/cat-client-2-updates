package dev.catclient2.friends.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.catclient2.friends.protocol.AccountView;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns a Minecraft access token into an account identity by asking Mojang, so a launcher cannot
 * claim to be somebody else. Successful lookups are cached because the launchers only re-authenticate
 * on start-up and Mojang rate-limits the endpoint.
 */
public class MojangAuth {
    private static final String PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile";
    private static final String NAME_LOOKUP_URL = "https://api.mojang.com/users/profiles/minecraft/";

    private static final long CACHE_MILLIS = 60 * 60 * 1000L;

    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    private final Map<String, CachedProfile> cache = new ConcurrentHashMap<>();

    /**
     * @return the authenticated account, or null when Mojang rejects the token.
     */
    public AccountView validate(String accessToken) throws IOException, InterruptedException {
        CachedProfile cached = cache.get(accessToken);
        if (cached != null && cached.expiresAt > System.currentTimeMillis()) {
            return cached.account;
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(PROFILE_URL))
            .header("Authorization", "Bearer " + accessToken)
            .timeout(Duration.ofSeconds(20))
            .GET()
            .build();

        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            // Offline or Mojang unreachable: let the caller report a network problem instead of
            // pretending the account is fake.
            throw new IOException("Could not reach the Mojang session server: " + e.getMessage(), e);
        }

        if (response.statusCode() == 401 || response.statusCode() == 403) return null;
        if (response.statusCode() != 200) {
            throw new IOException("Mojang session server returned status " + response.statusCode());
        }

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        String id = JsonHttp.normalizeUuid(JsonHttp.requireString(json, "id"));
        String name = JsonHttp.requireString(json, "name");
        if (id.isEmpty() || name == null) return null;

        AccountView account = new AccountView(id, name);
        cache.put(accessToken, new CachedProfile(account, System.currentTimeMillis() + CACHE_MILLIS));
        return account;
    }

    private record CachedProfile(AccountView account, long expiresAt) {
    }
}
