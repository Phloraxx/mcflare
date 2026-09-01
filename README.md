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

The Fabric 26.2 server adapter includes a minimal trusted same-machine PROXY decoder. Paper/other proxy stacks should use their native PROXY-protocol support when available.

## Same Fabric JAR on client and server

The Fabric artifact is loaded in both physical environments. Client connection hooks remain client-only; server gateway/PROXY hooks remain dedicated-server-only. Different artifacts are needed for genuinely different loaders/version hooks, not because client and server require separate downloads.

## Scope

MCflare transports **only Minecraft's own Java connection**.

Packets already inside that connection, including mod/plugin custom payloads, are transparent. Separate sockets opened by mods remain outside MCflare. Voice chat UDP, web maps, generic TCP/UDP services, VPN behavior and arbitrary side channels are intentionally not part of v1.

## Modules

- `core/` — Java-8-compatible RFC6455 client, route resolver and loopback carrier.
- `gateway/` — Java-8-compatible HTTP/WebSocket-to-Minecraft gateway plus PROXY-v1 encoder.
- root Fabric module — current Minecraft 26.2 client/server adapter.

## Build

Minecraft 26.2 requires the configured Java 25 toolchain:

```bash
./gradlew --no-daemon clean build
```

## Engineering docs

Start with:

- `docs/V1_ARCHITECTURE.md`
- `docs/V1_PROTOCOL.md`
- `docs/REAL_IP.md`
- `docs/DEPLOYMENT.md`
- `docs/COMPATIBILITY.md`
- `docs/STANDARDS_AUDIT.md`
- `docs/IMPLEMENTATION_PLAN.md`
- `docs/TEST_EVIDENCE_2026-09-01.md`

`docs/PROJECT_KNOWLEDGE.md` retains superseded experiments as historical engineering evidence; the files above define the current product architecture.

## Status

Experimental. Fabric 26.2 transport, dual-side server loading, real-IP PROXY handoff, true Orange `/mcflare`, and named HTTP Tunnel `/mcflare` have passed Status-level integration tests. Full online-mode gameplay and broader loader/version gates remain before a stable v1 release.

## Attribution

MCflare retains the MIT license and required attribution for code derived from the original Modflared project. See `NOTICE.md`.
