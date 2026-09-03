package io.mcflare.server;

import io.mcflare.gateway.ProxyProtocolV1;
import io.mcflare.server.mixin.ConnectionAddressAccessor;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import net.minecraft.network.Connection;

/** Detects an optional trusted PROXY v1 line before ordinary Minecraft bytes. */
public final class ProxyProtocolDetector extends ChannelInboundHandlerAdapter {
    private static final byte[] PREFIX = "PROXY ".getBytes(StandardCharsets.US_ASCII);
    private static final int MAX_LINE_BYTES = 108;
    private ByteBuf buffered;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object message) {
        if (!(message instanceof ByteBuf)) {
            ctx.fireChannelRead(message);
            return;
        }
        ByteBuf bytes = (ByteBuf) message;
        if (buffered == null) buffered = ctx.alloc().buffer(Math.min(256, bytes.readableBytes() + 16));
        buffered.writeBytes(bytes);
        bytes.release();

        int readable = buffered.readableBytes();
        int compare = Math.min(readable, PREFIX.length);
        for (int i = 0; i < compare; i++) {
            if (buffered.getByte(buffered.readerIndex() + i) != PREFIX[i]) {
                replayDirect(ctx);
                return;
            }
        }
        if (readable < PREFIX.length) return;

        int start = buffered.readerIndex();
        int end = findCrlf(buffered, start, buffered.writerIndex());
        if (end < 0) {
            if (readable > MAX_LINE_BYTES) fail(ctx);
            return;
        }
        int lineBytes = end - start + 2;
        if (lineBytes > MAX_LINE_BYTES) { fail(ctx); return; }

        byte[] raw = new byte[end - start];
        buffered.getBytes(start, raw);
        try {
            ProxyProtocolV1.Source source = ProxyProtocolV1.parse(new String(raw, StandardCharsets.US_ASCII));
            Connection connection = (Connection) ctx.pipeline().get("packet_handler");
            if (connection == null) throw new IOException("Minecraft connection handler unavailable");
            ((ConnectionAddressAccessor) connection).mcflare$setAddress(
                    new InetSocketAddress(source.address(), source.port()));
        } catch (IOException error) {
            fail(ctx);
            return;
        }

        buffered.skipBytes(lineBytes);
        replayRemaining(ctx);
    }

    private static int findCrlf(ByteBuf bytes, int start, int end) {
        for (int i = start; i + 1 < end; i++)
            if (bytes.getByte(i) == '\r' && bytes.getByte(i + 1) == '\n') return i;
        return -1;
    }

    private void replayDirect(ChannelHandlerContext ctx) { replayRemaining(ctx); }

    private void replayRemaining(ChannelHandlerContext ctx) {
        ChannelPipeline pipeline = ctx.pipeline();
        ByteBuf replay = buffered;
        buffered = null;
        pipeline.remove(this);
        if (replay.isReadable()) ctx.fireChannelRead(replay);
        else replay.release();
    }

    private void fail(ChannelHandlerContext ctx) {
        if (buffered != null) { buffered.release(); buffered = null; }
        ctx.close();
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        if (buffered != null) { buffered.release(); buffered = null; }
    }
}
