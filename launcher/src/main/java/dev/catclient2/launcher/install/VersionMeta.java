package dev.catclient2.launcher.install;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Model for a single version's metadata JSON (e.g. https://piston-meta.mojang.com/v1/packages/.../1.21.11.json).
 * Only the fields needed to download/launch vanilla are modeled; unknown fields are ignored by Gson.
 */
public class VersionMeta {
    private static final Gson GSON = new Gson();

    public String id;
    public String type;
    public String mainClass;
    public Downloads downloads;
    public AssetIndexRef assetIndex;
    public String assets;
    public List<Library> libraries;
    public Arguments arguments;

    public static VersionMeta fetch(String url) throws IOException, InterruptedException {
        String json = DownloadUtil.getString(url);
        return GSON.fromJson(json, VersionMeta.class);
    }

    public static class Downloads {
        public DownloadEntry client;
    }

    public static class DownloadEntry {
        public String sha1;
        public long size;
        public String url;
    }

    public static class AssetIndexRef {
        public String id;
        public String sha1;
        public long size;
        public long totalSize;
        public String url;
    }

    public static class Library {
        public String name;
        public LibraryDownloads downloads;
        public JsonArray rules; // raw; evaluated via RuleEvaluator

        // Fabric-style flat Maven-coordinate libraries (no "downloads" object) instead
        // provide these directly - present on Fabric loader/intermediary version JSONs.
        public String url;
        public String sha1;

        public boolean appliesToCurrentOs() {
            return RuleEvaluator.rulesAllow(rules);
        }

        /**
         * Resolves this library to a concrete (relative-path, download-url, sha1) triple,
         * handling both the vanilla ("downloads.artifact") and Fabric (flat "url"/"sha1"
         * plus Maven-coordinate "name") library JSON shapes.
         */
        public ResolvedLibrary resolve() {
            if (downloads != null && downloads.artifact != null) {
                return new ResolvedLibrary(downloads.artifact.path, downloads.artifact.url, downloads.artifact.sha1);
            }

            // Fabric-style: "name" is "group:artifact:version[:classifier]", "url" is the repo base.
            String[] parts = name.split(":");
            String group = parts[0].replace('.', '/');
            String artifact = parts[1];
            String version = parts[2];
            String classifierSuffix = parts.length > 3 ? "-" + parts[3] : "";

            String path = group + "/" + artifact + "/" + version + "/" + artifact + "-" + version + classifierSuffix + ".jar";
            String base = url.endsWith("/") ? url : url + "/";

            return new ResolvedLibrary(path, base + path, sha1);
        }
    }

    public record ResolvedLibrary(String relativePath, String url, String sha1) {
    }

    public static class LibraryDownloads {
        public Artifact artifact;
    }

    public static class Artifact {
        public String path;
        public String sha1;
        public long size;
        public String url;
    }

    public static class Arguments {
        public List<JsonElement> game;
        public List<JsonElement> jvm;
    }

    /**
     * Resolves a raw argument entry (either a plain string, or a {"rules": [...], "value": string|string[]})
     * into zero or more concrete argument strings for the current OS.
     */
    public static List<String> resolveArgument(JsonElement raw) {
        List<String> out = new ArrayList<>();

        if (raw.isJsonPrimitive()) {
            out.add(raw.getAsString());
            return out;
        }

        JsonObject obj = raw.getAsJsonObject();
        JsonArray rules = obj.has("rules") ? obj.getAsJsonArray("rules") : null;
        if (!RuleEvaluator.rulesAllow(rules)) return out;

        JsonElement value = obj.get("value");
        if (value.isJsonArray()) {
            for (JsonElement v : value.getAsJsonArray()) out.add(v.getAsString());
        } else {
            out.add(value.getAsString());
        }

        return out;
    }
}
