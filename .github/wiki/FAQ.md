# FAQ

## Do players need Cloudflare software?

No. Players install the matching MCflare mod and use Minecraft normally. They do not need `cloudflared`, WARP, a VPN, a Tunnel token, or a special launcher.

## What address do players enter?

The normal Minecraft hostname, for example `play.example.com`.

## Does `online-mode=true` work?

Yes. A real Microsoft/Mojang-authenticated Minecraft 26.2 client using the exact `1.0.1` release-package Fabric artifact has completed a full world join through both Orange Cloud and a named Cloudflare Tunnel.

## Do Paper/Purpur players install the Paper plugin?

No. The Paper/Purpur JAR is server-only. Players use the supported Fabric/Quilt or NeoForge client mod.

## Does Quilt need a separate build?

No. Quilt uses the matching Fabric artifact.

## Can MCflare preserve the player's real IP?

Yes, through trusted Cloudflare visitor metadata translated to PROXY protocol v1. The backend must be configured to consume PROXY correctly.

## Does MCflare replace Minecraft encryption or authentication?

No. It carries Minecraft's existing byte stream. Cloudflare additionally terminates the outer TLS/WebSocket connection.

## What about voice chat and web maps?

MCflare carries Minecraft Java's own connection. A mod or plugin that opens another UDP/TCP/HTTP socket still needs a separate route for that socket.

## What happens if Cloudflare or WSS drops during gameplay?

The Minecraft connection ends normally. MCflare does not splice a new WebSocket underneath an existing game session. A fresh reconnect starts a fresh Minecraft session.

## Can one gateway route several Minecraft servers by port?

The v1 design keeps this simple: use one public hostname and gateway/backend pairing per Minecraft instance, and let Cloudflare/reverse-proxy routing select the hostname.

## Where should I ask for help?

Use [GitHub Discussions](https://github.com/Phloraxx/mcflare/discussions) for setup questions and ideas. Use [Issues](https://github.com/Phloraxx/mcflare/issues) for reproducible bugs.

The longer FAQ is in [`docs/FAQ.md`](https://github.com/Phloraxx/mcflare/blob/main/docs/FAQ.md).
