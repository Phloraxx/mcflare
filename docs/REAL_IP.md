# Real Player IP Preservation

Status: v1 core requirement; locally validated on Fabric and NeoForge 1.21.11/26.1/26.2 and live full-client validated through the Fabric integrated gateway on both true Orange and named Tunnel.

## Problem

When MCflare opens the backend Minecraft TCP connection, the backend socket naturally sees the gateway/local proxy address. Server logs, IP bans, moderation, geolocation, and rate-limiting often need the real player address.

Do not add a custom MCflare identity packet. HTTP and TCP ecosystems already provide the required primitives.

## Cloudflare -> MCflare

For ordinary HTTP/WebSocket proxying, Cloudflare sends the connecting visitor address to the origin in `CF-Connecting-IP`. If Pseudo IPv4 is configured to overwrite headers, the real IPv6 address is preserved in `CF-Connecting-IPv6`.

MCflare therefore chooses:

1. non-empty `CF-Connecting-IPv6`, when present;
2. otherwise `CF-Connecting-IP`.

The selected value is accepted only as a syntactically valid IPv4/IPv6 literal. It is never passed to DNS resolution; a hostname or malformed address is rejected rather than resolved.

`CF-Ray` should be logged as connection correlation metadata, not treated as identity.

## MCflare -> Minecraft

The gateway optionally prepends one HAProxy PROXY protocol v1 line before the untouched Minecraft stream.

IPv4 shape:

```text
PROXY TCP4 <real-player-ip> 127.0.0.1 <source-port> <minecraft-port>\r\n
```

IPv6 shape:

```text
PROXY TCP6 <real-player-ip> ::1 <source-port> <minecraft-port>\r\n
```

Then the normal Minecraft bytes follow byte-for-byte.

## Source-port semantics

Cloudflare exposes the real visitor IP in HTTP headers but does not expose the player's original TCP source port to this origin application. MCflare therefore uses the non-zero TCP source port of the HTTP/WebSocket ingress connection as an opaque interoperability value. The player IP is authoritative; MCflare does **not** claim that the substituted port is the player's original Internet source port.

The in-project parser accepts the standard PROXY-v1 source-port range, including zero, but the gateway's normal encoded path uses its available nonzero ingress port.

## Fabric and NeoForge implementation

The shared dedicated-server adapter wraps Minecraft's existing `ServerConnectionListener` child-channel initializer using Netty's normal `ChannelInitializer` lifecycle. For loopback connections it installs a small detector before ordinary Minecraft decoding.

The detector buffers only enough bytes to distinguish the optional `PROXY ` prefix and enforces the PROXY-v1 108-byte text-line maximum. A valid TCP4/TCP6 line is parsed by MCflare's loader-independent `ProxyProtocolV1` codec, the source address is applied to Minecraft's `Connection.address`, and the remaining bytes continue through the normal Minecraft pipeline.

If no PROXY header is detected, normal Minecraft bytes continue untouched. Remote direct clients do not receive the trusted local PROXY treatment. MCflare does not bundle or depend on Netty's separate HAProxy codec module.

## Trust model

`CF-Connecting-IP` is trustworthy only when requests genuinely arrive through trusted Cloudflare ingress. A directly reachable HTTP gateway allows a malicious client to forge forwarding headers.

Recommended deployment:

- Tunnel: keep the gateway private/loopback or private-network reachable from cloudflared.
- Orange: keep the gateway behind the reverse proxy and restrict the public origin to Cloudflare where operationally possible; Authenticated Origin Pulls is an optional infrastructure hardening layer.
- Minecraft-side PROXY parser: trust only the integrated/local MCflare gateway by default.

Multi-tenant hosts should review loopback trust carefully if untrusted tenants share a network namespace.

## Paper / Purpur / proxy stacks

Paper and Purpur expose native `proxies.proxy-protocol` handling in their global configuration. MCflare's Paper plugin therefore does not patch Minecraft networking or parse PROXY itself: it starts the shared gateway, which emits standard PROXY v1, and the server platform restores the address. The same final plugin SHA passed TCP4 and TCP6 WSS->PROXY Status on Paper and Purpur 1.21.11, 26.1.2 and 26.2. Velocity likewise has native HAProxy protocol support. Prefer platform-native PROXY handling wherever available.

## Proven tests

### Local synthetic Cloudflare proof

Fabric 26.2 server at `127.0.0.1:25585`, MCflare gateway at `127.0.0.1:25587`. A synthetic WSS request included `CF-Connecting-IP: 198.51.100.42`; MCflare emitted PROXY v1 and a real Minecraft Status request/response succeeded. The same parser/lifecycle was subsequently runtime-proven on Fabric 26.1 plus NeoForge 1.21.11, 26.1 and 26.2, including synthetic TCP6.

### Live Cloudflare proof

On 2026-09-01 both `mcflare-orange-test.mulearnscet.in/mcflare` and `mcflare2-test.mulearnscet.in/mcflare` were routed to the integrated Fabric gateway. Initial Status probes returned the distinct dev-server response and logged `realIpPresent=true` plus `cfRayPresent=true`.

The stronger acceptance then used the actual Fabric 26.1 Minecraft client under an isolated ARM64 Oracle Docker/Xvfb/Mesa-llvmpipe environment. Quick Play joined the isolated world through true Orange and then through the named Tunnel. The server logged `Player357[/144.24.114.90:60826] logged in` for Orange and `Player977[/144.24.114.90:49428] logged in` for Tunnel; `144.24.114.90` independently matched the Oracle client host public IPv4. A later true-Orange run joined as `Player393[/144.24.114.90:53422]`; issuing Minecraft's native `ban-ip 144.24.114.90` immediately disconnected that player, and a fresh real-client attempt was rejected as `Player44 (/144.24.114.90:42538)` with `Your IP address is banned from this server.` The test IP was then pardoned. This proves the restored address reaches Minecraft's native enforcement path, not only Status/logging. The server was intentionally `online-mode=false`, so this does not claim Mojang session-authentication coverage.

## References

- Cloudflare HTTP headers: https://developers.cloudflare.com/fundamentals/reference/http-headers/
- HAProxy PROXY protocol specification: https://github.com/haproxy/haproxy/blob/master/doc/proxy-protocol.txt
- Paper PROXY protocol setting: https://docs.papermc.io/paper/reference/global-configuration/
