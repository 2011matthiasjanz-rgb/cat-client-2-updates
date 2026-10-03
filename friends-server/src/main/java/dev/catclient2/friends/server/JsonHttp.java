package dev.catclient2.friends.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Small helpers around {@link HttpExchange}: JSON in, JSON out, plus the error shape the
 * launcher knows how to display.
 */
public final class JsonHttp {
    public static final Gson GSON = new GsonBuilder().serializeNulls().create();

    /** Requests are tiny; anything bigger is a bug or an attack, not a real payload. */
    private static final int MAX_BODY_BYTES = 4 * 1024 * 1024;

    private JsonHttp() {
    }

    public static JsonObject readObject(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            byte[] body = in.readNBytes(MAX_BODY_BYTES);
            if (body.length == 0) return new JsonObject();
            return JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new ApiException(400, "Malformed JSON body");
        }
    }

    public static void sendJson(HttpExchange exchange, int status, Object payload) throws IOException {
        byte[] body = GSON.toJson(payload).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    public static void sendError(HttpExchange exchange, int status, String message) throws IOException {
        JsonObject error = new JsonObject();
        error.addProperty("error", message);
        sendJson(exchange, status, error);
    }

    public static void requireMethod(HttpExchange exchange, String... methods) {
        for (String method : methods) {
            if (method.equalsIgnoreCase(exchange.getRequestMethod())) return;
        }
        throw new ApiException(405, "Expected " + String.join(" or ", methods));
    }

    public static String queryParam(HttpExchange exchange, String name) {
        return queryParam(exchange, name, null);
    }

    public static String queryParam(HttpExchange exchange, String name, String fallback) {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null) return fallback;

        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) continue;
            if (!decode(pair.substring(0, eq)).equals(name)) continue;
            return decode(pair.substring(eq + 1));
        }
        return fallback;
    }

    public static String requireString(JsonObject json, String field) {
        if (!json.has(field) || json.get(field).isJsonNull()) return null;
        String value = json.get(field).getAsString();
        return value.isBlank() ? null : value;
    }

    public static String requireUuid(JsonObject json, String field) {
        String value = requireString(json, field);
        if (value == null) throw new ApiException(400, "Missing '" + field + "'");
        return normalizeUuid(value);
    }

    public static String normalizeUuid(String uuid) {
        return uuid.replace("-", "").toLowerCase();
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
