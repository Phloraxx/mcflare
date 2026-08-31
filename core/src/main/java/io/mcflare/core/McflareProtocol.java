package io.mcflare.core;

/** Stable HTTP/WebSocket identifiers shared by client and gateway. */
public final class McflareProtocol {
    public static final String PATH = "/.well-known/mcflare";
    public static final String SUBPROTOCOL = "mcflare.v1";

    private McflareProtocol() {}
}
