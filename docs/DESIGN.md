# MCflare design

## Rules

1. The player installs one Minecraft mod. No separate Cloudflare client process.
2. The admin publishes one hostname. Discovery must not require TXT/SRV metadata beyond normal Minecraft DNS.
3. Normal Minecraft servers must keep working.
4. The transport core must not depend on Minecraft or a mod loader.
5. Do not parse Minecraft play packets. MCflare carries bytes; Minecraft owns its protocol.
6. Add adapters only for mods that open a separate network socket.
7. Prefer a small recoverable failure over a complex transparent-resume protocol.

## Components

`core` is Java 8 and contains only protocol/transport code:

- RFC6455 client
- WebSocket byte-stream adapter
- Minecraft status request/probe
- loopback WSS carrier
- MCF1 constants/control/datagram clients

The Fabric module only hooks Minecraft connection creation/lifecycle. Future Forge/NeoForge/legacy adapters reuse `core`.

`gateway` exists only for Enhanced mode. It terminates HTTP/WebSocket once, then directly proxies Minecraft or a configured MCF1 side service. There is no second local proxy process.

## Discovery

The logical hostname the player entered is kept separate from Minecraft's DNS/SRV-resolved socket address.

- WSS discovery probes the logical hostname.
- Normal TCP uses Minecraft's resolved/SRV destination.
- Positive discoveries are cached longer than misses.
- When both direct TCP and MCflare are available, MCflare gets a short preference window so an accidentally exposed origin is not chosen merely because TCP connected first.

The probe sends the current client's real Minecraft protocol version when the adapter can provide it. The core can fall back to an unknown version for loader-independent tooling.

## Enhanced protocol

Every Enhanced side-service connection begins with:

```text
MCF1 | version | opcode
```

Supported v1 operations are `HELLO`, `OPEN_STREAM`, and `OPEN_DATAGRAM`. Service IDs are configured at the gateway; clients never select arbitrary backend addresses. This prevents MCflare from becoming an open proxy.

Datagrams are length-framed because WebSocket frame boundaries are not application-record boundaries. The v1 maximum datagram is 8192 bytes.

## Known constraints

- Cloudflare can terminate long-lived WebSockets during edge/software restarts. MCflare sends heartbeats, but v1 does not attempt transparent Minecraft-session replay.
- Basic `tcp://` mode does not preserve the player's source IP at the Minecraft origin.
- Enhanced HTTP mode receives Cloudflare connection headers at the gateway, but server/proxy adapters are still needed before plugins can safely consume that identity.
- WSS datagrams work, but TCP head-of-line blocking is undesirable for realtime voice under packet loss. Treat them as a compatibility fallback, not the final preferred voice transport.
- Direct Connect to an uncached ordinary server may pay a small secure-discovery grace. Multiplayer-list discovery should normally warm the cache first.
- Numeric IP server entries are deliberately not auto-probed; MCflare is hostname/TLS based.

## Deliberate non-goals for v1

- transparent session resume after a broken Cloudflare WebSocket
- arbitrary client-selected TCP/UDP destinations
- packet-level understanding of arbitrary Minecraft mods
- a second background executable on player machines
- auto-editing DNS or requiring an MCflare TXT record

These can be revisited only if measured user problems justify the complexity.
