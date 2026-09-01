# Real Player IP Preservation

Status: v1 core requirement; locally and live-path validated on Fabric 26.2.

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

## Source-port interoperability finding

Cloudflare exposes the real visitor IP in HTTP headers but does not expose the original TCP source port to this origin application. The HAProxy text specification permits port 0, but Netty 4.2.15's HAProxy decoder rejected a v1 header using source port 0 in our real 26.2 integration test.

For interoperability, MCflare uses the non-zero TCP source port of the ingress connection arriving at the gateway as an opaque connection port. The IP is the identity that Minecraft moderation and logging systems materially use; MCflare must not claim that this substituted port is the player's original Internet source port.

## Fabric implementation

The dedicated-server artifact wraps Minecraft's existing `ServerConnectionListener` channel initializer. For loopback connections it temporarily installs Netty's `HAProxyMessageDecoder`. If a PROXY header is present, the standard decoded source address is applied to Minecraft's `Connection.address`, then the normal Minecraft pipeline continues.

If no PROXY header is detected, normal Minecraft bytes continue untouched. Remote direct clients do not receive the trusted local PROXY treatment.

The server adapter uses Netty's standard decoder rather than parsing HAProxy text manually.

## Trust model

`CF-Connecting-IP` is trustworthy only when requests genuinely arrive through trusted Cloudflare ingress. A directly reachable HTTP gateway allows a malicious client to forge forwarding headers.

Recommended deployment:

- Tunnel: keep the gateway private/loopback or private-network reachable from cloudflared.
- Orange: keep the gateway behind the reverse proxy and restrict the public origin to Cloudflare where operationally possible; Authenticated Origin Pulls is an optional infrastructure hardening layer.
- Minecraft-side PROXY decoder: trust only the integrated/local MCflare gateway by default.

Multi-tenant hosts should review loopback trust carefully if untrusted tenants share a network namespace.

## Paper / proxy stacks

Paper already exposes native `proxy-protocol` handling in its global configuration. Velocity also has HAProxy protocol support. Where a platform natively supports PROXY protocol, prefer that standard implementation rather than a platform-specific MCflare IP-forwarding format.

## Proven tests

### Local synthetic Cloudflare proof

Fabric 26.2 server at `127.0.0.1:25585`, MCflare gateway at `127.0.0.1:25587`. A synthetic WSS request included `CF-Connecting-IP: 198.51.100.42`; MCflare emitted PROXY v1 and a real Minecraft Status request/response succeeded.

### Live Cloudflare proof

On 2026-09-01 both `mcflare-orange-test.mulearnscet.in/mcflare` and `mcflare2-test.mulearnscet.in/mcflare` were routed to the integrated Fabric gateway. Both returned a distinct dev-server Status response and the gateway logged `realIpPresent=true` plus `cfRayPresent=true`. This proves Cloudflare metadata, MCflare PROXY emission, server decoding, and Minecraft byte forwarding coexist on both delivery modes.

## References

- Cloudflare HTTP headers: https://developers.cloudflare.com/fundamentals/reference/http-headers/
- HAProxy PROXY protocol specification: https://github.com/haproxy/haproxy/blob/master/doc/proxy-protocol.txt
- Paper PROXY protocol setting: https://docs.papermc.io/paper/reference/global-configuration/
