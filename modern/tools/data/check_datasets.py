#!/usr/bin/env python3
"""MAM-44 checks of AC-1 to AC-5 on the generated datasets, by reading the files back from disk.

  AC-1  determinism: the generator is run twice in fresh Python processes (random hash seeds) into scratch folders;
        every generated file must have the same sha256 in both runs and in modern/testdata/; manifest.csv must
        match the files; the README records the seed 20260115 and the frozen business date 2026-01-15.
  AC-2  fixed-width/appinput-baseline.dat: every record 200 bytes; valid IN, GR, FA; E101, E102, E104, E103.
  AC-3  fixed-width/provider-master-seed.dat: every record 130 bytes; statuses P A T D S; flags U P F; tiers L M H;
        prefixes 332 251 341 291 225 and another; due dates before, equal to and after 2026-01-15; every date is 8
        digits with a 4-digit year; the file tells the same story as seed/provider_db/01-providers.sql.
  AC-4  fixed-width/sancin-baseline.dat: every record 80 bytes; T, S, R, an unknown NPI, an invalid action.
  AC-5  scenarios/: a 200-byte record for each of the 8 NPIs of providers.txt, the QF-2 type mapping, synthetic
        Medicaid IDs and taxonomies (207Q00000X for 1003000111), and the expected-outcome list.
  also  the edge file (130 bytes) and the negative files (exactly the malformed records they are meant to hold).

Usage:  python modern/tools/data/check_datasets.py [--skip-determinism] [--scratch DIR]
Exit 1 if any check fails.
"""
import sys

sys.dont_write_bytecode = True

import argparse
import csv
import hashlib
import os
import re
import shutil
import subprocess
import tempfile
from datetime import date
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from pemdata import BUSINESS_DATE, NO_DATE, SEED, TEST_DATA_FEEDS, TESTDATA, Layout, read_records

FAILURES = []


def check(ok: bool, label: str, detail: str = ""):
    print(("PASS" if ok else "FAIL") + f": {label}" + (f" ({detail})" if detail else ""))
    if not ok:
        FAILURES.append(label)


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def tree_hashes(root: Path) -> dict:
    return {p.relative_to(root).as_posix(): sha(p) for p in sorted(root.rglob("*")) if p.is_file()}


def real_date(text: str) -> bool:
    try:
        date(int(text[:4]), int(text[4:6]), int(text[6:8]))
        return len(text) == 8 and text.isdigit()
    except ValueError:
        return False


# ---- AC-1 ------------------------------------------------------------------------------------------------------------

def ac1(scratch: Path | None):
    base = Path(tempfile.mkdtemp(prefix="pem-determinism-", dir=scratch))
    try:
        runs = []
        for i in (1, 2):
            out = base / f"run{i}"
            env = dict(os.environ, PYTHONHASHSEED="random")
            subprocess.run([sys.executable, str(Path(__file__).resolve().parent / "generate.py"), "--out", str(out)],
                           check=True, env=env, stdout=subprocess.DEVNULL)
            runs.append(tree_hashes(out))
        check(runs[0] == runs[1], "AC-1 two generator runs give byte-identical files (equal sha256)",
              f"{len(runs[0])} files")
        committed = {rel: sha(TESTDATA / rel) for rel in runs[0] if (TESTDATA / rel).exists()}
        check(set(committed) == set(runs[0]) and committed == runs[0],
              "AC-1 the files in modern/testdata equal a fresh generator run")
    finally:
        shutil.rmtree(base, ignore_errors=True)
    # the manifest agrees with the files
    ok = True
    with (TESTDATA / "manifest.csv").open(encoding="utf-8", newline="") as f:
        rows = list(csv.DictReader(f))
    for r in rows:
        p = TESTDATA / r["path"]
        ok = ok and p.exists() and sha(p) == r["sha256"] and p.stat().st_size == int(r["bytes"])
    check(ok and len(rows) >= 12, "AC-1 manifest.csv lists the sha256 and size of every generated file", f"{len(rows)} files")
    readme = (TESTDATA / "README.md").read_text(encoding="utf-8")
    check(str(SEED) in readme and "2026-01-15" in readme,
          "AC-1 README records the seed 20260115 and the frozen business date 2026-01-15")


# ---- readers ---------------------------------------------------------------------------------------------------------

def load(rel: str, layout: str):
    lay = Layout(layout)
    recs = read_records(TESTDATA / rel)
    return lay, recs, [lay.parse(r) for r in recs]


def ac2():
    lay, recs, rows = load("fixed-width/appinput-baseline.dat", "APPINPUT")
    check(lay.length == 200 and all(len(r) == 200 for r in recs), "AC-2 every APPINPUT record is 200 bytes",
          f"{len(recs)} records")
    seen, kinds = set(), {}
    for n, r in enumerate(rows, 1):
        codes = set()
        npi = r["PRV-NPI"]
        if not (len(npi) == 10 and npi.isdigit()):
            codes.add("E101")
        elif npi in seen:
            codes.add("E103")
        if r["PRV-TYPE-CODE"] not in ("IN", "GR", "FA"):
            codes.add("E102")
        if not r["PRV-TAXONOMY"]:
            codes.add("E104")
        if not codes:
            seen.add(npi)
            kinds.setdefault("valid " + r["PRV-TYPE-CODE"], n)
        for c in codes:
            kinds.setdefault(c, n)
    for want in ("valid IN", "valid GR", "valid FA", "E101", "E102", "E104", "E103"):
        check(want in kinds, f"AC-2 the baseline holds {want}", f"record {kinds.get(want)}")
    # the NPIs of the valid applications are not seeded (a seeded NPI would be E103 against the stored provider)
    seed_npis = {r["PRV-NPI"] for r in load("fixed-width/provider-master-seed.dat", "PRVRECRD")[2]}
    check(not (seen & seed_npis), "AC-2 the valid applications are new NPIs, none of the 16 seeded providers",
          f"{len(seen)} valid NPIs")


def ac3():
    lay, recs, rows = load("fixed-width/provider-master-seed.dat", "PRVRECRD")
    check(lay.length == 130 and all(len(r) == 130 for r in recs), "AC-3 every PRVRECRD record is 130 bytes",
          f"{len(recs)} records")
    have = lambda col: {r[col] for r in rows}
    check(have("PRV-ENROLL-STATUS") >= set("PATDS"), "AC-3 statuses P A T D S", "".join(sorted(have("PRV-ENROLL-STATUS"))))
    check(have("PRV-SCREEN-FLAG") >= set("UPF"), "AC-3 screen flags U P F", "".join(sorted(have("PRV-SCREEN-FLAG"))))
    check(have("PRV-RISK-TIER") >= set("LMH"), "AC-3 risk tiers L M H", "".join(sorted(have("PRV-RISK-TIER"))))
    prefixes = {r["PRV-TAXONOMY"][:3] for r in rows}
    check({"332", "251", "341", "291", "225"} <= prefixes and bool(prefixes - {"332", "251", "341", "291", "225"}),
          "AC-3 taxonomy prefixes 332 251 341 291 225 and another", " ".join(sorted(prefixes)))
    dues = [r["PRV-REVAL-DUE-DATE"] for r in rows if r["PRV-REVAL-DUE-DATE"] != NO_DATE]
    check(any(d < BUSINESS_DATE for d in dues) and any(d == BUSINESS_DATE for d in dues)
          and any(d > BUSINESS_DATE for d in dues), "AC-3 due dates before, equal to and after 2026-01-15",
          f"{sum(d < BUSINESS_DATE for d in dues)} before, {sum(d == BUSINESS_DATE for d in dues)} equal, "
          f"{sum(d > BUSINESS_DATE for d in dues)} after, {len(rows) - len(dues)} none")
    dates_ok = all(real_date(r[c]) and 1900 <= int(r[c][:4]) <= 9999 for r in rows
                   for c in ("PRV-EFFECTIVE-DATE", "PRV-REVAL-DUE-DATE"))
    check(dates_ok, "AC-3 every date is 8 digits CCYYMMDD with a 4-digit year and is a real date")
    tier_rule = {"332": "H", "251": "H", "341": "M", "291": "M", "225": "M"}
    check(all(r["PRV-RISK-TIER"] in ("", tier_rule.get(r["PRV-TAXONOMY"][:3], "L")) for r in rows),
          "AC-3 tiers follow the legacy rule (332 and 251 High, 341 291 225 Moderate, else Limited)")
    check(len({r["PRV-NPI"] for r in rows}) == len(rows), "AC-3 NPIs are unique")
    # the same story as the SQL seed: same providers, same order, same values
    sql = (TESTDATA / "seed/provider_db/01-providers.sql").read_text(encoding="utf-8")
    row_re = re.compile(r"^\s+\('[0-9a-f-]{36}', '(\d{10})', '(\d{9})', '(IN|GR|FA)', '([0-9A-Z]{10})', (NULL|'[LMH]'), "
                        r"'([PATDS])', '([UPF])', DATE '([\d-]{10})', (?:NULL|DATE '[\d-]{10}'), (NULL|DATE '[\d-]{10}'), "
                        r"([^)]*)\)", re.M)
    sql_rows = row_re.findall(sql)
    mismatches = []
    for n, (s, r) in enumerate(zip(sql_rows, rows), 1):
        npi, mid, typ, tax, tier, status, flag, eff, due, rest = s
        vals = [None if v.strip() == "NULL" else v.strip().strip("'") for v in rest.split(", ")]
        got = [r["PRV-NPI"], r["PRV-MEDICAID-ID"], r["PRV-TYPE-CODE"], r["PRV-TAXONOMY"], r["PRV-RISK-TIER"],
               r["PRV-ENROLL-STATUS"], r["PRV-SCREEN-FLAG"], r["PRV-EFFECTIVE-DATE"], r["PRV-REVAL-DUE-DATE"]]
        want = [npi, mid, typ, tax, "" if tier == "NULL" else tier.strip("'"), status, flag, eff.replace("-", ""),
                NO_DATE if due == "NULL" else due[6:-1].replace("-", "")]
        if typ == "IN":
            got += [r["PRV-LAST-NAME"], r["PRV-FIRST-NAME"], r["PRV-LICENSE-NO"], r["PRV-LICENSE-STATE"]]
            want += vals[:4]
        else:
            got += [r["PRV-LEGAL-NAME"], r["PRV-DBA-NAME"], r["PRV-OWNER-NPI"]]
            want += vals[4:7]
        if got != want:
            mismatches.append(n)
    check(len(sql_rows) == len(rows) == 16 and not mismatches,
          "AC-3 the 16 records equal the 16 rows of seed/provider_db/01-providers.sql (NPI, Medicaid ID, type, taxonomy, "
          "tier, status, flag, dates, names, licence, owner)", f"{len(sql_rows)} SQL rows, mismatching records {mismatches}")
    names = {(r["PRV-LAST-NAME"], r["PRV-FIRST-NAME"]) for r in rows if r["PRV-TYPE-CODE"] == "IN"}
    check(("ALPHA", "TESTPROV") in names and any(r["PRV-NPI"] == "1005940865" for r in rows),
          "AC-3 the reused providers are there (TESTPROV ALPHA 1005940865)")
    # the edge file
    lay2, recs2, rows2 = load("fixed-width/provider-master-edge.dat", "PRVRECRD")
    check(all(len(r) == 130 for r in recs2) and len(recs2) == 5, "AC-3 provider-master-edge.dat: 5 records of 130 bytes")
    check(not ({r["PRV-NPI"] for r in rows2} & {r["PRV-NPI"] for r in rows}),
          "AC-3 the edge providers are not seeded providers (the SQL seed and MAM-51 AC-1 stay as they are)")


def ac4():
    lay, recs, rows = load("fixed-width/sancin-baseline.dat", "SANCIN")
    check(lay.length == 80 and all(len(r) == 80 for r in recs), "AC-4 every SANCIN record is 80 bytes",
          f"{len(recs)} records")
    seed_npis = {r["PRV-NPI"] for r in load("fixed-width/provider-master-seed.dat", "PRVRECRD")[2]}
    actions = {r["SAN-ACTION"] for r in rows}
    check({"T", "S", "R"} <= actions, "AC-4 actions T, S and R", "".join(sorted(actions)))
    check(any(r["SAN-NPI"] not in seed_npis for r in rows), "AC-4 a record for an unknown NPI")
    check(any(r["SAN-ACTION"] not in ("T", "S", "R") for r in rows), "AC-4 a record with an invalid action code",
          "".join(sorted(a for a in actions if a not in "TSR")))
    check(all(real_date(r["SAN-EFF-DATE"]) for r in rows), "AC-4 every effective date is a real CCYYMMDD date")


def ac5():
    lay, recs, rows = load("scenarios/appinput-scenarios.dat", "APPINPUT")
    feed = []
    for line in (TEST_DATA_FEEDS / "providers.txt").read_text(encoding="utf-8").splitlines():
        if line.strip() and not line.startswith("#"):
            feed.append(line.split("|"))
    check(len(recs) == 8 and all(len(r) == 200 for r in recs), "AC-5 8 APPINPUT records of 200 bytes")
    want_npis = ["1003000111", "1003000222", "1003000333", "1003000444", "1003000555", "1003000666", "1003000777",
                 "1003000888"]
    check([r["PRV-NPI"] for r in rows] == want_npis == [f[0] for f in feed], "AC-5 the eight NPIs of providers.txt, verbatim")
    mapping = {"IND": "IN", "PT": "IN", "GRP": "GR", "DME": "GR", "HOSP": "FA", "HOS": "FA", "AMB": "FA", "LAB": "FA",
               "HHA": "FA"}                                        # QF-2 option A
    check(all(r["PRV-TYPE-CODE"] == mapping[f[2]] for r, f in zip(rows, feed)),
          "AC-5 the feed type codes are mapped to IN, GR or FA (QF-2)",
          " ".join(f"{f[2]}->{r['PRV-TYPE-CODE']}" for r, f in zip(rows, feed)))
    check(all(re.fullmatch(r"9\d{8}", r["PRV-MEDICAID-ID"]) for r in rows) and
          len({r["PRV-MEDICAID-ID"] for r in rows}) == 8, "AC-5 synthetic, distinct Medicaid IDs added")
    check(all(r["PRV-TAXONOMY"] for r in rows) and rows[0]["PRV-TAXONOMY"] == "207Q00000X",
          "AC-5 taxonomies added (207Q00000X for 1003000111)")
    name_ok = True
    for r, f in zip(rows, feed):
        name = (r["PRV-LAST-NAME"] + " " + r["PRV-FIRST-NAME"]).strip() if r["PRV-TYPE-CODE"] == "IN" \
            else r["PRV-LEGAL-NAME"]
        name_ok = name_ok and name == f[1]
    check(name_ok, "AC-5 the feed names are carried verbatim (individuals split at the first space)")
    with (TESTDATA / "scenarios/expected-outcomes.csv").open(encoding="utf-8", newline="") as f:
        exp = {r["npi"]: r for r in csv.DictReader(f)}
    want = {"1003000444": ("FAIL", "E203"), "1003000555": ("FAIL", "E202"), "1003000666": ("NONE", "")}
    want.update({n: ("PASS", "") for n in ("1003000111", "1003000222", "1003000333", "1003000777", "1003000888")})
    check(set(exp) == set(want_npis) and all((exp[n]["expected_outcome"], exp[n]["expected_code"]) == w
                                             for n, w in want.items()),
          "AC-5 expected outcomes: 444 FAIL E203, 555 FAIL E202, 111 222 333 777 888 PASS, 666 NONE (QF-14)")


def extra():
    lay, recs, rows = load("negative/appinput-bad-length.dat", "APPINPUT")
    bad = [n for n, r in enumerate(recs, 1) if len(r) != 200]
    check(bad == [3] and len(recs) == 4, "negative/appinput-bad-length.dat: only record 3 is not 200 bytes",
          f"lengths {[len(r) for r in recs]}")
    lay, recs, rows = load("negative/sancin-malformed.dat", "SANCIN")
    check([len(r) for r in recs] == [80, 80, 79, 80] and not real_date(rows[1]["SAN-EFF-DATE"]),
          "negative/sancin-malformed.dat: record 2 has an impossible date, record 3 is 79 bytes",
          f"lengths {[len(r) for r in recs]}, date {rows[1]['SAN-EFF-DATE']}")


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--skip-determinism", action="store_true")
    ap.add_argument("--scratch", type=Path, default=None, help="folder for the two scratch runs of AC-1")
    args = ap.parse_args()
    if not args.skip_determinism:
        ac1(args.scratch)
    ac2()
    ac3()
    ac4()
    ac5()
    extra()
    print(f"{'FAIL' if FAILURES else 'OK'}: {len(FAILURES)} failed check(s)")
    sys.exit(1 if FAILURES else 0)


if __name__ == "__main__":
    main()
