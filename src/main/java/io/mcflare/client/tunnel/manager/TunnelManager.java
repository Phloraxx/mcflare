package io.mcflare.client.tunnel.manager;

import io.mcflare.client.McflareClient;
import io.mcflare.client.interfaces.mixin.IConnection;
import io.mcflare.client.tunnel.RunningTunnel;
import io.mcflare.client.tunnel.TunnelStatus;
import io.mcflare.core.MinecraftStatusProbe;
import net.minecraft.SharedConstants;
import net.minecraft.network.Connection;
import org.jetbrains.annotations.NotNull;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** Coordinates zero-config MCflare discovery and in-process WebSocket carriers. */
public final class TunnelManager {
    private static final long POSITIVE_TTL_MS = Duration.ofMinutes(10).toMillis();
    private static final long NEGATIVE_TTL_MS = Duration.ofSeconds(30).toMillis();
    private static final int DIRECT_CONNECT_TIMEOUT_MS = 1_200;
    private static final int SECURE_PREFERENCE_GRACE_MS = 1_500;
    private static final int DISCOVERY_TIMEOUT_MS = 4_500;

    private final List<RunningTunnel> runningTunnels = new ArrayList<>();
    private final ConcurrentHashMap<String, ProbeCache> probeCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> probeLocks = new ConcurrentHashMap<>();

    public RunningTunnel createTunnel(String host) {
        try {
            var tunnel = RunningTunnel.create(host);
            synchronized (runningTunnels) { runningTunnels.add(tunnel); }
            McflareClient.LOGGER.info("MCflare carrier ready for {} on {}", host, tunnel.access().tunnelAddress());
            return tunnel;
        } catch (Exception e) {
            McflareClient.LOGGER.error("Failed to create MCflare carrier for " + host, e);
            return null;
        }
    }

    public void closeTunnel(@NotNull RunningTunnel runningTunnel) {
        synchronized (runningTunnels) { runningTunnels.remove(runningTunnel); }
        runningTunnel.closeTunnel();
    }

    public void closeTunnels() {
        List<RunningTunnel> copy;
        synchronized (runningTunnels) {
            copy = List.copyOf(runningTunnels);
            runningTunnels.clear();
        }
        copy.forEach(RunningTunnel::closeTunnel);
    }

    public boolean shouldUseTunnel(String logicalHost, int logicalPort, InetSocketAddress resolvedAddress) {
        String host = normalizeHost(logicalHost);
        if (!isProbeCandidate(host)) return false;
        String cacheKey = host + ":" + logicalPort;

        long now = System.currentTimeMillis();
        var cached = probeCache.get(cacheKey);
        if (cached != null && cached.expiresAt() > now) return cached.supported();

        var lock = probeLocks.computeIfAbsent(cacheKey, ignored -> new Object());
        synchronized (lock) {
            try {
                cached = probeCache.get(cacheKey);
                now = System.currentTimeMillis();
                if (cached != null && cached.expiresAt() > now) return cached.supported();

                long started = System.nanoTime();
                boolean supported = discoverTransport(host, logicalPort, resolvedAddress);
                long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
                long ttl = supported ? POSITIVE_TTL_MS : NEGATIVE_TTL_MS;
                probeCache.put(cacheKey, new ProbeCache(supported, now + ttl));
                McflareClient.LOGGER.info("MCflare discovery {} for {} in {} ms",
                        supported ? "matched" : "missed", cacheKey, elapsedMs);
                return supported;
            } finally {
                probeLocks.remove(cacheKey, lock);
            }
        }
    }

    /**
     * Prefer MCflare whenever both transports are reachable. For ordinary servers,
     * direct TCP gets a short head start but we still give secure discovery a brief
     * grace period before committing to the direct route. If direct TCP is closed,
     * wait for the full MCflare discovery timeout.
     */
    private boolean discoverTransport(String host, int logicalPort, InetSocketAddress resolvedAddress) {
        var websocketProbe = CompletableFuture.supplyAsync(() ->
                MinecraftStatusProbe.probe(
                        host, logicalPort, SharedConstants.getProtocolVersion(), DISCOVERY_TIMEOUT_MS
                ).isSupported(), McflareClient.EXECUTOR);
        var directProbe = CompletableFuture.supplyAsync(
                () -> directTcpReachable(resolvedAddress), McflareClient.EXECUTOR);

        try {
            boolean directReachable = directProbe.get(
                    DIRECT_CONNECT_TIMEOUT_MS + 250L, TimeUnit.MILLISECONDS);
            long secureWait = directReachable ? SECURE_PREFERENCE_GRACE_MS : DISCOVERY_TIMEOUT_MS;
            try {
                return Boolean.TRUE.equals(websocketProbe.get(secureWait, TimeUnit.MILLISECONDS));
            } catch (java.util.concurrent.TimeoutException ignored) {
                return false;
            }
        } catch (Exception e) {
            try {
                return Boolean.TRUE.equals(websocketProbe.get(DISCOVERY_TIMEOUT_MS, TimeUnit.MILLISECONDS));
            } catch (Exception ignored) {
                McflareClient.LOGGER.debug("MCflare discovery failed for {}:{}", host, logicalPort);
                return false;
            }
        } finally {
            websocketProbe.cancel(true);
            directProbe.cancel(true);
        }
    }

    private static boolean directTcpReachable(InetSocketAddress resolvedAddress) {
        try (var socket = new Socket()) {
            socket.connect(resolvedAddress, DIRECT_CONNECT_TIMEOUT_MS);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public void prepareConnection(@NotNull TunnelStatus status, Connection connection) {
        if (status.runningTunnel() != null) {
            ((IConnection) connection).setRunningTunnel(status.runningTunnel());
        }
    }

    public TunnelStatus handleConnect(String logicalHost, int logicalPort, @NotNull InetSocketAddress resolvedAddress) {
        String host = normalizeHost(logicalHost);
        if (!shouldUseTunnel(host, logicalPort, resolvedAddress)) {
            return new TunnelStatus(null, TunnelStatus.State.DONT_USE);
        }

        var tunnel = createTunnel(host);
        if (tunnel == null) {
            return new TunnelStatus(null, TunnelStatus.State.FAILED_TO_DETERMINE);
        }
        return new TunnelStatus(tunnel, TunnelStatus.State.USE);
    }

    private static String normalizeHost(String host) {
        if (host == null) return "";
        String normalized = host.strip().toLowerCase(Locale.ROOT);
        return normalized.endsWith(".")
                ? normalized.substring(0, normalized.length() - 1)
                : normalized;
    }

    private static boolean isProbeCandidate(String host) {
        if (host.isBlank() || host.equals("localhost")) return false;
        if (host.indexOf(':') >= 0) return false; // IPv6 literal for now

        String[] parts = host.split("\\.");
        if (parts.length == 4) {
            boolean ipv4 = true;
            for (String part : parts) {
                try {
                    int n = Integer.parseInt(part);
                    if (n < 0 || n > 255) ipv4 = false;
                } catch (NumberFormatException e) {
                    ipv4 = false;
                }
            }
            if (ipv4) return false;
        }
        return true;
    }

    private record ProbeCache(boolean supported, long expiresAt) {}
}
