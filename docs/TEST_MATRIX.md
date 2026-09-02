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
| All seven current CI rows reproduced locally (6 Fabric/NeoForge + Paper) | PASS |
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
| Fabric/NeoForge post-logging-API runtime direct + TCP4/TCP6 WSS/PROXY | PASS |
| Fabric 1.21.11 artifact unchanged on Quilt 1.21.11, direct + TCP4/TCP6 WSS/PROXY | PASS |
| Combined Fabric 26.x artifact unchanged on Quilt 26.1 and 26.2, direct + TCP4/TCP6 WSS/PROXY | PASS |
| Combined 26.x artifact integrated WSS -> PROXY -> Status on both versions | PASS |
| One final Paper plugin SHA on Paper 1.21.11 / 26.1.2 / 26.2, TCP4/TCP6 WSS/PROXY | PASS |
| Same final Paper plugin SHA on Purpur 1.21.11 / 26.1.2 / 26.2, direct + TCP4/TCP6 WSS/PROXY | PASS |
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
| Real Fabric 26.1 client Quick Play -> true Orange `/mcflare` -> world join | PASS |
| Real Fabric 26.1 client Quick Play -> named Tunnel `/mcflare` -> world join | PASS |
| Minecraft login log exposes restored `CF-Connecting-IP` (`144.24.114.90`) on Orange + Tunnel | PASS |
| Native Minecraft `ban-ip` immediately kicks restored-IP player and rejects fresh Orange reconnect | PASS |
| Real MCflare-equipped Fabric 26.1 client -> clean ordinary server with zero MCflare mods | PASS |
| Actual graphical Multiplayer `ServerStatusPinger` -> same ordinary server, visible MOTD/count/latency | PASS |
| Real Fabric 26.1 true-Orange session: 31m27s connected; seven distant fresh-chunk teleports in 5m07s; one WSS upgrade/login and no spontaneous reconnect | PASS |
| 30.02-minute active-gameplay latency/jitter acceptance: 120 cycles, 240/240 probes, zero route mismatches, player continuously online, exactly one gameplay WSS; Orange mean/p50/p95 155.58/145.57/187.57 ms, Tunnel 164.34/154.36/197.85 ms | PASS |
| Three simultaneous real Fabric 26.1 clients (2 Orange + 1 Tunnel), separate-region chunk loads, then Tunnel client replacement while 2 Orange sessions survive | PASS |
| Higher-scale full-protocol 26.1 GAME-state concurrency: 16/16 simultaneous true-Orange sessions held 45 s across MCflare heartbeat interval | PASS |
| Higher-scale full-protocol 26.1 GAME-state concurrency: 16/16 simultaneous named-Tunnel sessions held 45 s across MCflare heartbeat interval | PASS |
| Higher-scale GAME-state churn: four 16-client cohorts per delivery mode, 64/64 joins on Orange and 64/64 on Tunnel, zero residual disposable sockets | PASS |
| Real named-Tunnel client during local `cloudflared` restart: clean disconnect, zero residual WSS/backend sockets, connector recovery, fresh real-client rejoin | PASS |
| Real true-Orange client network black-hole: player disconnect, backend + WSS teardown, gateway slot release, fresh-client recovery | PASS |
| Historical migration checkpoint: refreshed `25588` gateway with `/mcflare` Orange + Tunnel, legacy paths, and direct production Status | PASS before legacy retirement |
| Java-client reconnect stress, 10/10 Orange + 10/10 Tunnel | PASS |
| 40 s pre-data Ping/Pong then Status on same socket, Orange + Tunnel | PASS after lazy-backend fix |
| Gateway does not open Minecraft backend until first binary data | PASS |
| Gateway connection bound / HTTP 503 overload / slot reuse | PASS |
| Gateway operational observability: sanitized CF-Ray, session duration/termination reason, capacity event, no raw forwarded player IP | PASS + JUnit |
| RFC6455 fragmentation, control interleave, masking and frame bound | PASS + JUnit |
| Historical migration checkpoint: legacy Orange `/.well-known/mcflare` side-by-side route | PASS before retirement |
| Historical migration checkpoint: legacy named Tunnel `/.well-known/mcflare` fallback | PASS before retirement |
| Current retired Orange + named-Tunnel `/.well-known/mcflare` endpoints | PASS — expected HTTP 404 |
| Current retired old `25577` production path | PASS — expected HTTP 400 |
| Production direct Minecraft Status | PASS |
| PufferPanel after named Tunnel connector restart | PASS — HTTP 200 |
| Quick Tunnel as regression control | INVALID/EXCLUDED — Cloudflare 404/500 before origin |

## Full-client evidence

The pre-v1-path checkpoint proved Minecraft 26.2 login through true Orange and the named HTTP Tunnel on the legacy path. On 2026-09-01 the rebuilt v1 Fabric 26.1 client was then launched as the real Minecraft client under ARM64 Oracle/Xvfb/Mesa llvmpipe and Quick Play joined an isolated Fabric server through `/mcflare` on both true Orange and the named Tunnel. In both cases the server reached `joined the game` and logged the restored visitor IPv4 `144.24.114.90`, matching the Oracle client host's public IPv4. This closes the rebuilt-v1 Fabric full-login and server-log IP proof for both ingress modes. The acceptance server was deliberately `online-mode=false`, so Mojang online-mode authentication remains a separate gate.

## Remaining external/release boundaries

1. External public-protocol publication and IANA registration of exact `mcflare.v1` remain before calling the wire protocol standards-complete.
2. Online-mode/authenticated login through `/mcflare` with a real player account remains available as an acceptance proof if stable-v1 release policy requires Mojang session-authentication validation.
3. Actual Cloudflare-edge interruption observation remains distinct from the proven local connector restart and client-network black-hole tests; Cloudflare does not expose a customer control to force an edge-server restart, so this must not be replaced with a misleading simulation.

## Optional expansion

- Real-client runtime expansion to Quilt/NeoForge and larger graphical/world-generation stress remain performance/compatibility work. The higher-scale MCflare transport/session gate itself is complete at 16 simultaneous GAME-state clients plus four 16-client churn cohorts per delivery mode.

Completed release gate: a real external IPv6 client has proven true-Orange visitor-IP restoration as `PROXY TCP6`, followed by a real Fabric 26.1 Status response through the integrated parser.

Detailed evidence: `TEST_EVIDENCE_2026-09-01.md` and `BUILD_MATRIX.md`.
