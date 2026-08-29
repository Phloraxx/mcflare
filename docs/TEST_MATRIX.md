# MCflare test matrix

## Proven gates

| Gate | Result |
|---|---|
| Real Minecraft status over WSS -> Cloudflare -> TCP | PASS |
| Full Minecraft 26.2 login through Basic mode | PASS |
| Player path with no `cloudflared` subprocess | PASS |
| Ordinary direct server fallback | PASS |
| Java-8 `core` against a live Cloudflare endpoint | PASS |
| Enhanced HTTP/WSS gateway status | PASS |
| Enhanced full Minecraft login | PASS |
| `CF-Connecting-IP` / `CF-Ray` presence at Enhanced gateway | PASS |
| MCF1 capability negotiation | PASS |
| MCF1 UDP round trip: 1..8192 bytes | PASS |
| Enhanced gateway killed during a game | clean disconnect observed |
| Edge restarted then route reused | PASS at status layer; repeat client join in regression suite |

Current voice-like WSS datagram benchmark on the temporary Quick Tunnel: 60 x 200-byte request/reply packets averaged about 155 ms RTT, p95 about 177 ms. This is a transport measurement, not a voice-quality guarantee.

## Automated tests

`core` currently tests:

- MCF1 preamble/service encoding and validation
- Minecraft status-handshake encoding, including protocol, hostname and port

CI runs `./gradlew --no-daemon clean build`.

## Version/loader targets

Support is claimed only after a real build plus connection test.

| Family | Target | State |
|---|---|---|
| Current | Fabric 26.2 | proven |
| Current | Quilt using Fabric-compatible artifact | pending |
| Current | NeoForge 26.2 | pending |
| Current | Forge 26.2 | pending |
| Modern LTS-style modpacks | Fabric/Forge 1.20.1 | pending |
| Legacy | Forge 1.12.2 | next legacy proof |
| Legacy | Forge 1.8.9 | pending |

The Java-8 core is already compatible with the runtime floor needed by the legacy targets; the remaining work is loader/version-specific connection hooks.

## Mod compatibility policy

1. Mods using Minecraft custom payload/plugin channels require no adapter.
2. Separate TCP services use `OPEN_STREAM`.
3. Separate UDP services use `OPEN_DATAGRAM` or a future realtime transport.
4. Prefer a mod's official socket/add-on API over mixins or global packet interception.
5. Simple Voice Chat is the first external-socket integration target; Plasmo Voice follows after the generic path is validated.
