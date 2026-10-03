package dev.catclient2.launcher.launch;

import dev.catclient2.launcher.install.FabricVersionMeta;
import dev.catclient2.launcher.install.VersionMeta;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ClasspathBuilder {
    public static List<Path> build(Path instanceDir, String vanillaVersion, VersionMeta vanillaMeta, FabricVersionMeta fabricMeta) {
        Set<Path> paths = new LinkedHashSet<>();
        Path librariesDir = instanceDir.resolve("libraries");

        for (VersionMeta.Library library : vanillaMeta.libraries) {
            if (!library.appliesToCurrentOs()) continue;
            if (library.downloads == null || library.downloads.artifact == null) continue;
            paths.add(librariesDir.resolve(library.resolve().relativePath()));
        }

        for (VersionMeta.Library library : fabricMeta.libraries) {
            paths.add(librariesDir.resolve(library.resolve().relativePath()));
        }

        paths.add(instanceDir.resolve("versions").resolve(vanillaVersion).resolve(vanillaVersion + ".jar"));

        return new ArrayList<>(paths);
    }

    public static String toClasspathString(List<Path> paths) {
        StringBuilder sb = new StringBuilder();
        for (Path path : paths) {
            if (!sb.isEmpty()) sb.append(File.pathSeparator);
            sb.append(path.toAbsolutePath());
        }
        return sb.toString();
    }
}
