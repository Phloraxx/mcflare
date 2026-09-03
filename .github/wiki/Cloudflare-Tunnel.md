# Cloudflare Tunnel

Use a named Cloudflare Tunnel when you want Cloudflare to reach MCflare through an outbound connector from the server side.

```text
Minecraft player
      ↓
Cloudflare
      ↓
named Tunnel
      ↓
cloudflared
      ↓ exact /mcflare route
MCflare gateway
      ↓
Minecraft server
```

## What runs where

`cloudflared` runs only in the server infrastructure. The Minecraft player does not install it and never receives a Tunnel token.

A typical ingress rule points the hostname and exact `/mcflare` path to the MCflare HTTP listener, for example:

```yaml
ingress:
  - hostname: play.example.com
    path: ^/mcflare$
    service: http://127.0.0.1:25577
  - service: http_status:404
```

Adjust the listener address for your actual network/container layout.

## Why HTTP/WebSocket instead of generic TCP Tunnel mode?

MCflare's public transport is HTTP Upgrade/WebSocket. The player connects through normal HTTPS/WSS to Cloudflare; the gateway turns that stream back into Minecraft TCP behind the edge.

## Verify

Check `cloudflared` health, the public hostname, the exact path rule, and reachability from the connector to the gateway. Then verify a `101` Upgrade with `mcflare.v1` followed by Minecraft Status/full join.

The full Tunnel deployment reference is [`docs/DEPLOYMENT.md`](https://github.com/Phloraxx/mcflare/blob/main/docs/DEPLOYMENT.md#mode-b-cloudflare-named-tunnel).
