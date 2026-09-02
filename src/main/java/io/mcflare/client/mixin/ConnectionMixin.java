package io.mcflare.client.mixin;

import io.mcflare.client.interfaces.mixin.IConnection;
import io.mcflare.core.LoopbackCarrier;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Intrinsic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Implements(@Interface(iface = IConnection.class, prefix = "connection$"))
@Mixin(Connection.class)
public abstract class ConnectionMixin implements IConnection {
    @Unique private LoopbackCarrier mcflare$carrier;

    @Inject(method = "disconnect*", at = @At("TAIL"))
    public void disconnect(Component reason, CallbackInfo callbackInfo) {
        synchronized (this) {
            if (mcflare$carrier != null) {
                mcflare$carrier.close();
                mcflare$carrier = null;
            }
        }
    }

    @Intrinsic
    public void connection$setMcflareCarrier(LoopbackCarrier carrier) { mcflare$carrier = carrier; }

    @Intrinsic
    public LoopbackCarrier connection$getMcflareCarrier() { return mcflare$carrier; }
}
