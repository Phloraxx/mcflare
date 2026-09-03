# v1.0.0-rc.1 Release Evidence

This page records the final packaged-artifact checks used for the first rebuilt MCflare v1 release candidate. It complements the broader historical acceptance evidence; it is intentionally focused on the exact JARs produced by the hosted release workflow.

## Source artifact set

GitHub Actions dry-run workflow `33667757283` built the candidate from commit `d60321873b53d147079648b698813a55f8c039b9` with release version `1.0.0-rc.1`.

The hosted `mcflare-release-bundle` artifact had digest:

```text
sha256:6dca657bd5dac71e5d735f94a73cf1f957b5aeb26fe29b4935b2ec76e9144a02
```

The downloaded bundle contained exactly five JARs plus `SHA256SUMS.txt`, and `sha256sum -c SHA256SUMS.txt` passed for every JAR.

## Exact JAR SHA-256

```text
faeb5ee881c4e48299f6efc022c88daa83268c2b62cdf7869bca9f2a8aada3b8  mcflare-fabric-1.21.11-1.0.0-rc.1.jar
56528899cf1373b039369ad1b8997852001cfb1e6cbbab6cb2b4405a91ce037e  mcflare-fabric-26.1-26.2-1.0.0-rc.1.jar
5bb93a6a9492c6ca264ed7990d2f0e26a7025179b7dd470688d77df4d146cea0  mcflare-neoforge-1.21.11-1.0.0-rc.1.jar
bf84615d9e5ba361bbf36c83ff6e2179cf3ec01555baa48f44a5a6ce1d63b693  mcflare-neoforge-26.1-26.2-1.0.0-rc.1.jar
03ed4a32bf3cc3c3cd45465a7948cd7399752ef7fd487fd8a3807715d871383f  mcflare-paper-1.0.0-rc.1.jar
```

Fabric, NeoForge, and Paper metadata inside those files all advertised `1.0.0-rc.1`. The Fabric artifacts also contained version-matched nested `core` and `gateway` JARs.

## Packaged-JAR runtime smoke

On 2026-09-03 the hosted JARs above were copied into previously validated, isolated loader/server installations on the Oracle ARM64 host. Production Minecraft, the legacy gateway, and the normal v1 gateway were not restarted or replaced.

| Packaged artifact | Runtime | Result |
|---|---|---|
| Fabric 1.21.11 | Minecraft 1.21.11 | direct Status + `/mcflare` Status PASS |
| Fabric 26.1–26.2 | Minecraft 26.1 | direct Status + `/mcflare` Status PASS |
| Fabric 26.1–26.2 | Minecraft 26.2 | direct Status + `/mcflare` Status PASS |
| NeoForge 1.21.11 | Minecraft 1.21.11 | direct Status + `/mcflare` Status PASS |
| NeoForge 26.1–26.2 | Minecraft 26.1 | direct Status + `/mcflare` Status PASS |
| NeoForge 26.1–26.2 | Minecraft 26.2 | direct Status + `/mcflare` Status PASS |
| Paper plugin | Paper 1.21.11 | `/mcflare` + PROXY-v1 Status PASS |
| Paper plugin | Paper 26.1.2 | `/mcflare` + PROXY-v1 Status PASS |
| Paper plugin | Paper 26.2 | `/mcflare` + PROXY-v1 Status PASS |
| Paper plugin | Purpur 1.21.11 | `/mcflare` + PROXY-v1 Status PASS |
| Paper plugin | Purpur 26.1.2 | `/mcflare` + PROXY-v1 Status PASS |
| Paper plugin | Purpur 26.2 | `/mcflare` + PROXY-v1 Status PASS |

The local WebSocket probe performed a real RFC6455 upgrade on `/mcflare`, required the exact `mcflare.v1` subprotocol, sent the ordinary Minecraft Status byte stream as a masked binary frame, and parsed the Minecraft Status response returned by the gateway.

Paper/Purpur were configured with native HAProxy PROXY support. Those smoke runs supplied the reserved documentation address `198.51.100.42` as synthetic `CF-Connecting-IP`, causing the gateway to exercise the same PROXY-v1 handoff used with trusted Cloudflare ingress. No real player address was used.

The NeoForge ARM/Linux servers emitted the already-known non-fatal Netty kqueue/Log4j diagnostic while starting; every tested server still reached `Done`, opened the MCflare gateway, and passed Status.

## Scope

This release smoke focuses on exact packaged loader/server artifacts and their gateway path. Earlier acceptance evidence covers graphical Fabric clients, full LOGIN → CONFIGURATION → GAME transport, IPv4/IPv6 restoration, long sessions, concurrency/churn, failure cleanup, and public Orange/named-Tunnel delivery. Release packaging changes the embedded version through `release_version`; it does not introduce a different transport implementation.

The release candidate therefore has both broad transport acceptance and a final exact-package smoke gate without requiring production changes.
