<div align="center">

# MCflare Wiki

**Practical setup and operations for MCflare.**

[Repository](https://github.com/Phloraxx/mcflare) · [Releases](https://github.com/Phloraxx/mcflare/releases) · [Discussions](https://github.com/Phloraxx/mcflare/discussions)

</div>

> [!TIP]
> New to MCflare? Start with **[Getting started](Getting-Started.md)**. It takes you from choosing the JAR to the first normal Minecraft join.

## Find the right guide

| I want to… | Read |
|---|---|
| install MCflare | [Getting started](Getting-Started.md) |
| choose Orange Cloud or Tunnel | [Choosing a deployment](Choosing-a-Deployment.md) |
| configure Orange Cloud | [Orange Cloud](Orange-Cloud.md) |
| configure a named Tunnel | [Cloudflare Tunnel](Cloudflare-Tunnel.md) |
| preserve real player IPs | [Real player IP](Real-Player-IP.md) |
| check supported versions | [Compatibility](Compatibility.md) |
| fix a connection problem | [Troubleshooting](Troubleshooting.md) |
| get a short answer | [FAQ](FAQ.md) |

## Player vs server

| Player | Server owner |
|---|---|
| Install the matching Fabric/Quilt or NeoForge mod. | Install the matching mod or Paper/Purpur plugin. |
| Join `play.example.com` normally. | Route exact path `/mcflare` through Cloudflare. |
| No `cloudflared`, VPN, WARP, token, launcher, or local proxy. | Choose Orange Cloud or a named Tunnel. |

The public MCflare endpoint is `wss://play.example.com/mcflare` with WebSocket subprotocol `mcflare.v1`.

For protocol design, architecture, build details, and acceptance evidence, use the repository's [technical documentation](https://github.com/Phloraxx/mcflare/tree/main/docs).