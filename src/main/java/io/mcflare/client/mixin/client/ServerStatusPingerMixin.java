package io.mcflare.client.mixin.client;

import io.mcflare.client.McflareClient;
import io.mcflare.client.interfaces.mixin.IConnection;
import io.mcflare.core.LoopbackCarrier;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.Connection;
import net.minecraft.server.network.EventLoopGroupHolder;
import net.minecraft.util.debugchart.LocalSampleLogger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.net.InetSocketAddress;

@Mixin(ServerStatusPinger.class)
public abstract class ServerStatusPingerMixin {
    @Redirect(method = "pingServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/Connection;connectToServer(Ljava/net/InetSocketAddress;Lnet/minecraft/server/network/EventLoopGroupHolder;Lnet/minecraft/util/debugchart/LocalSampleLogger;)Lnet/minecraft/network/Connection;"))
    public Connection pingServer(InetSocketAddress address, EventLoopGroupHolder holder,
                                 LocalSampleLogger samples, ServerData data) {
        ServerAddress logical = ServerAddress.parseString(data.ip);
        String host = logical.getHost();
        LoopbackCarrier carrier = McflareClient.ROUTES.prepare(host, logical.getPort(), address,
                error -> McflareClient.LOGGER.debug("MCflare carrier closed for {}: {}", host, error.toString()));
        InetSocketAddress target = carrier == null ? address : carrier.getLocalAddress();
        try {
            Connection connection = Connection.connectToServer(target, holder, samples);
            if (carrier != null) ((IConnection) connection).setMcflareCarrier(carrier);
            return connection;
        } catch (RuntimeException | Error error) {
            if (carrier != null) carrier.close();
            throw error;
        }
    }
}
