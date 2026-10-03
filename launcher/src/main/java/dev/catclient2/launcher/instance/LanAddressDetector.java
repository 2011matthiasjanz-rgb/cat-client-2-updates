package dev.catclient2.launcher.instance;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the address a friend has to connect to after "Open to LAN": the vanilla log announces the
 * port, and the address is this machine's LAN IP. That way nobody has to type an IP into the
 * launcher by hand while the game is running.
 */
public final class LanAddressDetector {
    /** Vanilla logs "Local game hosted on port 61234" once the world is open. */
    private static final Pattern LAN_PORT = Pattern.compile("hosted on port (\\d+)", Pattern.CASE_INSENSITIVE);

    private LanAddressDetector() {
    }

    /**
     * @return the port the world is hosted on, or -1 while nothing is hosted yet.
     */
    public static int hostedPort(Path instanceDir) {
        Path log = instanceDir.resolve("logs").resolve("latest.log");
        if (!Files.isRegularFile(log)) return -1;

        try {
            List<String> lines;
            try (var stream = Files.lines(log, StandardCharsets.UTF_8)) {
                lines = stream.toList();
            }
            for (int i = lines.size() - 1; i >= 0; i--) {
                Matcher matcher = LAN_PORT.matcher(lines.get(i));
                if (matcher.find()) return Integer.parseInt(matcher.group(1));
            }
        } catch (IOException | NumberFormatException e) {
            return -1;
        }
        return -1;
    }

    /** This machine's address on the local network, or an empty string when there is none. */
    public static String localAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) return "";

            for (NetworkInterface networkInterface : Collections.list(interfaces)) {
                if (!networkInterface.isUp() || networkInterface.isLoopback()) continue;

                for (InetAddress address : Collections.list(networkInterface.getInetAddresses())) {
                    if (address instanceof Inet4Address && !address.isLoopbackAddress() && !address.isAnyLocalAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (SocketException e) {
            return "";
        }
        return "";
    }

    /**
     * Quick check whether the machine sits behind a router: if opening a UDP socket leaves it with
     * a private address, a port forward or a tunnel is needed for friends outside the network.
     */
    public static boolean behindNat() {
        try (DatagramSocket socket = new DatagramSocket()) {
            // 192.0.2.1 is the reserved documentation range: nothing is sent, this only picks a route.
            socket.connect(InetAddress.getByName("192.0.2.1"), 9);
            InetAddress local = socket.getLocalAddress();
            return local != null && local.isSiteLocalAddress();
        } catch (IOException e) {
            return true;
        }
    }
}
