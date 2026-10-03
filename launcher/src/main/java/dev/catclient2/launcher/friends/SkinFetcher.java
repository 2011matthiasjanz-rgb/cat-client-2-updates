package dev.catclient2.launcher.friends;

import dev.catclient2.launcher.util.OperatingSystem;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

/**
 * Fetches a player's face from their skin as a small icon for the launcher, via Crafatar (a public
 * Mojang-skin mirror keyed by UUID). Cached to disk so re-launching does not re-download it and a
 * failed request cannot block startup.
 */
public class SkinFetcher {
    private static final int SIZE = 64;
    private static final HttpClient CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    private SkinFetcher() {
    }

    /** Blocks on network I/O - call off the Swing event thread. Returns null if it could not be fetched. */
    public static ImageIcon fetchFace(UUID uuid) {
        Path cacheFile = OperatingSystem.instanceRoot().resolve("skin-cache").resolve(uuid + ".png");

        try {
            if (!Files.exists(cacheFile)) {
                download("https://crafatar.com/avatars/" + uuid + "?size=" + SIZE + "&overlay", cacheFile);
            }
            BufferedImage image = ImageIO.read(cacheFile.toFile());
            if (image == null) return null;

            Image scaled = image.getScaledInstance(SIZE, SIZE, Image.SCALE_SMOOTH);
            return new ImageIcon(scaled);
        } catch (Exception e) {
            System.err.println("[cat-client] Could not load skin for " + uuid + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * The full, unscaled 64x64 (or legacy 64x32) skin texture - what {@link SkinModel3D} needs to
     * build a 3D preview, as opposed to {@link #fetchFace} which only gives a flat pre-rendered head.
     * Blocks on network I/O - call off the Swing event thread. Returns null if it could not be fetched.
     */
    public static BufferedImage fetchRawSkin(UUID uuid) {
        Path cacheFile = OperatingSystem.instanceRoot().resolve("skin-cache").resolve(uuid + "-raw.png");

        try {
            if (!Files.exists(cacheFile)) {
                download("https://crafatar.com/skins/" + uuid, cacheFile);
            }
            return ImageIO.read(cacheFile.toFile());
        } catch (Exception e) {
            System.err.println("[cat-client] Could not load raw skin for " + uuid + ": " + e.getMessage());
            return null;
        }
    }

    private static void download(String url, Path target) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<byte[]> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IOException("Crafatar returned status " + response.statusCode());
        }

        Files.createDirectories(target.getParent());
        Files.write(target, response.body());
    }
}
