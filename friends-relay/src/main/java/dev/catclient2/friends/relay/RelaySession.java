package dev.catclient2.friends.relay;

import java.net.ServerSocket;
import java.net.Socket;

/**
 * One active relay slot: a host has registered under {@code token} and owns {@code poolIndex}'s
 * {@link ServerSocket} until a joiner connects (bridged once) or the host connection dies.
 */
final class RelaySession {
    final String token;
    final int poolIndex;
    final int externalPort;

    volatile Socket hostSocket;

    RelaySession(String token, int poolIndex, int externalPort, Socket hostSocket) {
        this.token = token;
        this.poolIndex = poolIndex;
        this.externalPort = externalPort;
        this.hostSocket = hostSocket;
    }
}
