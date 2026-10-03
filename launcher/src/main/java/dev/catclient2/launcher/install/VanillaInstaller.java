package dev.catclient2.launcher.install;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

public class VanillaInstaller {
    private final Path instanceDir;

    public VanillaInstaller(Path instanceDir) {
        this.instanceDir = instanceDir;
    }

    /**
     * Downloads the vanilla client jar, its libraries, and assets for the given version
     * into this instance's directory layout, mirroring the official launcher's structure:
     * versions/&lt;id&gt;/&lt;id&gt;.jar, libraries/, assets/.
     */
    public VersionMeta install(String minecraftVersion, Consumer<String> onStatus) throws IOException, InterruptedException {
        onStatus.accept("Fetching version manifest...");
        VersionManifest manifest = VersionManifest.fetch();
        VersionManifest.Entry entry = manifest.find(minecraftVersion);
        if (entry == null) {
            throw new IOException("Minecraft version " + minecraftVersion + " not found in version manifest");
        }

        onStatus.accept("Fetching version metadata...");
        VersionMeta meta = VersionMeta.fetch(entry.url);

        Path versionDir = instanceDir.resolve("versions").resolve(minecraftVersion);
        Files.createDirectories(versionDir);
        Files.writeString(versionDir.resolve(minecraftVersion + ".json"),
            new com.google.gson.Gson().toJson(meta));

        onStatus.accept("Downloading client jar...");
        Path clientJar = versionDir.resolve(minecraftVersion + ".jar");
        DownloadUtil.download(meta.downloads.client.url, clientJar, meta.downloads.client.sha1);

        onStatus.accept("Downloading libraries...");
        Path librariesDir = instanceDir.resolve("libraries");
        for (VersionMeta.Library library : meta.libraries) {
            if (!library.appliesToCurrentOs()) continue;
            if (library.downloads == null || library.downloads.artifact == null) continue;

            VersionMeta.ResolvedLibrary resolved = library.resolve();
            Path target = librariesDir.resolve(resolved.relativePath());
            DownloadUtil.download(resolved.url(), target, resolved.sha1());
        }

        onStatus.accept("Downloading assets...");
        Path assetsRoot = instanceDir.resolve("assets");
        AssetDownloader.downloadAssets(meta.assetIndex, assetsRoot,
            (done, total) -> onStatus.accept("Downloading assets... (" + done + "/" + total + ")"));

        return meta;
    }
}
