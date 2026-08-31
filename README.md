# MCflare

MCflare carries the normal Minecraft Java TCP connection over a standard WebSocket so an origin-hidden server can sit behind normal Cloudflare orange-cloud HTTP/WebSocket proxying. Cloudflare Tunnel is an optional origin transport for servers that cannot expose HTTPS.

**Player:** install MCflare, enter `play.example.com`, join normally.

**Admin:** run one small MCflare gateway in front of Minecraft, then publish the gateway through orange-cloud proxying (preferred) or Tunnel (optional). No TXT discovery record, player-side `cloudflared`, WARP, custom launcher, or special server address is required.

> Status: experimental. Fabric 26.2 is proven end-to-end. The transport core and gateway compile to Java 8. Orange-first latency benchmarking is the next deployment gate.

## Scope

MCflare transports **only Minecraft's own connection**.

- Vanilla Minecraft traffic works.
- Mod/plugin packets carried inside Minecraft's connection work transparently.
- Mods that open separate TCP/UDP sockets use their own networking and are outside MCflare's scope.
- Simple Voice Chat therefore uses its normal separate UDP port; MCflare does not proxy it.

This boundary keeps MCflare small and avoids turning it into a generic VPN or multiplexer.

## Preferred deployment

```text
Minecraft + MCflare
        | WSS :443
        v
play.example.com (Cloudflare orange proxy)
        | HTTPS/WSS
        v
reverse proxy / TLS
        | local HTTP/WebSocket
        v
MCflare Gateway
        | TCP
        v
127.0.0.1:25565
```

Tunnel remains optional:

```text
Cloudflare -> Tunnel -> same MCflare Gateway -> Minecraft
```

The player protocol and gateway do not change between Orange and Tunnel.

## Current implementation

- `core/` — dependency-free Java-8 RFC6455 client, Minecraft discovery, and loopback carrier.
- `gateway/` — minimal HTTP/WebSocket-to-Minecraft TCP gateway.
- root module — current Fabric 26.2 hooks.

The current routing still uses a Minecraft Status probe over WSS for zero-config detection. The next latency refactor will use a dedicated WebSocket subprotocol and retain the successful discovery socket for the real Minecraft connection, removing an avoidable extra handshake. See `docs/LOW_LATENCY_ARCHITECTURE.md`.

## Build

```bash
./gradlew clean build
```

Start with `docs/PROJECT_KNOWLEDGE.md` for the canonical engineering record, `docs/LOW_LATENCY_ARCHITECTURE.md` for the target Orange-first design, and `docs/TEST_MATRIX.md` for proof gates.

## Attribution

MCflare retains the MIT license and attribution for code derived from the original Modflared project. See `NOTICE.md`.
