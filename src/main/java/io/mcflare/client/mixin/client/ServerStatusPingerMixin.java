package io.mcflare.client.mixin.client;

import io.mcflare.client.McflareClient;
import io.mcflare.client.tunnel.TunnelStatus;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.Connection;
import net.minecraft.server.network.EventLoopGroupHolder;
import net.minecraft.util.debugchart.LocalSampleLogger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.net.InetSocketAddress;

@Mixin(ServerStatusPinger.class)
public abstract class ServerStatusPingerMixin {
    @Redirect(method = "pingServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/Connection;connectToServer(Ljava/net/InetSocketAddress;Lnet/minecraft/server/network/EventLoopGroupHolder;Lnet/minecraft/util/debugchart/LocalSampleLogger;)Lnet/minecraft/network/Connection;"))
    public Connection pingServer(InetSocketAddress address, EventLoopGroupHolder holder,
                                 LocalSampleLogger localSampleLogger, ServerData data) {
        ServerAddress logical = ServerAddress.parseString(data.ip);
        TunnelStatus status = McflareClient.TUNNEL_MANAGER.handleConnect(
                logical.getHost(), logical.getPort(), address);
        InetSocketAddress target = status.usesMcflare()
                ? status.runningTunnel().access().tunnelAddress()
                : address;

        try {
            Connection connection = Connection.connectToServer(target, holder, localSampleLogger);
            McflareClient.TUNNEL_MANAGER.prepareConnection(status, connection);
            return connection;
        } catch (RuntimeException | Error error) {
            if (status.usesMcflare()) {
                McflareClient.TUNNEL_MANAGER.closeTunnel(status.runningTunnel());
            }
            throw error;
        }
    }
}
