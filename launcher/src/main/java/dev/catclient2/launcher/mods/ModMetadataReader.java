package dev.catclient2.launcher.mods;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Reads a mod's identity out of its own jar, so the launcher does not need the user to maintain a
 * mod list by hand.
 *
 * <p>Supported layouts: Fabric / Quilt ({@code fabric.mod.json}, {@code quilt.mod.json}) and
 * Forge / NeoForge ({@code META-INF/mods.toml}, {@code META-INF/neoforge.mods.toml}). Jars without
 * any of these are plain libraries and are reported as such instead of being guessed at.
 */
public class ModMetadataReader {
    private static final List<String> FABRIC_ENTRIES = List.of("fabric.mod.json", "quilt.mod.json");
    private static final List<String> TOML_ENTRIES = List.of("META-INF/mods.toml", "META-INF/neoforge.mods.toml");

    private ModMetadataReader() {
    }

    /**
     * @return the mod's metadata, or null when the jar is not a mod (library, mixin-only jar, ...).
     */
    public static ModMetadata read(Path jar) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            for (String entryName : FABRIC_ENTRIES) {
                ZipEntry entry = zip.getEntry(entryName);
                if (entry == null) continue;

                ModMetadata metadata = fromFabric(readJson(zip, entry),
                    entryName.contains("quilt") ? "quilt" : "fabric");
                if (metadata == null) return null;

                // A wrapper's own "version" is the wrapper's, not the wrapped mod's - Essential's
                // container reports 1.0.0 no matter which Essential build it ships. The file name is
                // where the real version is written, so it wins for wrappers.
                String fromFileName = versionFromFileName(jar.getFileName().toString());
                if (metadata.wrapper() && fromFileName != null) {
                    metadata = new ModMetadata(metadata.id(), metadata.name(), fromFileName,
                        metadata.loader(), true);
                }
                return metadata;
            }
            for (String entryName : TOML_ENTRIES) {
                ZipEntry entry = zip.getEntry(entryName);
                if (entry == null) continue;
                return fromToml(readString(zip, entry), entryName.contains("neoforge") ? "neoforge" : "forge");
            }
        } catch (IOException e) {
            return null;
        }
        return null;
    }

    private static ModMetadata fromFabric(JsonObject json, String loader) {
        // Loader mods are thin wrappers: the jar only declares a loader plus nested jars, while the
        // mod the user actually installed is named as the ModMenu parent (Essential ships
        // "essential-container" wrapping "essential", for example). The parent is the identity that
        // has to be shared, otherwise the pack is published under a name no download source knows.
        JsonObject parent = parent(json);
        boolean wrapper = parent != null && string(parent, "id") != null;

        String id = string(parent, "id");
        if (id == null || id.isBlank()) id = string(json, "id");
        if (id == null || id.isBlank()) return null;

        String version = string(json, "version");
        String name = string(parent, "name");
        if (name == null || name.isBlank()) name = string(json, "name");

        return new ModMetadata(id, name, version, loader, wrapper);
    }

    /**
     * Pulls a version out of a mod's file name, which is where publishers put it when the metadata
     * cannot be trusted: {@code Essential_1-5-0-1_fabric_1-16-5.jar} yields {@code 1.5.0.1}. Only
     * groups of at least three numbers are accepted, so a name that merely contains digits
     * ("mc1.21") is not mistaken for a version.
     *
     * @return the version, or null when the name carries none
     */
    private static String versionFromFileName(String fileName) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
            .compile("(?<![\\d.])(\\d+(?:[-.]\\d+){2,})(?![\\d.])")
            .matcher(fileName);

        if (!matcher.find()) return null;

        return matcher.group(1).replace('-', '.');
    }

    /** {@code custom.modmenu.parent}, or null when the mod is not a wrapper. */
    private static JsonObject parent(JsonObject json) {
        if (!json.has("custom") || !json.get("custom").isJsonObject()) return null;

        JsonObject custom = json.getAsJsonObject("custom");
        if (!custom.has("modmenu") || !custom.get("modmenu").isJsonObject()) return null;

        JsonObject modmenu = custom.getAsJsonObject("modmenu");
        if (!modmenu.has("parent") || !modmenu.get("parent").isJsonObject()) return null;

        return modmenu.getAsJsonObject("parent");
    }

    /**
     * Minimal TOML reading: grabs the first {@code modId} / {@code version} pair that follows the
     * first {@code [[mods]]} table, which is where both Forge and NeoForge put the mod identity.
     */
    private static ModMetadata fromToml(String toml, String loader) {
        String modId = null;
        String version = null;
        String name = null;

        boolean inModsTable = false;
        for (String rawLine : toml.split("\\R")) {
            String line = rawLine.trim();
            if (line.startsWith("#")) continue;

            if (line.startsWith("[[") && line.endsWith("]]")) {
                if (inModsTable) break; // a second table starts, keep the first mod's identity
                inModsTable = line.equals("[[mods]]");
                continue;
            }
            if (!inModsTable) continue;

            int eq = line.indexOf('=');
            if (eq < 0) continue;

            String key = line.substring(0, eq).trim();
            String value = unquote(line.substring(eq + 1).trim());

            switch (key) {
                case "modId" -> {
                    if (modId == null) modId = value;
                }
                case "version" -> {
                    if (version == null) version = value;
                }
                case "displayName" -> {
                    if (name == null) name = value;
                }
                default -> {
                }
            }
        }

        if (modId == null || modId.isBlank()) return null;
        return new ModMetadata(modId, name, version, loader);
    }

    private static String unquote(String value) {
        int comment = value.indexOf(" #");
        if (comment >= 0) value = value.substring(0, comment).trim();
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static String string(JsonObject json, String field) {
        if (json == null || !json.has(field) || json.get(field).isJsonNull()) return null;
        JsonElement element = json.get(field);
        if (!element.isJsonPrimitive()) return null;
        return element.getAsString();
    }

    private static JsonObject readJson(ZipFile zip, ZipEntry entry) throws IOException {
        return JsonParser.parseString(readString(zip, entry)).getAsJsonObject();
    }

    private static String readString(ZipFile zip, ZipEntry entry) throws IOException {
        try (InputStream in = zip.getInputStream(entry)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
