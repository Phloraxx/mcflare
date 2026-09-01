package io.mcflare.server;

import io.mcflare.server.mixin.ConnectionAddressAccessor;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.haproxy.HAProxyCommand;
import io.netty.handler.codec.haproxy.HAProxyMessage;
import java.net.InetSocketAddress;
import net.minecraft.network.Connection;

public final class ProxyProtocolAddressHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object message) throws Exception {
        if (!(message instanceof HAProxyMessage proxy)) {
            super.channelRead(ctx, message);
            return;
        }
        try {
            if (proxy.command() != HAProxyCommand.PROXY || proxy.sourceAddress() == null) {
                ctx.close();
                return;
            }
            Connection connection = (Connection) ctx.channel().pipeline().get("packet_handler");
            if (connection == null) {
                ctx.close();
                return;
            }
            ((ConnectionAddressAccessor) connection).mcflare$setAddress(
                    new InetSocketAddress(proxy.sourceAddress(), proxy.sourcePort()));
        } finally {
            proxy.release();
        }
    }
}
