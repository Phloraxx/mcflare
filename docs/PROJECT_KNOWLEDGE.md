> **HISTORICAL RESEARCH ARCHIVE:** This long-form file preserves experiments, rejected designs, and the reasoning that led to MCflare v1. It is intentionally not an onboarding guide and should not be read as current behavior where it mentions Tunnel-basic, MCF1, voice/datagram services, `/.well-known/mcflare`, Fabric-only support, or Netty HAProxy decoding. Current authority is [V1_PROTOCOL.md](V1_PROTOCOL.md) for wire behavior, [V1_ARCHITECTURE.md](V1_ARCHITECTURE.md) for components, and the current Installation/Deployment/Compatibility guides for operation.

# MCflare project knowledge

> Canonical engineering record. Updated 2026-08-30.
>
> This document records what is proven, what is inferred, what is deliberately excluded, and why. Update it whenever an architectural decision or test result changes.

## 1. Problem

MCflare lets a Minecraft client reach a server whose origin IP and Minecraft port are not publicly exposed. The public hostname terminates at Cloudflare; the server-side `cloudflared` connector reaches the private/local Minecraft service.

The target UX is intentionally small:

- Player: install one Minecraft mod, enter the normal server hostname, join.
- Admin: publish one hostname through Cloudflare Tunnel; no MCflare TXT record or player-specific configuration.
- Player machines must not install or launch `cloudflared`, WARP, or a separate proxy executable.
- Ordinary Minecraft servers must continue to work normally with MCflare installed.

The project is experimental. Fabric 26.2 is the only Minecraft adapter currently proven end-to-end.
## 2. Non-negotiable invariants

1. Once a hostname is positively identified as MCflare, connection failure is **fail closed**. Never fall back to direct TCP for that attempt.
2. Client discovery uses the logical hostname the player entered, not an SRV-resolved backend hostname.
3. Direct/ordinary Minecraft still uses Minecraft's normal DNS/SRV-resolved destination.
4. MCflare does not parse gameplay packets. The base transport carries an ordered byte stream.
5. Arbitrary side-service backend addresses are never client selectable; the gateway exposes only admin-configured service IDs.
6. The gateway is not an Internet-facing origin. Bind it to loopback/private networking and let `cloudflared` reach it.
7. `CF-Connecting-IP` or other proxy identity metadata is trusted only when the gateway is reachable exclusively through trusted Cloudflare infrastructure.
8. The shared transport core stays independent of Minecraft, Fabric, Forge, NeoForge, Netty, and external helper processes.
9. Add compatibility adapters only when software opens a network path outside Minecraft's own connection.
10. Do not add transparent session replay/resume until measured production failures justify its complexity.

These invariants take precedence over convenience features.
## 3. Architecture

### Basic mode

```text
Minecraft + MCflare
        |
        | WSS :443
        v
Cloudflare edge
        |
Cloudflare Tunnel
        |
tcp://127.0.0.1:25565
        |
Minecraft server
```

The server needs only `cloudflared`; there is no MCflare server component. This is the minimum-admin-setup mode.

Important qualification: Cloudflare documents TCP published applications as TCP streamed over WebSocket with `cloudflared access tcp` on the client. MCflare's dependency-free WebSocket carrier has been experimentally proven compatible with that wire behavior, but Cloudflare does not document a stable third-party custom-client protocol contract. Treat Basic mode as proven but more compatibility-sensitive than Enhanced mode.
### Enhanced mode

```text
Minecraft + MCflare
        |
        | standards-based WSS :443
        v
Cloudflare edge
        |
Cloudflare HTTP Tunnel
        |
http://127.0.0.1:25577
        |
MCflare Gateway
   |            |
Minecraft    side services
```

Enhanced mode terminates a normal HTTP/WebSocket upgrade at the gateway. The same public hostname carries Minecraft plus optional MCF1 service channels. It is the recommended production direction when source-IP-aware controls, voice, or other side services are required.

The original two-process Enhanced PoC (`HTTP edge -> raw gateway -> Minecraft`) was deliberately collapsed into one gateway process after tests proved the extra hop added no value.
## 4. Zero-config discovery

No TXT record, custom SRV metadata, Cloudflare IP-range guessing, or local configuration file is required.

For a player-entered hostname such as `play.example.com`, MCflare probes:

```text
wss://play.example.com/.well-known/mcflare
```

The probe sends a real Minecraft Status handshake and requires a parseable Minecraft Status response. A generic website merely supporting WebSockets is therefore not enough to be classified as MCflare.

The adapter supplies the actual Minecraft client protocol version when available. The loader-independent core supports an unknown-version fallback only for tooling/tests.

The path itself is not a secret or authentication mechanism. Its purpose is deterministic routing and protocol identification.
### DNS and SRV rule

Minecraft can transform the name a player typed into another host/port through `_minecraft._tcp` SRV resolution. MCflare must preserve both identities:

```text
logical host:   play.example.com
resolved socket: backend.example.net:25001
```

- WSS discovery always uses the logical host.
- Ordinary direct TCP uses Minecraft's resolved socket.

This prevents an SRV backend name from accidentally becoming the public MCflare identity while preserving vanilla SRV behavior for normal servers.

Numeric IPv4 and IPv6 literals are not auto-probed in v1 because TLS/SNI and the origin-hiding model are hostname-oriented.
### Discovery timing and cache

Current policy:

- Positive MCflare result: cache for 10 minutes.
- Negative/ordinary result: cache for 30 seconds.
- Duplicate in-flight probes for the same host/port are serialized.
- Direct TCP reachability timeout: 1.2 seconds.
- If direct TCP is reachable, MCflare gets a 1.5-second secure-preference grace before direct is selected.
- If direct TCP is not reachable, the WSS probe can use the full 4.5-second discovery timeout.

This intentionally prefers MCflare when both direct and protected paths exist, reducing the chance that an accidentally exposed origin wins purely because TCP connected first.

Server-list pings run on Minecraft's worker pool and normal joins run on the Server Connector thread in the proven 26.2 adapter, so discovery does not block the render/UI thread.
### Fail-closed state model

The client routing result has only two legal states:

```text
DIRECT   -> no MCflare carrier
MCFLARE  -> exactly one MCflare carrier
```

`TunnelStatus` enforces this invariant. The old inherited `FAILED_TO_DETERMINE` state was removed because it allowed a dangerous interpretation: protected server detected, carrier creation failed, then connect directly.

Current behavior is:

```text
not MCflare -> direct TCP may be used
MCflare detected -> secure carrier is mandatory
carrier/WSS failure -> connection fails and the positive cache is invalidated
```

There is no intentional direct fallback after positive MCflare classification.
## 5. Client transport core

`core/` is intentionally dependency-free Java 8 transport/protocol code. It contains:

- `Rfc6455Client` — TLS + RFC6455 client.
- `WebSocketByteStream` — treats fragmented WebSocket delivery as an ordered byte stream where needed.
- `MinecraftStatusProbe` — encodes/decodes discovery status traffic.
- `LoopbackCarrier` — local TCP listener that maps Minecraft's byte stream to WSS without a helper process.
- `GatewayProtocol` — shared MCF1 constants/limits.
- `GatewayControlClient` and `GatewayDatagramClient` — Enhanced side-service clients.

The current Minecraft adapter still uses an in-process loopback socket because it is simple and keeps Minecraft seeing ordinary TCP. A future direct Netty transport is not justified unless profiling proves the loopback hop matters.
### RFC6455/TLS hardening currently implemented

- TLS endpoint identification is enabled and SNI is set for DNS hostnames.
- TCP connect/TLS/WebSocket establishment is bounded; the session becomes unbounded-read only after a successful upgrade.
- Client frames are masked; masking is done in bulk rather than byte-at-a-time writes.
- Server frames are required to be unmasked.
- RSV bits, control-frame limits, continuation state, nested fragmentation, close/ping/pong, and 64-bit lengths are validated.
- Client heartbeat sends a WebSocket Ping about every 30 seconds during an active carrier.
- Local loopback sockets use `TCP_NODELAY` and keepalive.
- Expected local stream shutdown is separated from unexpected WSS failure so normal status-ping closure does not invalidate discovery cache.

The transport is a byte carrier; WebSocket frame boundaries are never treated as Minecraft packet boundaries.
## 6. Enhanced MCF1 protocol

Every side-service stream starts with:

```text
'M' 'C' 'F' '1' | version | opcode
```

Version 1 operations:

- `HELLO` — return gateway capabilities.
- `OPEN_STREAM` — connect to one admin-configured TCP service.
- `OPEN_DATAGRAM` — connect to one admin-configured UDP service.
- `ERROR` — protocol/service failure.

Service IDs are 1..64 UTF-8 bytes and restricted to ASCII letters, digits, `.`, `_`, and `-`. This keeps the protocol/config/JSON representation unambiguous.

The client never sends an IP address or backend port. `voicechat`, for example, maps to a target supplied only in the gateway's server-side configuration.
### Datagram records

WebSocket frame boundaries are not application record boundaries. This was discovered experimentally when Cloudflare split one MCF1 response across frames.

MCF1 datagrams therefore use explicit framing:

```text
u16 length | datagram bytes
```

The v1 maximum datagram is 8192 bytes. This is intentionally conservative: it is comfortably above normal compressed voice payloads while preventing oversized allocations and platform-dependent giant-UDP behavior.

The Cloudflare WSS path has been verified byte-perfect for datagrams of 1, 20, 256, 1200, 4096, and 8192 bytes.
## 7. Enhanced gateway security boundary

The gateway intentionally remains a small blocking/thread-per-connection Java 8 server instead of introducing a framework. Hardening is applied around that simple model:

- Default/recommended listener is loopback/private only.
- Maximum concurrent accepted connections: 256.
- The 257th connection is rejected immediately with HTTP 503; this behavior was adversarially tested.
- HTTP/WebSocket handshake timeout: 10 seconds.
- Maximum inbound WebSocket frame: 1 MiB.
- WebSocket client masking and upgrade headers are validated.
- `Sec-WebSocket-Key` must decode to exactly 16 bytes.
- Side services are allowlisted by server configuration.

These are resource/sanity limits, not a replacement for Cloudflare edge protections or Minecraft authentication.
### Source IP and trusted headers

Basic `tcp://` mode does not preserve the player's source IP at the Minecraft origin. The origin sees the proxied/Tunnel-side connection.

Enhanced HTTP/WSS mode experimentally received both `CF-Connecting-IP` and `CF-Ray` at the gateway, matching Cloudflare's HTTP proxy documentation.

Do not blindly inject `CF-Connecting-IP` into Minecraft as if the socket originated there. Safe future integrations include:

- MCflare server mod/plugin identity API.
- Velocity/Bungee trusted forwarding adapter.
- Paper/server plugin integration.
- Gateway-only rate limiting/audit controls.

A directly Internet-reachable gateway would let clients forge proxy headers. Therefore trusted source identity requires the gateway listener to remain private and reachable only through the controlled Tunnel path.
## 8. Version and loader strategy

“Universal” means one reusable network engine plus thin version/loader hooks; it does not mean claiming untested Minecraft versions.

`mcflare-core` compiles to Java 8 bytecode and was also executed successfully on a real Temurin 8u504 runtime against a live Cloudflare/Minecraft endpoint. That proves the transport runtime floor, not the loader hooks.

Current adapter status:

| Minecraft family | Loader | Status |
|---|---|---|
| 26.2 | Fabric | proven full login |
| 26.2 | Quilt | pending compatibility proof |
| 26.2 | NeoForge | pending |
| 26.2 | Forge | pending |
| 1.20.1 | Fabric/Forge | pending |
| 1.12.2 | Forge | planned first legacy proof |
| 1.8.9 | Forge | pending |

Support is claimed only after a real build plus status/login regression on that version/loader.
### Adapter rule

Version-specific code should do only what cannot live in `core`:

1. Capture the logical hostname/port before SRV loses that identity.
2. Intercept Minecraft's outbound connection creation.
3. Ask the shared manager whether the route is direct or MCflare.
4. Redirect to the in-process loopback carrier when MCflare is selected.
5. Attach carrier lifecycle cleanup to Minecraft's connection lifecycle.
6. Provide the current Minecraft protocol version to discovery.

The current Fabric 26.2 implementation requires only three mixins: join interception, server-list ping interception, and connection cleanup. Decorative status UI mixins inherited from the PoC were removed.
## 9. Mod compatibility model

Most Minecraft mods do **not** need MCflare-specific support. Anything already encoded inside the Minecraft connection is carried transparently, including normal packets, Fabric/Forge/NeoForge custom payloads, plugin messages, login handshakes, inventory/chunk/entity traffic, and server plugin channels.

Client-only graphics/UI/performance mods should be unrelated to MCflare unless they replace networking themselves.

Special handling is needed only when a mod/service opens another network socket:

- Separate TCP service -> MCF1 `OPEN_STREAM`.
- Separate UDP service -> MCF1 `OPEN_DATAGRAM` fallback or a future realtime transport.
- Prefer the mod's documented socket/add-on API.
- Avoid OS-wide packet interception and fragile mixins into another mod's internals.

Unknown arbitrary mod protocols are not automatically guessed. They either already ride Minecraft's connection or need an explicit adapter/service declaration.
### Simple Voice Chat

Simple Voice Chat uses a separate UDP path (default port 24454), so it does not automatically ride Minecraft TCP. Its 26.2 project exposes an official `ClientVoicechatSocket` replacement API, which MCflare now uses instead of packet interception or SVC mixins.

Proven Enhanced path:

```text
Simple Voice Chat client socket
 -> `McflareVoicechatSocket`
 -> MCF1 `OPEN_DATAGRAM voicechat`
 -> WSS
 -> Cloudflare
 -> MCflare Gateway
 -> UDP 127.0.0.1:24454
 -> Simple Voice Chat server
```

A real SVC 2.6.22+26.2 client/server test completed authentication and connection validation through MCflare. The server logged the player as successfully connected to voice chat while the public UDP endpoint remained unnecessary. SVC's generated `voice_host=` was left blank; because Minecraft itself connects to MCflare's loopback carrier, SVC sees `127.0.0.1:24454` rather than the protected origin.

SVC's default keepalive interval is 1000 ms. A diagnostic relay confirmed the keepalive exchange continued through MCflare, so the adapter does not add a redundant voice heartbeat thread. `GatewayDatagramClient` uses a bounded timeout only for WSS/service setup, then switches established datagram reads to blocking mode. A 5.5-second idle test followed by a successful datagram round trip verified this lifecycle.

Voice transport selection is asynchronous so SVC initialization does not block Minecraft's render thread. For an ordinary non-MCflare server, MCflare does not install the custom voice socket and SVC continues to use its stock UDP implementation; this was verified with a real SVC authentication/connection-check test.

For a server already classified as MCflare, voice is fail-closed: the gateway must positively advertise a `voicechat` datagram service. If it does not, Minecraft remains connected but SVC voice disconnects rather than falling back to a directly advertised UDP endpoint. This prevents a side-channel/origin leak from undoing the main protected route.
### Voice transport choice

WSS datagrams are functionally proven but should be treated as the compatibility fallback, not automatically the preferred realtime path. A 60-packet test using 200-byte voice-like request/reply payloads through a temporary Quick Tunnel measured roughly:

- average RTT: 155 ms
- median: 155 ms
- p95: 177 ms
- max: 211 ms

More importantly, WebSocket runs over TCP, so packet loss can create head-of-line blocking that realtime voice normally avoids with UDP.

Cloudflare Realtime TURN is a candidate preferred transport because it supports TURN over UDP, TCP, and TLS (including TLS on port 443). Current Cloudflare documentation states a shared Realtime free tier of 1,000 GB/month before $0.05/GB egress. This is research/design only; TURN voice has not yet been implemented or quality-tested in MCflare.
## 10. Cloudflare behavior and constraints

Current official Cloudflare documentation relevant to MCflare:

- Cloudflare Tunnel is outbound-only and available on all plans; no public origin listener is required.
- Published TCP applications are documented as TCP streamed over WebSocket with client-side `cloudflared access tcp`; Cloudflare recommends Client-to-Tunnel for long-lived TCP.
- Cloudflare Tunnel supports normal proxied WebSockets.
- Cloudflare can restart edge servers during deployments, terminating active WebSockets.
- Cloudflare recommends application keepalives for long-running WebSockets and documents idle connection closure when no traffic flows.
- Stopping/replacing a `cloudflared` replica drops long-lived WebSocket/TCP flows; a new replica handles new connections rather than preserving the old stream.
- HTTP origins receive original visitor identity in `CF-Connecting-IP` when Cloudflare proxying is trusted/configured normally.

Development-environment finding (2026-08-30): the Mac test network began blocking Cloudflare Tunnel QUIC on port 7844. `cloudflared` pre-checks reported QUIC failure and one HTTP/2 region unreachable. This produced misleading symptoms where an old Quick Tunnel could still answer Status but full game streams timed out, while a newly created Quick Tunnel never connected. Re-running the disposable connector with `--protocol http2` restored Status and full Minecraft login for both baseline `a7e6a74` and the current voice branch. Treat Quick Tunnel transport health as an external test variable; do not diagnose MCflare regressions from Status-only success. Production should use a named Tunnel and monitor connector health.

These facts are why MCflare uses heartbeat + ordinary reconnect in v1 instead of promising uninterrupted sessions.
### No transparent resume in v1

A deliberate failure test killed the Enhanced edge during a live game. Minecraft disconnected cleanly; restarting the edge restored the route for a normal subsequent connection.

Transparent continuation would require logical session IDs, sequence numbers, acknowledgements, replay buffers, retention windows, and careful interaction with Minecraft's encrypted/compressed stream. That complexity is not justified by current evidence.

v1 policy:

```text
Cloudflare/WSS path breaks
 -> current Minecraft connection ends
 -> route cache is invalidated on unexpected carrier failure
 -> player reconnects
 -> MCflare performs fresh discovery
```

Revisit session resume only if field data shows Cloudflare-induced disconnects are common enough to materially harm users.
## 11. Proven experiments and measurements

The core theory was first isolated from Minecraft mods: a standard WebSocket client sent an actual Minecraft 26.2 Status handshake through Cloudflare to a TCP origin and received a valid status response. The experiment was then repeated against an unmodified Mojang 26.2 server.

A full client test then replaced the external bridge with MCflare's in-process Java carrier. With the old Modflared JAR and external Node bridge absent, the real server logged the test player joining through Cloudflare.

Proven results include:

| Test | Result |
|---|---|
| Real Minecraft Status over direct WSS -> Cloudflare -> TCP | PASS |
| Full 26.2 login through Basic-style carrier | PASS |
| Full 26.2 login through single-process Enhanced HTTP/WSS gateway | PASS |
| No player `cloudflared` process/binary | PASS |
| Normal direct Minecraft server with MCflare installed | PASS |
| Java 8u504 core runtime against live Cloudflare endpoint | PASS |
| `CF-Connecting-IP` and `CF-Ray` present at Enhanced gateway | PASS |
| Test | Result |
|---|---|
| MCF1 `HELLO` capability negotiation | PASS |
| MCF1 datagram round trip 1..8192 bytes | PASS |
| Gateway max-connection rejection (257th after 256 held) | PASS, immediate HTTP 503 |
| Gateway killed during active game | clean disconnect observed |
| Hardened RFC6455 parser after security pass | live Cloudflare Status PASS |
| Fail-closed routing refactor | build/tests PASS; full protected login PASS |
| Ordinary direct regression after fail-closed refactor | discovery miss ~3 ms; full join PASS |
| Simple Voice Chat 2.6.22+26.2 over Enhanced `voicechat` datagram service | authentication + connection validation PASS |
| Voice-enabled MCflare artifact with Simple Voice Chat not installed | PASS; no hard runtime dependency, protected full login succeeds |
| Protected MCflare server with no `voicechat` service | Minecraft stays joined; voice fails closed; no normal UDP fallback |
| Ordinary non-MCflare SVC server with MCflare installed | stock UDP authentication + validation PASS |
| Datagram service idle for 5.5 s then send/receive | PASS after setup/read-timeout split |
| Baseline vs voice branch under blocked QUIC Quick Tunnel | both failed full stream; external connector issue isolated |
| Quick Tunnel forced to HTTP/2 on same network | baseline and current branch full login PASS |
| Named Cloudflare Tunnel Basic control (`legacy-tunnel-test.example.com`) | Status + full 26.2 login PASS |
| Named Cloudflare Tunnel Enhanced HTTP/WSS (`tunnel-test.example.com`) | Status + full 26.2 login PASS; gateway received Cloudflare source metadata |

Observed protected discovery on healthy temporary Quick Tunnels has generally been around 0.9-1.5 seconds after startup/network variability. These are development measurements, not latency SLAs. Quick Tunnel connector health must be checked separately because Status success alone did not guarantee a healthy long-lived route during the QUIC/7844 incident.

The final full protected login after the fail-closed refactor again logged a normal player join on the real 26.2 test server.
## 12. Player and admin UX target

### Player

Normal target flow:

```text
install one MCflare-compatible mod artifact
 -> launch Minecraft normally
 -> add play.example.com
 -> Join Server
```

There should be no Cloudflare account, WARP enrollment, `cloudflared` download, command-line proxy, localhost address, TXT token, or per-server configuration on the player machine.

MCflare remains dormant for ordinary servers after discovery classifies them as direct.
### Admin — Basic

```yaml
ingress:
  - hostname: play.example.com
    service: tcp://127.0.0.1:25565
  - service: http_status:404
```

No server MCflare component is required.

### Admin — Enhanced

Run one gateway locally/private and configure the Tunnel hostname to its HTTP listener:

```yaml
ingress:
  - hostname: play.example.com
    service: http://127.0.0.1:25577
  - service: http_status:404
```

Example gateway services are server-side declarations such as `voicechat=udp://127.0.0.1:24454`. The public hostname remains the same.
## 13. Designs deliberately rejected or deferred

### TXT-based discovery

Rejected as unnecessary admin configuration. Active Minecraft-over-WSS probing already identifies compatible endpoints without another DNS record.

### Bundling/downloading player-side `cloudflared`

Used only in the earliest Modflared baseline to prove Cloudflare TCP transport. Removed from the product direction after the dependency-free Java carrier successfully completed real Minecraft sessions.

### IP-range/CNAME heuristics

Rejected as brittle. Being on a Cloudflare IP does not prove a hostname is an MCflare Minecraft endpoint.

### One WebSocket carrying Minecraft and voice together

Rejected. Realtime side services should not queue behind large ordered Minecraft traffic. Enhanced mode uses independent logical connections/services.
### Generic OS packet interception

Rejected as the primary compatibility mechanism. It would add platform-specific drivers/permissions and obscure which application owns a side channel. Prefer explicit mod APIs and gateway services.

### Direct Minecraft Netty WebSocket replacement

Deferred. The current in-process loopback carrier is simple, portable, and proven. Removing the local socket would increase hook complexity across Minecraft versions for little demonstrated benefit.

### Transparent stream resume/replay

Deferred for v1 because it greatly increases protocol state and correctness risk. Normal reconnect is currently the intended recovery behavior.

### WSS as guaranteed primary voice transport

Rejected. It works as a fallback, but TCP head-of-line blocking makes native realtime UDP/TURN worth testing before choosing a preferred voice transport.
## 14. Code-quality rules

MCflare should stay understandable enough to audit without reconstructing a networking framework.

- Prefer one obvious implementation over parallel abstractions.
- Core protocol constants live in one place (`GatewayProtocol`).
- Loader adapters must not reimplement TLS/WebSocket/discovery.
- Enhanced mode is one gateway process, not a chain of local proxies.
- No unused optional-mod dependencies in the transport baseline.
- No long-lived child process on player machines.
- Avoid large queues; current stream writes apply natural blocking/backpressure.
- Treat input size/time/resource limits as part of protocol correctness.
- Keep experimental features behind separate commits/gates.
- Do not claim a version/loader/mod integration until it has a real connection test.

A local duplicate-window scan across the active Java files found no cross-file 8-line copy/paste clusters during the 2026-08-30 hardening pass.
## 15. Build and CI baseline

The hardened transport baseline must pass:

```bash
./gradlew --no-daemon clean build
```

`core` and `gateway` target Java 8. The current Fabric 26.2 adapter targets the Java runtime required by that Minecraft release.

The pushed checkpoint before this hardening pass (`de01b66`) passed GitHub Actions on Ubuntu. Local hardening builds also pass; the hardening/docs changes must be pushed only after the final regression suite is clean.

Known build note: Gradle currently emits Java-8 source/target deprecation warnings under the modern build JDK and a general future Gradle-10 compatibility warning. No project-owned Gradle deprecation was identified during the pass; do not add workaround complexity without locating a concrete project source.
## 16. Remaining gates, in priority order

1. Commit/push the isolated Simple Voice Chat adapter after its final clean build/tests.
2. Add an automated routing test that deliberately forces protected-route setup failure and proves no direct target is selected; keep the test seam minimal rather than adding a production abstraction solely for testing.
3. Test a named production-style Cloudflare Tunnel, not only Quick Tunnels, and verify connector behavior under both QUIC and HTTP/2 transport.
4. Run longer multi-client sessions and connection churn tests.
5. Measure real two-client SVC audio latency/jitter/loss over WSS; compare with direct UDP.
6. Compare WSS datagram voice with TURN/UDP under realistic latency/loss/jitter before choosing a preferred realtime transport.
7. Prove the architecture on Forge 1.12.2, then expand loader/version adapters.
8. Test proxy stacks such as Velocity/Paper where handshake/SRV behavior differs.
9. Design trusted source-IP integration only after the gateway deployment model is fixed.
10. Build release packaging only after compatibility gates are real.

Production origin port 25565 should remain closed/publicly unreachable throughout testing.
## 17. Reference sources checked

Cloudflare (current docs checked 2026-08-30):

- Tunnel overview: https://developers.cloudflare.com/tunnel/
- Published application protocols: https://developers.cloudflare.com/cloudflare-one/networks/connectors/cloudflare-tunnel/routing-to-tunnel/protocols/
- Tunnel routing: https://developers.cloudflare.com/tunnel/routing/
- WebSockets behavior/keepalive/restarts: https://developers.cloudflare.com/network/websockets/
- Tunnel config/replica connection behavior: https://developers.cloudflare.com/tunnel/advanced/local-management/configuration-file/
- Original visitor IP headers: https://developers.cloudflare.com/support/troubleshooting/restoring-visitor-ips/restoring-original-visitor-ips/
- Realtime TURN: https://developers.cloudflare.com/realtime/turn/
- Realtime TURN FAQ/pricing: https://developers.cloudflare.com/realtime/turn/faq/

Simple Voice Chat 26.2 source/API reviewed locally from https://github.com/henkelmax/simple-voice-chat/tree/26.2, including `ClientVoicechatSocket` and `ClientVoicechatInitializationEvent#setSocketImplementation`.

MCflare retains the MIT attribution required for code derived from Modflared; see `NOTICE.md`.

## 18. Architecture re-evaluation — 2026-08-30

The preferred v1 architecture is now **normal Cloudflare orange-cloud HTTP/WebSocket proxying as the production default**, with Cloudflare Tunnel retained as an optional way to reach the same HTTP/WebSocket gateway when inbound origin HTTPS is unavailable or intentionally forbidden. The former raw `tcp://` Basic mode remains a proven historical compatibility path but should not define new protocol work. See `LOW_LATENCY_ARCHITECTURE.md` for the latency-first target architecture and migration plan.

### Recommended protocol simplification (next refactor; not implemented yet)

Enhanced mode should select services by the WebSocket HTTP path instead of multiplexing them after upgrade with `MCF1` magic/opcodes and a separate `HELLO` capability request:

```text
/.well-known/mcflare                         -> Minecraft byte stream
/.well-known/mcflare/v1/datagram/voicechat  -> Simple Voice Chat datagrams
/.well-known/mcflare/v1/stream/<service>     -> reserve only when a real TCP-side-service adapter exists
```

For the Minecraft path, the WebSocket upgrade itself should become discovery: require `Sec-WebSocket-Protocol: mcflare.v1`, and treat a matching `101` response as proof of MCflare. Keep that successful WebSocket as a prepared transport for the actual Minecraft carrier instead of closing it and opening a second connection. This removes Minecraft Status parsing from route discovery and eliminates an avoidable TCP+TLS+WebSocket handshake on the first protected connection.

For a datagram path, the Enhanced gateway validates the configured service before accepting/using the channel and sends a tiny server-first acknowledgement. The client sends no application bytes before that acknowledgement. This is important for legacy Basic mode: the current named Basic Tunnel was experimentally tested with the proposed voice path; its WebSocket upgrade succeeded but remained byte-silent, so a side-service attempt can timeout/fail closed without injecting control bytes into the Minecraft origin.

This makes `HELLO`/capability JSON unnecessary for current SVC behavior. The SVC adapter already knows that it needs service `voicechat` of type datagram, so it should directly attempt that service. The refactor can remove `GatewayControlClient`, capability parsing/futures, `MCF1` magic/opcode dispatch, and the gateway's first-four-byte Minecraft-vs-control sniff. Keep explicit `u16 length + payload` datagram framing: WebSocket frame boundaries are not application record boundaries.

### Shared-version simplification

Before adding Forge/NeoForge/legacy adapters, move hostname normalization, cache TTLs, WSS-vs-direct discovery policy, and direct-reachability probing out of the Fabric `TunnelManager` into a dependency-free Java-8 core resolver. Loader adapters should supply only the logical hostname/port, actual Minecraft protocol version, and Minecraft-resolved/SRV address. DNS/SRV resolution remains owned by Minecraft.

The current `RunningTunnel` and `TunnelStatus` wrappers can likely collapse around `LoopbackCarrier`: the carrier already owns the protected hostname and local listener. A connection should own and close its carrier directly; a central list of every live carrier is unnecessary if connection failure/disconnect paths close it and executor threads are daemon threads. The fail-closed invariant must remain explicit: a resolver result of MCflare followed by carrier-creation failure is an error, never a direct fallback.

### Complexity deliberately retained

The localhost `LoopbackCarrier` remains intentional. Replacing Minecraft's Netty channel directly would save one loopback socket but multiply loader/version-specific hooks and make universal support more fragile. `WebSocketByteStream` also remains justified for side-channel framing because RFC 6455 intermediaries may fragment/coalesce frames. Client/server RFC6455 implementations should remain separately auditable unless a very small pure helper can be shared without obscuring opposite masking/trust rules.

The generic `OPEN_STREAM` path is currently speculative because no real adapter uses it. Do not keep an in-band stream opcode merely for future possibility; reserve a URL convention and add the implementation when a real TCP-side-service integration needs it.

### Alternatives reviewed and deferred

Workers VPC can now let a Worker open raw TCP to a private service through a VPC Network binding, but it is beta and adds Worker/VPC deployment, quotas, and another Cloudflare product boundary while not solving UDP voice. It is not simpler than the small local Enhanced gateway for v1. Cloudflare Realtime TURN remains a promising future voice transport (native UDP/TCP/TLS relay), but adding TURN allocation/credentials before two-client WSS audio testing would be premature. Transparent Minecraft session replay/resume and direct Netty/WebSocket replacement remain deferred.

### Operational/security notes added by this review

Enhanced gateway paths can use Cloudflare WAF/rate-limiting on the initial WebSocket upgrade, but established WebSocket payloads are not inspected by WAF. Do not place browser-interactive Access/Managed Challenge behavior in front of MCflare paths. Keep gateway frame/connection limits. Make the current 256-connection ceiling configurable because Enhanced SVC commonly consumes two long-lived WebSockets per player (Minecraft + voice), plus short-lived discovery/status connections.

Trust `CF-Connecting-IP` only when the gateway is private behind Cloudflare. It does not automatically become Minecraft's socket remote address; IP-ban/proxy integrations remain separate future work. Production should keep `cloudflared` and the gateway on loopback or the same private container network where possible. Do not enable HTTP/2-to-origin for the current hand-written HTTP/1.1 WebSocket gateway.

For optional SVC integration, keep one MCflare artifact rather than a second addon JAR. The current no-SVC runtime test proves the Fabric `voicechat` entrypoint remains dormant when SVC is absent. Add metadata/version guards for incompatible SVC API versions rather than reflection or a second install step.

## 19. Scope simplification — 2026-08-31

This decision supersedes the earlier side-service/voice architecture for the product: **MCflare transports only Minecraft's own Java TCP connection.** Any protocol or mod traffic already inside the Minecraft connection remains transparently supported. A mod that opens a separate TCP/UDP socket is outside MCflare's scope and uses/exposes its own endpoint.

Consequences implemented on `feature/orange-minecraft-only`:

- Simple Voice Chat MCflare adapter removed; SVC uses native UDP independently.
- `GatewayControlClient`, `GatewayDatagramClient`, `GatewayProtocol`, `WebSocketByteStream`, MCF1 HELLO/OPEN_STREAM/OPEN_DATAGRAM, capability JSON, generic UDP/TCP side services, and their tests removed.
- SVC Maven/API dependency and Fabric `voicechat` entrypoint removed.
- Enhanced gateway reduced to one responsibility: `/.well-known/mcflare` WebSocket bytes <-> one configured Minecraft TCP backend.
- Gateway connection limit is configurable through the third CLI argument, default 256.

Historical SVC-over-MCflare and datagram experiments remain useful evidence but are retired from the intended product. They proved separate-socket proxying is possible; the new scope explicitly chooses not to carry that complexity.

The preferred deployment is normal Cloudflare orange-cloud HTTP/WebSocket proxying. Cloudflare Tunnel remains optional only as an origin-reachability mechanism for CGNAT/no-public-ingress cases; it reaches the same Minecraft-only HTTP/WebSocket gateway. The next latency optimization is prepared-WebSocket discovery: identify MCflare via an explicit WebSocket subprotocol and reuse the successful discovery socket as the actual Minecraft carrier.

## 20. Minecraft-only prepared-WebSocket baseline — 2026-08-31

The Minecraft-only simplification and first-connect latency refactor are now implemented on `feature/orange-minecraft-only`. Active Java source is reduced to four Java-8 core classes (`Rfc6455Client`, `McflareProtocol`, `RouteResolver`, `LoopbackCarrier`), two gateway classes, and the Fabric initializer plus three connection lifecycle hooks.

`MinecraftStatusProbe`, `TunnelManager`, `RunningTunnel`, and `TunnelStatus` were removed. Discovery now requests WebSocket subprotocol `mcflare.v1`; the gateway requires and echoes it. The successful discovery WSS is retained and handed directly to the one-shot loopback carrier for Minecraft, eliminating the former second TCP/TLS/WebSocket handshake. `RouteResolver` now lives in the Java-8 core and a successful secure WSS wins the direct-vs-secure race immediately.

The refactor passed a clean build, Java-8 resolver tests, and a full Minecraft 26.2 join through the named HTTP/Tunnel gateway. The gateway logged one upgrade for the actual join and the Oracle Minecraft server logged `Phlo joined the game`.

A 10-attempt named-Tunnel prepared-handshake sample then produced 9 successes (447, 460, 463, 469, 476, 700, 997, 1309, 2390 ms) and one TCP connect timeout. Treat this only as the current Tunnel path baseline. The product decision remains Orange proxy as the default and Tunnel as optional origin reachability.

## 2026-08-31 true Orange validation and benchmark correction

The product scope is now Minecraft-only. Historical SVC/MCF1/datagram sections above are retained as experiment history, but they are superseded by the later Minecraft-only decision and are not active architecture.

Two initially attempted "Orange" benchmark hostnames were later found to be invalid controls:

- `<redacted-unrelated-host>` is an ingress on Tunnel `<redacted-tunnel-id>`.
- `<redacted-unrelated-host>` is an ingress on Tunnel `<redacted-tunnel-id>`.

Their earlier latency measurements must not be cited as Orange-cloud results.

A preliminary true-Orange path was first validated on `<redacted-preliminary-host>`. A dedicated permanent test hostname, `orange-test.example.com`, was then created as a normal proxied A/AAAA record. All running `cloudflared` container configurations were audited and the dedicated hostname is absent from every Tunnel ingress. Cloudflare reaches Oracle Traefik directly, which forwards only `/.well-known/mcflare` to the gateway. The public `mcflare.v1` WSS upgrade and a full Minecraft 26.2 login both passed.

Same Mac, same Oracle backend/gateway, 15 samples per path:

| Path | Setup median | Minecraft RTT median | RTT p95 |
|---|---:|---:|---:|
| Direct Oracle WSS | 207 ms | 70 ms | 78 ms |
| Dedicated true Orange | 571 ms | 144 ms | 610 ms |
| Named Tunnel | 580 ms | 158 ms | 896 ms |

Dedicated true Orange reduced median gameplay RTT by about 14 ms versus the named Tunnel in the final same-client run, but remained about 74 ms slower than direct. This indicates that removing the Tunnel connector helps modestly, while Cloudflare edge/origin routing remains the larger latency cost on this network. Cloudflare-tail spikes were substantial in both proxied paths and should be investigated with longer-duration gameplay/jitter tests.

During Orange testing, one returned Cloudflare IPv4 address timed out while another connected immediately. `Rfc6455Client` was therefore hardened to race resolved addresses with a short stagger and use the first successful TCP connection. The fix passed the clean build, dedicated true-Orange WSS, full Minecraft login, and a real Temurin Java 8 runtime test. A freshly created proxied hostname initially returned Cloudflare `526` until Traefik/ACME had issued a matching origin certificate; MCflare deployment should verify the WebSocket upgrade before considering DNS provisioning complete.

Tunnel-specific code is now absent from MCflare. Tunnel may still be used externally to publish the same HTTP/WebSocket gateway, but it does not alter client/gateway protocol or source structure.
## 21. Standards-first v1 architecture freeze — 2026-09-01

The product protocol is now `wss://<logical-host>/mcflare` with WebSocket subprotocol `mcflare.v1`. `/mcflare` replaces the experimental unregistered `/.well-known/mcflare` suffix. The successful WebSocket upgrade remains the actual Minecraft carrier. Gameplay payloads remain raw ordered Minecraft bytes.

Orange and Tunnel are explicitly external ingress alternatives. Orange uses an administrator-controlled reverse proxy; Tunnel uses administrator-managed cloudflared with an HTTP service. MCflare has no Cloudflare API/Tunnel credential/process management.

Real visitor IP is now a v1 requirement. Cloudflare's `CF-Connecting-IP` / `CF-Connecting-IPv6` is translated by the gateway into standard HAProxy PROXY v1. Fabric and NeoForge share a bounded loopback-trusted PROXY-v1 parser that updates `Connection.address`; no separate Netty HAProxy codec is bundled. The Fabric artifacts are also runtime-proven unchanged on Quilt. Paper/Purpur use one Java-21 server plugin and their native PROXY implementation instead of Minecraft networking injection.

Oracle reconstruction from handoff commit `747fc9f88254d87451e6df8a38521d518a845a6f` passed a Java-25 clean build, local synthetic source-IP/PROXY Status, true Orange `/mcflare` Status, named Tunnel `/mcflare` Status, and combined live Cloudflare -> PROXY -> integrated Fabric Status for both ingress modes. Legacy routes and direct production/dev Minecraft remained healthy. Quick Tunnel was excluded after repeated edge 404/500 responses that never reached origin despite a healthy registered connector.

See `TEST_EVIDENCE_2026-09-01.md` for exact proof and `IMPLEMENTATION_PLAN.md` for the gates that still remained at this checkpoint. The same day, the rebuilt Fabric 26.1 real client subsequently closed the `/mcflare` world-join gate on both Orange and named Tunnel; see the following section.

## 22. Oracle real-client `/mcflare` acceptance — 2026-09-01

A real Minecraft client no longer requires the Mac test host. An isolated ARM64 Ubuntu Docker image on Oracle runs OpenJDK 25 + Xvfb + Mesa llvmpipe/OpenGL 4.5 and successfully launches the actual Fabric 26.1 Minecraft client. This environment is acceptance-test infrastructure only.

Using source commit `7e542e9354948b41f7d9188627d4f4661484c51e`, Minecraft Quick Play joined an isolated Fabric 26.1 server through `orange-test.example.com` and then `tunnel-test.example.com`. Both paths used `/mcflare` + `mcflare.v1`, the real client Mixins/RouteResolver/LoopbackCarrier, Cloudflare, the integrated gateway, PROXY v1 and the normal Minecraft login/configuration/game phases. Both reached `joined the game`.

The gateway reported `realIpPresent=true cfRayPresent=true` on both upgrades. Minecraft logged the client as `<redacted-public-ip>` on both joins, independently matching Oracle's public IPv4. This closes the real-client full-transport and login-log IP proof for true Orange and named Tunnel. The isolated server was intentionally offline-mode; the separate authenticated Mojang online-mode proof was later completed on 2026-09-03 with the published `v1.0.0-rc.1` Fabric client through both delivery modes.

After the proof, both test `/mcflare` routes were restored to parallel gateway `25588`, the temporary `25585/25587` server was stopped, named test cloudflared was restarted, and v1 Orange/Tunnel Status, both legacy WSS paths, direct production Minecraft Status and PufferPanel health all passed. Production `25565` and legacy `25577` were not restarted.

## 23. Direct-server and native IP-ban acceptance — 2026-09-01

The real Fabric 26.1 client with MCflare installed joined `ordinary-minecraft.test:25586`, a clean Fabric server with zero MCflare server mods. Minecraft logged `Player438[/127.0.0.1:33416]` and the player joined normally. This closes ordinary Quick Play/Direct Connect compatibility. The actual graphical Multiplayer pinger was subsequently exercised too; see section 24.

The source-IP proof was then strengthened beyond logging. Through true Orange, Minecraft saw `Player393[/<redacted-public-ip>:53422]`. Native `ban-ip <redacted-public-ip>` immediately kicked that player, and a fresh real MCflare-equipped client was rejected as `Player44 (/<redacted-public-ip>:42538)` because the IP was banned. The address was pardoned afterward and the ban file returned empty.

The temporary `25585/25587` test services were stopped, Orange `/mcflare` was restored to `25588`, and v1 Orange/Tunnel, both legacy paths, direct production Status and PufferPanel all passed. This confirms that `CF-Connecting-IP -> PROXY v1 -> Connection.address` is sufficient for Minecraft's native IP-ban behavior without custom identity packets.

## 24. Graphical ordinary-server pinger acceptance — 2026-09-01

The real Fabric 26.1 client was launched normally rather than with Quick Play, then the actual title-screen `Multiplayer` button was clicked under Oracle/Xvfb. The disposable profile already contained the ordinary server from the earlier Quick Play test; Quick Play had marked the NBT entry `hidden=1`, so only that test-profile flag was changed to `hidden=0`. The client container also received an explicit test-only hosts mapping `ordinary-minecraft.test -> 127.0.0.1`; no MCflare product source changed.

Minecraft's real `ServerStatusPinger` then opened a loopback TCP connection to `25586`. `/proc/net/tcp6` observed an ephemeral client socket (`B1D4`) to `127.0.0.1:25586` (`63F2`) in `ESTABLISHED` state. The rendered Multiplayer screen showed the saved server as online with `0/20`, green latency bars and MOTD `Ordinary no-MCflare 26.1 regression`. This closes the graphical ordinary-server status-ping regression with MCflare installed.

## 25. Sustained true-Orange gameplay/chunk-burst acceptance — 2026-09-01

The isolated Fabric 26.1 acceptance server again used `127.0.0.1:25585` with its integrated gateway on `<private-origin-ip>:25587`. Only the test `/mcflare` routes were temporarily moved from the parallel `25588` gateway to `25587`; production Minecraft `25565` and legacy gateway `25577` were not replaced. Both public hostnames returned the isolated server's distinct 125-byte Status response before the real-client run.

The real Fabric client joined true Orange at 14:10:04 as `Player6[/<redacted-public-ip>:39352]`; the gateway had accepted exactly one gameplay upgrade at 14:09:58 with both Cloudflare metadata fields present. An initial unsafe harness teleport caused the player to drown, but the transport remained connected. The player was switched to spectator, respawned, and Minecraft reported health `20.0f`.

The controlled burst phase ran from 14:32:43 through 14:37:50. Seven high-altitude teleports targeted `(2000,2000)`, `(6000,-6000)`, `(-12000,8000)`, `(24000,24000)`, `(-32000,-16000)`, `(48000,12000)` and `(-64000,64000)`. Each forced fresh-region work; the server recorded world-generation stalls ranging from about 3.3 s to 6.9 s. After the final burst Minecraft still reported health `20.0f` and one player online.

The same client session lasted 31m27s from login until deliberate container shutdown. There was one gameplay WSS upgrade, one Minecraft login, zero spontaneous player disconnect/rejoin events, and the same gateway/backend socket pair remained established during the closing check. The client log contained no transport error after login; only expected headless/X11 test-environment errors appeared. The deliberate client stop produced the normal `lost connection: Disconnected` and `left the game` messages. This closes the long-lived-connection plus fresh-chunk/teleport burst stability gate. The separate 30-minute active-gameplay latency/jitter characterization was subsequently completed on 2026-09-02: 1801.449 seconds, 120 cycles, 240/240 probes, zero route mismatches, and one continuous gameplay WSS connection.

After restoration, both `/mcflare` routes again hit `25588` and returned 105-byte Status responses; both legacy `/.well-known/mcflare` paths passed; direct production Status passed; PufferPanel returned HTTP 200; `25585/25587` were absent; and the original `25577` and `25588` gateway processes remained in place.

## 26. Three-real-client concurrency and live churn — 2026-09-01

The Oracle acceptance harness ran three actual Fabric 26.1 clients concurrently through one isolated MCflare gateway: two via true Orange and one via the named HTTP Tunnel. Minecraft logged all three using the restored visitor IPv4 `<redacted-public-ip>` with independent source ports, while the gateway held three concurrent WSS sessions and three backend Minecraft streams. All clients remained connected while they were spread into three distant regions; simultaneous world generation caused a ~21 s server tick stall but no MCflare connection loss.

The named-Tunnel client was then deliberately removed while both Orange clients stayed connected. A fresh named-Tunnel client joined successfully, restoring the three-client state without disturbing the surviving Orange sessions. The final three disconnects were deliberate test shutdowns. This closes the realistic real-client concurrency/churn acceptance gate at three clients. It is not a maximum-load claim: the Oracle software-rendered clients consumed roughly 2.1-2.5 GiB each, so a fourth simultaneous client was intentionally not attempted. The gateway's bounded-capacity/503 behavior remains separately covered by synthetic tests.

Both test routes were restored to `25588`; the isolated `25585/25587` server was stopped; v1 Orange/Tunnel, both legacy paths, direct production Status and PufferPanel health all passed afterward. Production `25565`, legacy `25577`, and the parallel `25588` process were not replaced.

## 27. Named-Tunnel local connector restart/recovery — 2026-09-01

The isolated Fabric 26.1 acceptance server used `127.0.0.1:25585` and integrated MCflare `<private-origin-ip>:25587`. Only `tunnel-test.example.com` `/mcflare` was temporarily routed to `25587`; true Orange stayed on `25588` as a live control. A real named-Tunnel client joined as `Player66[/<redacted-public-ip>:52604]`.

Restarting the local named test `cloudflared` container caused the gateway-side WSS stream to reset, the Minecraft server to remove the player cleanly, and the real client to show `Disconnected`; afterward no established gateway/backend socket remained. Once `cloudflared` returned, WSS Status and PufferPanel were healthy, and a fresh real client joined as `Player971[/<redacted-public-ip>:41994]`. This is evidence for local connector restart/recovery only, not a Cloudflare-edge failure test.

The Tunnel ingress was restored to `25588`; the isolated server/client were stopped; v1 Orange/Tunnel, both legacy paths, direct production Status and PufferPanel all passed. The persistent `25577` and `25588` gateway processes were unchanged.

## 28. True-Orange client-network loss and symmetric gateway teardown — 2026-09-01

A real Fabric 26.1 client joined the isolated `25585/25587` acceptance server through true Orange while attached to a dedicated disposable Docker network. Removing only that client network created a black-holed client path without restarting Cloudflare, Traefik, the gateway or Minecraft. The first run exposed a gateway lifecycle issue: Minecraft timed the player out and the backend stream closed, but the gateway-side WSS socket stayed established because the downstream pump did not close the WebSocket when backend EOF occurred. A dead network client could therefore occupy a gateway semaphore slot longer than its Minecraft session.

The gateway pump is now symmetric: when the Minecraft-to-WebSocket downstream side finishes or fails, it closes both the backend and WebSocket, which unblocks the uplink and releases the connection slot. `McflareGatewayLifecycleTest` covers this with a one-slot gateway: backend EOF must close the client promptly and a second WebSocket must then be accepted. Focused gateway tests pass with JDK 25 while still compiling the gateway to Java 8 bytecode.

The real black-hole test was repeated against the rebuilt integrated gateway. The client joined successfully with Cloudflare source metadata, its Docker network was removed, Minecraft removed the player, and by the 45-second post-cut inspection there were no established sockets on either `25585` or `25587`. A fresh-client rejoin after network restoration was already proven in the preceding run. This closes client-network-loss teardown/recovery, but it is not a Cloudflare-edge outage test.

Orange was restored byte-for-byte to the saved `25588` configuration. Both true-Orange and named-Tunnel `/mcflare` then returned the normal 105-byte production Status response, `25585/25587` were clear, and the original `25577` and `25588` gateway PIDs remained unchanged.

## 30. Higher-scale full-protocol GAME-state concurrency — 2026-09-02

A disposable MCProtocolLib `26.1-1` harness removed the Oracle llvmpipe rendering/RAM ceiling while still using MCflare's production `Rfc6455Client` and `LoopbackCarrier`. Against an isolated Fabric 26.1 server/integrated proxy-enabled gateway, each client had to reach both inbound and outbound Minecraft `GAME` state and remain connected. True Orange passed 16/16 simultaneous GAME sessions for 45 seconds plus four 16-client churn cohorts (64/64 additional joins). Named Tunnel passed the same 16/16 45-second cohort and four 16-client churn cohorts (64/64). Both 45-second runs crossed MCflare's 30-second WebSocket Ping interval with all sessions alive.

This closes higher-scale MCflare transport/session concurrency beyond the earlier three graphical clients. It does not claim 16 simultaneous moving/rendering clients or a Minecraft world-generation capacity benchmark; the three-client graphical separate-region test remains the active-world-load evidence. Both public routes were restored byte-for-byte to `25588`, the isolated `25585/25587` server stopped cleanly, final Orange/Tunnel production Status returned 105 bytes, direct production Status passed, PufferPanel returned HTTP 200, and the long-lived gateway processes remained unchanged.
