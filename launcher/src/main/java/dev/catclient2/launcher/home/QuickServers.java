package dev.catclient2.launcher.home;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.catclient2.launcher.util.OperatingSystem;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Quick-connect server list: starts from the bundled {@code home-content/servers.json}, plus
 * whatever the player adds themselves, saved under the instance root so it survives updates.
 */
public class QuickServers {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final List<QuickServer> servers;
    private final Path file = OperatingSystem.instanceRoot().resolve("quick-servers.json");

    private QuickServers(List<QuickServer> servers) {
        this.servers = new ArrayList<>(servers);
    }

    public static QuickServers load() {
        List<QuickServer> bundled = readJson(QuickServers.class.getResourceAsStream("/home-content/servers.json"));
        QuickServers instance = new QuickServers(bundled);

        Path file = instance.file;
        if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                List<QuickServer> saved = readJson(in);
                if (saved != null && !saved.isEmpty()) {
                    instance.servers.clear();
                    instance.servers.addAll(saved);
                }
            } catch (IOException e) {
                System.err.println("[cat-client] Could not read " + file + ": " + e.getMessage());
            }
        }
        return instance;
    }

    private static List<QuickServer> readJson(InputStream in) {
        if (in == null) return List.of();
        try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            List<QuickServer> servers = GSON.fromJson(reader, new TypeToken<List<QuickServer>>() {}.getType());
            return servers == null ? List.of() : servers;
        } catch (IOException e) {
            return List.of();
        }
    }

    public List<QuickServer> list() {
        return List.copyOf(servers);
    }

    public void add(String name, String address) {
        servers.add(new QuickServer(name, address));
        save();
    }

    public void remove(QuickServer server) {
        servers.remove(server);
        save();
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(servers), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[cat-client] Could not write " + file + ": " + e.getMessage());
        }
    }
}
