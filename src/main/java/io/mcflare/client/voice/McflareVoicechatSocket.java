package io.mcflare.client.voice;

import de.maxhenkel.voicechat.api.ClientVoicechatSocket;
import de.maxhenkel.voicechat.api.RawUdpPacket;
import io.mcflare.client.McflareClient;
import io.mcflare.core.GatewayControlClient;
import io.mcflare.core.GatewayDatagramClient;

import java.net.SocketAddress;
import java.util.concurrent.CompletableFuture;

/** SVC socket for an already-confirmed MCflare server. Voice fails closed unless the gateway advertises the datagram service. */
final class McflareVoicechatSocket implements ClientVoicechatSocket {
    private static final String SERVICE_ID = "voicechat";
    private static final int CAPABILITY_TIMEOUT_MS = 2_000;
    private static final int CONNECT_TIMEOUT_MS = 4_000;

    private final String host;
    private final CompletableFuture<GatewayControlClient.Result> capabilities;
    private final Object selectionLock = new Object();

    private volatile GatewayDatagramClient enhanced;
    private volatile boolean failed;
    private volatile boolean opened;

    McflareVoicechatSocket(String host) {
        this.host = host;
        this.capabilities = CompletableFuture.supplyAsync(
                () -> GatewayControlClient.probe(host, CAPABILITY_TIMEOUT_MS),
                McflareClient.EXECUTOR);
    }

    @Override
    public void open() {
        close();
        opened = true;
    }

    @Override
    public RawUdpPacket read() throws Exception {
        ensureEnhanced();
        return packet(enhanced.receive(), new McflareSocketAddress());
    }

    @Override
    public void send(byte[] data, SocketAddress address) throws Exception {
        ensureEnhanced();
        enhanced.send(data);
    }

    private void ensureEnhanced() throws Exception {
        if (!opened) throw new IllegalStateException("Voice socket is not open");
        if (enhanced != null) return;
        synchronized (selectionLock) {
            if (enhanced != null) return;
            if (failed) throw new IllegalStateException("MCflare voice service is unavailable");
            GatewayControlClient.Result result = capabilities.get();
            if (!result.hasService(SERVICE_ID, "datagram")) {
                failed = true;
                throw new IllegalStateException("Protected server does not advertise MCflare voice service '" + SERVICE_ID + "'");
            }
            try {
                enhanced = GatewayDatagramClient.connect(host, SERVICE_ID, CONNECT_TIMEOUT_MS);
            } catch (Exception e) {
                failed = true;
                throw e;
            }
            McflareClient.LOGGER.info("Simple Voice Chat is connected through MCflare service '{}'", SERVICE_ID);
        }
    }

    @Override
    public void close() {
        opened = false;
        GatewayDatagramClient currentEnhanced = enhanced;
        enhanced = null;
        if (currentEnhanced != null) {
            try { currentEnhanced.close(); } catch (Exception ignored) {}
        }
        failed = false;
    }

    @Override
    public boolean isClosed() {
        if (!opened || failed) return true;
        GatewayDatagramClient currentEnhanced = enhanced;
        return currentEnhanced != null && currentEnhanced.isClosed();
    }

    private static RawUdpPacket packet(byte[] data, SocketAddress address) {
        return new Packet(data, System.currentTimeMillis(), address);
    }

    private static final class McflareSocketAddress extends SocketAddress {}

    private static final class Packet implements RawUdpPacket {
        private final byte[] data;
        private final long timestamp;
        private final SocketAddress address;
        private Packet(byte[] data, long timestamp, SocketAddress address) {
            this.data = data;
            this.timestamp = timestamp;
            this.address = address;
        }
        @Override public byte[] getData() { return data; }
        @Override public long getTimestamp() { return timestamp; }
        @Override public SocketAddress getSocketAddress() { return address; }
    }
}
