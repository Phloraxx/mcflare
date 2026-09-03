# MCflare

[![CI](https://github.com/Phloraxx/mcflare/actions/workflows/ci.yml/badge.svg)](https://github.com/Phloraxx/mcflare/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/Phloraxx/mcflare?include_prereleases&sort=semver)](https://github.com/Phloraxx/mcflare/releases)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

**Put a Minecraft Java server behind Cloudflare without changing how players join.**

Players install the MCflare mod, add the server with its normal hostname, and use Minecraft's normal **Join Server** button. They do not run `cloudflared`, WARP, a VPN, a separate launcher, or a local proxy.

MCflare carries the Minecraft TCP connection through Cloudflare as a WebSocket and turns it back into an ordinary Minecraft connection at the server. It works with both Orange Cloud proxying and Cloudflare Tunnel, and can preserve the real player IP with PROXY protocol v1.

[Download](https://github.com/Phloraxx/mcflare/releases) · [Install](docs/INSTALLATION.md) · [Deploy](docs/DEPLOYMENT.md) · [FAQ](docs/FAQ.md)

## Install

Download the current release from [GitHub Releases](https://github.com/Phloraxx/mcflare/releases).

| Server / loader | File |
|---|---|
| Fabric / Quilt 1.21.11 | `mcflare-fabric-1.21.11-<version>.jar` |
| Fabric / Quilt 26.1–26.2 | `mcflare-fabric-26.1-26.2-<version>.jar` |
| NeoForge 1.21.11 | `mcflare-neoforge-1.21.11-<version>.jar` |
| NeoForge 26.1–26.2 | `mcflare-neoforge-26.1-26.2-<version>.jar` |
| Paper / Purpur | `mcflare-paper-<version>.jar` |

For Fabric, Quilt, and NeoForge, install the matching JAR on the player and the modded server. Paper/Purpur only needs the server plugin; players use the Fabric/Quilt or NeoForge mod.

Then route `/mcflare` from your public hostname to the MCflare gateway. The exact reverse-proxy or Tunnel setup is covered in [Deployment](docs/DEPLOYMENT.md).

Players connect to the normal address, for example:

```text
play.example.com
```

No WebSocket URL is entered in Minecraft.

## How it works

```text
Minecraft client → Cloudflare → MCflare gateway → Minecraft server
```

Minecraft Java uses raw TCP. Cloudflare's normal HTTP proxy does not proxy that protocol directly, so MCflare carries the same byte stream inside a standard WebSocket. It does not translate gameplay packets or invent a second Minecraft protocol.

If a hostname has already proved that it supports MCflare, the client remembers that and will not silently fall back to a raw origin later. Servers that do not use MCflare continue to use ordinary Minecraft TCP.

For protocol and trust details, see [Architecture](docs/V1_ARCHITECTURE.md), [Wire protocol](docs/V1_PROTOCOL.md), and [Real player IP](docs/REAL_IP.md).

## Platforms

| Platform | Player | Server |
|---|---:|---:|
| Fabric | Yes | Yes |
| Quilt | Yes | Yes |
| NeoForge | Yes | Yes |
| Paper | — | Yes |
| Purpur | — | Yes |

Current release families support Minecraft **1.21.11** and **26.1–26.2**. See [Compatibility](docs/COMPATIBILITY.md) for the exact Java and loader versions tested in CI.

MCflare only carries Minecraft Java's own connection. Separate UDP/TCP sockets used by voice chat, web maps, or other mods are outside its scope.

## Docs and development

- [Installation](docs/INSTALLATION.md)
- [Choose between Orange Cloud and Tunnel](docs/SETUP_CHOICES.md)
- [Deployment examples](docs/DEPLOYMENT.md)
- [Troubleshooting](docs/TROUBLESHOOTING.md)
- [All documentation](docs/README.md)

Questions belong in [Discussions](https://github.com/Phloraxx/mcflare/discussions); reproducible bugs belong in [Issues](https://github.com/Phloraxx/mcflare/issues). See [CONTRIBUTING.md](CONTRIBUTING.md) for builds and pull requests.

MCflare is MIT-licensed. It includes selected MIT-licensed ideas and Minecraft integration work derived from Modflared by Rafael / HttpRafa; see [NOTICE.md](NOTICE.md).

MCflare is independent of Mojang Studios, Microsoft, and Cloudflare.
