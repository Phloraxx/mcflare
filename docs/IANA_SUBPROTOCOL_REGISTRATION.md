# IANA WebSocket Subprotocol Registration Readiness

Reviewed: 2026-09-02 (IST)

## Decision

For stable MCflare v1, keep and register the already-deployed WebSocket subprotocol identifier:

```text
mcflare.v1
```

Do **not** rename v1 solely to make the identifier domain-derived.

RFC 6455 section 1.9 recommends domain-derived names to reduce collision risk, but that section is explicitly non-normative. The live IANA WebSocket Subprotocol Name Registry is First Come First Served, and `mcflare.v1` was still unassigned at this review checkpoint. RFC 7936 confirms that protocol matching itself is case-sensitive while IANA refuses registrations that differ from an existing identifier only by case.

Changing the identifier now would be a wire-compatibility migration: the current clients and gateways require an exact `mcflare.v1` echo, and the identifier has already been exercised across the full Fabric/NeoForge/Paper compatibility matrix and real Orange/Tunnel gameplay acceptance. There is no standards requirement that justifies invalidating that evidence. Future incompatible protocol generations may use a new registered identifier.

## Registry

Exact registry name:

```text
WebSocket Subprotocol Name Registry
```

Registry group:

```text
WebSocket Protocol Registries
```

Registration procedure: **First Come First Served**.

Authoritative references:

- IANA registry: https://www.iana.org/assignments/websocket
- IANA protocol-registration forms: https://www.iana.org/protocols/apply
- IANA general request form: https://www.iana.org/form/protocol-assignment
- RFC 6455 sections 1.9 and 11.5: https://www.rfc-editor.org/rfc/rfc6455
- RFC 7936: https://www.rfc-editor.org/rfc/rfc7936

## Proposed registration data

The live IANA registry was re-checked on 2026-09-02. Its current WebSocket Subprotocol table exposes five columns: Subprotocol Identifier, Subprotocol Common Name, Subprotocol Definition, Reference, and Change Controller. The following values are frozen except for the two explicitly marked external/publication fields.

| Registry field | Proposed value |
|---|---|
| Subprotocol Identifier | `mcflare.v1` |
| Subprotocol Common Name | `MCflare v1` |
| Subprotocol Definition | `PUBLIC_STABLE_PROTOCOL_URL_TBD` |
| Reference | `PUBLIC_STABLE_PROTOCOL_URL_TBD` |
| Change Controller | `PUBLIC_MAINTAINER_CONTACT_TBD` |

`docs/V1_PROTOCOL.md` is the source text intended to become the public stable protocol definition. At this checkpoint the project repository is private, so a private GitHub/raw URL must **not** be submitted as the registry reference. Publish an immutable or durably versioned public copy first.

The change-controller contact is deliberately not guessed or copied from local Git metadata. The maintainer must explicitly choose a public contact identity/address suitable for permanent registry publication.

## General IANA request form draft

IANA currently routes WebSocket-subprotocol requests through the General Protocol Registration Request form. That form collects contact name/email plus free-form request/registry/description/additional-information text rather than exposing dedicated WebSocket-subprotocol input boxes. The registry-specific values below therefore remain the authoritative copy source.

### Contact Name / Contact Email

Supply the explicitly approved public maintainer contact at submission time.

### Request Description

```text
Please register the WebSocket subprotocol identifier "mcflare.v1" in the
WebSocket Subprotocol Name Registry.

Subprotocol Identifier: mcflare.v1
Subprotocol Common Name: MCflare v1
Subprotocol Definition: PUBLIC_STABLE_PROTOCOL_URL_TBD
Reference: PUBLIC_STABLE_PROTOCOL_URL_TBD
Change Controller: PUBLIC_MAINTAINER_CONTACT_TBD
```

### Why this assignment is needed

```text
MCflare v1 uses the RFC 6455 Sec-WebSocket-Protocol token "mcflare.v1" to
identify a minimal binary WebSocket transport that carries one ordered
Minecraft TCP byte stream. Registration prevents accidental identifier
collision and freezes the deployed v1 discovery contract.
```

### Additional Information

```text
The registration is requested under RFC 6455 section 11.5 and RFC 7936.
The protocol definition is published at the reference above. The identifier
is matched case-sensitively by implementations.
```

## Submission gate

Do not submit until all of the following are true:

1. `docs/V1_PROTOCOL.md` has been reviewed as the exact standalone v1 interoperability definition.
2. That definition is available at a stable, public HTTPS URL that does not require repository/account access.
3. A public change-controller/contact identity has been explicitly selected.
4. The IANA registry is re-checked immediately before submission for `mcflare.v1` and case-only equivalents.
5. The submitted field values are copied from this document without silently changing the identifier.

If `mcflare.v1` is allocated by someone else before submission, stop. Do not silently select a near-match. Treat that as an explicit protocol-identifier migration requiring code, interoperability tests, documentation, and a transition plan.

## After registration

After IANA confirms the assignment:

1. Record the assigned registry row and confirmation date in `WEBSOCKET_STANDARDS_RELEASE.md`.
2. Replace the registration-gate wording in `V1_PROTOCOL.md` with the registered status and registry reference.
3. Re-run the exact-subprotocol negotiation tests and the seven-row hosted CI matrix.
4. Do not change the v1 identifier afterward. A backward-incompatible v2 must use a separately defined/registered subprotocol identifier.

Registration itself is an external administrative action and is intentionally not automated by the build or release workflow.
