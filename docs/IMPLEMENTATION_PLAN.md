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

Remaining: login/log/API-level assertion that downstream ban/logging surfaces expose the restored IP; IPv6 live test when an IPv6 client path is available.

## Gate 3 - dual-side loader artifacts

Implemented/proven:

- Fabric metadata uses `environment: "*"`;
- Fabric and NeoForge each package the same shared Minecraft adapter source for client + dedicated server;
- client mixins remain client-only and server mixins are dedicated-server scoped;
- NeoForge-specific Java is only a tiny `@Mod` marker;
- the same loader artifact starts a local gateway on dedicated server;
- local bind failure is non-fatal to Minecraft;
- generated `config/mcflare.properties` controls enable/listen/max-connections.

Remaining: ordinary direct server regression using the rebuilt artifact on a real client and full protected login through `/mcflare`.

## Gate 4 - Orange and Tunnel equivalence

Proven 2026-09-01:

- true Orange `/mcflare` -> standalone gateway -> production Minecraft Status;
- named HTTP Tunnel `/mcflare` -> same standalone gateway -> production Minecraft Status;
- true Orange `/mcflare` -> integrated Fabric gateway -> PROXY -> dev Minecraft Status;
- named HTTP Tunnel `/mcflare` -> same integrated Fabric gateway -> PROXY -> dev Minecraft Status;
- both delivery modes supplied Cloudflare IP/Ray headers to the same gateway implementation.

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

Required before stable release:

- full online-mode 26.2 login through true Orange `/mcflare`;
- full online-mode 26.2 login through named Tunnel `/mcflare`;
- 30+ minute gameplay sessions;
- chunk-load/teleport burst tests;
- connection churn and concurrent-client ceiling tests;
- Cloudflare edge restart/drop behavior observed as clean Minecraft disconnect/reconnect;
- ordinary server Direct Connect and server-list ping with MCflare installed.

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

1. Real player full-login/gameplay proof for rebuilt Fabric and/or NeoForge client artifacts.
2. Quilt compatibility test using the appropriate Fabric artifact.
3. Paper/Purpur server integration using native PROXY protocol.
4. Demand-driven older Minecraft/Forge targets.

Never fork RFC6455/discovery/gateway logic per loader.

## Gate 8 - operational hardening

- Document Orange reverse-proxy snippets for Traefik, Caddy and Nginx.
- Document named Tunnel ingress example.
- Optional Authenticated Origin Pulls/firewall guidance for Orange.
- Keep gateway connection/header/frame bounds.
- Avoid browser Challenge/Access login on `/mcflare`.
- Log CF-Ray, route type only in infrastructure docs, duration, close reason, and capacity events; avoid unnecessary raw player-IP logging.

## Gate 9 - CI/release packaging

Use one workflow with loader-scoped matrices rather than one workflow file per Minecraft version. Current CI has six rows: Fabric and NeoForge at 1.21.11, combined 26.1-26.2 release baseline, and 26.2 head compatibility. Produce loader/version artifacts from shared modules. Use explicit project task paths (`:build`, `:neoforge:build`, `:runServer`, `:neoforge:runServer`) so Gradle task-name matching does not execute both loader projects unintentionally.

## Definition of v1 done

A new administrator can follow either the Orange or Tunnel guide without modifying MCflare source; a player installs one appropriate client artifact and enters a normal hostname; real IP is preserved on supported server platforms; ordinary Minecraft servers still work; and the protocol specification fits on one page without Cloudflare-specific wire messages.
