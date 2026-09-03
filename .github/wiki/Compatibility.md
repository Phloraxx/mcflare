# Compatibility

MCflare keeps the network transport shared, but Minecraft connection hooks and loader packaging are version-sensitive. Releases are therefore published as a small set of tested artifact families.

| Artifact family | Player | Dedicated server | Java |
|---|---:|---:|---:|
| Fabric / Quilt 1.21.11 | Yes | Yes | 21 |
| Fabric / Quilt 26.1–26.2 | Yes | Yes | 25 |
| NeoForge 1.21.11 | Yes | Yes | 21 |
| NeoForge 26.1–26.2 | Yes | Yes | 25 |
| Paper / Purpur plugin | No | Yes | 21 |

Quilt uses the matching Fabric artifact. Paper/Purpur players still use a supported Fabric/Quilt or NeoForge MCflare client mod.

## What has been exercised

The compatibility work includes ordinary server Status, MCflare WSS Status, full LOGIN → CONFIGURATION → GAME transport, ordinary non-MCflare regression behavior, IPv4/IPv6 real-IP handling where applicable, concurrency/lifecycle tests, and release packaging checks.

A real authenticated `online-mode=true` Minecraft 26.2 client using the published `v1.0.0-rc.1` Fabric artifact has joined successfully through both Orange Cloud and a named Tunnel.

For exact loader versions, runtime evidence, and packaging rationale, see [`docs/COMPATIBILITY.md`](https://github.com/Phloraxx/mcflare/blob/main/docs/COMPATIBILITY.md) and [`docs/TEST_MATRIX.md`](https://github.com/Phloraxx/mcflare/blob/main/docs/TEST_MATRIX.md).
