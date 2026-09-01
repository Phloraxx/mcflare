package io.mcflare.server;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.ProtocolDetectionResult;
import io.netty.handler.codec.ProtocolDetectionState;
import io.netty.handler.codec.haproxy.HAProxyMessageDecoder;
import io.netty.handler.codec.haproxy.HAProxyProtocolVersion;

public final class ProxyProtocolDetector extends ChannelInboundHandlerAdapter {
    private ByteBuf buffered;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object message) {
        if (!(message instanceof ByteBuf bytes)) {
            ctx.fireChannelRead(message);
            return;
        }
        if (buffered == null) buffered = bytes;
        else { buffered.writeBytes(bytes); bytes.release(); }

        ProtocolDetectionResult<HAProxyProtocolVersion> result = HAProxyMessageDecoder.detectProtocol(buffered);
        if (result.state() == ProtocolDetectionState.NEEDS_MORE_DATA) return;

        ChannelPipeline pipeline = ctx.pipeline();
        String name = ctx.name();
        if (result.state() == ProtocolDetectionState.DETECTED) {
            pipeline.addAfter(name, "mcflare-haproxy-decoder", new HAProxyMessageDecoder());
            pipeline.addAfter("mcflare-haproxy-decoder", "mcflare-haproxy-address", new ProxyProtocolAddressHandler());
        }
        ByteBuf replay = buffered;
        buffered = null;
        pipeline.remove(this);
        pipeline.fireChannelRead(replay);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        if (buffered != null) { buffered.release(); buffered = null; }
    }
}
