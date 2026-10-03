package dev.catclient2.launcher.mods;

import dev.catclient2.friends.protocol.AccountView;
import dev.catclient2.friends.protocol.JoinSession;
import dev.catclient2.friends.protocol.ModRequirement;
import dev.catclient2.launcher.LauncherConfig;
import dev.catclient2.launcher.instance.PublicAddressResolver;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Host side of the friend system: turns the instance that is about to start into a session
 * description that a friend can reproduce - same Minecraft version, same mod loader, same mods in
 * the same versions.
 */
public final class JoinPackBuilder {
    private static final ModrinthApi MODRINTH = new ModrinthApi();

    private JoinPackBuilder() {
    }

    /**
     * @param address      the LAN address friends should connect to, empty while the host has
     *                     not opened a world yet
     * @param publicAddress the routable address for friends outside the host's network, empty
     *                      when no port forward is available
     * @param publicPort   the port reachable at {@code publicAddress}
     * @param onStatus     progress text for the launcher UI
     */
    public static JoinSession build(AccountView host, String minecraftVersion, String loader, String loaderVersion,
                                    Path instanceDir, String address, int port,
                                    String publicAddress, int publicPort, Consumer<String> onStatus)
        throws IOException {

        List<InstalledMod> installed = InstalledMods.scan(instanceDir, false);
        List<ModRequirement> requirements = new ArrayList<>(installed.size());
        Set<String> seen = new HashSet<>();

        for (InstalledMod mod : installed) {
            // A wrapper and the mod it ships share an id, so a folder holding both must not end up
            // asking the joiner for the same mod twice.
            if (!seen.add(mod.id())) continue;

            onStatus.accept("Checking " + mod.name() + " on Modrinth...");

            ModRequirement requirement;
            try {
                requirement = MODRINTH.resolve(mod, minecraftVersion, loader);
            } catch (IOException e) {
                requirement = ModRequirement.unresolved(mod.id(), mod.name(), mod.version(), minecraftVersion, loader,
                    "lookup failed: " + e.getMessage());
            }
            requirements.add(requirement);
        }

        // An address alone is not enough: without a port there is nothing to connect to, which is
        // the case while no world is open.
        boolean joinable = address != null && !address.isBlank() && port > 0;
        return new JoinSession(UUID.randomUUID().toString(), host, minecraftVersion, loader, loaderVersion,
            address == null ? "" : address, port, requirements, LauncherConfig.MINECRAFT_VERSION, joinable,
            System.currentTimeMillis(),
            joinable ? null : "open a world (or a server) in game so friends have an address to join",
            publicAddress == null ? "" : publicAddress, publicPort);
    }
}
