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
    @Unique private boolean mcflare$disconnected;

    @Inject(method = "disconnect*", at = @At("HEAD"))
    public void disconnect(Component reason, CallbackInfo callbackInfo) {
        synchronized (this) {
            mcflare$disconnected = true;
            if (mcflare$carrier != null) {
                mcflare$carrier.close();
                mcflare$carrier = null;
            }
        }
    }

    @Intrinsic
    public void connection$setMcflareCarrier(LoopbackCarrier carrier) {
        synchronized (this) {
            if (mcflare$disconnected) {
                if (carrier != null) carrier.close();
                return;
            }
            if (mcflare$carrier != null && mcflare$carrier != carrier) mcflare$carrier.close();
            mcflare$carrier = carrier;
        }
    }
}
