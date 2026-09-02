package io.mcflare.core;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
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
