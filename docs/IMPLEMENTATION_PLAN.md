# MCflare v1 Implementation and Release Plan

Status date: 2026-09-01.

## Architecture freeze

Do not add new protocol layers while the following model satisfies the requirement:

```text
Minecraft bytes -> WSS /mcflare (mcflare.v1) -> Cloudflare -> same HTTP gateway -> optional PROXY v1 -> Minecraft bytes
```

Orange and Tunnel remain infrastructure-only choices.

## Gate 1 - protocol simplification

Implemented:

- `/mcflare` endpoint;
- `mcflare.v1` WebSocket subprotocol;
- successful discovery WebSocket reused for gameplay;
- no MCF1, HELLO, service multiplexing, UDP, voice integration, or gameplay packet parsing;
- standard WebSocket Ping/Pong;
- address racing across resolved Cloudflare endpoints.

Release action: keep exact `mcflare.v1` documented; IANA registration is optional future formalization, not a stable-v1 blocker.

## Gate 2 - real IP

Implemented/proven on the shared Fabric/NeoForge adapter (runtime-tested on both loaders at 1.21.11, 26.1 and 26.2):

- read `CF-Connecting-IPv6` then `CF-Connecting-IP`;
- optional HAProxy PROXY v1 emission;
- opaque nonzero ingress source-port value because Cloudflare does not expose the original player TCP source port;
- bounded in-project standard PROXY-v1 TCP4/TCP6 parser; no separate Netty HAProxy codec dependency;
- loopback-only PROXY detector/parser on Fabric and NeoForge servers;
- apply parsed source address to Minecraft `Connection.address`;
- gateway metadata logs indicate presence, not raw visitor IP.

Proven on real Fabric 26.1 world joins through both true Orange and named Tunnel: Minecraft login logs exposed `<redacted-public-ip>`, matching the Oracle client host public IPv4. A subsequent true-Orange acceptance used Minecraft's native `ban-ip <redacted-public-ip>`: the connected player was immediately kicked and a fresh real-client reconnect was rejected as IP-banned with the restored address. Live public-IPv6 visitor propagation is now also proven: an external IPv6 client matched the gateway's `PROXY TCP6` source by normalized hash, and a second forced-IPv6 request returned a real Fabric 26.1 Status response through the integrated parser. Platform-specific moderation integrations beyond Minecraft's native ban list remain optional expansion.

## Gate 3 - dual-side loader artifacts

Implemented/proven:

- Fabric metadata uses `environment: "*"`;
- Fabric and NeoForge each package the same shared Minecraft adapter source for client + dedicated server;
- client mixins remain client-only and server mixins are dedicated-server scoped;
- NeoForge-specific Java is only a tiny `@Mod` marker;
- the same loader artifact starts a local gateway on dedicated server;
- local bind failure is non-fatal to Minecraft;
- generated `config/mcflare.properties` controls enable/listen/max-connections.

Real rebuilt Fabric 26.1 client full protected login through `/mcflare` is proven on both true Orange and named Tunnel. The same client artifact joined a clean Fabric 26.1 server with zero MCflare server mods through ordinary direct TCP, and Minecraft's actual graphical Multiplayer screen successfully pinged that same ordinary server and rendered its MOTD/player count/latency. Normal-server Quick Play/Direct Connect and `ServerStatusPinger` compatibility are therefore proven; optional real-client expansion to other loader/version families remains.

## Gate 4 - Orange and Tunnel equivalence

Proven 2026-09-01:

- true Orange `/mcflare` -> standalone gateway -> production Minecraft Status;
- named HTTP Tunnel `/mcflare` -> same standalone gateway -> production Minecraft Status;
- true Orange `/mcflare` -> integrated Fabric gateway -> PROXY -> dev Minecraft Status;
- named HTTP Tunnel `/mcflare` -> same integrated Fabric gateway -> PROXY -> dev Minecraft Status;
- real Fabric 26.1 Minecraft client -> true Orange `/mcflare` -> full login/world join;
- the same real client path -> named Tunnel `/mcflare` -> full login/world join;
- both full joins supplied Cloudflare IP/Ray metadata and restored the same original client IPv4 in Minecraft's login log.

Quick Tunnel was excluded as an acceptance control after a registered/healthy disposable connector repeatedly returned Cloudflare 404/500 before reaching the gateway. Named Tunnel is the canonical Tunnel test.

## Gate 5 - regressions

Migration and current-state regressions are both recorded:

- during side-by-side migration, the legacy Orange and named-Tunnel `/.well-known/mcflare` routes were proven before retirement;
- after retirement, both legacy `/.well-known/mcflare` test endpoints return the expected HTTP 404 instead of silently continuing as alternate v1 routes;
- the retired old `25577` production path returns its expected HTTP 400 response;
- PufferPanel remains healthy after the named-Tunnel connector restart;
- direct Minecraft Status to the backend remains healthy;
- clean Gradle build/tests pass from a clean worktree;
- no live production Minecraft listener/process is replaced by acceptance tests.

## Gate 6 - full gameplay

Current state:

- full rebuilt Fabric 26.1 client login/world join through true Orange `/mcflare`: PASS;
- full rebuilt Fabric 26.1 client login/world join through named Tunnel `/mcflare`: PASS;
- Minecraft login log real-IP restoration on both paths: PASS;
- true-Orange long-lived transport session: PASS — one real Fabric 26.1 client stayed on one WSS/login session for 31m27s until deliberate shutdown;
- fresh-chunk/teleport burst stability: PASS — seven high-altitude distant teleports over 5m07s, with repeated multi-second world-generation stalls and no MCflare reconnect/disconnect;
- realistic real-client concurrency: PASS — three simultaneous Fabric 26.1 clients (two Orange, one named Tunnel), three WSS/backend streams, separate-region chunk loads, and a named-Tunnel client replacement while the two Orange sessions stayed connected;
- higher-scale full-protocol transport/session concurrency: PASS — MCProtocolLib 26.1-1 clients using production `Rfc6455Client` + `LoopbackCarrier` reached real Minecraft GAME state at 16/16 simultaneous sessions for 45 seconds on true Orange and 16/16 on named Tunnel; each path then passed four 16-client churn cohorts (64/64 additional GAME joins), with zero residual disposable sockets;
- named-Tunnel local connector restart recovery: PASS — an in-world real client disconnected cleanly when the test `cloudflared` connector restarted, all gateway/backend sockets closed, and a fresh real client rejoined after connector recovery;
- true-Orange client-network black-hole teardown/recovery: PASS — removing only the live client container network disconnected the player, the hardened gateway closed both backend and WSS sides, no `25585/25587` socket remained, and fresh-client recovery is proven;
- 30-minute active-gameplay latency/jitter characterization: PASS — 1801.449 seconds, 120 cycles/240 probes, 240/240 successful, zero route mismatches, player online throughout, and exactly one gameplay WSS connection per cycle. True Orange measured mean/p50/p95/max 155.58/145.57/187.57/451.25 ms; named Tunnel measured 164.34/154.36/197.85/447.76 ms;
- the acceptance server was offline-mode, so authenticated Mojang online-mode login remains unproven.

Optional future validation/formalization:

- optional public protocol publication/IANA registration if MCflare later needs third-party interoperability formalization;
- optional online-mode/authenticated client proof for Mojang session-authentication validation;
- optional observation of an actual Cloudflare-edge interruption if one occurs naturally; do not substitute a local simulation and label it an edge outage;

## Gate 7 - compatibility expansion

Fabric and NeoForge version-branch reduction is now proven:

- one shared root Java adapter source compiles/runs on 1.21.11, 26.1 and 26.2 for both loaders;
- shared root source contains no Fabric/NeoForge imports;
- 1.21.11 is a separate binary per loader only because of Java-21/mapping/toolchain packaging boundaries;
- one 26.1-baseline Fabric binary runs unchanged on Fabric 26.1 and 26.2;
- one SHA-identical 26.1-baseline NeoForge binary runs unchanged on NeoForge 26.1 and 26.2;
- both loaders pass ordinary direct Status and WSS -> PROXY -> Status;
- CI uses loader-scoped matrices, not version branches/workflow files.

Next order:

1. Build the exact release artifacts through the tag/manual release workflow and smoke-test those packaged JARs.
2. Keep IANA registration, authenticated Mojang online-mode proof, and natural Cloudflare-edge interruption observation as optional future validation/formalization.

Optional expansion after those boundaries: larger graphical/world-generation stress, real-client runtime expansion to Quilt/NeoForge, then demand-driven older Minecraft/Forge targets. The higher-scale MCflare transport/session concurrency gate is already complete.

Never fork RFC6455/discovery/gateway logic per loader.

## Gate 8 - operational hardening

- PASS — concrete Orange reverse-proxy snippets are documented for Traefik, Caddy and NGINX.
- PASS — named Tunnel HTTP ingress remains documented as the same `/mcflare` gateway path.
- Optional Authenticated Origin Pulls/firewall guidance for Orange remains infrastructure hardening, not a wire-protocol requirement.
- PASS — gateway connection/header/frame bounds remain enforced.
- PASS — deployment guidance explicitly avoids browser Challenge/Access login on `/mcflare`.
- PASS — gateway logs sanitized CF-Ray correlation, per-session duration/termination reason and capacity-rejection events while logging only a boolean for forwarded player-IP presence, never the raw forwarded address.

## Gate 9 - CI/release packaging

Use one workflow with loader-scoped matrices rather than one workflow file per Minecraft version. CI has six Fabric/NeoForge compatibility rows plus one Paper/Purpur plugin build on Java 21. Quilt is covered by runtime reuse of the Fabric artifacts rather than another build job or module. Produce loader/version artifacts from shared modules. Use explicit project task paths (`:build`, `:neoforge:build`, `:runServer`, `:neoforge:runServer`) so Gradle task-name matching does not execute both loader projects unintentionally.

## Definition of v1 done

A new administrator can follow either the Orange or Tunnel guide without modifying MCflare source; a player installs one appropriate client artifact and enters a normal hostname; real IP is preserved on supported server platforms; ordinary Minecraft servers still work; and the protocol specification fits on one page without Cloudflare-specific wire messages.
