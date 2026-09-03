# Compatibility and Packaging

MCflare's wire transport is loader-independent, but Minecraft integration hooks and loader packaging are version-sensitive. Support is therefore expressed as a small set of tested artifact families.

## Supported artifact families

| Artifact family | Player | Dedicated server | Java |
|---|---:|---:|---:|
| Fabric / Quilt 1.21.11 | Yes | Yes | 21 |
| Fabric / Quilt 26.1–26.2 | Yes | Yes | 25 |
| NeoForge 1.21.11 | Yes | Yes | 21 |
| NeoForge 26.1–26.2 | Yes | Yes | 25 |
| Paper / Purpur plugin | No | Yes | 21 |

Release filenames are versioned, for example:

```text
mcflare-fabric-1.21.11-<release>.jar
mcflare-fabric-26.1-26.2-<release>.jar
mcflare-neoforge-1.21.11-<release>.jar
mcflare-neoforge-26.1-26.2-<release>.jar
mcflare-paper-<release>.jar
```

Quilt uses the Fabric artifacts. Paper/Purpur players still need a supported Fabric/Quilt or NeoForge client mod.

## Why player and server do not need separate Fabric/NeoForge JARs

Client/server is not the reason MCflare has multiple binaries. Loader APIs and Minecraft-version hooks are.

For a supported Fabric or NeoForge family, the same JAR can be installed on:

```text
player mods/   ↔   dedicated server mods/
```

Physical-side hooks are isolated internally so client-only code does not run as dedicated-server behavior and vice versa.

Do not create separate `client.jar` / `server.jar` variants unless a real packaging boundary requires it.

## Why there are still multiple version families

MCflare does not decode Minecraft gameplay packets, which makes the transport broadly version-neutral.

Minecraft connection **hooks** are different. Mojang can change:

- connection creation methods;
- server-list Status paths;
- listener/channel initialization;
- mappings/signatures;
- minimum Java runtime.

A JAR is therefore claimed compatible only after the integration itself is built and exercised on that family.

## Shared core versus adapters

### Shared core/gateway

Loader-independent code owns:

- RFC 6455 transport;
- route selection and positive pins;
- loopback carrier;
- gateway WebSocket lifecycle;
- PROXY-v1 codec;
- operational logging/capacity behavior.

`core/` and `gateway/` target Java 8 for broad standalone deployability.

### Minecraft adapters

Version/loader-specific code should only do what Minecraft integration requires:

- preserve the logical hostname the player typed;
- intercept join/status socket creation;
- attach carrier lifecycle to Minecraft connection lifecycle;
- start/stop the gateway on dedicated servers;
- restore PROXY metadata on platforms that need MCflare's parser.

Minecraft packet interpretation does not belong in adapters.

## Fabric

Fabric is the reference loader integration.

Proven families:

- dedicated 1.21.11 artifact;
- one combined 26.1–26.2 artifact tested unchanged on both versions.

The same family artifact serves client and dedicated server.

## Quilt

Quilt does not add another MCflare binary family.

The exact matching Fabric artifacts have been runtime-tested on Quilt for the documented families, including protected WSS/PROXY Status behavior.

Use:

```text
Fabric MCflare JAR → Quilt client/server
```

## NeoForge

NeoForge is a thin packaging/integration layer over the same shared Minecraft adapter source used by Fabric where possible.

Proven families:

- dedicated 1.21.11 artifact;
- one combined 26.1–26.2 artifact tested unchanged on both versions.

The same family artifact serves client and dedicated server.

## Paper and Purpur

Paper/Purpur support is server-only from MCflare's perspective.

One Java-21 plugin JAR owns gateway lifecycle across the tested Paper/Purpur family. Real-IP restoration uses the platform's native:

```yaml
proxies:
  proxy-protocol: true
```

rather than a second MCflare-specific network decoder.

Players connecting to Paper/Purpur still need a supported Fabric/Quilt or NeoForge MCflare client mod.

## Other Minecraft mods

Most mods need no special MCflare integration if they communicate through Minecraft's existing connection.

Examples normally carried transparently:

- custom payloads;
- plugin messages;
- login extensions;
- compression/encryption;
- chunks/entities;
- inventory/gameplay traffic.

### Likely conflict class

Explicit testing is appropriate for mods that replace or heavily modify the same areas MCflare hooks, such as:

- connection resolver;
- Join Server/connect screen;
- `Connection.connect*` behavior;
- server-list Status pinger;
- server listener/channel initializer.

Client rendering, UI, shader, and performance mods are usually unrelated to the network hook.

## Separate-socket services

MCflare carries only Minecraft's own Java connection.

Separate services remain separate:

- voice-chat UDP/TCP media sockets;
- Dynmap/web-map HTTP;
- telemetry sockets;
- unrelated game/service ports.

A mod may use Minecraft plugin/custom payload messages for control while still opening a separate voice/media socket; only the Minecraft-stream portion travels through MCflare.

## Minecraft proxies

If MCflare fronts a Minecraft proxy, that proxy becomes the configured backend.

Prefer its native HAProxy/PROXY support when available. Do not reinterpret Velocity/Bungee forwarding protocols as part of `mcflare.v1`.

MCflare's WebSocket transport ends at the gateway; backend-specific forwarding begins after that boundary.

## Java runtimes

| Component | Target/runtime |
|---|---|
| `core/` | Java 8 bytecode |
| `gateway/` | Java 8 bytecode |
| Fabric/Quilt 1.21.11 | Java 21 |
| NeoForge 1.21.11 | Java 21 |
| Fabric/Quilt 26.1–26.2 | Java 25 |
| NeoForge 26.1–26.2 | Java 25 |
| Paper/Purpur plugin | Java 21 |

Use the Java runtime required by the Minecraft/server family rather than assuming the core's Java-8 target makes modern Minecraft itself runnable on Java 8.

## What “supported” means here

A release family should have, at minimum:

- clean build;
- server-list Status through MCflare;
- full LOGIN → CONFIGURATION → GAME transport proof;
- ordinary non-MCflare server regression;
- server lifecycle/bind-failure behavior;
- IPv4/IPv6 WSS and real-IP tests where applicable;
- release packaging/metadata verification.

The repository's exact CI properties and matrix rationale are in [BUILD_MATRIX.md](BUILD_MATRIX.md).

Detailed runtime evidence is in [TEST_MATRIX.md](TEST_MATRIX.md) and [TEST_EVIDENCE_2026-09-01.md](TEST_EVIDENCE_2026-09-01.md).

## Optional compatibility expansion

Useful future checks, but not current hobby-release blockers:

- authenticated `online-mode=true` login;
- popular connection-altering mods/proxies relevant to each ecosystem;
- additional graphical-client/world-generation stress;
- newer Minecraft families after their hooks/toolchains stabilize.

## Related docs

- [Installation](INSTALLATION.md)
- [Choose your setup](SETUP_CHOICES.md)
- [Build matrix](BUILD_MATRIX.md)
- [v1 architecture](V1_ARCHITECTURE.md)

## Reference

- [Fabric `fabric.mod.json` documentation](https://docs.fabricmc.net/develop/loader/fabric-mod-json)
