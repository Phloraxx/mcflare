# MCflare Low-Latency Architecture

## Objective

Make MCflare processing overhead negligible and avoid redundant handshakes. The dominant remaining latency can be Cloudflare/ISP routing, which MCflare cannot fix by adding protocol layers.

## Minimal data path

```text
Minecraft
 -> localhost carrier
 -> one TCP/TLS/WebSocket establishment
 -> Cloudflare
 -> one HTTP/WebSocket origin hop
 -> gateway
 -> Minecraft TCP
```

The discovery WebSocket is reused for gameplay. There is no second WSS establishment, JSON control exchange, base64 encoding, WebSocket compression, packet translation, or side-service multiplexer.

## Orange versus Tunnel

Both use the same WSS stream. Orange lets Cloudflare connect to an administrator-controlled HTTPS/reverse-proxy origin. Tunnel lets external cloudflared make outbound connections and deliver the same HTTP/WebSocket request to the gateway.

Measured on 2026-08-31 from the original test network, dedicated true Orange had lower median gameplay RTT than the named Tunnel but both remained substantially slower than direct Oracle; Cloudflare routing was the dominant extra cost. Those measurements must be interpreted with the handoff's routing caveats, including an active Tailscale exit-node confounder discovered later.

## Address selection

Keep the current resolved-address racing. It was added after one Cloudflare Anycast address timed out while another succeeded. Do not hard-code Cloudflare IP ranges or prefer IPv4/IPv6 based on assumptions.

## Keepalive

Use standard WebSocket Ping/Pong. Cloudflare recommends keepalive for long-lived WebSockets and may terminate established connections during edge software restarts. MCflare v1 treats that as a normal Minecraft disconnect/reconnect event.

## Local carrier

The localhost hop is intentionally retained because it makes client adapters much thinner. Removing it would move complexity into Minecraft's Netty internals for negligible WAN-latency benefit.

## Current proof

On 2026-09-01 both true Orange and a named HTTP Tunnel successfully carried `/mcflare` to the same integrated Fabric 26.2 server gateway, including Cloudflare visitor metadata -> PROXY v1 -> Minecraft Status. See `TEST_EVIDENCE_2026-09-01.md`.
