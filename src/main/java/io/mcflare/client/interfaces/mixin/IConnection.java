package io.mcflare.client.interfaces.mixin;

import io.mcflare.core.LoopbackCarrier;

public interface IConnection {
    void setMcflareCarrier(LoopbackCarrier carrier);
    LoopbackCarrier getMcflareCarrier();
}
