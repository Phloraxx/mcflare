package de.rafael.modflared.mixin.client;

import de.rafael.modflared.Modflared;
import de.rafael.modflared.interfaces.mixin.IServerData;
import de.rafael.modflared.tunnel.TunnelStatus;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.network.Connection;
import net.minecraft.util.debugchart.LocalSampleLogger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.net.InetSocketAddress;

@Mixin(ServerStatusPinger.class)
public abstract class ServerStatusPingerMixin {

    @Redirect(method = "pingServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/Connection;connectToServer(Ljava/net/InetSocketAddress;ZLnet/minecraft/util/debugchart/LocalSampleLogger;)Lnet/minecraft/network/Connection;"))
    public Connection pingServer(InetSocketAddress address, boolean useEpoll, LocalSampleLogger localSampleLogger, ServerData data) {
        var result = Modflared.TUNNEL_MANAGER.handleConnect(address);
        if(result.state() == TunnelStatus.State.USE) {
            var connection = Connection.connectToServer(result.runningTunnel().access().tunnelAddress(), useEpoll, localSampleLogger);
            Modflared.TUNNEL_MANAGER.prepareConnection(result, connection);
            ((IServerData) data).setTunnelStatus(result);
            return connection;
        }
        return Connection.connectToServer(address, useEpoll, localSampleLogger);
    }

}
