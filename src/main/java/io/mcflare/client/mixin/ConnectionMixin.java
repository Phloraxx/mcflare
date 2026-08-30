package io.mcflare.client.mixin;

import io.mcflare.client.McflareClient;
import io.mcflare.client.interfaces.mixin.IConnection;
import io.mcflare.client.tunnel.RunningTunnel;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Implements(@Interface(iface = IConnection.class, prefix = "connection$"))
@Mixin(Connection.class)
public abstract class ConnectionMixin implements IConnection {

    @Unique
    private RunningTunnel mcflare$runningTunnel = null;

    @Inject(method = "disconnect*", at = @At("TAIL"))
    public void disconnect(Component disconnectReason, CallbackInfo callbackInfo) {
        synchronized(this) {
            if(this.mcflare$runningTunnel != null) {
                McflareClient.TUNNEL_MANAGER.closeTunnel(this.mcflare$runningTunnel);
                this.mcflare$runningTunnel = null;
            }
        }
    }

    @Intrinsic
    public void connection$setRunningTunnel(RunningTunnel runningTunnel) {
        this.mcflare$runningTunnel = runningTunnel;
    }

    @Intrinsic
    public RunningTunnel connection$getRunningTunnel() {
        return this.mcflare$runningTunnel;
    }
    
}
