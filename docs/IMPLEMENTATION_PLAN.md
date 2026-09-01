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

Release action: decide/register final IANA WebSocket subprotocol identifier before stable v1.

## Gate 2 - real IP

Implemented/proven on the shared Fabric/NeoForge adapter (runtime-tested on both loaders at 1.21.11, 26.1 and 26.2):

- read `CF-Connecting-IPv6` then `CF-Connecting-IP`;
- optional HAProxy PROXY v1 emission;
- opaque nonzero ingress source-port value because Cloudflare does not expose the original player TCP source port;
- bounded in-project standard PROXY-v1 TCP4/TCP6 parser; no separate Netty HAProxy codec dependency;
- loopback-only PROXY detector/parser on Fabric and NeoForge servers;
- apply parsed source address to Minecraft `Connection.address`;
- gateway metadata logs indicate presence, not raw visitor IP.

Proven on real Fabric 26.1 world joins through both true Orange and named Tunnel: Minecraft login logs exposed `144.24.114.90`, matching the Oracle client host public IPv4. A subsequent true-Orange acceptance used Minecraft's native `ban-ip 144.24.114.90`: the connected player was immediately kicked and a fresh real-client reconnect was rejected as IP-banned with the restored address. Live public-IPv6 visitor propagation is now also proven: an external IPv6 client matched the gateway's `PROXY TCP6` source by normalized hash, and a second forced-IPv6 request returned a real Fabric 26.1 Status response through the integrated parser. Platform-specific moderation integrations beyond Minecraft's native ban list remain optional expansion.

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

Before commit/cutover:

- legacy `/.well-known/mcflare` test route still works while side-by-side migration rules exist;
- old named Tunnel fallback route still works;
- PufferPanel hostname remains healthy after connector restart;
- direct Minecraft Status to backend remains healthy;
- clean Gradle build/tests pass from a clean worktree;
- no live production Minecraft listener/process is replaced.

## Gate 6 - full gameplay

Current state:

- full rebuilt Fabric 26.1 client login/world join through true Orange `/mcflare`: PASS;
- full rebuilt Fabric 26.1 client login/world join through named Tunnel `/mcflare`: PASS;
- Minecraft login log real-IP restoration on both paths: PASS;
- true-Orange long-lived transport session: PASS — one real Fabric 26.1 client stayed on one WSS/login session for 31m27s until deliberate shutdown;
- fresh-chunk/teleport burst stability: PASS — seven high-altitude distant teleports over 5m07s, with repeated multi-second world-generation stalls and no MCflare reconnect/disconnect;
- realistic real-client concurrency: PASS — three simultaneous Fabric 26.1 clients (two Orange, one named Tunnel), three WSS/backend streams, separate-region chunk loads, and a named-Tunnel client replacement while the two Orange sessions stayed connected;
- named-Tunnel local connector restart recovery: PASS — an in-world real client disconnected cleanly when the test `cloudflared` connector restarted, all gateway/backend sockets closed, and a fresh real client rejoined after connector recovery;
- the acceptance server was offline-mode, so authenticated Mojang online-mode login remains unproven.

Remaining before stable release:

- online-mode/authenticated client proof if required for release acceptance;
- 30+ minute active-gameplay session with latency/jitter characterization;
- higher-scale connection-churn/ceiling characterization beyond the proven three-real-client scenario;
- actual Cloudflare-edge/network interruption behavior remains distinct from the proven local `cloudflared` connector restart;

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

1. Ordinary external-server regression and, if required, authenticated online-mode proof with the rebuilt client.
2. Ban/moderation API visibility for the already-proven restored login address.
3. 30+ minute active gameplay/jitter characterization, higher-scale load/churn characterization, actual Cloudflare-edge/network interruption behavior and public IPv6 testing.
4. Optional real-client runtime expansion to Quilt/NeoForge, then demand-driven older Minecraft/Forge targets.

Never fork RFC6455/discovery/gateway logic per loader.

## Gate 8 - operational hardening

- Document Orange reverse-proxy snippets for Traefik, Caddy and Nginx.
- Document named Tunnel ingress example.
- Optional Authenticated Origin Pulls/firewall guidance for Orange.
- Keep gateway connection/header/frame bounds.
- Avoid browser Challenge/Access login on `/mcflare`.
- Log CF-Ray, route type only in infrastructure docs, duration, close reason, and capacity events; avoid unnecessary raw player-IP logging.

## Gate 9 - CI/release packaging

Use one workflow with loader-scoped matrices rather than one workflow file per Minecraft version. CI has six Fabric/NeoForge compatibility rows plus one Paper/Purpur plugin build on Java 21. Quilt is covered by runtime reuse of the Fabric artifacts rather than another build job or module. Produce loader/version artifacts from shared modules. Use explicit project task paths (`:build`, `:neoforge:build`, `:runServer`, `:neoforge:runServer`) so Gradle task-name matching does not execute both loader projects unintentionally.

## Definition of v1 done

A new administrator can follow either the Orange or Tunnel guide without modifying MCflare source; a player installs one appropriate client artifact and enters a normal hostname; real IP is preserved on supported server platforms; ordinary Minecraft servers still work; and the protocol specification fits on one page without Cloudflare-specific wire messages.
