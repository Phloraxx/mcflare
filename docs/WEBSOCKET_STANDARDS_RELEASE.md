# WebSocket Standards Release Gate

Date reviewed: 2026-09-02 (IST)

## Scope

MCflare transports the Minecraft TCP byte stream over RFC 6455 binary WebSocket messages on `/mcflare`. The current protocol token is `mcflare.v1`.

This gate covers protocol behavior that is independent of Cloudflare, Caddy, Traefik, NGINX, or Tunnel deployment details.

## Normative requirements checked

- Client-to-server WebSocket frames are masked; server-to-client frames are not.
- Control frames are final and no larger than 125 bytes.
- Ping is answered with Pong carrying the same application payload.
- Binary fragmentation and continuation frames remain a byte stream.
- Unsupported text frames and invalid masking fail closed.
- `Sec-WebSocket-Protocol` selection is case-sensitive.
- A received Close frame is answered with a Close frame if one has not already been sent.
- Server-initiated teardown emits a normal WebSocket Close before closing TCP on the best-effort gateway path.

References: RFC 6455 and RFC 7936.

## Release fixes after baseline `98e41fe`

1. Replace case-insensitive subprotocol selection with exact token matching while retaining case-insensitive matching for HTTP `Connection`/`Upgrade` semantics.
2. Echo a valid received Close payload from the gateway before marking the WebSocket closed.
3. Echo a valid received Close payload from the MCflare client as a masked client control frame.
4. Emit Close code 1000 on best-effort server-initiated gateway teardown.
5. Reject unsolicited server-selected subprotocols and WebSocket extensions that the client did not offer.
6. Serialize the closed-state check with frame writes so no data/control frame can queue behind a completed Close.
7. Add regression tests for mixed-case rejection, protocol-list selection, unsolicited negotiation, received-close echo, and server-initiated close.

## IANA subprotocol registration gate

The IANA WebSocket Subprotocol Name Registry is First-Come-First-Served. As re-checked on 2026-09-02, `mcflare.v1` is not present in the registry.

Before calling the public protocol standards-complete:

1. Publish a stable protocol definition suitable for use as the registry definition/reference.
2. Decide whether to register the concise existing `mcflare.v1` identifier or migrate before release to a collision-resistant domain-derived identifier as recommended by RFC 6455 section 1.9.
3. Submit the selected identifier, common name, definition/reference, and change-controller information to IANA.
4. Re-check the registry immediately before submission because allocation is First-Come-First-Served.
5. Do not change the identifier after release without an explicit protocol-version migration plan.

Registration is an administrative external action and is intentionally not performed by build/test automation.

## Remaining external acceptance boundaries

These are not claims made by the unit/integration suite:

- legitimate authenticated-client validation with `online-mode=true`;
- larger real-player concurrency/churn on production-shaped hardware;
- observation of reconnect behavior if Cloudflare itself terminates an established edge WebSocket during an edge deployment/restart.

Cloudflare documents that edge deployments can terminate active WebSockets, so MCflare must treat disconnect/reconnect as a normal transport lifecycle event rather than assume indefinite socket persistence.