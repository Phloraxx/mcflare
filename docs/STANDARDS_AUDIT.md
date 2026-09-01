# Standards-First Audit

The simplest MCflare is glue between standards, not a new networking framework.

| Need | Rejected custom mechanism | v1 primitive |
|---|---|---|
| Identify MCflare | TXT record, status-probe protocol, HELLO JSON | WebSocket `Sec-WebSocket-Protocol` |
| Public endpoint | unregistered `.well-known` suffix | ordinary `/mcflare` path |
| Gameplay transport | MCF framing/magic/stream IDs | RFC 6455 binary messages as byte stream |
| Keepalive | MCflare heartbeat message | WebSocket Ping/Pong |
| Encryption | MCflare crypto | WSS + Minecraft's own session encryption |
| Compression | MCflare/permessage compression | Minecraft's existing compression only |
| Real player IP | MCflare IP packet/custom login field | `CF-Connecting-IP` -> HAProxy PROXY v1 |
| IPv6 original IP | custom metadata | `CF-Connecting-IPv6` when present |
| Multi-host routing | MCflare host router | reverse proxy / cloudflared ingress |
| Tunnel management | embedded cloudflared/token manager | administrator-managed cloudflared |
| TLS certificates | MCflare ACME/TLS server | reverse proxy or Tunnel owns ingress TLS |
| Errors | MCflare control/error protocol | HTTP status + WebSocket Close semantics |
| Request tracing | MCflare trace ID | Cloudflare `CF-Ray` |
| Authentication | MCflare accounts/tokens | Minecraft online-mode; ingress hardening externally |
| Session recovery | replay/sequence protocol | ordinary Minecraft reconnect |
| DDoS/WAF | Minecraft packet firewall in gateway | Cloudflare edge + bounded gateway resources |
| HTTP versions | custom H2/H3 server | HTTP/1.1 WebSocket origin; Cloudflare handles public edge protocols |
| Server source-IP support | per-loader custom identity format | platform/native or small standard PROXY decoder |

## Decisions retained despite being custom code

### Direct-versus-MCflare route selection

A player can have MCflare installed while joining ordinary Minecraft servers. There is no standard Minecraft DNS flag that means "speak MCflare WSS" without adding admin/player configuration. The active WSS-versus-direct race therefore remains a legitimate MCflare responsibility.

### Loopback carrier

Minecraft expects a TCP socket. The loopback carrier is a small compatibility shim that prevents each loader/version adapter from replacing Minecraft's full Netty transport.

### Minimal server WebSocket implementation

The standalone gateway intentionally remains a small bounded RFC6455 implementation rather than introducing a full HTTP framework. It must remain auditable and standards-compliant; switch frameworks only if measured maintenance/security cost justifies it.

## Standards-registration notes

RFC 8615 says new `.well-known` URI suffixes must be registered. v1 therefore uses `/mcflare`.

RFC 6455 creates the IANA WebSocket Subprotocol registry and recommends registration. The current experimental identifier is `mcflare.v1`. Before stable release, decide whether to register it as-is or adopt a namespaced final identifier, then freeze it.

## External facts validated during 2026-09-01 review

- Cloudflare supports proxied WebSockets and recommends keepalive.
- Cloudflare Tunnel supports hostname and regex path routing to local HTTP services.
- Cloudflare documents client-side cloudflared as necessary for non-HTTP public-hostname services, not ordinary HTTP applications.
- Cloudflare forwards `CF-Connecting-IP` to HTTP origins and can preserve original IPv6 in `CF-Connecting-IPv6`.
- Fabric supports `environment: "*"` for a JAR that loads on both physical sides.
- Paper exposes native PROXY-protocol processing.

## References

- https://www.rfc-editor.org/rfc/rfc6455
- https://www.rfc-editor.org/rfc/rfc8615
- https://www.iana.org/assignments/websocket/
- https://developers.cloudflare.com/network/websockets/
- https://developers.cloudflare.com/fundamentals/reference/http-headers/
- https://developers.cloudflare.com/tunnel/advanced/local-management/configuration-file/
- https://developers.cloudflare.com/cloudflare-one/networks/connectors/cloudflare-tunnel/routing-to-tunnel/protocols/
- https://docs.fabricmc.net/develop/loader/fabric-mod-json
- https://docs.papermc.io/paper/reference/global-configuration/

## Backend timing

Minecraft installs a pre-handshake read timeout on accepted TCP sockets. MCflare therefore uses lazy backend connection rather than inventing a Minecraft keepalive: WebSocket control traffic stays at the WebSocket layer, and the backend exists only once Minecraft bytes actually start.
