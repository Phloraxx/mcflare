package io.mcflare.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteResolverTest {
    @Test
    void normalizesDnsNames() {
        assertEquals("play.example.com", RouteResolver.normalizeHost(" Play.Example.COM. "));
    }

    @Test
    void probesDnsNamesButNotLocalOrIpLiterals() {
        assertTrue(RouteResolver.isProbeCandidate("play.example.com"));
        assertFalse(RouteResolver.isProbeCandidate("localhost"));
        assertFalse(RouteResolver.isProbeCandidate("127.0.0.1"));
        assertFalse(RouteResolver.isProbeCandidate("::1"));
    }
}
