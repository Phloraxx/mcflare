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

Implemented/proven on Fabric 26.2:

- read `CF-Connecting-IPv6` then `CF-Connecting-IP`;
- optional HAProxy PROXY v1 emission;
- nonzero opaque source-port workaround for Netty decoder interoperability;
- same-machine PROXY detector/decoder on Fabric server;
- apply decoded source address to Minecraft `Connection.address`;
- gateway metadata logs indicate presence, not raw visitor IP.

Remaining: login/log/API-level assertion that downstream ban/logging surfaces expose the restored IP; IPv6 live test when an IPv6 client path is available.

## Gate 3 - one Fabric artifact

Implemented/proven:

- Fabric metadata changed to `environment: "*"`;
- client mixins remain client-only;
- server mixins are dedicated-server scoped;
- same artifact starts a local gateway on dedicated server;
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

Order:

1. Fabric 26.2 release-quality proof.
2. Quilt compatibility test using the same Fabric artifact.
3. NeoForge current release adapter.
4. Paper/Purpur server integration using native PROXY protocol.
5. Demand-driven older Minecraft/Forge targets.

Never fork RFC6455/discovery/gateway logic per loader.

## Gate 8 - operational hardening

- Document Orange reverse-proxy snippets for Traefik, Caddy and Nginx.
- Document named Tunnel ingress example.
- Optional Authenticated Origin Pulls/firewall guidance for Orange.
- Keep gateway connection/header/frame bounds.
- Avoid browser Challenge/Access login on `/mcflare`.
- Log CF-Ray, route type only in infrastructure docs, duration, close reason, and capacity events; avoid unnecessary raw player-IP logging.

## Gate 9 - CI/release packaging

Use one workflow with a matrix rather than one workflow file per Minecraft version. Produce loader/version artifacts from shared modules. Verify nested core/gateway/Netty dependency packaging by inspecting the built Fabric JAR and by a clean-server runtime test.

## Definition of v1 done

A new administrator can follow either the Orange or Tunnel guide without modifying MCflare source; a player installs one appropriate client artifact and enters a normal hostname; real IP is preserved on supported server platforms; ordinary Minecraft servers still work; and the protocol specification fits on one page without Cloudflare-specific wire messages.
