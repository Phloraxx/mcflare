# Choosing a Deployment

Orange Cloud and Cloudflare Tunnel carry the same MCflare protocol. This decision is only about how Cloudflare reaches the server-side gateway.

| Situation | Recommended starting point |
|---|---|
| You already run Traefik, Caddy, NGINX, or another HTTPS reverse proxy | **Orange Cloud** |
| You prefer an outbound-only connection from the server | **Cloudflare Tunnel** |
| You already operate `cloudflared` | **Cloudflare Tunnel** |
| You want to reuse existing HTTPS routing | **Orange Cloud** |

## Orange Cloud

```text
player → Cloudflare → HTTPS reverse proxy → /mcflare → gateway → Minecraft
```

No `cloudflared` process is required for this MCflare path, but you are responsible for protecting the origin and reverse-proxy listener.

Read [Orange Cloud](Orange-Cloud.md).

## Cloudflare Tunnel

```text
player → Cloudflare → named Tunnel → cloudflared → /mcflare → gateway → Minecraft
```

The MCflare listener can remain private/loopback. `cloudflared` exists only on the server infrastructure; players never receive a Tunnel token.

Read [Cloudflare Tunnel](Cloudflare-Tunnel.md).

## Multiple Minecraft servers

Use one hostname and one gateway/backend pairing per Minecraft instance. Let your reverse proxy or Tunnel ingress route the hostnames rather than turning MCflare into another generic router.

For exact production examples, see [`docs/DEPLOYMENT.md`](https://github.com/Phloraxx/mcflare/blob/main/docs/DEPLOYMENT.md).
