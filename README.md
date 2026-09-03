# MCflare

[![CI](https://github.com/Phloraxx/mcflare/actions/workflows/ci.yml/badge.svg)](https://github.com/Phloraxx/mcflare/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/Phloraxx/mcflare?include_prereleases&sort=semver)](https://github.com/Phloraxx/mcflare/releases)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

Put a Minecraft Java server behind Cloudflare and let players keep joining with a normal Minecraft address.

Players install the matching MCflare mod and use `play.example.com` as usual. No `cloudflared`, WARP, VPN, Tunnel token, custom launcher, or local proxy is needed on the player side.

[Download](https://github.com/Phloraxx/mcflare/releases) · [Getting started](.github/wiki/Getting-Started.md) · [Deployment](.github/wiki/Choosing-a-Deployment.md) · [Troubleshooting](.github/wiki/Troubleshooting.md) · [Discussions](https://github.com/Phloraxx/mcflare/discussions)

## How it looks

```text
Minecraft client
      │  play.example.com
      ▼
  Cloudflare
      │  wss://play.example.com/mcflare
      ▼
MCflare gateway
      │  normal Minecraft TCP
      ▼
Minecraft server
```

The WebSocket part happens behind the scenes. Minecraft still sees its normal connection, and the player still types the normal server hostname.

MCflare works with either Cloudflare's Orange Cloud proxy or a named Cloudflare Tunnel. Those are server-side deployment choices, not different player modes.

## Install

Download the latest release and choose the artifact for your loader/server family.

| Setup | Player | Server |
|---|---|---|
| Fabric / Quilt 1.21.11 | Fabric JAR | same Fabric JAR |
| Fabric / Quilt 26.1–26.2 | Fabric JAR | same Fabric JAR |
| NeoForge 1.21.11 | NeoForge JAR | same NeoForge JAR |
| NeoForge 26.1–26.2 | NeoForge JAR | same NeoForge JAR |
| Paper / Purpur | Fabric/Quilt or NeoForge JAR | Paper plugin |

Quilt uses the matching Fabric artifact. Paper/Purpur players still use a Fabric/Quilt or NeoForge client mod.

Players put the JAR in `mods/`. Fabric, Quilt, and NeoForge servers do the same. Paper/Purpur servers put `mcflare-paper-<version>.jar` in `plugins/`.

Then route the exact `/mcflare` path from the public hostname to the MCflare gateway. The [deployment guide](.github/wiki/Choosing-a-Deployment.md) explains which Cloudflare path to use; [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) has the complete Traefik, Caddy, NGINX, and Tunnel examples.

## What has been tested

Current release families cover Minecraft **1.21.11** and **26.1–26.2** across Fabric/Quilt, NeoForge, Paper, and Purpur where applicable.

The acceptance suite includes full LOGIN → CONFIGURATION → GAME sessions, long-lived gameplay, concurrency/churn, ordinary non-MCflare server regression, IPv4/IPv6 real-IP restoration, and authenticated `online-mode=true` joins through both Orange Cloud and a named Tunnel.

See [Compatibility](.github/wiki/Compatibility.md) for the short support table and [the test matrix](docs/TEST_MATRIX.md) for the detailed evidence.

## Real player IP

Cloudflare terminates the public WebSocket, so MCflare can translate trusted Cloudflare visitor metadata into PROXY protocol v1 before the Minecraft stream. This lets the backend recover the visitor address for logs, native IP bans, and moderation tooling.

Both sides must agree on PROXY mode. Read [Real player IP](.github/wiki/Real-Player-IP.md) before enabling it.

## What MCflare does not carry

MCflare carries Minecraft Java's own connection. A proximity voice-chat socket, Dynmap, a web panel, or another separate UDP/TCP/HTTP service still needs its own network path.

It also does not make a public origin private just because a DNS record is Orange Cloud. Follow the [origin protection guidance](docs/DEPLOYMENT.md#orange-origin-protection) for that deployment.

## Guides

- [Getting started](.github/wiki/Getting-Started.md)
- [Choosing Orange Cloud or Tunnel](.github/wiki/Choosing-a-Deployment.md)
- [Orange Cloud](.github/wiki/Orange-Cloud.md)
- [Cloudflare Tunnel](.github/wiki/Cloudflare-Tunnel.md)
- [Real player IP](.github/wiki/Real-Player-IP.md)
- [Troubleshooting](.github/wiki/Troubleshooting.md)
- [FAQ](.github/wiki/FAQ.md)

The [technical documentation](docs/README.md) contains the protocol, architecture, build matrix, test evidence, and maintainer notes.

## Help and contributing

Use [Discussions](https://github.com/Phloraxx/mcflare/discussions) for setup questions and ideas, and [Issues](https://github.com/Phloraxx/mcflare/issues) for reproducible bugs.

See [CONTRIBUTING.md](CONTRIBUTING.md) for builds and pull requests and [SECURITY.md](SECURITY.md) for security reports.

MCflare is MIT-licensed and includes selected MIT-licensed work derived from Modflared by Rafael / HttpRafa; see [NOTICE.md](NOTICE.md). It is an independent hobby project and is not affiliated with Mojang Studios, Microsoft, or Cloudflare.
