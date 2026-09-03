# MCflare

[![CI](https://github.com/Phloraxx/mcflare/actions/workflows/ci.yml/badge.svg)](https://github.com/Phloraxx/mcflare/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

**Transparent Minecraft Java transport over Cloudflare WebSockets.**

MCflare lets players join a Minecraft Java server with the normal hostname while the Minecraft TCP byte stream travels through Cloudflare as a standard WebSocket. The player keeps the normal **Join Server** experience; the administrator can use either a normal Cloudflare proxied HTTPS path or a named Cloudflare Tunnel.

MCflare is useful when you want a Minecraft connection to travel through Cloudflare's HTTP/WebSocket infrastructure instead of requiring a player-side TCP tunnel, VPN, WARP setup, or `cloudflared` process.

> MCflare is a hobby project. It is independent of and not endorsed by Mojang Studios, Microsoft, or Cloudflare.

## What it looks like

```text
Minecraft client
    │
    │ normal server hostname
    ▼
MCflare client adapter
    │
    │ wss://play.example.com/mcflare
    │ Sec-WebSocket-Protocol: mcflare.v1
    │ binary frames = Minecraft TCP bytes
    ▼
Cloudflare
    │
    │ Orange proxy or named Tunnel
    ▼
MCflare gateway
    │
    │ optional PROXY v1 real-IP handoff
    ▼
Minecraft server
```

There is no custom gameplay protocol. MCflare carries the ordered Minecraft TCP byte stream without decoding Minecraft packets.

## Player experience

1. Install the MCflare JAR for your loader and Minecraft version.
2. Add the server to Minecraft normally, for example `play.example.com`.
3. Click **Join Server**.

Players do **not** configure:

- Cloudflare accounts or API keys;
- Tunnel tokens;
- `cloudflared`;
- WSS URLs or proxy addresses;
- a separate MCflare launcher or account.

Ordinary non-MCflare servers continue to use normal Minecraft TCP.

## Server administrator experience

1. Install the matching Fabric/Quilt/NeoForge server mod or the Paper/Purpur plugin.
2. Route only `/mcflare` from the public Minecraft hostname to the MCflare gateway.
3. Start Minecraft normally.

Choose either:

- **Orange proxy:** Cloudflare proxied HTTPS/WebSocket traffic → your reverse proxy → MCflare.
- **Named Tunnel:** Cloudflare → `cloudflared` on server infrastructure → MCflare.

The client uses the same MCflare protocol either way.

**Start here:** [Installation](docs/INSTALLATION.md) · [Choose a setup](docs/SETUP_CHOICES.md) · [Deployment](docs/DEPLOYMENT.md) · [FAQ](docs/FAQ.md)

## Supported platforms

| Platform | Client | Server | Minecraft / Java |
|---|---:|---:|---|
| Fabric | Yes | Yes | 1.21.11 / Java 21; 26.1–26.2 / Java 25 |
| Quilt | Yes | Yes | uses the matching Fabric artifact |
| NeoForge | Yes | Yes | 1.21.11 / Java 21; 26.1–26.2 / Java 25 |
| Paper | No | Yes | one Java-21 server plugin across the tested family |
| Purpur | No | Yes | uses the Paper-compatible plugin |

The same Fabric or NeoForge JAR is used on the player and dedicated server for a supported version family. Paper/Purpur is server-only; players still use Fabric/Quilt or NeoForge.

See [Compatibility](docs/COMPATIBILITY.md) for exact packaging and test coverage.

## Why WebSockets?

Cloudflare's normal HTTP reverse-proxy path does not speak the Minecraft Java TCP protocol directly. WebSockets provide a standards-based bidirectional byte transport that Cloudflare can proxy over HTTPS.

MCflare therefore wraps the existing Minecraft connection rather than inventing a second gameplay protocol:

```text
Minecraft TCP bytes → WebSocket binary frames → Cloudflare → WebSocket binary frames → Minecraft TCP bytes
```

This keeps Minecraft compression, encryption, login extensions, plugin messages, custom payloads, chunks, entities, inventory traffic, and most mod traffic inside the original Minecraft stream.

## Real player IP

Cloudflare terminates the public WebSocket, so the backend would normally see the gateway/proxy address. MCflare can translate trusted Cloudflare visitor metadata into standard **HAProxy PROXY protocol v1** before the Minecraft byte stream.

IPv4 and IPv6 restoration are tested. Fabric/Quilt and NeoForge use MCflare's bounded loopback-trusted parser; Paper/Purpur use native HAProxy PROXY support.

See [Real player IP](docs/REAL_IP.md) before enabling this feature—the forwarding headers are only trustworthy when the gateway's ingress is actually controlled by Cloudflare or trusted proxy infrastructure.

## Downgrade resistance

Once a hostname has successfully proven that it supports MCflare, the client stores that positive result locally. Future connections require the protected WSS path instead of silently falling back to an accidentally exposed raw Minecraft origin.

Ordinary/negative discovery results are not given the same durable trust.

See [Concepts](docs/CONCEPTS.md#route-discovery-and-positive-pins) and [Troubleshooting](docs/TROUBLESHOOTING.md#a-hostname-was-intentionally-converted-back-to-ordinary-minecraft).

## Downloads and releases

The rebuilt MCflare v1 line starts with **`v1.0.0-rc.1`**. GitHub Releases is the authoritative source for release binaries; do not download release JARs from random mirrors.

Each release contains five supported JAR families plus `SHA256SUMS.txt`. The release workflow builds, verifies, checksums, and publishes those artifacts from the tagged commit.

See [Release process](docs/RELEASE.md).

## Documentation

### Use MCflare

- [Installation](docs/INSTALLATION.md)
- [Choose your setup](docs/SETUP_CHOICES.md)
- [Deployment: Orange and named Tunnel](docs/DEPLOYMENT.md)
- [Compatibility](docs/COMPATIBILITY.md)
- [Troubleshooting](docs/TROUBLESHOOTING.md)
- [FAQ](docs/FAQ.md)

### Understand MCflare

- [Concepts](docs/CONCEPTS.md)
- [v1 architecture](docs/V1_ARCHITECTURE.md)
- [v1 wire protocol](docs/V1_PROTOCOL.md)
- [Real-IP design](docs/REAL_IP.md)
- [Documentation index](docs/README.md)

Detailed experiments and acceptance evidence remain in `docs/` for maintainers and regression work; normal users do not need to read them.

## What MCflare intentionally does not do

MCflare is **not**:

- a central relay/SaaS service;
- a VPN or WARP replacement;
- a generic TCP/UDP tunnel;
- a voice-chat transport;
- a Cloudflare DNS/Tunnel management client;
- a player-side `cloudflared` installer;
- an account system;
- a mechanism for resuming a Minecraft session after its underlying connection has already died.

If the WebSocket dies, Minecraft disconnects normally and the player reconnects with a fresh session.

## Project status

The rebuilt v1 transport is release-candidate quality for this hobby project. The repository documents successful testing of ordinary direct-server behavior, full login/configuration/GAME transport, Orange and named-Tunnel delivery, real IPv4/IPv6 restoration, long sessions, failure cleanup, concurrency/churn, loader/platform compatibility, and release packaging.

IANA registration of `mcflare.v1`, a naturally occurring Cloudflare-edge WebSocket termination, authenticated Mojang `online-mode=true` validation, and larger graphical/world-generation stress are optional future validation—not release blockers.

## Building

Use the Gradle wrapper. The default current-generation Fabric build uses Java 25.

```bash
./gradlew --no-daemon :core:build :gateway:build :build
./gradlew --no-daemon :core:build :gateway:build :neoforge:build
./gradlew --no-daemon :core:build :gateway:build :paper:build
```

The complete loader/version matrix is enforced by GitHub Actions. See [Build matrix](docs/BUILD_MATRIX.md) and [Contributing](CONTRIBUTING.md).

## Support, security, and contributing

- Bugs and reproducible interoperability problems: [GitHub Issues](https://github.com/Phloraxx/mcflare/issues)
- Usage questions: [SUPPORT.md](SUPPORT.md)
- Security reports: [SECURITY.md](SECURITY.md)
- Contributions: [CONTRIBUTING.md](CONTRIBUTING.md)
- Community conduct: [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)

Never post Cloudflare credentials, Minecraft/Microsoft authentication tokens, raw player public IP addresses, or unrelated infrastructure secrets in an issue.

## License and attribution

MCflare is MIT-licensed. It began from selected MIT-licensed ideas and Minecraft integration code from **Modflared** by Rafael / HttpRafa; attribution is preserved in [NOTICE.md](NOTICE.md) and [LICENSE](LICENSE).
