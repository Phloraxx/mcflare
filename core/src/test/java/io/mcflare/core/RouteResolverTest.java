package io.mcflare.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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
}
