# MCflare authenticated online-mode evidence - 2026-09-03

This record closes the previously separate Mojang session-authentication proof for the published `v1.0.0-rc.1` Fabric artifact.

## Client

A real Minecraft 26.2 client was launched on macOS through Prism Launcher with an active Microsoft account that owns Minecraft. The disposable instance contained only Fabric Loader 0.19.3 and the published MCflare release JAR:

```text
mcflare-fabric-26.1-26.2-1.0.0-rc.1.jar
SHA-256 142a6bd0fe2f098012ff529528a17a0c265d859be7f4b2de5a7fabe8791a29d2
```

Prism completed the Microsoft, Xbox/XSTS and Minecraft-services authentication flow before launching the game. The Minecraft log reported MCflare `1.0.0-rc.1` and the authenticated player profile; no development MCflare JAR or other gameplay mod was loaded in the accepted run.

## Server and transport

A disposable vanilla Minecraft 26.2 server ran on `127.0.0.1:25585` with:

```text
online-mode=true
enforce-secure-profile=true
```

The existing standalone v1 test gateway on `25588` was temporarily restarted against that isolated server. The gateway used `proxyProtocol=false` for this proof so Mojang authentication was tested independently from the already-proven real-IP/PROXY path.

The same published RC client then joined through both public delivery modes using `/mcflare` and WebSocket subprotocol `mcflare.v1`:

- true Orange Cloudflare proxy: PASS;
- named Cloudflare Tunnel: PASS.

For each connection, the MCflare gateway recorded a Cloudflare-backed WebSocket upgrade. The Minecraft server's `User Authenticator` resolved the authenticated player to the same UUID held by the launcher's Microsoft/Minecraft profile, then logged the player in and reached `joined the game`.

This proves Minecraft's normal online-mode encryption/session-authentication exchange survives the MCflare byte stream through both supported Cloudflare ingress choices.

## Restoration

After both joins, the disposable client and isolated server/gateways were stopped. The standalone `25588` gateway was restored to its original `127.0.0.1:25565` backend and original 256-connection setting.

Final regression checks:

```text
true Orange /mcflare Status: PASS, 105 bytes
named Tunnel /mcflare Status: PASS, 105 bytes
direct 127.0.0.1:25565 Status: PASS, 105 bytes
```

No `25585`, `25587` or disposable Quick Tunnel process remained afterward. Production Minecraft itself was not restarted or reconfigured for this proof.
