# How MCflare Works

MCflare does not translate Minecraft packets. It changes how the existing Minecraft TCP stream is carried between the player and server edge.

```text
Minecraft client
      ↓
MCflare client hook
      ↓ WSS /mcflare, subprotocol mcflare.v1
Cloudflare
      ↓ HTTP/WebSocket
MCflare gateway
      ↓ normal Minecraft TCP (optionally PROXY v1 first)
Minecraft server
```

## On the player

MCflare preserves the hostname the player entered. For a protected MCflare host, it opens the `/mcflare` WebSocket internally and gives Minecraft a local carrier that behaves like its network stream.

For an ordinary non-MCflare host, Minecraft continues with ordinary direct TCP.

## At Cloudflare

Cloudflare terminates the outer TLS/WebSocket connection. Orange Cloud and named Tunnel are two ways of delivering the same HTTP/WebSocket request to the gateway.

## At the gateway

The gateway validates the WebSocket request and subprotocol, opens the configured Minecraft backend lazily when application data arrives, and copies bytes in both directions.

If real-IP forwarding is enabled, trusted Cloudflare visitor metadata is converted to a PROXY-v1 line before the Minecraft stream.

## At the Minecraft server

Minecraft receives its normal protocol stream. Login, encryption, compression, configuration, plugin/custom payloads, and gameplay remain Minecraft protocol behavior.

For the exact interoperability contract, see [`docs/V1_PROTOCOL.md`](https://github.com/Phloraxx/mcflare/blob/main/docs/V1_PROTOCOL.md). For design/trust boundaries, see [`docs/V1_ARCHITECTURE.md`](https://github.com/Phloraxx/mcflare/blob/main/docs/V1_ARCHITECTURE.md).
