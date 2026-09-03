# MCflare

[![CI](https://github.com/Phloraxx/mcflare/actions/workflows/ci.yml/badge.svg)](https://github.com/Phloraxx/mcflare/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/Phloraxx/mcflare?include_prereleases&sort=semver)](https://github.com/Phloraxx/mcflare/releases)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

Put a Minecraft Java server behind Cloudflare while players keep using a normal Minecraft address.

Players install the matching MCflare mod and join the server as usual, for example `play.example.com`. They do not need `cloudflared`, WARP, a VPN, a custom launcher, or a local proxy.

[Download](https://github.com/Phloraxx/mcflare/releases) · [Installation](docs/INSTALLATION.md) · [Deployment](docs/DEPLOYMENT.md) · [FAQ](docs/FAQ.md)

## Install

Download the current release from [GitHub Releases](https://github.com/Phloraxx/mcflare/releases).

| Server / loader | File |
|---|---|
| Fabric / Quilt 1.21.11 | `mcflare-fabric-1.21.11-<version>.jar` |
| Fabric / Quilt 26.1–26.2 | `mcflare-fabric-26.1-26.2-<version>.jar` |
| NeoForge 1.21.11 | `mcflare-neoforge-1.21.11-<version>.jar` |
| NeoForge 26.1–26.2 | `mcflare-neoforge-26.1-26.2-<version>.jar` |
| Paper / Purpur | `mcflare-paper-<version>.jar` |

For Fabric, Quilt, and NeoForge, use the matching JAR on the player and the modded server. Paper/Purpur uses the server plugin; players use the Fabric/Quilt or NeoForge mod.

On the server side, route `/mcflare` from the public hostname to the MCflare gateway. [Deployment](docs/DEPLOYMENT.md) has working examples for reverse proxies and Cloudflare Tunnel.

## Supported platforms

| Platform | Player | Server |
|---|---:|---:|
| Fabric | Yes | Yes |
| Quilt | Yes | Yes |
| NeoForge | Yes | Yes |
| Paper | — | Yes |
| Purpur | — | Yes |

Current release families support Minecraft **1.21.11** and **26.1–26.2**. See [Compatibility](docs/COMPATIBILITY.md) for the exact Java and loader versions tested in CI.

## How it works

```text
Minecraft client → Cloudflare → MCflare gateway → Minecraft server
```

MCflare carries Minecraft Java's existing TCP byte stream through a WebSocket and turns it back into a normal Minecraft connection at the server. It works with either Cloudflare's Orange Cloud proxy or a named Cloudflare Tunnel.

Real player IP forwarding is supported through trusted Cloudflare visitor metadata and PROXY protocol v1. The protocol, trust model, and downgrade behavior are documented in [Architecture](docs/V1_ARCHITECTURE.md), [Wire protocol](docs/V1_PROTOCOL.md), and [Real player IP](docs/REAL_IP.md).

MCflare only carries Minecraft Java's own connection. Voice chat, web maps, and other separate sockets need their own path.

## Documentation

- [Installation](docs/INSTALLATION.md)
- [Choose between Orange Cloud and Tunnel](docs/SETUP_CHOICES.md)
- [Deployment examples](docs/DEPLOYMENT.md)
- [Troubleshooting](docs/TROUBLESHOOTING.md)
- [All documentation](docs/README.md)

Questions belong in [Discussions](https://github.com/Phloraxx/mcflare/discussions); reproducible bugs belong in [Issues](https://github.com/Phloraxx/mcflare/issues). See [CONTRIBUTING.md](CONTRIBUTING.md) for builds and pull requests.

MCflare is MIT-licensed. It includes selected MIT-licensed ideas and Minecraft integration work derived from Modflared by Rafael / HttpRafa; see [NOTICE.md](NOTICE.md).

MCflare is independent of Mojang Studios, Microsoft, and Cloudflare.
