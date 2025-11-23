package de.rafael.modflared.mixin;

import de.rafael.modflared.Modflared;
import de.rafael.modflared.interfaces.mixin.IConnection;
import de.rafael.modflared.tunnel.RunningTunnel;
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
    private RunningTunnel modflared$runningTunnel = null;

    /* Replaced by MultiplayerServerListPingerMixin
    @Redirect(method = "connect(Ljava/net/InetSocketAddress;ZLnet/minecraft/util/profiler/PerformanceLog;)Lnet/minecraft/network/ClientConnection;", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/ClientConnection;connect(Ljava/net/InetSocketAddress;ZLnet/minecraft/network/ClientConnection;)Lio/netty/channel/ChannelFuture;"))
    private static ChannelFuture connect(@NotNull InetSocketAddress address, boolean useEpoll, ClientConnection connection) {
        return ClientConnection.connect(Modflared.TUNNEL_MANAGER.handleConnect(address, connection).address(), useEpoll, connection);
    }*/

    @Inject(method = "disconnect*", at = @At("TAIL"))
    public void disconnect(Component disconnectReason, CallbackInfo callbackInfo) {
        synchronized(this) {
            if(this.modflared$runningTunnel != null) {
                Modflared.TUNNEL_MANAGER.closeTunnel(this.modflared$runningTunnel);
                this.modflared$runningTunnel = null;
            }
        }
    }

    @Intrinsic
    public void connection$setRunningTunnel(RunningTunnel runningTunnel) {
        this.modflared$runningTunnel = runningTunnel;
    }
    
}
