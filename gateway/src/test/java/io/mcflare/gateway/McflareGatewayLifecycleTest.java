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
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
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
            assertEquals(-1, socket.getInputStream().read());
        } catch (SocketException expected) {
            // A reset is also a prompt peer close.
        } catch (SocketTimeoutException timeout) {
            fail("gateway left the WebSocket open after backend EOF", timeout);
        }
    }
}
