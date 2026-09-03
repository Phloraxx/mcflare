# MCflare Concepts

This page explains the vocabulary used throughout the documentation without requiring knowledge of the implementation.

## The core idea

MCflare is a transparent bridge between a normal Minecraft Java TCP connection and a standard WebSocket path that Cloudflare can proxy.

```text
Minecraft TCP bytes
      ↓
MCflare client transport
      ↓
WebSocket /mcflare
      ↓
Cloudflare
      ↓
MCflare gateway
      ↓
Minecraft TCP bytes
```

The bytes inside are still Minecraft's bytes. MCflare does not define a parallel gameplay protocol.

## Client adapter

The **client adapter** is the small loader/version-specific layer installed into Minecraft. It intercepts connection establishment and server-list status connections when a host needs MCflare.

Its goal is to keep the player experience unchanged: the player types the ordinary Minecraft hostname and Minecraft still sees a normal local TCP connection.

## Loopback carrier

The **loopback carrier** is a short-lived local TCP listener on the player's machine. Minecraft connects to it as though it were an ordinary server socket; MCflare copies that byte stream into the remote WebSocket.

This extra local hop keeps the version-specific Minecraft integration small and avoids replacing Minecraft's entire Netty transport.

## `/mcflare`

`/mcflare` is the one HTTP/WebSocket endpoint exposed by a v1 gateway.

Conceptually:

```text
wss://play.example.com/mcflare
```

Reverse proxies and named Tunnels route that path to the local MCflare HTTP listener.

## `mcflare.v1`

`mcflare.v1` is the exact, case-sensitive WebSocket subprotocol token negotiated during the HTTP Upgrade:

```text
Sec-WebSocket-Protocol: mcflare.v1
```

It tells the client and gateway that both sides expect the MCflare v1 byte-stream contract. It is not a Minecraft version or Cloudflare configuration mode.

See [V1_PROTOCOL.md](V1_PROTOCOL.md) for the normative wire behavior.

## MCflare gateway

The **gateway** accepts `/mcflare`, validates the WebSocket handshake, and opens one configured Minecraft backend connection when application data begins.

It then copies data in both directions:

```text
WebSocket binary payload ↔ Minecraft TCP stream
```

One gateway instance maps to one Minecraft backend. Generic hostname routing belongs to existing infrastructure such as Traefik, Caddy, NGINX, or `cloudflared`.

## Orange proxy

**Orange proxy** means the Minecraft hostname is using Cloudflare's ordinary proxied HTTPS/WebSocket path.

Typical flow:

```text
player → Cloudflare → HTTPS reverse proxy → MCflare gateway → Minecraft
```

The administrator is responsible for protecting the origin and making sure forwarding headers can only arrive through trusted infrastructure.

## Named Tunnel

A **named Tunnel** means `cloudflared` runs on server infrastructure and provides the origin path to Cloudflare.

Typical flow:

```text
player → Cloudflare → named Tunnel → cloudflared → MCflare gateway → Minecraft
```

Tunnel lifecycle and credentials belong to server infrastructure, not the MCflare client or wire protocol.

## Deployment neutrality

Orange proxy and named Tunnel are different ways to deliver the same request. The player does not select a mode and the mod does not carry Tunnel IDs/tokens.

Both expose the same:

```text
wss://<host>/mcflare
Sec-WebSocket-Protocol: mcflare.v1
```

## Route discovery and positive pins

For a hostname that has not been seen before, MCflare can determine whether the protected WSS path is available while preserving compatibility with ordinary Minecraft servers.

A **positive pin** is created only after a host successfully proves MCflare. That positive trust is persisted in:

```text
~/.mcflare/known-hosts-v1.txt
```

After that, the client requires WSS and fails closed rather than silently using raw TCP. Negative/ordinary discovery knowledge is deliberately less trusted and remains short-lived.

## PROXY protocol v1

Cloudflare terminates the public WebSocket, so the backend TCP socket cannot infer the player's original address from the TCP peer.

MCflare can turn trusted Cloudflare visitor metadata into a standard HAProxy **PROXY protocol v1** line before the normal Minecraft bytes. The Minecraft-side integration then restores the remote address.

This feature is separate from `mcflare.v1`: it is a backend handoff between the gateway and Minecraft. See [REAL_IP.md](REAL_IP.md).

## Real-IP trust boundary

A header such as `CF-Connecting-IP` is not inherently trustworthy just because it has that name. MCflare should only treat it as visitor metadata when the gateway is reached through infrastructure controlled by Cloudflare/trusted proxies.

Do not expose a forwarding-header-trusting gateway directly to arbitrary Internet clients.

## Direct / ordinary Minecraft

An **ordinary server** is a hostname that does not use MCflare. The client leaves it on Minecraft's standard TCP path.

This compatibility is intentional: installing MCflare should not turn every Minecraft connection into a proxy connection.

## Fail closed

**Fail closed** means a host already proven to require MCflare will fail to connect if its protected WSS path is unavailable instead of silently trying the raw origin.

This protects against accidental origin exposure becoming an automatic downgrade.

## Connection lifecycle

One MCflare WebSocket corresponds to one Minecraft connection. If either side closes, the gateway tears down the paired connection and releases its capacity slot.

MCflare does not transparently resume a broken game session on a new WebSocket because Minecraft's protocol state belonged to the old connection.

## Loader adapter versus transport core

MCflare is intentionally split into:

- a **loader-independent core/gateway**, which understands WebSockets, route state, lifecycle, and PROXY v1;
- thin **Fabric/Quilt/NeoForge/Paper/Purpur adapters**, which integrate that transport with Minecraft/server lifecycle APIs.

This is why the wire protocol can stay stable while loader artifacts still need version-specific builds.

## What is outside the boundary?

MCflare v1 does not transport separate sockets such as voice UDP, web maps, telemetry, or arbitrary services. It does not manage DNS, certificates, Cloudflare accounts, or Tunnel credentials.

For the complete architecture and non-goals, read [V1_ARCHITECTURE.md](V1_ARCHITECTURE.md).
