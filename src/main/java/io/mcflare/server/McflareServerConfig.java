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
        boolean enabled = Boolean.parseBoolean(properties.getProperty("enabled", "true"));
        InetSocketAddress listen = parseAddress(properties.getProperty("listen", "127.0.0.1:25577"));
        int max = Integer.parseInt(properties.getProperty("max-connections", "256"));
        if (max < 1) throw new IOException("max-connections must be positive");
        return new McflareServerConfig(enabled, listen, max);
    }

    private static InetSocketAddress parseAddress(String value) throws IOException {
        String trimmed = value == null ? "" : value.trim();
        int colon = trimmed.lastIndexOf(':');
        if (colon <= 0 || colon == trimmed.length() - 1) throw new IOException("listen must be host:port");
        try { return new InetSocketAddress(trimmed.substring(0, colon), Integer.parseInt(trimmed.substring(colon + 1))); }
        catch (NumberFormatException e) { throw new IOException("invalid listen port", e); }
    }
}
