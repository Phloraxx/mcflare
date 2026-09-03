package io.mcflare.server;

import io.mcflare.gateway.ProxyProtocolSourceTrust;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import java.net.InetSocketAddress;

/** Wraps Minecraft's normal child initializer, then adds optional loopback PROXY detection. */
public final class ProxyProtocolChannelInitializer extends ChannelInitializer<Channel> {
    private final ChannelHandler vanilla;

    public ProxyProtocolChannelInitializer(ChannelHandler vanilla) {
        this.vanilla = vanilla;
    }

    @Override
    protected void initChannel(Channel channel) {
        // Let Netty run Minecraft's own ChannelInitializer through its normal handler lifecycle.
        // This avoids loader-specific reflective/Mixin access to protected initChannel().
        channel.pipeline().addLast("mcflare-vanilla-initializer", vanilla);
        if (!(channel.remoteAddress() instanceof InetSocketAddress)) return;
        InetSocketAddress remote = (InetSocketAddress) channel.remoteAddress();
        if (remote.getAddress() == null || !ProxyProtocolSourceTrust.isTrusted(remote.getAddress())) return;
        if (channel.pipeline().get("timeout") != null) {
            channel.pipeline().addAfter("timeout", "mcflare-proxy-detector", new ProxyProtocolDetector());
        } else {
            channel.pipeline().addFirst("mcflare-proxy-detector", new ProxyProtocolDetector());
        }
    }
}
