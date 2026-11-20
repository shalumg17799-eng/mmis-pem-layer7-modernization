"""Shared helpers of the synthetic-data tools (MAM-44): paths, the layout reader, NPI check digit.

Field positions are never typed here: every layout is read from the JSON files exported from the knowledge graph
(modern/libs/fixed-width/src/main/resources/layouts/<NAME>.json, tools/data/export_layouts.py). A record is built
and parsed by field NAME against those files, with the REDEFINES view chosen by the layout's discriminator.

Synthetic data only (synthetic-data skill section 1).
"""
import sys

sys.dont_write_bytecode = True

import json
from pathlib import Path

REPO = Path(__file__).resolve().parents[3]
MODERN = REPO / "modern"
TESTDATA = MODERN / "testdata"
LAYOUT_DIR = MODERN / "libs/fixed-width/src/main/resources/layouts"
TEST_DATA_FEEDS = REPO / "aws-transform/test-data"

SEED = 20260115                  # the seed of every generator; it is also the frozen business date
BUSINESS_DATE = "20260115"       # CCYYMMDD, frozen for all fixtures (synthetic-data skill section 4)
NO_DATE = "99991231"             # "no date" in a date field: the codec rejects blank and 00000000 (D-024), see README

RESERVED_NPI_RANGES = ((1003000000, 1003000999), (1990000000, 1990000999))


def npi_check_digit(first9: str) -> str:
    """NPI check digit: Luhn over the prefix 80840 and the first nine digits."""
    total = 0
    for i, ch in enumerate(reversed("80840" + first9)):
        d = int(ch)
        if i % 2 == 0:
            d = d * 2 - 9 if d * 2 > 9 else d * 2
        total += d
    return str((10 - total % 10) % 10)


def npi_valid(npi: str) -> bool:
    return len(npi) == 10 and npi.isdigit() and npi_check_digit(npi[:9]) == npi[9]


def npi_reserved(npi: str) -> bool:
    n = int(npi)
    return any(lo <= n <= hi for lo, hi in RESERVED_NPI_RANGES)


# The classic worked example of the NPI algorithm: 123456789 has check digit 3.
assert npi_check_digit("123456789") == "3", "NPI check digit self-test failed"


class Layout:
    """A fixed-width layout read from its exported JSON file."""

    def __init__(self, name: str):
        d = json.loads((LAYOUT_DIR / f"{name}.json").read_text(encoding="utf-8"))
        self.name = d["name"]
        self.length = d["length"]
        self.discriminator = d["discriminator"]
        self.fields = d["fields"]
        self.by_name = {}
        for f in self.fields:
            self.by_name.setdefault(f["name"], f)

    def _in_view(self, f, disc):
        return not f["views"] or disc in f["views"]

    def build(self, values: dict, view: str | None = None) -> bytes:
        """One record: alphanumeric fields right-padded with spaces, digit fields left zero-filled. A value for a
        field outside the record's view is an error; `view` forces the view when the discriminator holds a value
        that has none (the invalid type of an E102 application)."""
        disc = view or values.get(self.discriminator, "")
        out = bytearray(b" " * self.length)
        for name, value in values.items():
            f = self.by_name[name]
            if not self._in_view(f, disc):
                raise ValueError(f"{self.name}: field {name} does not exist for view '{disc}'")
            text = "" if value is None else str(value)
            if len(text) > f["length"]:
                raise ValueError(f"{self.name}.{name}: '{text}' is longer than {f['length']}")
            if f["type"] == "ALPHANUMERIC":
                text = text.ljust(f["length"])
            else:
                if not text.isdigit():
                    raise ValueError(f"{self.name}.{name}: '{text}' is not numeric")
                text = text.rjust(f["length"], "0")
            data = text.encode("ascii")
            out[f["start"] - 1:f["start"] - 1 + f["length"]] = data
        for f in self.fields:
            if f["type"] != "ALPHANUMERIC" and self._in_view(f, disc) and f["name"] not in values:
                raise ValueError(f"{self.name}: no value for the digit field {f['name']}")
        return bytes(out)

    def parse(self, record: bytes, all_views: bool = False) -> dict:
        """Field name -> text (trailing spaces removed). Fields of a view the discriminator does not select are left
        out unless all_views is set. A short record yields short (or empty) values and never raises."""
        text = record.decode("latin-1")
        disc = None
        if self.discriminator:
            f = self.by_name[self.discriminator]
            disc = text[f["start"] - 1:f["start"] - 1 + f["length"]].strip()
        out = {}
        for f in self.fields:
            if not all_views and not self._in_view(f, disc):
                continue
            out[f["name"]] = text[f["start"] - 1:f["start"] - 1 + f["length"]].rstrip(" ")
        return out


def read_records(path: Path) -> list[bytes]:
    """The records of a generated file: one per line (LF)."""
    data = path.read_bytes()
    if not data:
        return []
    lines = data.split(b"\n")
    if lines[-1] == b"":
        lines.pop()
    return lines


def write_records(path: Path, records: list[bytes]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(b"".join(r + b"\n" for r in records))


def write_text(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(text.encode("utf-8").replace(b"\r\n", b"\n"))
