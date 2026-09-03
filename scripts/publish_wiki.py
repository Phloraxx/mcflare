#!/usr/bin/env python3
"""Prepare or publish the GitHub Wiki from .github/wiki source pages."""

from __future__ import annotations

import argparse
import re
import shutil
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / ".github" / "wiki"
REMOTE = "https://github.com/Phloraxx/mcflare.wiki.git"
REQUIRED = {
    "Home.md",
    "Getting-Started.md",
    "Choosing-a-Deployment.md",
    "Orange-Cloud.md",
    "Cloudflare-Tunnel.md",
    "Real-Player-IP.md",
    "Compatibility.md",
    "Troubleshooting.md",
    "FAQ.md",
    "How-It-Works.md",
    "Contributing.md",
    "_Sidebar.md",
    "_Footer.md",
}
LINK_RE = re.compile(r"\]\(([^)]+\.md)(#[^)]+)?\)")


def transformed(text: str) -> str:
    """Convert repository-source .md links into GitHub Wiki page links."""
    def repl(match: re.Match[str]) -> str:
        target = match.group(1)
        fragment = match.group(2) or ""
        if "/" in target or target.startswith(("http://", "https://")):
            return match.group(0)
        return f"]({target[:-3]}{fragment})"
    return LINK_RE.sub(repl, text)


def validate() -> list[Path]:
    pages = sorted(SOURCE.glob("*.md"))
    names = {p.name for p in pages}
    missing = sorted(REQUIRED - names)
    if missing:
        raise SystemExit("Missing wiki source pages: " + ", ".join(missing))
    if "# MCflare Wiki" not in (SOURCE / "Home.md").read_text(encoding="utf-8"):
        raise SystemExit("Home.md does not contain the expected wiki heading")
    return pages


def render(destination: Path) -> list[Path]:
    pages = validate()
    destination.mkdir(parents=True, exist_ok=True)
    out = []
    for page in pages:
        target = destination / page.name
        target.write_text(transformed(page.read_text(encoding="utf-8")), encoding="utf-8")
        out.append(target)
    return out


def run(*args: str, cwd: Path | None = None, check: bool = True) -> subprocess.CompletedProcess[str]:
    return subprocess.run(args, cwd=cwd, check=check, text=True, capture_output=True)


def publish() -> None:
    probe = run("git", "ls-remote", REMOTE, check=False)
    if probe.returncode != 0:
        raise SystemExit(
            "GitHub Wiki is enabled but its git repository is not initialized yet. "
            "Create and save the first Wiki page once in the GitHub web UI, then rerun this command."
        )

    with tempfile.TemporaryDirectory(prefix="mcflare-wiki-") as tmp:
        checkout = Path(tmp) / "wiki"
        run("git", "clone", "--quiet", REMOTE, str(checkout))
        for old in checkout.glob("*.md"):
            old.unlink()
        render(checkout)
        run("git", "add", "-A", cwd=checkout)
        status = run("git", "status", "--porcelain", cwd=checkout).stdout.strip()
        if not status:
            print("Wiki is already up to date.")
            return
        run("git", "commit", "-m", "docs: sync MCflare wiki", cwd=checkout)
        run("git", "push", "--quiet", cwd=checkout)
        print(f"Published {len(list(SOURCE.glob('*.md')))} wiki pages.")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="validate and render wiki pages without publishing")
    parser.add_argument("--publish", action="store_true", help="publish pages to the initialized GitHub Wiki")
    args = parser.parse_args()

    if args.publish:
        publish()
        return

    with tempfile.TemporaryDirectory(prefix="mcflare-wiki-check-") as tmp:
        pages = render(Path(tmp))
        leftover = re.compile(r"\]\(([^/):]+\.md)(#[^)]*)?\)")
        if any(leftover.search(p.read_text(encoding="utf-8")) for p in pages):
            raise SystemExit("Rendered wiki still contains an intra-wiki .md link")
        print(f"Wiki validation passed: {len(pages)} pages rendered.")


if __name__ == "__main__":
    main()
