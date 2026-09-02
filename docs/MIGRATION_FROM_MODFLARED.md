# Migrating from Modflared

MCflare started from selected MIT-licensed Modflared ideas/integration code, but the current network architecture is intentionally different. Treat MCflare as a separate protocol/deployment line rather than a drop-in update to an existing Modflared installation.

## Architecture change

Classic Modflared's player-side model launches/uses `cloudflared` to reach a Cloudflare Tunnel TCP service and can discover that behavior through DNS TXT records or a forced-tunnel list.

MCflare v1 does **not** launch `cloudflared` on the player machine. The player mod opens an ordinary secure WebSocket directly:

```text
wss://play.example.com/mcflare
Sec-WebSocket-Protocol: mcflare.v1
```

The server side runs an MCflare gateway that converts WebSocket binary data back into the normal Minecraft TCP stream.

## What to remove from an old client setup

MCflare does not use:

- player-side `cloudflared` binaries/processes;
- `cloudflared-use-tunnel` DNS TXT discovery;
- `cloudflared-route=...` DNS TXT discovery;
- Modflared `forced_tunnels.json` behavior;
- Tunnel credentials/tokens in the Minecraft mod.

Do not copy those settings into MCflare.

## What the server needs now

1. Install the MCflare Fabric/Quilt/NeoForge server JAR or Paper/Purpur plugin.
2. Configure its local/private gateway listener.
3. Route `/mcflare` to that gateway using either Cloudflare Orange proxy + reverse proxy or a named HTTP Tunnel.
4. Configure PROXY-v1 handling if real player IP restoration is enabled.

See [INSTALLATION.md](INSTALLATION.md) and [DEPLOYMENT.md](DEPLOYMENT.md).

## Player UX after migration

Players install the appropriate MCflare client JAR and continue joining with the normal Minecraft hostname. They do not need a WSS URL, proxy port, Tunnel token, or separate launcher.

A hostname that successfully proves MCflare is persisted as a positive route. This prevents a later WSS failure from silently downgrading that known protected hostname to an accidentally exposed raw Minecraft origin.

## Compatibility note

The current MCflare v1 endpoint/protocol is not the legacy Modflared connection mechanism. Upgrade the client and server sides together when migrating a hostname.

## Project identity

The existing Modflared GitHub/Modrinth project belongs to the original Modflared release line. MCflare release artifacts should be distributed under the MCflare repository/project identity, not uploaded as if they were Modflared updates.
