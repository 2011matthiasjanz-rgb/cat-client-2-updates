package dev.catclient2.launcher.install;

import com.google.gson.Gson;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Model for the version JSON the Fabric installer generates (e.g. versions/fabric-loader-0.18.2-1.21.11/*.json).
 * This is a partial "overlay" on top of the vanilla version JSON (referenced via "inheritsFrom") -
 * it only contributes its own mainClass, extra libraries and extra jvm/game arguments; everything
 * else (downloads, assetIndex, full library set) must come from the inherited vanilla VersionMeta.
 */
public class FabricVersionMeta {
    private static final Gson GSON = new Gson();

    public String id;
    public String inheritsFrom;
    public String mainClass;
    public List<VersionMeta.Library> libraries;
    public VersionMeta.Arguments arguments;

    public static FabricVersionMeta readFrom(Path jsonFile) throws IOException {
        return GSON.fromJson(Files.readString(jsonFile), FabricVersionMeta.class);
    }
}
