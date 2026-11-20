#!/usr/bin/env python3
"""MAM-44: the deterministic generator of the synthetic legacy-format datasets under modern/testdata/.

Run:   python modern/tools/data/generate.py            (writes modern/testdata/)
       python modern/tools/data/generate.py --out DIR   (writes the same tree under DIR; used by the determinism check)

Seed 20260115, which is also the frozen business date 2026-01-15, recorded in modern/testdata/README.md. The
generator draws from ONE random stream, random.Random(20260115): first exactly the draws of testdata/generate_seed.py
(16 NPIs, then 16 Medicaid IDs), so the 16 seeded providers keep their NPIs and Medicaid IDs and the fixed-width
file tells the same story as the SQL seed; then, in a fixed order, the new values (NPIs in the 10061xxxxx block,
which is outside the seed block 10059xxxxx and the reserved journey ranges 1003000xxx and 1990000xxx).

Layout positions are never typed here: pemdata.Layout reads the JSON layouts exported from the knowledge graph.

Files written (relative to the output root):
  fixed-width/appinput-baseline.dat       APPINPUT 200   enrolment intake, every validation branch (AC-2)
  fixed-width/provider-master-seed.dat    PRVRECRD 130   the 16 seeded providers (AC-3), the same as seed/provider_db
  fixed-width/provider-master-edge.dat    PRVRECRD 130   extra states some rules need that the seed must not hold
  fixed-width/sancin-baseline.dat         SANCIN    80   T, S, R, unknown NPI, invalid action (AC-4)
  scenarios/appinput-scenarios.dat        APPINPUT 200   the 8 sample applications of aws-transform/test-data (AC-5)
  scenarios/expected-outcomes.csv, provider-type-mapping.csv, business-dates.csv
  negative/appinput-bad-length.dat, negative/sancin-malformed.dat   deliberately unreadable records (D-024)
  coverage.csv, coverage-gaps.csv         rule coverage (AC-6), written by coverage_report.py
  manifest.csv                            path, layout, records, bytes and sha256 of every file above

Synthetic data only. The NPIs and names of aws-transform/test-data appear verbatim in scenarios/ and nowhere else.
"""
import sys

sys.dont_write_bytecode = True

import argparse
import csv
import hashlib
import io
import random
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from pemdata import (BUSINESS_DATE, NO_DATE, SEED, TEST_DATA_FEEDS, TESTDATA, Layout, npi_check_digit, write_records,
                     write_text)

sys.path.insert(0, str(TESTDATA))
import generate_seed as gs  # the existing provider seed: its provider table and its draw order are reused, not changed

import coverage_report

# QF-2 (default applied, option A): the feed type codes of the sample feed mapped to the legacy codes (D-008)
FEED_TO_LEGACY = {"IND": "IN", "PT": "IN", "GRP": "GR", "DME": "GR", "HOSP": "FA", "HOS": "FA", "AMB": "FA",
                  "LAB": "FA", "HHA": "FA"}
# the feed has no taxonomy: one synthetic code per feed type (207Q00000X for 1003000111, as the spec says)
FEED_TAXONOMY = {"IND": "207Q00000X", "PT": "225100000X", "GRP": "193200000X", "DME": "332B00000X",
                 "HOSP": "282N00000X", "HOS": "251G00000X", "AMB": "341600000X", "LAB": "291U00000X",
                 "HHA": "251E00000X"}
# the expected outcome of each sample application (spec MAM-44 AC-5, QF-14 default applied)
EXPECTED = {
    "1003000111": ("PASS", "", "clean on all four sources"),
    "1003000222": ("PASS", "", "clean on all four sources"),
    "1003000333": ("PASS", "", "clean on all four sources"),
    "1003000444": ("FAIL", "E203", "owner 1990000111 is on the LEIE list (test-data/leie.txt)"),
    "1003000555": ("FAIL", "E202", "licence PT99900 is EXPIRED on the licence board (test-data/license_board.txt)"),
    "1003000666": ("NONE", "", "no expected outcome yet: QF-14 default A; the screening stories decide how a licence "
                              "that is not found is classified, then this row is updated"),
    "1003000777": ("PASS", "", "clean on all four sources"),
    "1003000888": ("PASS", "", "clean on all four sources"),
}
BUSINESS_DATES = [
    ("frozen", "2026-01-15", "the frozen business date of every fixture"),
    ("sweep", "2026-03-31", "the month-end revalidation sweep of journey J6: seeded due dates up to this date are due"),
    ("leap-day", "2028-02-29", "legacy PRVRSK01 copies month and day into the year 2033 and makes the impossible "
                               "date 20330229 (R-PRVRSK01-ADD-02, Q-017)"),
    ("century-pivot", "2065-01-15", "legacy 2-digit due year 70 is read as 1970 by the pivot at 70 "
                                    "(R-PRVRSK01-cbl-00044, D-004)"),
]

PRVREC = Layout("PRVRECRD")
APPIN = Layout("APPINPUT")
SANIN = Layout("SANCIN")


def d8(iso):
    """'2021-03-01' -> '20210301'; None -> the no-date value."""
    return NO_DATE if iso is None else iso.replace("-", "")


def ind(last, first, lic, state="TX"):
    return {"PRV-LAST-NAME": last, "PRV-FIRST-NAME": first, "PRV-LICENSE-NO": lic, "PRV-LICENSE-STATE": state}


def org(legal, dba, owner):
    return {"PRV-LEGAL-NAME": legal, "PRV-DBA-NAME": dba, "PRV-OWNER-NPI": owner}


def master_values(npi, mid, typ, tax, tier, status, flag, eff, due, variant):
    v = {"PRV-NPI": npi, "PRV-MEDICAID-ID": mid, "PRV-TYPE-CODE": typ, "PRV-TAXONOMY": tax, "PRV-RISK-TIER": tier,
         "PRV-ENROLL-STATUS": status, "PRV-SCREEN-FLAG": flag, "PRV-EFFECTIVE-DATE": eff, "PRV-REVAL-DUE-DATE": due}
    v.update(variant)
    return v


def application(npi, mid, typ, tax, variant, view=None):
    """An APPINPUT record: PRVRECRD in bytes 1-130 with no tier, status or flag yet (PRVENR01 sets them), the
    application date as effective date and no due date; bytes 131-200 reserved (D-025)."""
    return APPIN.build(master_values(npi, mid, typ, tax, "", "", "", BUSINESS_DATE, NO_DATE, variant), view=view)


def sanction(npi, action, reason, eff=BUSINESS_DATE):
    return SANIN.build({"SAN-NPI": npi, "SAN-ACTION": action, "SAN-REASON": reason, "SAN-EFF-DATE": eff})


class Stream:
    """The one random stream, plus the NPIs and Medicaid IDs already handed out (never reused)."""

    def __init__(self, seed_npis, seed_mids):
        self.rng = random.Random(SEED)
        self.npis = set(seed_npis)
        self.mids = set(seed_mids)

    def npi(self):
        while True:
            first9 = "10061" + f"{self.rng.randrange(10000):04d}"
            npi = first9 + npi_check_digit(first9)
            if npi not in self.npis:
                self.npis.add(npi)
                return npi

    def mid(self):
        while True:
            mid = "9" + f"{self.rng.randrange(10 ** 8):08d}"
            if mid not in self.mids:
                self.mids.add(mid)
                return mid


def generate(root: Path) -> None:
    # 1. the draws of generate_seed.py, exactly: 16 NPIs then 16 Medicaid IDs from random.Random(20260115)
    rng = random.Random(SEED)
    keys = [p[0] for p in gs.PROVIDERS]
    npi = dict(zip(keys, gs.npis(rng, len(keys))))
    mid = {k: "9" + f"{rng.randrange(10 ** 8):08d}" for k in keys}
    s = Stream(npi.values(), mid.values())
    s.rng = rng                                   # continue the same stream for everything new

    # 2. provider master seed: the 16 seeded providers (no tier = blank, no due date = NO_DATE, end date has no field)
    seed_rows = []
    for key, typ, tax, tier, status, flag, eff, end, due, names, owner in gs.PROVIDERS:
        if typ == "IN":
            first, last, lic = names
            variant = ind(last, first, lic)
        else:
            variant = org(names[0], names[1], npi[owner])
        seed_rows.append(PRVREC.build(master_values(npi[key], mid[key], typ, tax, tier or "", status, flag, d8(eff),
                                                    d8(due), variant)))
    write_records(root / "fixed-width/provider-master-seed.dat", seed_rows)

    # 3. enrolment intake baseline: every validation branch of PRVENR01 (E101-E104, D-016)
    a_npi, g_npi, f_npi, b_npi, c_npi = (s.npi() for _ in range(5))
    m = [s.mid() for _ in range(8)]
    IN_TAX, GR_TAX, FA_TAX = "207Q00000X", "193200000X", "282N00000X"
    appinput = [
        application(a_npi, m[0], "IN", IN_TAX, ind("INTAKE ONE", "TESTPROV", "SD20001")),                 # 1 valid IN
        application(g_npi, m[1], "GR", GR_TAX, org("SAMPLE INTAKE GROUP 01", "SAMPLE INTAKE CLINIC 01",
                                                   npi["alpha"])),                                      # 2 valid GR
        application(f_npi, m[2], "FA", FA_TAX, org("SAMPLE INTAKE HOSPITAL 01", "SAMPLE INTAKE CARE 01",
                                                   npi["charlie"])),                                    # 3 valid FA
        application("", m[3], "IN", IN_TAX, ind("MISSING NPI", "TESTPROV", "SD20004")),                  # 4 E101
        application(b_npi, m[4], "ZZ", IN_TAX, ind("BAD TYPE", "TESTPROV", "SD20005"), view="IN"),       # 5 E102
        application(c_npi, m[5], "IN", "", ind("NO TAXONOMY", "TESTPROV", "SD20006")),                    # 6 E104
        application(a_npi, m[6], "IN", IN_TAX, ind("INTAKE ONE DUP", "TESTPROV", "SD20007")),            # 7 E103
        application(g_npi, m[7], "ZZ", GR_TAX, org("SAMPLE INTAKE GROUP 01 DUP", "SAMPLE INTAKE CLINIC 01",
                                                   npi["alpha"]), view="GR"),                           # 8 E102+E103
    ]
    write_records(root / "fixed-width/appinput-baseline.dat", appinput)

    # 4. edge providers: states some rules need that the 16 seeded providers (and the SQL seed) must not hold
    xs = [s.npi() for _ in range(5)]
    xm = [s.mid() for _ in range(5)]
    edge = [
        # active, enrolment effective in the future of the business date (PRVTMS01 future-year alert)
        PRVREC.build(master_values(xs[0], xm[0], "IN", "207Q00000X", "L", "A", "P", "20270301", "20320301",
                                   ind("FUTURE START", "TESTPROV", "SD30001"))),
        # active, enrolled before 2000 (PRVTMS01 2-digit-year compare flags it as a future enrolment)
        PRVREC.build(master_values(xs[1], xm[1], "IN", "207Q00000X", "L", "A", "P", "19980601", "20300601",
                                   ind("LEGACY START", "TESTPROV", "SD30002"))),
        # active with no due date (R-PRVREV01-ADD-03, Q-049)
        PRVREC.build(master_values(xs[2], xm[2], "IN", "207Q00000X", "L", "A", "P", "20240101", NO_DATE,
                                   ind("NO DUE DATE", "TESTPROV", "SD30003"))),
        # active that failed screening (R-PRVDIR01-ADD-01: still listed by the legacy directory extract)
        PRVREC.build(master_values(xs[3], xm[3], "IN", "207Q00000X", "L", "A", "F", "20250101", "20300101",
                                   ind("FAILED SCREEN", "TESTPROV", "SD30004"))),
        # pending with a blank taxonomy (R-PRVRSK01-ADD-03: silently becomes LIMITED)
        PRVREC.build(master_values(xs[4], xm[4], "FA", "", "", "P", "U", "20260110", NO_DATE,
                                   org("SAMPLE BLANK TAXONOMY 01", "SAMPLE BLANK TAXONOMY DBA 01", npi["alpha"]))),
    ]
    write_records(root / "fixed-width/provider-master-edge.dat", edge)

    # 5. sanction input baseline: T, S, R, an unknown NPI, an invalid action (D-026), a reinstate of a pending one (D-027)
    unknown_npi = s.npi()
    sancin = [
        sanction(npi["dme"], "T", "SAMPLE TERMINATION: PROGRAM VIOLATION"),
        sanction(npi["hospital"], "S", "SAMPLE SUSPENSION: PENDING REVIEW"),
        sanction(npi["therapy"], "R", "SAMPLE REINSTATEMENT: REVIEW DONE"),
        sanction(unknown_npi, "T", "SAMPLE: NPI NOT IN THE MASTER"),
        sanction(npi["clinic"], "X", "SAMPLE: INVALID ACTION CODE"),
        sanction(npi["hha"], "R", "SAMPLE: REINSTATE OF A PENDING PROVIDER"),
    ]
    write_records(root / "fixed-width/sancin-baseline.dat", sancin)

    # 6. scenarios: the 8 sample applications as APPINPUT records (QF-2 type mapping, synthetic ids and taxonomies)
    sm = [s.mid() for _ in range(8)]
    feed = []
    for line in (TEST_DATA_FEEDS / "providers.txt").read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith("#"):
            feed.append(line.split("|"))
    assert len(feed) == 8, "providers.txt must hold 8 applications"
    scenario_rows, outcome_rows = [], []
    for (f_npi_v, name, ftype, lic, state, owner), mid_v in zip(feed, sm):
        legacy = FEED_TO_LEGACY[ftype]
        if legacy == "IN":
            last, _, first = name.partition(" ")      # LAST + ' ' + FIRST reads back as the feed name, verbatim
            variant = ind(last, first, lic, state)
        else:
            variant = org(name, "", owner)
        scenario_rows.append(application(f_npi_v, mid_v, legacy, FEED_TAXONOMY[ftype], variant))
        outcome, code, note = EXPECTED[f_npi_v]
        outcome_rows.append([f_npi_v, ftype, legacy, outcome, code, note])
    write_records(root / "scenarios/appinput-scenarios.dat", scenario_rows)
    write_text(root / "scenarios/expected-outcomes.csv",
               csv_text(["npi", "feed_type", "legacy_type", "expected_outcome", "expected_code", "note"], outcome_rows))
    write_text(root / "scenarios/provider-type-mapping.csv",
               csv_text(["feed_code", "legacy_type", "basis"],
                        [[k, v, "QF-2 default A (business to confirm), D-008"] for k, v in FEED_TO_LEGACY.items()]))
    write_text(root / "scenarios/business-dates.csv", csv_text(["label", "business_date", "purpose"], BUSINESS_DATES))

    # 7. negative files: a record the readers must refuse (wrong length, impossible date), never under fixed-width/
    n1, n2 = s.npi(), s.npi()
    nm = [s.mid() for _ in range(4)]
    ok1 = application(n1, nm[0], "IN", IN_TAX, ind("NEGATIVE ONE", "TESTPROV", "SD40001"))
    bad = application(n2, nm[1], "IN", IN_TAX, ind("SHORT RECORD", "TESTPROV", "SD40002"))[:-1]      # 199 bytes
    after = application(s.npi(), nm[2], "IN", IN_TAX, ind("NEVER READ", "TESTPROV", "SD40003"))
    write_records(root / "negative/appinput-bad-length.dat",
                  [ok1, application("", nm[3], "IN", IN_TAX, ind("MISSING NPI", "TESTPROV", "SD40004")), bad, after])
    write_records(root / "negative/sancin-malformed.dat", [
        sanction(npi["dme"], "T", "SAMPLE TERMINATION: PROGRAM VIOLATION"),
        sanction(npi["hospital"], "S", "SAMPLE: IMPOSSIBLE EFFECTIVE DATE", eff="20260230"),
        sanction(npi["therapy"], "R", "SAMPLE: SHORT RECORD")[:-1],                                      # 79 bytes
        sanction(npi["dme"], "T", "SAMPLE: NEVER READ"),
    ])

    # 8. coverage (AC-6), then the manifest of everything written
    coverage_report.write(root)
    write_manifest(root)


def csv_text(header, rows):
    buf = io.StringIO()
    w = csv.writer(buf, lineterminator="\n")
    w.writerow(header)
    w.writerows(rows)
    return buf.getvalue()


LAYOUT_OF = {
    "fixed-width/appinput-baseline.dat": "APPINPUT", "fixed-width/provider-master-seed.dat": "PRVRECRD",
    "fixed-width/provider-master-edge.dat": "PRVRECRD", "fixed-width/sancin-baseline.dat": "SANCIN",
    "scenarios/appinput-scenarios.dat": "APPINPUT", "negative/appinput-bad-length.dat": "APPINPUT",
    "negative/sancin-malformed.dat": "SANCIN",
}


def write_manifest(root: Path) -> None:
    paths = sorted(p.relative_to(root).as_posix() for p in root.rglob("*")
                   if p.is_file() and p.name != "manifest.csv" and p.suffix in (".dat", ".csv"))
    rows = []
    for rel in paths:
        data = (root / rel).read_bytes()
        records = data.count(b"\n")
        layout = LAYOUT_OF.get(rel, "CSV")
        if layout == "CSV":
            records -= 1                               # the header line
        rows.append([rel, layout, records, len(data), hashlib.sha256(data).hexdigest()])
    write_text(root / "manifest.csv", csv_text(["path", "layout", "records", "bytes", "sha256"], rows))


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--out", type=Path, default=TESTDATA, help="output root (default modern/testdata)")
    args = ap.parse_args()
    generate(args.out)
    print(f"generated under {args.out} (seed {SEED}, business date {BUSINESS_DATE})")


if __name__ == "__main__":
    main()
