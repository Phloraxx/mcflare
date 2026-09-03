package io.mcflare.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteResolverTest {
    @TempDir Path tempDir;

    @Test
    void normalizesDnsNames() {
        assertEquals("play.example.com", RouteResolver.normalizeHost(" Play.Example.COM. "));
    }

    @Test
    void persistedKnownRouteFailsClosedInsteadOfUsingReachableDirectTcp() throws Exception {
        Path pins = tempDir.resolve("known-hosts-v1.txt");
        Files.write(pins, "known.example.invalid:25565\n".getBytes(StandardCharsets.UTF_8));
        try (ServerSocket directServer = new ServerSocket(0);
             RouteResolver resolver = new RouteResolver(pins)) {
            InetSocketAddress reachableDirect = new InetSocketAddress("127.0.0.1", directServer.getLocalPort());
            assertThrows(IllegalStateException.class,
                    () -> resolver.prepare("known.example.invalid", 25565, reachableDirect, null));
        }
    }

    @Test
    void probesDnsNamesButNotLocalOrIpLiterals() {
        assertTrue(RouteResolver.isProbeCandidate("play.example.com"));
        assertFalse(RouteResolver.isProbeCandidate("localhost"));
        assertFalse(RouteResolver.isProbeCandidate("127.0.0.1"));
        assertFalse(RouteResolver.isProbeCandidate("::1"));
    }

    @Test
    void negativeCacheRemainsBoundedAcrossManyOneOffHosts() throws Exception {
        try (RouteResolver resolver = new RouteResolver()) {
            Method remember = RouteResolver.class.getDeclaredMethod("rememberNegative", String.class, long.class);
            remember.setAccessible(true);
            long now = System.currentTimeMillis();
            for (int i = 0; i < 2_000; i++) {
                remember.invoke(resolver, "ordinary-" + i + ".example.test:25565", now);
            }

            Field cacheField = RouteResolver.class.getDeclaredField("negativeCache");
            cacheField.setAccessible(true);
            @SuppressWarnings("unchecked")
            ConcurrentHashMap<String, Long> cache =
                    (ConcurrentHashMap<String, Long>) cacheField.get(resolver);
            assertTrue(cache.size() <= 512, "negative cache grew to " + cache.size());
        }
    }
}
