# MCflare

MCflare carries the normal Minecraft Java TCP byte stream over a standard secure WebSocket so a Minecraft hostname can use Cloudflare's ordinary HTTP/WebSocket infrastructure without requiring `cloudflared`, WARP, a VPN, or a custom launcher on the player's computer.

## Player experience

```text
Install the MCflare artifact for your Minecraft loader/version
-> add play.example.com normally
-> Join Server
```

MCflare stays transparent for ordinary Minecraft servers.

## v1 architecture

```text
Minecraft client
  -> MCflare client adapter
  -> WSS /mcflare (`mcflare.v1`)
  -> Cloudflare
       -> Orange -> reverse proxy ----┐
       -> Tunnel -> cloudflared ------┤
                                      v
                              MCflare gateway
                                      |
                         optional PROXY protocol v1
                                      |
                              Minecraft TCP server
```

Orange and Tunnel use the exact same MCflare code and wire protocol. They differ only in how Cloudflare reaches the local HTTP/WebSocket gateway.

- **Orange:** the administrator supplies normal HTTPS ingress/reverse proxying to the MCflare listener.
- **Tunnel:** the administrator runs `cloudflared` externally and maps the hostname to the same MCflare HTTP listener.

MCflare itself has no Tunnel token, Cloudflare API token, DNS client, cloudflared child process, certificate manager, or Orange/Tunnel mode.

## Wire protocol

The client opens:

```text
wss://play.example.com/mcflare
Sec-WebSocket-Protocol: mcflare.v1
```

A compatible gateway returns HTTP 101 and echoes `mcflare.v1`. That successful WebSocket is immediately retained as the gameplay carrier. Binary WebSocket payloads are the ordered Minecraft TCP byte stream; there is no MCflare gameplay framing, packet parser, HELLO, JSON capability protocol, compression, multiplexer, or UDP layer.

## Real player IP

Cloudflare supplies the visitor address to the HTTP/WebSocket origin in `CF-Connecting-IP` (and, when relevant, `CF-Connecting-IPv6`). MCflare can translate that standard HTTP metadata into standard HAProxy PROXY protocol v1 before the Minecraft stream.

The Fabric server adapter includes a minimal loopback-trusted PROXY decoder. The same source is proven on Fabric 1.21.11, 26.1 and 26.2. Paper/other proxy stacks should use their native PROXY-protocol support when available.

## Same Fabric JAR on client and server

Each Fabric release artifact is loaded in both physical environments. Client connection hooks remain client-only; server gateway/PROXY hooks remain dedicated-server-only. Current testing collapses 26.1 and 26.2 into one binary JAR; 1.21.11 remains a separate remapped/Java-21 artifact. Different artifacts are needed only at genuine runtime/loader boundaries, not because client and server require separate downloads.

## Scope

MCflare transports **only Minecraft's own Java connection**.

Packets already inside that connection, including mod/plugin custom payloads, are transparent. Separate sockets opened by mods remain outside MCflare. Voice chat UDP, web maps, generic TCP/UDP services, VPN behavior and arbitrary side channels are intentionally not part of v1.

## Modules

- `core/` — Java-8-compatible RFC6455 client, route resolver and loopback carrier.
- `gateway/` — Java-8-compatible HTTP/WebSocket-to-Minecraft gateway plus PROXY-v1 encoder.
- root Fabric module — one parameterized client/server adapter source tree for Fabric 1.21.11 and 26.x.

## Build

The default build produces the combined Fabric 26.1-26.2 artifact and requires Java 25:

```bash
./gradlew --no-daemon clean build
```

The same source also builds the Java-21/remapped 1.21.11 artifact through the CI/build matrix. See `docs/BUILD_MATRIX.md`.

## Engineering docs

Start with:

- `docs/V1_ARCHITECTURE.md`
- `docs/V1_PROTOCOL.md`
- `docs/REAL_IP.md`
- `docs/DEPLOYMENT.md`
- `docs/COMPATIBILITY.md`
- `docs/BUILD_MATRIX.md`
- `docs/STANDARDS_AUDIT.md`
- `docs/IMPLEMENTATION_PLAN.md`
- `docs/TEST_EVIDENCE_2026-09-01.md`

`docs/PROJECT_KNOWLEDGE.md` retains superseded experiments as historical engineering evidence; the files above define the current product architecture.

## Status

Experimental. Fabric 1.21.11, 26.1 and 26.2 server adapters now build from one source tree; the actual 1.21.11 release JAR passed standalone runtime testing, and one combined 26.1-26.2 JAR passed unchanged on both server versions. Real-IP PROXY handoff, true Orange `/mcflare`, and named HTTP Tunnel `/mcflare` have passed Status-level integration tests. Full online-mode gameplay and additional loader gates remain before stable v1.

## Attribution

MCflare retains the MIT license and required attribution for code derived from the original Modflared project. See `NOTICE.md`.
