# Documentation

You only need a few pages to run MCflare:

- [Installation](INSTALLATION.md) — pick the right JAR and install it.
- [Setup choices](SETUP_CHOICES.md) — Orange Cloud or Cloudflare Tunnel.
- [Deployment](DEPLOYMENT.md) — reverse-proxy and Tunnel examples.
- [Compatibility](COMPATIBILITY.md) — supported Minecraft, Java, and loader versions.
- [Troubleshooting](TROUBLESHOOTING.md) — connection and real-IP problems.
- [FAQ](FAQ.md) — common questions.

If you are just trying to get a server online, start with **Installation → Setup choices → Deployment**.

## How MCflare works

- [Concepts](CONCEPTS.md) — names used throughout the project.
- [Architecture](V1_ARCHITECTURE.md) — client, Cloudflare, gateway, and backend flow.
- [Wire protocol](V1_PROTOCOL.md) — exact `mcflare.v1` WebSocket contract.
- [Real player IP](REAL_IP.md) — Cloudflare visitor metadata and PROXY v1.
- [Low-latency architecture](LOW_LATENCY_ARCHITECTURE.md) — transport and measured path.

`V1_PROTOCOL.md` is the source of truth for interoperability. Historical test notes are not the protocol specification.

## Building and releases

- [Build matrix](BUILD_MATRIX.md)
- [Release process](RELEASE.md)
- [v1.0.0-rc.1 release evidence](RELEASE_EVIDENCE_1.0.0-rc.1.md)
- [Test matrix](TEST_MATRIX.md)
- [Contributing](../CONTRIBUTING.md)

## Maintainer notes

The remaining files in this directory preserve design decisions, standards work, experiments, and regression evidence. They are useful when changing MCflare, but they are not required reading for users.

Useful references include:

- [Design constraints](DESIGN.md)
- [Standards audit](STANDARDS_AUDIT.md)
- [Test evidence](TEST_EVIDENCE_2026-09-01.md)
- [Project knowledge / research log](PROJECT_KNOWLEDGE.md)
- [Completed implementation plan](IMPLEMENTATION_PLAN.md)
- [Migration from Modflared](MIGRATION_FROM_MODFLARED.md)

When an old experiment disagrees with current documentation, follow `V1_PROTOCOL.md`, `V1_ARCHITECTURE.md`, and the current installation/deployment guides.
