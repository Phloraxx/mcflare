package io.mcflare.core;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Generic datagram service carried over an MCflare enhanced gateway stream. */
public final class GatewayDatagramClient implements Closeable {

    private final WebSocketByteStream stream;

    private GatewayDatagramClient(WebSocketByteStream stream) {
        this.stream = stream;
    }

    public static GatewayDatagramClient connect(String host, String serviceId, int timeoutMs)
            throws IOException {
        Rfc6455Client ws = Rfc6455Client.connect(
                host, 443, MinecraftStatusProbe.DEFAULT_PATH, timeoutMs, timeoutMs);
        WebSocketByteStream stream = new WebSocketByteStream(ws);
        try {
            stream.write(GatewayProtocol.servicePreamble(
                    GatewayProtocol.OP_OPEN_DATAGRAM, serviceId));

            byte[] header = stream.readExact(8);
            if (!GatewayProtocol.hasMagic(header)
                    || (header[4] & 0xFF) != GatewayProtocol.VERSION
                    || (header[5] & 0xFF) != GatewayProtocol.responseOpcode(GatewayProtocol.OP_OPEN_DATAGRAM)) {
                throw new IOException("invalid MCflare datagram response");
            }
            int length = ((header[6] & 0xFF) << 8) | (header[7] & 0xFF);
            byte[] payload = stream.readExact(length);
            String json = new String(payload, StandardCharsets.UTF_8);
            if (!json.contains("\"ok\":true")) {
                throw new IOException("gateway rejected datagram service: " + json);
            }
            return new GatewayDatagramClient(stream);
        } catch (IOException | RuntimeException e) {
            try { stream.close(); } catch (IOException ignored) {}
            throw e;
        }
    }

    public synchronized void send(byte[] payload) throws IOException {
        if (payload.length < 1 || payload.length > GatewayProtocol.MAX_DATAGRAM) {
            throw new IllegalArgumentException("datagram size must be 1.." + GatewayProtocol.MAX_DATAGRAM + " bytes");
        }
        byte[] framed = new byte[payload.length + 2];
        framed[0] = (byte) ((payload.length >>> 8) & 0xFF);
        framed[1] = (byte) (payload.length & 0xFF);
        System.arraycopy(payload, 0, framed, 2, payload.length);
        stream.write(framed);
    }

    public byte[] receive() throws IOException {
        int length = stream.readU16();
        if (length < 1 || length > GatewayProtocol.MAX_DATAGRAM) {
            throw new IOException("invalid datagram length: " + length);
        }
        return stream.readExact(length);
    }

    @Override
    public void close() throws IOException {
        stream.close();
    }
}
