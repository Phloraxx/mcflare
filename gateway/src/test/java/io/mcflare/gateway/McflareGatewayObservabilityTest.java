package io.mcflare.gateway;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class McflareGatewayObservabilityTest {
    @Test void backendEofLogsCorrelatedSessionWithoutRawPlayerIp() throws Exception {
        List<String> info = new CopyOnWriteArrayList<String>();
        List<String> errors = new CopyOnWriteArrayList<String>();
        try (ServerSocket backendListener = new ServerSocket(0)) {
            ExecutorService backendExecutor = Executors.newSingleThreadExecutor();
            Future<Void> backend = backendExecutor.submit(() -> {
                try (Socket socket = backendListener.accept()) {
                    assertEquals(7, socket.getInputStream().read());
                }
                return null;
            });

            int gatewayPort = freePort();
            McflareGateway gateway = McflareGateway.startAsync(
                    new InetSocketAddress("127.0.0.1", gatewayPort),
                    new InetSocketAddress("127.0.0.1", backendListener.getLocalPort()),
                    1, false, info::add, errors::add);
            try (Socket client = openWebSocket(gatewayPort,
                    "CF-Connecting-IP: 198.51.100.42\r\nCF-Ray: test-ray\r\n")) {
                client.setSoTimeout(2_000);
                sendMaskedBinary(client, (byte) 7);
                assertNormalClose(client.getInputStream());
                backend.get(2, TimeUnit.SECONDS);
                waitForLog(info, "event=close");
            } finally {
                gateway.close();
                backendExecutor.shutdownNow();
            }

            String upgrade = findLog(info, "event=upgrade");
            String close = findLog(info, "event=close");
            assertTrue(upgrade.contains("session=1"));
            assertTrue(upgrade.contains("realIpPresent=true"));
            assertTrue(upgrade.contains("cfRay=test-ray"));
            assertTrue(close.contains("session=1"));
            assertTrue(close.contains("durationMs="));
            assertTrue(close.contains("reason=backend-eof"));
            assertTrue(errors.isEmpty(), errors.toString());
            for (String line : info) assertFalse(line.contains("198.51.100.42"), line);
        }
    }

    @Test void capacityRejectionReturns503AndEmitsCapacityEvent() throws Exception {
        List<String> info = new CopyOnWriteArrayList<String>();
        int gatewayPort = freePort();
        McflareGateway gateway = McflareGateway.startAsync(
                new InetSocketAddress("127.0.0.1", gatewayPort),
                new InetSocketAddress("127.0.0.1", freePort()),
                1, false, info::add, ignored -> { });
        try (Socket held = openWebSocket(gatewayPort, "CF-Ray: bad ray value\r\n");
             Socket rejected = new Socket("127.0.0.1", gatewayPort)) {
            rejected.setSoTimeout(2_000);
            String response = readHeaders(rejected.getInputStream());
            assertTrue(response.startsWith("HTTP/1.1 503"), response);
            waitForLog(info, "event=capacity-reject");
            String capacity = findLog(info, "event=capacity-reject");
            assertTrue(capacity.contains("active=1"));
            assertTrue(capacity.contains("maxConnections=1"));
            String upgrade = findLog(info, "event=upgrade");
            assertTrue(upgrade.contains("cfRay=invalid"), upgrade);
            assertFalse(upgrade.contains("bad ray value"), upgrade);
            sendMaskedClose(held);
            assertNormalClose(held.getInputStream());
        } finally {
            gateway.close();
        }
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) { return socket.getLocalPort(); }
    }

    private static Socket openWebSocket(int port, String extraHeaders) throws IOException {
        Socket socket = new Socket("127.0.0.1", port);
        socket.setSoTimeout(2_000);
        String request = "GET /mcflare HTTP/1.1\r\nHost: localhost\r\nUpgrade: websocket\r\n"
                + "Connection: Upgrade\r\nSec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
                + "Sec-WebSocket-Version: 13\r\nSec-WebSocket-Protocol: mcflare.v1\r\n"
                + extraHeaders + "\r\n";
        socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
        socket.getOutputStream().flush();
        String response = readHeaders(socket.getInputStream());
        assertTrue(response.startsWith("HTTP/1.1 101"), response);
        return socket;
    }

    private static void sendMaskedBinary(Socket socket, byte value) throws IOException {
        sendMaskedFrame(socket, 0x2, new byte[] {value});
    }

    private static void sendMaskedClose(Socket socket) throws IOException {
        sendMaskedFrame(socket, 0x8, new byte[] {0x03, (byte) 0xE8});
    }

    private static void sendMaskedFrame(Socket socket, int opcode, byte[] payload) throws IOException {
        byte[] mask = new byte[] {1, 2, 3, 4};
        OutputStream output = socket.getOutputStream();
        output.write(0x80 | opcode);
        output.write(0x80 | payload.length);
        output.write(mask);
        for (int i = 0; i < payload.length; i++) output.write(payload[i] ^ mask[i & 3]);
        output.flush();
    }

    private static void assertNormalClose(InputStream input) throws IOException {
        assertEquals(0x88, input.read());
        assertEquals(2, input.read());
        assertEquals(0x03, input.read());
        assertEquals(0xE8, input.read());
    }

    private static String readHeaders(InputStream input) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int matched = 0;
        while (matched < 4) {
            int value = input.read();
            if (value < 0) break;
            out.write(value);
            char expected = "\r\n\r\n".charAt(matched);
            matched = value == expected ? matched + 1 : (value == '\r' ? 1 : 0);
        }
        return new String(out.toByteArray(), StandardCharsets.US_ASCII);
    }

    private static void waitForLog(List<String> logs, String marker) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            for (String line : logs) if (line.contains(marker)) return;
            Thread.sleep(10L);
        }
        fail("missing log marker " + marker + " in " + logs);
    }

    private static String findLog(List<String> logs, String marker) {
        for (String line : logs) if (line.contains(marker)) return line;
        fail("missing log marker " + marker + " in " + logs);
        return null;
    }
}
