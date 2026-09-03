# MCflare 1.0.0 release evidence

This record captures the final stable-release package gate performed on 2026-09-03 before creating `v1.0.0`.

## GitHub Actions dry run

The `release` workflow was dispatched on branch `release/v1.0.0` with version `1.0.0`.

- workflow run: `33770022680`
- source commit: `5faa29331fe1a06a055b5f524be8b5fa80c05fb7`
- Fabric 1.21.11: PASS
- Fabric 26.1–26.2: PASS
- NeoForge 1.21.11: PASS
- NeoForge 26.1–26.2: PASS
- Paper/Purpur plugin: PASS
- release bundle verification: PASS
- GitHub Release publish: correctly skipped for the manual dry run

The workflow verified the embedded `1.0.0` version metadata before assembling the bundle and generated `SHA256SUMS.txt` from the five final JARs.

## Dry-run artifact hashes

```text
fbe404187d22760ba65f7ee359fcc6b5bd705b172d229a588e69930044129807  mcflare-fabric-1.21.11-1.0.0.jar
7ffbaa3292fe7e5a5ba44aa29a97a0d5ff640308a49878bb3220cb48b59246d5  mcflare-fabric-26.1-26.2-1.0.0.jar
5cf05ceb3e759cd3ad905ad768ef23fd1eaf7a0664ebf8286785a967d6c1262c  mcflare-neoforge-1.21.11-1.0.0.jar
273056c7d24177cb181b6d3eed4db6d214fe8db942c7756b3a8b547316e0f28d  mcflare-neoforge-26.1-26.2-1.0.0.jar
e32d9c1c0de78ccd7f1e95a774f9c81c759f2f4bc0c708f144202bda33106021  mcflare-paper-1.0.0.jar
```
## Packaged-JAR runtime smoke

The exact dry-run JARs were copied into the same isolated Oracle ARM64 loader/server installations previously used for the release-candidate package gate. No production Minecraft process was restarted or replaced.

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

All copied artifacts matched the dry-run bundle hashes above. Fabric/Quilt also loaded the nested `core-1.0.0.jar` and `gateway-1.0.0.jar`; NeoForge and Paper metadata advertised `1.0.0`.
## Authentication and protocol confidence

No MCflare runtime transport source changed between `v1.0.0-rc.1` and this stable release preparation. The changes after the RC are release reproducibility/identity checks, documentation, Wiki/public-launch work, and recorded acceptance evidence.

The already-published RC artifact separately completed real Microsoft/Mojang-authenticated `online-mode=true` world joins through both supported public delivery modes:

- true Cloudflare Orange Cloud: PASS;
- named Cloudflare Tunnel: PASS.

That proof covers the normal Minecraft encryption and session-authentication exchange over the same `wss://<host>/mcflare` / `mcflare.v1` transport used by `1.0.0`.

## Release decision

The stable bundle satisfies the documented release gate: normal CI is green, all five binary families build with version-correct metadata, the bundle/checksums are complete, and exact packaged artifacts pass the supported loader/server smoke matrix. The immutable `v1.0.0-rc.1` release remains unchanged as historical release-candidate evidence.
