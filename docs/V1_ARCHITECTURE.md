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

### Gateway

The gateway accepts one HTTP/WebSocket endpoint, validates `/mcflare` and `mcflare.v1`, opens one configured Minecraft backend, and copies bytes bidirectionally. One gateway instance maps to one Minecraft server instance. Hostname routing belongs to Traefik/Caddy/Nginx or cloudflared ingress rules.

### Server adapter

On Fabric, the same JAR can run on the dedicated server. It starts a local gateway and adds a minimal PROXY-protocol decoder to Minecraft's Netty listener so the real visitor address can become the connection remote address.

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
- The Fabric server accepts PROXY-protocol metadata only from loopback connections by default.
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
- Same Fabric 26.2 JAR loads in dedicated-server environment.
- Integrated server gateway starts automatically on configured local address.
- Synthetic Cloudflare `CF-Connecting-IP` -> gateway -> PROXY v1 -> Fabric server -> real Minecraft Status: PASS.
- True Orange `/mcflare` -> integrated Fabric server -> Status: PASS.
- Named HTTP Tunnel `/mcflare` -> same integrated Fabric server -> Status: PASS.
- Both live Cloudflare paths delivered visitor-IP and Ray metadata to the gateway.

## Remaining release gates

Full online-mode player login through the new `/mcflare` path, ordinary-server regression with the dual-side artifact, sustained gameplay/jitter, multiple concurrent clients, additional loader/version adapters, and final source-IP behavior tests for login/logging/ban APIs remain before stable release.

## Lazy backend connection

The gateway completes the HTTP/WebSocket upgrade first but does not open the Minecraft TCP backend until the first binary application bytes arrive. Standard WebSocket Ping/Pong can therefore keep the Cloudflare-side connection healthy during discovery without starting Minecraft's pre-handshake timeout. Gateway connection slots remain bounded by `max-connections`.
