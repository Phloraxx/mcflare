# Contributing

MCflare is a small hobby project, but reproducible bug reports and focused pull requests are welcome.

## Before opening an issue

For setup questions, use [GitHub Discussions](https://github.com/Phloraxx/mcflare/discussions). For a reproducible bug, use [GitHub Issues](https://github.com/Phloraxx/mcflare/issues).

Include Minecraft version, loader/server platform, MCflare version, Orange/Tunnel/direct path, whether `/mcflare` upgrades successfully, and the smallest useful log excerpt.

Do not include Cloudflare credentials, Tunnel tokens, Microsoft/Minecraft auth tokens, private keys, or raw player IP addresses.

## Building

The repository uses Gradle and a CI matrix across the supported Fabric, NeoForge, and Paper/Purpur families. Follow [`CONTRIBUTING.md`](https://github.com/Phloraxx/mcflare/blob/main/CONTRIBUTING.md) for the current commands and pull-request expectations.

## Technical reading

Start with:

- [`docs/V1_ARCHITECTURE.md`](https://github.com/Phloraxx/mcflare/blob/main/docs/V1_ARCHITECTURE.md)
- [`docs/V1_PROTOCOL.md`](https://github.com/Phloraxx/mcflare/blob/main/docs/V1_PROTOCOL.md)
- [`docs/BUILD_MATRIX.md`](https://github.com/Phloraxx/mcflare/blob/main/docs/BUILD_MATRIX.md)
- [`docs/TEST_MATRIX.md`](https://github.com/Phloraxx/mcflare/blob/main/docs/TEST_MATRIX.md)

Security reports should follow [`SECURITY.md`](https://github.com/Phloraxx/mcflare/blob/main/SECURITY.md).
