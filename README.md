# MCflare

MCflare lets Minecraft clients reach origin-hidden servers through Cloudflare using one client mod and a normal server hostname.

**Player goal:** install MCflare once, enter `play.example.com`, join normally.

**Admin goal:** publish one Cloudflare Tunnel hostname. No TXT discovery record, player-side `cloudflared`, WARP, localhost command, or custom launcher is required.

> Status: experimental. Fabric 26.2 is proven end-to-end. The transport core and Enhanced gateway compile to Java 8 for reuse by legacy and modern loader adapters.

## Modes

| | Basic | Enhanced |
|---|---|---|
| Tunnel origin | `tcp://minecraft:25565` | `http://mcflare:25577` |
| Server gateway | none | one MCflare process |
| Minecraft | yes | yes |
| Separate TCP/UDP mod services | no | yes |
| Cloudflare source-IP header at gateway | no | yes |
| Player experience | identical | identical |

Basic is the minimum setup and is experimentally compatible with Cloudflare TCP-over-WebSocket behavior. Enhanced uses the normal documented HTTP/WebSocket path and is the recommended production direction when the server needs voice/side services, connection policy, source-IP-aware gateway controls, or a more explicit protocol boundary.

## Basic setup

```yaml
ingress:
  - hostname: play.example.com
    service: tcp://127.0.0.1:25565
  - service: http_status:404
```

The client opens `wss://play.example.com/.well-known/mcflare` and sends a real Minecraft status handshake. If a valid Minecraft status response comes back, MCflare uses WSS for the game stream. Otherwise Minecraft uses its normal TCP/SRV route.

No special DNS metadata is required.

## Enhanced setup

Run the gateway locally:

```bash
java -jar mcflare-gateway.jar \
  127.0.0.1:25577 \
  127.0.0.1:25565 \
  voicechat=udp://127.0.0.1:24454
```

Then publish it as HTTP:

```yaml
ingress:
  - hostname: play.example.com
    service: http://127.0.0.1:25577
  - service: http_status:404
```

Enhanced mode terminates WebSocket locally, then dispatches the byte stream:

```text
Minecraft bytes -> Minecraft backend
MCF1 + HELLO -> capability response
MCF1 + OPEN_STREAM -> configured TCP service
MCF1 + OPEN_DATAGRAM -> configured UDP service
```

The gateway is intentionally one process. It also sees Cloudflare HTTP metadata such as the presence of `CF-Connecting-IP` and `CF-Ray`; the origin should remain private so those headers cannot be spoofed by direct Internet clients.

## Compatibility model

Most mods need no MCflare integration because their packets already travel inside Minecraft's connection. Only mods that create separate sockets need an adapter.

- `core/` — dependency-free Java 8 RFC6455 carrier, discovery, and Enhanced service protocol.
- `gateway/` — one Java 8 HTTP/WebSocket gateway for Minecraft plus optional stream/datagram services.
- root module — current Fabric 26.2 connection hooks.
- future loader adapters — thin hooks that reuse `core/`; they should not reimplement transport.

Simple Voice Chat 2.6.22+26.2 is the first proven side-service adapter. MCflare uses SVC's official client-socket API: ordinary servers keep stock UDP, while an MCflare-protected server uses the Enhanced `voicechat` datagram service and fails closed if that service is absent. WSS datagrams are the proven compatibility path; native realtime UDP/TURN remains a future candidate pending real loss/jitter comparisons.

## Build

```bash
./gradlew clean build
```

Core protocol tests run as part of `build`. CI performs the same clean build on every push and pull request.

Start with `docs/PROJECT_KNOWLEDGE.md` for the canonical engineering record. `docs/DESIGN.md` is the concise architecture summary and `docs/TEST_MATRIX.md` tracks proof gates.

## Attribution

MCflare retains the MIT license and attribution for code derived from the original Modflared project. See `NOTICE.md`.
