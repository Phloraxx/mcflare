# MCflare v1 Architecture

Status: architecture freeze candidate, validated on 2026-09-01.

## Product statement

MCflare carries exactly one thing: the ordered byte stream of a Minecraft Java TCP connection over a standard RFC 6455 WebSocket. It does not parse, transform, authenticate, compress, multiplex, or understand Minecraft gameplay packets.

Player UX: install the MCflare artifact for the player's loader/version, enter the normal server hostname, and join. Server UX: install the server-capable MCflare artifact or run the standalone gateway, then publish its local HTTP/WebSocket listener through either ordinary Cloudflare Orange ingress or Cloudflare Tunnel.

## Architectural invariant

Orange and Tunnel are deployment choices, not MCflare transports. The MCflare client and gateway use the same URL, subprotocol, WebSocket framing, lifecycle, and Minecraft byte stream for both.

```text
Minecraft client
  -> thin loader adapter
  -> loopback TCP carrier
  -> WSS /mcflare, subprotocol mcflare.v1
  -> Cloudflare
       -> Orange -> existing reverse proxy ----┐
       -> Tunnel -> external cloudflared ------┤
                                               v
                                      MCflare gateway
                                               |
                                      PROXY v1 (when enabled)
                                               |
                                      Minecraft TCP server
```

## Components

### Core

`core/` remains independent of Minecraft loaders and Cloudflare products. Its job is TLS/RFC6455 client transport, route selection, lifecycle, and the local loopback carrier.

### Client adapter

The loader/version adapter intercepts only connection establishment and server-list status connection creation. For a protected route it substitutes the loopback carrier address; ordinary servers continue to use Minecraft's own resolved TCP destination.

### Route discovery and durable positive pins

For an unknown DNS hostname, `RouteResolver` may race standard `wss://<host>/mcflare` discovery against ordinary Minecraft TCP reachability. A successful `mcflare.v1` WebSocket is positive trust and is persisted as `host:logicalPort` in `~/.mcflare/known-hosts-v1.txt`. Only positive MCflare knowledge is durable; ordinary/negative results remain a short in-memory cache.

A persisted pin bypasses discovery and direct probing. The client opens required WSS immediately and fails closed if that WSS path is unavailable, so a previously proven MCflare hostname cannot silently downgrade to raw Minecraft TCP after a client restart. The pin file is intentionally line-oriented and minimal: non-empty corrupt data fails closed, and a newly discovered route is not added to the trusted in-memory set until its durable append succeeds.

### Gateway

The gateway accepts one HTTP/WebSocket endpoint, validates `/mcflare` and `mcflare.v1`, opens one configured Minecraft backend, and copies bytes bidirectionally. One gateway instance maps to one Minecraft server instance. Hostname routing belongs to Traefik/Caddy/Nginx or cloudflared ingress rules.

### Server adapter

On Fabric and NeoForge, the same loader artifact can run on the dedicated server. Both loaders compile the same root Minecraft adapter source. It starts a local gateway and adds a minimal loopback-trusted PROXY-v1 prefix parser to Minecraft's Netty listener so the real visitor address can become the connection remote address. The parser uses only Netty core types already supplied by Minecraft; MCflare does not bundle `netty-codec-haproxy`.

On Paper and Purpur, MCflare does not patch Minecraft networking. A single Java-21 plugin starts/stops the same shared gateway and the platform's native HAProxy PROXY support restores the visitor address. The final plugin binary is runtime-proven unchanged on 1.21.11, 26.1.2 and 26.2 for both Paper and Purpur.

## One server, multiple Minecraft instances

Each Minecraft instance keeps its own public hostname and port. MCflare does not add a generic router.

```text
survival.example.com -> ingress -> MCflare A -> :25565
creative.example.com -> ingress -> MCflare B -> :25566
modded.example.com   -> ingress -> MCflare C -> :25567
```

Orange uses the administrator's existing reverse proxy. Tunnel uses cloudflared's existing hostname/path-to-service routing. This avoids duplicating infrastructure features inside MCflare.

## Security boundaries

- WSS protects the client-to-Cloudflare transport; Minecraft's own encryption/authentication remains end-to-end inside the byte stream.
- The gateway trusts Cloudflare visitor-IP headers only when its ingress is controlled by Cloudflare/reverse-proxy infrastructure.
- The shared Fabric/NeoForge server adapter accepts PROXY-protocol metadata only from loopback connections by default.
- Protected gameplay must never depend on a browser challenge or interactive Cloudflare Access flow.
- MCflare does not hold Cloudflare API tokens, Tunnel tokens, Tunnel UUIDs, or DNS credentials.
- Tunnel lifecycle is outside MCflare.

## Deliberate non-goals

No UDP, voice-chat transport, generic TCP services, WebSocket compression, Minecraft packet parsing, stream multiplexing, Cloudflare API management, ACME/TLS termination, session replay/resume, browser UI, account system, or arbitrary service discovery in v1.

Mods whose packets already use the Minecraft connection are carried transparently. Mods that open separate sockets remain separate services.

## Why the loopback carrier stays

The local TCP hop keeps Minecraft seeing an ordinary TCP connection and leaves the loader adapter small. Replacing Minecraft's Netty transport directly would save negligible latency while multiplying version/loader hooks. The current model has already completed real 26.2 Status/login traffic.

## Proven 2026-09-01 gates

- Java 25 clean build on macOS restored and passed.
- Clean build reconstructed and passed on Oracle ARM64: 19 tasks.
- Fabric and NeoForge artifacts load in dedicated-server environments; one shared Java adapter source is proven on 1.21.11, 26.1 and 26.2 for both loaders.
- Integrated server gateway starts automatically on configured local address.
- Synthetic Cloudflare `CF-Connecting-IP` -> gateway -> PROXY v1 -> Fabric server -> real Minecraft Status: PASS.
- True Orange `/mcflare` -> integrated Fabric server -> Status: PASS.
- Named HTTP Tunnel `/mcflare` -> same integrated Fabric server -> Status: PASS.
- Both live Cloudflare paths delivered visitor-IP and Ray metadata to the gateway.
- Real Fabric 26.1 Minecraft client Quick Play joined a world through true Orange `/mcflare` and named Tunnel `/mcflare`.
- Both real joins restored `144.24.114.90` into Minecraft's login log, matching the Oracle client host public IPv4.
- A real Fabric 26.1 true-Orange session remained on one WSS/login connection for 31m27s and survived seven distant fresh-chunk teleports over 5m07s without a transport reconnect; final health/online checks remained good.
- A separate 1801.449-second active-gameplay latency/jitter acceptance completed 120 cycles and 240/240 probes with zero route mismatches, the player online throughout and exactly one gameplay WSS connection per cycle. True Orange measured mean/p50/p95/max 155.58/145.57/187.57/451.25 ms; named Tunnel measured 164.34/154.36/197.85/447.76 ms.
- Three simultaneous real Fabric 26.1 clients were held in-world through the same gateway (two true Orange, one named Tunnel); all three survived separate-region chunk generation, and a deliberate Tunnel-client disconnect/replacement did not disturb the two Orange sessions.
- Restarting only the local named-Tunnel `cloudflared` connector while a real client was in-world caused a bounded clean Minecraft disconnect; both WSS/backend sockets closed, the connector recovered, and a fresh real client rejoined with the restored visitor IP. This does not claim a Cloudflare-edge outage test.

## Remaining release gates

Real rebuilt Fabric 26.1 player login/world join through `/mcflare` is proven on both true Orange and named Tunnel, including restored IP visibility in Minecraft login logs. Minecraft's native `ban-ip` also acts on that restored address and rejects a fresh Cloudflare-routed reconnect. The same MCflare-equipped client joins an ordinary zero-MCflare server through normal TCP, and the actual graphical Multiplayer server-list pinger successfully resolves and renders that ordinary server. A real external IPv6 client has now proven true-Orange visitor-IP restoration as `PROXY TCP6` and a live Status response through the Fabric parser. Public WSS Status concurrency is characterized to 128 simultaneous connections per delivery path with zero failures in the measured run. Remaining before stable release: authenticated online-mode proof if required, higher-scale real-gameplay load/churn characterization, and actual Cloudflare-edge interruption behavior. The fresh-chunk teleport-burst, public-IPv6, local connector-restart, and true-Orange client-network black-hole teardown/recovery gates are complete. Fabric/NeoForge server-side loader/version gates for 1.21.11/26.1/26.2 are complete.

## Lazy backend connection

The gateway completes the HTTP/WebSocket upgrade first but does not open the Minecraft TCP backend until the first binary application bytes arrive. Standard WebSocket Ping/Pong can therefore keep the Cloudflare-side connection healthy during discovery without starting Minecraft's pre-handshake timeout. Gateway connection slots remain bounded by `max-connections`.
