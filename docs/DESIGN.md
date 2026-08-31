# MCflare Design

## Product boundary

MCflare transports one thing: the normal Minecraft Java TCP byte stream. Traffic already carried inside that connection is transparent to MCflare. Separate sockets opened by mods are intentionally out of scope.

## Target data path

```text
Minecraft -> loopback carrier -> WSS :443 -> Cloudflare -> MCflare Gateway -> Minecraft TCP
```

Normal Cloudflare orange-cloud proxying is the preferred deployment. Tunnel is not part of MCflare code; it is only an optional external origin transport for environments that cannot accept inbound HTTPS.

## Why the loopback carrier stays

Minecraft still sees a normal TCP socket. That keeps loader/version adapters thin and avoids replacing Minecraft's Netty transport across many versions. The local hop is negligible compared with WAN latency.

## Gateway

The gateway accepts only `/.well-known/mcflare`, terminates WebSocket, and copies bytes to/from one configured Minecraft TCP backend. It does not multiplex services, parse Minecraft packets, proxy arbitrary destinations, or provide UDP.

## Security invariants

- Once a server is classified as MCflare, carrier failure is fatal; never fall back to a potentially exposed direct origin.
- Keep Minecraft TCP closed to the public Internet where possible.
- With orange-cloud deployment, restrict the gateway origin to Cloudflare/reverse-proxy traffic.
- Connection count and handshake/frame bounds remain enforced.

## Discovery and first connection

MCflare requests `Sec-WebSocket-Protocol: mcflare.v1`. A gateway must echo that subprotocol in its `101` response. That successful WebSocket is immediately retained as the actual Minecraft carrier, so discovery and transport establishment are one operation. Cached positive routes still fail closed if a future MCflare WebSocket cannot be established.
