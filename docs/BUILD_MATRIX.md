# MCflare Build Matrix

## Goal

Keep one loader-neutral Minecraft adapter source tree and produce the smallest number of binary artifacts actually required by loader/runtime packaging boundaries.

Client/server does **not** split an artifact. Each Fabric or NeoForge artifact contains both client connection hooks and dedicated-server gateway/PROXY hooks.

## Proven release families

| Artifact family | Compile baseline | Runtime versions | Java | Build/mapping mode | Status |
|---|---:|---|---:|---|---|
| `mcflare-fabric-1.21.11` | Minecraft 1.21.11 | 1.21.11 | 21 | legacy Loom-remap + Mojang mappings | production artifact runtime PASS |
| `mcflare-fabric-26.1-26.2` | Minecraft 26.1 | 26.1 and 26.2 | 25 | modern Loom | same exact binary runtime PASS on both |
| `mcflare-neoforge-1.21.11` | NeoForge 21.11.6-beta / MC 1.21.11 | 1.21.11 | 21 | ModDevGradle 2.0.124 + Parchment | production artifact runtime PASS, TCP4/TCP6 PROXY PASS |
| `mcflare-neoforge-26.1-26.2` | NeoForge 26.1.0.1-beta / MC 26.1 | 26.1 and 26.2 | 25 | ModDevGradle 2.0.141 | same SHA-identical binary runtime PASS on both |

Each loader also has a 26.2 head-compatibility CI row. The head rows compile directly against current 26.2 APIs but do not create extra release families while the 26.1-baseline combined artifacts remain valid.

## Shared Java source

The root `src/main/java` adapter source is compiled by both Fabric and NeoForge. It contains no Fabric or NeoForge imports. Loader-specific Java is currently limited to NeoForge's tiny `@Mod("mcflare")` marker class; Fabric requires no Java bootstrap class.

Shared adapter responsibilities are limited to:

- intercepting Minecraft status/join connection establishment;
- using the loader-independent `RouteResolver` and loopback carrier;
- starting/stopping the local gateway on a dedicated server;
- inserting the optional loopback-trusted PROXY-v1 detector into Minecraft's Netty listener.

The RFC6455 transport and gateway remain in `core/` and `gateway/` and are not copied per loader.

## Why 1.21.11 remains a separate binary

The Java source is identical, but the packaging/toolchain boundary is real. Minecraft 1.21.11 uses Java 21 plus legacy remapping/mapping pipelines. Minecraft 26.x uses Java 25 and current unobfuscated/tooling pipelines.

Forcing a single binary across those generations would require additional remap/bootstrap machinery with no transport benefit. A separate 1.21.11 artifact per loader is smaller and more auditable.

## Why 26.1 and 26.2 share one binary

Relevant connection APIs/mixin targets are compatible across 26.1 and 26.2. For each loader, one artifact built against the older 26.1 baseline was installed unchanged in clean standalone 26.1 and 26.2 servers.

The same exact binaries passed:

- mod loading and server mixin application;
- integrated local MCflare gateway startup;
- ordinary direct Minecraft Status;
- `CF-Connecting-IP` -> PROXY v1 -> Minecraft Status.

The NeoForge combined artifact was SHA-256 identical in both test installations.

## PROXY implementation has no extra Netty codec dependency

MCflare speaks standard HAProxy PROXY protocol v1 on the wire but no longer bundles `netty-codec-haproxy`. PROXY v1 is parsed by a bounded in-project ASCII prefix parser (108-byte line maximum), avoiding Netty-version coupling between Minecraft releases.

The parser accepts TCP4/TCP6 literals, never resolves forwarding metadata through DNS, strips the PROXY line before normal Minecraft bytes continue, and leaves non-PROXY direct connections untouched.

## Build properties

Fabric properties:

- `minecraft_version`
- `minecraft_dependency`
- `loader_version`
- `loom_version`
- `adapter_java_version`
- `use_mojang_mappings`
- `artifact_label`

NeoForge properties:

- `neoforge_moddev_version`
- `neo_version`
- `neo_version_range`
- `neoforge_minecraft_version_range`
- `neoforge_java_version`
- `neoforge_artifact_label`
- `neoforge_use_parchment`
- `parchment_minecraft_version`
- `parchment_mappings_version`

`core/` and `gateway/` remain Java-8-compatible regardless of adapter target.

## Default artifacts

Default Fabric build (Java 25):

```bash
./gradlew --no-daemon :core:build :gateway:build :build
```

Expected artifact:

```text
build/libs/mcflare-fabric-26.1-26.2-<version>.jar
```

Default NeoForge build (Java 25):

```bash
./gradlew --no-daemon :core:build :gateway:build :neoforge:build
```

Expected artifact:

```text
neoforge/build/libs/mcflare-neoforge-26.1-26.2-<version>.jar
```

Use explicit project-qualified task paths. In this multi-project Gradle build, an unqualified task such as `runServer` can match tasks in more than one subproject.

## CI policy

One workflow contains two loader-scoped three-row matrices:

Fabric:
1. 1.21.11 release build on Java 21.
2. 26.1-baseline combined 26.1-26.2 release build on Java 25.
3. 26.2 head-compatibility build on Java 25.

NeoForge:
1. 1.21.11 release build on Java 21 + Parchment.
2. 26.1-baseline combined 26.1-26.2 release build on Java 25.
3. 26.2 head-compatibility build on Java 25.

All six exact CI command rows were reproduced successfully on Oracle before this matrix was committed.

Do not create one workflow file or Git branch per Minecraft version. Add a version-specific source set only if the same shared Java source genuinely stops compiling/applying on that family.
