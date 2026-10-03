package dev.catclient2.launcher.install;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.IntConsumer;

public class DownloadUtil {
    private static final HttpClient CLIENT = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    private DownloadUtil() {
    }

    public static String getString(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("GET " + url + " failed with status " + response.statusCode());
        }
        return response.body();
    }

    /**
     * Downloads a file to disk, skipping the download if it already exists with a matching SHA1.
     */
    public static void download(String url, Path target, String expectedSha1) throws IOException, InterruptedException {
        if (expectedSha1 != null && Files.exists(target) && expectedSha1.equalsIgnoreCase(sha1(target))) {
            return;
        }

        Files.createDirectories(target.getParent());

        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<InputStream> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new IOException("GET " + url + " failed with status " + response.statusCode());
        }

        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        try (InputStream in = response.body()) {
            Files.copy(in, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        Files.move(tmp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);

        if (expectedSha1 != null && !expectedSha1.equalsIgnoreCase(sha1(target))) {
            throw new IOException("SHA1 mismatch for " + target + " (expected " + expectedSha1 + ")");
        }
    }

    public static String sha1(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }
}
