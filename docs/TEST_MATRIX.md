# MCflare Test Matrix

## Proven current gates

| Test | Result |
|---|---|
| Clean Java-25 Gradle build on Oracle reconstruction | PASS — 19 tasks |
| Gateway PROXY-v1 unit tests | PASS |
| Fabric 26.2 dual-side artifact loads on dedicated server | PASS |
| Integrated server gateway starts/stops | PASS |
| Direct dev Minecraft with PROXY detector installed | PASS |
| Synthetic `CF-Connecting-IP` -> PROXY v1 -> Fabric Status | PASS |
| True Orange `/mcflare` -> standalone gateway -> production Status | PASS |
| Named HTTP Tunnel `/mcflare` -> same standalone gateway -> production Status | PASS |
| True Orange `/mcflare` -> integrated Fabric/PROXY -> dev Status | PASS |
| Named HTTP Tunnel `/mcflare` -> integrated Fabric/PROXY -> dev Status | PASS |
| Actual Java `Rfc6455Client` Status over `/mcflare` through Orange and named Tunnel | PASS |
| Legacy Orange `/.well-known/mcflare` side-by-side route | PASS |
| Legacy named Tunnel `/.well-known/mcflare` fallback | PASS |
| Production direct Minecraft Status | PASS |
| PufferPanel route after named connector restart | PASS — HTTP 200 |
| Quick Tunnel as regression control | INVALID/EXCLUDED — edge 404/500 before origin despite healthy connector |

## Already proven in prior checkpoint

- Prepared discovery WebSocket reuse.
- True Orange full Minecraft 26.2 login on old path.
- Named HTTP Tunnel full login on old path.
- Java-8 core WSS runtime.
- Gateway capacity limit and RFC6455 validation hardening.
- Ordinary server direct fallback with prior client artifact.

## Required before stable v1

1. Clean final build after all documentation/source edits.
2. Full online-mode Fabric 26.2 login through `/mcflare` true Orange.
3. Full online-mode Fabric 26.2 login through `/mcflare` named Tunnel.
4. Ordinary direct server regression with the rebuilt dual-side client artifact.
5. Verify restored player IP in server login/log/ban-facing API, not only Status transport.
6. Live IPv6 visitor-IP path when available.
7. Sustained gameplay, teleport/chunk bursts and multiple concurrent clients.
8. Artifact-content inspection and clean-server install proof.
9. Quilt same-artifact compatibility test.
10. NeoForge/Paper gates only after Fabric v1 is stable.

Detailed evidence: `TEST_EVIDENCE_2026-09-01.md`.
| Java client reconnect stress: Orange 10/10 + Tunnel 10/10 | PASS |
| 40 s pre-data WebSocket Ping/Pong then Minecraft Status, Orange + Tunnel | PASS after lazy-backend fix |
| Gateway capacity bound and HTTP 503 overload behavior | PASS |
| RFC6455 fragmentation/control/masking/frame-bound tests | PASS + JUnit |
| IPv6 gateway + Fabric PROXY TCP6 synthetic path | PASS; public-IPv6 visitor still OPEN |
