# MCflare test matrix

> Detailed evidence and design context live in `PROJECT_KNOWLEDGE.md`.

## Proven gates

| Gate | Result |
|---|---|
| Real Minecraft Status over WSS -> Cloudflare -> TCP | PASS |
| Full Minecraft 26.2 login through Basic-style carrier | PASS |
| Player path with no `cloudflared` subprocess/binary | PASS |
| Ordinary direct server fallback | PASS |
| Ordinary direct regression after fail-closed refactor | PASS; discovery miss ~3 ms in local test |
| Java-8 `core` bytecode build | PASS |
| Real Temurin 8u504 `core` against live Cloudflare endpoint | PASS |
| Enhanced HTTP/WSS gateway Status | PASS |
| Enhanced full Minecraft login | PASS |
| `CF-Connecting-IP` / `CF-Ray` presence at Enhanced gateway | PASS |
| MCF1 capability negotiation | PASS |
| MCF1 UDP round trip: 1, 20, 256, 1200, 4096, 8192 bytes | PASS |
| Hardened RFC6455 client against live Cloudflare | PASS |
| Gateway 256-connection cap / 257th connection | PASS; immediate HTTP 503 |
| Enhanced gateway killed during a game | clean disconnect observed |
| Edge restarted then route reused | PASS at Status layer |
| Fail-closed routing state refactor | build/tests PASS; protected full login PASS |
## Measured observations

- Protected discovery on temporary Quick Tunnels has commonly completed in roughly 0.9-1.3 seconds in recent client tests; this is not an SLA.
- WSS datagram benchmark: 60 x 200-byte request/reply packets averaged ~155 ms RTT, median ~155 ms, p95 ~177 ms, max ~211 ms.
- The latest ordinary direct regression used `127.0.0.1.nip.io:25575`, classified it non-MCflare in ~3 ms, and completed a normal full login.
- The post-hardening protected regression completed discovery, created an in-process carrier, and the real 26.2 test server logged the player joining.

## Automated tests

`core` currently covers:

- MCF1 preamble/service encoding and service-ID validation.
- Minecraft Status-handshake encoding, including protocol, hostname and port.
- Invalid/oversized service identifiers.

CI runs `./gradlew --no-daemon clean build`.
## Version/loader targets

Support is claimed only after a real build plus connection test.

| Family | Target | State |
|---|---|---|
| Current | Fabric 26.2 | proven |
| Current | Quilt using Fabric-compatible artifact | pending |
| Current | NeoForge 26.2 | pending |
| Current | Forge 26.2 | pending |
| Modern modpacks | Fabric/Forge 1.20.1 | pending |
| Legacy | Forge 1.12.2 | next legacy proof |
| Legacy | Forge 1.8.9 | pending |

The Java-8 transport runtime is proven independently. Loader/version-specific hooks remain separate proof gates.
## Pending gates

1. Add a minimal automated test seam that forces protected-route setup failure and asserts the direct destination is never selected. The production state model is already fail-closed; this is a regression-proofing test, not a new architecture.
2. Repeat the complete suite against a named production-style Tunnel instead of only disposable Quick Tunnels.
3. Multi-client concurrency and connection-churn testing.
4. Longer sessions under realistic gameplay/network changes.
5. Simple Voice Chat real client/server adapter over MCF1 datagrams.
6. TURN/UDP voice experiment and loss/jitter comparison against WSS fallback.
7. Velocity/Paper/proxy compatibility tests.
8. Forge 1.12.2 legacy adapter proof.

## Mod compatibility policy

- Mods using Minecraft's own payload/plugin channels need no adapter.
- Separate TCP services use `OPEN_STREAM`.
- Separate UDP services use `OPEN_DATAGRAM` or a future realtime transport.
- Prefer official mod APIs over mixins/global packet interception.
- Simple Voice Chat is the first side-service target; its API dependency is intentionally not part of the hardened transport baseline.