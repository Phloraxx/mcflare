# MCflare Fabric Build Matrix

## Goal

Keep one Fabric Java source tree and produce the smallest number of binary artifacts actually required by Minecraft's runtime/mapping boundaries.

Client/server does **not** split the artifact: each Fabric artifact contains both client hooks and dedicated-server gateway/PROXY hooks.

## Proven release families

| Artifact family | Compile baseline | Runtime versions | Java | Loom mode | HAProxy codec | Status |
|---|---:|---|---:|---|---|---|
| `mcflare-fabric-1.21.11` | 1.21.11 | 1.21.11 | 21 | legacy `fabric-loom-remap` + Mojang mappings | Netty 4.2.7 | production artifact runtime PASS |
| `mcflare-fabric-26.1-26.2` | 26.1 | 26.1 and 26.2 | 25 | modern unobfuscated Loom | Netty 4.2.7 | same binary runtime PASS on both |

A third CI row compiles against 26.2/Loader 0.19.3/Loom 1.17/Netty 4.2.15 as a head-compatibility check, but it is not intended to create a third release artifact while the combined 26.x binary remains valid.

## Why 1.21.11 is a separate binary

The Java source is currently identical, but the packaging pipeline is not. Minecraft 1.21.11 still uses the legacy obfuscated/remapped Fabric production path and Java 21. The 26.x line uses the modern unobfuscated runtime and Java 25.

Trying to force one cross-generation binary would require extra class/remap/bootstrap machinery for little user benefit. Two Fabric artifacts are simpler and more auditable.

## Why 26.1 and 26.2 share one binary

The relevant connection APIs and mixin redirect descriptors were bytecode-audited and are identical across 26.1 and 26.2. A JAR compiled against the older 26.1 API and Netty 4.2.7 baseline was installed unchanged in standalone Fabric 26.1 and Fabric 26.2 servers.

Both runtimes passed:

- mod loading;
- server mixin application;
- integrated local MCflare gateway startup;
- `CF-Connecting-IP` -> PROXY v1 -> Minecraft Status;
- ordinary Minecraft server Status path.

Therefore the release metadata can safely declare `>=26.1 <26.3` for this tested family.

## Build properties

The root Fabric adapter is parameterized by Gradle properties rather than branches:

- `minecraft_version` - compile baseline;
- `minecraft_dependency` - Fabric metadata runtime range;
- `loader_version` - Loader compile/runtime minimum;
- `loom_version` - matching Loom generation;
- `netty_haproxy_version` - HAProxy codec aligned with the baseline Minecraft Netty API;
- `adapter_java_version` - Java class-file level required by that Minecraft family;
- `use_mojang_mappings` - selects legacy Loom-remap/Mojang mappings when required;
- `artifact_label` - output filename family label.

`core/` and `gateway/` remain Java-8-compatible regardless of the adapter target.

## Default build

The repository defaults to the combined current 26.x release family:

```bash
./gradlew --no-daemon clean build
```

Expected artifact:

```text
build/libs/mcflare-fabric-26.1-26.2-<version>.jar
```

## 1.21.11 build

```bash
./gradlew --no-daemon clean build \
  -Pminecraft_version=1.21.11 \
  -Pminecraft_dependency='~1.21.11' \
  -Ploader_version=0.18.2 \
  -Ploom_version=1.14-SNAPSHOT \
  -Pnetty_haproxy_version=4.2.7.Final \
  -Padapter_java_version=21 \
  -Puse_mojang_mappings=true \
  -Partifact_label=1.21.11
```

## CI policy

Use one GitHub Actions workflow with a matrix. Do not create one workflow file or one Git branch per Minecraft version.

Current rows:

1. Fabric 1.21.11 release build.
2. Fabric 26.1-26.2 release-baseline build.
3. Fabric 26.2 head-compatibility build.

Add a new row only when it catches a real compatibility boundary. Add a version-specific source set only if the same Java source genuinely cannot compile/apply on that family.

## Future loaders

NeoForge and Paper are separate loader/platform artifacts, not separate copies of MCflare transport logic. They should reuse `core/` and `gateway/` and keep only loader lifecycle/network hooks in their adapters.
