#!/usr/bin/env python3
"""Normalize ZIP/JAR timestamp metadata without recompressing payload bytes."""

import argparse
import hashlib
import os
import struct
import tempfile
import zipfile
from pathlib import Path

EOCD = b"PK\x05\x06"
CENTRAL = b"PK\x01\x02"
LOCAL = b"PK\x03\x04"
DOS_TIME = 0
DOS_DATE = (1 << 5) | 1  # 1980-01-01
FIXED_UNIX = 315532800  # 1980-01-01T00:00:00Z
FIXED_FILETIME = (FIXED_UNIX + 11644473600) * 10_000_000


def _u16(buf, offset):
    return struct.unpack_from("<H", buf, offset)[0]


def _u32(buf, offset):
    return struct.unpack_from("<I", buf, offset)[0]


def _patch_extra(buf, start, length):
    end = start + length
    cursor = start
    while cursor < end:
        if cursor + 4 > end:
            raise ValueError("truncated ZIP extra field")
        field_id = _u16(buf, cursor)
        field_size = _u16(buf, cursor + 2)
        data = cursor + 4
        field_end = data + field_size
        if field_end > end:
            raise ValueError("invalid ZIP extra field length")

        if field_id == 0x5455 and field_size >= 1:  # Extended Timestamp
            # Central-directory records commonly retain only mtime even when the
            # flag byte originated from a local field with atime/ctime. Normalize
            # every complete timestamp word that is actually present.
            pos = data + 1
            while pos + 4 <= field_end:
                struct.pack_into("<I", buf, pos, FIXED_UNIX)
                pos += 4
        elif field_id == 0x000A and field_size >= 4:  # NTFS timestamps
            pos = data + 4
            while pos + 4 <= field_end:
                tag = _u16(buf, pos)
                size = _u16(buf, pos + 2)
                pos += 4
                if pos + size > field_end:
                    raise ValueError("truncated NTFS extra field")
                if tag == 0x0001 and size >= 24:
                    for offset in (0, 8, 16):
                        struct.pack_into("<Q", buf, pos + offset, FIXED_FILETIME)
                pos += size

        cursor = field_end


def normalize(path):
    path = Path(path)
    buf = bytearray(path.read_bytes())
    eocd = buf.rfind(EOCD)
    if eocd < 0 or eocd + 22 > len(buf):
        raise ValueError(f"{path}: EOCD record not found")

    disk = _u16(buf, eocd + 4)
    cd_disk = _u16(buf, eocd + 6)
    disk_entries = _u16(buf, eocd + 8)
    total_entries = _u16(buf, eocd + 10)
    cd_size = _u32(buf, eocd + 12)
    cd_offset = _u32(buf, eocd + 16)
    if disk or cd_disk or disk_entries != total_entries:
        raise ValueError(f"{path}: multi-disk/ZIP64 archives are unsupported")
    if cd_offset + cd_size > eocd:
        raise ValueError(f"{path}: invalid central-directory bounds")

    cursor = cd_offset
    for _ in range(total_entries):
        if bytes(buf[cursor:cursor + 4]) != CENTRAL:
            raise ValueError(f"{path}: invalid central-directory entry")
        struct.pack_into("<H", buf, cursor + 12, DOS_TIME)
        struct.pack_into("<H", buf, cursor + 14, DOS_DATE)
        name_len = _u16(buf, cursor + 28)
        extra_len = _u16(buf, cursor + 30)
        comment_len = _u16(buf, cursor + 32)
        local_offset = _u32(buf, cursor + 42)
        _patch_extra(buf, cursor + 46 + name_len, extra_len)

        if bytes(buf[local_offset:local_offset + 4]) != LOCAL:
            raise ValueError(f"{path}: invalid local-file header")
        struct.pack_into("<H", buf, local_offset + 10, DOS_TIME)
        struct.pack_into("<H", buf, local_offset + 12, DOS_DATE)
        local_name_len = _u16(buf, local_offset + 26)
        local_extra_len = _u16(buf, local_offset + 28)
        _patch_extra(buf, local_offset + 30 + local_name_len, local_extra_len)
        cursor += 46 + name_len + extra_len + comment_len

    if cursor != cd_offset + cd_size:
        raise ValueError(f"{path}: central-directory size mismatch")

    mode = path.stat().st_mode
    with tempfile.NamedTemporaryFile(dir=path.parent, prefix=path.name + ".", delete=False) as tmp:
        tmp.write(buf)
        tmp_path = Path(tmp.name)
    os.chmod(tmp_path, mode)
    os.replace(tmp_path, path)


def _self_test():
    def make(path, year, epoch):
        with zipfile.ZipFile(path, "w") as archive:
            info = zipfile.ZipInfo("META-INF/jars/core.jar", (year, 1, 2, 3, 4, 6))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.extra = struct.pack("<HHBI", 0x5455, 5, 1, epoch)
            archive.writestr(info, b"same payload")
            archive.writestr("fabric.mod.json", b'{"version":"test"}')

    with tempfile.TemporaryDirectory() as temp:
        first = Path(temp) / "a.jar"
        second = Path(temp) / "b.jar"
        make(first, 2025, 1_700_000_000)
        make(second, 2026, 1_800_000_000)
        normalize(first)
        normalize(second)
        if first.read_bytes() != second.read_bytes():
            raise SystemExit("normalization self-test failed: archives still differ")
        before = hashlib.sha256(first.read_bytes()).digest()
        normalize(first)
        after = hashlib.sha256(first.read_bytes()).digest()
        if before != after:
            raise SystemExit("normalization self-test failed: normalization is not idempotent")
        with zipfile.ZipFile(first) as archive:
            if archive.read("META-INF/jars/core.jar") != b"same payload":
                raise SystemExit("normalization self-test failed: payload changed")
    print("ZIP_TIMESTAMP_NORMALIZER_SELF_TEST=PASS")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("paths", nargs="*")
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        _self_test()
    if not args.paths and not args.self_test:
        parser.error("provide at least one ZIP/JAR path or --self-test")
    for path in args.paths:
        normalize(path)
        print(f"normalized {path}")


if __name__ == "__main__":
    main()
