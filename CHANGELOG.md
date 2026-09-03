# Changelog

Notable changes to the rebuilt MCflare line are recorded here. The changelog starts with the rebuilt MCflare architecture; earlier Modflared project releases are a separate project line and are not reconstructed here.

## Unreleased

No changes yet.

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
