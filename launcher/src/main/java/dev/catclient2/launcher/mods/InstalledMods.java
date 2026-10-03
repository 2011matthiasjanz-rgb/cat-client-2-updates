package dev.catclient2.launcher.mods;

import dev.catclient2.launcher.instance.InstanceManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * What is currently sitting in an instance's mods folder, read straight from the jars themselves.
 */
public final class InstalledMods {
    /** Mods the launcher installs itself, so they are never part of a shared pack. */
    private static final List<String> LAUNCHER_OWNED = List.of("meteorclient");

    private InstalledMods() {
    }

    public static List<InstalledMod> scan(Path instanceDir) {
        return scan(instanceDir, true);
    }

    /**
     * @param includeLauncherMods whether the launcher-provided mods (Cat Client 2 itself) are part
     *                            of the result - they are not for a pack that is shared with friends.
     */
    public static List<InstalledMod> scan(Path instanceDir, boolean includeLauncherMods) {
        Path modsDir = InstanceManager.modsDir(instanceDir);
        if (!Files.isDirectory(modsDir)) return List.of();

        List<InstalledMod> mods = new ArrayList<>();
        try (Stream<Path> files = Files.list(modsDir)) {
            List<Path> jars = files
                .filter(path -> {
                    String name = path.getFileName().toString().toLowerCase();
                    // .jar.tmp is a download in progress, .jar.old a mod that is being replaced.
                    if (!name.endsWith(".jar") || name.endsWith(".jar.tmp") || name.endsWith(".jar.old")) {
                        return false;
                    }
                    return Files.isRegularFile(path);
                })
                .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                .toList();

            for (Path jar : jars) {
                ModMetadata metadata;
                try {
                    metadata = ModMetadataReader.read(jar);
                } catch (IOException e) {
                    continue;
                }
                if (metadata == null) continue;
                if (!includeLauncherMods && LAUNCHER_OWNED.contains(metadata.id())) continue;

                mods.add(new InstalledMod(jar, metadata));
            }
        } catch (IOException e) {
            return List.of();
        }

        return mods;
    }
}
