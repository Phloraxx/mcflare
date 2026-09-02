# MCflare v1 Wire Protocol

Status: frozen v1 wire definition used by the implementation. IANA registration is optional future formalization, not a hobby-project release requirement.

## 1. Scope

MCflare v1 carries one ordered Minecraft TCP byte stream over one RFC 6455 WebSocket connection. It adds no application framing, packet translation, multiplexing, authentication protocol, or Cloudflare-specific message format.

This document defines the interoperable wire contract associated with the WebSocket subprotocol identifier `mcflare.v1`. Client discovery policy, durable route pins, reverse-proxy configuration, Cloudflare forwarding headers, and backend PROXY-protocol forwarding are deployment or implementation concerns and are not part of the `mcflare.v1` WebSocket subprotocol.

The key words **MUST**, **MUST NOT**, **SHOULD**, **SHOULD NOT**, and **MAY** in this document are to be interpreted as described in BCP 14 when, and only when, they appear in all capitals.

## 2. Endpoint and WebSocket negotiation

The public MCflare v1 endpoint is:

```text
wss://<hostname>/mcflare
```

The path is exactly `/mcflare`. Public deployments normally use the standard WSS port 443; a non-default port may be used by controlled/test deployments without changing the subprotocol semantics.

A client attempting MCflare v1 MUST perform a standards-compliant RFC 6455 WebSocket opening handshake and MUST offer this exact, case-sensitive token in `Sec-WebSocket-Protocol`:

```text
mcflare.v1
```

A server accepting MCflare v1 MUST return HTTP `101 Switching Protocols` and MUST select exactly `mcflare.v1` in `Sec-WebSocket-Protocol`. The token is case-sensitive: for example, `MCFLARE.V1` is not equivalent to `mcflare.v1`.

The client MAY offer `mcflare.v1` among other WebSocket subprotocol tokens. A server that selects MCflare v1 returns exactly the `mcflare.v1` token. A client requiring MCflare v1 MUST reject a successful HTTP upgrade that omits the selected token or selects a different token.

MCflare v1 does not define any WebSocket extension. A conforming v1 connection MUST operate without WebSocket extensions, and endpoints MUST NOT require an extension to interpret the Minecraft byte stream. The current MCflare v1 implementation does not offer extensions and rejects an unsolicited server-selected extension.

MCflare v1 defines no discovery-only message or connection type. The opening handshake itself can serve as capability proof. A client that intends to proceed after a successful capability probe SHOULD retain that accepted WebSocket as the gameplay carrier rather than close it and perform an otherwise redundant second WebSocket handshake.

## 3. Data semantics

After the opening handshake, application data is binary only.

For each direction, concatenate the payload bytes of accepted WebSocket binary data and continuation frames in receive order. The resulting octet sequence is exactly the corresponding Minecraft TCP byte stream:

```text
concatenated WebSocket binary payload bytes == Minecraft TCP stream bytes
```

WebSocket message boundaries and frame boundaries have no Minecraft meaning. An implementation MAY split outgoing Minecraft bytes across any number of binary WebSocket messages/frames permitted by RFC 6455, and a receiver MUST preserve the byte order when presenting them to Minecraft.

MCflare v1 adds no magic prefix, length field, stream identifier, sequence number, opcode, JSON envelope, base64 encoding, capability exchange, or packet-level translation.

Text WebSocket messages are not part of MCflare v1 and MUST NOT be interpreted as Minecraft bytes.

## 4. Fragmentation and control frames

RFC 6455 fragmentation is supported. Continuation-frame payload bytes contribute to the same ordered byte stream as the binary message they continue.

Ping, Pong, and Close are standard RFC 6455 control frames. Their payloads MUST NOT enter the Minecraft byte stream. Ping MAY be used as a transport keepalive and MUST receive the RFC 6455 Pong behavior from a conforming peer.

Client-to-server frames follow RFC 6455 masking requirements; server-to-client frames follow RFC 6455 server framing requirements. Invalid masking, invalid control-frame fragmentation/size, unsupported RSV bits, or unsupported opcodes are protocol errors and MUST NOT be reinterpreted as Minecraft bytes.

Implementations MAY enforce finite HTTP-header, frame-size, connection-count, and resource limits. Such implementation limits do not create MCflare application framing; peers can fragment binary data as RFC 6455 permits.

## 5. Connection lifecycle

One accepted MCflare v1 WebSocket represents one Minecraft transport connection. Closing or losing that WebSocket terminates that Minecraft transport connection.

MCflare v1 does not define transparent session replay, stream resumption, multi-stream multiplexing, or migration of an established Minecraft session to another WebSocket. A new WebSocket is a new transport connection from the perspective of this subprotocol.

Endpoints use ordinary RFC 6455 Close semantics. A received valid Close is answered according to RFC 6455 if a Close has not already been sent. An implementation-initiated graceful shutdown SHOULD send a normal WebSocket Close when possible before the underlying TCP/TLS connection is torn down.

## 6. HTTP failure behavior

Before WebSocket upgrade, failures remain ordinary HTTP/WebSocket failures. MCflare v1 defines no custom error body or control protocol.

Implementations MAY use status codes such as:

- `400` for an invalid upgrade or missing/incorrect required subprotocol;
- `404` when `/mcflare` is not served;
- `503` when the gateway has no available connection capacity.

Clients MUST treat only a valid RFC 6455 `101` response selecting exact `mcflare.v1` as successful MCflare v1 negotiation.

## 7. Transport security and origin behavior

The public endpoint uses WSS, so the WebSocket handshake and byte stream are protected by TLS between the client and the public WebSocket endpoint. Normal TLS hostname verification applies.

MCflare v1 defines no browser-origin model, cookie authentication, bearer token, Cloudflare Access flow, or JavaScript challenge. Infrastructure that places browser-interactive authentication/challenge behavior in front of `/mcflare` is not transparently compatible with a native Minecraft client unless that behavior still permits the standards-based WebSocket handshake without additional client protocol work.

Minecraft's own application-level authentication, encryption, compression, status, login, and gameplay packets remain unmodified inside the byte stream.

## 8. Real client address forwarding is outside the WebSocket subprotocol

Cloudflare headers such as `CF-Connecting-IP`, `CF-Connecting-IPv6`, and `CF-Ray` are HTTP ingress metadata and are not MCflare v1 application data. A gateway may use trusted ingress metadata to restore the visitor address toward its local Minecraft backend, for example with PROXY protocol v1, but that backend handoff is outside the `mcflare.v1` WebSocket wire contract.

Deployments MUST NOT trust client-supplied forwarding headers merely because their names resemble Cloudflare headers. Such metadata is trustworthy only when the origin path is restricted to a controlled/trusted ingress that establishes those headers.

## 9. Discovery and route memory are client policy

How a Minecraft client decides whether a hostname supports MCflare is not encoded in the v1 byte stream. A client MAY probe/race WSS against ordinary Minecraft reachability for an unknown destination.

A valid `101` selecting exact `mcflare.v1` is positive proof that the endpoint accepted this subprotocol. Implementations may remember that positive result and apply fail-closed policy on later attempts. Negative-cache lifetime, durable positive-route storage, direct-TCP fallback policy, DNS/SRV behavior, and connection-racing algorithms are implementation policy rather than subprotocol messages.

## 10. Deployment neutrality

The wire contract is identical whether the public WebSocket reaches the gateway through a conventional reverse proxy or a Cloudflare Tunnel:

```text
Cloudflare Orange -> HTTPS reverse proxy -> MCflare gateway
Cloudflare Tunnel -> cloudflared -> MCflare gateway
```

There is no Orange/Tunnel mode bit, token, frame, or negotiation field in `mcflare.v1`. No client-side `cloudflared`, Tunnel token, Tunnel UUID, WARP session, or Cloudflare API credential is part of the protocol.

## 11. Versioning and registration

The v1 identifier is frozen as `mcflare.v1`. RFC 6455 section 1.9 recommends domain-derived names to reduce collision risk, but that advice is explicitly non-normative. Changing the already interoperable exact-match v1 token would itself require a protocol migration.

If MCflare later grows into a broadly implemented public interoperability protocol, the project may register exact `mcflare.v1` in IANA's First Come First Served WebSocket Subprotocol Name Registry. RFC 7936 clarifies that matching is case-sensitive and that IANA refuses new registrations differing from an existing identifier only by case. Registration is not required for this hobby-project release line.

Any future backward-incompatible wire protocol MUST use a separately defined subprotocol identifier rather than silently changing the semantics of `mcflare.v1`.

## 12. References

- RFC 6455, *The WebSocket Protocol*: https://www.rfc-editor.org/rfc/rfc6455
- RFC 7936, *Clarifying Registry Procedures for the WebSocket Subprotocol Name Registry*: https://www.rfc-editor.org/rfc/rfc7936
- RFC 2119 / RFC 8174, BCP 14 requirement keywords: https://www.rfc-editor.org/info/bcp14
- IANA WebSocket Protocol Registries: https://www.iana.org/assignments/websocket/

## 13. Non-normative implementation notes

The reference MCflare gateway opens its local Minecraft backend lazily after the first non-empty WebSocket application bytes rather than immediately after HTTP upgrade. This prevents discovery Ping/Pong traffic from consuming a Minecraft pre-handshake connection. Lazy backend activation is an implementation optimization, not a requirement for interoperable `mcflare.v1` peers.

The reference implementation currently applies bounded HTTP-header/frame sizes and bounded concurrent gateway slots. Those defensive resource limits likewise do not alter the application byte-stream semantics defined above.
