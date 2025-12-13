package dev.httxrafa.modflared;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import dev.httxrafa.modflared.tunnel.manager.TunnelManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import org.slf4j.Logger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Mod(value = Modflared.MOD_ID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = Modflared.MOD_ID, value = Dist.CLIENT)
public class Modflared {

    public static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().serializeNulls().create();

    public static final String MOD_ID = "modflared";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final TunnelManager TUNNEL_MANAGER = new TunnelManager();

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        TUNNEL_MANAGER.initDirectories();
        TUNNEL_MANAGER.prepareBinary();
        TUNNEL_MANAGER.loadForcedTunnels();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            TUNNEL_MANAGER.closeTunnels();
            EXECUTOR.shutdownNow();
        }));
    }

}
