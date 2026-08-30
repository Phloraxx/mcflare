package io.mcflare.client.tunnel;

import java.util.Objects;

/** Selected transport for one Minecraft connection. Null carrier means ordinary direct TCP. */
public record TunnelStatus(RunningTunnel runningTunnel) {
    public static TunnelStatus direct() {
        return new TunnelStatus(null);
    }

    public static TunnelStatus mcflare(RunningTunnel tunnel) {
        return new TunnelStatus(Objects.requireNonNull(tunnel, "tunnel"));
    }

    public boolean usesMcflare() {
        return runningTunnel != null;
    }
}
