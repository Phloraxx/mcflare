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

The reverse proxy owns public TLS. MCflare does not need a certificate. Keep HTTP/1.1 WebSocket upgrade behavior to the local gateway; there is no need for MCflare to implement HTTP/2 origin serving.

## Mode B: Cloudflare Tunnel

Use when inbound 443/reverse-proxy access is unavailable or intentionally avoided.

```text
player -> WSS :443 -> Cloudflare -> Tunnel -> external cloudflared -> MCflare HTTP listener -> Minecraft
```

Example local ingress:

```yaml
ingress:
  - hostname: play.example.com
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

## Integrated Fabric server

The same Fabric JAR can load on the dedicated server. Default generated config:

```properties
enabled=true
listen=127.0.0.1:25577
max-connections=256
```

The backend Minecraft address/port is taken from the running dedicated server rather than duplicated in MCflare config. If the local listener port is occupied, MCflare logs the bind failure and Minecraft continues to run; choose another per-instance listener port.

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
