package io.mcflare.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public final class McflareServerConfig {
    public final boolean enabled;
    public final InetSocketAddress listen;
    public final int maxConnections;

    private McflareServerConfig(boolean enabled, InetSocketAddress listen, int maxConnections) {
        this.enabled = enabled;
        this.listen = listen;
        this.maxConnections = maxConnections;
    }

    public static McflareServerConfig load() throws IOException {
        Path file = Paths.get("config", "mcflare.properties");
        Properties properties = new Properties();
        if (Files.exists(file)) {
            try (InputStream input = Files.newInputStream(file)) { properties.load(input); }
        } else {
            Files.createDirectories(file.getParent());
            properties.setProperty("enabled", "true");
            properties.setProperty("listen", "127.0.0.1:25577");
            properties.setProperty("max-connections", "256");
            try (OutputStream output = Files.newOutputStream(file)) {
                properties.store(output, "MCflare local WebSocket endpoint; configure Orange/Tunnel ingress separately");
            }
        }
        boolean enabled = parseBoolean("enabled", properties.getProperty("enabled", "true"));
        InetSocketAddress listen = parseAddress(properties.getProperty("listen", "127.0.0.1:25577"));
        int max = parsePositiveInt("max-connections", properties.getProperty("max-connections", "256"));
        return new McflareServerConfig(enabled, listen, max);
    }

    private static boolean parseBoolean(String name, String value) throws IOException {
        String trimmed = value == null ? "" : value.trim();
        if ("true".equalsIgnoreCase(trimmed)) return true;
        if ("false".equalsIgnoreCase(trimmed)) return false;
        throw new IOException(name + " must be true or false");
    }

    private static int parsePositiveInt(String name, String value) throws IOException {
        try {
            int parsed = Integer.parseInt(value == null ? "" : value.trim());
            if (parsed < 1) throw new IOException(name + " must be positive");
            return parsed;
        } catch (NumberFormatException error) {
            throw new IOException(name + " must be an integer", error);
        }
    }

    private static InetSocketAddress parseAddress(String value) throws IOException {
        String trimmed = value == null ? "" : value.trim();
        int colon = trimmed.lastIndexOf(':');
        if (colon <= 0 || colon == trimmed.length() - 1) throw new IOException("listen must be host:port");
        String host = trimmed.substring(0, colon).trim();
        if (host.isEmpty()) throw new IOException("listen host must not be empty");
        final int port;
        try { port = Integer.parseInt(trimmed.substring(colon + 1).trim()); }
        catch (NumberFormatException error) { throw new IOException("invalid listen port", error); }
        if (port < 1 || port > 65535) throw new IOException("listen port must be between 1 and 65535");
        return new InetSocketAddress(host, port);
    }
}
