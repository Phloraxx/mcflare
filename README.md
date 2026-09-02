# MCflare

![MCflare icon](src/main/resources/assets/mcflare/icon.png)

[![CI](https://github.com/Phloraxx/mcflare/actions/workflows/ci.yml/badge.svg)](https://github.com/Phloraxx/mcflare/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

**Transparent Minecraft Java transport over Cloudflare WebSockets.**

MCflare lets players join a Minecraft server with the normal hostname while the Minecraft TCP byte stream travels inside a standards-based WebSocket through Cloudflare. Players do not run `cloudflared`, a VPN, WARP, or a custom launcher.

```text
Minecraft client
    │
    │ normal Join Server UX
    ▼
MCflare client adapter
    │  wss://play.example.com/mcflare
    │  Sec-WebSocket-Protocol: mcflare.v1
    ▼
Cloudflare ── Orange proxy or named Tunnel
    ▼
MCflare gateway
    │  Minecraft TCP bytes + optional PROXY v1
    ▼
Minecraft server
```

## Player experience

1. Install the MCflare JAR for your loader and Minecraft version.
2. Add `play.example.com` to Minecraft normally.
3. Click **Join Server**.

There is no MCflare account, token, proxy address, or separate connection UI. Ordinary non-MCflare servers continue to use normal Minecraft TCP.

## Server quick start

| Platform | MCflare artifact | Side |
|---|---|---|
| Fabric | `mcflare-fabric-…jar` | client + server |
| Quilt | Fabric artifact | client + server |
| NeoForge | `mcflare-neoforge-…jar` | client + server |
| Paper | `mcflare-paper-…jar` | server only |
| Purpur | Paper artifact | server only |

Install the server artifact, configure the MCflare gateway listener, then route **only** `/mcflare` from your Minecraft hostname to that listener through either Cloudflare's normal proxied HTTP/WebSocket path or a named Cloudflare Tunnel.

See **[Installation](docs/INSTALLATION.md)** and **[Deployment](docs/DEPLOYMENT.md)** for the complete setup.

## Supported Minecraft families

| Loader/platform | Minecraft | Java | Artifact strategy |
|---|---:|---:|---|
| Fabric / Quilt | 1.21.11 | 21 | dedicated JAR |
| Fabric / Quilt | 26.1–26.2 | 25 | one shared JAR |
| NeoForge | 1.21.11 | 21 | dedicated JAR |
| NeoForge | 26.1–26.2 | 25 | one shared JAR |
| Paper / Purpur | 1.21.11, 26.1.x, 26.2 | 21 | one server plugin JAR |

The same Fabric or NeoForge JAR is used on the client and dedicated server. Client-only and server-only hooks are isolated internally.

## Cloudflare deployment choices

**Orange proxy** and **named Tunnel** are deployment choices, not different MCflare protocols.

- **Orange:** Cloudflare proxies HTTPS/WebSocket traffic to your reverse proxy, which routes `/mcflare` to the gateway. Protect the origin separately; proxied DNS alone is not an origin-firewall policy.
- **Named Tunnel:** `cloudflared` runs on server infrastructure and maps `/mcflare` to the same gateway. Nothing Tunnel-specific is shipped to players.

Both use exactly:

```text
wss://<minecraft-host>/mcflare
Sec-WebSocket-Protocol: mcflare.v1
```

Binary WebSocket payloads are the ordered Minecraft TCP byte stream. There is no custom gameplay framing or JSON control protocol.

## Real player IP

Cloudflare terminates the public WebSocket, so the gateway can translate Cloudflare visitor metadata into standard HAProxy **PROXY protocol v1** before the Minecraft stream. IPv4 and IPv6 restoration are tested.

Fabric/Quilt and NeoForge use MCflare's bounded loopback-trusted PROXY-v1 parser. Paper/Purpur use their native HAProxy PROXY support.

See **[Real player IP](docs/REAL_IP.md)** for the trust boundary and server configuration.

## Downloads and releases

There is not yet an official GitHub Release for the rebuilt MCflare v1 line. GitHub Releases will be the authoritative source for current MCflare binaries.

Release builds are produced by the repository's tag/manual release workflow. A release bundle contains the five supported binary families plus `SHA256SUMS.txt`.

See **[Release process](docs/RELEASE.md)**.

## What MCflare intentionally does not do

MCflare transports only Minecraft's own Java connection. It does not provide:

- a central MCflare relay/SaaS service;
- Cloudflare API or Tunnel credentials inside the mod;
- `cloudflared` on player machines;
- voice-chat UDP or other side-channel tunnelling;
- VPN/WARP functionality;
- automatic continuation of an already-broken Minecraft session.

If a WebSocket dies, Minecraft disconnects normally and the player reconnects with a fresh session.

## Documentation

**Start here:**

- [Installation](docs/INSTALLATION.md)
- [Deployment: Orange and named Tunnel](docs/DEPLOYMENT.md)
- [Compatibility](docs/COMPATIBILITY.md)
- [Troubleshooting](docs/TROUBLESHOOTING.md)
- [Release process](docs/RELEASE.md)
- [Migrating from Modflared](docs/MIGRATION_FROM_MODFLARED.md)
- [Documentation index](docs/README.md)

**Protocol / implementation:**

- [v1 architecture](docs/V1_ARCHITECTURE.md)
- [v1 wire protocol](docs/V1_PROTOCOL.md)
- [Real-IP design](docs/REAL_IP.md)
- [Build matrix](docs/BUILD_MATRIX.md)

Detailed experiments and test evidence are retained under `docs/` for maintainers, but are not required reading for normal installation.

## Building

Use the Gradle wrapper. The default current-generation Fabric build uses Java 25.

```bash
./gradlew --no-daemon :core:build :gateway:build :build
./gradlew --no-daemon :core:build :gateway:build :neoforge:build
./gradlew --no-daemon :core:build :gateway:build :paper:build
```

The complete version/loader matrix is enforced in GitHub Actions. See [BUILD_MATRIX.md](docs/BUILD_MATRIX.md) and [CONTRIBUTING.md](CONTRIBUTING.md).

## Project status

MCflare is a hobby project approaching its first release of the rebuilt architecture. The core transport, downgrade-resistant route pinning, real-IP handoff, loader/platform matrix, long-session behavior, failure cleanup, and higher-scale full-protocol GAME-state concurrency have all been exercised.

IANA registration of `mcflare.v1`, a naturally occurring Cloudflare-edge WebSocket termination, larger graphical-player stress, and Mojang `online-mode=true` validation are **optional future validation/formalization**, not release blockers for this hobby project.

## Support and security

- Bugs and reproducible interoperability problems: [GitHub Issues](https://github.com/Phloraxx/mcflare/issues)
- Usage questions: [SUPPORT.md](SUPPORT.md)
- Security reports: [SECURITY.md](SECURITY.md)
- Contributions: [CONTRIBUTING.md](CONTRIBUTING.md)
- Community conduct: [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)

Never post Cloudflare credentials, Minecraft/Microsoft authentication tokens, or raw player IP addresses in an issue.

## License and attribution

MCflare is MIT-licensed. It began from selected MIT-licensed ideas and Minecraft integration code from **Modflared** by Rafael / HttpRafa; attribution is preserved in [NOTICE.md](NOTICE.md) and [LICENSE](LICENSE).

MCflare is an independent hobby project and is not affiliated with or endorsed by Mojang Studios, Microsoft, or Cloudflare.
