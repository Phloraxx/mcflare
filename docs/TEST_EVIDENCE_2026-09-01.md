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
