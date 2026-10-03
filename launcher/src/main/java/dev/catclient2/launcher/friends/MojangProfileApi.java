package dev.catclient2.launcher.friends;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.catclient2.friends.protocol.AccountView;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Resolves a Minecraft player name into an account uuid so a friend can be added by typing their
 * name. The name -> uuid mapping is published by Mojang, the service only accepts uuids it already
 * knows, so a typo simply comes back as "unknown account".
 */
public class MojangProfileApi {
    private static final String PROFILE_URL = "https://api.mojang.com/users/profiles/minecraft/";

    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    /** @return the account, or null when no player owns that name. */
    public AccountView byName(String name) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(PROFILE_URL + FriendApi.encode(name.trim())))
            .timeout(Duration.ofSeconds(15))
            .header("Accept", "application/json")
            .GET()
            .build();

        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while looking up " + name, e);
        }

        if (response.statusCode() == 204 || response.statusCode() == 404) return null;
        if (response.statusCode() != 200) {
            throw new IOException("Name lookup failed with status " + response.statusCode());
        }

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        String id = json.get("id").getAsString();
        String resolvedName = json.get("name").getAsString();
        return new AccountView(id.replace("-", "").toLowerCase(), resolvedName);
    }
}
