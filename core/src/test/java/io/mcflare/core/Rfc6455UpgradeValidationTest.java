package io.mcflare.core;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class Rfc6455UpgradeValidationTest {
    private static final String KEY = "MDEyMzQ1Njc4OWFiY2RlZg==";

    @Test void exactRequestedSubprotocolIsAccepted() throws Exception {
        Rfc6455Client.validateUpgradeResponse(response("Sec-WebSocket-Protocol: mcflare.v1\r\n"), KEY, "mcflare.v1");
    }

    @Test void mixedCaseSubprotocolIsRejected() {
        assertThrows(IOException.class, () -> Rfc6455Client.validateUpgradeResponse(
                response("Sec-WebSocket-Protocol: MCFLARE.V1\r\n"), KEY, "mcflare.v1"));
    }

    @Test void unsolicitedSubprotocolIsRejected() {
        assertThrows(IOException.class, () -> Rfc6455Client.validateUpgradeResponse(
                response("Sec-WebSocket-Protocol: mcflare.v1\r\n"), KEY, null));
    }

    @Test void unsolicitedExtensionIsRejected() {
        assertThrows(IOException.class, () -> Rfc6455Client.validateUpgradeResponse(
                response("Sec-WebSocket-Extensions: permessage-deflate\r\n"), KEY, null));
    }

    @Test void nonHttp11SwitchingProtocolsStatusIsRejected() {
        String invalid = response("").replace("HTTP/1.1 101", "HTTP/1.0 101");
        assertThrows(IOException.class, () -> Rfc6455Client.validateUpgradeResponse(invalid, KEY, null));
    }

    @Test void upgradeHeaderReadUsesAbsoluteDeadlineDespiteSlowDrip() throws Exception {
        try (ServerSocket server = new ServerSocket(0);
             Socket client = new Socket("127.0.0.1", server.getLocalPort());
             Socket peer = server.accept()) {
            Thread writer = new Thread(() -> {
                byte[] bytes = "HTTP/1.1 101 Switching Protocols\r\n".getBytes(StandardCharsets.US_ASCII);
                for (byte value : bytes) {
                    try {
                        peer.getOutputStream().write(value);
                        peer.getOutputStream().flush();
                        Thread.sleep(30L);
                    } catch (Exception stopped) {
                        return;
                    }
                }
            });
            writer.setDaemon(true);
            writer.start();
            assertThrows(SocketTimeoutException.class,
                    () -> Rfc6455Client.readHeaders(client.getInputStream(), client, 150));
        }
    }

    @Test void duplicateSingletonUpgradeHeadersAreRejected() {
        String duplicateAccept = response("Sec-WebSocket-Accept: duplicate\r\n");
        assertThrows(IOException.class, () -> Rfc6455Client.validateUpgradeResponse(duplicateAccept, KEY, null));

        String duplicateProtocol = response("Sec-WebSocket-Protocol: mcflare.v1\r\nSec-WebSocket-Protocol: mcflare.v1\r\n");
        assertThrows(IOException.class, () -> Rfc6455Client.validateUpgradeResponse(duplicateProtocol, KEY, "mcflare.v1"));
    }

    @Test void malformedOrFoldedUpgradeResponseHeaderIsRejected() {
        String malformed = response("").replace("Upgrade: websocket\r\n", " Upgrade: websocket\r\n");
        assertThrows(IOException.class, () -> Rfc6455Client.validateUpgradeResponse(malformed, KEY, null));
    }

    @Test void repeatedConnectionHeaderTokensRemainValid() throws Exception {
        String repeated = response("").replace("Connection: Upgrade\r\n",
                "Connection: keep-alive\r\nConnection: Upgrade\r\n");
        Rfc6455Client.validateUpgradeResponse(repeated, KEY, null);
    }

    @Test void formatsIpv6HostAuthorityCorrectly() {
        assertEquals("[::1]", Rfc6455Client.formatHostHeader("::1", 443));
        assertEquals("[::1]:8443", Rfc6455Client.formatHostHeader("::1", 8443));
        assertEquals("play.example.com", Rfc6455Client.formatHostHeader("play.example.com", 443));
    }

    @Test void invalidDnsHostShapeFailsBeforeNetworkUse() {
        assertThrows(IllegalArgumentException.class, () -> Rfc6455Client.connect(
                "bad..example.com", 443, "/mcflare", 100, 0, "mcflare.v1"));
        StringBuilder tooLong = new StringBuilder();
        for (int i = 0; i < 254; i++) tooLong.append('a');
        assertThrows(IllegalArgumentException.class, () -> Rfc6455Client.connect(
                tooLong.toString(), 443, "/mcflare", 100, 0, "mcflare.v1"));
    }

    @Test void invalidRequestPathAndSubprotocolFailBeforeNetworkUse() {
        assertThrows(IllegalArgumentException.class, () -> Rfc6455Client.connect(
                "localhost", 443, "/mcflare\r\nX-Test: injected", 100, 0, "mcflare.v1"));
        assertThrows(IllegalArgumentException.class, () -> Rfc6455Client.connect(
                "localhost", 443, "/mcflare", 100, 0, "mcflare.v1\r\nX-Test"));
    }

    @Test void arbitraryStatusLineContaining101IsRejected() {
        String invalid = response("").replace("HTTP/1.1 101 Switching Protocols", "NOTHTTP 101 Whatever");
        assertThrows(IOException.class, () -> Rfc6455Client.validateUpgradeResponse(invalid, KEY, null));
    }

    private static String response(String extra) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            String accept = Base64.getEncoder().encodeToString(sha1.digest(
                    (KEY + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.US_ASCII)));
            return "HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\n"
                    + "Connection: Upgrade\r\nSec-WebSocket-Accept: " + accept + "\r\n" + extra + "\r\n";
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
