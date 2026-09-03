#!/usr/bin/env python3
"""Publish an existing MCflare GitHub release bundle to Modrinth.

The script deliberately publishes the already-released JARs instead of rebuilding
anything. Each loader/game-version artifact becomes its own Modrinth version.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import mimetypes
import os
import re
import sys
import urllib.error
import urllib.request
from pathlib import Path
from uuid import uuid4

API = "https://api.modrinth.com/v2"
USER_AGENT = "Phloraxx/mcflare publication script"


def release_channel(version: str) -> str:
    lower = version.lower()
    if "alpha" in lower:
        return "alpha"
    if any(marker in lower for marker in ("beta", "-rc", ".rc")):
        return "beta"
    return "release"


def artifact_specs(version: str) -> list[dict[str, object]]:
    return [
        {
            "filename": f"mcflare-fabric-1.21.11-{version}.jar",
            "name": f"MCflare {version} — Fabric / Quilt 1.21.11",
            "loaders": ["fabric", "quilt"],
            "game_versions": ["1.21.11"],
            "environment": "client_and_server",
        },
        {
            "filename": f"mcflare-fabric-26.1-26.2-{version}.jar",
            "name": f"MCflare {version} — Fabric / Quilt 26.1–26.2",
            "loaders": ["fabric", "quilt"],
            "game_versions": ["26.1", "26.2"],
            "environment": "client_and_server",
        },
        {
            "filename": f"mcflare-neoforge-1.21.11-{version}.jar",
            "name": f"MCflare {version} — NeoForge 1.21.11",
            "loaders": ["neoforge"],
            "game_versions": ["1.21.11"],
            "environment": "client_and_server",
        },
        {
            "filename": f"mcflare-neoforge-26.1-26.2-{version}.jar",
            "name": f"MCflare {version} — NeoForge 26.1–26.2",
            "loaders": ["neoforge"],
            "game_versions": ["26.1", "26.2"],
            "environment": "client_and_server",
        },
        {
            "filename": f"mcflare-paper-{version}.jar",
            "name": f"MCflare {version} — Paper / Purpur",
            "loaders": ["paper", "purpur"],
            "game_versions": ["1.21.11", "26.1", "26.2"],
            "environment": "server_only",
        },
    ]


def parse_checksums(path: Path) -> dict[str, str]:
    checksums: dict[str, str] = {}
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line:
            continue
        match = re.fullmatch(r"([0-9a-fA-F]{64})\s+\*?(.+)", line)
        if not match:
            raise ValueError(f"invalid checksum line: {raw_line!r}")
        checksums[match.group(2)] = match.group(1).lower()
    return checksums


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def verify_bundle(dist: Path, specs: list[dict[str, object]]) -> None:
    checksum_path = dist / "SHA256SUMS.txt"
    if not checksum_path.is_file():
        raise FileNotFoundError(f"missing {checksum_path}")
    checksums = parse_checksums(checksum_path)
    expected = {str(spec["filename"]) for spec in specs}
    actual_jars = {path.name for path in dist.glob("*.jar")}
    if actual_jars != expected:
        missing = sorted(expected - actual_jars)
        extra = sorted(actual_jars - expected)
        raise ValueError(f"release bundle mismatch; missing={missing}, extra={extra}")
    for filename in sorted(expected):
        expected_hash = checksums.get(filename)
        if expected_hash is None:
            raise ValueError(f"{filename} is absent from SHA256SUMS.txt")
        actual_hash = sha256(dist / filename)
        if actual_hash != expected_hash:
            raise ValueError(f"checksum mismatch for {filename}")


def encode_multipart(data: dict[str, object], file_path: Path) -> tuple[bytes, str]:
    boundary = f"----mcflare-{uuid4().hex}"
    body = bytearray()

    def add(value: bytes) -> None:
        body.extend(value)
        body.extend(b"\r\n")

    add(f"--{boundary}".encode())
    add(b'Content-Disposition: form-data; name="data"')
    add(b"Content-Type: application/json")
    add(b"")
    add(json.dumps(data, separators=(",", ":")).encode("utf-8"))
    add(f"--{boundary}".encode())
    add(f'Content-Disposition: form-data; name="file"; filename="{file_path.name}"'.encode())
    add(f"Content-Type: {mimetypes.guess_type(file_path.name)[0] or 'application/java-archive'}".encode())
    add(b"")
    body.extend(file_path.read_bytes())
    body.extend(b"\r\n")
    body.extend(f"--{boundary}--\r\n".encode())
    return bytes(body), boundary


def api_json(url: str, token: str | None = None) -> object:
    headers = {"User-Agent": USER_AGENT, "Accept": "application/json"}
    if token:
        headers["Authorization"] = token
    request = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.load(response)


def existing_versions(project: str, token: str) -> list[dict[str, object]]:
    result = api_json(f"{API}/project/{project}/version?include_changelog=false", token)
    if not isinstance(result, list):
        raise ValueError("unexpected Modrinth version-list response")
    return result


def already_published(existing: list[dict[str, object]], spec: dict[str, object], version: str) -> bool:
    wanted_loaders = set(spec["loaders"])  # type: ignore[arg-type]
    wanted_games = set(spec["game_versions"])  # type: ignore[arg-type]
    for item in existing:
        if item.get("version_number") != version:
            continue
        if set(item.get("loaders", [])) != wanted_loaders:
            continue
        if set(item.get("game_versions", [])) != wanted_games:
            continue
        return True
    return False


def publish(project: str, token: str, spec: dict[str, object], version: str, channel: str, changelog: str, file_path: Path) -> dict[str, object]:
    payload = {
        "name": spec["name"],
        "version_number": version,
        "changelog": changelog,
        "dependencies": [],
        "game_versions": spec["game_versions"],
        "version_type": channel,
        "loaders": spec["loaders"],
        "featured": False,
        "status": "listed",
        "project_id": project,
        "file_parts": ["file"],
        "primary_file": "file",
        "environment": spec["environment"],
    }
    body, boundary = encode_multipart(payload, file_path)
    request = urllib.request.Request(
        f"{API}/version",
        data=body,
        method="POST",
        headers={
            "Authorization": token,
            "User-Agent": USER_AGENT,
            "Accept": "application/json",
            "Content-Type": f"multipart/form-data; boundary={boundary}",
        },
    )
    with urllib.request.urlopen(request, timeout=120) as response:
        result = json.load(response)
    if not isinstance(result, dict):
        raise ValueError("unexpected Modrinth create-version response")
    return result


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--project", default=os.environ.get("MODRINTH_PROJECT_ID", ""))
    parser.add_argument("--tag", required=True, help="GitHub release tag, for example v1.0.0-rc.1")
    parser.add_argument("--dist", type=Path, default=Path("dist"))
    parser.add_argument("--changelog-file", type=Path)
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    if not args.tag.startswith("v") or len(args.tag) < 2:
        parser.error("--tag must begin with v")
    version = args.tag[1:]
    specs = artifact_specs(version)
    verify_bundle(args.dist, specs)
    changelog = args.changelog_file.read_text(encoding="utf-8") if args.changelog_file else ""
    channel = release_channel(version)

    print(f"Verified release bundle for {args.tag}")
    for spec in specs:
        print(
            f"- {spec['filename']}: loaders={','.join(spec['loaders'])} "
            f"games={','.join(spec['game_versions'])} environment={spec['environment']} channel={channel}"
        )

    if args.dry_run:
        return 0
    if not args.project:
        parser.error("--project or MODRINTH_PROJECT_ID is required")
    token = os.environ.get("MODRINTH_TOKEN")
    if not token:
        parser.error("MODRINTH_TOKEN is required")

    try:
        existing = existing_versions(args.project, token)
        for spec in specs:
            if already_published(existing, spec, version):
                print(f"Skipping existing Modrinth version: {spec['name']}")
                continue
            result = publish(args.project, token, spec, version, channel, changelog, args.dist / str(spec["filename"]))
            print(f"Published {spec['name']} as Modrinth version {result.get('id', '<unknown>')}")
            existing.append(result)
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        print(f"Modrinth API error: HTTP {exc.code}: {detail}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
