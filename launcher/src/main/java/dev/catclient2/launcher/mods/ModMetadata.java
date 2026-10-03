package dev.catclient2.launcher.mods;

/**
 * The bits of a mod's own metadata that matter for building a shareable pack: which mod it is, how
 * it calls itself and which version it is.
 *
 * @param wrapper whether the jar is only a loader that ships the real mod as a nested jar
 *                (Essential's "essential-container"). The id and name then already point at the
 *                wrapped mod, but the declared version is the wrapper's and is ignored.
 */
public record ModMetadata(String id, String name, String version, String loader, boolean wrapper) {
    public ModMetadata(String id, String name, String version, String loader) {
        this(id, name, version, loader, false);
    }

    public String display() {
        String label = name == null || name.isBlank() ? id : name;
        return version == null || version.isBlank() ? label : label + " " + version;
    }
}
