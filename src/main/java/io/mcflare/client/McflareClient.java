package io.mcflare.client;

import com.mojang.logging.LogUtils;
import io.mcflare.core.RouteResolver;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;

public final class McflareClient implements ClientModInitializer {
    public static final String MOD_ID = "mcflare";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final RouteResolver ROUTES = new RouteResolver();

    @Override
    public void onInitializeClient() {
        LOGGER.info("MCflare WebSocket transport initialized");
        Runtime.getRuntime().addShutdownHook(new Thread(ROUTES::close, "mcflare-shutdown"));
    }
}
