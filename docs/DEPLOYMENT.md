# MCflare Deployment Guide

Status: Orange and named-Tunnel paths both live-validated on 2026-09-01.

## MCflare does not manage Cloudflare

MCflare contains no Cloudflare API client, Tunnel token, Tunnel UUID, DNS credential, cloudflared downloader, cloudflared child process, certificate manager, or ACME client. Ingress is infrastructure owned by the administrator/hosting platform.

The gateway is always the same ordinary HTTP/1.1 WebSocket application.

## Mode A: Cloudflare Orange

Use when the administrator controls an HTTPS ingress/reverse proxy.

```text
player -> WSS :443 -> Cloudflare Orange -> Traefik/Caddy/Nginx -> MCflare HTTP listener -> Minecraft
```

Example routing intent:

```text
Host(play.example.com) && Path(/mcflare)
  -> http://127.0.0.1:25577
```

The reverse proxy owns public TLS. MCflare does not need a certificate. Keep HTTP/1.1 WebSocket upgrade behavior to the local gateway; there is no need for MCflare to implement HTTP/2 origin serving. Cloudflare supports proxied WebSockets; ensure the zone WebSockets setting is enabled.

### Traefik

Traefik supports WebSocket upgrades without a WebSocket-specific middleware. A file-provider example is:

```yaml
http:
  routers:
    mcflare:
      rule: 'Host(`play.example.com`) && Path(`/mcflare`)'
      entryPoints:
        - websecure
      service: mcflare
      tls: {}
  services:
    mcflare:
      loadBalancer:
        servers:
          - url: http://mcflare-gateway:25577
```

Replace `mcflare-gateway:25577` with the private address Traefik can actually reach. If Traefik runs in a container, `127.0.0.1` refers to that container rather than the Minecraft host unless they share the same network namespace.

### Caddy

Caddy's `reverse_proxy` handles the WebSocket upgrade automatically:

```caddyfile
play.example.com {
    @mcflare path /mcflare
    reverse_proxy @mcflare 127.0.0.1:25577
}
```

Use a private/container-network hostname instead of `127.0.0.1` when Caddy and MCflare do not share a host/network namespace.

### NGINX

NGINX requires the WebSocket hop-by-hop upgrade headers to be forwarded explicitly:

```nginx
location = /mcflare {
    proxy_pass http://127.0.0.1:25577;
    proxy_http_version 1.1;

    proxy_set_header Host $host;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";

    proxy_set_header CF-Connecting-IP $http_cf_connecting_ip;
    proxy_set_header CF-Connecting-IPv6 $http_cf_connecting_ipv6;
    proxy_set_header CF-Ray $http_cf_ray;
}
```

The `CF-*` headers above must be trusted only when this origin path is reachable through controlled Cloudflare ingress. A directly reachable origin allows arbitrary clients to supply look-alike forwarding headers; firewall/private-bind the origin accordingly.

## Mode B: Cloudflare Tunnel

Use when inbound 443/reverse-proxy access is unavailable or intentionally avoided.

```text
player -> WSS :443 -> Cloudflare -> Tunnel -> external cloudflared -> MCflare HTTP listener -> Minecraft
```

Example local ingress:

```yaml
ingress:
  - hostname: play.example.com
    path: ^/mcflare$
    service: http://127.0.0.1:25577
  - service: http_status:404
```

Cloudflare documents HTTP published applications as normal HTTPS-to-local-HTTP proxying. Non-HTTP services such as `tcp://` require client-side cloudflared; MCflare deliberately avoids that model by speaking standard WebSocket itself.

## Multiple Minecraft instances

Each server instance gets its own hostname and its own MCflare listener. Routing remains external.

Orange example:

```text
survival.example.com /mcflare -> 127.0.0.1:25577 -> Minecraft :25565
creative.example.com /mcflare -> 127.0.0.1:25578 -> Minecraft :25566
```

Tunnel example:

```yaml
- hostname: survival.example.com
  service: http://127.0.0.1:25577
- hostname: creative.example.com
  service: http://127.0.0.1:25578
```

Cloudflared ingress can also match request paths with regular expressions. MCflare itself does not duplicate this router.

## Integrated Fabric / NeoForge server

The same Fabric or NeoForge loader JAR can load on its dedicated server. Both use the shared server adapter and generated config:

```properties
enabled=true
listen=127.0.0.1:25577
max-connections=256
```

The backend Minecraft address/port is taken from the running dedicated server rather than duplicated in MCflare config. If the local listener port is occupied, MCflare logs the bind failure and Minecraft continues to run; choose another per-instance listener port.

## Paper / Purpur plugin

Paper and Purpur use one server-only Java-21 plugin rather than the Minecraft Mixin adapter. The plugin starts/stops the same gateway and defaults `proxy-protocol: true` so real player IP remains a first-class requirement. Enable the platform's native setting:

```yaml
proxies:
  proxy-protocol: true
```

The Minecraft backend port must then be treated as a PROXY-protocol backend: firewall/private-bind it so players cannot bypass MCflare and connect without a PROXY header. Do not add a second Paper/Purpur IP-forwarding implementation; the native server decoder is the standard handoff. The same plugin JAR is runtime-proven on Paper and Purpur 1.21.11, 26.1.2 and 26.2.

## Standalone gateway

For server software without an MCflare server adapter, run the Java-8-compatible gateway separately:

```text
McflareGateway <listen-host:port> <minecraft-host:port> [max-connections] [proxy-protocol]
```

`proxy-protocol=false` is appropriate for a backend that does not understand PROXY protocol. Use true only when the backend server/proxy is configured to consume it.

## Security checklist

- Orange DNS record is proxied.
- Public Minecraft origin port is closed or otherwise not used as the protected hostname path where possible.
- Gateway listener is loopback/private whenever infrastructure permits.
- Reverse proxy passes WebSocket Upgrade/Connection headers and Cloudflare forwarding headers.
- Do not put browser-interactive Managed Challenge/Access login in front of `/mcflare`.
- Tunnel remains managed externally; no Tunnel token goes in MCflare config.
- Keep old test routes separate during migrations; path-specific rules make side-by-side validation possible.

## Gateway operational logging

The gateway emits small structured operational events suitable for platform logs:

- `event=listen`: configured listener/backend, connection ceiling and PROXY-mode state;
- `event=upgrade`: monotonic per-process session ID, forwarded-IP **presence only**, and a sanitized Cloudflare Ray identifier when present;
- `event=close`: the same session ID, duration in milliseconds and one termination reason;
- `event=capacity-reject`: current full-capacity count and configured ceiling;
- `event=error`: session ID plus failure stage and exception type.

The gateway intentionally does not write the raw forwarded player address to these operational events. Invalid or control-character-bearing CF-Ray values are reduced to `invalid` rather than copied into logs.

## Operational validation

A healthy deployment must pass:

1. HTTPS DNS/TLS resolution.
2. `GET /mcflare` with WebSocket Upgrade and `mcflare.v1` returns HTTP 101.
3. A real Minecraft Status request over that same WebSocket returns a parseable Status response.
4. Gateway sees Cloudflare IP and Ray metadata.
5. If PROXY mode is enabled, backend server accepts the connection and observes/restores the forwarded address.
6. Full player login and sustained gameplay pass before production cutover.

## References

- Cloudflare Tunnel routing: https://developers.cloudflare.com/tunnel/routing/
- Local ingress configuration: https://developers.cloudflare.com/tunnel/advanced/local-management/configuration-file/
- Published application protocols: https://developers.cloudflare.com/cloudflare-one/networks/connectors/cloudflare-tunnel/routing-to-tunnel/protocols/
- Cloudflare WebSockets: https://developers.cloudflare.com/network/websockets/
- Traefik WebSocket guide: https://doc.traefik.io/traefik/user-guides/websocket/
- Caddy `reverse_proxy`: https://caddyserver.com/docs/caddyfile/directives/reverse_proxy
- NGINX WebSocket proxying: https://nginx.org/en/docs/http/websocket.html
