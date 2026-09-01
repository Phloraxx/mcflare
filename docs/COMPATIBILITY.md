# MCflare Compatibility and Packaging

## Packaging principle

Client/server is not the reason MCflare needs multiple artifacts. Loader APIs and Minecraft-version hooks are.

For a specific compatible Fabric version family, the same Fabric JAR should be installable on the player and Fabric dedicated server. Fabric `fabric.mod.json` supports `environment: "*"`; client-only mixins remain physically scoped to the client and server mixins remain server scoped.

Current/proposed release model:

```text
mcflare-fabric-1.21.11.jar     -> client + Fabric server
mcflare-fabric-26.1-26.2.jar   -> client + Fabric server on either 26.1 or 26.2
mcflare-neoforge-1.21.11.jar   -> client + NeoForge 1.21.11 server
mcflare-neoforge-26.1-26.2.jar -> client + NeoForge server on either 26.1 or 26.2
mcflare-paper-<family>.jar      -> Paper server only, if/when implemented
```

Do not create separate `client.jar` and `server.jar` for the same Fabric/NeoForge target unless a real packaging problem requires it.

## Shared core versus adapters

The transport core is version/loader independent and Java-8-compatible. Version-specific code should only:

- preserve the logical hostname the player typed;
- intercept join/status socket creation;
- attach carrier lifecycle to Minecraft connection lifecycle;
- on dedicated server, start/stop the local gateway;
- on platforms that need it, restore PROXY-protocol source address.

Minecraft packet interpretation does not belong in adapters.

## Minecraft version compatibility

Protocol compatibility is broad because MCflare carries bytes without decoding Minecraft packets. Hook compatibility is narrow because Mojang can change connection methods and anonymous channel initializers between versions.

Claim support only after a real build plus ordinary-direct and protected Status/login regression on that exact loader/version family.

Current proven shared source adapter: Fabric **and NeoForge** 1.21.11, 26.1 and 26.2. The same root Java source compiles/applies across all six loader/version combinations. Each loader's real 1.21.11 production artifact passed standalone server tests, and each loader has one 26.1-baseline JAR proven unchanged on standalone 26.1 and 26.2 servers. See `BUILD_MATRIX.md`.

## Other mods

Most mods need no MCflare integration if they communicate using Minecraft's existing connection. Fabric/Forge/NeoForge custom payloads, plugin messages, login extensions, inventory/entity/chunk traffic, compression, and Minecraft encryption remain ordinary stream bytes.

Likely conflict class: mods that replace the same connection resolver, connect screen, `Connection.connect*`, status pinger, or server listener pipeline. These require explicit compatibility tests.

Client rendering/UI/performance mods are normally unrelated.

Separate-socket services are outside scope. Voice chat, Dynmap HTTP, mod-specific UDP, separate telemetry sockets, and similar services continue to use their own network paths.

## Fabric / Quilt

Fabric is the reference adapter. Quilt Loader commonly aims for Fabric-mod compatibility, but MCflare should not advertise Quilt compatibility until the exact Fabric artifact completes real Quilt Status/login tests. Prefer proving reuse of the Fabric artifact over adding a Quilt-specific codebase.

## NeoForge

NeoForge is now implemented as a thin packaging module over the same Minecraft adapter source used by Fabric. Shared root source contains no NeoForge imports; the only NeoForge-specific Java is a tiny `@Mod("mcflare")` marker. NeoForge 1.21.11, 26.1 and 26.2 have passed direct Status plus integrated WSS -> PROXY -> Status tests. One exact 26.1-baseline NeoForge JAR is runtime-proven on both 26.1 and 26.2.

## Paper / Purpur

Paper is server-only from MCflare's perspective. Players still need a client-side Fabric/NeoForge-compatible MCflare artifact. Paper already has native PROXY protocol support, so a future Paper deployment should prefer the platform's standard setting rather than reimplementing player-IP forwarding.

## Velocity and Minecraft proxies

If MCflare fronts a Minecraft proxy, that proxy becomes the configured Minecraft backend. Prefer the proxy's native HAProxy/PROXY support where available. Do not reinterpret Velocity/Bungee forwarding as MCflare wire protocol.

## Java runtimes

`core/` and `gateway/` target Java 8 for broad deployability. Fabric and NeoForge 1.21.11 artifacts target Java 21; both combined 26.1-26.2 loader artifacts target Java 25.

## Test matrix per adapter

Mandatory:

- clean build;
- server list Status through MCflare;
- full online-mode login;
- ordinary server with MCflare installed;
- server shutdown/restart lifecycle;
- failed local gateway bind does not crash Minecraft;
- IPv4 and IPv6 WSS where available;
- real-IP restoration when server adapter supports it;
- common connection-altering mods/proxies relevant to that ecosystem.

## Reference

Fabric metadata and physical-side behavior: https://docs.fabricmc.net/develop/loader/fabric-mod-json
