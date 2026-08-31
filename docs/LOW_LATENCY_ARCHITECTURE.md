# MCflare Low-Latency Architecture

Status: current target architecture after the Minecraft-only simplification and true Orange-cloud validation on 2026-08-31.

## Goal

MCflare has one job: carry the normal Minecraft Java TCP byte stream over a standard WebSocket. Anything already inside Minecraft's connection works transparently. Separate TCP/UDP/HTTP connections opened by mods are outside MCflare's scope.

The latency goal is not a literal zero added RTT; it is to make MCflare's own processing overhead negligible and avoid redundant network handshakes or proxy layers.

## Preferred path

```text
Minecraft -> loopback carrier -> WSS :443 -> Cloudflare Orange -> reverse proxy -> MCflare Gateway -> Minecraft TCP
```

Cloudflare Tunnel is not a code feature. An admin who cannot expose an HTTPS origin may optionally place the exact same gateway behind a Tunnel, but client/gateway behavior is unchanged.

## Current wire behavior

The client opens:

```text
wss://play.example.com/.well-known/mcflare
Sec-WebSocket-Protocol: mcflare.v1
```

A real gateway must echo `mcflare.v1` in its `101 Switching Protocols` response. That successful WebSocket is retained as the gameplay transport; discovery does not open a second connection.

There is no MCF1 control protocol, capability JSON, service multiplexer, UDP framing, or Minecraft packet translation.

## Client core

The dependency-free Java-8 core contains four concepts:

- `Rfc6455Client` — TLS + RFC6455 transport.
- `McflareProtocol` — path and subprotocol constants.
- `RouteResolver` — direct-vs-MCflare route selection and cache policy.
- `LoopbackCarrier` — presents a local TCP socket to Minecraft and copies bytes to/from WSS.

## Route selection

For an unknown hostname, MCflare races a secure WSS attempt against ordinary Minecraft reachability. A successful `mcflare.v1` upgrade wins immediately. If ordinary TCP finishes first, MCflare gets a short secure-preference window before direct is selected.

Positive MCflare results are cached longer than negative results. A cached protected server always fails closed: if a later WSS connection fails, MCflare does not silently fall back to a potentially exposed origin.

Minecraft remains authoritative for DNS/SRV resolution of ordinary direct servers. Loader adapters only provide the logical hostname/port and Minecraft-resolved address.

`Rfc6455Client` races resolved origin/Cloudflare addresses with a short stagger instead of trusting the first DNS result. This was added after one Cloudflare anycast IPv4 address on the test network timed out while another connected immediately.

## Why the loopback carrier stays

Minecraft still sees a normal TCP socket. The localhost hop costs essentially nothing compared with WAN RTT and avoids replacing Minecraft's Netty transport separately for every version/loader family.

A Minecraft `Connection` directly owns its `LoopbackCarrier`. Disconnect/setup failure closes it; there is no global tunnel/carrier registry.

## Gateway

The gateway has one responsibility:

1. accept HTTP/WebSocket on `/.well-known/mcflare`;
2. require `mcflare.v1`;
3. open one configured Minecraft TCP backend;
4. copy bytes in both directions;
5. enforce handshake/frame/connection bounds.

It does not parse Minecraft packets, choose arbitrary destinations, proxy UDP, or multiplex external mod services.

Keep the blocking implementation while it remains easy to audit. Make the connection ceiling configurable before large-server deployment; do not add Netty or another framework without measured need.

## Orange-cloud deployment

Preferred production layout:

```text
Cloudflare Orange :443 -> existing TLS reverse proxy -> MCflare Gateway -> 127.0.0.1:25565
```

Use a dedicated proxied hostname, Full (strict) origin TLS where possible, and keep public Minecraft TCP closed. Restrict origin access to Cloudflare/reverse-proxy traffic where operationally practical.

## 2026-08-31 latency benchmark

The valid same-client/same-backend 15-sample comparison was:

| Path | Setup median | Minecraft RTT median | RTT p95 |
|---|---:|---:|---:|
| Direct Oracle WSS (`aegis-safety-preview-144-24-114-90.sslip.io`) | 228 ms | 73 ms | 286 ms |
| True Orange (`hooks.ieeesahrdaya.com`, no cloudflared ingress) | 505 ms | 148 ms | 409 ms |
| Named Tunnel (`mcflare2-test.mulearnscet.in`) | 502 ms | 174 ms | 355 ms |

True Orange improved median gameplay RTT by about 26 ms versus the named Tunnel in this run. Direct remained about 75 ms faster than Orange, so Cloudflare edge/origin routing is still the dominant extra latency on this test network.

Earlier measurements using `payment.mulearnscet.in` and `aegissafety.co.in` are invalid as Orange measurements: both hostnames were later confirmed in local `cloudflared` ingress configurations and are Tunnel-backed.

The Orange path was additionally proven with a full Minecraft 26.2 login; the real Oracle server logged `Phlo joined the game`. The same Orange WSS path also succeeded under a real Temurin Java 8 runtime.

## Interpreting "no lag"

MCflare's local work is byte copying plus small WebSocket framing; there is no JSON/base64/compression/protocol translation on the gameplay stream. The remaining large variable is network routing through Cloudflare.

Do not rewrite the loopback carrier or gateway to chase tens of milliseconds that are demonstrably outside the process. Future latency work should measure Cloudflare PoP/origin routing, ISP variation, sustained gameplay jitter, and chunk/teleport bursts.

Argo should not be assumed to solve this path; Cloudflare currently documents WebSockets as incompatible with Argo Smart Routing.

## Optional Tunnel deployment

Tunnel is strictly an infrastructure fallback for CGNAT, no-public-ingress, or zero-inbound-origin environments:

```text
Cloudflare -> cloudflared -> same MCflare Gateway -> Minecraft
```

There is no Tunnel-specific client code, gateway code, wire protocol, discovery mode, or configuration state inside MCflare.

## Out of scope

Separate sockets opened by mods are not transported by MCflare. Simple Voice Chat therefore uses its own native UDP service. Historical SVC/MCF1/datagram experiments remain in `PROJECT_KNOWLEDGE.md` only as superseded research evidence.
