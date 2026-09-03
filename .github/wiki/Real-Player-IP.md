# Real Player IP

Cloudflare terminates the public WebSocket connection, so the TCP peer seen by the MCflare gateway is not automatically the Minecraft player's address.

MCflare can restore the visitor address from trusted Cloudflare metadata and send it to the Minecraft backend using HAProxy PROXY protocol v1.

## When you need it

Use real-IP forwarding when server-side tools need the visitor address for native IP bans, moderation/audit logs, rate limiting, or similar administration.

## The trust boundary

Only accept Cloudflare visitor metadata from an ingress you actually trust. Do not expose a listener publicly and accept arbitrary `CF-Connecting-IP` values from the internet.

## Both sides must agree

If the gateway sends a PROXY line, the Minecraft backend must be configured to parse it. If the backend expects PROXY but the gateway sends ordinary Minecraft bytes, the connection also fails.

Fabric/Quilt/NeoForge can use MCflare's integrated parser. Paper/Purpur use the platform's native HAProxy/PROXY support.

## Verified behavior

The project has tested IPv4 and IPv6 visitor restoration and Minecraft's native IP-ban behavior on restored addresses.

Use the complete platform-specific instructions in [`docs/REAL_IP.md`](https://github.com/Phloraxx/mcflare/blob/main/docs/REAL_IP.md) before enabling this on a production server.
