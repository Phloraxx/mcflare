# MCflare Test Evidence - 2026-09-01

This file records tests performed during the standards-first v1 reconstruction. It distinguishes proven behavior from planned behavior.

## Safe starting point

Validated source checkpoint: `747fc9f88254d87451e6df8a38521d518a845a6f` on `feature/orange-minecraft-only`. The Oracle reconstruction used a new isolated worktree/branch and did not replace the existing `25577` gateway or production Minecraft server.

## Build environment

Oracle system Java remains Java 21 runtime-only. A user-local Ubuntu OpenJDK 25.0.4 JDK was extracted under `/home/drvij/.local/jdk25-root`; no system Java package was installed. Gradle clean build result after reconstruction:

```text
BUILD SUCCESSFUL in 1m 2s
19 actionable tasks: 16 executed, 3 up-to-date
```

Gateway JUnit tests include PROXY-v1 IPv4/IPv6 encoding and no-header behavior when no client IP exists.

## Fabric server integration

Minecraft 26.2 dedicated dev server:

```text
Minecraft: 127.0.0.1:25585
MCflare integrated gateway: 127.0.0.1:25587 initially
PROXY protocol: enabled
```

The same Fabric artifact loaded in server environment. Gateway lifecycle started successfully. A separate earlier Mac test also confirmed that a gateway bind conflict logs an error while Minecraft continues running.

## Synthetic source-IP proof

A local RFC6455 client sent:

```text
GET /mcflare
Sec-WebSocket-Protocol: mcflare.v1
CF-Connecting-IP: 198.51.100.42
CF-Ray: synthetic test value
```

It then sent a real Minecraft 26.2 Status handshake/request over the WebSocket. The integrated gateway emitted PROXY v1, the Fabric listener decoded it, and the dev server returned its Status response.

Result: `CF_IP_PROXY_MINECRAFT_STATUS=PASS`.

### Source-port finding

A manually injected PROXY v1 line with source port `0` was closed by Netty's HAProxy decoder. The same line with a nonzero source port succeeded. Implementation therefore supplies a nonzero ingress connection port as an opaque placeholder while preserving the actual Cloudflare visitor IP.

## True Orange standalone proof

A parallel v1 gateway was started on `10.0.0.18:25588`, leaving legacy `25577` untouched. A second Traefik router for only `/mcflare` was added to the dedicated true-Orange hostname while the old `/.well-known/mcflare` router remained.

`mcflare-orange-test.mulearnscet.in/mcflare` returned `101 Switching Protocols`, echoed `mcflare.v1`, and carried a real Status request to the production 26.2 backend.

Result: `TRUE_ORANGE_V1_MINECRAFT_STATUS=PASS`.

Gateway: `realIpPresent=true`, `cfRayPresent=true`.

## Named Tunnel standalone proof

The existing named Tunnel has `mcflare2-test.mulearnscet.in`. A validated path-specific rule for `^/mcflare$` was inserted ahead of the legacy fallback route. Cloudflared supports regex path ingress rules and validation returned `OK` after excluding an unrelated legacy `warp-routing` key warning.

`mcflare2-test.mulearnscet.in/mcflare` returned `101`, echoed `mcflare.v1`, and carried Status to the same standalone gateway/backend.

Result: `NAMED_TUNNEL_V1_MINECRAFT_STATUS=PASS`.

## Live Cloudflare -> PROXY -> Fabric combined proof

The dev integrated gateway was rebound to `10.0.0.18:25587`. Only the new `/mcflare` test routes were pointed to it; legacy paths remained on `25577`.

Both:

```text
mcflare-orange-test.mulearnscet.in/mcflare
mcflare2-test.mulearnscet.in/mcflare
```

returned the dev server's distinct MOTD `MCflare Oracle integration test`, proving the request reached the integrated server rather than the production backend. The server gateway logged two upgrades with `realIpPresent=true` and `cfRayPresent=true`.

Results:

```text
Orange: LIVE_CF_IP_PROXY_FABRIC_STATUS=PASS
Tunnel: LIVE_CF_IP_PROXY_FABRIC_STATUS=PASS
```

## Quick Tunnel non-result

A disposable Quick Tunnel connector registered successfully and reported healthy DNS, QUIC, HTTP/2 and API prechecks, but the generated `trycloudflare.com` hostname repeatedly returned Cloudflare 404/500 without reaching the gateway. This is not counted as an MCflare failure or acceptance result. Production-style named Tunnel remains the regression control.

## Remaining release gates

The side-by-side legacy regressions, PufferPanel health check, direct Minecraft regression, final clean build, and actual Java `Rfc6455Client` Status probes are complete. Remaining before a stable release:

- full authenticated/online-mode gameplay login through `/mcflare` from a real MCflare-equipped player;
- sustained gameplay and reconnect tests;
- connection concurrency/overload tests on the new integrated server path;
- live IPv6 visitor-IP restoration test;
- loader/version adapter validation beyond Fabric 26.2.

## Actual MCflare Java client transport

The dependency-free `Rfc6455Client` from `mcflare-core` was compiled into a standalone probe and used against both live v1 endpoints. It sent a real Minecraft 26.2 Status handshake/request through the negotiated WebSocket and read the status response through `Rfc6455Client.readData()`.

- true Orange `mcflare-orange-test.mulearnscet.in/mcflare`: **PASS**
- named Tunnel `mcflare2-test.mulearnscet.in/mcflare`: **PASS**
- negotiated subprotocol: `mcflare.v1`
- client protocol path: `/mcflare`

This proves the production client transport and the independent RFC6455 test harness agree on the v1 wire contract.

## Final PROXY trust hardening

The Fabric PROXY detector was narrowed from same-machine addresses to loopback-only trust. After that change, a clean 19-task build passed, a synthetic `CF-Connecting-IP` Status request through the integrated gateway passed, and a direct vanilla Status request to the same Minecraft listener also passed without a PROXY header.

```text
LOOPBACK_ONLY_PROXY_STATUS=PASS
DIRECT_WITH_LOOPBACK_DETECTOR_STATUS=PASS
```

## Post-push edge-case hardening

Additional Oracle-only gates found and fixed one timing issue. Minecraft installs a roughly 30-second pre-handshake read timeout; the gateway previously opened the backend immediately at WebSocket upgrade, so a deliberately delayed first Minecraft packet could lose the backend even while WebSocket Ping/Pong remained healthy. The gateway now lazy-connects the Minecraft backend on the first binary application bytes.

Validation after the change:

```text
Orange Rfc6455Client reconnects: 10/10 PASS
Tunnel Rfc6455Client reconnects: 10/10 PASS
Orange 40-second Ping/Pong then Status: PASS
Tunnel 40-second Ping/Pong then Status: PASS
Gateway max=4, fifth connection HTTP 503: PASS
Slot release/reacquire: PASS
Fragmented binary + interleaved Ping: PASS
Wrong path -> HTTP 404: PASS
Wrong subprotocol -> HTTP 400: PASS
Unmasked client frame rejected: PASS
Oversized frame rejected before payload read: PASS
Lazy backend: no backend until first binary bytes: PASS
```

The WebSocket framing/rejection cases are also covered by `WebSocketServerConnectionTest`, so they are no longer manual-only.

A live public-IPv6 visitor test could not be run: Oracle has no global IPv6 default route and the Mac test host was offline. The IPv6 path was nevertheless tested end-to-end synthetically: when both forwarding headers were supplied the gateway preferred `CF-Connecting-IPv6` and emitted `PROXY TCP6`, and a Fabric 26.2 server accepted a direct TCP6 PROXY header and returned a real Minecraft Status response. The public-IPv6 edge/origin gate remains open.

```text
SYNTHETIC_IPV6_GATEWAY=PASS
FABRIC_PROXY_TCP6_STATUS=PASS
```

## Fabric version-family consolidation

A standards-first build matrix was tested instead of copying Modflared's per-version branch model.

The identical MCflare Fabric Java source compiled successfully against:

- Minecraft 1.21.11 / Fabric Loader 0.18.2 / Loom-remap 1.14 / Java 21 / Netty HAProxy 4.2.7;
- Minecraft 26.1 / Fabric Loader 0.18.4 / Loom 1.15 / Java 25 / Netty HAProxy 4.2.7;
- Minecraft 26.2 / Fabric Loader 0.19.3 / Loom 1.17 / Java 25 / Netty HAProxy 4.2.15.

The client-side redirect targets were inspected in the mapped Minecraft bytecode. `Connection.connect`, `Connection.connectToServer`, `ServerAddress.parseString/getHost/getPort`, and the relevant invocation descriptors are identical across all three tested versions.

### Standalone 1.21.11 production artifact

The real remapped `mcflare-fabric-1.21.11` JAR was installed into a clean Fabric 1.21.11 server created with the official Fabric installer. It loaded its nested core, gateway and Netty HAProxy 4.2.7 dependencies, started the local MCflare endpoint, and passed a synthetic Cloudflare-header WebSocket -> PROXY v1 -> real Minecraft Status exchange.

Result: `REMAPPED_1_21_11_ARTIFACT_PROXY_STATUS=PASS`.

### One binary for Minecraft 26.1 and 26.2

A single artifact was compiled against the older 26.1 baseline, embedded Netty HAProxy 4.2.7, and declared the tested runtime range `>=26.1 <26.3`.

The **same exact JAR file** was then installed without modification into separate clean standalone Fabric servers:

- Fabric 26.1 / Loader 0.18.4: `COMBINED_26X_JAR_ON_26_1=PASS`;
- Fabric 26.2 / Loader 0.19.3: `COMBINED_26X_JAR_ON_26_2=PASS`.

Both servers loaded the same artifact, started the integrated gateway, accepted `CF-Connecting-IP`, emitted PROXY v1, and returned a real Minecraft Status response. This justifies one current `mcflare-fabric-26.1-26.2` release artifact instead of separate 26.1 and 26.2 binaries.

The client-side combined-binary claim is supported by identical redirect descriptors across the two 26.x versions; a real graphical player login remains part of the stable-release gameplay gate.

### CI-matrix parity check

The final three-row GitHub Actions matrix was reproduced locally before commit. The 1.21.11 row used an actual OpenJDK 21 compiler/runtime, while both 26.x rows used OpenJDK 25. All three `clean build` executions passed. A no-argument default build also produced `mcflare-fabric-26.1-26.2-0.1.0-dev.jar` with runtime metadata `minecraft: >=26.1 <26.3`, `java: >=25`, Loader `>=0.18.4`, and embedded Netty HAProxy 4.2.7.
