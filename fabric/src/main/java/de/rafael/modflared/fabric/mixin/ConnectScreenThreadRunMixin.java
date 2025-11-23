package de.rafael.modflared.fabric.mixin;

import de.rafael.modflared.methods.ConnectScreenMethods;
import io.netty.channel.ChannelFuture;
import net.minecraft.network.Connection;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.net.InetSocketAddress;

@Mixin(targets = "net.minecraft.client.gui.screens.ConnectScreen$1")
public abstract class ConnectScreenThreadRunMixin implements Runnable {

    @Redirect(method = "run", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/Connection;connect(Ljava/net/InetSocketAddress;ZLnet/minecraft/network/Connection;)Lio/netty/channel/ChannelFuture;"))
    private @NotNull ChannelFuture connect(@NotNull InetSocketAddress address, boolean useEpoll, Connection connection) {
        return ConnectScreenMethods.connect(address, useEpoll, connection);
    }

}
