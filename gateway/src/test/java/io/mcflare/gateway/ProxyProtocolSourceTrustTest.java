package io.mcflare.gateway;

import static org.junit.jupiter.api.Assertions.*;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class ProxyProtocolSourceTrustTest {
    @AfterEach void clear() {
        ProxyProtocolSourceTrust.clearAdditionalLocalAddress();
    }

    @Test void loopbackIsAlwaysTrusted() throws Exception {
        assertTrue(ProxyProtocolSourceTrust.isTrusted(InetAddress.getByName("127.0.0.1")));
        assertTrue(ProxyProtocolSourceTrust.isTrusted(InetAddress.getByName("::1")));
    }

    @Test void nonLocalAdditionalAddressIsRejected() throws Exception {
        InetAddress documentationAddress = InetAddress.getByAddress(new byte[] {(byte) 192, 0, 2, 123});
        if (NetworkInterface.getByInetAddress(documentationAddress) != null) return;
        assertThrows(java.io.IOException.class,
                () -> ProxyProtocolSourceTrust.configureAdditionalLocalAddress(documentationAddress));
        assertFalse(ProxyProtocolSourceTrust.isTrusted(documentationAddress));
    }

    @Test void exactConfiguredNonLoopbackLocalAddressIsTrusted() throws Exception {
        InetAddress local = firstNonLoopbackLocalAddress();
        Assumptions.assumeTrue(local != null, "runner has no non-loopback local address");
        ProxyProtocolSourceTrust.configureAdditionalLocalAddress(local);
        assertTrue(ProxyProtocolSourceTrust.isTrusted(local));
        ProxyProtocolSourceTrust.clearAdditionalLocalAddress();
        assertFalse(ProxyProtocolSourceTrust.isTrusted(local));
    }

    private static InetAddress firstNonLoopbackLocalAddress() throws Exception {
        java.util.Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        if (interfaces == null) return null;
        for (NetworkInterface network : Collections.list(interfaces)) {
            for (InetAddress address : Collections.list(network.getInetAddresses())) {
                if (!address.isLoopbackAddress()) return address;
            }
        }
        return null;
    }
}
