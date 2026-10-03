package dev.catclient2.launcher.auth;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.lenni0451.commons.httpclient.HttpClient;
import net.raphimc.minecraftauth.java.JavaAuthManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class SessionStore {
    private final Path file;

    public SessionStore(Path instanceDir) {
        this.file = instanceDir.resolve("session.json");
    }

    public void save(JavaAuthManager manager) {
        try {
            Files.createDirectories(file.getParent());
            JsonObject json = JavaAuthManager.toJson(manager);
            Files.writeString(file, json.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public JavaAuthManager tryLoad(HttpClient httpClient) {
        if (!Files.exists(file)) return null;

        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject json = JsonParser.parseString(content).getAsJsonObject();
            return JavaAuthManager.fromJson(httpClient, json);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public void clear() {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
