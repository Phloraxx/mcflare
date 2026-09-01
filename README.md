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

The shared Fabric/NeoForge server adapter includes a minimal loopback-trusted PROXY-v1 parser. The same Minecraft adapter source is runtime-proven on Fabric and NeoForge 1.21.11, 26.1 and 26.2. MCflare no longer bundles Netty's HAProxy codec; the standard text header is parsed by a bounded in-project parser. Paper/other proxy stacks should use their native PROXY-protocol support when available.

## Same loader JAR on client and server

Each Fabric or NeoForge release artifact is intended for both physical environments. Client connection hooks remain client-only; server gateway/PROXY hooks remain dedicated-server-only. For both loaders, current testing collapses 26.1 and 26.2 into one binary JAR; 1.21.11 remains a separate Java-21/toolchain artifact. Different artifacts are needed only at genuine loader/runtime boundaries, not because client and server require separate downloads.

## Scope

MCflare transports **only Minecraft's own Java connection**.

Packets already inside that connection, including mod/plugin custom payloads, are transparent. Separate sockets opened by mods remain outside MCflare. Voice chat UDP, web maps, generic TCP/UDP services, VPN behavior and arbitrary side channels are intentionally not part of v1.

## Modules

- `core/` — Java-8-compatible RFC6455 client, route resolver and loopback carrier.
- `gateway/` — Java-8-compatible HTTP/WebSocket-to-Minecraft gateway plus standard PROXY-v1 codec.
- root Fabric module — packages the shared Minecraft adapter for Fabric.
- `neoforge/` — packages the same shared Minecraft adapter for NeoForge; loader-specific Java is only a tiny `@Mod` marker.

## Build

The repository defaults to the combined 26.1-26.2 family and requires Java 25 for current-generation loader artifacts. Use loader-qualified tasks in this multi-project build.

Fabric:

```bash
./gradlew --no-daemon :core:build :gateway:build :build
```

NeoForge:

```bash
./gradlew --no-daemon :core:build :gateway:build :neoforge:build
```

The same shared source also builds the Java-21 1.21.11 Fabric and NeoForge artifacts through the CI/build matrix. See `docs/BUILD_MATRIX.md`. Avoid unqualified `runServer`; use `:runServer` for Fabric or `:neoforge:runServer` for NeoForge.

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

Experimental. One loader-neutral Minecraft adapter source is now runtime-proven on both Fabric and NeoForge 1.21.11, 26.1 and 26.2. Each loader needs only two tested release families: 1.21.11 and a combined 26.1-26.2 binary. Real-IP PROXY handoff, true Orange `/mcflare`, and named HTTP Tunnel `/mcflare` have passed Status-level integration tests. Full authenticated player gameplay remains before stable v1.

## Attribution

MCflare retains the MIT license and required attribution for code derived from the original Modflared project. See `NOTICE.md`.
