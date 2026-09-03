# Release process

MCflare releases are built from GitHub Actions so the published JARs are reproducible from a known commit and accompanied by checksums.

## Release blockers

For this hobby project, a release candidate requires:

1. the normal seven-row CI matrix is green;
2. the tag/manual release workflow successfully builds all five supported binary families;
3. the exact packaged JARs receive a small smoke test on their intended platform families;
4. documentation/support matrix and changelog match the artifacts;
5. `SHA256SUMS.txt` is generated from the final binaries.

The following are **not** release blockers: IANA registration of `mcflare.v1`, intentionally forcing a Cloudflare-edge restart, Mojang `online-mode=true` proof, or larger graphical/world-generation stress. They may be useful future validation.

## Dry-run a release build

Run the **release** workflow manually and provide a version such as `1.0.0-rc.1`. Manual runs build and bundle artifacts but do not create a GitHub Release.

The bundle contains:

```text
mcflare-fabric-1.21.11-<version>.jar
mcflare-fabric-26.1-26.2-<version>.jar
mcflare-neoforge-1.21.11-<version>.jar
mcflare-neoforge-26.1-26.2-<version>.jar
mcflare-paper-<version>.jar
SHA256SUMS.txt
```

## Publish

After the dry-run artifacts are smoke-tested, create and push an annotated release tag matching `v*`, for example:

```bash
git tag -a v1.0.0 -m 'MCflare 1.0.0'
git push origin v1.0.0
```

The tag-triggered workflow rebuilds the same five binary families, generates checksums, and creates the GitHub Release from that tag. Release notes are taken from the matching version section in `CHANGELOG.md`; the publish job fails rather than silently generating unreviewed notes when that section is missing.

## Version source

Normal development builds use `<mod_version>-dev`. Release workflow builds pass `-Prelease_version=<tag-version>`, which becomes the version embedded in Fabric, NeoForge, and Paper metadata as well as the JAR filename.

## Distribution identity

The existing Modflared Modrinth project is a separate, active project line. Do not upload MCflare artifacts there or reuse its project slug. If MCflare is published to Modrinth later, create/use a distinct MCflare project identity and add that publishing step only after the project ID and credentials are intentionally configured.
