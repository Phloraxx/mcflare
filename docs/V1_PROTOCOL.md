# MCflare v1 Wire Protocol

Status: minimal standards-based protocol candidate.

## Endpoint

```text
wss://<player-entered-hostname>/mcflare
Sec-WebSocket-Protocol: mcflare.v1
```

MCflare intentionally uses `/mcflare`, not an unregistered `/.well-known/...` suffix. RFC 8615 requires new well-known URI suffixes to be registered; a normal path has no such administrative requirement.

## WebSocket handshake

A client requests `mcflare.v1` using the standard `Sec-WebSocket-Protocol` header. A compatible gateway must return HTTP `101 Switching Protocols` and echo `mcflare.v1`. That is the discovery proof.

The successful discovery WebSocket is retained as the actual gameplay carrier. v1 must not perform a discovery WebSocket and then open a second gameplay WebSocket.

RFC 6455 explicitly defines subprotocol negotiation for this use. Before a stable public release, register the final subprotocol identifier in IANA's First Come First Served WebSocket Subprotocol Name Registry or adopt an appropriately namespaced final identifier.

## Data semantics

After upgrade, each binary WebSocket message contributes bytes to one ordered Minecraft TCP byte stream. WebSocket message/frame boundaries have no Minecraft meaning.

```text
WebSocket binary payload bytes == Minecraft TCP stream bytes
```

No MCflare magic header, packet length, stream ID, capability JSON, HELLO, sequence number, service opcode, base64 encoding, or packet translation is added.

## Fragmentation and control frames

RFC 6455 fragmentation may split application data. Receivers must treat data as a stream and support continuation frames. Client frames are masked; server frames are unmasked. Ping/Pong/Close are standard WebSocket control frames and never enter the Minecraft byte stream.

## Keepalive

The client sends standard WebSocket Ping periodically during an active carrier. The current interval is approximately 30 seconds. No custom MCflare heartbeat exists.

Cloudflare documents long-lived WebSocket support but notes that edge restarts can terminate connections and recommends keepalives. A terminated carrier ends the Minecraft connection; v1 does not attempt transparent session replay.

## Compression

Do not negotiate `permessage-deflate` in v1. Minecraft already has its own compression layer, and double-compressing an encrypted/compressed gameplay stream adds complexity with little expected value.

## Discovery policy

For a previously unknown hostname, the client may race an MCflare WSS attempt against ordinary Minecraft TCP reachability. A valid `101 + mcflare.v1` is definitive. Ordinary direct Minecraft remains Minecraft's responsibility, including its DNS/SRV resolution.

Once the current attempt has positively selected MCflare, failure of that carrier is an MCflare connection failure, not a reason to reinterpret the same attempt as direct TCP.

## Server errors

Before upgrade, standard HTTP errors are sufficient: 400 for an invalid upgrade/subprotocol, 404 for a wrong path, and 503 when connection capacity is exhausted. After upgrade, use standard WebSocket Close semantics. Do not invent an MCflare error/control protocol for v1.

## Address families

The WSS client should attempt usable resolved addresses rather than assuming the first DNS answer is healthy. The current core races Cloudflare addresses with a short stagger, a behavior motivated by a real test where one returned Anycast IPv4 address timed out while another connected immediately.

## Deployment neutrality

The protocol is identical for:

```text
Cloudflare Orange -> reverse proxy -> HTTP/WebSocket gateway
Cloudflare Tunnel -> cloudflared -> HTTP/WebSocket gateway
```

The client cannot and should not distinguish them. Tunnel-specific TCP publishing (`tcp://...`) is not the MCflare v1 transport because Cloudflare documents that non-HTTP published applications require client-side cloudflared. MCflare instead publishes an ordinary HTTP/WebSocket origin.

## References

- RFC 6455, The WebSocket Protocol: https://www.rfc-editor.org/rfc/rfc6455
- RFC 8615, Well-Known URIs: https://www.rfc-editor.org/rfc/rfc8615
- IANA WebSocket registries: https://www.iana.org/assignments/websocket/
- Cloudflare WebSockets: https://developers.cloudflare.com/network/websockets/
