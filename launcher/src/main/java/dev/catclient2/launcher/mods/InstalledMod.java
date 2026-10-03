package dev.catclient2.launcher.mods;

import java.nio.file.Path;

/**
 * A mod jar that is present in an instance, together with the identity read from inside it.
 */
public record InstalledMod(Path file, ModMetadata metadata) {
    public String id() {
        return metadata.id();
    }

    public String version() {
        return metadata.version();
    }

    public String name() {
        String name = metadata.name();
        return name == null || name.isBlank() ? metadata.id() : name;
    }
}
