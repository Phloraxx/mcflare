# MCflare 1.0.1 release evidence

This record captures the package, runtime, and authenticated-join gate for the `1.0.1` patch release.

## Release source and dry runs

The first two `release` workflow dry runs used the same source commit and embedded version `1.0.1`:

- source commit: `619dcfaa2cd2bb198d6d0d4fbc9cfae32386ac8a`
- workflow run `33789757538`: PASS
- workflow run `33793974402`: PASS
- Fabric 1.21.11: PASS
- Fabric 26.1–26.2: PASS
- NeoForge 1.21.11: PASS
- NeoForge 26.1–26.2: PASS
- Paper/Purpur plugin: PASS
- release bundle verification: PASS
- GitHub Release publish: correctly skipped for both manual dry runs

Both runs produced the same five JARs and `SHA256SUMS.txt`. Every JAR was byte-for-byte identical between the two runs.

## Reproducible artifact hashes

```text
cff15f7d9d61ecc67a1bd75cf4fe8ec2d310be5178fc45cd8aa7d779a864fff8  mcflare-fabric-1.21.11-1.0.1.jar
ebf52c77b4134a7f9084ac5b722f745c847da69503c28769868c66893f2824b0  mcflare-fabric-26.1-26.2-1.0.1.jar
a796be2801e6a3cf7d3c9175438c0790fa74045d2792cbdd9b228a397ce6275d  mcflare-neoforge-1.21.11-1.0.1.jar
cdf4bd092ee947458ee0b6949133accdb6421c7be9682b91e2963cd4c985ffa2  mcflare-neoforge-26.1-26.2-1.0.1.jar
a11fe7a3e3c675e1f4670ae733e57fed1e79ec1cfe9406ce9fdf48900986e924  mcflare-paper-1.0.1.jar
```

The workflow and an independent local verification both accepted `SHA256SUMS.txt`. Fabric, NeoForge, and Paper metadata all advertised `1.0.1`.

## Exact-package runtime smoke

The JARs downloaded from workflow run `33789757538` were copied into isolated Oracle ARM64 loader/server installations. They were not rebuilt locally.

| Packaged artifact | Runtime | Result |
|---|---|---|
| Fabric 1.21.11 | Minecraft 1.21.11 | direct Status + `/mcflare` Status PASS |
| Fabric 26.1–26.2 | Minecraft 26.1 | direct Status + `/mcflare` Status PASS |
| Fabric 26.1–26.2 | Minecraft 26.2 | direct Status + `/mcflare` Status PASS |
| Fabric 1.21.11 | Quilt 1.21.11 | direct Status + `/mcflare` Status PASS |
| Fabric 26.1–26.2 | Quilt 26.1 | direct Status + `/mcflare` Status PASS |
| Fabric 26.1–26.2 | Quilt 26.2 | direct Status + `/mcflare` Status PASS |
| NeoForge 1.21.11 | Minecraft 1.21.11 | direct Status + `/mcflare` Status PASS |
| NeoForge 26.1–26.2 | Minecraft 26.1 | direct Status + `/mcflare` Status PASS |
| NeoForge 26.1–26.2 | Minecraft 26.2 | direct Status + `/mcflare` Status PASS |
| Paper plugin | Paper 1.21.11 | `/mcflare` + PROXY-v1 IPv4/IPv6 Status PASS |
| Paper plugin | Paper 26.1.2 | `/mcflare` + PROXY-v1 IPv4/IPv6 Status PASS |
| Paper plugin | Paper 26.2 | `/mcflare` + PROXY-v1 IPv4/IPv6 Status PASS |
| Paper plugin | Purpur 1.21.11 | `/mcflare` + PROXY-v1 IPv4/IPv6 Status PASS |
| Paper plugin | Purpur 26.1.2 | `/mcflare` + PROXY-v1 IPv4/IPv6 Status PASS |
| Paper plugin | Purpur 26.2 | `/mcflare` + PROXY-v1 IPv4/IPv6 Status PASS |

All copied JAR hashes matched the reproducible bundle. The smoke environment was shut down afterward; no proof JVM or test listener remained.

## Authenticated online-mode proof

Because `1.0.1` changes runtime transport code, authentication was re-tested with the exact package instead of relying on the earlier release-candidate proof.

A clean Minecraft 26.2 Prism Launcher instance on macOS used Fabric Loader 0.19.3 and only:

```text
mcflare-fabric-26.1-26.2-1.0.1.jar
ebf52c77b4134a7f9084ac5b722f745c847da69503c28769868c66893f2824b0
```

The client used an existing Microsoft/Minecraft account. A fresh isolated vanilla 26.2 server on Oracle ran with:

```text
online-mode=true
enforce-secure-profile=true
```

The standalone gateway ran directly from the `core-1.0.1.jar` and `gateway-1.0.1.jar` nested inside the same reproducible Fabric release JAR. It listened on the existing test port and forwarded to the isolated server with `proxyProtocol=false`, keeping the Mojang authentication proof separate from the already-tested real-IP path.

Before launching the graphical client, the exact release `Rfc6455Client` returned the isolated server's distinct Status response through both public test routes:

- true Orange Cloudflare proxy: PASS;
- named Cloudflare Tunnel: PASS.

The same clean client then joined the isolated server through each route separately. For both connections the server's `User Authenticator` resolved the authenticated player UUID, logged the player in, and reached `joined the game`.

- true Orange Cloudflare proxy, authenticated world join: PASS;
- named Cloudflare Tunnel, authenticated world join: PASS.

This re-proves the normal Minecraft encryption and Microsoft/Mojang session-authentication exchange with the exact `1.0.1` package on both supported Cloudflare delivery modes.

## Restoration

The disposable Prism instance, graphical clients, isolated server, and temporary standalone gateway were stopped and removed after the proof. Test ports `25585` and `25588` were free afterward. The existing production/private-origin Minecraft listener on `25565` remained present and was not restarted or reconfigured.

No Cloudflare route was changed for this proof: the existing Orange and named-Tunnel test routes already pointed `/mcflare` at the standalone test gateway.

## Release decision

The `1.0.1` package gate passes: normal CI is green, two independent release builds are byte-reproducible, all five binary families have version-correct metadata, exact packaged artifacts pass the supported loader/server matrix, IPv4/IPv6 PROXY-v1 restoration passes on Paper/Purpur, and real authenticated `online-mode=true` world joins pass through both Orange Cloud and a named Tunnel.
