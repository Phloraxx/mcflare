package io.mcflare.client.mixin.client;

import io.mcflare.client.McflareClient;
import io.mcflare.client.interfaces.mixin.IConnection;
import io.mcflare.core.LoopbackCarrier;
import io.netty.channel.ChannelFuture;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.Connection;
import net.minecraft.server.network.EventLoopGroupHolder;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.net.InetSocketAddress;

@Mixin(targets = "net.minecraft.client.gui.screens.ConnectScreen$1")
public class ConnectScreenRunnableMixin {
    @Shadow @Final private ServerAddress val$hostAndPort;

    @Redirect(method = "run", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/Connection;connect(Ljava/net/InetSocketAddress;Lnet/minecraft/server/network/EventLoopGroupHolder;Lnet/minecraft/network/Connection;)Lio/netty/channel/ChannelFuture;"))
    private @NotNull ChannelFuture connect(@NotNull InetSocketAddress address,
                                           EventLoopGroupHolder holder,
                                           Connection connection) {
        String host = val$hostAndPort.getHost();
        LoopbackCarrier carrier = McflareClient.ROUTES.prepare(host, val$hostAndPort.getPort(), address,
                error -> McflareClient.LOGGER.debug("MCflare carrier closed for {}: {}", host, error.toString()));
        InetSocketAddress target = carrier == null ? address : carrier.getLocalAddress();
        try {
            ChannelFuture future = Connection.connect(target, holder, connection);
            if (carrier != null) ((IConnection) connection).setMcflareCarrier(carrier);
            return future;
        } catch (RuntimeException | Error error) {
            if (carrier != null) carrier.close();
            throw error;
        }
    }
}
