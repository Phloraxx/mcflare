# Changelog

Notable changes to the rebuilt MCflare line are recorded here. The changelog starts with the rebuilt MCflare architecture; earlier Modflared project releases are a separate project line and are not reconstructed here.

## Unreleased

### Fixed

- Close active client sessions when the shared gateway is stopped, so plugin/server shutdown cannot leave old gateway threads and backend streams alive.
- Prevent a carrier from being attached after its Minecraft `Connection` has already begun disconnecting.
- Preserve thread interruption during route discovery instead of turning an interrupted probe into an ordinary negative-cache result.

### Changed

- Advance ordinary development builds to `1.0.1-dev` after the `1.0.0` stable release.
- Remove the unused client carrier getter from the internal Mixin bridge.
- Run the full CI matrix once per pull request instead of duplicating it for both the branch push and pull-request event; merged `main` still receives its own full CI run.
- Remove unused Maven-publication, IDE-plugin, and source-JAR build plumbing from the Fabric root build.

## 1.0.0 - 2026-09-03

### Highlights

- First stable release of the rebuilt MCflare transport.
- Players join with the normal Minecraft server address and need only the matching MCflare mod; no player-side `cloudflared`, WARP, VPN, Tunnel token, custom launcher, or local proxy is required.
- Fabric/Quilt and NeoForge are supported across Minecraft 1.21.11 and 26.1–26.2, with a Paper/Purpur server plugin for server-side integration.
- Both Cloudflare Orange Cloud and named Cloudflare Tunnel deployments use the same `/mcflare` WebSocket protocol and player experience.
- Trusted Cloudflare visitor metadata can restore the real player address through PROXY protocol v1.
- Authenticated `online-mode=true` world joins are proven through both Orange Cloud and a named Tunnel.

### Changed since 1.0.0-rc.1

- Normalize archive timestamps and file order so clean release builds are byte-reproducible, including Fabric artifacts with nested `core` and `gateway` JARs.
- Verify downstream release artifact identity before optional distribution publishing.
- Add the public GitHub Wiki and simplify the repository landing page around downloads, setup, deployment, and troubleshooting.
- Record the authenticated Mojang/Microsoft `online-mode=true` proof using the published RC artifact.
- No MCflare wire-protocol or Minecraft transport behavior changed from `1.0.0-rc.1`.

## 1.0.0-rc.1 - 2026-09-03

### Added

- Transparent Minecraft TCP transport over `wss://<host>/mcflare` using exact WebSocket subprotocol `mcflare.v1`.
- Fabric/Quilt and NeoForge client/server artifacts across the supported 1.21.11 and 26.1–26.2 families.
- Paper/Purpur server plugin using the shared gateway and native PROXY support.
- Cloudflare visitor-IP restoration through PROXY protocol v1 for IPv4 and IPv6.
- Positive-route persistence to prevent silent downgrade of previously proven MCflare hosts.
- Bounded gateway connection/header/frame handling and structured privacy-conscious observability.
- Orange-proxy and named-Tunnel deployment documentation using one shared wire protocol.
- GitHub CI compatibility matrix and reproducible release-bundle workflow with SHA-256 checksums.

### Changed

- Removed legacy `/.well-known/mcflare` v1 routing in favor of `/mcflare`.
- Removed Tunnel-specific behavior from the client protocol; Orange and Tunnel are now strictly infrastructure choices.
- Reworked documentation around normal player/admin UX instead of internal experiment history.

### Fixed

- Gateway backend-close lifecycle now closes the WebSocket side and releases bounded connection slots.
- RFC6455 subprotocol matching is case-sensitive and unsolicited subprotocol/extensions are rejected.
- WebSocket Close handling follows the RFC6455 closing handshake more closely.
