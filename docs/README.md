# MCflare documentation

Start here if you want to install, run, troubleshoot, or understand MCflare. You do not need to understand the protocol internals to use it.

## Pick your path

| I want to… | Start here | Then read |
|---|---|---|
| Join an MCflare server as a player | [Installation](INSTALLATION.md#player-installation) | [FAQ](FAQ.md) |
| Run my Minecraft server through Cloudflare | [Choose your setup](SETUP_CHOICES.md) | [Installation](INSTALLATION.md), [Deployment](DEPLOYMENT.md) |
| Configure real player IPs | [Real player IP](REAL_IP.md) | [Deployment security](DEPLOYMENT.md#security-checklist) |
| Check whether my version is supported | [Compatibility](COMPATIBILITY.md) | [Build matrix](BUILD_MATRIX.md) |
| Fix a connection problem | [Troubleshooting](TROUBLESHOOTING.md) | [FAQ](FAQ.md), [Support](../SUPPORT.md) |
| Understand how MCflare works | [Concepts](CONCEPTS.md) | [v1 architecture](V1_ARCHITECTURE.md) |
| Implement/interoperate with the wire protocol | [v1 wire protocol](V1_PROTOCOL.md) | [Standards audit](STANDARDS_AUDIT.md) |
| Build or publish MCflare | [Contributing](../CONTRIBUTING.md) | [Build matrix](BUILD_MATRIX.md), [Release process](RELEASE.md) |

## User and administrator guides

### [Installation](INSTALLATION.md)
Choose the correct JAR, install the client/server pieces, and verify a basic connection.

### [Choose your setup](SETUP_CHOICES.md)
A decision guide for Orange proxy versus named Tunnel, loader choices, real-IP requirements, and multiple Minecraft instances.

### [Deployment](DEPLOYMENT.md)
Copy-paste Traefik, Caddy, NGINX, and named-Tunnel examples, plus the gateway security checklist.

### [Compatibility](COMPATIBILITY.md)
Supported Minecraft families, Fabric/Quilt/NeoForge/Paper/Purpur packaging, Java requirements, and mod/proxy compatibility boundaries.

### [Troubleshooting](TROUBLESHOOTING.md)
A diagnostic flow for player, Cloudflare ingress, gateway, backend, and real-IP failures.

### [FAQ](FAQ.md)
Short answers to the questions most players and administrators are likely to have.

### [Migrating from Modflared](MIGRATION_FROM_MODFLARED.md)
What changes when moving from the older player-side `cloudflared` model to MCflare v1.

## Concepts and architecture

### [Concepts](CONCEPTS.md)
Plain-language definitions for the client adapter, loopback carrier, `/mcflare`, `mcflare.v1`, gateway, positive route pins, PROXY v1, Orange proxy, and named Tunnel.

### [v1 architecture](V1_ARCHITECTURE.md)
The current component model, trust boundaries, lifecycle, and deliberate non-goals.

### [v1 wire protocol](V1_PROTOCOL.md)
The exact interoperable WebSocket contract. Use this—not historical research notes—as the protocol reference.

### [Real player IP](REAL_IP.md)
How Cloudflare visitor metadata is validated and translated into standard HAProxy PROXY protocol v1.

### [Low-latency architecture](LOW_LATENCY_ARCHITECTURE.md)
Why the transport stays minimal and what the measured path looks like.

### [Design constraints](DESIGN.md)
Compact statement of the architectural rules that keep MCflare small.

## Compatibility, builds, and releases

- [Build matrix](BUILD_MATRIX.md) — exact loader/version/toolchain families and CI properties.
- [Release process](RELEASE.md) — dry-run and tag-driven release workflow.
- [v1.0.0-rc.1 release evidence](RELEASE_EVIDENCE_1.0.0-rc.1.md) — exact hosted artifact hashes and packaged-JAR smoke results.
- [Test matrix](TEST_MATRIX.md) — concise list of proven gates and optional future validation.

## Maintainer evidence and historical research

These files preserve engineering decisions and regression evidence. They are intentionally **not** onboarding documentation and may discuss experiments that were later rejected or superseded.

- [Test evidence — 2026-09-01/02](TEST_EVIDENCE_2026-09-01.md)
- [Project knowledge / historical research log](PROJECT_KNOWLEDGE.md)
- [Completed implementation plan](IMPLEMENTATION_PLAN.md)
- [Standards audit](STANDARDS_AUDIT.md)
- [WebSocket standards release notes](WEBSOCKET_STANDARDS_RELEASE.md)

When historical material conflicts with the current product, the authoritative order is:

1. `V1_PROTOCOL.md` for wire behavior;
2. `V1_ARCHITECTURE.md` for component architecture;
3. current installation/deployment/compatibility guides;
4. historical research/evidence files.

## Optional future formalization

[IANA subprotocol registration notes](IANA_SUBPROTOCOL_REGISTRATION.md) are retained in case MCflare ever grows into a third-party interoperability ecosystem. Registration is **not** a release requirement for MCflare v1.
