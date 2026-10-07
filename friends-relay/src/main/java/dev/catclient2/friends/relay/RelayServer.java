package dev.catclient2.friends.relay;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A tiny relay that lets a Minecraft host behind a router with no port forwarding be joined from
 * the internet: the host makes an outbound connection to this server (never needs an open inbound
 * port), and a joiner's plain, unmodified Minecraft client connects to one of this relay's own
 * public ports instead of the host's address. Once both sides are present the relay just pumps raw
 * bytes between them - it never parses the Minecraft protocol itself.
 *
 * <p>One control port accepts host registrations; a fixed pool of additional ports (one per
 * concurrent session) accepts the matching joiner connection. A shared port could work too, but
 * would require sniffing Minecraft's own handshake packet to route by token - the per-session port
 * pool avoids that complexity entirely, at the cost of a hard cap on concurrent sessions (the pool
 * size).
 */
public final class RelayServer {
    private static final byte OP_HOST_REGISTER = 0x01;
    private static final byte OP_OK = 0x03;
    private static final byte OP_ERROR = 0x04;

    private final int controlPort;
    private final int poolSize;
    private final int firstPoolPort;

    private final Map<String, RelaySession> sessionsByToken = new ConcurrentHashMap<>();
    private final Map<Integer, RelaySession> sessionsByPoolIndex = new ConcurrentHashMap<>();
    private final ExecutorService registrationExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public RelayServer(int controlPort, int poolSize, int firstPoolPort) {
        this.controlPort = controlPort;
        this.poolSize = poolSize;
        this.firstPoolPort = firstPoolPort;
    }

    /**
     * Blocks forever running the control accept loop on the calling thread - a process running this
     * relay has nothing else to do, so unlike the pool/sweeper threads (which are daemons purely so
     * a clean shutdown never has to join them), this one is deliberately what keeps the JVM alive.
     */
    public void start() throws IOException {
        ServerSocket control = new ServerSocket();
        control.bind(new InetSocketAddress(controlPort));
        System.out.println("[cat-relay] Control port listening on " + controlPort);

        for (int i = 0; i < poolSize; i++) {
            int poolIndex = i;
            int externalPort = firstPoolPort + i;
            ServerSocket poolSocket = new ServerSocket();
            poolSocket.bind(new InetSocketAddress(externalPort));

            Thread joinerThread = new Thread(() -> acceptJoinerLoop(poolIndex, poolSocket), "relay-pool-" + poolIndex);
            joinerThread.setDaemon(true);
            joinerThread.start();
        }
        System.out.println("[cat-relay] " + poolSize + " session port(s) starting at " + firstPoolPort);

        acceptControlLoop(control);
    }

    // -------------------------------------------------------------- host registration

    private void acceptControlLoop(ServerSocket control) {
        while (true) {
            try {
                Socket socket = control.accept();
                registrationExecutor.submit(() -> handleRegistration(socket));
            } catch (IOException e) {
                System.err.println("[cat-relay] Control accept failed: " + e.getMessage());
            }
        }
    }

    private void handleRegistration(Socket socket) {
        try {
            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());

            byte opcode = in.readByte();
            if (opcode != OP_HOST_REGISTER) {
                sendError(out, "Expected HOST_REGISTER");
                socket.close();
                return;
            }

            int tokenLength = in.readUnsignedByte();
            byte[] tokenBytes = new byte[tokenLength];
            in.readFully(tokenBytes);
            String token = new String(tokenBytes, StandardCharsets.UTF_8);

            if (sessionsByToken.containsKey(token)) {
                sendError(out, "Token already registered");
                socket.close();
                return;
            }

            Integer poolIndex = claimFreeSlot();
            if (poolIndex == null) {
                sendError(out, "Relay is full");
                socket.close();
                return;
            }

            // Relies on the OS to notice a dead host (no application-level heartbeat is sent on this
            // connection - see the class doc on RelayClient for why: anything written here would
            // race the eventual pump threads once a joiner arrives).
            socket.setKeepAlive(true);

            int externalPort = firstPoolPort + poolIndex;
            RelaySession session = new RelaySession(token, poolIndex, externalPort, socket);
            sessionsByToken.put(token, session);
            sessionsByPoolIndex.put(poolIndex, session);

            out.writeByte(OP_OK);
            out.writeInt(externalPort);
            out.flush();

            System.out.println("[cat-relay] Registered session (pool slot " + poolIndex + ", port " + externalPort + ")");

            // Deliberately does not read from this socket again: once a joiner arrives, bridge()
            // reuses this exact connection's streams as one half of the byte pipe, and a second
            // reader here would race it for bytes. Before a joiner arrives, the only way to notice
            // the host went away is the periodic stale sweep (sessionsByToken/-byPoolIndex only get
            // cleared from releaseSession(), called either by the sweep or once bridging ends).
        } catch (IOException e) {
            closeQuietly(socket);
        }
    }

    private Integer claimFreeSlot() {
        for (int i = 0; i < poolSize; i++) {
            if (!sessionsByPoolIndex.containsKey(i)) return i;
        }
        return null;
    }

    private static void sendError(DataOutputStream out, String reason) {
        try {
            byte[] reasonBytes = reason.getBytes(StandardCharsets.UTF_8);
            out.writeByte(OP_ERROR);
            out.writeByte(Math.min(255, reasonBytes.length));
            out.write(reasonBytes, 0, Math.min(255, reasonBytes.length));
            out.flush();
        } catch (IOException ignored) {
            // Best-effort - the connection is being torn down regardless.
        }
    }

    // -------------------------------------------------------------- joiner side

    private void acceptJoinerLoop(int poolIndex, ServerSocket poolSocket) {
        while (true) {
            try {
                Socket joiner = poolSocket.accept();
                RelaySession session = sessionsByPoolIndex.get(poolIndex);

                if (session == null || session.hostSocket == null || session.hostSocket.isClosed()) {
                    closeQuietly(joiner);
                    continue;
                }

                bridge(session, joiner);
            } catch (IOException e) {
                System.err.println("[cat-relay] Pool " + poolIndex + " accept failed: " + e.getMessage());
            }
        }
    }

    /** Pumps raw bytes in both directions until both finish, then frees the session's slot. */
    private void bridge(RelaySession session, Socket joiner) {
        Socket host = session.hostSocket;

        // Each direction only half-closes its destination's write side on EOF (not the whole socket)
        // - the other direction may still be mid-write on the very same socket pair, and fully
        // closing either socket there would truncate that in-flight data.
        Thread toHost = pumpThread(joiner, host, "relay-pump-to-host-" + session.poolIndex);
        Thread toJoiner = pumpThread(host, joiner, "relay-pump-to-joiner-" + session.poolIndex);
        toHost.start();
        toJoiner.start();

        Thread watcher = new Thread(() -> {
            try {
                toHost.join();
                toJoiner.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            closeQuietly(joiner);
            releaseSession(session, "session ended");
        }, "relay-watch-" + session.poolIndex);
        watcher.setDaemon(true);
        watcher.start();
    }

    private static Thread pumpThread(Socket from, Socket to, String name) {
        Thread thread = new Thread(() -> {
            try {
                from.getInputStream().transferTo(to.getOutputStream());
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

    private void releaseSession(RelaySession session, String reason) {
        if (sessionsByToken.remove(session.token) != null) {
            sessionsByPoolIndex.remove(session.poolIndex);
            closeQuietly(session.hostSocket);
            System.out.println("[cat-relay] Released session (pool slot " + session.poolIndex + "): " + reason);
        }
    }

    // -------------------------------------------------------------- housekeeping

    private static void closeQuietly(Socket socket) {
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {
        }
    }
}
