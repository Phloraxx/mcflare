# Contributing to MCflare

MCflare is a focused open-source project. Contributions should keep the architecture simple rather than add infrastructure for its own sake.

## Before changing code

Please read:

- `docs/V1_ARCHITECTURE.md`
- `docs/V1_PROTOCOL.md`
- `docs/BUILD_MATRIX.md`

Important invariants:

- Orange proxy and named Tunnel are deployment choices, never client protocol modes.
- The v1 endpoint is `/mcflare` with exact WebSocket subprotocol `mcflare.v1`.
- Binary payloads are the ordered Minecraft TCP byte stream; do not add custom gameplay framing without a protocol-version change.
- Known MCflare hosts fail closed rather than silently downgrading to an exposed direct origin.
- Do not put Cloudflare/Tunnel credentials or `cloudflared` inside the player mod.
- Keep loader-specific code thin; core RFC6455/discovery/gateway behavior belongs in shared modules.

## Development setup

Use the checked-in Gradle wrapper. Current builds need both Java 21 and Java 25 depending on the matrix row.

Default Fabric 26.1–26.2 build:

```bash
./gradlew --no-daemon :core:build :gateway:build :build
```

Paper:

```bash
./gradlew --no-daemon :core:build :gateway:build :paper:build
```

NeoForge:

```bash
./gradlew --no-daemon :core:build :gateway:build :neoforge:build
```

GitHub Actions is authoritative for the complete Fabric/NeoForge/Paper matrix.

## Pull requests

Keep PRs focused. Include:

- the behavior being changed and why;
- affected loader/version families;
- tests added or updated;
- whether wire behavior, discovery/pinning, real-IP handling, or deployment assumptions changed.

Do not include credentials, raw player IPs, Minecraft/Microsoft auth tokens, production host secrets, or unrelated server logs in commits/issues.

Large protocol or architecture ideas should start in [GitHub Discussions](https://github.com/Phloraxx/mcflare/discussions) before implementation. Incompatible wire changes require a new protocol version rather than silently changing `mcflare.v1`.

## Documentation

User-facing behavior belongs in `README.md` and the guided docs such as `docs/INSTALLATION.md`, `docs/SETUP_CHOICES.md`, `docs/FAQ.md`, `docs/DEPLOYMENT.md`, or `docs/TROUBLESHOOTING.md`. Deep experimental evidence should stay out of the onboarding path and go in the maintainer evidence docs.

Keep the root README text-first. Conceptual artwork belongs under `docs/assets/` and should be paired with authoritative text or Mermaid/code examples so documentation remains searchable, accessible, and useful without the image.

Before opening a documentation PR, run:

```bash
python3 scripts/check_docs.py
```

The checker validates local Markdown paths/anchors, required documentation artwork, and the README artwork boundary.
