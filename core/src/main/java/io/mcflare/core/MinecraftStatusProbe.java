package io.mcflare.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Active MCflare discovery using the Minecraft status protocol over WSS. */
public final class MinecraftStatusProbe {
    public static final String DEFAULT_PATH = "/.well-known/mcflare";
    public static final int UNKNOWN_PROTOCOL = -1;
    private static final int MAX_STATUS_BYTES = 2 * 1024 * 1024;

    private MinecraftStatusProbe() {}

    public static Result probe(String host, int minecraftPort, int timeoutMs) {
        return probe(host, minecraftPort, UNKNOWN_PROTOCOL, timeoutMs);
    }

    public static Result probe(String host, int minecraftPort, int protocolVersion, int timeoutMs) {
        long started = System.nanoTime();
        try (Rfc6455Client ws = Rfc6455Client.connect(host, 443, DEFAULT_PATH, timeoutMs, timeoutMs)) {
            byte[] request = buildStatusRequest(host, minecraftPort, protocolVersion);
            ws.sendBinary(request);

            ByteArrayOutputStream received = new ByteArrayOutputStream();
            while (received.size() < MAX_STATUS_BYTES) {
                byte[] chunk = ws.readData();
                if (chunk == null) break;
                received.write(chunk);
                String json = tryParseStatus(received.toByteArray());
                if (json != null) {
                    return Result.supported(json, elapsedMillis(started));
                }
            }
            return Result.unsupported(elapsedMillis(started), "no Minecraft status response");
        } catch (Exception e) {
            return Result.unsupported(elapsedMillis(started), e.getClass().getSimpleName());
        }
    }

    public static byte[] buildStatusRequest(String host, int port, int protocolVersion) throws IOException {
        ByteArrayOutputStream handshakeBody = new ByteArrayOutputStream();
        writeVarInt(handshakeBody, 0);
        writeVarInt(handshakeBody, protocolVersion);
        byte[] hostBytes = host.getBytes(StandardCharsets.UTF_8);
        writeVarInt(handshakeBody, hostBytes.length);
        handshakeBody.write(hostBytes);
        handshakeBody.write((port >>> 8) & 0xFF);
        handshakeBody.write(port & 0xFF);
        writeVarInt(handshakeBody, 1);

        ByteArrayOutputStream request = new ByteArrayOutputStream();
        byte[] body = handshakeBody.toByteArray();
        writeVarInt(request, body.length);
        request.write(body);
        request.write(0x01);
        request.write(0x00);
        return request.toByteArray();
    }

    private static String tryParseStatus(byte[] bytes) throws IOException {
        Cursor cursor = new Cursor(bytes);
        Integer packetLength = cursor.readVarIntOrNull();
        if (packetLength == null || packetLength < 0) return null;
        if (cursor.remaining() < packetLength) return null;

        int packetEnd = cursor.position + packetLength;
        Integer packetId = cursor.readVarIntOrNull();
        if (packetId == null || packetId != 0) return null;
        Integer jsonLength = cursor.readVarIntOrNull();
        if (jsonLength == null || jsonLength < 2 || jsonLength > MAX_STATUS_BYTES) return null;
        if (cursor.position + jsonLength > packetEnd || cursor.remaining() < jsonLength) return null;
        String json = new String(bytes, cursor.position, jsonLength, StandardCharsets.UTF_8).trim();
        if (!json.startsWith("{") || !json.endsWith("}")) return null;
        if (!json.contains("\"version\"") || !json.contains("\"description\"")) return null;
        return json;
    }

    private static void writeVarInt(ByteArrayOutputStream out, int value) {
        int current = value;
        do {
            int temp = current & 0x7F;
            current >>>= 7;
            if (current != 0) temp |= 0x80;
            out.write(temp);
        } while (current != 0);
    }

    private static long elapsedMillis(long started) {
        return (System.nanoTime() - started) / 1_000_000L;
    }

    public static final class Result {
        private final boolean supported;
        private final String statusJson;
        private final long latencyMillis;
        private final String reason;

        private Result(boolean supported, String statusJson, long latencyMillis, String reason) {
            this.supported = supported;
            this.statusJson = statusJson;
            this.latencyMillis = latencyMillis;
            this.reason = reason;
        }

        public static Result supported(String statusJson, long latencyMillis) {
            return new Result(true, statusJson, latencyMillis, null);
        }

        public static Result unsupported(long latencyMillis, String reason) {
            return new Result(false, null, latencyMillis, reason);
        }

        public boolean isSupported() { return supported; }
        public String getStatusJson() { return statusJson; }
        public long getLatencyMillis() { return latencyMillis; }
        public String getReason() { return reason; }
    }

    private static final class Cursor {
        private final byte[] data;
        private int position;

        private Cursor(byte[] data) {
            this.data = data;
        }

        private int remaining() {
            return data.length - position;
        }

        private Integer readVarIntOrNull() throws IOException {
            int value = 0;
            int shift = 0;
            for (int count = 0; count < 5; count++) {
                if (position >= data.length) return null;
                int current = data[position++] & 0xFF;
                value |= (current & 0x7F) << shift;
                if ((current & 0x80) == 0) return value;
                shift += 7;
            }
            throw new IOException("VarInt too long");
        }
    }
}
