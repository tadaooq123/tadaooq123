#!/usr/bin/env python3
"""Replace every `ffreep %st(0)` (bytes DF C0) with `fstp %st(0)` (DD D8).

Both instructions pop the x87 stack, so the result is the same. Rosetta in
macOS 26 does not implement ffreep, and Quartus crashes on start-up because two
of its libraries use it. The same fix is used by nixpkgs and quartus-for-mac.

Usage: patch_ffreep.py FILE [FILE ...]   (missing files are skipped)
"""
import re
import struct
import subprocess
import sys

LINE = re.compile(r"^\s*([0-9a-f]+):\s+df c0\s+ffreep\s+%st(\(0\))?\s*$")


def load_segments(data):
    """Return (vaddr, filesz, offset) for every PT_LOAD segment of an ELF64 file."""
    if data[:4] != b"\x7fELF" or data[4] != 2:
        raise ValueError("not an ELF64 file")
    phoff, = struct.unpack_from("<Q", data, 0x20)
    phentsize, phnum = struct.unpack_from("<HH", data, 0x36)
    segs = []
    for i in range(phnum):
        base = phoff + i * phentsize
        p_type, = struct.unpack_from("<I", data, base)
        if p_type == 1:  # PT_LOAD
            p_offset, p_vaddr = struct.unpack_from("<QQ", data, base + 8)
            p_filesz, = struct.unpack_from("<Q", data, base + 32)
            segs.append((p_vaddr, p_filesz, p_offset))
    return segs


def to_offset(segs, vaddr):
    for start, size, offset in segs:
        if start <= vaddr < start + size:
            return offset + vaddr - start
    raise ValueError(f"address {vaddr:#x} is outside every segment")


def patch(path):
    with open(path, "rb") as f:
        data = bytearray(f.read())
    segs = load_segments(data)
    disasm = subprocess.run(["objdump", "-d", "-w", path], check=True,
                            capture_output=True, text=True).stdout
    count = 0
    for line in disasm.splitlines():
        m = LINE.match(line)
        if not m:
            continue
        off = to_offset(segs, int(m.group(1), 16))
        if data[off:off + 2] != b"\xdf\xc0":
            raise ValueError(f"{path}: expected DF C0 at offset {off:#x}")
        data[off:off + 2] = b"\xdd\xd8"
        count += 1
    if count:
        with open(path, "wb") as f:
            f.write(data)
    print(f"{path}: patched {count} ffreep")


def main():
    import os
    for path in sys.argv[1:]:
        if os.path.exists(path):
            patch(path)
        else:
            print(f"{path}: not found, skipped")


if __name__ == "__main__":
    main()
