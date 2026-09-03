package io.mcflare.core;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.ServerSocket;
import java.net.Socket;
import org.junit.jupiter.api.Test;

class Rfc6455ClientCloseTest {
    @Test void receivedCloseIsEchoedAsMaskedClientClose() throws Exception {
        try (ServerSocket server = new ServerSocket(0);
             Socket raw = new Socket("127.0.0.1", server.getLocalPort());
             Socket peer = server.accept()) {
            peer.setSoTimeout(2000);
            Rfc6455Client client = wrap(raw);
            peer.getOutputStream().write(new byte[] {(byte) 0x88, 2, 0x03, (byte) 0xE8});
            peer.getOutputStream().flush();

            assertNull(client.readData());
            Frame close = readClientFrame(peer.getInputStream());
            assertEquals(0x8, close.opcode);
            assertArrayEquals(new byte[] {0x03, (byte) 0xE8}, close.payload);
            client.close();
        }
    }

    @Test void configuredReadTimeoutBoundsSilentPeer() throws Exception {
        try (ServerSocket server = new ServerSocket(0);
             Socket raw = new Socket("127.0.0.1", server.getLocalPort());
             Socket peer = server.accept()) {
            Rfc6455Client client = wrap(raw);
            client.setReadTimeout(100);
            assertThrows(java.net.SocketTimeoutException.class, client::readData);
            client.close();
        }
    }

    @Test void invalidSendSliceFailsBeforeWritingFrameBytes() throws Exception {
        try (ServerSocket server = new ServerSocket(0);
             Socket raw = new Socket("127.0.0.1", server.getLocalPort());
             Socket peer = server.accept()) {
            peer.setSoTimeout(150);
            Rfc6455Client client = wrap(raw);

            assertThrows(IndexOutOfBoundsException.class, () -> client.sendBinary(new byte[] {1}, 0, 2));
            assertThrows(java.net.SocketTimeoutException.class, () -> peer.getInputStream().read());
            client.close();
        }
    }

    @Test void localCloseSendsMaskedCloseBeforeTcpEof() throws Exception {
        try (ServerSocket server = new ServerSocket(0);
             Socket raw = new Socket("127.0.0.1", server.getLocalPort());
             Socket peer = server.accept()) {
            peer.setSoTimeout(2000);
            Rfc6455Client client = wrap(raw);

            client.close();

            Frame close = readClientFrame(peer.getInputStream());
            assertEquals(0x8, close.opcode);
            assertArrayEquals(new byte[0], close.payload);
            assertEquals(-1, peer.getInputStream().read());
        }
    }

    @Test void oneByteClosePayloadIsRejected() throws Exception {
        assertInvalidServerClose(new byte[] {0x03});
    }

    @Test void reservedCloseCodeIsRejected() throws Exception {
        assertInvalidServerClose(new byte[] {0x03, (byte) 0xED}); // 1005 must not appear on the wire
    }

    @Test void reservedUnallocatedCloseCodeIsRejected() throws Exception {
        assertInvalidServerClose(new byte[] {0x07, (byte) 0xD0}); // 2000 is reserved without an extension
    }

    @Test void malformedUtf8CloseReasonIsRejected() throws Exception {
        assertInvalidServerClose(new byte[] {0x03, (byte) 0xE8, (byte) 0xC3, 0x28});
    }

    @Test void oversizedServerFrameIsRejectedBeforePayloadRead() throws Exception {
        try (ServerSocket server = new ServerSocket(0);
             Socket raw = new Socket("127.0.0.1", server.getLocalPort());
             Socket peer = server.accept()) {
            Rfc6455Client client = wrap(raw);
            peer.getOutputStream().write(new byte[] {(byte) 0x82, 127, 0, 0, 0, 0, 0, 0x10, 0, 1});
            peer.getOutputStream().flush();
            IOException error = assertThrows(IOException.class, client::readData);
            assertTrue(error.getMessage().contains("too large"));
            client.close();
        }
    }

    @Test void nonMinimalExtendedLengthIsRejected() throws Exception {
        try (ServerSocket server = new ServerSocket(0);
             Socket raw = new Socket("127.0.0.1", server.getLocalPort());
             Socket peer = server.accept()) {
            Rfc6455Client client = wrap(raw);
            peer.getOutputStream().write(new byte[] {(byte) 0x82, 126, 0, 1, 'x'});
            peer.getOutputStream().flush();
            IOException error = assertThrows(IOException.class, client::readData);
            assertTrue(error.getMessage().contains("Non-minimal"));
            client.close();
        }
    }

    private static void assertInvalidServerClose(byte[] payload) throws Exception {
        try (ServerSocket server = new ServerSocket(0);
             Socket raw = new Socket("127.0.0.1", server.getLocalPort());
             Socket peer = server.accept()) {
            Rfc6455Client client = wrap(raw);
            peer.getOutputStream().write(0x88);
            peer.getOutputStream().write(payload.length);
            peer.getOutputStream().write(payload);
            peer.getOutputStream().flush();
            assertThrows(IOException.class, client::readData);
            client.close();
        }
    }

    private static Rfc6455Client wrap(Socket socket) throws Exception {
        Constructor<Rfc6455Client> constructor = Rfc6455Client.class.getDeclaredConstructor(Socket.class);
        constructor.setAccessible(true);
        return constructor.newInstance(socket);
    }

    private static Frame readClientFrame(InputStream input) throws Exception {
        int first = input.read();
        int second = input.read();
        assertTrue(first >= 0 && second >= 0);
        assertTrue((second & 0x80) != 0, "client frame must be masked");
        int length = second & 0x7F;
        assertTrue(length <= 125);
        byte[] mask = readExact(input, 4);
        byte[] payload = readExact(input, length);
        for (int i = 0; i < payload.length; i++) payload[i] ^= mask[i & 3];
        return new Frame(first & 0x0F, payload);
    }

    private static byte[] readExact(InputStream input, int length) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        while (out.size() < length) {
            int value = input.read();
            if (value < 0) throw new java.io.EOFException();
            out.write(value);
        }
        return out.toByteArray();
    }

    private static final class Frame {
        final int opcode;
        final byte[] payload;
        Frame(int opcode, byte[] payload) { this.opcode = opcode; this.payload = payload; }
    }
}
