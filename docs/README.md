# MCflare documentation

Use this page as the documentation map. Most users only need Installation, Deployment, Compatibility, and Troubleshooting.

## Players and server administrators

- [INSTALLATION.md](INSTALLATION.md) — choose the correct JAR and install it.
- [DEPLOYMENT.md](DEPLOYMENT.md) — expose `/mcflare` with Orange proxy or a named Tunnel.
- [COMPATIBILITY.md](COMPATIBILITY.md) — supported Minecraft/loaders/platforms.
- [TROUBLESHOOTING.md](TROUBLESHOOTING.md) — common connection and real-IP failures.
- [RELEASE.md](RELEASE.md) — how release artifacts are built and verified.
- [MIGRATION_FROM_MODFLARED.md](MIGRATION_FROM_MODFLARED.md) — differences from the original Modflared deployment model.

## Protocol and architecture

- [V1_ARCHITECTURE.md](V1_ARCHITECTURE.md) — current component architecture.
- [V1_PROTOCOL.md](V1_PROTOCOL.md) — frozen v1 WebSocket wire contract.
- [REAL_IP.md](REAL_IP.md) — Cloudflare visitor-IP to PROXY-v1 trust model.
- [BUILD_MATRIX.md](BUILD_MATRIX.md) — loader/version/toolchain matrix.
- [DESIGN.md](DESIGN.md) — design constraints.
- [LOW_LATENCY_ARCHITECTURE.md](LOW_LATENCY_ARCHITECTURE.md) — latency design and measurements.

## Maintainer evidence and historical research

These files are useful for regression work but are not installation documentation:

- [TEST_MATRIX.md](TEST_MATRIX.md)
- [TEST_EVIDENCE_2026-09-01.md](TEST_EVIDENCE_2026-09-01.md)
- [PROJECT_KNOWLEDGE.md](PROJECT_KNOWLEDGE.md)
- [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md)
- [STANDARDS_AUDIT.md](STANDARDS_AUDIT.md)
- [WEBSOCKET_STANDARDS_RELEASE.md](WEBSOCKET_STANDARDS_RELEASE.md)

## Optional future formalization

- [IANA_SUBPROTOCOL_REGISTRATION.md](IANA_SUBPROTOCOL_REGISTRATION.md) — prepared notes if MCflare ever needs formal WebSocket subprotocol registration. This is **not** a hobby-project release requirement.
