package io.mcflare.server.mixin;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChannelInitializer.class)
public interface ChannelInitializerInvoker {
    @Invoker(value = "initChannel", remap = false)
    void mcflare$invokeInitChannel(Channel channel) throws Exception;
}
