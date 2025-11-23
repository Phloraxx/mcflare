package de.rafael.modflared.methods;

import de.rafael.modflared.Modflared;
import de.rafael.modflared.interfaces.mixin.IConnectScreen;
import de.rafael.modflared.tunnel.TunnelStatus;
import io.netty.channel.ChannelFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.network.Connection;
import org.jetbrains.annotations.NotNull;

import java.net.InetSocketAddress;

public class ConnectScreenMethods {

    public static ChannelFuture connect(@NotNull InetSocketAddress address, boolean useEpoll, Connection connection) {
        var status = Modflared.TUNNEL_MANAGER.handleConnect(address);
        Modflared.TUNNEL_MANAGER.prepareConnection(status, connection);

        var currentScreen =  Minecraft.getInstance().screen;
        if (currentScreen instanceof ConnectScreen connectScreen) {
            ((IConnectScreen) connectScreen).setStatus(status);
        }

        return Connection.connect(status.state() == TunnelStatus.State.USE ? status.runningTunnel().access().tunnelAddress() : address, useEpoll, connection);
    }

}
