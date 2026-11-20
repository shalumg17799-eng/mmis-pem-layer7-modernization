"""MAM-53: modern/tools/data/ebcdic_to_ascii.py converts a legacy EBCDIC dataset (code page 037 or 1047) to ASCII for the
Phase 9 parity run. Lane 3 tests (2026-11-19), written after the tool: one or more tests per acceptance criterion, each
named test_mam53_acN_* and marked "[MAM-53 AC-n]" in its docstring.

Every input is a synthetic byte string built here under pytest's tmp_path: no real data, no file of the repository.
The expected EBCDIC bytes come from the IBM code page tables written out below (hex), not from the tool's own table:
the tests prove that table (95 printable characters under each page, the eight positions where 1047 differs from 037)
instead of echoing it. The 037 half of the table is also checked against Python's own cp037 codec.

Run: modern/kag/.venv/Scripts/python.exe -m pytest -q modern/tools/checks/tests/test_ebcdic_to_ascii.py
"""
import hashlib
import importlib.util
import os
import re
import stat
import subprocess
import sys
from pathlib import Path

import pytest

sys.dont_write_bytecode = True

REPO = Path(__file__).resolve().parents[4]
# MAM53_TOOL lets the same tests run against a modified copy of the tool (mutation check); the default is the real tool
TOOL = Path(os.environ.get("MAM53_TOOL", REPO / "modern/tools/data/ebcdic_to_ascii.py"))
PAGES = ["037", "1047"]

# ---- the IBM code pages, written out: printable ASCII character -> EBCDIC byte -------------------------------------------


def _block(chars: str, first: int) -> dict:
    return {c: first + i for i, c in enumerate(chars)}


_COMMON = {" ": 0x40, "!": 0x5A, '"': 0x7F, "#": 0x7B, "$": 0x5B, "%": 0x6C, "&": 0x50, "'": 0x7D, "(": 0x4D, ")": 0x5D,
           "*": 0x5C, "+": 0x4E, ",": 0x6B, "-": 0x60, ".": 0x4B, "/": 0x61, ":": 0x7A, ";": 0x5E, "<": 0x4C, "=": 0x7E,
           ">": 0x6E, "?": 0x6F, "@": 0x7C, "\\": 0xE0, "_": 0x6D, "`": 0x79, "{": 0xC0, "|": 0x4F, "}": 0xD0, "~": 0xA1}
for _chars, _first in (("0123456789", 0xF0), ("ABCDEFGHI", 0xC1), ("JKLMNOPQR", 0xD1), ("STUVWXYZ", 0xE2),
                       ("abcdefghi", 0x81), ("jklmnopqr", 0x91), ("stuvwxyz", 0xA2)):
    _COMMON.update(_block(_chars, _first))

# the three characters whose byte differs between the pages: [ ] ^ (037: BA BB B0; 1047: AD BD 5F)
TABLE = {"037": dict(_COMMON, **{"[": 0xBA, "]": 0xBB, "^": 0xB0}),
         "1047": dict(_COMMON, **{"[": 0xAD, "]": 0xBD, "^": 0x5F})}
# the eight positions where IBM 1047 differs from 037: LF/NEL swapped, not sign and caret swapped, the brackets and Y acute/diaeresis
DIFFERING_POSITIONS = {0x15, 0x25, 0x5F, 0xAD, 0xB0, 0xBA, 0xBB, 0xBD}
PRINTABLE_ASCII = [chr(c) for c in range(0x20, 0x7F)]


def ebcdic(text: bytes, page: str) -> bytes:
    """ASCII text to EBCDIC bytes of the page, by the table above."""
    return bytes(TABLE[page][chr(c)] for c in text)


# the known text of AC-3: letters, digits, space and the punctuation of the legacy layouts, then [ ] ^ at the end.
# EBCDIC bytes recorded as hex (037 and 1047 agree everywhere except at the [ ] ^ bytes, offsets 109, 111, 113).
KNOWN_TEXT = (b"TESTPROV ALPHA 0123456789 abc xyz (A&B) #7 @1 $2 %3 +4 =5 <6> 'q' \"r\" ;s *t !u ?v |w ~x `y {z} \\ _ - / , . : "
              b"[A] ^B")
KNOWN_HEX = {
    "037": ("e3c5e2e3d7d9d6e540c1d3d7c8c140f0f1f2f3f4f5f6f7f8f94081828340a7a8a9404dc150c25d407bf7407cf1405bf2406cf3404ef4407e"
            "f5404cf66e407d987d407f997f405ea2405ca3405aa4406fa5404fa640a1a74079a840c0a9d040e0406d40604061406b404b407a40"
            "bac1bb40b0c2"),
    "1047": ("e3c5e2e3d7d9d6e540c1d3d7c8c140f0f1f2f3f4f5f6f7f8f94081828340a7a8a9404dc150c25d407bf7407cf1405bf2406cf3404ef4407e"
             "f5404cf66e407d987d407f997f405ea2405ca3405aa4406fa5404fa640a1a74079a840c0a9d040e0406d40604061406b404b407a40"
             "adc1bd405fc2"),
}
BRACKET_OFFSET = KNOWN_TEXT.index(b"[")                      # 109: the first byte where the pages differ
TEXT_WITHOUT_DIFFERING_CHARS = KNOWN_TEXT[:BRACKET_OFFSET].rstrip()


def load_tool():
    spec = importlib.util.spec_from_file_location("ebcdic_to_ascii_under_test", TOOL)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


TOOL_MODULE = load_tool()


# ---- helpers ---------------------------------------------------------------------------------------------------------

def run(*args) -> subprocess.CompletedProcess:
    env = dict(os.environ, PYTHONDONTWRITEBYTECODE="1")
    return subprocess.run([sys.executable, str(TOOL), *map(str, args)], capture_output=True, text=True,
                          errors="replace", env=env)


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def convert(tmp_path: Path, data: bytes, page: str | None, lrecl: int | None = None, name: str = "legacy.dat"):
    """Write `data` as the input file and run the tool; returns (process, input path, output path)."""
    src, out = tmp_path / name, tmp_path / (name + ".txt")
    src.write_bytes(data)
    args = [src, out]
    if page is not None:
        args += ["--codepage", page]
    if lrecl is not None:
        args += ["--lrecl", lrecl]
    return run(*args), src, out


def fixed_width_text(records: int, lrecl: int) -> bytes:
    """Synthetic ASCII fixed-width records: record n cycles through all 95 printable characters starting at character n,
    so every character, [ ] ^ included, is in the file (in every record of 95 bytes or more)."""
    return b"".join(bytes(0x20 + (n + i) % 95 for i in range(lrecl)) for n in range(records))


def message(proc: subprocess.CompletedProcess, *paths: Path) -> str:
    """stderr in lower case with the paths removed (a temp path holds digits that must not match a size or an offset)."""
    err = proc.stderr
    for p in paths:
        err = err.replace(str(p), "")
    return err.lower()


# ---- the oracle itself -----------------------------------------------------------------------------------------------

def test_the_oracle_tables_hold_all_printable_ascii_and_agree_with_pythons_cp037():
    for page in PAGES:
        assert sorted(TABLE[page]) == PRINTABLE_ASCII and len(set(TABLE[page].values())) == 95
    assert all(bytes([b]).decode("cp037") == ch for ch, b in TABLE["037"].items())
    assert {ch for ch in PRINTABLE_ASCII if TABLE["037"][ch] != TABLE["1047"][ch]} == {"[", "]", "^"}


@pytest.mark.parametrize("page", PAGES)
def test_the_recorded_hex_of_the_known_text_is_the_table_encoding(page):
    assert bytes.fromhex(KNOWN_HEX[page]) == ebcdic(KNOWN_TEXT, page)


# ---- AC-1: same byte count, one ASCII byte per EBCDIC byte, no line terminator added ---------------------------------

@pytest.mark.parametrize("page", PAGES)
@pytest.mark.parametrize("records,lrecl", [(1, 1), (1, 7), (3, 80), (2, 130), (5, 200)])
def test_mam53_ac1_output_has_the_same_number_of_bytes_as_the_input(tmp_path, page, records, lrecl):
    """[MAM-53 AC-1] the output file has as many bytes as the input, for several record shapes."""
    proc, src, out = convert(tmp_path, ebcdic(fixed_width_text(records, lrecl), page), page)
    assert proc.returncode == 0, proc.stderr
    assert out.stat().st_size == src.stat().st_size == records * lrecl


@pytest.mark.parametrize("page", PAGES)
def test_mam53_ac1_each_output_byte_is_the_ascii_of_the_input_byte_at_the_same_position(tmp_path, page):
    """[MAM-53 AC-1] one ASCII byte per EBCDIC byte: position i of the output is the conversion of position i."""
    text = fixed_width_text(3, 130)
    proc, _, out = convert(tmp_path, ebcdic(text, page), page)
    assert proc.returncode == 0, proc.stderr
    assert out.read_bytes() == text


@pytest.mark.parametrize("page", PAGES)
def test_mam53_ac1_no_line_terminator_is_added_between_or_after_the_records(tmp_path, page):
    """[MAM-53 AC-1] three 80-byte records come back as 240 bytes with no CR or LF anywhere, and no trailing one."""
    proc, _, out = convert(tmp_path, ebcdic(fixed_width_text(3, 80), page), page, lrecl=80)
    data = out.read_bytes()
    assert proc.returncode == 0, proc.stderr
    assert len(data) == 240 and b"\n" not in data and b"\r" not in data


@pytest.mark.parametrize("page", PAGES)
def test_mam53_ac1_an_empty_file_gives_an_empty_output(tmp_path, page):
    """[MAM-53 AC-1] zero bytes in, zero bytes out; the output file exists."""
    proc, _, out = convert(tmp_path, b"", page)
    assert proc.returncode == 0, proc.stderr
    assert out.is_file() and out.stat().st_size == 0


# ---- AC-2: the code page is required and checked ---------------------------------------------------------------------

def test_mam53_ac2_a_missing_code_page_exits_non_zero_and_lists_037_and_1047(tmp_path):
    """[MAM-53 AC-2] no --codepage: non-zero exit, a message naming both supported pages, no output file."""
    proc, _, out = convert(tmp_path, ebcdic(b"HELLO", "037"), None)
    assert proc.returncode != 0
    assert "037" in proc.stderr and "1047" in proc.stderr, proc.stderr
    assert not out.exists()


@pytest.mark.parametrize("page", ["500", "1140", "37", "0037", "cp037", "EBCDIC", "", "037,1047", " 1047"])
def test_mam53_ac2_an_unsupported_code_page_exits_non_zero_and_lists_037_and_1047(tmp_path, page):
    """[MAM-53 AC-2] a code page that is not exactly 037 or 1047: non-zero exit, both pages listed, no output file."""
    proc, _, out = convert(tmp_path, ebcdic(b"HELLO", "037"), page)
    assert proc.returncode != 0
    assert "037" in proc.stderr and "1047" in proc.stderr, proc.stderr
    assert not out.exists()


@pytest.mark.parametrize("page", [None, "500"])
def test_mam53_ac2_an_existing_output_file_is_left_as_it_was_when_the_code_page_is_refused(tmp_path, page):
    """[MAM-53 AC-2] a refused run writes nothing, so an output file that was already there keeps its content."""
    src, out = tmp_path / "legacy.dat", tmp_path / "legacy.txt"
    src.write_bytes(ebcdic(b"HELLO", "037"))
    out.write_bytes(b"EARLIER CONTENT")
    args = [src, out] + (["--codepage", page] if page else [])
    assert run(*args).returncode != 0
    assert out.read_bytes() == b"EARLIER CONTENT"


@pytest.mark.parametrize("page", PAGES)
def test_mam53_ac2_each_supported_code_page_is_accepted(tmp_path, page):
    """[MAM-53 AC-2] 037 and 1047 both run (the list in the message is the list of what works)."""
    proc, _, out = convert(tmp_path, ebcdic(b"HELLO", page), page)
    assert proc.returncode == 0, proc.stderr
    assert out.read_bytes() == b"HELLO"


# ---- AC-3: known text under both pages, and where the pages differ ---------------------------------------------------

@pytest.mark.parametrize("page", PAGES)
def test_mam53_ac3_the_known_text_converts_to_the_recorded_ascii_under_its_own_page(tmp_path, page):
    """[MAM-53 AC-3] letters, digits, space, layout punctuation and [ ] ^, encoded in `page`, give KNOWN_TEXT."""
    proc, _, out = convert(tmp_path, bytes.fromhex(KNOWN_HEX[page]), page)
    assert proc.returncode == 0, proc.stderr
    assert out.read_bytes() == KNOWN_TEXT


def test_mam53_ac3_the_two_encodings_of_the_known_text_differ_exactly_at_the_bracket_and_caret_bytes():
    """[MAM-53 AC-3] the fixture is really encoded differently: bytes differ at the offsets of [ ] ^ and nowhere else."""
    a, b = bytes.fromhex(KNOWN_HEX["037"]), bytes.fromhex(KNOWN_HEX["1047"])
    assert len(a) == len(b) == len(KNOWN_TEXT)
    assert [i for i in range(len(a)) if a[i] != b[i]] == [i for i, c in enumerate(KNOWN_TEXT) if chr(c) in "[]^"]


@pytest.mark.parametrize("hexbyte,page,expected", [
    ("C8C5D3D3D6", "037", b"HELLO"), ("C8C5D3D3D6", "1047", b"HELLO"),
    ("8885939396", "037", b"hello"), ("8885939396", "1047", b"hello"),
    ("F0F1F2F3F4F5F6F7F8F9", "037", b"0123456789"), ("F0F1F2F3F4F5F6F7F8F9", "1047", b"0123456789"),
    ("404B60617A", "037", b" .-/:"), ("404B60617A", "1047", b" .-/:"),
    ("BA", "037", b"["), ("BB", "037", b"]"), ("B0", "037", b"^"),
    ("AD", "1047", b"["), ("BD", "1047", b"]"), ("5F", "1047", b"^"),
])
def test_mam53_ac3_known_anchor_bytes_convert_as_the_ibm_tables_say(tmp_path, hexbyte, page, expected):
    """[MAM-53 AC-3] anchors anyone can check against the IBM tables: HELLO, hello, digits, space . - / : and [ ] ^."""
    proc, _, out = convert(tmp_path, bytes.fromhex(hexbyte), page)
    assert proc.returncode == 0, proc.stderr
    assert out.read_bytes() == expected


@pytest.mark.parametrize("wrong,right", [("1047", "037"), ("037", "1047")])
def test_mam53_ac3_a_file_converted_with_the_other_page_stops_at_the_first_differing_byte(tmp_path, wrong, right):
    """[MAM-53 AC-3] the known text in `right` converted with `wrong` gives a different result: the run stops at the
    first byte where the pages differ (the [ at offset 109), names it and writes no ASCII (D-024: no guessing)."""
    data = bytes.fromhex(KNOWN_HEX[right])
    proc, src, out = convert(tmp_path, data, wrong)
    err = message(proc, src, out)
    assert proc.returncode != 0
    assert f"offset {BRACKET_OFFSET}" in err and f"0x{data[BRACKET_OFFSET]:02x}" in err, proc.stderr
    assert not out.exists()


@pytest.mark.parametrize("page", PAGES)
def test_mam53_ac3_text_without_the_differing_characters_converts_the_same_under_both_pages(tmp_path, page):
    """[MAM-53 AC-3] before the first [ ] ^ the two encodings are the same bytes and give the same ASCII."""
    data = bytes.fromhex(KNOWN_HEX["037"])[:BRACKET_OFFSET]
    assert data == bytes.fromhex(KNOWN_HEX["1047"])[:BRACKET_OFFSET]
    proc, _, out = convert(tmp_path, data, page)
    assert proc.returncode == 0, proc.stderr
    assert out.read_bytes() == KNOWN_TEXT[:BRACKET_OFFSET]


def test_mam53_ac3_bytes_that_1047_reads_as_caret_and_brackets_are_refused_by_037(tmp_path):
    """[MAM-53 AC-3] 5F AD BD is ^[] under 1047 and not printable ASCII under 037: a different result."""
    data = bytes.fromhex("5FADBD")
    ok, _, out_ok = convert(tmp_path, data, "1047", name="under1047.dat")
    refused, src, out_refused = convert(tmp_path, data, "037", name="under037.dat")
    assert ok.returncode == 0 and out_ok.read_bytes() == b"^[]"
    assert refused.returncode != 0 and "offset 0" in message(refused, src, out_refused) and not out_refused.exists()


def test_mam53_ac3_the_pages_differ_at_exactly_the_eight_documented_positions():
    """[MAM-53 AC-3] 1047 differs from 037 at 15 25 5F AD B0 BA BB BD and at no other byte."""
    t037, t1047 = TOOL_MODULE.table("037"), TOOL_MODULE.table("1047")
    assert {b for b in range(256) if t037[b] != t1047[b]} == DIFFERING_POSITIONS


def test_mam53_ac3_the_result_differs_between_the_pages_exactly_where_one_page_reads_a_bracket_or_caret():
    """[MAM-53 AC-3] per byte, the outcome (an ASCII character or a refusal) differs between 037 and 1047 for exactly the
    six bytes of [ ] ^; the LF/NEL pair 15 and 25 is refused under both pages. The printable side is the oracle's."""
    def outcome(page, b):
        try:
            return TOOL_MODULE.convert(bytes([b]), page).decode("ascii")
        except ValueError:
            return None

    differing = {b for b in range(256) if outcome("037", b) != outcome("1047", b)}
    assert differing == {0x5F, 0xAD, 0xB0, 0xBA, 0xBB, 0xBD}
    assert outcome("037", 0x15) is outcome("1047", 0x15) is outcome("037", 0x25) is outcome("1047", 0x25) is None
    for ch in "[]^":
        assert outcome("037", TABLE["037"][ch]) == ch and outcome("1047", TABLE["1047"][ch]) == ch


@pytest.mark.parametrize("page", PAGES)
def test_mam53_ac3_exactly_the_95_printable_ascii_characters_convert_and_each_gives_its_own_character(page):
    """[MAM-53 AC-3] across all 256 byte values the page converts the 95 bytes of the table to their characters."""
    converted = {}
    for b in range(256):
        try:
            converted[b] = TOOL_MODULE.convert(bytes([b]), page).decode("ascii")
        except ValueError:
            pass
    assert converted == {byte: ch for ch, byte in TABLE[page].items()}


# ---- AC-4: round trip ------------------------------------------------------------------------------------------------

@pytest.mark.parametrize("page", PAGES)
@pytest.mark.parametrize("lrecl", [80, 130, 200])
def test_mam53_ac4_encode_in_the_test_and_convert_back_returns_the_original_bytes(tmp_path, page, lrecl):
    """[MAM-53 AC-4] an ASCII fixed-width fixture of 3 records, every printable character in it, encoded here with the
    page's table and converted back with --lrecl, equals the original (SANCIN 80, PRVRECRD 130, APPINPUT 200)."""
    original = fixed_width_text(3, lrecl)
    proc, _, out = convert(tmp_path, ebcdic(original, page), page, lrecl=lrecl)
    assert proc.returncode == 0, proc.stderr
    assert out.read_bytes() == original


@pytest.mark.parametrize("page", PAGES)
def test_mam53_ac4_a_sanction_input_shaped_fixture_round_trips(tmp_path, page):
    """[MAM-53 AC-4] two SANCIN-shaped 80-byte records (NPI, action, reason, date, filler) with brackets in the reason."""
    def record(npi, action, reason):
        return (npi + action + reason.ljust(40) + "20260115" + " " * 21).encode("ascii")

    original = record("1006100019", "T", "SAMPLE [TERMINATION]: ^VIOLATION") + record("1006100027", "R", "SAMPLE REINSTATE")
    assert len(original) == 160
    proc, _, out = convert(tmp_path, ebcdic(original, page), page, lrecl=80)
    assert proc.returncode == 0, proc.stderr
    assert out.read_bytes() == original


@pytest.mark.parametrize("page", PAGES)
def test_mam53_ac4_the_tools_own_encode_helper_agrees_with_the_table_and_round_trips(page):
    """[MAM-53 AC-4] encode() (used for the round trip in the story report) gives the table's bytes and convert() undoes it."""
    original = bytes(range(0x20, 0x7F)) * 2
    encoded = TOOL_MODULE.encode(original, page)
    assert encoded == ebcdic(original, page)
    assert TOOL_MODULE.convert(encoded, page) == original


# ---- AC-5: record length and non-text bytes --------------------------------------------------------------------------

@pytest.mark.parametrize("size", [1, 79, 81, 159, 161])
def test_mam53_ac5_a_size_that_is_not_a_multiple_of_lrecl_names_the_size_and_the_record_length(tmp_path, size):
    """[MAM-53 AC-5] --lrecl 80 with a file of `size` bytes: non-zero exit, the message names the size and 80, no output."""
    proc, src, out = convert(tmp_path, ebcdic(fixed_width_text(1, size), "037"), "037", lrecl=80)
    err = message(proc, src, out)
    assert proc.returncode != 0
    assert re.search(rf"\b{size}\b", err) and re.search(r"\b80\b", err), proc.stderr
    assert not out.exists()


@pytest.mark.parametrize("size", [0, 80, 160, 800])
def test_mam53_ac5_a_size_that_is_a_multiple_of_lrecl_is_converted(tmp_path, size):
    """[MAM-53 AC-5] the length check passes for 0, 1, 2 and 10 records of 80 bytes."""
    proc, _, out = convert(tmp_path, ebcdic(fixed_width_text(1, size), "1047"), "1047", lrecl=80)
    assert proc.returncode == 0, proc.stderr
    assert out.stat().st_size == size


@pytest.mark.parametrize("lrecl", ["0", "-80"])
def test_mam53_ac5_a_record_length_that_is_not_positive_is_refused_naming_size_and_length(tmp_path, lrecl):
    """[MAM-53 AC-5] --lrecl 0 or negative can never divide the file: non-zero exit, size and length named, no output."""
    src, out = tmp_path / "legacy.dat", tmp_path / "legacy.txt"
    src.write_bytes(ebcdic(fixed_width_text(1, 100), "037"))
    proc = run(src, out, "--codepage", "037", f"--lrecl={lrecl}")
    err = message(proc, src, out)
    assert proc.returncode != 0
    assert re.search(r"\b100\b", err) and lrecl in err, proc.stderr
    assert not out.exists()


NON_TEXT_BYTES = [0x00, 0x15, 0x25, 0x41, 0x4A, 0x80, 0xFF]


@pytest.mark.parametrize("page", PAGES)
@pytest.mark.parametrize("bad", NON_TEXT_BYTES)
def test_mam53_ac5_a_non_text_byte_exits_non_zero_naming_its_offset_and_value_and_writes_no_output(tmp_path, page, bad):
    """[MAM-53 AC-5] NUL, the EBCDIC LF and NEL, 41 and 4A (non-ASCII letters), 80 and FF: each stops the run at its offset."""
    assert bad not in TABLE[page].values()
    data = ebcdic(b"AB CD", page) + bytes([bad]) + ebcdic(b"EF", page)
    proc, src, out = convert(tmp_path, data, page)
    err = message(proc, src, out)
    assert proc.returncode != 0
    assert re.search(r"offset\s+5\b", err) and f"0x{bad:02x}" in err, proc.stderr
    assert not out.exists()


@pytest.mark.parametrize("position", ["first", "last"])
def test_mam53_ac5_a_non_text_byte_at_the_first_or_last_offset_is_found(tmp_path, position):
    """[MAM-53 AC-5] offset 0 and offset size-1 are both checked (the whole file is scanned, with a valid --lrecl)."""
    body = ebcdic(fixed_width_text(1, 79), "037")
    data, offset = (b"\x00" + body, 0) if position == "first" else (body + b"\xff", 79)
    proc, src, out = convert(tmp_path, data, "037", lrecl=80)
    err = message(proc, src, out)
    assert proc.returncode != 0
    assert re.search(rf"offset\s+{offset}\b", err) and f"0x{data[offset]:02x}" in err, proc.stderr
    assert not out.exists()


def test_mam53_ac5_the_first_of_several_non_text_bytes_is_the_one_named(tmp_path):
    """[MAM-53 AC-5] bytes at offsets 3 and 9: the message names offset 3 and its value, not offset 9."""
    data = ebcdic(b"ABC", "037") + b"\x00" + ebcdic(b"DEFGH", "037") + b"\xff" + ebcdic(b"I", "037")
    proc, src, out = convert(tmp_path, data, "037")
    err = message(proc, src, out)
    assert re.search(r"offset\s+3\b", err) and "0x00" in err and "offset 9" not in err and "0xff" not in err, proc.stderr


def test_mam53_ac5_nothing_is_written_when_the_bad_byte_comes_after_a_long_valid_prefix(tmp_path):
    """[MAM-53 AC-5] no partial output: 20,000 valid bytes then one bad byte leaves no output file at all."""
    data = ebcdic(fixed_width_text(1, 20000), "037") + b"\x00"
    proc, _, out = convert(tmp_path, data, "037")
    assert proc.returncode != 0 and not out.exists()


def test_mam53_ac5_a_refused_run_leaves_an_existing_output_file_untouched(tmp_path):
    """[MAM-53 AC-5] no replacement character, no truncation: the earlier output file keeps its content."""
    src, out = tmp_path / "legacy.dat", tmp_path / "legacy.txt"
    src.write_bytes(ebcdic(b"ABC", "037") + b"\x00")
    out.write_bytes(b"EARLIER CONTENT")
    assert run(src, out, "--codepage", "037").returncode != 0
    assert out.read_bytes() == b"EARLIER CONTENT"


@pytest.mark.parametrize("page", PAGES)
def test_mam53_ac5_every_byte_outside_the_table_is_refused_with_offset_and_value_never_replaced(page):
    """[MAM-53 AC-5] all 161 byte values that are not one of the 95 printable characters raise, naming offset 2 and the
    value; no byte is ever turned into ?, U+FFFD or any other substitute."""
    for b in sorted(set(range(256)) - set(TABLE[page].values())):
        with pytest.raises(ValueError) as e:
            TOOL_MODULE.convert(ebcdic(b"AB", page) + bytes([b]) + ebcdic(b"C", page), page)
        text = str(e.value).lower()
        assert re.search(r"offset\s+2\b", text) and f"0x{b:02x}" in text, (hex(b), text)


@pytest.mark.parametrize("page", PAGES)
def test_mam53_ac5_a_successful_run_writes_only_the_converted_printable_ascii(tmp_path, page):
    """[MAM-53 AC-5] the output of a good run holds printable ASCII only: no replacement character, no control byte."""
    proc, _, out = convert(tmp_path, ebcdic(fixed_width_text(4, 95), page), page, lrecl=95)
    data = out.read_bytes()
    assert proc.returncode == 0, proc.stderr
    assert all(0x20 <= b <= 0x7E for b in data) and b"\xef\xbf\xbd" not in data


# ---- AC-6: the original is never touched -----------------------------------------------------------------------------

def test_mam53_ac6_an_output_path_equal_to_the_input_path_is_refused_with_a_message(tmp_path):
    """[MAM-53 AC-6] same path twice: non-zero exit, a message, and the input is byte for byte as it was."""
    src = tmp_path / "legacy.dat"
    original = ebcdic(fixed_width_text(2, 80), "037")
    src.write_bytes(original)
    proc = run(src, src, "--codepage", "037")
    assert proc.returncode != 0
    assert proc.stderr.strip() and "output" in proc.stderr.lower(), proc.stderr
    assert src.read_bytes() == original


def test_mam53_ac6_the_same_file_spelled_differently_is_refused(tmp_path):
    """[MAM-53 AC-6] dir/../dir/name and name are one file: refused, input unchanged."""
    (tmp_path / "dir").mkdir()
    src = tmp_path / "dir" / "legacy.dat"
    src.write_bytes(ebcdic(fixed_width_text(1, 80), "1047"))
    before = sha256(src)
    proc = run(src, tmp_path / "dir" / ".." / "dir" / "legacy.dat", "--codepage", "1047")
    assert proc.returncode != 0 and proc.stderr.strip()
    assert sha256(src) == before


def test_mam53_ac6_a_second_name_of_the_same_file_a_hard_link_is_refused(tmp_path):
    """[MAM-53 AC-6] a hard link is the same file under another name: refused, input unchanged."""
    src, alias = tmp_path / "legacy.dat", tmp_path / "alias.dat"
    src.write_bytes(ebcdic(fixed_width_text(1, 80), "037"))
    os.link(src, alias)
    before = sha256(src)
    proc = run(src, alias, "--codepage", "037")
    assert proc.returncode != 0 and proc.stderr.strip()
    assert sha256(src) == before and sha256(alias) == before


@pytest.mark.parametrize("case", ["converted 037", "037 file read as 1047", "converted with lrecl", "no code page",
                                  "unsupported code page", "bad record length", "non-text byte", "same path",
                                  "output in a missing folder"])
def test_mam53_ac6_the_input_sha256_is_unchanged_in_every_kind_of_run(tmp_path, case):
    """[MAM-53 AC-6] success, every refusal and every failure: the sha256 of the input before and after is the same."""
    src, out = tmp_path / "legacy.dat", tmp_path / "legacy.txt"
    good = ebcdic(fixed_width_text(2, 80), "037")
    data = {"non-text byte": good[:100] + b"\x00" + good[101:], "bad record length": good[:-1]}.get(case, good)
    src.write_bytes(data)
    before = sha256(src)
    args = {
        "converted 037": [src, out, "--codepage", "037"],
        "037 file read as 1047": [src, out, "--codepage", "1047"],     # refused at the first [ ] ^
        "converted with lrecl": [src, out, "--codepage", "037", "--lrecl", "80"],
        "no code page": [src, out],
        "unsupported code page": [src, out, "--codepage", "500"],
        "bad record length": [src, out, "--codepage", "037", "--lrecl", "80"],
        "non-text byte": [src, out, "--codepage", "037"],
        "same path": [src, src, "--codepage", "037"],
        "output in a missing folder": [src, tmp_path / "missing" / "legacy.txt", "--codepage", "037"],
    }[case]
    proc = run(*args)
    assert (proc.returncode == 0) == (case in ("converted 037", "converted with lrecl")), proc.stderr
    assert sha256(src) == before and src.stat().st_size == len(data)


def test_mam53_ac6_a_read_only_input_is_converted_which_shows_the_tool_never_opens_it_for_writing(tmp_path):
    """[MAM-53 AC-6] the input is read only: with the read-only attribute set the run still succeeds, sha256 unchanged."""
    src, out = tmp_path / "legacy.dat", tmp_path / "legacy.txt"
    text = fixed_width_text(2, 80)
    src.write_bytes(ebcdic(text, "037"))
    before = sha256(src)
    os.chmod(src, stat.S_IREAD)
    try:
        proc = run(src, out, "--codepage", "037")
        assert proc.returncode == 0, proc.stderr
        assert out.read_bytes() == text
        assert sha256(src) == before
    finally:
        os.chmod(src, stat.S_IREAD | stat.S_IWRITE)


@pytest.mark.parametrize("page", PAGES)
def test_mam53_ac6_a_successful_run_leaves_the_input_in_ebcdic_and_writes_a_separate_file(tmp_path, page):
    """[MAM-53 AC-6] after a good run the input still holds the EBCDIC bytes and the output is a different file."""
    text = fixed_width_text(1, 130)
    proc, src, out = convert(tmp_path, ebcdic(text, page), page)
    assert proc.returncode == 0, proc.stderr
    assert src.read_bytes() == ebcdic(text, page) and out.read_bytes() == text and not os.path.samefile(src, out)
