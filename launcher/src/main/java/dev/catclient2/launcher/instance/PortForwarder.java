package dev.catclient2.launcher.instance;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.DatagramPacket;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Asks the router to forward the hosted world to the internet, the same way the Essential mod's
 * connection manager does, so friends outside the local network do not need a manual port forward.
 *
 * <p>This speaks UPnP IGD directly (SSDP discovery plus a SOAP call) so no native library is needed.
 * Everything here degrades quietly: no router, UPnP disabled, CGNAT or a busy device all end up as
 * {@code null} results, and the caller falls back to the LAN address.
 */
public final class PortForwarder {
    private static final String SSDP_ADDRESS = "239.255.255.250";
    private static final int SSDP_PORT = 1900;
    private static final int DISCOVERY_TIMEOUT_MILLIS = 1500;
    private static final int SOAP_TIMEOUT_MILLIS = 2500;

    private PortForwarder() {
    }

    /** What happened to the mapping, so the UI can say something honest. */
    public record Mapping(boolean mapped, String reason) {
        public boolean mapped() {
            return mapped;
        }
    }

    // ------------------------------------------------------------------ public api

    /**
     * Opens a mapping for the port the world is hosted on.
     *
     * @param port         the port announced by the game log
     * @param protocol     "TCP" for Minecraft
     * @param internalIp   this machine's address on the local network
     * @param log          progress text for the launcher UI
     * @return the mapping result, never null
     */
    public static Mapping open(int port, String protocol, String internalIp, Consumer<String> log) {
        if (internalIp == null || internalIp.isBlank()) {
            return new Mapping(false, "no local network address found");
        }
        if (port <= 0 || port > 65535) {
            return new Mapping(false, "Minecraft is not hosting a world yet");
        }

        String controlUrl;
        try {
            controlUrl = discoverControlUrl();
        } catch (IOException e) {
            return new Mapping(false, "no router that speaks UPnP found (" + e.getMessage() + ")");
        }
        if (controlUrl == null) {
            return new Mapping(false, "no router that speaks UPnP found");
        }

        log.accept("Asking the router to open port " + port + "...");
        try {
            soap(controlUrl, "AddPortMapping", port, protocol, internalIp,
                "Cat Client 2 (Minecraft)");
            return new Mapping(true, "port " + port + " opened on the router");
        } catch (SoapFault fault) {
            return new Mapping(false, fault.getMessage());
        } catch (IOException e) {
            return new Mapping(false, "the router refused the request (" + e.getMessage() + ")");
        }
    }

    /**
     * Removes a mapping again. Best effort: if the router is gone the port forwarding is gone with
     * it, and a failure here must never keep the launcher from exiting.
     */
    public static void close(int port, String protocol) {
        if (port <= 0 || port > 65535) return;

        try {
            String controlUrl = discoverControlUrl();
            if (controlUrl == null) return;
            soap(controlUrl, "DeletePortMapping", port, protocol, null, null);
        } catch (IOException | RuntimeException e) {
            // Nothing to do: the mapping either expired or the router is unreachable.
        }
    }

    // ------------------------------------------------------------------ discovery

    private static String discoverControlUrl() throws IOException {
        List<String> locations = ssdpLocations();
        if (locations.isEmpty()) return null;

        for (String location : locations) {
            try {
                String description = fetch(location);
                if (!isInternetGatewayDevice(description)) continue;

                String service = serviceControlUrl(description);
                if (service != null) return service;
            } catch (IOException | RuntimeException e) {
                // Try the next candidate; routers answer SSDP even when the device is offline.
            }
        }
        return null;
    }

    private static List<String> ssdpLocations() throws IOException {
        String search = "M-SEARCH * HTTP/1.1\r\n"
            + "HOST: " + SSDP_ADDRESS + ":" + SSDP_PORT + "\r\n"
            + "MAN: \"ssdp:discover\"\r\n"
            + "MX: 1\r\n"
            + "ST: urn:schemas-upnp-org:device:InternetGatewayDevice:1\r\n\r\n";

        byte[] payload = search.getBytes(StandardCharsets.UTF_8);
        List<String> found = new ArrayList<>();

        try (MulticastSocket socket = new MulticastSocket()) {
            socket.setSoTimeout(DISCOVERY_TIMEOUT_MILLIS);
            socket.setReuseAddress(true);

            // Routers answer the multicast group, but only if a route exists; asking every local
            // IPv4 address unicast as well covers machines where that route is missing.
            List<InetAddress> targets = new ArrayList<>();
            targets.add(InetAddress.getByName(SSDP_ADDRESS));
            for (NetworkInterface networkInterface : interfaces()) {
                for (InterfaceAddress address : networkInterface.getInterfaceAddresses()) {
                    if (address.getAddress() instanceof Inet4Address && !address.getAddress().isLoopbackAddress()) {
                        targets.add(address.getAddress());
                    }
                }
            }

            for (InetAddress target : targets) {
                try {
                    socket.send(new DatagramPacket(payload, payload.length, target, SSDP_PORT));
                } catch (IOException e) {
                    // This interface cannot reach the discovery address; the next one might.
                }
            }

            byte[] buffer = new byte[8192];
            long deadline = System.currentTimeMillis() + DISCOVERY_TIMEOUT_MILLIS;
            while (System.currentTimeMillis() < deadline) {
                DatagramPacket response = new DatagramPacket(buffer, buffer.length);
                try {
                    socket.receive(response);
                } catch (SocketTimeoutException e) {
                    break;
                }

                String location = header(new String(response.getData(), 0, response.getLength(),
                    StandardCharsets.UTF_8), "location");
                if (location != null && !found.contains(location)) found.add(location);
            }
        }
        return found;
    }

    private static List<NetworkInterface> interfaces() {
        try {
            return Collections.list(NetworkInterface.getNetworkInterfaces());
        } catch (IOException e) {
            return List.of();
        }
    }

    private static String header(String response, String name) {
        for (String line : response.split("\r\n")) {
            int colon = line.indexOf(':');
            if (colon < 0) continue;
            if (line.substring(0, colon).trim().equalsIgnoreCase(name)) {
                return line.substring(colon + 1).trim();
            }
        }
        return null;
    }

    private static boolean isInternetGatewayDevice(String description) {
        return description.contains("InternetGatewayDevice");
    }

    /**
     * Reads the control URL of the first WANIPConnection/WANPPPConnection service out of the
     * device description.
     */
    private static String serviceControlUrl(String description) {
        try {
            Document document = parse(description);
            NodeList services = document.getElementsByTagName("service");
            for (int i = 0; i < services.getLength(); i++) {
                Element service = (Element) services.item(i);
                String serviceType = text(service, "serviceType");
                if (serviceType == null) {
                    continue;
                }
                if (!serviceType.contains("WANIPConnection") && !serviceType.contains("WANPPPConnection")) {
                    continue;
                }

                String control = text(service, "controlURL");
                if (control == null) continue;

                NodeList devices = document.getElementsByTagName("URLBase");
                String base = devices.getLength() > 0 ? text((Element) devices.item(0), null) : null;
                if (base == null || base.isBlank()) base = baseOf(description);
                return base + control;
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    private static String text(Element parent, String tag) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element element && (tag == null || element.getTagName().equals(tag))) {
                return element.getTextContent().trim();
            }
        }
        return null;
    }

    /** Falls back to the scheme, host and port the description itself was served from. */
    private static String baseOf(String descriptionUrl) {
        try {
            URI uri = URI.create(descriptionUrl);
            return uri.getScheme() + "://" + uri.getAuthority();
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // The description comes off the local network, but XML parsing should still not resolve
        // external entities.
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setNamespaceAware(false);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    private static String fetch(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(SOAP_TIMEOUT_MILLIS);
        connection.setReadTimeout(SOAP_TIMEOUT_MILLIS);
        connection.setRequestProperty("User-Agent", "cat-client-2-launcher/1.0");
        try (var in = connection.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } finally {
            connection.disconnect();
        }
    }

    // ------------------------------------------------------------------ soap

    private static void soap(String controlUrl, String action, int port, String protocol,
                             String internalIp, String description) throws IOException {
        String body = "<u:" + action + " xmlns:u=\"urn:schemas-upnp-org:service:WANIPConnection:1\">"
            + "<NewRemoteHost></NewRemoteHost>"
            + "<NewExternalPort>" + port + "</NewExternalPort>"
            + "<NewProtocol>" + protocol + "</NewProtocol>"
            + "<NewInternalPort>" + port + "</NewInternalPort>"
            + (internalIp == null
                ? ""
                : "<NewInternalClient>" + internalIp + "</NewInternalClient>")
            + (description == null
                ? ""
                : "<NewPortMappingDescription>" + description + "</NewPortMappingDescription>")
            + "<NewEnabled>1</NewEnabled>"
            + "<NewPortMappingLifetime>0</NewPortMappingLifetime>"
            + "</u:" + action + ">";

        // 0 is "permanent" in IGD; some devices only accept a finite lease, so retry once with an
        // hour and let the OS clean it up.
        try {
            call(controlUrl, action, body);
        } catch (SoapFault fault) {
            if (!"725".equals(fault.errorCode())) throw fault;
            call(controlUrl, action,
                body.replace("<NewPortMappingLifetime>0</NewPortMappingLifetime>",
                    "<NewPortMappingLifetime>3600</NewPortMappingLifetime>"));
        }
    }

    private static void call(String controlUrl, String action, String body) throws IOException {
        String envelope = "<?xml version=\"1.0\"?>"
            + "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\""
            + " s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">"
            + "<s:Body>" + body + "</s:Body>"
            + "</s:Envelope>";

        HttpURLConnection connection = (HttpURLConnection) URI.create(controlUrl).toURL().openConnection();
        connection.setConnectTimeout(SOAP_TIMEOUT_MILLIS);
        connection.setReadTimeout(SOAP_TIMEOUT_MILLIS);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"");
        connection.setRequestProperty("SOAPAction", "\"" + action + "\"");
        connection.setRequestProperty("User-Agent", "cat-client-2-launcher/1.0");

        byte[] payload = envelope.getBytes(StandardCharsets.UTF_8);
        connection.setRequestProperty("Content-Length", String.valueOf(payload.length));
        try (OutputStream out = connection.getOutputStream()) {
            out.write(payload);
        }

        int status;
        String response;
        try (var in = connection.getInputStream()) {
            response = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            status = connection.getResponseCode();
        } catch (IOException e) {
            // A SOAP fault is answered with 500, so the error body comes from the error stream.
            response = readError(connection);
            status = connection.getResponseCode();
        } finally {
            connection.disconnect();
        }

        if (status >= 400) {
            throw new SoapFault(errorCode(response), errorDescription(response, status));
        }
    }

    private static String readError(HttpURLConnection connection) {
        try (var in = connection.getErrorStream()) {
            if (in == null) return "";
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private static String errorCode(String response) {
        String code = between(response, "<errorCode>", "</errorCode>");
        return code == null ? "" : code;
    }

    private static String errorDescription(String response, int status) {
        String description = between(response, "<errorDescription>", "</errorDescription>");
        if (description != null) {
            return "router says " + description.trim()
                + (codeSuffix(response));
        }
        return "HTTP " + status;
    }

    private static String codeSuffix(String response) {
        String code = errorCode(response);
        return code.isEmpty() ? "" : " (code " + code + ")";
    }

    private static String between(String text, String open, String close) {
        if (text == null) return null;
        int start = text.indexOf(open);
        if (start < 0) return null;
        int end = text.indexOf(close, start);
        if (end < 0) return null;
        return text.substring(start + open.length(), end);
    }

    /** An error the router itself reported, as opposed to a connection problem. */
    private static final class SoapFault extends IOException {
        private final String errorCode;

        private SoapFault(String errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }

        String errorCode() {
            return errorCode;
        }
    }

    // ------------------------------------------------------------------ reachability

    /**
     * Checks whether something is listening and accepts a connection, so a mapping is only
     * advertised when the game is really reachable on it. The port is closed again right after.
     */
    public static boolean isReachable(String host, int port, int timeoutMillis) {
        if (host == null || host.isBlank() || port <= 0) return false;

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMillis);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** True when the given address is on this machine's local network, so a direct connect works. */
    public static boolean isLocalAddress(String host) {
        if (host == null || host.isBlank()) return false;
        try {
            return InetAddress.getByName(host).isSiteLocalAddress()
                || InetAddress.getByName(host).isLoopbackAddress();
        } catch (IOException e) {
            return false;
        }
    }
}
