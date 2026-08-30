package io.mcflare.core;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewayProtocolTest {
    @Test
    void buildsHelloPreamble() {
        assertArrayEquals(
                new byte[] {'M', 'C', 'F', '1', 1, 1},
                GatewayProtocol.preamble(GatewayProtocol.OP_HELLO));
    }

    @Test
    void buildsServicePreamble() {
        byte[] frame = GatewayProtocol.servicePreamble(
                GatewayProtocol.OP_OPEN_DATAGRAM, "voicechat");
        assertTrue(GatewayProtocol.hasMagic(frame));
        assertEquals(GatewayProtocol.VERSION, frame[4] & 0xFF);
        assertEquals(GatewayProtocol.OP_OPEN_DATAGRAM, frame[5] & 0xFF);
        int length = frame[6] & 0xFF;
        assertEquals("voicechat",
                new String(frame, 7, length, StandardCharsets.UTF_8));
    }

    @Test
    void serviceIdsAreJsonAndProtocolSafe() {
        assertTrue(GatewayProtocol.isValidServiceId("voicechat"));
        assertTrue(GatewayProtocol.isValidServiceId("mod.service-1"));
        assertFalse(GatewayProtocol.isValidServiceId("bad\"id"));
        assertFalse(GatewayProtocol.isValidServiceId("bad id"));
        assertFalse(GatewayProtocol.isValidServiceId(""));
    }

    @Test
    void rejectsOversizedServiceId() {
        StringBuilder id = new StringBuilder();
        for (int i = 0; i < GatewayProtocol.MAX_SERVICE_ID_BYTES + 1; i++) id.append('x');
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> GatewayProtocol.servicePreamble(GatewayProtocol.OP_OPEN_STREAM, id.toString()));
    }
}
