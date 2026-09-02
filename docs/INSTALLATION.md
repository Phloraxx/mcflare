# Installation

MCflare is designed so the player experience remains normal Minecraft. Players install a mod once; server administrators install the matching server integration and expose `/mcflare` through Cloudflare.

## 1. Choose the artifact

| Environment | Artifact |
|---|---|
| Fabric / Quilt 1.21.11 | `mcflare-fabric-1.21.11-<version>.jar` |
| Fabric / Quilt 26.1–26.2 | `mcflare-fabric-26.1-26.2-<version>.jar` |
| NeoForge 1.21.11 | `mcflare-neoforge-1.21.11-<version>.jar` |
| NeoForge 26.1–26.2 | `mcflare-neoforge-26.1-26.2-<version>.jar` |
| Paper / Purpur server | `mcflare-paper-<version>.jar` |

Fabric artifacts are also runtime-tested on Quilt. Paper/Purpur is server-only; players still use Fabric/Quilt or NeoForge.

## 2. Player installation

Place the correct Fabric/Quilt or NeoForge JAR in the normal `mods/` directory and launch Minecraft.

There is no player-side configuration for Cloudflare, Tunnel tokens, proxy addresses, or WSS URLs. Add the Minecraft server using its normal hostname, for example:

```text
play.example.com
```

MCflare automatically leaves ordinary non-MCflare servers on normal TCP.

## 3. Fabric / Quilt / NeoForge server

Place the same loader/version JAR in the dedicated server's `mods/` directory.

On first server start MCflare creates `config/mcflare.properties`. The important values are:

```properties
enabled=true
listen=127.0.0.1:25577
max-connections=256
```

Keep the gateway on loopback or a private interface whenever possible. Do not expose the gateway listener directly to arbitrary Internet traffic merely to make Cloudflare routing easier.

MCflare starts the WebSocket gateway and forwards accepted Minecraft streams to the actual Minecraft listener. The integrated server path enables PROXY-v1 handoff so Minecraft can see the restored visitor address.

## 4. Paper / Purpur server

Place `mcflare-paper-<version>.jar` in `plugins/` and start the server once.

The generated/default plugin configuration includes:

```yaml
enabled: true
listen: '127.0.0.1:25577'
backend-host: '127.0.0.1'
backend-port: 0
max-connections: 256
proxy-protocol: true
```

When `proxy-protocol: true`, enable Paper's native HAProxy/PROXY support as described in [REAL_IP.md](REAL_IP.md). Purpur follows the same Paper-compatible path.

## 5. Route `/mcflare` through Cloudflare

Choose exactly one ingress style for a hostname:

- **Orange proxy:** Cloudflare proxied DNS → HTTPS reverse proxy → MCflare gateway.
- **Named Tunnel:** Cloudflare → `cloudflared` → MCflare gateway.

The client does not know which one you selected. Both expose the same endpoint:

```text
wss://play.example.com/mcflare
Sec-WebSocket-Protocol: mcflare.v1
```

Copy a tested configuration from [DEPLOYMENT.md](DEPLOYMENT.md).

## 6. Verify the installation

A healthy setup has these properties:

1. `https://play.example.com/mcflare` reaches the gateway only when used as a WebSocket upgrade.
2. The upgrade selects the exact, case-sensitive `mcflare.v1` subprotocol.
3. A player with MCflare reaches Minecraft LOGIN → CONFIGURATION → GAME.
4. The same player can still join an ordinary non-MCflare server through direct TCP.
5. If real-IP forwarding is enabled, Minecraft sees the visitor address rather than a loopback/Cloudflare edge address.

See [TROUBLESHOOTING.md](TROUBLESHOOTING.md) if one of these fails.

## Upgrades

Replace the JAR with the newer artifact for the same loader/version family and restart Minecraft/the server normally. Do not mix Fabric and NeoForge artifacts.

A hostname successfully proven as MCflare is remembered on the player machine to prevent silent downgrade to an accidentally exposed direct origin. See the troubleshooting guide before intentionally converting such a hostname back to a non-MCflare server.
