#!/usr/bin/env python3
"""MAM-44 AC-7: the no-real-data scan over every generated file under modern/testdata/.

The scan FAILS (exit 1, one line per finding) on anything that is not synthetic:
  * every NPI (a 10-digit number anywhere in a generated file, and every NPI field of the fixed-width files) must have
    a valid check digit (Luhn with the 80840 prefix) and lie outside the reserved ranges 1003000000-1003000999 and
    1990000000-1990000999; the only exceptions are the NPIs of aws-transform/test-data (providers.txt and leie.txt),
    which are allowed verbatim in scenarios/ and nowhere else (QF-12, option A);
  * generated names are obviously fake: an individual's first name is TESTPROV, an organisation's legal name starts
    with 'SAMPLE '; in scenarios/ a name must be exactly a name of aws-transform/test-data/providers.txt (or blank);
  * no street address other than '123 TEST ST', no e-mail address outside example.test / providers.pem.local, no
    number shaped like a social security number.
Scanned: fixed-width/, scenarios/, negative/, coverage*.csv, manifest.csv and seed/**/*.sql (README and code are not data).

Usage:  python modern/tools/data/scan_no_real_data.py [--root DIR]
        python modern/tools/data/scan_no_real_data.py --self-test   injects five violations into a scratch copy; each
                                                                     must be reported, and the clean copy must pass
"""
import sys

sys.dont_write_bytecode = True

import argparse
import re
import shutil
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from pemdata import (TEST_DATA_FEEDS, TESTDATA, Layout, npi_check_digit, npi_reserved, npi_valid,
                     read_records)

LAYOUT_OF = {
    "appinput-baseline.dat": "APPINPUT", "appinput-scenarios.dat": "APPINPUT", "appinput-bad-length.dat": "APPINPUT",
    "provider-master-seed.dat": "PRVRECRD", "provider-master-edge.dat": "PRVRECRD",
    "sancin-baseline.dat": "SANCIN", "sancin-malformed.dat": "SANCIN",
}
NPI_FIELDS = ("PRV-NPI", "PRV-OWNER-NPI", "SAN-NPI")
TEN_DIGITS = re.compile(r"(?<!\d)\d{10}(?!\d)")
UUID = re.compile(r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
HEX64 = re.compile(r"[0-9a-f]{64}")
STREET = re.compile(r"\b\d{1,5} [A-Z][A-Z ]{1,30} (?:ST|STREET|AVE|AVENUE|RD|ROAD|BLVD|DR|DRIVE|LN|LANE)\b")
EMAIL = re.compile(r"[\w.+-]+@[\w-]+(?:\.[\w-]+)+")
SSN = re.compile(r"(?<!\d)\d{3}-\d{2}-\d{4}(?!\d)")


def verbatim():
    """The fabricated NPIs and names of aws-transform/test-data, which scenarios/ may repeat verbatim."""
    npis, names = set(), set()
    for line in (TEST_DATA_FEEDS / "providers.txt").read_text(encoding="utf-8").splitlines():
        if line.strip() and not line.startswith("#"):
            c = line.split("|")
            npis.add(c[0])
            if c[5]:
                npis.add(c[5])
            if c[1]:
                names.add(c[1])
    for line in (TEST_DATA_FEEDS / "leie.txt").read_text(encoding="utf-8").splitlines():
        if line.strip() and not line.startswith("#"):
            npis.add(line.split("|")[0])
    return npis, names


def scanned_files(root: Path):
    for p in sorted(root.rglob("*")):
        rel = p.relative_to(root).as_posix()
        if not p.is_file() or "__pycache__" in rel:
            continue
        if rel.startswith(("fixed-width/", "scenarios/", "negative/")) or rel in ("manifest.csv",) \
                or (rel.startswith("coverage") and rel.endswith(".csv")) \
                or (rel.startswith("seed/") and rel.endswith(".sql")):
            yield p, rel


def check_npi(npi: str, rel: str, where: str, allowed_npis: set, findings: list):
    in_scenarios = rel.startswith("scenarios/")
    if in_scenarios and npi in allowed_npis:
        return                                                  # verbatim from aws-transform/test-data
    if npi in allowed_npis:
        findings.append(f"{rel} {where}: NPI {npi} of aws-transform/test-data is only allowed verbatim in scenarios/")
    elif not (len(npi) == 10 and npi.isdigit()):
        findings.append(f"{rel} {where}: NPI '{npi}' is not 10 digits")
    elif not npi_valid(npi):
        findings.append(f"{rel} {where}: NPI {npi} has an invalid check digit (Luhn with prefix 80840)")
    elif npi_reserved(npi):
        findings.append(f"{rel} {where}: NPI {npi} lies in a reserved range (1003000000-999, 1990000000-999)")


def check_text(text: str, rel: str, where: str, allowed_npis: set, findings: list):
    """Numbers and strings of one piece of text: ten-digit numbers are NPIs, no street address, e-mail or SSN."""
    stripped = HEX64.sub("", UUID.sub("", text))
    for m in sorted(set(TEN_DIGITS.findall(stripped))):
        check_npi(m, rel, where, allowed_npis, findings)
    for m in STREET.findall(stripped):
        if m != "123 TEST ST":
            findings.append(f"{rel} {where}: street address '{m}'")
    for m in EMAIL.findall(stripped):
        if not m.endswith(("@example.test", "@providers.pem.local")):
            findings.append(f"{rel} {where}: e-mail address '{m}'")
    for m in SSN.findall(stripped):
        findings.append(f"{rel} {where}: social-security-shaped number '{m}'")


def scan(root: Path) -> list:
    allowed_npis, allowed_names = verbatim()
    findings, files = [], 0
    for path, rel in scanned_files(root):
        files += 1
        layout_name = LAYOUT_OF.get(path.name)
        if layout_name and rel.endswith(".dat"):
            # a fixed-width file has no separators, so it is read field by field, by layout position
            lay = Layout(layout_name)
            for n, rec in enumerate(read_records(path), 1):
                f = lay.parse(rec, all_views=True)
                for field, v in f.items():
                    if field in NPI_FIELDS:
                        if v == "" and field != "SAN-NPI":
                            continue                            # blank NPI: the E101 record; blank owner
                        check_npi(v, rel, f"record {n} {field}", allowed_npis, findings)
                    else:
                        check_text(v, rel, f"record {n} {field}", allowed_npis, findings)
                check_names(f, rel, n, allowed_names, findings)
        else:
            check_text(path.read_bytes().decode("latin-1"), rel, "text", allowed_npis, findings)
    if files == 0:
        findings.append(f"{root}: no generated file found to scan")
    return findings


def check_names(f: dict, rel: str, n: int, allowed_names: set, findings: list):
    """The REDEFINES view of the type decides which name fields are meant: IN the person, GR and FA the organisation;
    a type that is neither (the invalid-type applications) must read as one of the two."""
    typ = f.get("PRV-TYPE-CODE")
    if typ is None:
        return                                          # SANCIN: no names
    last, first = f.get("PRV-LAST-NAME", ""), f.get("PRV-FIRST-NAME", "")
    legal, dba = f.get("PRV-LEGAL-NAME", ""), f.get("PRV-DBA-NAME", "")
    person = (last + " " + first).strip()
    if rel.startswith("scenarios/"):
        # the feed name verbatim: LAST + ' ' + FIRST for an individual, the legal name for an organisation
        name = person if typ == "IN" else legal
        if name and name not in allowed_names:
            findings.append(f"{rel} record {n}: name '{name}' is not a name of aws-transform/test-data/providers.txt")
        if typ != "IN" and dba:
            findings.append(f"{rel} record {n}: unexpected DBA name '{dba}'")
        return
    person_ok = first == "TESTPROV"
    org_ok = legal.startswith("SAMPLE ") and (dba == "" or dba.startswith("SAMPLE "))
    if typ == "IN" and not person_ok:
        findings.append(f"{rel} record {n}: first name '{first}' is not the obviously fake TESTPROV")
    elif typ in ("GR", "FA") and not org_ok:
        findings.append(f"{rel} record {n}: legal name '{legal}' / DBA '{dba}' do not start with SAMPLE")
    elif typ not in ("IN", "GR", "FA") and not (person_ok or org_ok):
        findings.append(f"{rel} record {n}: names are not obviously fake (first '{first}', legal '{legal}')")


def self_test() -> int:
    """Five injected violations into a scratch copy: each must be reported; the untouched copy must pass."""
    tmp = Path(tempfile.mkdtemp(prefix="pem-scan-selftest-"))
    try:
        base = tmp / "clean"
        for sub in ("fixed-width", "scenarios", "negative"):
            shutil.copytree(TESTDATA / sub, base / sub)
        for name in ("manifest.csv", "coverage.csv", "coverage-gaps.csv"):
            shutil.copy(TESTDATA / name, base / name)
        shutil.copytree(TESTDATA / "seed", base / "seed")
        clean = scan(base)
        ok = not clean
        print(("PASS" if ok else "FAIL") + f": the clean copy reports {len(clean)} findings")
        edge = (base / "fixed-width/provider-master-edge.dat").read_bytes().split(b"\n")[0]
        reserved = ("100300012" + npi_check_digit("100300012")).encode()      # valid check digit, reserved range
        assert npi_valid(reserved.decode()) and npi_reserved(reserved.decode())
        cases = {
            "an NPI with an invalid check digit": lambda r: r.replace(edge[:10], b"1005940866", 1),
            "a Luhn-valid NPI in the reserved range 1003000000-999": lambda r: r.replace(edge[:10], reserved, 1),
            "a test-data NPI (1003000111) outside scenarios/": lambda r: r.replace(edge[:10], b"1003000111", 1),
            "a real-looking name (JOHN SMITH)": lambda r: r.replace(b"TESTPROV  ", b"JOHN SMITH", 1),
            "a street address (456 OAK AVE)": lambda r: r.replace(b"FUTURE START", b"456 OAK AVE ", 1),
        }
        for label, mutate in cases.items():
            case = tmp / "case"
            if case.exists():
                shutil.rmtree(case)
            shutil.copytree(base, case)
            target = case / "fixed-width/provider-master-edge.dat"
            target.write_bytes(mutate(target.read_bytes()))
            found = scan(case)
            hit = bool(found)
            ok = ok and hit
            print(("PASS" if hit else "FAIL") + f": injected {label}: {len(found)} finding(s)" +
                  (f", e.g. {found[0]}" if found else ""))
        return 0 if ok else 1
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--root", type=Path, default=TESTDATA)
    ap.add_argument("--self-test", action="store_true")
    args = ap.parse_args()
    if args.self_test:
        sys.exit(self_test())
    findings = scan(args.root)
    files = sum(1 for _ in scanned_files(args.root))
    if findings:
        for line in findings:
            print("FAIL:", line)
        print(f"FAIL: {len(findings)} finding(s) in {files} files")
        sys.exit(1)
    print(f"OK: no real data found in {files} generated files (valid NPI check digits, outside the reserved ranges; "
          f"names obviously fake; test-data values only in scenarios/)")


if __name__ == "__main__":
    main()
