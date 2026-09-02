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

## Optional IANA subprotocol registration

The IANA WebSocket Subprotocol Name Registry is First-Come-First-Served. As re-checked on 2026-09-02, `mcflare.v1` is not present in the registry.

The identifier decision is now frozen: register the existing `mcflare.v1` token as-is for v1 rather than migrating solely for domain-style naming. RFC 6455 section 1.9's domain-derived naming advice is non-normative; changing the deployed exact-match token would create a protocol migration and invalidate compatibility evidence without a normative requirement.

If MCflare later needs formal third-party interoperability registration:

1. The standalone v1 interoperability definition is complete in `docs/V1_PROTOCOL.md`; publish that exact text (or an immutable rendering of it) at a stable public HTTPS URL suitable for the registry definition/reference. The current private repository URL is not sufficient.
2. Explicitly select the public maintainer/change-controller contact that may be permanently listed by IANA. Do not infer it from local Git metadata.
3. Re-check the registry immediately before submission because allocation is First-Come-First-Served, including case-only variants per RFC 7936.
4. Submit the prepared `mcflare.v1` registration package from `docs/IANA_SUBPROTOCOL_REGISTRATION.md` using IANA's General Protocol Registration Request form.
5. After confirmation, record the registry row/date and do not change the v1 identifier without an explicit protocol-version migration plan.

Registration is an administrative external action and is intentionally not performed by build/test automation.

## Optional external validation

These are outside the unit/integration suite and must not be replaced by misleading simulations:

- public protocol publication plus IANA registration of exact `mcflare.v1` is optional future formalization for this hobby project;
- legitimate authenticated-client validation with `online-mode=true` remains an optional proof of Mojang session authentication, not a separate MCflare wire mechanism;
- observation of behavior if Cloudflare itself terminates an established edge WebSocket during an edge deployment/restart remains distinct from the already-proven local connector restart and client-network-loss tests.

Larger graphical/world-generation concurrency on production-shaped hardware is optional performance characterization. MCflare transport/session concurrency is already proven at 16 simultaneous GAME-state clients plus four 16-client churn cohorts on each public delivery mode.

Cloudflare documents that edge deployments can terminate active WebSockets, so MCflare must treat disconnect/reconnect as a normal transport lifecycle event rather than assume indefinite socket persistence.
