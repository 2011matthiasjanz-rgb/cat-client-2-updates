package dev.catclient2.friends.protocol;

/**
 * A single mod a joining player needs, as advertised by the host.
 *
 * <p>When the host's launcher was able to identify the mod on Modrinth it fills in the exact
 * project / version and the download url plus its SHA-1, so the joiner downloads byte-identical
 * files. {@code resolved == false} means the host shipped a mod that is not on Modrinth (or could
 * not be matched); the joiner then re-resolves it by name for the target Minecraft version and
 * loader, and reports it as a warning if that fails too.
 */
public record ModRequirement(
    String modId,
    String name,
    String version,
    String minecraftVersion,
    String loader,
    String projectId,
    String projectSlug,
    String versionId,
    String fileName,
    String downloadUrl,
    String sha1,
    long size,
    boolean resolved,
    String note
) {
    public static ModRequirement unresolved(String modId, String name, String version,
                                            String minecraftVersion, String loader, String note) {
        return new ModRequirement(modId, name, version, minecraftVersion, loader,
            null, null, null, null, null, null, 0, false, note);
    }
}
