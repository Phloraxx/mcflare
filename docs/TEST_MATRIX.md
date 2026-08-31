# MCflare Test Matrix

## Proven current baseline

| Test | Result |
|---|---|
| Fabric 26.2 protected full login | PASS |
| Ordinary server direct fallback | PASS |
| Java-8 core TLS/WSS probe against Cloudflare | PASS |
| `mcflare.v1` WebSocket subprotocol discovery | PASS |
| Discovery WebSocket reused for full Minecraft login | PASS; one gateway upgrade for join |
| True Orange-cloud WSS path with no Tunnel ingress | PASS |
| True Orange-cloud full Minecraft 26.2 login | PASS |
| True Orange-cloud path on real Temurin Java 8 | PASS |
| Named Tunnel HTTP/WSS full login | PASS; optional deployment control |
| Gateway 256-slot overload rejects next connection with 503 | PASS |
| WebSocket fragmentation/validation hardening | PASS |
| Fail-closed protected carrier behavior | PASS |
| MCflare installed alongside SVC with no MCflare voice plugin | PASS |

## 2026-08-31 direct vs Orange vs Tunnel benchmark

Same Mac, same Oracle backend/gateway, 15 samples per route:

| Path | Setup median | Minecraft RTT median | RTT p95 |
|---|---:|---:|---:|
| Direct Oracle WSS | 228 ms | 73 ms | 286 ms |
| True Orange (`hooks.ieeesahrdaya.com`) | 505 ms | 148 ms | 409 ms |
| Named Tunnel (`mcflare2-test.mulearnscet.in`) | 502 ms | 174 ms | 355 ms |

`hooks.ieeesahrdaya.com` was verified absent from every running `cloudflared` ingress before use. Earlier `payment.mulearnscet.in` and `aegissafety.co.in` measurements are excluded because both were later confirmed Tunnel-backed.

## Current scope gates

1. Clean build/tests after each transport change.
2. Online-mode Minecraft gate.
3. Sustained Orange gameplay/jitter/chunk-load benchmark.
4. Origin firewall/Full-strict production hardening.
5. Forge/NeoForge/legacy adapter gates after the transport is frozen.

Mods using Minecraft custom payloads/plugin messages share the Minecraft connection and require no MCflare adapter. Mods opening separate TCP/UDP sockets are outside MCflare's scope.
