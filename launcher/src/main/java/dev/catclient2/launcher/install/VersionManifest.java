package dev.catclient2.launcher.install;

import com.google.gson.Gson;

import java.io.IOException;
import java.util.List;

public class VersionManifest {
    private static final String MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private static final Gson GSON = new Gson();

    public Latest latest;
    public List<Entry> versions;

    public static VersionManifest fetch() throws IOException, InterruptedException {
        String json = DownloadUtil.getString(MANIFEST_URL);
        return GSON.fromJson(json, VersionManifest.class);
    }

    public Entry find(String id) {
        for (Entry entry : versions) {
            if (entry.id.equals(id)) return entry;
        }
        return null;
    }

    public static class Latest {
        public String release;
        public String snapshot;
    }

    public static class Entry {
        public String id;
        public String type;
        public String url;
        public String time;
        public String releaseTime;
        public String sha1;
        public int complianceLevel;
    }
}
