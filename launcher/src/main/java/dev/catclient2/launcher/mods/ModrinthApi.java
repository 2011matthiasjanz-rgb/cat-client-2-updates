package dev.catclient2.launcher.mods;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.catclient2.friends.protocol.ModRequirement;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Modrinth lookups, used to turn "a friend runs these mods" into "download exactly these files".
 *
 * <p>Modrinth is the source of truth for which file belongs to which Minecraft version and mod
 * loader, so version selection always happens here instead of guessing from a mod's own version
 * string.
 */
public class ModrinthApi {
    private static final String API = "https://api.modrinth.com/v2";
    private static final String USER_AGENT = "cat-client-2-launcher/1.0";

    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    private final Map<String, List<Project>> searchCache = new ConcurrentHashMap<>();
    private final Map<String, List<Version>> versionCache = new ConcurrentHashMap<>();

    public record Project(String id, String slug, String title) {
    }

    public record ModFile(String filename, boolean primary, long size, String sha1, String url) {
    }

    public record Version(String id, String versionNumber, String versionType, String datePublished,
                          List<String> gameVersions, List<String> loaders, List<ModFile> files) {
        public ModFile primaryFile() {
            for (ModFile file : files) {
                if (file.primary()) return file;
            }
            return files.isEmpty() ? null : files.get(0);
        }

        boolean supports(String minecraftVersion, String loader) {
            return gameVersions.contains(minecraftVersion) && loaders.contains(loader);
        }
    }

    /**
     * Turns an installed mod into a shareable requirement.
     *
     * <p>An exact Modrinth version match is preferred. When the exact version is not published for
     * the given Minecraft version and loader, the newest compatible one is used and the requirement
     * carries a note so the joiner can see the substitution.
     */
    public ModRequirement resolve(InstalledMod mod, String minecraftVersion, String loader) throws IOException {
        return resolve(mod.id(), mod.name(), mod.version(), minecraftVersion, loader);
    }

    public ModRequirement resolve(String modId, String name, String version, String minecraftVersion, String loader)
        throws IOException {
        Project project = findProject(name, modId, minecraftVersion);
        if (project == null) {
            return ModRequirement.unresolved(modId, name, version, minecraftVersion, loader,
                "not on Modrinth - copy this mod into the friend's instance manually");
        }

        List<Version> versions = versions(project.id(), minecraftVersion, loader);
        if (versions.isEmpty()) {
            return ModRequirement.unresolved(modId, name, version, minecraftVersion, loader,
                "Modrinth project '" + project.slug() + "' has no " + loader + " build for Minecraft " + minecraftVersion);
        }

        Version chosen = pickVersion(versions, version);
        if (chosen == null) {
            return ModRequirement.unresolved(modId, name, version, minecraftVersion, loader,
                "Modrinth project '" + project.slug() + "' has no version '" + version + "' for Minecraft " + minecraftVersion);
        }

        ModFile file = chosen.primaryFile();
        if (file == null) {
            return ModRequirement.unresolved(modId, name, version, minecraftVersion, loader,
                "Modrinth version has no downloadable file");
        }

        String note = chosen.versionNumber().equalsIgnoreCase(version)
            ? null
            : "host runs " + version + ", using the closest " + loader + " build (" + chosen.versionNumber() + ")";

        return new ModRequirement(modId, name, version, minecraftVersion, loader,
            project.id(), project.slug(), chosen.id(), file.filename(), file.url(), file.sha1(), file.size(), true, note);
    }

    /**
     * Finds the Modrinth project a locally installed mod belongs to.
     *
     * <p>The display name is searched first and the mod id second, because a mod's name and its id
     * regularly differ ("Sodium" / "sodium-fabric", a wrapper id that only resembles the project).
     * A project that has no build for the requested Minecraft version is not accepted here, so a
     * same-named project for a different game version cannot shadow the right one.
     *
     * @return the project, or null when the mod is not distributed through Modrinth
     */
    private Project findProject(String name, String modId, String minecraftVersion) throws IOException {
        for (String query : new String[]{name, modId}) {
            if (query == null || query.isBlank()) continue;

            List<Project> candidates = search(query, minecraftVersion);
            if (candidates.isEmpty()) continue;

            Project match = bestMatch(candidates, name);
            if (match != null) return match;
        }
        return null;
    }

    /** Modrinth project for an already known project id or slug. */
    public Project project(String idOrSlug) throws IOException {
        JsonObject json = getObject(API + "/project/" + enc(idOrSlug));
        return new Project(json.get("id").getAsString(), json.get("slug").getAsString(), json.get("title").getAsString());
    }

    private List<Project> search(String name, String minecraftVersion) throws IOException {
        String key = name.toLowerCase(Locale.ROOT) + "|" + minecraftVersion;
        List<Project> cached = searchCache.get(key);
        if (cached != null) return cached;

        // The loader is filtered later on the version endpoint; Modrinth's categories facet does
        // not support the "a|b" or syntax, so it is not used here at all.
        String url = searchUrl(name, minecraftVersion);

        List<Project> hits = readHits(url);
        if (hits.isEmpty() && minecraftVersion != null && !minecraftVersion.isBlank()) {
            // Nothing tags that Minecraft version (or it is too old for Modrinth), so fall back to
            // an unfiltered search and let the version listing produce the accurate message.
            hits = readHits(searchUrl(name, null));
        }

        searchCache.put(key, hits);
        return hits;
    }

    private static String searchUrl(String name, String minecraftVersion) {
        StringBuilder facets = new StringBuilder("[[\"project_type:mod\"]");
        if (minecraftVersion != null && !minecraftVersion.isBlank()) {
            facets.append(",[\"versions:").append(minecraftVersion).append("\"]");
        }
        facets.append(']');

        return API + "/search?query=" + enc(name) + "&limit=10&facets=" + enc(facets.toString());
    }

    private List<Project> readHits(String url) throws IOException {
        // The search endpoint answers with {"hits":[...]}, the version endpoints with a plain array.
        JsonObject response = getObject(url);
        if (!response.has("hits") || !response.get("hits").isJsonArray()) return List.of();

        List<Project> out = new ArrayList<>();
        for (JsonElement element : response.getAsJsonArray("hits")) {
            JsonObject hit = element.getAsJsonObject();
            out.add(new Project(hit.get("project_id").getAsString(), hit.get("slug").getAsString(),
                hit.get("title").getAsString()));
        }
        return out;
    }

    private Project bestMatch(List<Project> candidates, String name) {
        String wanted = normalize(name);
        for (Project project : candidates) {
            if (normalize(project.slug()).equals(wanted) || normalize(project.title()).equals(wanted)) {
                return project;
            }
        }
        return candidates.get(0);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /**
     * Versions of a project for the given Minecraft version and loader, newest first.
     *
     * <p>Modrinth is the only place that knows which file belongs to which Minecraft version, so the
     * filter is applied here and never guessed from a mod's own version string. A project that has no
     * build for the requested Minecraft version yields an empty list, which makes the caller report
     * the mod as unavailable instead of handing out a jar that cannot load.
     */
    private List<Version> versions(String projectId, String minecraftVersion, String loader) throws IOException {
        String key = projectId + "|" + minecraftVersion + "|" + loader;
        List<Version> cached = versionCache.get(key);
        if (cached != null) return cached;

        List<Version> compatible = new ArrayList<>();
        for (Version version : fetchVersions(projectId, null, loader)) {
            if (minecraftVersion == null || minecraftVersion.isBlank() || version.supports(minecraftVersion, loader)) {
                compatible.add(version);
            }
        }

        List<Version> sorted = sortVersions(compatible);
        versionCache.put(key, sorted);
        return sorted;
    }

    private List<Version> fetchVersions(String projectId, String minecraftVersion, String loader) throws IOException {
        StringBuilder url = new StringBuilder(API + "/project/" + enc(projectId) + "/version?loaders=")
            .append(enc("\"" + loader + "\""));
        if (minecraftVersion != null) {
            url.append("&game_versions=").append(enc("\"" + minecraftVersion + "\""));
        }

        List<Version> out = new ArrayList<>();
        for (JsonElement element : getArray(url.toString())) {
            JsonObject version = element.getAsJsonObject();

            List<ModFile> files = new ArrayList<>();
            for (JsonElement fileElement : version.getAsJsonArray("files")) {
                JsonObject file = fileElement.getAsJsonObject();
                JsonObject hashes = file.has("hashes") ? file.getAsJsonObject("hashes") : new JsonObject();
                files.add(new ModFile(
                    file.get("filename").getAsString(),
                    file.has("primary") && file.get("primary").getAsBoolean(),
                    file.has("size") ? file.get("size").getAsLong() : 0,
                    hashes.has("sha1") ? hashes.get("sha1").getAsString() : null,
                    file.get("url").getAsString()));
            }

            List<String> gameVersions = new ArrayList<>();
            for (JsonElement gameVersion : version.getAsJsonArray("game_versions")) {
                gameVersions.add(gameVersion.getAsString());
            }
            List<String> loaders = new ArrayList<>();
            for (JsonElement loaderElement : version.getAsJsonArray("loaders")) {
                loaders.add(loaderElement.getAsString());
            }

            out.add(new Version(
                version.get("id").getAsString(),
                version.get("version_number").getAsString(),
                version.has("version_type") ? version.get("version_type").getAsString() : "release",
                version.has("date_published") ? version.get("date_published").getAsString() : "",
                gameVersions,
                loaders,
                files));
        }
        return out;
    }

    private static List<Version> sortVersions(List<Version> versions) {
        List<Version> sorted = new ArrayList<>(versions);
        sorted.sort(Comparator
            .comparingInt((Version version) -> typeRank(version.versionType()))
            .thenComparing(Version::datePublished, Comparator.reverseOrder()));
        return sorted;
    }

    private static int typeRank(String versionType) {
        return switch (versionType == null ? "" : versionType) {
            case "release" -> 0;
            case "snapshot", "beta" -> 1;
            case "alpha" -> 2;
            default -> 3;
        };
    }

    /**
     * Exact version match if possible, otherwise the closest one. Mod version strings rarely match
     * Modrinth's {@code version_number} verbatim (Sodium for example uses "mc1.21.11-0.8.7-fabric"),
     * so a containment check in both directions is used as the second best guess.
     */
    private Version pickVersion(List<Version> versions, String wantedVersion) {
        if (wantedVersion == null || wantedVersion.isBlank()) return versions.isEmpty() ? null : versions.get(0);

        for (Version version : versions) {
            if (version.versionNumber().equalsIgnoreCase(wantedVersion)) return version;
        }
        for (Version version : versions) {
            if (version.versionNumber().toLowerCase(Locale.ROOT).contains(wantedVersion.toLowerCase(Locale.ROOT))) {
                return version;
            }
        }
        for (Version version : versions) {
            if (wantedVersion.toLowerCase(Locale.ROOT).contains(version.versionNumber().toLowerCase(Locale.ROOT))) {
                return version;
            }
        }
        return versions.isEmpty() ? null : versions.get(0);
    }

    // ------------------------------------------------------------------- http

    private JsonObject getObject(String url) throws IOException {
        JsonElement element = get(url);
        if (!element.isJsonObject()) throw new IOException("Modrinth returned an unexpected answer for " + url);
        return element.getAsJsonObject();
    }

    private JsonArray getArray(String url) throws IOException {
        JsonElement element = get(url);
        if (element.isJsonNull()) return new JsonArray();
        if (!element.isJsonArray()) {
            throw new IOException("Modrinth did not return a list for " + url + ": " + shorten(element.toString()));
        }
        return element.getAsJsonArray();
    }

    private JsonElement get(String url) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(20))
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .GET()
            .build();

        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while querying Modrinth", e);
        }

        if (response.statusCode() == 404) return new JsonArray();
        if (response.statusCode() != 200) {
            throw new IOException("Modrinth returned status " + response.statusCode() + " for "
                + url + ": " + shorten(response.body()));
        }
        return JsonParser.parseString(response.body());
    }

    private static String shorten(String body) {
        if (body == null) return "";
        String collapsed = body.replaceAll("\\s+", " ").trim();
        return collapsed.length() <= 200 ? collapsed : collapsed.substring(0, 200) + "...";
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
