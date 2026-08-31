# MCflare Test Matrix

## Proven current baseline

| Test | Result |
|---|---|
| Fabric 26.2 protected full login | PASS |
| Ordinary server direct fallback | PASS |
| Java-8 core TLS/WSS probe against Cloudflare | PASS |
| Named Tunnel Basic control full login | PASS (historical deployment) |
| Named Tunnel HTTP/WSS gateway full login | PASS |
| Gateway source headers present behind Cloudflare HTTP | PASS |
| Gateway 256-slot overload rejects next connection with 503 | PASS |
| WebSocket fragmentation/validation hardening | PASS |
| Fail-closed protected carrier behavior | PASS by state design + normal regression |
| SVC-over-MCflare experiment | PASS historically; feature retired as out of scope |

## Current scope gates

1. Clean build/tests after Minecraft-only simplification.
2. Full named-gateway Minecraft login with stripped gateway.
3. Orange-cloud WSS Status + full login.
4. Direct vs Orange vs Tunnel RTT/jitter benchmark.
5. Prepared-WebSocket discovery refactor, followed by the same benchmark.
6. Online-mode Minecraft gate.
7. Forge/NeoForge/legacy adapter gates after the transport is frozen.

## Compatibility rule

Mods using Minecraft custom payloads/plugin messages share the Minecraft connection and require no MCflare adapter. Mods opening separate TCP/UDP sockets are outside MCflare's transport scope and must expose/use their own service.
