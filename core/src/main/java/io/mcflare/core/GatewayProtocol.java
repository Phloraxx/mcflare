package io.mcflare.core;

import java.nio.charset.StandardCharsets;

/** Shared wire constants and tiny encoders for optional Enhanced-mode services. */
public final class GatewayProtocol {
    public static final byte[] MAGIC = new byte[] {'M', 'C', 'F', '1'};
    public static final int VERSION = 1;

    public static final int OP_HELLO = 1;
    public static final int OP_OPEN_STREAM = 2;
    public static final int OP_OPEN_DATAGRAM = 3;
    public static final int OP_ERROR = 0xFF;

    public static final int MAX_SERVICE_ID_BYTES = 64;
    public static final int MAX_CONTROL_PAYLOAD = 64 * 1024;
    public static final int MAX_DATAGRAM = 8 * 1024;

    private GatewayProtocol() {}

    public static int responseOpcode(int opcode) {
        return 0x80 | opcode;
    }

    public static byte[] preamble(int opcode) {
        return new byte[] {
                MAGIC[0], MAGIC[1], MAGIC[2], MAGIC[3],
                (byte) VERSION, (byte) opcode
        };
    }

    public static byte[] servicePreamble(int opcode, String serviceId) {
        byte[] id = serviceId.getBytes(StandardCharsets.UTF_8);
        if (id.length < 1 || id.length > MAX_SERVICE_ID_BYTES) {
            throw new IllegalArgumentException("invalid service id length");
        }
        byte[] result = new byte[MAGIC.length + 3 + id.length];
        System.arraycopy(MAGIC, 0, result, 0, MAGIC.length);
        result[4] = (byte) VERSION;
        result[5] = (byte) opcode;
        result[6] = (byte) id.length;
        System.arraycopy(id, 0, result, 7, id.length);
        return result;
    }

    public static boolean hasMagic(byte[] data) {
        if (data == null || data.length < MAGIC.length) return false;
        for (int i = 0; i < MAGIC.length; i++) {
            if (data[i] != MAGIC[i]) return false;
        }
        return true;
    }
}
