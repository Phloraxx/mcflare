package io.mcflare.core;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/** Version/loader-independent zero-config route selection. */
public final class RouteResolver implements Closeable {
    private static final long POSITIVE_TTL_MS = 10L * 60L * 1000L;
    private static final long NEGATIVE_TTL_MS = 30L * 1000L;
    private static final int DIRECT_CONNECT_TIMEOUT_MS = 1200;
    private static final int SECURE_PREFERENCE_GRACE_MS = 1500;
    private static final int DISCOVERY_TIMEOUT_MS = 4500;

    private final ConcurrentHashMap<String, ProbeCache> cache = new ConcurrentHashMap<String, ProbeCache>();
    private final ExecutorService executor = Executors.newCachedThreadPool(new DaemonThreadFactory());

    /** Returns null for ordinary TCP, or a ready local carrier for MCflare. */
    public LoopbackCarrier prepare(String logicalHost, int logicalPort,
                                   InetSocketAddress resolvedAddress,
                                   LoopbackCarrier.ErrorHandler errorHandler) {
        final String host = normalizeHost(logicalHost);
        if (!isProbeCandidate(host)) return null;
        final String key = host + ":" + logicalPort;
        final long now = System.currentTimeMillis();
        ProbeCache cached = cache.get(key);

        if (cached != null && cached.expiresAt > now) {
            if (!cached.mcflare) return null;
            return carrierFrom(openRequired(host), host, errorHandler);
        }

        CompletableFuture<Rfc6455Client> secure = CompletableFuture.supplyAsync(
                () -> tryOpen(host), executor);
        CompletableFuture<Boolean> direct = CompletableFuture.supplyAsync(
                () -> directTcpReachable(resolvedAddress), executor);

        try {
            CompletableFuture.anyOf(secure, direct).get(DISCOVERY_TIMEOUT_MS, TimeUnit.MILLISECONDS);

            // Secure success is decisive and wins immediately, even if direct probing is unfinished.
            if (secure.isDone()) {
                Rfc6455Client prepared = secure.getNow(null);
                if (prepared != null) {
                    direct.cancel(true);
                    cache.put(key, new ProbeCache(true, now + POSITIVE_TTL_MS));
                    return carrierFrom(prepared, host, errorHandler);
                }
            }

            if (direct.isDone()) {
                boolean directReachable = Boolean.TRUE.equals(direct.getNow(Boolean.FALSE));
                long secureWait = directReachable ? SECURE_PREFERENCE_GRACE_MS : DISCOVERY_TIMEOUT_MS;
                Rfc6455Client prepared = await(secure, secureWait);
                if (prepared != null) {
                    cache.put(key, new ProbeCache(true, now + POSITIVE_TTL_MS));
                    return carrierFrom(prepared, host, errorHandler);
                }
            }
        } catch (Exception ignored) {
            Rfc6455Client prepared = await(secure, DISCOVERY_TIMEOUT_MS);
            if (prepared != null) {
                cache.put(key, new ProbeCache(true, now + POSITIVE_TTL_MS));
                return carrierFrom(prepared, host, errorHandler);
            }
        }

        closeLateResult(secure);
        direct.cancel(true);
        cache.put(key, new ProbeCache(false, now + NEGATIVE_TTL_MS));
        return null;
    }

    public void invalidate(String host) {
        final String prefix = normalizeHost(host) + ":";
        for (String key : cache.keySet()) {
            if (key.startsWith(prefix)) cache.remove(key);
        }
    }

    private LoopbackCarrier carrierFrom(Rfc6455Client webSocket, final String host,
                                        final LoopbackCarrier.ErrorHandler externalHandler) {
        try {
            return LoopbackCarrier.start(webSocket, error -> {
                invalidate(host);
                if (externalHandler != null) externalHandler.onError(error);
            });
        } catch (IOException e) {
            closeQuietly(webSocket);
            invalidate(host);
            throw new IllegalStateException("MCflare secure transport unavailable for " + host, e);
        }
    }

    private static Rfc6455Client openRequired(String host) {
        try {
            return Rfc6455Client.connect(host, 443, McflareProtocol.PATH,
                    DISCOVERY_TIMEOUT_MS, 0, McflareProtocol.SUBPROTOCOL);
        } catch (IOException e) {
            throw new IllegalStateException("MCflare secure transport unavailable for " + host, e);
        }
    }

    private static Rfc6455Client tryOpen(String host) {
        try {
            return Rfc6455Client.connect(host, 443, McflareProtocol.PATH,
                    DISCOVERY_TIMEOUT_MS, 0, McflareProtocol.SUBPROTOCOL);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Rfc6455Client await(CompletableFuture<Rfc6455Client> future, long timeoutMs) {
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static void closeLateResult(CompletableFuture<Rfc6455Client> future) {
        future.thenAccept(RouteResolver::closeQuietly);
    }

    private static boolean directTcpReachable(InetSocketAddress address) {
        try (Socket socket = new Socket()) {
            socket.connect(address, DIRECT_CONNECT_TIMEOUT_MS);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    static String normalizeHost(String host) {
        if (host == null) return "";
        String normalized = host.trim().toLowerCase(Locale.ROOT);
        return normalized.endsWith(".") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    static boolean isProbeCandidate(String host) {
        if (host == null || host.length() == 0 || "localhost".equals(host)) return false;
        if (host.indexOf(':') >= 0) return false;
        String[] parts = host.split("\\.");
        if (parts.length != 4) return true;
        for (String part : parts) {
            try {
                int value = Integer.parseInt(part);
                if (value < 0 || value > 255) return true;
            } catch (NumberFormatException e) {
                return true;
            }
        }
        return false;
    }

    private static void closeQuietly(Closeable closeable) {
        if (closeable == null) return;
        try { closeable.close(); } catch (IOException ignored) {}
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }

    private static final class ProbeCache {
        private final boolean mcflare;
        private final long expiresAt;
        private ProbeCache(boolean mcflare, long expiresAt) {
            this.mcflare = mcflare;
            this.expiresAt = expiresAt;
        }
    }

    private static final class DaemonThreadFactory implements ThreadFactory {
        private final AtomicInteger count = new AtomicInteger();
        @Override public Thread newThread(Runnable task) {
            Thread thread = new Thread(task, "mcflare-discovery-" + count.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
