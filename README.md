<div align="center">

# MCflare

### Minecraft Java through Cloudflare — without making players run a tunnel.

Put Cloudflare in front of a Minecraft Java server while players keep joining `play.example.com` normally.

[![CI](https://github.com/Phloraxx/mcflare/actions/workflows/ci.yml/badge.svg)](https://github.com/Phloraxx/mcflare/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/Phloraxx/mcflare?include_prereleases&sort=semver&label=release)](https://github.com/Phloraxx/mcflare/releases)
[![License](https://img.shields.io/github/license/Phloraxx/mcflare)](LICENSE)

**[Download](https://github.com/Phloraxx/mcflare/releases)** · **[Get started](.github/wiki/Getting-Started.md)** · **[Deployment](.github/wiki/Choosing-a-Deployment.md)** · **[Troubleshooting](.github/wiki/Troubleshooting.md)** · **[Discussions](https://github.com/Phloraxx/mcflare/discussions)**

</div>

> [!IMPORTANT]
> Players install the matching MCflare mod and use Minecraft normally. They do **not** need `cloudflared`, WARP, a VPN, a Tunnel token, a custom launcher, or a local proxy.

## What MCflare changes

```text
Player                                      Server

Minecraft client                            Minecraft server
      │                                           ▲
      │ play.example.com                          │ normal Minecraft TCP
      ▼                                           │
  Cloudflare  ─────── wss://.../mcflare ───►  MCflare gateway
```

The WebSocket transport stays behind the scenes. Minecraft's own login, encryption, compression, plugin messages, custom payloads, chunks, and gameplay remain the original Minecraft byte stream.

MCflare supports both **Cloudflare Orange Cloud** and a **named Cloudflare Tunnel**. That choice belongs to the server administrator; the player experience is the same either way.

## Why use it?

- **Normal Join Server experience** — players keep using the ordinary Minecraft hostname.
- **No player-side Cloudflare setup** — only the MCflare mod is required on the client.
- **Real player IP support** — trusted Cloudflare visitor metadata can be restored through PROXY protocol v1.
- **Normal servers stay normal** — non-MCflare hosts continue over ordinary Minecraft TCP.
- **Fail-closed protection** — once a hostname is positively known to use MCflare, a broken protected route does not silently downgrade to an exposed raw origin.

## Quick start

### 1. Install the matching release

| Server ecosystem | Player | Server |
|---|---|---|
| Fabric / Quilt | Fabric MCflare JAR | same Fabric JAR |
| NeoForge | NeoForge MCflare JAR | same NeoForge JAR |
| Paper / Purpur | Fabric/Quilt or NeoForge client mod | Paper plugin |

Current release families cover Minecraft **1.21.11** and **26.1–26.2**. Quilt uses the matching Fabric artifact.

### 2. Route one path through Cloudflare

Expose the MCflare gateway at exactly:

```text
wss://play.example.com/mcflare
Sec-WebSocket-Protocol: mcflare.v1
```

Already running Traefik, Caddy, NGINX, or another HTTPS reverse proxy? Start with **Orange Cloud**. Prefer an outbound-only origin path? Use a **named Cloudflare Tunnel**.

The [deployment guide](.github/wiki/Choosing-a-Deployment.md) helps choose between them; [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) contains complete configuration examples.

### 3. Join normally

```text
play.example.com
```

That is all the player enters in Minecraft.

## Tested in practice

The current acceptance matrix includes full LOGIN → CONFIGURATION → GAME sessions, authenticated `online-mode=true` joins, long-lived gameplay, reconnect behavior, concurrent sessions and churn, ordinary-server regression, and IPv4/IPv6 real-IP restoration through the supported Cloudflare paths.

See the [compatibility guide](.github/wiki/Compatibility.md) for the short support matrix or [TEST_MATRIX.md](docs/TEST_MATRIX.md) for the detailed evidence.

## Scope

MCflare carries **Minecraft Java's own connection**. Separate sockets such as proximity voice chat, Dynmap, web panels, telemetry, or unrelated UDP/TCP services need their own route.

An Orange Cloud DNS record also does not automatically make a reachable origin private. Follow the [origin protection guidance](docs/DEPLOYMENT.md#orange-origin-protection) when using that deployment.

## Documentation

| I want to… | Start here |
|---|---|
| install MCflare | [Getting started](.github/wiki/Getting-Started.md) |
| choose Orange Cloud or Tunnel | [Choosing a deployment](.github/wiki/Choosing-a-Deployment.md) |
| configure Orange Cloud | [Orange Cloud](.github/wiki/Orange-Cloud.md) |
| configure a named Tunnel | [Cloudflare Tunnel](.github/wiki/Cloudflare-Tunnel.md) |
| preserve player IPs | [Real player IP](.github/wiki/Real-Player-IP.md) |
| fix a connection problem | [Troubleshooting](.github/wiki/Troubleshooting.md) |
| understand the protocol or architecture | [Technical documentation](docs/README.md) |

Use [Discussions](https://github.com/Phloraxx/mcflare/discussions) for setup questions and ideas, and [Issues](https://github.com/Phloraxx/mcflare/issues) for reproducible bugs. Contribution instructions are in [CONTRIBUTING.md](CONTRIBUTING.md); security reports should follow [SECURITY.md](SECURITY.md).

---

<sub>MCflare is MIT-licensed and includes selected MIT-licensed work derived from Modflared by Rafael / HttpRafa; see [NOTICE.md](NOTICE.md). MCflare is an independent hobby project and is not affiliated with Mojang Studios, Microsoft, or Cloudflare.</sub>
