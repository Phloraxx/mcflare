package io.mcflare.client.tunnel;

import io.mcflare.client.McflareClient;
import io.mcflare.core.LoopbackCarrier;
import io.mcflare.core.MinecraftStatusProbe;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.InetSocketAddress;

/** Minecraft-facing wrapper around the version-independent MCflare core carrier. */
public final class RunningTunnel implements AutoCloseable {
    public static final String PROBE_PATH = MinecraftStatusProbe.DEFAULT_PATH;

    private final Access access;
    private final LoopbackCarrier carrier;

    private RunningTunnel(Access access, LoopbackCarrier carrier) {
        this.access = access;
        this.carrier = carrier;
    }

    public static @NotNull RunningTunnel create(String host) throws IOException {
        LoopbackCarrier carrier = LoopbackCarrier.start(host, PROBE_PATH,
                error -> McflareClient.LOGGER.debug("MCflare carrier stream closed: {}", error.toString()));
        return new RunningTunnel(new Access(host, carrier.getLocalAddress()), carrier);
    }

    public Access access() { return access; }
    public void closeTunnel() { close(); }

    @Override
    public void close() { carrier.close(); }

    public record Access(String hostname, InetSocketAddress tunnelAddress) {}
}
