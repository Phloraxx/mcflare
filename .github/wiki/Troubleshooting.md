# Troubleshooting

Most MCflare problems sit in one of four places:

1. player/client integration;
2. Cloudflare or reverse-proxy ingress;
3. MCflare gateway;
4. Minecraft backend / PROXY configuration.

Work through them in that order instead of changing several layers at once.

## Player cannot join

First verify the player has the MCflare artifact matching the Minecraft family and loader. Quilt uses the Fabric artifact; the Paper plugin is server-only.

If this hostname previously proved itself as MCflare, the client intentionally does not fall back to raw Minecraft when WSS fails.

## `/mcflare` returns 404

The current endpoint is exactly `/mcflare`. Check the hostname, reverse-proxy/Tunnel rule, and whether a fallback website/router is receiving the request. The old `/.well-known/mcflare` route is retired.

## WebSocket returns 400 instead of 101

Check the exact path, WebSocket Upgrade headers, and that the client offers `Sec-WebSocket-Protocol: mcflare.v1`. The subprotocol spelling is case-sensitive.

## Upgrade returns 101 but Minecraft does not respond

The Cloudflare/WebSocket part is working. Check gateway-to-backend reachability, the backend port, capacity, and whether gateway/backend agree on PROXY mode.

## Status works but full login fails

Check the exact loader/Minecraft versions and test with a minimal mod set. Mods that replace connection resolution, `Connection.connect*`, server-list Status, or listener/channel setup are the most likely conflict class.

## Real player IP is wrong

Verify that the request arrived through trusted Cloudflare ingress, visitor metadata reached the gateway, PROXY output is enabled only when the backend parses it, and the backend is not directly reachable by raw players.

## Need a full diagnostic tree?

Use [`docs/TROUBLESHOOTING.md`](https://github.com/Phloraxx/mcflare/blob/main/docs/TROUBLESHOOTING.md). It includes Orange/Tunnel-specific checks, disconnect handling, gateway capacity, intentional MCflare removal, and what to include in a bug report.

Do not post Tunnel tokens, Microsoft/Minecraft auth tokens, private keys, or raw player IP addresses in public bug reports.
