package io.mcflare.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewayControlClientTest {
    @Test
    void findsAdvertisedServiceByIdAndKind() {
        GatewayControlClient.Result result = GatewayControlClient.Result.supported(
                "{\"protocol\":1,\"services\":[{\"id\":\"voicechat\",\"kind\":\"datagram\",\"maxDatagram\":8192}]}",
                12);

        assertTrue(result.hasService("voicechat", "datagram"));
        assertFalse(result.hasService("voicechat", "stream"));
        assertFalse(result.hasService("missing", "datagram"));
    }

    @Test
    void unsupportedResultNeverAdvertisesServices() {
        GatewayControlClient.Result result = GatewayControlClient.Result.unsupported(8, "timeout");
        assertFalse(result.hasService("voicechat", "datagram"));
    }
}
