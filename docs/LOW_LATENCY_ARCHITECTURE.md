# MCflare Low-Latency Architecture

Status: proposed next architecture after named-Tunnel/SVC proof. Orange-cloud HTTP/WebSocket proxying is the preferred production deployment; Cloudflare Tunnel remains an optional transport to the same gateway.

## 1. Goal

The target is not literally zero network latency; any proxy adds path length. The engineering goal is to make **MCflare's own processing overhead negligible** so almost all steady-state latency is the unavoidable network route:

```text
player -> nearest Cloudflare edge -> Oracle origin
```

The current Tunnel path adds another stateful connector/overlay leg:

```text
player -> Cloudflare edge -> Tunnel overlay -> cloudflared -> gateway -> Minecraft
```

Orange-cloud mode removes the Tunnel connector, Tunnel QUIC/HTTP2 transport, connector reconnection state, and Tunnel ingress processing from the normal gameplay path.

## 2. Performance targets

- One long-lived WebSocket per Minecraft connection.
- No second discovery WebSocket before the real connection.
- No capability/HELLO connection for known side services.
- No in-band Minecraft-vs-service multiplexer.
- No packet compression or transformation inside MCflare.
- `TCP_NODELAY` on latency-sensitive TCP sockets.
- Loopback/reverse-proxy/gateway overhead should remain operationally negligible compared with WAN RTT.
- Benchmark Orange vs Tunnel vs direct baseline before declaring a production latency target.
## 3. Unified deployment model

The client protocol is identical regardless of how Cloudflare reaches the gateway.

### Preferred: Orange-cloud proxy

```text
Minecraft + MCflare
        |
        | WSS :443
        v
play.example.com (Cloudflare proxied DNS)
        |
        v
origin TLS/reverse proxy
        |
        v
MCflare Gateway
        |
        +--> 127.0.0.1:25565 Minecraft
        +--> 127.0.0.1:24454 UDP voice (optional)
```

### Optional: Cloudflare Tunnel

```text
Minecraft + MCflare -> WSS :443 -> Cloudflare -> cloudflared -> same MCflare Gateway
```

Tunnel is for CGNAT, no-public-IP, or zero-public-ingress deployments. There is no separate Tunnel-specific MCflare protocol and no raw `tcp://Minecraft` mode in the preferred architecture.
## 4. WebSocket upgrade is discovery

Do not open a WebSocket, send a Minecraft Status request to prove MCflare, close it, then open another WebSocket for gameplay.

Use one standardized upgrade:

```text
GET /.well-known/mcflare HTTP/1.1
Upgrade: websocket
Sec-WebSocket-Protocol: mcflare.v1
```

The gateway accepts only the MCflare path/protocol and returns the same subprotocol in the `101 Switching Protocols` response. A successful upgrade is therefore sufficient proof that the hostname supports MCflare.

The successful WebSocket is a **prepared transport**. Keep it alive and give it to `LoopbackCarrier`; do not reconnect.

This removes Minecraft Status parsing from route discovery and removes one TCP + TLS + WebSocket handshake from the first protected connection.

Server-list status still works normally: Minecraft's real status packets pass through the prepared carrier after selection, so the gateway does not synthesize or parse Minecraft status.

Positive cache entries mean "this hostname is expected to be MCflare". On a cached-positive route, directly open the real WebSocket and fail closed if it cannot be established. Negative cache entries skip WebSocket discovery briefly for ordinary servers.
## 5. Side services use URL paths, not MCF1

Use HTTP/WebSocket routing for service selection:

```text
/.well-known/mcflare
    -> Minecraft byte stream

/.well-known/mcflare/v1/datagram/voicechat
    -> UDP service `voicechat`

/.well-known/mcflare/v1/stream/<service>
    -> reserved; implement only when a real TCP-side-service adapter exists
```

For a datagram service, the gateway rejects an unknown service during setup. After upgrade it sends a tiny server-first acknowledgement; the client sends no application bytes until the acknowledgement arrives. This makes failure against a non-Enhanced/legacy endpoint safe and fail-closed.

Keep explicit `u16 length + payload` datagram records. WebSocket frame boundaries are transport details and must not define UDP packet boundaries.

Delete from the hot path:

- `MCF1` magic sniffing.
- HELLO/capability JSON.
- `GatewayControlClient`.
- `OPEN_DATAGRAM` and speculative `OPEN_STREAM` opcodes.
- capability `CompletableFuture` in the SVC adapter.

The SVC adapter already knows it requires `voicechat`; one direct service connection is enough.
## 6. Loader-independent route resolver

Move route policy from the Fabric `TunnelManager` into Java-8 `core` before adding more loaders.

The loader adapter supplies only:

- logical hostname and port entered by the player;
- current Minecraft protocol version only if a future feature genuinely needs it;
- Minecraft's already-resolved/SRV destination for ordinary direct TCP.

Core owns hostname normalization, positive/negative caches, in-flight probe deduplication, WebSocket preparation, direct reachability checks, and fail-closed policy.

Use one in-flight future per hostname instead of a separate `probeLocks` map. Concurrent server-list pings should share discovery work.

For an unknown server, start the MCflare upgrade asynchronously while checking ordinary TCP reachability on the existing Minecraft worker thread. If the MCflare upgrade succeeds, it wins. If ordinary TCP works and MCflare does not establish inside the short preference window, select direct. If ordinary TCP is unavailable, wait for the full MCflare setup timeout.

Do not own DNS/SRV resolution. Minecraft remains authoritative for ordinary-server resolution.

## 7. Carrier ownership

Keep the localhost carrier. It costs essentially no WAN latency and avoids version-specific Netty transport implementations.

Collapse `RunningTunnel`/`TunnelStatus` where practical. A Minecraft `Connection` should directly own the `LoopbackCarrier` selected for that connection. Disconnect or setup failure closes it. Once a hostname is confirmed MCflare, carrier/WebSocket failure never falls back to direct TCP.
## 8. Gateway runtime

Keep blocking I/O and simple pipes; do not introduce Netty just to reduce thread count. The standalone gateway can target a modern Java runtime even though the client core remains Java 8.

A later implementation may use Java 21 virtual threads for accepted WebSockets and downstream pipes. That preserves readable blocking code while making hundreds of long-lived connections cheap. Keep an explicit configurable connection ceiling regardless of thread model.

Gateway duties should remain only:

1. validate HTTP/WebSocket upgrade and MCflare subprotocol;
2. select route from the URL path;
3. expose trusted Cloudflare request metadata to logging/rate-limit hooks;
4. stream Minecraft bytes unchanged;
5. frame/unframe configured UDP side services;
6. enforce connection/frame/datagram/time limits.

No Minecraft packet parser belongs in the gateway.

## 9. Orange-cloud origin layout

Preferred production layout when the server has a public IP:

```text
Cloudflare :443 -> existing TLS reverse proxy -> MCflare Gateway on private/loopback port
```

Use Full (strict) TLS to the origin. Prefer an Origin CA or publicly trusted origin certificate. Restrict origin access to Cloudflare where operationally possible; Authenticated Origin Pulls can provide additional origin authentication. Never expose Minecraft TCP 25565 or SVC UDP 24454 merely to make Orange mode work.

If port 443 is already managed by Dokploy/Traefik/Caddy, reuse it. A same-host reverse proxy adds negligible latency compared with WAN RTT and is simpler/safer than implementing certificate management in the Java gateway.
## 10. Latency model

For Minecraft itself, MCflare should add almost no compute latency. WebSocket framing is tiny compared with network RTT, and Minecraft is already TCP-based.

The dominant variable is routing:

```text
direct: player -> Oracle
Orange: player -> Cloudflare edge -> Oracle
Tunnel: player -> Cloudflare edge -> Tunnel connector path -> Oracle
```

Orange is expected to remove the connector/overlay penalty that caused the observed Tunnel ping increase, but this must be measured. Cloudflare anycast decides the edge PoP and Argo Smart Routing is not compatible with WebSockets, so MCflare cannot guarantee that every ISP/location gets a route as short as direct Oracle.

Do not optimize for handshake latency at the expense of steady-state simplicity. The important gameplay metric is established-session RTT/jitter. Still, prepared WebSockets remove an avoidable duplicate first-connection handshake.

Benchmark at minimum:

- direct-origin test baseline from the same client/network;
- Orange WSS Minecraft path;
- Tunnel WSS Minecraft path;
- p50/p95/p99 application RTT and jitter over several minutes;
- packet throughput during chunk loading/teleport;
- CPU, allocations, and gateway scheduling under multiple clients.

Orange becomes the official default only after it wins the actual A/B test or stays within an agreed small overhead budget.
## 11. Voice latency policy

Simple Voice Chat over WSS is proven functionally, including through the named Tunnel. Orange should reduce its route overhead too, but voice remains more sensitive than Minecraft because TCP retransmission can cause head-of-line delay.

Therefore:

- Keep WSS datagrams as the zero-extra-infrastructure default.
- Measure real two-client speech over Orange before adding another transport.
- If voice latency/jitter or loss recovery is materially worse than direct UDP, add a low-latency UDP relay transport (for example TURN) behind the existing SVC socket abstraction.
- Do not add TURN credentials/allocation/session logic until the WSS audio test demonstrates the need.

Minecraft and voice must remain independent channels. A voice stall must never block the Minecraft WebSocket.

## 12. Cloudflare-specific settings

- WebSockets must be enabled.
- Do not put browser-interactive Access or Managed Challenge behavior on MCflare paths.
- WAF/rate limiting may protect the initial `101` upgrade; established WebSocket payloads are not inspected by WAF.
- Keep application-level heartbeat; Cloudflare can close idle WebSockets and edge deployments can terminate established sessions.
- Do not enable Argo expecting a WebSocket latency improvement; Cloudflare currently documents WebSockets as incompatible with Argo.
- Do not enable HTTP/2-to-origin for the hand-written HTTP/1.1 gateway unless the TLS reverse proxy terminates that protocol and forwards a normal WebSocket to MCflare.
## 13. Migration from the current proven implementation

Implement in small regression-safe stages:

1. Add the Orange-first architecture document and benchmark plan without changing wire behavior.
2. Add WebSocket subprotocol validation to client/gateway.
3. Make successful discovery return a prepared live WebSocket and reuse it for the actual carrier.
4. Replace Minecraft-status discovery with upgrade-only discovery.
5. Move discovery/cache policy to Java-8 core.
6. Change Enhanced service selection from MCF1 to URL paths.
7. Simplify SVC to one direct voice-service connection.
8. Remove `GatewayControlClient`, MCF1 control/opcodes, and unused speculative stream implementation.
9. Collapse redundant carrier wrapper/global lifecycle state.
10. Deploy an Orange-cloud test hostname to the same gateway and run direct/Orange/Tunnel latency A/B tests.
11. Only after the new path is proven, make Orange the documented default and Tunnel the optional deployment.

Every stage must keep ordinary-server fallback, protected fail-closed behavior, no-SVC loading, named-Tunnel Minecraft, and named-Tunnel SVC tests green.

## 14. Definition of "seamless"

Player: install the matching MCflare client once, type the normal Minecraft hostname, join. No helper daemon, local port, TXT/SRV metadata, Cloudflare account, WARP, or per-server MCflare configuration.

Admin Orange: run Minecraft, run one MCflare Gateway sidecar, expose only HTTPS through the existing reverse proxy, create one proxied DNS record, keep game/voice origin ports private.

Admin Tunnel: same gateway and client protocol; replace public origin routing with `cloudflared -> gateway` when inbound HTTPS is impossible or intentionally forbidden.
