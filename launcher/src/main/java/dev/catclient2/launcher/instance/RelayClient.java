package dev.catclient2.launcher.instance;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PushbackInputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Host side of the internet relay (see the {@code friends-relay} module): connects outbound to a
 * small always-on relay service and registers this session under a token. The relay then forwards
 * whatever connects to its own public address through to this same connection, so a friend can join
 * without the host's router needing any configuration (no port forward, no UPnP).
 *
 * <p>Once registered, nothing is sent on this connection until a joiner actually shows up - the
 * relay server does not read from a registered-but-idle connection either (see
 * {@code RelayServer.handleRegistration}, which deliberately stops reading after sending the OK
 * reply), so this side waits for the relay's first forwarded byte before opening the local
 * connection to Minecraft and starting the bidirectional pump. Opening that local connection any
 * earlier would have nothing to talk to yet and would race the eventual pump threads for the same
 * socket streams.
 *
 * <p>Degrades quietly like {@link PortForwarder}: if the relay is unreachable (including the normal
 * 30-60s cold start of a free-tier host), {@link #info()} simply stays {@code null} and the caller
 * falls back to whatever other address it already has (LAN, UPnP).
 */
public final class RelayClient {
    private static final byte OP_HOST_REGISTER = 0x01;
    private static final byte OP_OK = 0x03;

    private static final String REGISTER_PREFIX = "catclient2-register:";

    public record Connected(String externalHost, int externalPort) {
    }

    private final Socket controlSocket = new Socket();
    private volatile Connected connected;
    private volatile boolean closed;

    private RelayClient() {
    }

    /**
     * Starts connecting in the background and returns immediately - the actual handshake can take a
     * while (relay cold start), and the caller (the session-publishing poll loop) must never block
     * on it. Check {@link #info()}/{@link #isAlive()} later to see whether it came up.
     *
     * @param viaMinecraftPort true when {@code relayHost:controlPort} is not a dedicated friends-relay
     *                         process but a real Minecraft server running {@code relay-forge-mod} -
     *                         registration then has to look like a Minecraft handshake packet (the
     *                         only kind of traffic such a server's one open port actually accepts)
     *                         instead of this module's own byte-frame protocol, and there is no
     *                         separate external port to report back: the relay address *is*
     *                         {@code relayHost:controlPort} itself, since relay-forge-mod routes by
     *                         token on that same port rather than handing out a per-session one.
     */
    public static RelayClient start(String relayHost, int controlPort, String token, int localPort,
                                     boolean viaMinecraftPort, Consumer<String> onStatus) {
        RelayClient client = new RelayClient();

        Thread thread = new Thread(
            () -> client.connectAndWaitForJoiner(relayHost, controlPort, token, localPort, viaMinecraftPort, onStatus),
            "relay-client");
        thread.setDaemon(true);
        thread.start();

        return client;
    }

    private void connectAndWaitForJoiner(String relayHost, int controlPort, String token, int localPort,
                                          boolean viaMinecraftPort, Consumer<String> onStatus) {
        try {
            controlSocket.connect(new InetSocketAddress(relayHost, controlPort), 10_000);
            controlSocket.setKeepAlive(true);

            if (viaMinecraftPort) {
                registerViaMinecraftHandshake(relayHost, controlPort, token, onStatus);
            } else {
                registerViaRelayProtocol(relayHost, controlPort, token, onStatus);
            }
            if (connected == null) return;

            // Block here (no heartbeat, no other traffic on this connection) until the relay starts
            // forwarding a joiner's bytes - PushbackInputStream so the byte that woke us up is not
            // lost once the local Minecraft connection takes over reading from the same stream.
            PushbackInputStream relayIn = new PushbackInputStream(controlSocket.getInputStream(), 1);
            int firstByte = relayIn.read();
            if (firstByte < 0) return; // Host side closed before any joiner arrived.
            relayIn.unread(firstByte);

            onStatus.accept("A friend is connecting through the relay...");

            try (Socket local = new Socket("127.0.0.1", localPort)) {
                // Each direction only half-closes its destination's write side on EOF (not the whole
                // socket) - the other direction may still be mid-write on the very same socket, and
                // fully closing it there would truncate that in-flight data. Once both directions
                // have finished, the try-with-resources above closes what's left of `local`, and the
                // outer finally closes `controlSocket`.
                Thread toLocal = pumpThread(relayIn, local, "relay-client-to-local");
                Thread toRelay = pumpThread(local.getInputStream(), controlSocket, "relay-client-to-relay");
                toLocal.start();
                toRelay.start();
                toLocal.join();
                toRelay.join();
            }
        } catch (IOException e) {
            onStatus.accept("Could not reach the relay: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            close();
        }
    }

    private void registerViaRelayProtocol(String relayHost, int controlPort, String token,
                                           Consumer<String> onStatus) throws IOException {
        DataOutputStream out = new DataOutputStream(controlSocket.getOutputStream());
        DataInputStream in = new DataInputStream(controlSocket.getInputStream());

        byte[] tokenBytes = token.getBytes(StandardCharsets.UTF_8);
        out.writeByte(OP_HOST_REGISTER);
        out.writeByte(tokenBytes.length);
        out.write(tokenBytes);
        out.flush();

        byte opcode = in.readByte();
        if (opcode != OP_OK) {
            int length = in.readUnsignedByte();
            byte[] reasonBytes = new byte[length];
            in.readFully(reasonBytes);
            onStatus.accept("Relay rejected registration: " + new String(reasonBytes, StandardCharsets.UTF_8));
            close();
            return;
        }

        int externalPort = in.readInt();
        connected = new Connected(relayHost, externalPort);
        onStatus.accept("Relay ready at " + relayHost + ":" + externalPort);
    }

    /**
     * Registers by sending a normal Minecraft handshake packet whose hostName carries this module's
     * marker prefix - the only way to reach relay-forge-mod, since the server it runs on exposes no
     * port other than the ordinary Minecraft one. There is no reply to wait for: relay-forge-mod
     * cancels vanilla's handshake handling and simply keeps the connection open and unread until a
     * joiner's matching token claims it (see relay-forge-mod's ServerHandshakeMixin), so success here
     * just means "the handshake was sent" - connected is set optimistically once that write succeeds.
     */
    private void registerViaMinecraftHandshake(String relayHost, int controlPort, String token,
                                                Consumer<String> onStatus) throws IOException {
        DataOutputStream out = new DataOutputStream(controlSocket.getOutputStream());
        writeHandshakePacket(out, REGISTER_PREFIX + token, controlPort);

        connected = new Connected(relayHost, controlPort);
        onStatus.accept("Registered with " + relayHost + ":" + controlPort + " as a relay host");
    }

    /** Minecraft's own handshake packet wire format: a VarInt-length-prefixed packet, id 0x00. */
    private static void writeHandshakePacket(DataOutputStream out, String hostName, int port) throws IOException {
        java.io.ByteArrayOutputStream body = new java.io.ByteArrayOutputStream();
        java.io.DataOutputStream bodyOut = new java.io.DataOutputStream(body);
        writeVarInt(bodyOut, 0x00); // packet id: handshake/intention
        writeVarInt(bodyOut, 765); // protocol version placeholder - relay-forge-mod reads hostName before this matters
        writeString(bodyOut, hostName);
        bodyOut.writeShort(port);
        writeVarInt(bodyOut, 2); // next state: login

        byte[] bodyBytes = body.toByteArray();
        writeVarInt(out, bodyBytes.length);
        out.write(bodyBytes);
        out.flush();
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }

    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while (true) {
            int b = value & 0x7F;
            value >>>= 7;
            if (value != 0) {
                out.writeByte(b | 0x80);
            } else {
                out.writeByte(b);
                return;
            }
        }
    }

    private static Thread pumpThread(java.io.InputStream from, Socket to, String name) {
        Thread thread = new Thread(() -> {
            try {
                from.transferTo(to.getOutputStream());
            } catch (IOException ignored) {
                // Normal once either side closes.
            } finally {
                halfCloseOutput(to);
            }
        }, name);
        thread.setDaemon(true);
        return thread;
    }

    /** Shuts down only the write side, so the socket's other (still active) direction is unaffected. */
    private static void halfCloseOutput(Socket socket) {
        try {
            if (!socket.isOutputShutdown()) socket.shutdownOutput();
        } catch (IOException ignored) {
        }
    }

    /** Non-null once the relay has assigned this session a reachable external address/port. */
    public Connected info() {
        return connected;
    }

    public boolean isAlive() {
        return !closed && !controlSocket.isClosed();
    }

    public void close() {
        closed = true;
        try {
            controlSocket.close();
        } catch (IOException ignored) {
        }
    }
}
