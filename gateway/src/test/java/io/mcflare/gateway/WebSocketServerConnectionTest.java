package io.mcflare.gateway;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class WebSocketServerConnectionTest {
    @Test void handshakeCapturesCloudflareMetadata() throws Exception {
        try (Harness h = new Harness()) {
            Socket client = h.connect("/mcflare", "mcflare.v1",
                    "CF-Connecting-IP: 198.51.100.42\r\nCF-Ray: test-ray\r\n");
            WebSocketServerConnection ws = h.accept.get(2, TimeUnit.SECONDS);
            assertEquals("198.51.100.42", ws.header("cf-connecting-ip"));
            assertEquals("test-ray", ws.header("cf-ray"));
            ws.close(); client.close();
        }
    }

    @Test void fragmentedBinaryAndInterleavedPingRemainAByteStream() throws Exception {
        try (Harness h = new Harness()) {
            Socket client = h.connect("/mcflare", "mcflare.v1", "");
            WebSocketServerConnection ws = h.accept.get(2, TimeUnit.SECONDS);
            OutputStream out = client.getOutputStream();
            out.write(maskedFrame(0x2, false, "hello".getBytes(StandardCharsets.US_ASCII)));
            out.write(maskedFrame(0x9, true, new byte[] {'P'}));
            out.write(maskedFrame(0x0, false, new byte[] {' '}));
            out.write(maskedFrame(0x0, true, "world".getBytes(StandardCharsets.US_ASCII)));
            out.flush();

            byte[] data = ws.readExact(11);
            assertArrayEquals("hello world".getBytes(StandardCharsets.US_ASCII), data);
            assertArrayEquals(new byte[] {(byte) 0x8A, 1, 'P'}, readExact(client.getInputStream(), 3));
            ws.close(); client.close();
        }
    }

    @Test void unmaskedClientFrameIsRejected() throws Exception {
        try (Harness h = new Harness()) {
            Socket client = h.connect("/mcflare", "mcflare.v1", "");
            WebSocketServerConnection ws = h.accept.get(2, TimeUnit.SECONDS);
            client.getOutputStream().write(new byte[] {(byte) 0x82, 1, 'x'});
            client.getOutputStream().flush();
            IOException error = assertThrows(IOException.class, () -> ws.readExact(1));
            assertTrue(error.getMessage().contains("masked"));
            ws.close(); client.close();
        }
    }

    @Test void oversizedFrameIsRejectedBeforePayloadRead() throws Exception {
        try (Harness h = new Harness()) {
            Socket client = h.connect("/mcflare", "mcflare.v1", "");
            WebSocketServerConnection ws = h.accept.get(2, TimeUnit.SECONDS);
            OutputStream out = client.getOutputStream();
            out.write(0x82); out.write(0xFF);
            long size = 1024L * 1024L + 1L;
            for (int shift = 56; shift >= 0; shift -= 8) out.write((int) (size >>> shift) & 0xFF);
            out.flush();
            IOException error = assertThrows(IOException.class, () -> ws.readExact(1));
            assertTrue(error.getMessage().contains("too large"));
            ws.close(); client.close();
        }
    }

    @Test void wrongPathAndSubprotocolAreRejectedBeforeUpgrade() throws Exception {
        assertHttpError("/wrong", "mcflare.v1", "404");
        assertHttpError("/mcflare", "wrong.v1", "400");
        assertHttpError("/mcflare", "MCFLARE.V1", "400");
    }

    @Test void exactSubprotocolMayAppearInAProtocolList() throws Exception {
        try (Harness h = new Harness()) {
            Socket client = h.connect("/mcflare", "other.v1, mcflare.v1", "");
            WebSocketServerConnection ws = h.accept.get(2, TimeUnit.SECONDS);
            ws.close(); client.close();
        }
    }

    @Test void receivedCloseIsEchoedBeforeWebSocketEof() throws Exception {
        try (Harness h = new Harness()) {
            Socket client = h.connect("/mcflare", "mcflare.v1", "");
            client.setSoTimeout(2000);
            WebSocketServerConnection ws = h.accept.get(2, TimeUnit.SECONDS);
            byte[] closePayload = new byte[] {0x03, (byte) 0xE8};
            client.getOutputStream().write(maskedFrame(0x8, true, closePayload));
            client.getOutputStream().flush();

            byte[] one = new byte[1];
            assertEquals(-1, ws.read(one, 0, 1));
            assertArrayEquals(new byte[] {(byte) 0x88, 2, 0x03, (byte) 0xE8},
                    readExact(client.getInputStream(), 4));
            ws.close(); client.close();
        }
    }

    @Test void serverInitiatedCloseSendsNormalClosureBeforeTcpEof() throws Exception {
        try (Harness h = new Harness()) {
            Socket client = h.connect("/mcflare", "mcflare.v1", "");
            client.setSoTimeout(2000);
            WebSocketServerConnection ws = h.accept.get(2, TimeUnit.SECONDS);

            ws.close();

            assertArrayEquals(new byte[] {(byte) 0x88, 2, 0x03, (byte) 0xE8},
                    readExact(client.getInputStream(), 4));
            assertEquals(-1, client.getInputStream().read());
            client.close();
        }
    }

    @Test void malformedClosePayloadsAreRejected() throws Exception {
        assertCloseRejected(new byte[] {0x03});
        assertCloseRejected(new byte[] {0x03, (byte) 0xED});
        assertCloseRejected(new byte[] {0x07, (byte) 0xD0});
        assertCloseRejected(new byte[] {0x03, (byte) 0xE8, (byte) 0xC3, 0x28});
    }

    @Test void nonMinimalExtendedLengthIsRejectedBeforePayloadRead() throws Exception {
        try (Harness h = new Harness()) {
            Socket client = h.connect("/mcflare", "mcflare.v1", "");
            WebSocketServerConnection ws = h.accept.get(2, TimeUnit.SECONDS);
            client.getOutputStream().write(new byte[] {(byte) 0x82, (byte) 0xFE, 0, 1});
            client.getOutputStream().flush();
            IOException error = assertThrows(IOException.class, () -> ws.readExact(1));
            assertTrue(error.getMessage().contains("non-minimal"));
            ws.close(); client.close();
        }
    }

    @Test void absoluteReadDeadlineIsNotExtendedByPingFrames() throws Exception {
        try (Harness h = new Harness()) {
            Socket client = h.connect("/mcflare", "mcflare.v1", "");
            WebSocketServerConnection ws = h.accept.get(2, TimeUnit.SECONDS);
            ws.setReadDeadline(180);
            CompletableFuture<Throwable> result = new CompletableFuture<Throwable>();
            Thread reader = new Thread(() -> {
                try { ws.readExact(1); result.complete(null); }
                catch (Throwable error) { result.complete(error); }
            });
            reader.setDaemon(true);
            reader.start();
            for (int i = 0; i < 4; i++) {
                Thread.sleep(40L);
                client.getOutputStream().write(maskedFrame(0x9, true, new byte[] {(byte) i}));
                client.getOutputStream().flush();
            }
            Throwable error = result.get(250, TimeUnit.MILLISECONDS);
            assertTrue(error instanceof java.net.SocketTimeoutException, String.valueOf(error));
            ws.setReadDeadline(0);
            ws.close(); client.close();
        }
    }

    @Test void handshakeUsesAbsoluteDeadlineDespiteSlowDripTraffic() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            CompletableFuture<Throwable> result = new CompletableFuture<Throwable>();
            Thread acceptor = new Thread(() -> {
                try (Socket socket = server.accept()) {
                    WebSocketServerConnection.accept(socket, "/mcflare", "mcflare.v1", 150);
                    result.complete(null);
                } catch (Throwable error) { result.complete(error); }
            });
            acceptor.setDaemon(true);
            acceptor.start();
            try (Socket client = new Socket("127.0.0.1", server.getLocalPort())) {
                for (byte value : "GET /mcflare HTTP/1.1\r\n".getBytes(StandardCharsets.US_ASCII)) {
                    try {
                        client.getOutputStream().write(value);
                        client.getOutputStream().flush();
                    } catch (IOException closed) {
                        break;
                    }
                    Thread.sleep(30L);
                    if (result.isDone()) break;
                }
            }
            Throwable error = result.get(300, TimeUnit.MILLISECONDS);
            assertTrue(error instanceof java.net.SocketTimeoutException, String.valueOf(error));
        }
    }

    @Test void http10AndMissingHostAreRejected() throws Exception {
        String key = Base64.getEncoder().encodeToString("0123456789abcdef".getBytes(StandardCharsets.US_ASCII));
        String common = "Upgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: " + key
                + "\r\nSec-WebSocket-Version: 13\r\nSec-WebSocket-Protocol: mcflare.v1\r\n\r\n";
        assertRawUpgradeRejected("GET /mcflare HTTP/1.0\r\nHost: localhost\r\n" + common);
        assertRawUpgradeRejected("GET /mcflare HTTP/1.1\r\n" + common);
    }

    private static void assertCloseRejected(byte[] payload) throws Exception {
        try (Harness h = new Harness()) {
            Socket client = h.connect("/mcflare", "mcflare.v1", "");
            WebSocketServerConnection ws = h.accept.get(2, TimeUnit.SECONDS);
            client.getOutputStream().write(maskedFrame(0x8, true, payload));
            client.getOutputStream().flush();
            assertThrows(IOException.class, () -> ws.readExact(1));
            ws.close(); client.close();
        }
    }

    private static void assertRawUpgradeRejected(String request) throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            CompletableFuture<Throwable> result = new CompletableFuture<Throwable>();
            Thread t = new Thread(() -> {
                try (Socket socket = server.accept()) {
                    WebSocketServerConnection.accept(socket, "/mcflare", "mcflare.v1");
                    result.complete(null);
                } catch (Throwable error) { result.complete(error); }
            });
            t.setDaemon(true);
            t.start();
            try (Socket client = new Socket("127.0.0.1", server.getLocalPort())) {
                client.getOutputStream().write(request.getBytes(StandardCharsets.ISO_8859_1));
                client.getOutputStream().flush();
            }
            assertTrue(result.get(2, TimeUnit.SECONDS) instanceof IOException);
        }
    }

    private static void assertHttpError(String path, String subprotocol, String status) throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            CompletableFuture<Throwable> result = new CompletableFuture<Throwable>();
            Thread t = new Thread(() -> {
                try (Socket socket = server.accept()) {
                    WebSocketServerConnection.accept(socket, "/mcflare", "mcflare.v1");
                    result.complete(null);
                } catch (Throwable e) { result.complete(e); }
            });
            t.start();
            try (Socket client = new Socket("127.0.0.1", server.getLocalPort())) {
                sendUpgrade(client, path, subprotocol, "");
                String response = new String(readHeaders(client.getInputStream()), StandardCharsets.ISO_8859_1);
                assertTrue(response.startsWith("HTTP/1.1 " + status));
            }
            assertNotNull(result.get(2, TimeUnit.SECONDS));
        }
    }

    private static byte[] maskedFrame(int opcode, boolean fin, byte[] payload) {
        byte[] mask = new byte[] {1, 2, 3, 4};
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write((fin ? 0x80 : 0) | opcode); out.write(0x80 | payload.length);
        out.write(mask, 0, mask.length);
        for (int i = 0; i < payload.length; i++) out.write(payload[i] ^ mask[i & 3]);
        return out.toByteArray();
    }

    private static void sendUpgrade(Socket client, String path, String subprotocol, String extra) throws IOException {
        String key = Base64.getEncoder().encodeToString("0123456789abcdef".getBytes(StandardCharsets.US_ASCII));
        String request = "GET " + path + " HTTP/1.1\r\nHost: localhost\r\nUpgrade: websocket\r\n"
                + "Connection: Upgrade\r\nSec-WebSocket-Key: " + key + "\r\nSec-WebSocket-Version: 13\r\n"
                + "Sec-WebSocket-Protocol: " + subprotocol + "\r\n" + extra + "\r\n";
        client.getOutputStream().write(request.getBytes(StandardCharsets.ISO_8859_1));
        client.getOutputStream().flush();
    }

    private static byte[] readHeaders(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); int matched = 0;
        while (matched < 4) {
            int b = in.read(); if (b < 0) break; out.write(b);
            char expected = "\r\n\r\n".charAt(matched);
            matched = b == expected ? matched + 1 : (b == '\r' ? 1 : 0);
        }
        return out.toByteArray();
    }

    private static byte[] readExact(InputStream in, int n) throws IOException {
        byte[] data = new byte[n]; int off = 0;
        while (off < n) { int r = in.read(data, off, n - off); if (r < 0) throw new IOException("EOF"); off += r; }
        return data;
    }

    private static final class Harness implements AutoCloseable {
        final ServerSocket server = new ServerSocket(0);
        final CompletableFuture<WebSocketServerConnection> accept = new CompletableFuture<WebSocketServerConnection>();
        Harness() throws IOException {
            Thread t = new Thread(() -> {
                try { accept.complete(WebSocketServerConnection.accept(server.accept(), "/mcflare", "mcflare.v1")); }
                catch (Throwable e) { accept.completeExceptionally(e); }
            });
            t.setDaemon(true); t.start();
        }
        Socket connect(String path, String subprotocol, String extra) throws IOException {
            Socket client = new Socket("127.0.0.1", server.getLocalPort());
            sendUpgrade(client, path, subprotocol, extra);
            String response = new String(readHeaders(client.getInputStream()), StandardCharsets.ISO_8859_1);
            assertTrue(response.startsWith("HTTP/1.1 101"), response);
            return client;
        }
        public void close() throws Exception { server.close(); }
    }
}
