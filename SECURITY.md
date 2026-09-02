# Security policy

MCflare sits on a network trust boundary: it accepts WebSocket traffic, consumes Cloudflare visitor metadata, can emit PROXY protocol, and deliberately prevents silent downgrade for previously proven MCflare hosts.

## Reporting a vulnerability

Prefer a private GitHub Security Advisory for this repository when available (**Security → Advisories → New draft security advisory**).

If you cannot access a private reporting path, open a minimal GitHub issue asking the maintainer for a private channel. Do not include exploit details, credentials, authentication material, or player addresses in that public issue.

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
- spoofing/trust-boundary errors in visitor-IP → PROXY-v1 handling;
- unbounded frame/header/resource behavior;
- cross-session data leakage;
- credential handling that should not exist in MCflare.

Only the current development line and subsequently published GitHub Releases are expected to receive fixes. Historical inherited tags are not supported release lines.
