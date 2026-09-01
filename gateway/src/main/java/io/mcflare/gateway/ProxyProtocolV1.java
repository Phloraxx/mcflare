package io.mcflare.gateway;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

/** Minimal HAProxy PROXY protocol v1 codec for real client IP preservation. */
public final class ProxyProtocolV1 {
    private ProxyProtocolV1() {}

    public static byte[] encode(String sourceIp, int sourcePort, int destinationPort) throws IOException {
        if (sourceIp == null || sourceIp.trim().isEmpty()) return null;
        if (sourcePort < 1 || sourcePort > 65535) throw new IOException("invalid PROXY source port");
        if (destinationPort < 1 || destinationPort > 65535) throw new IOException("invalid PROXY destination port");

        InetAddress source = parseIpLiteral(sourceIp.trim());
        final String family;
        final String destination;
        if (source instanceof Inet4Address) {
            family = "TCP4";
            destination = "127.0.0.1";
        } else if (source instanceof Inet6Address) {
            family = "TCP6";
            destination = "::1";
        } else {
            throw new IOException("unsupported client IP family");
        }
        String normalized = normalize(source);
        String line = "PROXY " + family + " " + normalized + " " + destination
                + " " + sourcePort + " " + destinationPort + "\r\n";
        return line.getBytes(StandardCharsets.US_ASCII);
    }

    /** Parse one PROXY v1 line without DNS lookups. The CRLF must already be removed. */
    public static Source parse(String line) throws IOException {
        String[] parts = line.split(" ", -1);
        if (parts.length != 6 || !"PROXY".equals(parts[0])) throw new IOException("invalid PROXY v1 header");
        boolean tcp4 = "TCP4".equals(parts[1]);
        boolean tcp6 = "TCP6".equals(parts[1]);
        if (!tcp4 && !tcp6) throw new IOException("unsupported PROXY family");

        InetAddress source = parseIpLiteral(parts[2]);
        InetAddress destination = parseIpLiteral(parts[3]);
        if (tcp4 && (!(source instanceof Inet4Address) || !(destination instanceof Inet4Address)))
            throw new IOException("PROXY TCP4 address family mismatch");
        if (tcp6 && (!(source instanceof Inet6Address) || !(destination instanceof Inet6Address)))
            throw new IOException("PROXY TCP6 address family mismatch");

        int sourcePort = parsePort(parts[4], true);
        parsePort(parts[5], false);
        return new Source(source, sourcePort);
    }

    /** Parse only IP literals; forwarding metadata must never trigger DNS resolution. */
    public static InetAddress parseIpLiteral(String value) throws IOException {
        if (value == null || value.isEmpty()) throw new IOException("empty IP literal");
        if (value.indexOf(':') >= 0) {
            if (!value.matches("[0-9A-Fa-f:.]+")) throw new IOException("invalid IPv6 literal");
            try {
                InetAddress address = InetAddress.getByName(value);
                if (!(address instanceof Inet6Address)) throw new IOException("invalid IPv6 literal");
                return address;
            } catch (IllegalArgumentException e) {
                throw new IOException("invalid IPv6 literal", e);
            }
        }

        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) throw new IOException("invalid IPv4 literal");
        byte[] bytes = new byte[4];
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty() || parts[i].length() > 3) throw new IOException("invalid IPv4 literal");
            int part = 0;
            for (int j = 0; j < parts[i].length(); j++) {
                char c = parts[i].charAt(j);
                if (c < '0' || c > '9') throw new IOException("invalid IPv4 literal");
                part = part * 10 + (c - '0');
            }
            if (part > 255) throw new IOException("invalid IPv4 literal");
            bytes[i] = (byte) part;
        }
        return InetAddress.getByAddress(bytes);
    }

    private static int parsePort(String value, boolean allowZero) throws IOException {
        if (value == null || value.isEmpty()) throw new IOException("invalid PROXY port");
        int port = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') throw new IOException("invalid PROXY port");
            port = port * 10 + (c - '0');
            if (port > 65535) throw new IOException("invalid PROXY port");
        }
        if (!allowZero && port == 0) throw new IOException("invalid PROXY destination port");
        return port;
    }

    private static String normalize(InetAddress address) {
        String value = address.getHostAddress();
        int scope = value.indexOf('%');
        return scope >= 0 ? value.substring(0, scope) : value;
    }

    public static final class Source {
        private final InetAddress address;
        private final int port;
        private Source(InetAddress address, int port) { this.address = address; this.port = port; }
        public InetAddress address() { return address; }
        public int port() { return port; }
    }
}
