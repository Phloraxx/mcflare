package io.mcflare.server.mixin;

import io.mcflare.server.ProxyProtocolChannelInitializer;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelHandler;
import net.minecraft.server.network.ServerConnectionListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerConnectionListener.class)
public abstract class ServerConnectionListenerMixin {
    @Redirect(method = "startTcpServerListener", at = @At(value = "INVOKE",
            target = "Lio/netty/bootstrap/ServerBootstrap;childHandler(Lio/netty/channel/ChannelHandler;)Lio/netty/bootstrap/ServerBootstrap;", remap = false))
    private ServerBootstrap mcflare$installProxyProtocol(ServerBootstrap bootstrap, ChannelHandler vanilla) {
        return bootstrap.childHandler(new ProxyProtocolChannelInitializer(vanilla));
    }
}
