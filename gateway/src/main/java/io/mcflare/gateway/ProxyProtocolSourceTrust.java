package io.mcflare.gateway;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;

/** Process-local trust boundary for PROXY-v1 sources used by the integrated server gateway. */
public final class ProxyProtocolSourceTrust {
    private static volatile InetAddress additionalLocalAddress;

    private ProxyProtocolSourceTrust() {}

    public static synchronized void configureAdditionalLocalAddress(InetAddress address) throws IOException {
        if (address == null || address.isLoopbackAddress()) {
            additionalLocalAddress = null;
            return;
        }
        try {
            if (NetworkInterface.getByInetAddress(address) == null) {
                throw new IOException("configured Minecraft backend address is not local: " + address.getHostAddress());
            }
        } catch (SocketException error) {
            throw new IOException("could not verify configured Minecraft backend address", error);
        }
        additionalLocalAddress = address;
    }

    public static synchronized void clearAdditionalLocalAddress() {
        additionalLocalAddress = null;
    }

    public static boolean isTrusted(InetAddress address) {
        if (address == null) return false;
        if (address.isLoopbackAddress()) return true;
        InetAddress configured = additionalLocalAddress;
        return configured != null && configured.equals(address);
    }
}
