package dev.catclient2.launcher.instance;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Finds the address friends outside the local network have to use.
 *
 * <p>A LAN address such as 192.168.1.20 is useless across the internet, so after a port forward
 * succeeded the public address is published instead. A private or CGNAT address is never returned:
 * in that case there is nothing to forward to and publishing it would only send friends into a
 * timeout.
 */
public final class PublicAddressResolver {
    /** Plain-text IP echo services; the first answer that is a routable address wins. */
    private static final List<String> SERVICES = List.of(
        "https://api4.ipify.org/",
        "https://ipv4.icanhazip.com/",
        "https://api.ipify.org/");

    private PublicAddressResolver() {
    }

    /** @return the public IPv4 address, or an empty string when it cannot be determined */
    public static String resolve() {
        HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

        for (String service : SERVICES) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(service))
                    .timeout(Duration.ofSeconds(4))
                    .header("User-Agent", "cat-client-2-launcher/1.0")
                    .GET()
                    .build();

                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) continue;

                String ip = response.body().trim();
                if (isPublicIpv4(ip)) return ip;
            } catch (IOException | RuntimeException e) {
                // Try the next service; without an address the LAN one is still published.
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "";
            }
        }
        return "";
    }

    /**
     * True for a routable IPv4 address. Loopback, link-local, private and the 100.64/10 carrier
     * grade NAT range are all rejected: none of them can be reached from the internet.
     */
    public static boolean isPublicIpv4(String value) {
        if (value == null || value.isBlank()) return false;

        String[] parts = value.split("\\.");
        if (parts.length != 4) return false;

        int[] octets = new int[4];
        for (int i = 0; i < 4; i++) {
            if (parts[i].isEmpty() || parts[i].length() > 3) return false;
            for (int c = 0; c < parts[i].length(); c++) {
                if (!Character.isDigit(parts[i].charAt(c))) return false;
            }
            octets[i] = Integer.parseInt(parts[i]);
            if (octets[i] > 255) return false;
        }

        if (octets[0] == 10 || octets[0] == 127 || octets[0] == 0) return false;
        if (octets[0] == 169 && octets[1] == 254) return false;
        if (octets[0] == 172 && octets[1] >= 16 && octets[1] <= 31) return false;
        if (octets[0] == 192 && octets[1] == 168) return false;
        if (octets[0] == 100 && octets[1] >= 64 && octets[1] <= 127) return false;
        if (octets[0] >= 224) return false;

        return true;
    }

    /**
     * Picks the address a guest has to dial.
     *
     * <p>When both are on the same local network the LAN address is used, because many routers do
     * not loop a connection from the LAN back out through the public address. Everyone else uses
     * the public address, which is the only one that works across the internet.
     *
     * @param hostLocal   the host's LAN address as published in the session
     * @param hostPublic  the host's public address, empty when no port forward was possible
     * @param guestLocal  the guest's own LAN address
     */
    public static String chooseFor(String hostLocal, String hostPublic, String guestLocal) {
        String local = hostLocal == null ? "" : hostLocal.trim();
        String pub = hostPublic == null ? "" : hostPublic.trim();
        String guest = guestLocal == null ? "" : guestLocal.trim();

        if (local.isEmpty()) return pub;
        if (pub.isEmpty()) return local;
        if (sameSubnet(local, guest)) return local;
        return pub;
    }

    /** Compares the first three octets, which is the granularity a home network uses. */
    static boolean sameSubnet(String a, String b) {
        if (a == null || b == null) return false;

        String[] left = a.split("\\.");
        String[] right = b.split("\\.");
        if (left.length != 4 || right.length != 4) return false;

        return left[0].equals(right[0]) && left[1].equals(right[1]) && left[2].equals(right[2]);
    }
}
