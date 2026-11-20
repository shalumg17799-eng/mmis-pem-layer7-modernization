#!/usr/bin/env python3
"""MAM-53: convert a legacy EBCDIC dataset to ASCII for the Phase 9 parity run.

Usage:
    python ebcdic_to_ascii.py INPUT OUTPUT --codepage {037,1047} [--lrecl N]

One ASCII byte per EBCDIC byte; no line terminators are added (AC-1). The code page is required and
must be 037 or 1047 (AC-2). With --lrecl the input size must be a multiple of the record length
(AC-5). A byte that does not convert to printable ASCII (0x20-0x7E) stops the run with its offset
and value; no replacement character is ever written (AC-5, D-024). The input is only read: an output
path equal to the input path is refused, and nothing is written unless the whole file converts
(AC-6). Synthetic data only.

Code page 037 is Python's cp037. Code page 1047 (z/OS Open Systems Latin-1) differs from 037 in
eight positions only (the brackets, caret, not sign, Y-acute, diaeresis and the LF/NEL pair); it is
built here from cp037 with those eight positions swapped, because Python has no cp1047 codec.
"""

import argparse
import hashlib
import os
import sys
from pathlib import Path

SUPPORTED = ("037", "1047")

# EBCDIC byte -> Unicode code point where 1047 differs from 037
_CP1047_DIFF = {
    0x15: 0x0A,  # 037: NEL (U+0085)   1047: LF
    0x25: 0x85,  # 037: LF             1047: NEL
    0x5F: 0x5E,  # 037: not sign       1047: caret
    0xAD: 0x5B,  # 037: Y acute        1047: [
    0xB0: 0xAC,  # 037: caret          1047: not sign
    0xBA: 0xDD,  # 037: [              1047: Y acute
    0xBB: 0xA8,  # 037: ]              1047: diaeresis
    0xBD: 0x5D,  # 037: diaeresis      1047: ]
}


def table(codepage: str) -> list:
    """The 256 Unicode code points of the code page, indexed by EBCDIC byte."""
    base = [ord(bytes([b]).decode("cp037")) for b in range(256)]
    if codepage == "1047":
        for b, cp in _CP1047_DIFF.items():
            base[b] = cp
    return base


def convert(data: bytes, codepage: str) -> bytes:
    """EBCDIC bytes to ASCII bytes; ValueError names the first byte that is not printable ASCII."""
    t = table(codepage)
    out = bytearray(len(data))
    for offset, b in enumerate(data):
        cp = t[b]
        if not 0x20 <= cp <= 0x7E:
            raise ValueError(
                f"byte at offset {offset} has value 0x{b:02X}, which code page {codepage} "
                f"does not convert to printable ASCII (U+{cp:04X})")
        out[offset] = cp
    return bytes(out)


def encode(text: bytes, codepage: str) -> bytes:
    """ASCII bytes to EBCDIC bytes (used by the round-trip test, AC-4)."""
    t = table(codepage)
    back = {cp: b for b, cp in enumerate(t)}
    return bytes(back[c] for c in text)


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main(argv=None) -> int:
    p = argparse.ArgumentParser(description="Convert an EBCDIC file (code page 037 or 1047) to ASCII.")
    p.add_argument("input", type=Path)
    p.add_argument("output", type=Path)
    p.add_argument("--codepage", help="EBCDIC code page: 037 or 1047 (required)")
    p.add_argument("--lrecl", type=int, help="record length; the file size must be a multiple of it")
    a = p.parse_args(argv)

    if a.codepage not in SUPPORTED:
        print(f"error: --codepage is required and must be one of {', '.join(SUPPORTED)} "
              f"(got {a.codepage!r})", file=sys.stderr)
        return 2
    if not a.input.is_file():
        print(f"error: input file {a.input} does not exist", file=sys.stderr)
        return 2
    if a.output.exists() and os.path.samefile(a.input, a.output) or a.output.resolve() == a.input.resolve():
        print("error: the output path is the input file; the original is never overwritten", file=sys.stderr)
        return 2
    before = sha256(a.input)
    data = a.input.read_bytes()
    if a.lrecl is not None:
        if a.lrecl <= 0 or len(data) % a.lrecl != 0:
            print(f"error: {a.input} is {len(data)} bytes, not a multiple of the record length {a.lrecl}",
                  file=sys.stderr)
            return 3
    try:
        ascii_bytes = convert(data, a.codepage)
    except ValueError as e:
        print(f"error: {a.input}: {e}", file=sys.stderr)
        return 4
    a.output.write_bytes(ascii_bytes)
    if sha256(a.input) != before:
        print(f"error: {a.input} changed while it was converted", file=sys.stderr)
        return 5
    records = f", {len(data) // a.lrecl} records of {a.lrecl}" if a.lrecl else ""
    print(f"converted {a.input} ({len(data)} bytes{records}, code page {a.codepage}) -> {a.output}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
