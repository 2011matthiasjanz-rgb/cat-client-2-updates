package dev.catclient2.launcher.friends;

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
 * Changes the account's real Minecraft skin through Mojang's own API - this changes the skin
 * everywhere, on every server, not just in Cat Client 2.
 *
 * <p>Endpoint verified against Mojang's own documentation: {@code POST
 * https://api.minecraftservices.com/minecraft/profile/skins}, multipart body with a {@code variant}
 * field ("classic" or "slim") and a {@code file} field holding the PNG.
 */
public class SkinUploader {
    private static final URI ENDPOINT = URI.create("https://api.minecraftservices.com/minecraft/profile/skins");
    private static final HttpClient CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    private SkinUploader() {
    }

    public enum Variant {
        CLASSIC, SLIM;

        String apiValue() {
            return name().toLowerCase();
        }
    }

    /** Blocks on network I/O - call off the Swing event thread. */
    public static void upload(String accessToken, Path pngFile, Variant variant) throws IOException, InterruptedException {
        byte[] bytes = Files.readAllBytes(pngFile);
        String boundary = "CatClient2Skin" + UUID.randomUUID();

        byte[] body = buildMultipartBody(boundary, variant.apiValue(), pngFile.getFileName().toString(), bytes);

        HttpRequest request = HttpRequest.newBuilder(ENDPOINT)
            .header("Authorization", "Bearer " + accessToken)
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build();

        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("Mojang rejected the skin upload (status " + response.statusCode() + "): " + response.body());
        }
    }

    private static byte[] buildMultipartBody(String boundary, String variant, String fileName, byte[] fileBytes) throws IOException {
        var out = new java.io.ByteArrayOutputStream();
        String crlf = "\r\n";

        out.write(("--" + boundary + crlf).getBytes());
        out.write(("Content-Disposition: form-data; name=\"variant\"" + crlf + crlf).getBytes());
        out.write((variant + crlf).getBytes());

        out.write(("--" + boundary + crlf).getBytes());
        out.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"" + crlf).getBytes());
        out.write(("Content-Type: image/png" + crlf + crlf).getBytes());
        out.write(fileBytes);
        out.write(crlf.getBytes());

        out.write(("--" + boundary + "--" + crlf).getBytes());
        return out.toByteArray();
    }
}
