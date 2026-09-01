package io.mcflare.client;

import com.mojang.logging.LogUtils;
import io.mcflare.core.RouteResolver;
import org.slf4j.Logger;

/** Loader-neutral client transport state. Mixins initialize this class on first use. */
public final class McflareClient {
    public static final String MOD_ID = "mcflare";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final RouteResolver ROUTES = new RouteResolver();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(ROUTES::close, "mcflare-shutdown"));
    }

    private McflareClient() {}
}
