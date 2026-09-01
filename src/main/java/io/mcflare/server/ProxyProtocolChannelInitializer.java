package io.mcflare.server;

import io.mcflare.server.mixin.ChannelInitializerInvoker;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import java.net.InetSocketAddress;

public final class ProxyProtocolChannelInitializer extends ChannelInitializer<Channel> {
    private final ChannelInitializerInvoker vanilla;

    public ProxyProtocolChannelInitializer(ChannelInitializerInvoker vanilla) {
        this.vanilla = vanilla;
    }

    @Override
    protected void initChannel(Channel channel) throws Exception {
        vanilla.mcflare$invokeInitChannel(channel);
        if (!(channel.remoteAddress() instanceof InetSocketAddress remote)) return;
        if (remote.getAddress() == null || !remote.getAddress().isLoopbackAddress()) return;
        channel.pipeline().addAfter("timeout", "mcflare-proxy-detector", new ProxyProtocolDetector());
    }
}
