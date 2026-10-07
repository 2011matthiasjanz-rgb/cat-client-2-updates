package dev.catclient2.friends.protocol;

import java.util.List;

/**
 * A game session published by a launcher while Minecraft is running. Friends of the host can
 * request permission to join it; once the host accepts, the joiner installs exactly the Minecraft
 * version, mod loader and mod versions listed here into a dedicated instance and auto-connects.
 *
 * <p>{@code address} and {@code port} describe the LAN address of the host. When the host's
 * router has been asked to open the port (UPnP), {@code publicAddress} and {@code publicPort}
 * carry the reachable address for friends outside the network.
 */
public record JoinSession(
    String id,
    AccountView host,
    String minecraftVersion,
    String loader,
    String loaderVersion,
    /** host:port the joiner connects to, empty when the host has not opened a world yet. */
    String address,
    int port,
    List<ModRequirement> mods,
    /** Minecraft version this launcher build (and therefore Cat Client 2) supports. */
    String launcherMinecraftVersion,
    boolean joinable,
    long updatedAt,
    String note,
    /** routable address for friends outside the host's LAN, empty otherwise. */
    String publicAddress,
    /** the public port; equals {@code port} when no public address is set. */
    int publicPort,
    /** address of the internet relay (see the {@code friends-relay} module), used as a fallback when
     *  neither the LAN address nor a UPnP-forwarded public address works; empty otherwise. */
    String relayAddress,
    /** the relay's port for this session, exclusively assigned to it. */
    int relayPort
) {
    public JoinSession {
        publicAddress = publicAddress == null ? "" : publicAddress;
        relayAddress = relayAddress == null ? "" : relayAddress;
        if (publicPort < 0) publicPort = 0;
        if (relayPort < 0) relayPort = 0;
    }

    public int modCount() {
        return mods == null ? 0 : mods.size();
    }
}
