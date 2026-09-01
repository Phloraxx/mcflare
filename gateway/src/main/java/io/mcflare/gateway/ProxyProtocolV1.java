package io.mcflare.gateway;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

/** Minimal HAProxy PROXY protocol v1 encoder for real client IP preservation. */
final class ProxyProtocolV1 {
    private ProxyProtocolV1() {}

    static byte[] encode(String sourceIp, int sourcePort, int destinationPort) throws IOException {
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
        String normalized = source.getHostAddress();
        int scope = normalized.indexOf('%');
        if (scope >= 0) normalized = normalized.substring(0, scope);
        String line = "PROXY " + family + " " + normalized + " " + destination
                + " " + sourcePort + " " + destinationPort + "\r\n";
        return line.getBytes(StandardCharsets.US_ASCII);
    }

    /** Parse only IP literals; forwarding headers must never trigger DNS resolution. */
    private static InetAddress parseIpLiteral(String value) throws IOException {
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
}
