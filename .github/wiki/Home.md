# MCflare Wiki

MCflare puts Cloudflare in front of a Minecraft Java server while players keep using a normal Minecraft address.

This wiki is the practical user and server-admin guide. The repository's [`docs/`](https://github.com/Phloraxx/mcflare/tree/main/docs) directory remains the detailed technical and test record.

## Start here

- [Getting started](Getting-Started.md) — install the right file and make the first connection.
- [Choosing a deployment](Choosing-a-Deployment.md) — Orange Cloud or Cloudflare Tunnel.
- [Real player IP](Real-Player-IP.md) — restore the visitor address with PROXY v1.
- [Troubleshooting](Troubleshooting.md) — work through connection failures in the right order.
- [Compatibility](Compatibility.md) — supported loaders, servers, Minecraft versions, and Java versions.
- [FAQ](FAQ.md) — short answers to common questions.

## What players need to know

Install the matching Fabric/Quilt or NeoForge MCflare mod, then join the server normally:

```text
play.example.com
```

Players do not need `cloudflared`, WARP, a VPN, a Tunnel token, a custom launcher, or a local proxy.

## What server owners need to know

Install the matching MCflare server mod or Paper/Purpur plugin, then route the exact `/mcflare` WebSocket path through Cloudflare to the MCflare gateway.

The public wire endpoint is:

```text
wss://play.example.com/mcflare
Sec-WebSocket-Protocol: mcflare.v1
```

For the deeper design, see [How MCflare works](How-It-Works.md).
