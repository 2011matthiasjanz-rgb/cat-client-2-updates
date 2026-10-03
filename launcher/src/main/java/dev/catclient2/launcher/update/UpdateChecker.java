package dev.catclient2.launcher.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.catclient2.launcher.LauncherConfig;
import dev.catclient2.launcher.install.DownloadUtil;

import java.util.Optional;

/**
 * Checks the dedicated updates repo's latest GitHub Release against the running launcher's own
 * version. This repo is deliberately separate from wherever this code itself is hosted (this
 * project's own git remote tracks upstream Meteor Client, which the maintainer cannot publish
 * releases to).
 */
public class UpdateChecker {
    private static final String LATEST_RELEASE_URL =
        "https://api.github.com/repos/2011matthiasjanz-rgb/cat-client-2-updates/releases/latest";

    private UpdateChecker() {
    }

    /**
     * Blocks on network I/O - call off the Swing event thread. Never throws: a failed check (no
     * internet, repo has no releases yet, GitHub is down) just means "no update available" rather
     * than disrupting the launcher's startup.
     */
    public static Optional<UpdateInfo> checkForUpdate() {
        try {
            String body = DownloadUtil.getString(LATEST_RELEASE_URL);
            JsonObject release = JsonParser.parseString(body).getAsJsonObject();

            String tag = release.has("tag_name") ? release.get("tag_name").getAsString() : null;
            if (tag == null || !VersionComparator.isNewer(LauncherConfig.LAUNCHER_VERSION, tag)) {
                return Optional.empty();
            }

            String downloadUrl = findAssetUrl(release);
            if (downloadUrl == null) return Optional.empty();

            String releaseNotesUrl = release.has("html_url") ? release.get("html_url").getAsString() : null;
            return Optional.of(new UpdateInfo(stripLeadingV(tag), downloadUrl, releaseNotesUrl));
        } catch (Exception e) {
            System.err.println("[cat-client] Update check failed: " + e.getMessage());
            return Optional.empty();
        }
    }

    /** Matches by extension rather than an exact filename, since jpackage names the installer after
     * the app and version (e.g. "Cat Client 2-1.2.0.exe"), not a fixed name. */
    private static String findAssetUrl(JsonObject release) {
        if (!release.has("assets") || !release.get("assets").isJsonArray()) return null;

        JsonArray assets = release.getAsJsonArray("assets");
        for (JsonElement element : assets) {
            JsonObject asset = element.getAsJsonObject();
            String name = asset.has("name") ? asset.get("name").getAsString() : "";
            if (name.endsWith(".exe") && asset.has("browser_download_url")) {
                return asset.get("browser_download_url").getAsString();
            }
        }
        return null;
    }

    private static String stripLeadingV(String tag) {
        return (tag.startsWith("v") || tag.startsWith("V")) ? tag.substring(1) : tag;
    }
}
