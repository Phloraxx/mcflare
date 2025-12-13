package dev.httxrafa.modflared;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import dev.httxrafa.modflared.tunnel.manager.TunnelManager;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Modflared implements ClientModInitializer {

    public static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().serializeNulls().create();

    public static final String MOD_ID = "modflared";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final TunnelManager TUNNEL_MANAGER = new TunnelManager();

    @Override
    public void onInitializeClient() {
        TUNNEL_MANAGER.initDirectories();
        TUNNEL_MANAGER.prepareBinary();
        TUNNEL_MANAGER.loadForcedTunnels();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            TUNNEL_MANAGER.closeTunnels();
            EXECUTOR.shutdownNow();
        }));
    }

}
