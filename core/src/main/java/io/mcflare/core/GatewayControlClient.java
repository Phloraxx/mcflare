package io.mcflare.core;

import java.nio.charset.StandardCharsets;

/** Optional Enhanced-mode capability negotiation over the MCflare hostname. */
public final class GatewayControlClient {
    private GatewayControlClient() {}

    public static Result probe(String host, int timeoutMs) {
        long started = System.nanoTime();
        Rfc6455Client webSocket = null;
        try {
            webSocket = Rfc6455Client.connect(
                    host, 443, MinecraftStatusProbe.DEFAULT_PATH, timeoutMs, timeoutMs);
            WebSocketByteStream stream = new WebSocketByteStream(webSocket);
            stream.write(GatewayProtocol.preamble(GatewayProtocol.OP_HELLO));

            byte[] header = stream.readExact(8);
            if (!GatewayProtocol.hasMagic(header)
                    || (header[4] & 0xFF) != GatewayProtocol.VERSION
                    || (header[5] & 0xFF) != GatewayProtocol.responseOpcode(GatewayProtocol.OP_HELLO)) {
                return Result.unsupported(elapsed(started), "invalid hello");
            }

            int length = ((header[6] & 0xFF) << 8) | (header[7] & 0xFF);
            if (length > GatewayProtocol.MAX_CONTROL_PAYLOAD) {
                return Result.unsupported(elapsed(started), "invalid length");
            }
            String json = new String(stream.readExact(length), StandardCharsets.UTF_8).trim();
            if (!json.startsWith("{") || !json.endsWith("}")) {
                return Result.unsupported(elapsed(started), "invalid json");
            }
            return Result.supported(json, elapsed(started));
        } catch (Exception e) {
            return Result.unsupported(elapsed(started), e.getClass().getSimpleName());
        } finally {
            if (webSocket != null) {
                try { webSocket.close(); }
                catch (Exception ignored) {}
            }
        }
    }

    private static long elapsed(long started) {
        return (System.nanoTime() - started) / 1_000_000L;
    }

    public static final class Result {
        private final boolean supported;
        private final String capabilitiesJson;
        private final long latencyMillis;
        private final String reason;

        private Result(boolean supported, String capabilitiesJson,
                       long latencyMillis, String reason) {
            this.supported = supported;
            this.capabilitiesJson = capabilitiesJson;
            this.latencyMillis = latencyMillis;
            this.reason = reason;
        }

        public static Result supported(String json, long latencyMillis) {
            return new Result(true, json, latencyMillis, null);
        }

        public static Result unsupported(long latencyMillis, String reason) {
            return new Result(false, null, latencyMillis, reason);
        }

        public boolean isSupported() { return supported; }
        public String getCapabilitiesJson() { return capabilitiesJson; }
        public long getLatencyMillis() { return latencyMillis; }
        public String getReason() { return reason; }
    }
}
