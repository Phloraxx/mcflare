package io.mcflare.core;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MinecraftStatusProbeTest {
    @Test
    void buildsVersionCorrectStatusHandshake() throws Exception {
        byte[] request = MinecraftStatusProbe.buildStatusRequest(
                "play.example.com", 25565, 776);
        ByteArrayInputStream input = new ByteArrayInputStream(request);

        int handshakeLength = readVarInt(input);
        byte[] handshake = new byte[handshakeLength];
        assertEquals(handshakeLength, input.read(handshake));
        ByteArrayInputStream body = new ByteArrayInputStream(handshake);

        assertEquals(0, readVarInt(body));
        assertEquals(776, readVarInt(body));
        assertEquals("play.example.com", readString(body));
        assertEquals(25565, (body.read() << 8) | body.read());
        assertEquals(1, readVarInt(body));

        assertEquals(1, readVarInt(input));
        assertEquals(0, readVarInt(input));
        assertEquals(0, input.available());
    }

    private static String readString(ByteArrayInputStream input) throws IOException {
        int length = readVarInt(input);
        byte[] bytes = new byte[length];
        if (input.read(bytes) != length) throw new IOException("truncated string");
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static int readVarInt(ByteArrayInputStream input) throws IOException {
        int value = 0;
        int shift = 0;
        for (int i = 0; i < 5; i++) {
            int current = input.read();
            if (current < 0) throw new IOException("truncated VarInt");
            value |= (current & 0x7F) << shift;
            if ((current & 0x80) == 0) return value;
            shift += 7;
        }
        throw new IOException("VarInt too long");
    }
}
