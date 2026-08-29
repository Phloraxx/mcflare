package io.mcflare.client;

import com.mojang.logging.LogUtils;
import io.mcflare.client.tunnel.manager.TunnelManager;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class McflareClient implements ClientModInitializer {
    public static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    public static final String MOD_ID = "mcflare";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final TunnelManager TUNNEL_MANAGER = new TunnelManager();

    @Override
    public void onInitializeClient() {
        LOGGER.info("MCflare direct WebSocket carrier initialized (no client cloudflared binary)");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            TUNNEL_MANAGER.closeTunnels();
            EXECUTOR.shutdownNow();
        }, "mcflare-shutdown"));
    }
}
