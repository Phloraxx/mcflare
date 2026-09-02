# Troubleshooting

Start by identifying whether the failure is player-side, Cloudflare ingress, MCflare gateway, or Minecraft backend.

## Player gets an ordinary connection error

Confirm the player installed the MCflare artifact matching both the loader and Minecraft version. Paper/Purpur plugins are server-side only and do not replace the player mod.

For a known MCflare hostname, the client intentionally fails closed if WSS is unavailable. It does not silently downgrade to raw Minecraft TCP.

## `/mcflare` returns 404

Your reverse proxy/Tunnel is not routing the exact current v1 path to the gateway, or a legacy route is being used.

Current endpoint:

```text
/mcflare
```

`/.well-known/mcflare` is historical and is not the v1 endpoint.

## WebSocket upgrade returns 400

Check all of the following:

- request path is exactly `/mcflare`;
- `Upgrade: websocket` and `Connection: Upgrade` survive the reverse proxy;
- the client offers `Sec-WebSocket-Protocol: mcflare.v1`;
- the proxy does not replace the request with a browser login/challenge page.

The subprotocol token is case-sensitive.

## Upgrade succeeds but Minecraft disconnects

Verify the gateway's configured Minecraft backend address and whether PROXY protocol is expected by that backend.

A mismatch such as “gateway sends PROXY but Minecraft does not parse PROXY” causes the first backend bytes to be interpreted as invalid Minecraft traffic.

## Real player IP is missing or wrong

Read [REAL_IP.md](REAL_IP.md). In particular:

- trust Cloudflare visitor-IP headers only on ingress that is actually restricted to Cloudflare/trusted infrastructure;
- enable the appropriate PROXY-v1 parser/native Paper setting;
- do not expose a header-trusting gateway directly to arbitrary clients.

## Cloudflare Orange path does not connect

Cloudflare supports proxied WebSockets, but your origin still needs a reachable HTTPS/WebSocket path and a reverse proxy rule for `/mcflare`. Check origin firewall policy, TLS, and the Cloudflare WebSockets setting.

Do not assume an orange DNS record alone hides or firewall-protects the origin. See [DEPLOYMENT.md](DEPLOYMENT.md).

## Named Tunnel does not connect

Confirm `cloudflared` is healthy and the public-hostname ingress maps the exact `/mcflare` path to the HTTP gateway listener. MCflare does not need or consume the Tunnel token itself.

## A hostname was intentionally converted back to ordinary Minecraft

MCflare persists **positive** proof for previously protected hosts so an origin exposure cannot silently downgrade a player to direct TCP.

The local store is:

```text
~/.mcflare/known-hosts-v1.txt
```

Only remove the corresponding entry if you intentionally retired MCflare for that hostname and understand that future connections may use direct Minecraft TCP.

## What to include in a bug report

Include:

- Minecraft version;
- Fabric/Quilt/NeoForge/Paper/Purpur version;
- exact MCflare artifact/version;
- Orange, named Tunnel, or ordinary-direct path;
- the smallest relevant client/server/gateway log excerpt;
- whether `/mcflare` upgrades successfully.

Do **not** include Cloudflare credentials, Tunnel tokens, Minecraft/Microsoft auth tokens, raw player public IP addresses, or unrelated server secrets.
