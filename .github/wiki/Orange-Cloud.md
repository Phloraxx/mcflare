# Orange Cloud

Use the Orange Cloud path when Cloudflare can reach an HTTPS reverse proxy you already operate.

```text
Minecraft player
      ↓
Cloudflare proxy
      ↓
HTTPS reverse proxy
      ↓ exact /mcflare route
MCflare gateway
      ↓
Minecraft server
```

## Requirements

- the Minecraft hostname is proxied through Cloudflare;
- HTTPS/TLS to the origin is healthy;
- the reverse proxy forwards WebSocket Upgrade correctly;
- the exact `/mcflare` path reaches the MCflare gateway;
- the gateway can reach the Minecraft backend.

MCflare has tested examples for Traefik, Caddy, and NGINX in [`docs/DEPLOYMENT.md`](https://github.com/Phloraxx/mcflare/blob/main/docs/DEPLOYMENT.md#mode-a-cloudflare-orange-proxy).

## Important security point

An Orange Cloud DNS record is not, by itself, an origin firewall. Keep the gateway private where possible and restrict the public origin according to your infrastructure model.

Never trust visitor-IP forwarding headers from an arbitrary public client. See [Real player IP](Real-Player-IP.md).

## Verify

A correct WebSocket request to `/mcflare` should return `101 Switching Protocols` and select the exact subprotocol `mcflare.v1`. Then verify Minecraft Status and a full join.

If Upgrade works but Minecraft does not, continue with [Troubleshooting](Troubleshooting.md).
