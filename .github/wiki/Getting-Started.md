# Getting Started

A basic MCflare setup has three parts: a player mod, a server integration, and one Cloudflare WebSocket route.

## Before you begin

You need a supported Minecraft/server family, a hostname you control, a Cloudflare-managed DNS zone, and either an HTTPS reverse proxy or a named Cloudflare Tunnel.

## 1. Download the right artifact

Use the latest [GitHub Release](https://github.com/Phloraxx/mcflare/releases).

| Environment | Player | Server |
|---|---|---|
| Fabric / Quilt 1.21.11 | Fabric JAR | same Fabric JAR |
| Fabric / Quilt 26.1–26.2 | Fabric JAR | same Fabric JAR |
| NeoForge 1.21.11 | NeoForge JAR | same NeoForge JAR |
| NeoForge 26.1–26.2 | NeoForge JAR | same NeoForge JAR |
| Paper / Purpur | Fabric/Quilt or NeoForge mod | Paper plugin |

Quilt uses the matching Fabric artifact. See [Compatibility](Compatibility.md) for Java requirements and exact tested families.

## 2. Install the player mod

Put the matching MCflare JAR in the player's normal `mods/` folder. That is all the Cloudflare-specific setup a player needs.

## 3. Install the server side

For Fabric, Quilt, or NeoForge, put the same supported loader-family JAR in the dedicated server's `mods/` folder.

For Paper or Purpur, put `mcflare-paper-<version>.jar` in `plugins/`.

On first start, the server integration creates its MCflare configuration. Keep the MCflare listener on loopback or a private interface whenever possible.

## 4. Pick a Cloudflare path

Use [Orange Cloud](Orange-Cloud.md) if you already have a reachable HTTPS reverse proxy. Use [Cloudflare Tunnel](Cloudflare-Tunnel.md) if you want the MCflare HTTP listener reached through an outbound `cloudflared` connector.

Both routes expose the same `/mcflare` endpoint. The player does not choose a mode.

## 5. Join normally

Players add only the ordinary server hostname:

```text
play.example.com
```

A healthy protected connection reaches Minecraft LOGIN → CONFIGURATION → GAME. Authenticated `online-mode=true` joins have been verified through both supported Cloudflare delivery modes.

## Next

- [Choose a deployment](Choosing-a-Deployment.md)
- [Configure real player IP](Real-Player-IP.md)
- [Troubleshoot a failed connection](Troubleshooting.md)

The full installation reference is [`docs/INSTALLATION.md`](https://github.com/Phloraxx/mcflare/blob/main/docs/INSTALLATION.md).
