# Publishing MCflare outside GitHub

GitHub Releases remain the source of truth for MCflare binaries. Third-party listings should publish those exact JARs rather than rebuilding the release.

## Modrinth

Use one MCflare project. Modrinth recommends keeping loader variants on one project and publishing different loader/game-version files as separate versions.

Suggested project fields:

- **Name:** `MCflare`
- **Slug:** `mcflare` if it is still available when the project is created
- **Summary:** `Put a Minecraft Java server behind Cloudflare while players keep using a normal Minecraft address.`
- **License:** MIT
- **Source:** `https://github.com/Phloraxx/mcflare`
- **Issues:** `https://github.com/Phloraxx/mcflare/issues`

Suggested description:

> MCflare lets a Minecraft Java server use Cloudflare while players keep joining with the normal server address.
>
> Players install the matching Fabric/Quilt or NeoForge mod. They do not need cloudflared, WARP, a VPN, a custom launcher, or a local proxy. Fabric/Quilt and NeoForge can also run MCflare on a modded server; Paper/Purpur uses the server plugin.
>
> Server setup requires a Cloudflare-proxied hostname and a route for `/mcflare` through an HTTPS reverse proxy or Cloudflare Tunnel. See the installation and deployment guides in the GitHub repository.
>
> MCflare is a hobby project, is independent of Mojang Studios, Microsoft, and Cloudflare, and includes MIT-licensed work derived from Modflared. See `NOTICE.md` in the repository for attribution.

Do not add generated gallery artwork just to fill the listing.

### Initial project setup

Create the project in Modrinth first and let its normal moderation flow complete. Then add these GitHub repository settings:

- repository secret `MODRINTH_TOKEN` with permission to create versions on the project;
- repository variable `MODRINTH_PROJECT_ID` containing the project ID or slug.

The repository intentionally does not contain either credential.

### Publishing a release

Run the `publish-modrinth` GitHub Actions workflow and enter an existing GitHub release tag such as `v1.0.0-rc.1`.

The workflow:

1. downloads the existing GitHub release JARs and `SHA256SUMS.txt`;
2. verifies the exact bundle before doing anything else;
3. publishes separate Modrinth versions for Fabric/Quilt 1.21.11, Fabric/Quilt 26.1–26.2, NeoForge 1.21.11, NeoForge 26.1–26.2, and Paper/Purpur;
4. reuses the same MCflare release number across those loader-specific versions;
5. skips an existing loader/game-version entry only when its uploaded SHA-512 matches the GitHub release JAR; a conflicting binary fails the publication run.

Release candidates are published as Modrinth beta versions, alpha releases as alpha, and normal releases as release versions.

For a local publication-plan check without credentials:

```bash
python3 scripts/publish_modrinth.py --tag v1.0.0-rc.1 --dist /path/to/release-bundle --dry-run
```

## Hangar

Hangar is useful for the Paper/Purpur plugin after the Modrinth listing is established. Keep it as a server-plugin distribution channel rather than a second home for the whole project.

Before enabling Hangar automation:

- create and approve the Hangar project;
- keep the Modflared attribution visible;
- add a `HANGAR_API_TOKEN` repository secret;
- publish the exact Paper/Purpur JAR from the GitHub release rather than rebuilding it.

Do not make Hangar publishing part of the normal release workflow until the first listing has been reviewed manually.
