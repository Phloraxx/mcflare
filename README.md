# MCflare

[![CI](https://github.com/Phloraxx/mcflare/actions/workflows/ci.yml/badge.svg)](https://github.com/Phloraxx/mcflare/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/Phloraxx/mcflare?include_prereleases&sort=semver)](https://github.com/Phloraxx/mcflare/releases)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

**Run a Minecraft Java server through Cloudflare while players still join normally.**

MCflare carries the normal Minecraft connection through Cloudflare as WebSocket traffic. Players still add a normal server address and click **Join Server** — no `cloudflared`, VPN, special launcher, Cloudflare account, or WebSocket URL on the player side.

It is useful when you want Cloudflare in front of a Minecraft server without turning the player experience into a networking setup guide.

> MCflare is an independent open-source project and is not endorsed by Mojang Studios, Microsoft, or Cloudflare.

## Why MCflare?

- **Normal Minecraft UX.** Players use the usual server list and hostname.
- **Cloudflare-compatible transport.** Minecraft traffic can travel through Cloudflare's normal HTTP/WebSocket infrastructure.
- **No player-side tunnel software.** The player only installs the matching MCflare mod.
- **Multiple server platforms.** Fabric, Quilt, NeoForge, Paper and Purpur are supported.
- **Real player IP support.** Trusted Cloudflare visitor metadata can be restored to the Minecraft server with PROXY protocol v1.
- **Normal servers still work normally.** MCflare does not replace ordinary Minecraft TCP for unrelated servers.

## Quick start

1. Download the JAR for your platform from [GitHub Releases](https://github.com/Phloraxx/mcflare/releases).
2. On the **player**, install the matching Fabric/Quilt or NeoForge mod.
3. On the **server**, install the same mod for Fabric/Quilt/NeoForge, or install the MCflare plugin for Paper/Purpur.
4. Route `https://your-server.example/mcflare` through Cloudflare to the MCflare gateway.
5. Give players the normal hostname, such as `play.example.com`.

That is the player-facing flow. The Cloudflare/reverse-proxy work is server-side.

**Detailed setup:** [Installation](docs/INSTALLATION.md) · [Choose a setup](docs/SETUP_CHOICES.md) · [Deployment](docs/DEPLOYMENT.md) · [Troubleshooting](docs/TROUBLESHOOTING.md)

## Which file do I need?

| Your setup | Download |
|---|---|
| Fabric / Quilt, Minecraft 1.21.11 | `mcflare-fabric-1.21.11-<version>.jar` |
| Fabric / Quilt, Minecraft 26.1–26.2 | `mcflare-fabric-26.1-26.2-<version>.jar` |
| NeoForge, Minecraft 1.21.11 | `mcflare-neoforge-1.21.11-<version>.jar` |
| NeoForge, Minecraft 26.1–26.2 | `mcflare-neoforge-26.1-26.2-<version>.jar` |
| Paper / Purpur server | `mcflare-paper-<version>.jar` |

The Fabric and NeoForge JARs are used on both the player and a matching modded server. Paper/Purpur uses the server plugin; players still use Fabric/Quilt or NeoForge.

## How it works

```text
Player → Cloudflare → MCflare gateway → Minecraft server
```

Minecraft Java normally uses raw TCP, which Cloudflare's standard HTTP reverse proxy does not understand. MCflare gives Cloudflare a normal WebSocket connection to proxy and carries the Minecraft byte stream inside it.

There is no second gameplay protocol and MCflare does not decode or rewrite normal Minecraft gameplay packets.

For the exact transport design, see [Architecture](docs/V1_ARCHITECTURE.md) and [Wire protocol](docs/V1_PROTOCOL.md).

## Orange proxy or Cloudflare Tunnel?

Both use the same MCflare protocol.

| Setup | Good fit when... |
|---|---|
| **Cloudflare Orange proxy** | you already expose an HTTPS reverse proxy and want `/mcflare` routed alongside it |
| **Cloudflare Tunnel** | you prefer `cloudflared` on the server side and do not want that WebSocket origin reached directly from the internet |

See [Choose your setup](docs/SETUP_CHOICES.md) before deploying.

## Real player IPs

Because Cloudflare terminates the public WebSocket, the backend would otherwise see the proxy/gateway address. MCflare can pass the trusted visitor address to Minecraft using standard **PROXY protocol v1**, including IPv4 and IPv6.

Only enable this when the gateway receives visitor metadata from infrastructure you trust. See [Real player IP](docs/REAL_IP.md).

## Compatibility

| Platform | Player | Server |
|---|---:|---:|
| Fabric | Yes | Yes |
| Quilt | Yes, using the Fabric artifact | Yes, using the Fabric artifact |
| NeoForge | Yes | Yes |
| Paper | — | Yes |
| Purpur | — | Yes |

Current release families cover Minecraft **1.21.11** and **26.1–26.2**. See [Compatibility](docs/COMPATIBILITY.md) for exact Java versions and tested combinations.

## Current release

The first rebuilt release candidate is **[v1.0.0-rc.1](https://github.com/Phloraxx/mcflare/releases/tag/v1.0.0-rc.1)**.

GitHub Releases is the authoritative source for binaries. Every release includes `SHA256SUMS.txt` so downloads can be verified.

## What MCflare is not

MCflare is not a VPN, generic TCP/UDP tunnel, voice-chat tunnel, Cloudflare account manager, or hosted relay service. It only carries Minecraft Java's own connection.

If a WebSocket connection dies, Minecraft disconnects normally and the player reconnects with a fresh session.

## Documentation

- **Getting started:** [Installation](docs/INSTALLATION.md)
- **Which deployment should I use?** [Setup choices](docs/SETUP_CHOICES.md)
- **Cloudflare and reverse-proxy setup:** [Deployment](docs/DEPLOYMENT.md)
- **Supported versions/platforms:** [Compatibility](docs/COMPATIBILITY.md)
- **Something is not working:** [Troubleshooting](docs/TROUBLESHOOTING.md)
- **Common questions:** [FAQ](docs/FAQ.md)
- **How MCflare works:** [Concepts](docs/CONCEPTS.md) · [Architecture](docs/V1_ARCHITECTURE.md)
- **All documentation:** [docs/README.md](docs/README.md)

## Questions, bugs and ideas

- **Setup questions / ideas:** [GitHub Discussions](https://github.com/Phloraxx/mcflare/discussions)
- **Reproducible bugs:** [GitHub Issues](https://github.com/Phloraxx/mcflare/issues)
- **Security reports:** [SECURITY.md](SECURITY.md)
- **Contributing:** [CONTRIBUTING.md](CONTRIBUTING.md)

Please do not post Cloudflare credentials, Minecraft/Microsoft authentication tokens, or player public IP addresses.

## Building

MCflare uses the Gradle wrapper. The complete loader/version matrix is built by GitHub Actions; maintainers and contributors can start with [BUILD_MATRIX.md](docs/BUILD_MATRIX.md).

## License and attribution

MCflare is MIT-licensed. It began from selected MIT-licensed ideas and Minecraft integration code from **Modflared** by Rafael / HttpRafa. Attribution is preserved in [NOTICE.md](NOTICE.md) and [LICENSE](LICENSE).
