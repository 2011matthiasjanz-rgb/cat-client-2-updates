package dev.catclient2.launcher.mods;

import dev.catclient2.friends.protocol.JoinSession;
import dev.catclient2.friends.protocol.ModRequirement;
import dev.catclient2.launcher.LauncherConfig;
import dev.catclient2.launcher.instance.LanAddressDetector;
import dev.catclient2.launcher.instance.PublicAddressResolver;
import dev.catclient2.launcher.install.DownloadUtil;
import dev.catclient2.launcher.install.FabricInstaller;
import dev.catclient2.launcher.install.VanillaInstaller;
import dev.catclient2.launcher.instance.InstanceManager;
import dev.catclient2.launcher.instance.ModJarProvisioner;
import dev.catclient2.launcher.install.VersionMeta;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Guest side of the friend system: brings a dedicated instance up to the host's Minecraft version,
 * mod loader and mod set, then hands the result back so the game can be started and connected to
 * the host's world.
 */
public final class JoinInstaller {
    private static final ModrinthApi MODRINTH = new ModrinthApi();

    private static final int VANILLA_END = 25;
    private static final int LOADER_END = 40;
    private static final int MODS_START = 45;

    private JoinInstaller() {
    }

    /**
     * @param instanceDir a dedicated folder for this friend, so their modpack never mixes with the
     *                    player's own instance
     */
    public record Prepared(Path instanceDir, String minecraftVersion, String fabricVersionId,
                           VersionMeta vanillaMeta, List<String> missing) {
        public boolean complete() {
            return missing.isEmpty();
        }
    }

    public interface Listener {
        void status(String message);

        void progress(int percent);

        /** Reports what happened to a single mod so the UI can show a checklist. */
        void modState(String modId, String state);
    }

    public static Prepared prepare(JoinSession session, Path instanceDir, Listener listener)
        throws IOException, InterruptedException {
        String minecraftVersion = session.minecraftVersion();
        String loaderVersion = session.loaderVersion();
        if (minecraftVersion == null || minecraftVersion.isBlank()) {
            throw new IOException("Your friend did not publish a Minecraft version");
        }
        if (session.loader() != null && !session.loader().isBlank() && !session.loader().equalsIgnoreCase("fabric")) {
            throw new IOException("This launcher only supports Fabric, but your friend runs " + session.loader());
        }
        if (loaderVersion == null || loaderVersion.isBlank()) loaderVersion = LauncherConfig.LOADER_VERSION;

        listener.status("Preparing Minecraft " + minecraftVersion + "...");
        listener.progress(2);
        Files.createDirectories(instanceDir);

        VanillaInstaller vanillaInstaller = new VanillaInstaller(instanceDir);
        VersionMeta vanillaMeta = vanillaInstaller.install(minecraftVersion, listener::status);
        listener.progress(VANILLA_END);

        FabricInstaller fabricInstaller = new FabricInstaller(instanceDir);
        String fabricVersionId = fabricInstaller.install(minecraftVersion, loaderVersion, listener::status);
        listener.progress(LOADER_END);

        List<String> missing = new ArrayList<>();
        if (minecraftVersion.equals(LauncherConfig.MINECRAFT_VERSION)) {
            ModJarProvisioner.provision(instanceDir);
        } else {
            missing.add("Cat Client 2 is built for Minecraft " + LauncherConfig.MINECRAFT_VERSION
                + " and was not installed for " + minecraftVersion);
        }

        Map<String, InstalledMod> existing = new HashMap<>();
        for (InstalledMod mod : InstalledMods.scan(instanceDir, false)) {
            existing.put(mod.id(), mod);
        }

        List<ModRequirement> requirements = session.mods() == null ? List.of() : session.mods();
        for (int i = 0; i < requirements.size(); i++) {
            ModRequirement requirement = requirements.get(i);
            listener.progress(MODS_START + (int) (50.0 * i / Math.max(1, requirements.size())));

            String failure = installMod(requirement, minecraftVersion, session.loader(), instanceDir, existing, listener);
            if (failure != null) missing.add(failure);
        }

        listener.progress(100);
        listener.status(missing.isEmpty()
            ? "Modpack ready"
            : "Modpack installed, " + missing.size() + " mod(s) could not be installed");

        return new Prepared(instanceDir, minecraftVersion, fabricVersionId, vanillaMeta, missing);
    }

    /** @return null on success, otherwise a human readable reason. */
    private static String installMod(ModRequirement requirement, String minecraftVersion, String loader,
                                     Path instanceDir, Map<String, InstalledMod> existing, Listener listener)
        throws InterruptedException {
        String modId = requirement.modId();
        String label = requirement.name() == null || requirement.name().isBlank() ? modId : requirement.name();

        InstalledMod current = existing.get(modId);
        boolean replacing = current != null && !sameVersion(current.version(), requirement.version());
        if (current != null && !replacing) {
            listener.modState(modId, "already installed (" + current.version() + ")");
            return null;
        }

        // The old jar is only put aside, never deleted: if the replacement cannot be fetched the
        // mod has to stay in a state the player can still launch with.
        Path backup = null;
        if (replacing) {
            backup = current.file().resolveSibling(current.file().getFileName() + ".old");
            try {
                Files.deleteIfExists(backup);
                Files.move(current.file(), backup, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                return label + ": could not replace the installed version (" + e.getMessage() + ")";
            }
        }

        try {
            // The host already pinned the exact file, so only fall back to Modrinth when it did not
            // manage to identify the mod.
            ModRequirement resolved = requirement;
            if (!requirement.resolved() || requirement.downloadUrl() == null) {
                listener.status("Looking up " + label + " on Modrinth...");
                resolved = MODRINTH.resolve(modId, label, requirement.version(), minecraftVersion,
                    loader == null || loader.isBlank() ? "fabric" : loader);
            }
            if (!resolved.resolved() || resolved.downloadUrl() == null) {
                String reason = resolved.note() == null ? "no download available" : resolved.note();
                listener.modState(modId, "not available: " + reason);
                return restore(backup, current) ? label + ": " + reason
                    : label + ": " + reason + " (the installed version was kept)";
            }

            String fileName = safeFileName(resolved.fileName(), modId, resolved.version());
            Path target = InstanceManager.modsDir(instanceDir).resolve(fileName);

            listener.status("Downloading " + label + "...");
            DownloadUtil.download(resolved.downloadUrl(), target, resolved.sha1());

            if (backup != null) Files.deleteIfExists(backup);

            // The host may already have pinned a different build, or the exact version was not
            // published for this Minecraft version - say so instead of silently swapping it.
            String detail = "installed (" + resolved.version() + ")";
            if (resolved.note() != null) detail = detail + " - " + resolved.note();
            listener.modState(modId, detail);
            return null;
        } catch (IOException e) {
            listener.modState(modId, "failed");
            boolean restored = restore(backup, current);
            return label + ": " + e.getMessage() + (restored ? " (the previous version was kept)" : "");
        }
    }

    /** Puts the replaced jar back. @return whether it is back in place. */
    private static boolean restore(Path backup, InstalledMod previous) {
        if (backup == null || previous == null) return true;
        try {
            Files.move(backup, previous.file(), StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean sameVersion(String a, String b) {
        if (a == null || b == null) return false;
        return a.equalsIgnoreCase(b);
    }

    private static String safeFileName(String preferred, String modId, String version) {
        String name = preferred;
        if (name == null || name.isBlank() || !name.toLowerCase(Locale.ROOT).endsWith(".jar")) {
            name = modId + "-" + (version == null || version.isBlank() ? "mod" : version) + ".jar";
        }
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    /**
     * Arguments that make the game connect to the host right after it starts, instead of
     * leaving the joiner in the main menu. Prefers a direct connection (same LAN, or a
     * UPnP-forwarded public address) since it has no extra latency and no dependency on a
     * third-party relay; falls back to the internet relay only when neither is available.
     */
    public static List<String> quickPlayArgs(JoinSession session) {
        String local = LanAddressDetector.localAddress();
        String direct = PublicAddressResolver.chooseFor(session.address(), session.publicAddress(), local);

        if (direct != null && !direct.isBlank()) {
            int port = session.publicPort() > 0 ? session.publicPort() : session.port();
            return List.of("--quickPlayMultiplayer", direct + ":" + port);
        }

        if (!session.relayAddress().isBlank() && session.relayPort() > 0) {
            return List.of("--quickPlayMultiplayer", session.relayAddress() + ":" + session.relayPort());
        }

        return List.of();
    }
}
