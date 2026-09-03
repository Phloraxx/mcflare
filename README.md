<div align="center">

# MCflare

### Minecraft Java through Cloudflare — players just join.

Put Cloudflare in front of a Minecraft Java server without making players run a tunnel, VPN, proxy, or custom launcher.

[![CI](https://github.com/Phloraxx/mcflare/actions/workflows/ci.yml/badge.svg)](https://github.com/Phloraxx/mcflare/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/Phloraxx/mcflare?include_prereleases&sort=semver&label=release)](https://github.com/Phloraxx/mcflare/releases)
[![License](https://img.shields.io/github/license/Phloraxx/mcflare)](LICENSE)

**[Download](https://github.com/Phloraxx/mcflare/releases)** · **[Wiki](https://github.com/Phloraxx/mcflare/wiki)** · **[Deployment](https://github.com/Phloraxx/mcflare/wiki/Choosing-a-Deployment)** · **[Troubleshooting](https://github.com/Phloraxx/mcflare/wiki/Troubleshooting)**

</div>

> [!IMPORTANT]
> Players install the matching MCflare mod and keep using the normal Minecraft server address, such as `play.example.com`.

## How it works

```text
Minecraft client ──► Cloudflare ──► MCflare gateway ──► Minecraft server
  play.example.com      WSS              /mcflare          normal TCP
```

The WebSocket transport stays behind the scenes. Minecraft's own login, encryption, compression, custom payloads, chunks, and gameplay remain unchanged.

## Quick start

### 1. Install MCflare

| Server | Player | Server side |
|---|---|---|
| Fabric / Quilt | Fabric MCflare JAR | same JAR |
| NeoForge | NeoForge MCflare JAR | same JAR |
| Paper / Purpur | Fabric/Quilt or NeoForge mod | Paper plugin |

Current release families cover Minecraft **1.21.11** and **26.1–26.2**. Quilt uses the Fabric artifact.

### 2. Route `/mcflare`

Expose the gateway through either **Cloudflare Orange Cloud** or a **named Cloudflare Tunnel**:

```text
wss://play.example.com/mcflare
Sec-WebSocket-Protocol: mcflare.v1
```

See the [deployment guide](https://github.com/Phloraxx/mcflare/wiki/Choosing-a-Deployment) for the recommended setup.

### 3. Join normally

```text
play.example.com
```

## Why MCflare

- Normal Minecraft **Join Server** experience.
- No player-side Cloudflare software or credentials.
- Orange Cloud and named Tunnel use the same player-facing protocol.
- Real player IP restoration is supported through trusted Cloudflare metadata and PROXY protocol v1.
- Ordinary non-MCflare servers continue using normal Minecraft TCP.

## Documentation

| Need | Guide |
|---|---|
| Install MCflare | [Getting started](https://github.com/Phloraxx/mcflare/wiki/Getting-Started) |
| Choose Orange Cloud or Tunnel | [Choosing a deployment](https://github.com/Phloraxx/mcflare/wiki/Choosing-a-Deployment) |
| Preserve player IPs | [Real player IP](https://github.com/Phloraxx/mcflare/wiki/Real-Player-IP) |
| Fix a problem | [Troubleshooting](https://github.com/Phloraxx/mcflare/wiki/Troubleshooting) |
| Check supported versions | [Compatibility](https://github.com/Phloraxx/mcflare/wiki/Compatibility) |
| Understand the internals | [Technical docs](docs/README.md) |

MCflare carries **Minecraft Java's own connection only**. Voice chat, web maps, panels, and other separate sockets need their own network path.

Use [Discussions](https://github.com/Phloraxx/mcflare/discussions) for setup questions and [Issues](https://github.com/Phloraxx/mcflare/issues) for reproducible bugs.

---

<sub>MIT licensed. Includes selected MIT-licensed work derived from Modflared by Rafael / HttpRafa; see [NOTICE.md](NOTICE.md). MCflare is independent of Mojang Studios, Microsoft, and Cloudflare.</sub>
