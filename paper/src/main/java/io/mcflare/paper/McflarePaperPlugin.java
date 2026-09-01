package io.mcflare.paper;

import io.mcflare.gateway.McflareGateway;
import java.net.InetSocketAddress;
import org.bukkit.plugin.java.JavaPlugin;

/** Thin Paper/Bukkit lifecycle wrapper around the shared MCflare gateway. */
public final class McflarePaperPlugin extends JavaPlugin {
    private McflareGateway gateway;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!getConfig().getBoolean("enabled", true)) {
            getLogger().info("MCflare gateway is disabled by config");
            return;
        }

        try {
            InetSocketAddress listen = parseAddress(getConfig().getString("listen", "127.0.0.1:25577"));
            String backendHost = getConfig().getString("backend-host", "127.0.0.1");
            int backendPort = getConfig().getInt("backend-port", 0);
            if (backendPort <= 0) backendPort = getServer().getPort();
            int maxConnections = getConfig().getInt("max-connections", 256);
            boolean proxyProtocol = getConfig().getBoolean("proxy-protocol", true);
            InetSocketAddress backend = new InetSocketAddress(backendHost, backendPort);
            gateway = McflareGateway.startAsync(listen, backend, maxConnections, proxyProtocol,
                    getLogger()::info, getLogger()::severe);
            getLogger().info("MCflare gateway listening on " + listen + " for Minecraft " + backend
                    + " (PROXY v1 " + (proxyProtocol ? "enabled" : "disabled") + ")");
            if (proxyProtocol) {
                getLogger().info("Paper must have proxies.proxy-protocol=true in config/paper-global.yml");
            }
        } catch (Exception error) {
            getLogger().severe("Failed to start MCflare gateway: " + error);
        }
    }

    @Override
    public void onDisable() {
        if (gateway != null) {
            gateway.close();
            gateway = null;
        }
    }
    private static InetSocketAddress parseAddress(String value) {
        if (value == null) throw new IllegalArgumentException("listen address is required");
        String trimmed = value.trim();
        int colon = trimmed.lastIndexOf(':');
        if (colon <= 0 || colon == trimmed.length() - 1) {
            throw new IllegalArgumentException("host:port required: " + value);
        }
        String host = trimmed.substring(0, colon);
        int port = Integer.parseInt(trimmed.substring(colon + 1));
        if (port < 1 || port > 65535) throw new IllegalArgumentException("invalid port: " + port);
        return new InetSocketAddress(host, port);
    }
}
