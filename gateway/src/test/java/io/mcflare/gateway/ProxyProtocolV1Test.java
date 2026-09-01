package io.mcflare.gateway;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ProxyProtocolV1Test {
    @Test void ipv4() throws Exception {
        assertEquals("PROXY TCP4 198.51.100.42 127.0.0.1 43210 25565\r\n",
                new String(ProxyProtocolV1.encode("198.51.100.42", 43210, 25565), StandardCharsets.US_ASCII));
    }
    @Test void ipv6() throws Exception {
        String line = new String(ProxyProtocolV1.encode("2001:db8::42", 43210, 25565), StandardCharsets.US_ASCII);
        assertTrue(line.startsWith("PROXY TCP6 2001:db8:"));
        assertTrue(line.endsWith(" ::1 43210 25565\r\n"));
    }
    @Test void missingIpDoesNotEmitHeader() throws Exception {
        assertNull(ProxyProtocolV1.encode(null, 43210, 25565));
    }
    @Test void parsesIpv4AndIpv6WithoutDns() throws Exception {
        ProxyProtocolV1.Source v4 = ProxyProtocolV1.parse("PROXY TCP4 198.51.100.42 127.0.0.1 43210 25565");
        assertEquals("198.51.100.42", v4.address().getHostAddress());
        assertEquals(43210, v4.port());
        ProxyProtocolV1.Source v6 = ProxyProtocolV1.parse("PROXY TCP6 2001:db8::42 ::1 0 25565");
        assertTrue(v6.address() instanceof java.net.Inet6Address);
        assertEquals(0, v6.port());
    }
    @Test void rejectsMalformedProxyHeaders() {
        assertThrows(java.io.IOException.class, () -> ProxyProtocolV1.parse("PROXY TCP4 example.com 127.0.0.1 1 25565"));
        assertThrows(java.io.IOException.class, () -> ProxyProtocolV1.parse("PROXY UDP4 198.51.100.42 127.0.0.1 1 25565"));
        assertThrows(java.io.IOException.class, () -> ProxyProtocolV1.parse("PROXY TCP6 198.51.100.42 ::1 1 25565"));
    }
    @Test void hostnameIsRejectedInsteadOfResolved() {
        assertThrows(java.io.IOException.class,
                () -> ProxyProtocolV1.encode("localhost", 43210, 25565));
        assertThrows(java.io.IOException.class,
                () -> ProxyProtocolV1.encode("example.com", 43210, 25565));
    }
    @Test void malformedIpv4IsRejected() {
        assertThrows(java.io.IOException.class,
                () -> ProxyProtocolV1.encode("999.51.100.42", 43210, 25565));
    }
}
