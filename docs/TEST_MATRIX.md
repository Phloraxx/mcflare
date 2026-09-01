# MCflare Test Matrix

## Proven current gates

| Test | Result |
|---|---|
| Default combined Fabric 26.1-26.2 clean build/tests | PASS |
| Fabric 1.21.11 full remapped build/tests | PASS |
| Fabric 26.2 head-compatibility build/tests | PASS |
| GitHub Fabric 3-row matrix (1.21.11 / 26.1-26.2 / 26.2 head) | PASS |
| NeoForge 1.21.11 build/tests on real JDK 21 + Parchment | PASS |
| NeoForge 26.1-26.2 baseline build/tests | PASS |
| NeoForge 26.2 head-compatibility build/tests | PASS |
| All six exact loader-scoped CI rows reproduced locally | PASS |
| Same Java adapter source compiles on 1.21.11, 26.1 and 26.2 | PASS |
| Actual remapped 1.21.11 JAR loads on standalone Fabric 1.21.11 server | PASS |
| 1.21.11 artifact integrated WSS -> PROXY -> Minecraft Status | PASS |
| One 26.1-baseline JAR loads unchanged on standalone Fabric 26.1 | PASS |
| Same exact 26.x JAR loads unchanged on standalone Fabric 26.2 | PASS |
| Shared root Java adapter contains no Fabric/NeoForge imports | PASS audit |
| NeoForge 1.21.11 dev runtime direct Status + WSS/PROXY Status | PASS |
| Actual NeoForge 1.21.11 production JAR on clean standalone server | PASS |
| NeoForge 1.21.11 production JAR WSS/PROXY TCP4 + TCP6 | PASS |
| One SHA-identical NeoForge 26.1-baseline JAR on clean standalone 26.1 and 26.2 | PASS |
| Combined NeoForge 26.x direct Status + WSS/PROXY Status on both versions | PASS |
| Standard Netty ChannelInitializer lifecycle on Fabric + NeoForge | PASS |
| In-project bounded PROXY-v1 parser; external HAProxy codec removed | PASS |
| Fabric post-refactor direct + WSS/PROXY TCP4/TCP6 regression | PASS |
| Combined 26.x artifact integrated WSS -> PROXY -> Status on both versions | PASS |
| Client redirect bytecode descriptors identical across 1.21.11 / 26.1 / 26.2 | PASS audit |
| Gateway PROXY-v1 unit tests | PASS |
| Integrated Fabric server gateway starts/stops | PASS |
| Direct Minecraft with hybrid PROXY detector installed | PASS |
| Synthetic `CF-Connecting-IP` -> PROXY v1 -> Fabric Status | PASS |
| Synthetic `CF-Connecting-IPv6` -> PROXY TCP6 -> Fabric Status | PASS |
| True Orange `/mcflare` -> standalone gateway -> production Status | PASS |
| Named HTTP Tunnel `/mcflare` -> same standalone gateway -> production Status | PASS |
| True Orange `/mcflare` -> integrated Fabric/PROXY -> dev Status | PASS |
| Named HTTP Tunnel `/mcflare` -> integrated Fabric/PROXY -> dev Status | PASS |
| Actual Java `Rfc6455Client` Status over `/mcflare`, Orange + Tunnel | PASS |
| Final refreshed `25588` gateway: `/mcflare` Orange + Tunnel, legacy paths, and direct production Status | PASS |
| Java-client reconnect stress, 10/10 Orange + 10/10 Tunnel | PASS |
| 40 s pre-data Ping/Pong then Status on same socket, Orange + Tunnel | PASS after lazy-backend fix |
| Gateway does not open Minecraft backend until first binary data | PASS |
| Gateway connection bound / HTTP 503 overload / slot reuse | PASS |
| RFC6455 fragmentation, control interleave, masking and frame bound | PASS + JUnit |
| Legacy Orange `/.well-known/mcflare` side-by-side route | PASS |
| Legacy named Tunnel `/.well-known/mcflare` fallback | PASS |
| Production direct Minecraft Status | PASS |
| PufferPanel after named Tunnel connector restart | PASS — HTTP 200 |
| Quick Tunnel as regression control | INVALID/EXCLUDED — Cloudflare 404/500 before origin |

## Prior full-login evidence

The pre-v1-path checkpoint already proved full Minecraft 26.2 login through true Orange and the named HTTP Tunnel on the legacy path. The new `/mcflare` transport has independently passed the same RFC6455/Status path and Java-client tests, but full authenticated gameplay must be repeated before stable v1.

## Required before stable v1

1. Full online-mode login through `/mcflare` true Orange with the rebuilt player artifact.
2. Full online-mode login through `/mcflare` named Tunnel with the rebuilt player artifact.
3. Ordinary external server Direct Connect/server-list regression with the rebuilt player artifact.
4. Assert restored player IP in login/log/ban-facing Minecraft APIs, not only connection-level Status tests.
5. Live public IPv6 visitor-IP restoration when a suitable client route is available.
6. Sustained gameplay, teleport/chunk bursts and realistic concurrent clients.
7. Quilt same-Fabric-artifact compatibility test.
8. Paper/Purpur gate using native PROXY support; NeoForge server-side 1.21.11/26.1/26.2 gates are complete.

Detailed evidence: `TEST_EVIDENCE_2026-09-01.md` and `BUILD_MATRIX.md`.
