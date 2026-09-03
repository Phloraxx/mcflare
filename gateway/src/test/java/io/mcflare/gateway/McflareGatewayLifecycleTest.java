package io.mcflare.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class McflareGatewayLifecycleTest {
    @Test
    void backendEofClosesWebSocketAndReleasesConnectionSlot() throws Exception {
        ServerSocket backendListener = new ServerSocket();
        backendListener.bind(new InetSocketAddress("127.0.0.1", 0));
        ExecutorService backendExecutor = Executors.newSingleThreadExecutor();
        Future<Void> backend = backendExecutor.submit(() -> {
            for (int i = 0; i < 2; i++) {
                try (Socket socket = backendListener.accept()) {
                    socket.setSoTimeout(2_000);
                    assertEquals(0, socket.getInputStream().read());
                }
            }
            return null;
        });

        int gatewayPort = freePort();
        McflareGateway gateway = McflareGateway.startAsync(
                new InetSocketAddress("127.0.0.1", gatewayPort),
                new InetSocketAddress("127.0.0.1", backendListener.getLocalPort()),
                1, false, ignored -> { }, ignored -> { });

        try {
            try (Socket first = openWebSocket(gatewayPort)) {
                sendMaskedBinary(first, (byte) 0);
                assertPeerClosesPromptly(first);
            }

            Thread.sleep(100L);

            try (Socket second = openWebSocket(gatewayPort)) {
                sendMaskedBinary(second, (byte) 0);
                assertPeerClosesPromptly(second);
            }

            backend.get(5, TimeUnit.SECONDS);
        } finally {
            gateway.close();
            backendListener.close();
            backendExecutor.shutdownNow();
        }
    }

    @Test
    void idleUpgradeTimesOutAndReleasesConnectionSlot() throws Exception {
        int gatewayPort = freePort();
        McflareGateway gateway = McflareGateway.startAsync(
                new InetSocketAddress("127.0.0.1", gatewayPort),
                new InetSocketAddress("127.0.0.1", freePort()),
                1, false, 150, ignored -> { }, ignored -> { });
        try {
            try (Socket first = openWebSocket(gatewayPort)) {
                assertPeerClosesPromptly(first);
            }
            try (Socket second = openWebSocket(gatewayPort)) {
                // A second upgrade succeeding proves the timed-out first session released the only slot.
            }
        } finally {
            gateway.close();
        }
    }

    @Test
    void gatewayCloseTerminatesActiveSession() throws Exception {
        ServerSocket backendListener = new ServerSocket();
        backendListener.bind(new InetSocketAddress("127.0.0.1", 0));
        CountDownLatch backendReceived = new CountDownLatch(1);
        ExecutorService backendExecutor = Executors.newSingleThreadExecutor();
        Future<Void> backend = backendExecutor.submit(() -> {
            try (Socket socket = backendListener.accept()) {
                socket.setSoTimeout(2_000);
                assertEquals(0, socket.getInputStream().read());
                backendReceived.countDown();
                assertEquals(-1, socket.getInputStream().read(), "gateway close must terminate the backend stream");
            }
            return null;
        });

        int gatewayPort = freePort();
        McflareGateway gateway = McflareGateway.startAsync(
                new InetSocketAddress("127.0.0.1", gatewayPort),
                new InetSocketAddress("127.0.0.1", backendListener.getLocalPort()),
                1, false, ignored -> { }, ignored -> { });

        try (Socket client = openWebSocket(gatewayPort)) {
            sendMaskedBinary(client, (byte) 0);
            assertTrue(backendReceived.await(2, TimeUnit.SECONDS), "backend session did not start");
            gateway.close();
            assertTcpEofPromptly(client);
            backend.get(5, TimeUnit.SECONDS);
        } finally {
            gateway.close();
            backendListener.close();
            backendExecutor.shutdownNow();
        }
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket()) {
            socket.bind(new InetSocketAddress("127.0.0.1", 0));
            return socket.getLocalPort();
        }
    }

    private static Socket openWebSocket(int port) throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress("127.0.0.1", port), 2_000);
        socket.setSoTimeout(2_000);
        OutputStream output = socket.getOutputStream();
        String request = "GET /mcflare HTTP/1.1\r\n"
                + "Host: 127.0.0.1:" + port + "\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
                + "Sec-WebSocket-Version: 13\r\n"
                + "Sec-WebSocket-Protocol: mcflare.v1\r\n\r\n";
        output.write(request.getBytes(StandardCharsets.US_ASCII));
        output.flush();
        String response = readHeaders(socket.getInputStream());
        assertTrue(response.startsWith("HTTP/1.1 101"), response);
        return socket;
    }

    private static String readHeaders(InputStream input) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int matched = 0;
        while (buffer.size() < 8_192) {
            int value = input.read();
            if (value < 0) break;
            buffer.write(value);
            byte expected = new byte[] {'\r', '\n', '\r', '\n'}[matched];
            if ((byte) value == expected) {
                matched++;
                if (matched == 4) break;
            } else {
                matched = (value == '\r') ? 1 : 0;
            }
        }
        return new String(buffer.toByteArray(), StandardCharsets.US_ASCII);
    }

    private static void sendMaskedBinary(Socket socket, byte payload) throws IOException {
        byte[] mask = new byte[] {1, 2, 3, 4};
        OutputStream output = socket.getOutputStream();
        output.write(0x82);
        output.write(0x81);
        output.write(mask);
        output.write(payload ^ mask[0]);
        output.flush();
    }

    private static void assertPeerClosesPromptly(Socket socket) throws IOException {
        socket.setSoTimeout(2_000);
        try {
            InputStream input = socket.getInputStream();
            assertEquals(0x88, input.read(), "gateway must initiate a WebSocket close after backend EOF");
            assertEquals(2, input.read(), "normal close payload length");
            assertEquals(0x03, input.read(), "normal close code high byte");
            assertEquals(0xE8, input.read(), "normal close code low byte");
            assertEquals(-1, input.read(), "TCP EOF must follow the close frame");
        } catch (SocketTimeoutException timeout) {
            fail("gateway left the WebSocket open after backend EOF", timeout);
        }
    }

    private static void assertTcpEofPromptly(Socket socket) throws IOException {
        socket.setSoTimeout(2_000);
        try {
            assertEquals(-1, socket.getInputStream().read(), "gateway close must terminate the client TCP stream");
        } catch (SocketTimeoutException timeout) {
            fail("gateway close left an active client session open", timeout);
        }
    }
}
