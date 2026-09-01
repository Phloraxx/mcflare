package io.mcflare.client;

import com.mojang.logging.LogUtils;
import io.mcflare.core.RouteResolver;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.nio.file.Paths;

/** Loader-neutral client transport state. Mixins initialize this class on first use. */
public final class McflareClient {
    public static final String MOD_ID = "mcflare";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final RouteResolver ROUTES = new RouteResolver(routePins());

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(ROUTES::close, "mcflare-shutdown"));
    }

    private static Path routePins() {
        String home = System.getProperty("user.home");
        if (home == null || home.trim().isEmpty()) return null;
        return Paths.get(home, ".mcflare", "known-hosts-v1.txt");
    }

    private McflareClient() {}
}
