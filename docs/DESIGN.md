# MCflare Design

Current architecture is defined by `V1_ARCHITECTURE.md` and `V1_PROTOCOL.md`.

## Product boundary

MCflare carries one ordered Minecraft Java TCP stream over RFC6455 WebSocket. It does not interpret Minecraft packets and does not carry separate mod sockets.

## Core invariants

1. `/mcflare` plus WebSocket subprotocol `mcflare.v1` is the v1 public protocol.
2. The successful discovery WebSocket is the gameplay carrier; do not reconnect just to start gameplay.
3. Orange and Tunnel are external ingress alternatives, never protocol modes.
4. The gateway is one configured Minecraft backend, not a generic service router.
5. Player IP uses Cloudflare visitor headers -> HAProxy PROXY v1, not an MCflare identity packet.
6. Minecraft encryption/authentication/compression remain Minecraft's responsibility.
7. WebSocket Ping/Pong/Close remain WebSocket's responsibility.
8. TLS/ACME, DNS, Cloudflare API, cloudflared and Tunnel credentials stay outside MCflare.
9. Keep the loopback carrier unless profiling proves it is a material bottleneck.
10. Claim loader/version compatibility only after real build + direct/protected connection regression.

## Current path

```text
Minecraft -> loopback carrier -> WSS /mcflare -> Cloudflare
  -> (Orange reverse proxy OR HTTP Tunnel)
  -> MCflare gateway -> optional PROXY v1 -> Minecraft TCP
```

## Multi-instance rule

One public hostname per Minecraft instance. External ingress maps that hostname to the corresponding MCflare listener. MCflare itself does not duplicate hostname routing.

## Failure semantics

A positive `101 + mcflare.v1` selects protected transport for that attempt. Carrier loss ends the current Minecraft session normally. There is no replay/resume layer. Ordinary server discovery remains available for hosts that are not MCflare.

## Security

Trust Cloudflare forwarding headers only behind trusted Cloudflare ingress. Trust PROXY headers only from configured/local gateway sources. Do not expose interactive browser challenges on `/mcflare`. Keep gateway resource limits.
