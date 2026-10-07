package dev.catclient2.relayforge;

import io.netty.channel.Channel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks hosts that registered themselves (via the {@code catclient2-register:<token>} handshake
 * marker) and are waiting for a joiner with the matching {@code catclient2-relay:<token>} marker.
 * Mirrors {@code RelaySession}/{@code sessionsByToken} in the standalone friends-relay module, but
 * keyed on a Netty {@link Channel} instead of a plain {@link java.net.Socket}, since this connection
 * was already accepted by Minecraft's own server socket.
 */
public final class RelayTokenRegistry {
    public static final RelayTokenRegistry INSTANCE = new RelayTokenRegistry();

    private final Map<String, Channel> hostsByToken = new ConcurrentHashMap<>();

    private RelayTokenRegistry() {
    }

    /** @return false if the token was already registered (caller should reject the connection). */
    public boolean register(String token, Channel hostChannel) {
        return hostsByToken.putIfAbsent(token, hostChannel) == null;
    }

    /** Non-null only while the host's connection is still open and unclaimed. */
    public Channel claim(String token) {
        Channel channel = hostsByToken.remove(token);
        return channel != null && channel.isActive() ? channel : null;
    }

    public void unregister(String token, Channel expected) {
        hostsByToken.remove(token, expected);
    }
}
