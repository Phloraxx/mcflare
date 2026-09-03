# Frequently Asked Questions

Short answers for players and server administrators. For setup steps, start with [Installation](INSTALLATION.md) and [Choose your setup](SETUP_CHOICES.md).

## What problem does MCflare solve?

Minecraft Java normally speaks its own TCP protocol, while Cloudflare's ordinary reverse-proxy path is built around HTTP/HTTPS and WebSockets. MCflare carries the existing Minecraft TCP byte stream inside a standard WebSocket so the connection can travel through Cloudflare's WebSocket-capable ingress.

MCflare does not translate Minecraft packets into HTTP or JSON.

## Do players need a Cloudflare account?

No. Players install the matching MCflare client mod and join the normal Minecraft hostname.

They do not need a Cloudflare account, API token, Tunnel token, WARP profile, VPN, or `cloudflared` process.

## What address does a player enter?

The normal Minecraft hostname, for example:

```text
play.example.com
```

The player does **not** enter `wss://play.example.com/mcflare`. MCflare opens that WebSocket internally when the hostname is proven to support MCflare.

## Does MCflare work with ordinary non-MCflare servers?

Yes. Ordinary servers continue to use normal Minecraft TCP.

For an unknown hostname, the client can determine whether the protected MCflare route is available. Once a hostname has positively proven MCflare, that positive result is persisted so a later outage cannot silently downgrade the player to an accidentally exposed raw origin.

## What is `mcflare.v1`?

It is the exact, case-sensitive WebSocket subprotocol identifier used during the `/mcflare` HTTP Upgrade handshake:

```text
Sec-WebSocket-Protocol: mcflare.v1
```

It is **not** a Minecraft version, loader version, Cloudflare Tunnel version, or account/API version. See [Concepts](CONCEPTS.md#mcflarev1) and [v1 wire protocol](V1_PROTOCOL.md).

## Orange proxy or named Tunnel—which should I use?

Both use the same player protocol.

- Choose **Orange proxy** if you already operate a reachable HTTPS reverse proxy and are comfortable protecting the origin separately.
- Choose a **named Tunnel** if you prefer an outbound-only path from server infrastructure and do not need public inbound HTTPS access to the MCflare gateway.

See [Choose your setup](SETUP_CHOICES.md) and [Deployment](DEPLOYMENT.md).

## Does Orange proxy automatically hide or firewall my origin?

No. A proxied DNS record makes clients use Cloudflare, but origin exposure is a separate infrastructure/security problem. Restrict the gateway/origin appropriately and do not trust forwarding headers on an arbitrary public listener.

A named Tunnel can avoid exposing the MCflare HTTP listener through inbound Internet routing, but other services on the machine still need their own security policy.

## Does MCflare preserve the player's real IP address?

It can. Cloudflare terminates the public WebSocket, so MCflare validates trusted Cloudflare visitor metadata and can emit standard HAProxy **PROXY protocol v1** toward Minecraft.

Fabric/Quilt and NeoForge use MCflare's local parser. Paper/Purpur use their native HAProxy PROXY support. IPv4 and IPv6 restoration have been tested.

Read [Real player IP](REAL_IP.md) before enabling it because the trust boundary matters.

## Does MCflare hide the Minecraft server's origin IP?

MCflare can remove the need for players to connect directly to the Minecraft TCP origin, especially with a named Tunnel. That does not magically erase an origin address that is exposed through other DNS records, services, logs, voice-chat ports, websites, or infrastructure.

Treat origin privacy as a deployment property, not a promise made by the mod itself.

## Does it support Fabric and NeoForge on both client and server?

Yes, for the documented version families. The same matching loader-family JAR is used on the player and dedicated server.

Quilt uses the Fabric artifact. Paper/Purpur use a server-only plugin while players still need a Fabric/Quilt or NeoForge client mod.

See [Compatibility](COMPATIBILITY.md).

## Why are there multiple JARs if the protocol just carries bytes?

The transport core is loader/version independent, but Minecraft's connection hooks and loader packaging change across versions. Multiple artifacts exist because of those integration boundaries—not because MCflare defines different network protocols for each loader.

## Does MCflare support Bedrock Edition?

No. MCflare v1 targets Minecraft Java Edition networking.

## Does it support Simple Voice Chat or other voice mods?

Not through MCflare. Voice-chat mods usually open a separate UDP or TCP socket, while MCflare intentionally carries only Minecraft's own Java connection.

The Minecraft-side control/plugin messages of a mod are still carried if they use the normal Minecraft stream; the mod's separate media socket remains separate.

## What about Dynmap, web maps, telemetry, or other extra ports?

They keep their own network paths. MCflare is not a generic tunnel or port multiplexer.

## Does MCflare parse or modify Minecraft packets?

No. Compression, encryption, login extensions, plugin messages, custom payloads, chunks, entities, inventories, and gameplay data remain ordinary bytes in the original stream.

## Does Minecraft encryption still work?

Yes. MCflare carries Minecraft's stream; it does not replace Minecraft's own protocol encryption/authentication. Cloudflare additionally terminates the outer TLS/WebSocket connection at its edge.

## Does `online-mode=true` work?

Yes. A real Minecraft 26.2 client using the published `v1.0.0-rc.1` Fabric JAR completed Microsoft/Mojang authentication and joined an isolated `online-mode=true`, `enforce-secure-profile=true` server through both true Orange Cloudflare proxying and the named Tunnel. See [TEST_EVIDENCE_2026-09-03.md](TEST_EVIDENCE_2026-09-03.md).

## What happens if Cloudflare or the WebSocket connection dies during gameplay?

The Minecraft connection ends and the player sees a normal disconnect. MCflare does not try to splice a new transport underneath an already-running Minecraft protocol session.

The player reconnects and starts a clean new Minecraft session.

## Why not automatically fall back to direct TCP after a protected host fails?

Because that can defeat the reason the administrator protected the server in the first place. Once a hostname has positively proven MCflare, the client requires WSS for future connections and fails closed if it is unavailable.

## Where are known MCflare hosts stored?

Positive route pins are stored in:

```text
~/.mcflare/known-hosts-v1.txt
```

Do not delete an entry just to work around an outage. Remove it only if the administrator intentionally retired MCflare for that hostname. See [Troubleshooting](TROUBLESHOOTING.md#a-hostname-was-intentionally-converted-back-to-ordinary-minecraft).

## Can one hostname route to several Minecraft backends by port?

MCflare v1 deliberately keeps the model simple: one public hostname/gateway maps to one Minecraft backend.

For multiple instances use separate hostnames, for example `survival.example.com`, `creative.example.com`, and `modded.example.com`. Let your reverse proxy or Tunnel configuration do hostname routing.

## Does MCflare require a central relay or hosted MCflare service?

No. There is no MCflare account system or central MCflare network. Cloudflare is the public ingress and your own gateway forwards to your own Minecraft backend.

## Is IANA registration required for `mcflare.v1`?

No. The token is documented locally and works as a private/custom WebSocket subprotocol identifier. Formal registration may be reconsidered if independent third-party implementations ever make it useful, but it is not a release requirement.

## Where should I report a problem?

Use [GitHub Discussions](https://github.com/Phloraxx/mcflare/discussions) for setup questions and ideas. Use GitHub Issues for reproducible bugs. See [SUPPORT.md](../SUPPORT.md) for the support flow.

Do not post Cloudflare credentials, Tunnel tokens, Minecraft/Microsoft authentication tokens, raw public player IPs, or unrelated infrastructure secrets.
