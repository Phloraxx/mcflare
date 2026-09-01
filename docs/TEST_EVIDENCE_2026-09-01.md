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

- authenticated/online-mode login remains unproven; later in this document a real rebuilt Fabric 26.1 offline-mode client full world join through `/mcflare` is proven on both Orange and Tunnel;
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

- Minecraft 1.21.11 / Fabric Loader 0.18.2 / Loom-remap 1.14 / Java 21;
- Minecraft 26.1 / Fabric Loader 0.18.4 / Loom 1.15 / Java 25;
- Minecraft 26.2 / Fabric Loader 0.19.3 / Loom 1.17 / Java 25.

These compatibility builds originally included a version-matched external Netty HAProxy codec during exploration; that dependency was later removed in favor of MCflare's bounded in-project PROXY-v1 parser.

The client-side redirect targets were inspected in the mapped Minecraft bytecode. `Connection.connect`, `Connection.connectToServer`, `ServerAddress.parseString/getHost/getPort`, and the relevant invocation descriptors are identical across all three tested versions.

### Standalone 1.21.11 production artifact

The real remapped `mcflare-fabric-1.21.11` JAR was installed into a clean Fabric 1.21.11 server created with the official Fabric installer. It loaded its nested core/gateway dependencies, started the local MCflare endpoint, and passed a synthetic Cloudflare-header WebSocket -> PROXY v1 -> real Minecraft Status exchange. The later final artifact no longer carries an external HAProxy codec.

Result: `REMAPPED_1_21_11_ARTIFACT_PROXY_STATUS=PASS`.

### One binary for Minecraft 26.1 and 26.2

A single artifact was compiled against the older 26.1 baseline and declared the tested runtime range `>=26.1 <26.3`. The final implementation uses the in-project PROXY-v1 parser rather than an embedded HAProxy codec.

The **same exact JAR file** was then installed without modification into separate clean standalone Fabric servers:

- Fabric 26.1 / Loader 0.18.4: `COMBINED_26X_JAR_ON_26_1=PASS`;
- Fabric 26.2 / Loader 0.19.3: `COMBINED_26X_JAR_ON_26_2=PASS`.

Both servers loaded the same artifact, started the integrated gateway, accepted `CF-Connecting-IP`, emitted PROXY v1, and returned a real Minecraft Status response. This justifies one current `mcflare-fabric-26.1-26.2` release artifact instead of separate 26.1 and 26.2 binaries.

The client-side combined-binary claim is supported by identical redirect descriptors across the two 26.x versions. A later Oracle headless-graphics acceptance in this document proves a real Fabric 26.1 client world join through both Orange and Tunnel; additional loader/version real-client runs remain optional expansion rather than proof of the shared transport.

### CI-matrix parity check

The final three-row GitHub Actions matrix was reproduced locally before commit. The 1.21.11 row used an actual OpenJDK 21 compiler/runtime, while both 26.x rows used OpenJDK 25. All three `clean build` executions passed. A no-argument default build also produced `mcflare-fabric-26.1-26.2-0.1.0-dev.jar` with runtime metadata `minecraft: >=26.1 <26.3`, `java: >=25`, Loader `>=0.18.4`.


## Cross-loader PROXY simplification and NeoForge validation

The previous server adapter depended on Netty's separate HAProxy codec and a Mixin `@Invoker` for `ChannelInitializer.initChannel`. NeoForge runtime testing exposed the invoker as loader-fragile. Both mechanisms were removed.

Current implementation:

- standard HAProxy PROXY protocol v1 remains unchanged on the wire;
- a loader-independent in-project parser handles the bounded 108-byte ASCII PROXY line;
- forwarding IPs are parsed as literals only and never resolved through DNS;
- TCP4 and TCP6 are supported;
- ordinary direct Minecraft bytes bypass the optional PROXY path;
- Minecraft's original child `ChannelInitializer` is installed through Netty's normal pipeline lifecycle rather than a Mixin invoker;
- shared root Minecraft adapter source has no Fabric or NeoForge imports.

Fabric regression after this refactor:

```text
FABRIC_26_1_POST_NETTY_DIRECT=PASS
FABRIC_26_1_POST_NETTY_WSS_PROXY_TCP4=PASS
FABRIC_26_1_POST_NETTY_WSS_PROXY_TCP6=PASS
```

### NeoForge 26.x

The same root adapter source compiled and ran on NeoForge 26.1 and 26.2. Development runtimes passed ordinary direct Status and integrated WSS -> PROXY -> Status on both.

One combined JAR was then built against the 26.1 baseline:

```text
mcflare-neoforge-26.1-26.2-0.1.0-dev.jar
SHA-256: 2db2453f59fc8a3963eb541b4cf6ec02a4880d8579b194b63215d996e6e2b0ae
```

The exact SHA-identical bytes were installed into clean official standalone NeoForge 26.1 and 26.2 server installations.

```text
STANDALONE_COMBINED_NEOFORGE_JAR_ON_26_1_DIRECT=PASS
STANDALONE_COMBINED_NEOFORGE_JAR_ON_26_1_WSS_PROXY=PASS
STANDALONE_COMBINED_NEOFORGE_JAR_ON_26_2_DIRECT=PASS
STANDALONE_COMBINED_NEOFORGE_JAR_ON_26_2_WSS_PROXY=PASS
```

This proves one NeoForge 26.1-26.2 release family is sufficient for the tested server side.

### NeoForge 1.21.11

The same root Java source built with JDK 21, ModDevGradle 2.0.124, NeoForge 21.11.6-beta and Parchment mappings. No 1.21.11-specific Java source was required.

A real production artifact was installed into a clean official standalone NeoForge 1.21.11 server:

```text
mcflare-neoforge-1.21.11-0.1.0-dev.jar
SHA-256: 945da6ea2ae69dd80cc93c7456f794b8588e0bd2c3c5bb7be2b6bede70746e11
```

Results:

```text
STANDALONE_NEOFORGE_1_21_11_ARTIFACT_DIRECT=PASS
STANDALONE_NEOFORGE_1_21_11_ARTIFACT_WSS_PROXY_TCP4=PASS
STANDALONE_NEOFORGE_1_21_11_ARTIFACT_WSS_PROXY_TCP6=PASS
```

An initial TCP6 harness assertion was a test bug: it assumed one `recv(2)` call must return the entire two-byte WebSocket frame header. Exact-length reads showed the server path was correct.

### Six-row CI parity

The final loader-scoped CI design has three Fabric rows and three NeoForge rows: 1.21.11 release, 26.1-baseline combined release, and direct 26.2 head compatibility for each loader. The exact commands were reproduced sequentially on Oracle with real JDK 21/25 toolchains.

```text
ALL_6_LOCAL_CI_ROWS=PASS
```

The earlier Fabric-only three-row matrix was also independently green on GitHub before NeoForge was added.

### Upstream NeoForge/Linux warning

Clean NeoForge standalone servers on this Oracle ARM Linux host emit a Log4j stack trace while Netty probes the unsupported kqueue transport (`Only supported on OSX/BSD`). The servers continue startup normally and MCflare direct/WSS tests pass. This behavior is outside MCflare's removed HAProxy dependency and is recorded as an environment/upstream warning, not an MCflare acceptance failure.

## Final live gateway smoke after cross-loader refactor

The parallel standards-v1 gateway on `10.0.0.18:25588` was restarted using the final shared `core`/`gateway` classes. The production Minecraft listener on `25565` and legacy gateway on `25577` were not restarted or replaced.

The production `Rfc6455Client` then sent a real Minecraft Status request over negotiated `mcflare.v1` WebSockets through both delivery modes:

```text
mcflare-orange-test.mulearnscet.in /mcflare: JAVA_RFC6455_STATUS=PASS
mcflare2-test.mulearnscet.in /mcflare: JAVA_RFC6455_STATUS=PASS
```

The refreshed gateway logged `realIpPresent=true` and `cfRayPresent=true` for both upgrades. Side-by-side legacy and direct regressions also passed:

```text
Orange /.well-known/mcflare: LEGACY_WSS_STATUS=PASS
Tunnel /.well-known/mcflare: LEGACY_WSS_STATUS=PASS
127.0.0.1:25565 direct production Status: PASS
```

After the probes, the legacy `25577` gateway retained its original PID/start time and all three expected listeners (`25565`, `25577`, `25588`) remained healthy.

## Quilt, Paper and Purpur packaging reduction

Quilt Loader 0.30.1 was tested using the existing Fabric artifacts with no Quilt-specific code. The exact Fabric 1.21.11 JAR passed direct plus TCP4/TCP6 WSS->PROXY Status on Quilt 1.21.11. The same exact combined Fabric 26.1-26.2 JAR passed the same three tests on Quilt 26.1 and Quilt 26.2. Result: no Quilt artifact is required.

A `paper/` module was then added as a Java-21 Bukkit/Paper lifecycle wrapper around the shared `McflareGateway`. It contains no NMS, Mixin or Paper networking hooks. Paper/Purpur native `proxies.proxy-protocol: true` consumes MCflare's standard PROXY-v1 header.

After replacing gateway `System.out/err` calls with injectable standard-Java log consumers, the final Paper plugin SHA was:

```text
3af90adb4e485bb666edd84781ee0703131fea49fa5cee3a426f15c52d78b4ba
```

That exact binary passed WSS->PROXY TCP4 and TCP6 Status on Paper 1.21.11, Paper 26.1.2 and Paper 26.2. The same exact binary passed direct Status plus WSS->PROXY TCP4/TCP6 on Purpur 1.21.11, 26.1.2 and 26.2. Paper 26.2 was also rerun after the logging cleanup and no Bukkit `System.out` plugin nag remained.

This reduces the tested server packaging to one Paper/Purpur JAR across all three version families while preserving platform-native source-IP restoration.

## Final seven-row CI and gateway-logging regression

After adding the Paper/Purpur plugin and making `McflareGateway` logging injectable through standard Java `Consumer<String>` sinks, all seven current CI commands were rerun locally with real JDK 21/25 toolchains:

```text
fabric_1_21_11=PASS
fabric_26x_release=PASS
fabric_26_2_head=PASS
neoforge_1_21_11=PASS
neoforge_26x_release=PASS
neoforge_26_2_head=PASS
paper_plugin=PASS
ALL_7_LOCAL_CI_ROWS=PASS
```

The pushed platform checkpoint `7e542e9354948b41f7d9188627d4f4661484c51e` also completed GitHub Actions run `33489597702` successfully. All seven hosted jobs were green: Fabric 1.21.11, Fabric 26.1-26.2 release, Fabric 26.2 head, NeoForge 1.21.11, NeoForge 26.1-26.2 release, NeoForge 26.2 head, and Paper/Purpur plugin.

The logging change was then runtime-tested on isolated current-family development servers. Fabric 26.1 and NeoForge 26.1 each passed ordinary direct Status plus WSS->PROXY TCP4 and TCP6 Status using the shared gateway. The Fabric dev config was restored afterward and the NeoForge dev server was stopped cleanly.

## Final live smoke after Paper/logging integration

After Paper/Purpur integration and the shared gateway logging-sink change, only the parallel standards-v1 gateway on `10.0.0.18:25588` was refreshed from the current classes. The legacy `25577` process remained the original August 31 process and production Minecraft `25565` was not restarted. The current Java `Rfc6455Client` then passed real Minecraft Status through both `mcflare-orange-test.mulearnscet.in/mcflare` and `mcflare2-test.mulearnscet.in/mcflare`; direct production Status also passed.

### Final local release artifact hashes after platform integration

```text
mcflare-fabric-26.1-26.2-0.1.0-dev.jar
6cd2c3042f568b2ee1ad1497df2ce2b63303962edb15bd8038cecb1691e1dc41

mcflare-neoforge-26.1-26.2-0.1.0-dev.jar
144256853262eb75ce3452743092080f0f5bd92a0cf7af1c70c58932f1aa8bf5

mcflare-paper-0.1.0-dev.jar
3af90adb4e485bb666edd84781ee0703131fea49fa5cee3a426f15c52d78b4ba
```

## Real Fabric client world-join acceptance on Oracle

The remaining real-client transport gate was moved onto Oracle rather than waiting for a desktop test host. A disposable ARM64 Ubuntu 24.04 Docker image was built with OpenJDK 25, Xvfb and Mesa. Inside that isolated container `glxinfo` reported direct rendering with `llvmpipe (LLVM 20.1.2, 128 bits)` and OpenGL 4.5, allowing the actual Minecraft 26.1 Fabric client to run without a physical GPU. The test checkout was an archive of commit `7e542e9354948b41f7d9188627d4f4661484c51e`; this Docker/Xvfb stack is test infrastructure only and is not a product dependency.

An isolated Fabric 26.1 server ran offline-mode on `127.0.0.1:25585` with its integrated MCflare gateway temporarily on `10.0.0.18:25587`. Only the test `/mcflare` routes were temporarily pointed from the parallel `25588` gateway to `25587`; production Minecraft `25565` and legacy MCflare `25577` were not replaced. Before client launch, both public hostnames returned the dev server's distinct 125-byte Status response.

The first Quick Play launch was correctly diagnosed as blocked by a brand-new Minecraft profile's onboarding/multiplayer-warning UI, not by MCflare. The isolated profile was then pre-seeded only with the equivalent already-acknowledged first-run flags. The actual Minecraft client, Fabric Loader and MCflare client Mixins then performed the connection.

True Orange result:

```text
Connecting to mcflare-orange-test.mulearnscet.in, 25565
MCFLARE_GATEWAY upgrade realIpPresent=true cfRayPresent=true
Player357[/144.24.114.90:60826] logged in
Player357 joined the game
```

Named Tunnel result:

```text
Connecting to mcflare2-test.mulearnscet.in, 25565
MCFLARE_GATEWAY upgrade realIpPresent=true cfRayPresent=true
Player977[/144.24.114.90:49428] logged in
Player977 joined the game
```

A separate public-IP check on the Oracle client host returned `144.24.114.90`. Therefore both delivery modes restored the actual Cloudflare visitor IPv4 into Minecraft's login address rather than loopback, the private Oracle address or a Cloudflare edge address. Both clients remained in-world until the test containers were deliberately stopped, after which Minecraft recorded normal `Disconnected`/left-game events.

This is stronger than the earlier Status proof: login, configuration and game-phase packets all traversed the real `RouteResolver` -> prepared `Rfc6455Client` -> `LoopbackCarrier` -> Cloudflare -> integrated gateway -> PROXY-v1 -> Minecraft path. The server was deliberately `online-mode=false`, so Mojang account/session authentication is not claimed by this test.

Afterward the Orange and named-Tunnel `/mcflare` routes were restored to `10.0.0.18:25588`, the isolated `25585/25587` server was stopped cleanly, and the named test cloudflared connector alone was restarted. Final restoration checks passed:

```text
/mcflare true Orange -> 25588: JAVA_RFC6455_STATUS=PASS bytes=105
/mcflare named Tunnel -> 25588: JAVA_RFC6455_STATUS=PASS bytes=105
legacy Orange /.well-known/mcflare: PASS bytes=105
legacy Tunnel /.well-known/mcflare: PASS bytes=105
direct production 127.0.0.1:25565 Status: PASS bytes=105
PufferPanel: HTTP 200
```

The legacy `25577` gateway retained PID `3784240` with its August 31 start time; the parallel v1 `25588` gateway remained PID `1320893`. No `25585` or `25587` listener remained after cleanup.

## Ordinary-server client regression

A clean Fabric 26.1 server was copied from the standalone test installation, all MCflare mods were removed (`MOD_COUNT=0`), and it listened only on `127.0.0.1:25586` with MOTD `Ordinary no-MCflare 26.1 regression`. The real Fabric 26.1 client still had MCflare installed and used Quick Play to `ordinary-minecraft.test:25586`.

```text
Player438[/127.0.0.1:33416] logged in
Player438 joined the game
```

The client remained connected until deliberately stopped about a minute later. This proves the rebuilt client does not require MCflare server-side and can choose the ordinary direct Minecraft TCP path when a hostname is not MCflare-enabled. The graphical Multiplayer pinger was tested separately below.

## Native Minecraft IP-ban acceptance through true Orange

The isolated Fabric 26.1 server/gateway was started again on `25585/25587`, and only the true-Orange `/mcflare` test route was temporarily moved from `25588` to `25587`. A real Fabric client joined through Cloudflare and Minecraft logged:

```text
Player393[/144.24.114.90:53422] logged in
Player393 joined the game
```

The server console then executed `ban-ip 144.24.114.90`. Minecraft reported that the ban affected the connected player and immediately disconnected it with `You have been IP banned from this server`. A completely fresh real-client launch through the same Orange hostname was then rejected before joining:

```text
Disconnecting Player44 (/144.24.114.90:42538): Your IP address is banned from this server.
Reason: Banned by an operator.
```

The test address was pardoned (`Unbanned IP 144.24.114.90`) and `banned-ips.json` returned to `[]`. The isolated server shut down cleanly, Orange `/mcflare` was restored to `25588`, and no `25585/25587` listeners remained. Final v1 Orange/Tunnel Status, both legacy WSS paths, direct production Status and PufferPanel HTTP 200 all passed again.

GitHub Actions run `33496826197` for docs checkpoint `548e839ca257a7f579cd48347c0d907254a65d88` also completed with all seven jobs green.

## Graphical Multiplayer `ServerStatusPinger` ordinary-server regression

The same ordinary Fabric 26.1 server remained on `127.0.0.1:25586` with zero MCflare mods. The real MCflare-equipped client was launched without Quick Play and the actual title-screen `Multiplayer` button was clicked under Xvfb. The pre-existing `servers.dat` entry was made visible by changing only its test-profile NBT `hidden` byte from `1` to `0`; the client container received `ordinary-minecraft.test -> 127.0.0.1` as a test-only hosts entry.

After opening Multiplayer, `/proc/net/tcp6` observed the client pinger connection from ephemeral port `B1D4` to `127.0.0.1:25586` (`63F2`) reach `ESTABLISHED`. The captured Minecraft screen rendered `Minecraft Server`, MOTD `Ordinary no-MCflare 26.1 regression`, `0/20`, and green latency bars. Therefore the actual `ServerStatusPinger` hook falls back to ordinary Minecraft TCP correctly; this is independent of the earlier Quick Play/world-join proof.

## Sustained true-Orange gameplay and fresh-chunk burst stability

A real Fabric 26.1 client remained on one true-Orange `/mcflare` gameplay session for 31m27s, from the initial login at 14:10:04 until deliberate client shutdown at 14:41:31. The server logged exactly one MCflare upgrade for the gameplay client, one Minecraft login/join, no spontaneous leave/rejoin, and one normal `Disconnected` event when the test container was stopped. The same WSS-side socket and backend TCP socket remained established during the final health check.

The first exploratory teleport placed the survival player unsafely and it later drowned; that was a test-fixture issue rather than a transport failure. The player was respawned, switched to Spectator mode, and confirmed at 20.0 health before the controlled burst phase.

The controlled burst phase ran for 5m07s, from 14:32:43 through 14:37:50. Seven high-altitude teleports forced fresh-region generation at approximately `(2000,2000)`, `(6000,-6000)`, `(-12000,8000)`, `(24000,24000)`, `(-32000,-16000)`, `(48000,12000)`, and `(-64000,64000)`. The isolated Oracle server reported world-generation stalls of roughly 3.3-6.9 seconds after several jumps, while the client stayed connected and Minecraft continued reporting the player online at 20.0 health.

This proves stability of the existing WSS byte-stream transport under repeated heavy fresh-chunk bursts. It is not a gameplay-latency or jitter benchmark; a 30+ minute actively played session with latency/jitter characterization remains a separate release-quality measurement.

After the run, both test `/mcflare` routes were restored from the temporary integrated gateway on `25587` to the normal parallel gateway on `25588`; only the named test cloudflared connector was restarted. The disposable client exited normally and the isolated `25585/25587` server shut down cleanly. Final regression checks passed: v1 Orange `/mcflare` 105-byte Status, v1 named Tunnel `/mcflare` 105-byte Status, both legacy `/.well-known/mcflare` paths, direct production `127.0.0.1:25565` Status, and PufferPanel HTTP 200. No `25585` or `25587` listener remained; legacy `25577` retained its original August 31 process and `25588` retained its existing process.

## Three-real-client Orange/Tunnel concurrency and churn

A fresh isolated Fabric 26.1 acceptance server ran on `127.0.0.1:25585` with integrated MCflare on `10.0.0.18:25587`. Only the two test `/mcflare` routes were temporarily moved from `25588` to `25587`; both public hostnames first returned the isolated server's distinct 125-byte Status response. Four separate client worktrees were prepared from exact checkpoint `787290be21a4b2d1e09aefa8be35e6f5546bc1f5`, with independent game/build directories and a shared dependency cache.

Three real software-rendered Fabric 26.1 clients were then joined simultaneously: `Player68` via true Orange (`/144.24.114.90:42390`), `Player174` via the named Tunnel (`/144.24.114.90:54570`), and `Player904` via true Orange (`/144.24.114.90:56326`). The gateway showed three concurrent established WSS connections and the backend had three corresponding Minecraft streams. All three players were switched to Spectator and teleported into separate distant regions; Minecraft still reported all three online afterward. The server fell about 21 seconds behind while generating all three regions simultaneously, which demonstrates host/world-generation pressure but caused no MCflare disconnect.

For live churn, only the named-Tunnel client `Player174` was deliberately stopped. Gateway/backend connection counts dropped by exactly one and the two Orange clients remained connected. A fresh named-Tunnel client, `Player106`, then upgraded successfully with Cloudflare metadata, joined as `/144.24.114.90:54672`, and was teleported to another distant region. Minecraft subsequently reported the replacement trio `Player68, Player904, Player106` online; the gateway again held three WSS streams. At the closing check all three remained connected. The three final disconnects were deliberate container shutdowns.

The test intentionally stopped at three simultaneous real clients because the Oracle host's Mesa/llvmpipe clients consumed roughly 2.1-2.5 GiB each and left only about 2-3 GiB available. This is a realistic concurrent-client acceptance proof, not a maximum-capacity benchmark. Gateway capacity bounding/HTTP-503 behavior is covered separately by the synthetic connection-limit test.

Afterward both `/mcflare` routes were restored to `25588`, only the named test cloudflared connector was restarted, all disposable clients were removed, and the isolated server shut down cleanly. Final checks passed: v1 Orange and named-Tunnel `/mcflare` returned 105-byte production Status responses, both legacy WSS paths passed, direct production Minecraft Status passed, PufferPanel returned HTTP 200, and no `25585/25587` listener remained. The original `25577` and `25588` gateway processes were unchanged.

## Named-Tunnel local connector restart and fresh-client recovery

Only the named test `/mcflare` ingress for `mcflare2-test.mulearnscet.in` was temporarily moved from the normal parallel gateway on `25588` to the isolated Fabric gateway on `10.0.0.18:25587`; the true-Orange control remained on `25588`. The Java `Rfc6455Client` probe confirmed the split before the live test: Orange returned the normal 105-byte production Status response while the named Tunnel returned the isolated server's distinct 125-byte Status response.

A real Fabric 26.1 client joined the named Tunnel as `Player66[/144.24.114.90:52604]`, with the gateway reporting both real-IP and Cloudflare-Ray metadata. Immediately before the interruption, one gameplay WSS stream and its backend Minecraft stream were established. At 15:38:11 the local test `cloudflared` container `main-pufferpanel-f33lxy-cloudflared-1` was deliberately restarted; Docker returned from the restart at 15:38:22. The gateway observed a connection reset, Minecraft removed `Player66` at 15:38:21 with `Disconnected`, and the real client surfaced `Client disconnected with reason: Disconnected` at 15:38:27. Post-drop inspection showed zero residual established gateway/backend sockets.

After the connector returned, the real Java WSS Status probe again passed against the isolated named-Tunnel endpoint and PufferPanel returned HTTP 200. A completely fresh real Fabric 26.1 client then joined successfully through the same Tunnel as `Player971[/144.24.114.90:41994]`, again with Cloudflare metadata present. This proves bounded disconnect and fresh-client recovery for a local `cloudflared` connector restart. It must not be described as a Cloudflare-edge outage/restart, because Cloudflare infrastructure itself was not restarted.

The fresh client was deliberately stopped, the named `/mcflare` ingress was restored to `25588`, only the named test connector was restarted to load that restored config, and the isolated `25585/25587` server shut down cleanly. Final regression checks passed: v1 Orange and named-Tunnel `/mcflare` each returned the normal 105-byte Status response, both legacy WSS paths passed, direct production Minecraft Status passed, PufferPanel returned HTTP 200, no `25585/25587` listener remained, and the original `25577` and `25588` gateway processes were unchanged.

## Durable positive-route pin and restart downgrade resistance

The client now persists only successfully proven MCflare routes as normalized `host:logicalPort` entries in `~/.mcflare/known-hosts-v1.txt`. Ordinary/direct outcomes are never written; the negative result remains only a short in-memory cache. A loaded positive pin skips direct probing and uses required WSS, so WSS failure is terminal instead of falling back to raw Minecraft TCP.

A real restart downgrade-resistance test was performed with `mcflare-orange-test.mulearnscet.in:25586`. The first real Fabric client learned the route through true Orange WSS. A completely fresh client process then reused the persisted pin while the hostname resolved to a private ordinary Minecraft server where raw `25586` was reachable and WSS `443` was closed. The client failed in the required-WSS path and the ordinary server recorded zero logins/joins.

```text
REAL_RESTART_DOWNGRADE_REFUSAL=PASS
PIN_SURVIVES_FAILURE=PASS
```

Storage failure behavior was tightened before commit. A non-empty malformed persisted pin file is treated as unsafe and fails closed rather than discarding potentially corrupted prior trust. A newly discovered positive route is not inserted into the trusted in-memory set until its durable append succeeds; an append failure therefore cannot be mistaken for a persistent pin. The already-open WSS is closed if persistence fails.

Focused `:core:test` passed after this hardening, including positive persistence across instances, malformed-store fail-closed behavior, failed-append behavior, and the existing persisted-known-route direct-fallback refusal test. `git diff --check` also passed. The expensive real-client restart test was not repeated because the healthy persisted-pin lookup/fail-closed path exercised by that test was not changed; only exceptional storage-failure branches were tightened.
