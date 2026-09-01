package io.mcflare.server.mixin;

import io.mcflare.gateway.McflareGateway;
import io.mcflare.server.McflareServerConfig;
import java.net.InetSocketAddress;
import net.minecraft.server.dedicated.DedicatedServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DedicatedServer.class)
public abstract class DedicatedServerMixin {
    @Unique private static final Logger MCFLARE_LOGGER = LoggerFactory.getLogger("MCflare");
    @Unique private McflareGateway mcflare$gateway;

    @Inject(method = "initServer", at = @At("RETURN"))
    private void mcflare$startGateway(CallbackInfoReturnable<Boolean> callback) {
        if (!Boolean.TRUE.equals(callback.getReturnValue())) return;
        DedicatedServer server = (DedicatedServer) (Object) this;
        try {
            McflareServerConfig config = McflareServerConfig.load();
            if (!config.enabled) {
                MCFLARE_LOGGER.info("MCflare server endpoint disabled by config");
                return;
            }
            String host = server.getServerIp();
            if (host == null || host.isBlank() || "0.0.0.0".equals(host) || "::".equals(host)) host = "127.0.0.1";
            InetSocketAddress minecraft = new InetSocketAddress(host, server.getServerPort());
            mcflare$gateway = McflareGateway.startAsync(config.listen, minecraft, config.maxConnections, true);
            MCFLARE_LOGGER.info("MCflare server endpoint listening on {} for Minecraft {} (PROXY v1 enabled)",
                    config.listen, minecraft);
        } catch (Exception error) {
            MCFLARE_LOGGER.error("MCflare server endpoint failed to start; Minecraft will continue without MCflare", error);
        }
    }

    @Inject(method = "stopServer", at = @At("HEAD"))
    private void mcflare$stopGateway(CallbackInfo callback) {
        if (mcflare$gateway == null) return;
        try { mcflare$gateway.close(); }
        catch (Exception ignored) {}
        mcflare$gateway = null;
    }
}
