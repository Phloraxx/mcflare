#!/usr/bin/env python3
"""Validate local Markdown links and repository documentation invariants."""

from __future__ import annotations

import re
import sys
from pathlib import Path
from urllib.parse import unquote

ROOT = Path(__file__).resolve().parents[1]
MARKDOWN_FILES = sorted(
    p for p in ROOT.rglob("*.md")
    if ".git" not in p.parts and "build" not in p.parts
)

LINK_RE = re.compile(r"!?(?:\[[^\]]*\])\(([^)]+)\)")
HEADING_RE = re.compile(r"^(#{1,6})\s+(.+?)\s*$")
FENCE_RE = re.compile(r"^\s*(```|~~~)")


def github_anchor(text: str) -> str:
    text = re.sub(r"<[^>]+>", "", text.strip().lower())
    text = re.sub(r"[`*_~]", "", text)
    text = re.sub(r"[^\w\- ]", "", text, flags=re.UNICODE)
    return text.replace(" ", "-")


def anchors(path: Path) -> set[str]:
    found: set[str] = set()
    counts: dict[str, int] = {}
    in_fence = False
    for line in path.read_text(encoding="utf-8").splitlines():
        if FENCE_RE.match(line):
            in_fence = not in_fence
            continue
        if in_fence:
            continue
        match = HEADING_RE.match(line)
        if not match:
            continue
        base = github_anchor(match.group(2))
        count = counts.get(base, 0)
        counts[base] = count + 1
        found.add(base if count == 0 else f"{base}-{count}")
    return found


def iter_targets(path: Path):
    in_fence = False
    for line_no, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if FENCE_RE.match(line):
            in_fence = not in_fence
            continue
        if in_fence:
            continue
        for match in LINK_RE.finditer(line):
            raw = match.group(1).strip()
            if raw.startswith("<") and raw.endswith(">"):
                raw = raw[1:-1]
            yield line_no, raw


def main() -> int:
    errors: list[str] = []
    anchor_cache: dict[Path, set[str]] = {}

    for md in MARKDOWN_FILES:
        for line_no, target in iter_targets(md):
            if not target or target.startswith(("http://", "https://", "mailto:", "#")):
                if target.startswith("#"):
                    key = md
                    anchor_cache.setdefault(key, anchors(key))
                    anchor = unquote(target[1:])
                    if anchor and anchor not in anchor_cache[key]:
                        errors.append(f"{md.relative_to(ROOT)}:{line_no}: missing anchor #{anchor}")
                continue

            destination, sep, fragment = target.partition("#")
            destination = unquote(destination)
            resolved = (md.parent / destination).resolve()
            try:
                resolved.relative_to(ROOT)
            except ValueError:
                errors.append(f"{md.relative_to(ROOT)}:{line_no}: link escapes repository: {target}")
                continue

            if not resolved.exists():
                errors.append(f"{md.relative_to(ROOT)}:{line_no}: missing target: {target}")
                continue

            if sep and fragment and resolved.suffix.lower() == ".md":
                anchor_cache.setdefault(resolved, anchors(resolved))
                anchor = unquote(fragment)
                if anchor not in anchor_cache[resolved]:
                    errors.append(
                        f"{md.relative_to(ROOT)}:{line_no}: missing anchor in "
                        f"{resolved.relative_to(ROOT)}: #{anchor}"
                    )

    readme = (ROOT / "README.md").read_text(encoding="utf-8")
    if "docs/assets/" in readme or re.search(r"!\[[^\]]*\]\(docs/assets/", readme):
        errors.append("README.md: generated documentation artwork must stay out of the README")

    required_assets = {
        ROOT / "docs/assets/architecture.webp",
        ROOT / "docs/assets/deployment.webp",
        ROOT / "docs/assets/real-ip.webp",
        ROOT / "docs/assets/ux.webp",
    }
    for asset in sorted(required_assets):
        if not asset.exists():
            errors.append(f"missing required documentation asset: {asset.relative_to(ROOT)}")

    if errors:
        print("Documentation validation failed:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1

    print(f"Documentation validation passed: {len(MARKDOWN_FILES)} Markdown files checked.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
