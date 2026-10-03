package dev.catclient2.launcher.install;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.BiConsumer;

public class AssetDownloader {
    private static final String RESOURCES_BASE = "https://resources.download.minecraft.net/";
    private static final Gson GSON = new Gson();

    /**
     * Downloads the asset index itself plus every referenced object, into the standard
     * &lt;assetsRoot&gt;/indexes and &lt;assetsRoot&gt;/objects layout.
     *
     * @param onProgress called with (downloaded, total) object counts.
     */
    public static void downloadAssets(VersionMeta.AssetIndexRef indexRef, Path assetsRoot, BiConsumer<Integer, Integer> onProgress)
        throws IOException, InterruptedException {
        Path indexFile = assetsRoot.resolve("indexes").resolve(indexRef.id + ".json");
        DownloadUtil.download(indexRef.url, indexFile, indexRef.sha1);

        JsonObject index = GSON.fromJson(Files.readString(indexFile), JsonObject.class);
        JsonObject objects = index.getAsJsonObject("objects");

        Path objectsDir = assetsRoot.resolve("objects");

        int total = objects.size();
        int done = 0;

        for (Map.Entry<String, com.google.gson.JsonElement> entry : objects.entrySet()) {
            JsonObject obj = entry.getValue().getAsJsonObject();
            String hash = obj.get("hash").getAsString();
            String prefix = hash.substring(0, 2);

            Path target = objectsDir.resolve(prefix).resolve(hash);
            String url = RESOURCES_BASE + prefix + "/" + hash;

            DownloadUtil.download(url, target, hash);

            done++;
            onProgress.accept(done, total);
        }
    }
}
