# Security policy

MCflare sits on a network trust boundary: it accepts WebSocket traffic, consumes trusted visitor metadata, can emit PROXY protocol, and prevents silent downgrade for previously proven MCflare hosts.

## Reporting a vulnerability

Use GitHub's **Report a vulnerability** flow for this repository whenever possible. Private vulnerability reporting is enabled, so security details can be sent directly to the maintainer without opening a public issue.

If that flow is unavailable, open a minimal GitHub issue asking for a private contact path. Do **not** include exploit details, credentials, authentication material, or player addresses in the public issue.

## Never include these in reports

- Cloudflare API tokens or Tunnel credentials;
- Minecraft/Microsoft account or session tokens;
- raw player public IP addresses;
- unrelated production secrets or complete environment dumps.

Redacted logs and synthetic/documentation IP ranges are preferred.

## Security-relevant behavior

Reports are especially useful for:

- bypass of the `mcflare.v1` handshake/path requirements;
- downgrade from a persisted known-MCflare route to direct TCP;
- spoofing or trust-boundary errors in visitor-IP → PROXY-v1 handling;
- unbounded frame/header/resource behavior;
- cross-session data leakage;
- credential handling that should not exist in MCflare.

Current MCflare releases and the active development line are the supported security targets.
