#!/usr/bin/env python3
"""MAM-88: the large synthetic data set of the performance smoke, written by `generate.py perf` (this module holds the work).

Run:   python modern/tools/data/generate.py perf --out DIR [--count 10000]

--out is REQUIRED and is never inside modern/testdata: the 10,000-record files are not stored in the repository, they are written
at run time (tools/perf/load_perf_dataset.py writes them into a git-ignored folder and loads them). The folder receives exactly:
  provider-master-perf.dat   PRVRECRD, 130-byte records (layout read from the graph export), one LF after each, NPI order
  provider-perf.sql          the same providers as rows of provider_db.provider: the 18-column list of
                             testdata/seed/provider_db/01-providers.sql, one row per line, INSERT ... ON CONFLICT DO NOTHING,
                             1,000 rows per statement, no TRUNCATE / DELETE: additive, loaded on top of the 16 seeded providers
  revalidation-perf.sql      the same providers as rows of revalidation_db.tracked_provider (npi, enroll_status, reval_due_date),
                             the read model the revalidation sweep reads (it is fed by events, which a SQL load does not send)

Seed 20260115 (testdata/README.md), one random stream: random.Random(20260115), consumed in a fixed order (NPIs, Medicaid IDs,
types, statuses, then the providers in NPI order), never iterating a set, so the output does not depend on PYTHONHASHSEED.

Synthetic data only (synthetic-data skill section 1): NPIs 1007xxxxxx with a valid check digit (Luhn, prefix 80840), a block outside
the seed block 10059xxxxx, the small data set 10061xxxxx, the reserved journey ranges, the aws-transform/test-data NPIs and every
NPI a WireMock stub answers with a hit; individuals TESTPROV PERFnnnnn, organisations SAMPLE PERF ... nnnnn; licences SD10001-SD10007
(state TX), the ones the licence-board stub answers ACTIVE, so that a perf screening takes the clean path.
"""
import sys

sys.dont_write_bytecode = True

import argparse
import json
import random
import re
import uuid
from datetime import date, timedelta
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from pemdata import (MODERN, NO_DATE, SEED, TEST_DATA_FEEDS, TESTDATA, Layout, npi_check_digit, npi_reserved, npi_valid,
                     read_records, write_records, write_text)

sys.path.insert(0, str(TESTDATA))
import generate_seed as gs  # only its fixed UUID namespace is reused (public id = uuid5(namespace, npi), as in the seed)

DEFAULT_COUNT = 10_000
MAX_COUNT = 50_000                       # 1007 + 5 digits = 100,000 NPI slots: at most half full keeps the draw cheap
NPI_PREFIX = "1007"                      # the perf block; first 9 digits = prefix + 5 digits, then the check digit
NPI_BLOCK = ("1007000000", "1007999999")  # every perf NPI lies in this range (the loader counts the perf rows by it)
DAT, PROVIDER_SQL, REVAL_SQL = "provider-master-perf.dat", "provider-perf.sql", "revalidation-perf.sql"
BATCH_ROWS = 1000                        # rows per INSERT statement

FROZEN = date(2026, 1, 15)               # the frozen business date (README), also the seed
FIRST_DAY = date(2000, 1, 1)             # no enrolment before 2000 (PRVTMS01 2-digit-year anomaly, kept to the edge file)
STUB_DIR = MODERN / "infra/wiremock/mappings"
LICENCES = [f"SD1000{n}" for n in range(1, 8)]   # SD10001-SD10007, state TX: answered ACTIVE by license-board/clean-seeded.json

# shares of the set (largest-remainder quotas, so each minimum of the story holds exactly, not by luck)
TYPE_SHARE = [("IN", 0.60), ("GR", 0.15), ("FA", 0.25)]
STATUS_SHARE = [("A", 0.60), ("T", 0.12), ("S", 0.08), ("P", 0.12), ("D", 0.08)]
# due-date bucket of the providers that have a due date (A, T, S): overdue = on or before 2026-01-15, so due on any later sweep date
DUE_SHARE = {"A": [("overdue", 0.15), ("year", 0.15), ("future", 0.70)],
             "T": [("overdue", 0.70), ("future", 0.30)],
             "S": [("overdue", 0.30), ("future", 0.70)]}
DUE_RANGE = {"overdue": (date(2023, 1, 1), FROZEN), "year": (date(2026, 1, 16), date(2026, 12, 31)),
             "future": (date(2027, 1, 1), date(2031, 12, 31))}
FLAG_OF_STATUS = {"P": "U", "D": "F", "A": "P", "T": "P", "S": "P"}       # the seed convention
TIER_OF_PREFIX = {"332": "H", "251": "H", "341": "M", "291": "M", "225": "M"}   # legacy PRVRSK01; any other prefix is L
# taxonomies by type, handed out round-robin so every legacy risk prefix (332 251 341 291 225) and others occur
TAXONOMY = {"IN": ["207Q00000X", "207R00000X", "208D00000X", "363L00000X", "225100000X", "225X00000X"],
            "GR": ["193200000X", "261QM0801X", "225100000X", "332B00000X", "251E00000X", "341600000X"],
            "FA": ["282N00000X", "251G00000X", "261QM0801X", "251E00000X", "332B00000X", "341600000X", "291U00000X",
                   "282NC2000X"]}
KIND = {"GR": "GROUP", "FA": "FACILITY"}

COLUMNS = ("public_id", "npi", "medicaid_id", "provider_type", "taxonomy", "risk_tier", "enroll_status", "screen_flag",
           "effective_date", "end_date", "reval_due_date", "last_name", "first_name", "license_no", "license_state",
           "legal_name", "dba_name", "owner_npi")                # = testdata/seed/provider_db/01-providers.sql, same order
TRACKED_COLUMNS = ("npi", "enroll_status", "reval_due_date")


# ---- what the perf set must never repeat -----------------------------------------------------------------------------------

def taken_ids() -> tuple:
    """(NPIs, Medicaid IDs) the perf set must not reuse: every NPI and Medicaid ID of the committed small data set (fixed-width,
    scenarios, negative and the seed SQL, read as text or by layout, so an over-collection is harmless), the NPIs of
    aws-transform/test-data and the NPIs the WireMock stubs answer with a hit (hit-<npi>.json)."""
    npis, mids = set(), set()
    lay = Layout("PRVRECRD")
    for sub in ("fixed-width", "scenarios", "negative"):
        for path in sorted((TESTDATA / sub).glob("*.dat")):
            for rec in read_records(path):
                if len(rec) < 30:
                    continue
                f = lay.parse(rec, all_views=True)
                for name in ("PRV-NPI", "PRV-OWNER-NPI"):
                    if re.fullmatch(r"\d{10}", f.get(name, "")):
                        npis.add(f[name])
                if re.fullmatch(r"9\d{8}", f.get("PRV-MEDICAID-ID", "")):
                    mids.add(f["PRV-MEDICAID-ID"])
    for path in sorted((TESTDATA / "seed").rglob("*.sql")):
        text = path.read_text(encoding="utf-8")
        npis |= set(re.findall(r"'(\d{10})'", text))
        mids |= set(re.findall(r"'(9\d{8})'", text))
    for path in sorted(TEST_DATA_FEEDS.glob("*.txt")):
        npis |= set(re.findall(r"(?<!\d)\d{10}(?!\d)", path.read_text(encoding="utf-8")))
    for path in sorted(STUB_DIR.rglob("hit-*.json")):
        m = re.fullmatch(r"hit-(\d{10})\.json", path.name)
        if m:
            npis.add(m.group(1))
    return npis, mids


def check_licence_stub() -> None:
    """The licences handed out are the ones the licence-board stub answers ACTIVE (read from its mapping, not assumed)."""
    q = json.loads((STUB_DIR / "license-board/clean-seeded.json").read_text(encoding="utf-8"))["request"]["queryParameters"]
    rule, state = re.compile(q["licenseNo"]["matches"]), q["state"]["equalTo"]
    if state != "TX" or not all(rule.fullmatch(lic) for lic in LICENCES):
        raise RuntimeError("the licence-board stub (clean-seeded.json) no longer answers SD10001-SD10007 / TX as ACTIVE: "
                           "the perf individuals would measure the 'licence not found' path; update LICENCES")


# ---- the draws --------------------------------------------------------------------------------------------------------------

def quotas(total: int, shares: list) -> list:
    """`total` keys in proportion to the shares, exactly (largest remainder, ties by list order)."""
    raw = [(k, total * f) for k, f in shares]
    base = {k: int(v) for k, v in raw}
    order = sorted(range(len(raw)), key=lambda i: (-(raw[i][1] - int(raw[i][1])), i))
    for i in order[:total - sum(base.values())]:
        base[raw[i][0]] += 1
    return [k for k, _ in shares for _ in range(base[k])]


def day(rng: random.Random, lo: date, hi: date) -> date:
    return lo + timedelta(days=rng.randrange((hi - lo).days + 1))


def draw_npis(rng: random.Random, count: int, taken: set) -> list:
    npis = set()
    while len(npis) < count:
        first9 = NPI_PREFIX + f"{rng.randrange(100000):05d}"
        npi = first9 + npi_check_digit(first9)
        if npi not in taken and npi not in npis:
            npis.add(npi)
    return sorted(npis)


def draw_medicaid_ids(rng: random.Random, count: int, taken: set) -> list:
    mids, seen = [], set()
    while len(mids) < count:
        mid = "9" + f"{rng.randrange(10 ** 8):08d}"
        if mid not in taken and mid not in seen:
            seen.add(mid)
            mids.append(mid)
    return mids


def build(count: int) -> list:
    """The providers, in ascending NPI order, as dicts (the single source of the .dat file and of both SQL files)."""
    rng = random.Random(SEED)
    taken_npis, taken_mids = taken_ids()
    npis = draw_npis(rng, count, taken_npis)
    mids = draw_medicaid_ids(rng, count, taken_mids)
    types = quotas(count, TYPE_SHARE)
    if count and "IN" not in types:                        # a group or facility needs an individual as its owner
        types[0] = "IN"
    rng.shuffle(types)
    statuses = quotas(count, STATUS_SHARE)
    rng.shuffle(statuses)
    buckets = {}                                           # the due-date bucket of each provider that has a due date
    for status, shares in DUE_SHARE.items():
        idx = [i for i, s in enumerate(statuses) if s == status]
        names = quotas(len(idx), shares)
        rng.shuffle(names)
        buckets.update(zip(idx, names))
    persons = [n for n, t in zip(npis, types) if t == "IN"]
    next_taxonomy = {t: 0 for t in TAXONOMY}
    next_licence = 0
    out = []
    for i, (npi, mid, typ, status) in enumerate(zip(npis, mids, types, statuses)):
        n = i + 1
        taxonomy = TAXONOMY[typ][next_taxonomy[typ] % len(TAXONOMY[typ])]
        next_taxonomy[typ] += 1
        tier = TIER_OF_PREFIX.get(taxonomy[:3], "L") if status in ("A", "T", "S") else ""
        end = None
        if status in ("A", "T", "S"):
            due = day(rng, *DUE_RANGE[buckets[i]])
            eff = day(rng, max(FIRST_DAY, due - timedelta(days=3652)), min(due - timedelta(days=1), FROZEN))
            if status == "T":
                end = day(rng, eff, FROZEN)
        else:
            due = None
            eff = day(rng, date(2025, 10, 1) if status == "P" else date(2025, 6, 1), FROZEN)
        p = {"npi": npi, "medicaid_id": mid, "provider_type": typ, "taxonomy": taxonomy, "risk_tier": tier,
             "enroll_status": status, "screen_flag": FLAG_OF_STATUS[status], "effective_date": eff, "end_date": end,
             "reval_due_date": due, "last_name": None, "first_name": None, "license_no": None, "license_state": None,
             "legal_name": None, "dba_name": None, "owner_npi": None}
        if typ == "IN":
            p.update(last_name=f"PERF{n:05d}", first_name="TESTPROV", license_no=LICENCES[next_licence % len(LICENCES)],
                     license_state="TX")
            next_licence += 1
        else:
            p.update(legal_name=f"SAMPLE PERF {KIND[typ]} {n:05d}", owner_npi=rng.choice(persons),
                     dba_name=f"SAMPLE PERF DBA {n:05d}" if rng.random() < 0.5 else None)
        p["public_id"] = str(uuid.uuid5(gs.NS, npi))
        out.append(p)
    return out


# ---- the three files --------------------------------------------------------------------------------------------------------

def d8(d) -> str:
    return NO_DATE if d is None else d.strftime("%Y%m%d")


def record(layout: Layout, p: dict) -> bytes:
    v = {"PRV-NPI": p["npi"], "PRV-MEDICAID-ID": p["medicaid_id"], "PRV-TYPE-CODE": p["provider_type"],
         "PRV-TAXONOMY": p["taxonomy"], "PRV-RISK-TIER": p["risk_tier"], "PRV-ENROLL-STATUS": p["enroll_status"],
         "PRV-SCREEN-FLAG": p["screen_flag"], "PRV-EFFECTIVE-DATE": d8(p["effective_date"]),
         "PRV-REVAL-DUE-DATE": d8(p["reval_due_date"])}
    if p["provider_type"] == "IN":
        v.update({"PRV-LAST-NAME": p["last_name"], "PRV-FIRST-NAME": p["first_name"], "PRV-LICENSE-NO": p["license_no"],
                  "PRV-LICENSE-STATE": p["license_state"]})
    else:
        v.update({"PRV-LEGAL-NAME": p["legal_name"], "PRV-DBA-NAME": p["dba_name"] or "", "PRV-OWNER-NPI": p["owner_npi"]})
    return layout.build(v)


def q(v) -> str:
    return "NULL" if v is None or v == "" else "'" + str(v).replace("'", "''") + "'"


def dt(v) -> str:
    return "NULL" if v is None else f"DATE '{v.isoformat()}'"


def provider_values(p: dict) -> str:
    cells = [q(p["public_id"]), q(p["npi"]), q(p["medicaid_id"]), q(p["provider_type"]), q(p["taxonomy"]), q(p["risk_tier"]),
             q(p["enroll_status"]), q(p["screen_flag"]), dt(p["effective_date"]), dt(p["end_date"]), dt(p["reval_due_date"]),
             q(p["last_name"]), q(p["first_name"]), q(p["license_no"]), q(p["license_state"]), q(p["legal_name"]),
             q(p["dba_name"]), q(p["owner_npi"])]
    return "(" + ", ".join(cells) + ")"


def tracked_values(p: dict) -> str:
    return "(" + ", ".join([q(p["npi"]), q(p["enroll_status"]), dt(p["reval_due_date"])]) + ")"


def header(table: str, what: str, count: int) -> str:
    return (f"-- MAM-88 performance data set, {table}: {what}. GENERATED by modern/tools/data/generate.py perf (seed {SEED}, "
            f"count {count}); do not edit by hand.\n"
            "-- Additive: loaded on top of the seed (the seed's TRUNCATE restores the 16 seeded providers and removes these rows) and\n"
            "-- re-runnable (ON CONFLICT DO NOTHING). Never stored in the repository: tools/perf/load_perf_dataset.py writes and loads it.\n"
            f"-- Synthetic data only: obviously fake names, NPIs {NPI_BLOCK[0][:4]}xxxxxx with a valid check digit, none of them in the\n"
            "-- seed, the small data set, the journey ranges or a stub hit.\n")


def insert_script(table: str, columns: tuple, rows: list, conflict: str) -> str:
    parts = []
    head = f"INSERT INTO {table} ({', '.join(columns)}) VALUES\n"
    for start in range(0, len(rows), BATCH_ROWS):
        batch = rows[start:start + BATCH_ROWS]
        parts.append(head + ",\n".join("  " + r for r in batch) + f"\nON CONFLICT {conflict}DO NOTHING;\n")
    return "".join(parts)


def generate(out: Path, count: int = DEFAULT_COUNT) -> list:
    """Writes the three files into `out` and returns the providers."""
    check_licence_stub()
    providers = build(count)
    npis = [p["npi"] for p in providers]               # the generator's own guard, before anything is written
    assert len(set(npis)) == count and all(npi_valid(n) and not npi_reserved(n) for n in npis), "NPI rule violated"
    lay = Layout("PRVRECRD")
    write_records(out / DAT, [record(lay, p) for p in providers])
    write_text(out / PROVIDER_SQL,
               header("provider_db.provider", "the providers of the .dat file", count)
               + insert_script("provider", COLUMNS, [provider_values(p) for p in providers], ""))
    write_text(out / REVAL_SQL,
               header("revalidation_db.tracked_provider", "the read model of the revalidation sweep", count)
               + insert_script("tracked_provider", TRACKED_COLUMNS, [tracked_values(p) for p in providers], "(npi) "))
    return providers


def summary(providers: list) -> str:
    def n(key, val):
        return sum(1 for p in providers if p[key] == val)
    status = " ".join(f"{s}={n('enroll_status', s)}" for s in "ATSPD")
    types = " ".join(f"{t}={n('provider_type', t)}" for t in ("IN", "GR", "FA"))
    active = [p for p in providers if p["enroll_status"] == "A"]
    due = sum(1 for p in active if p["reval_due_date"] <= FROZEN)
    return f"status {status}; types {types}; ACTIVE due on or before {FROZEN}: {due}"


def main(argv: list) -> None:
    ap = argparse.ArgumentParser(prog="generate.py perf", description=__doc__.split("\n")[0])
    ap.add_argument("--out", type=Path, required=True,
                    help="output folder (required: the files are never stored in the repository, so there is no default)")
    ap.add_argument("--count", type=int, default=DEFAULT_COUNT, help=f"number of providers (default {DEFAULT_COUNT})")
    args = ap.parse_args(argv)
    if not 1 <= args.count <= MAX_COUNT:
        ap.error(f"--count must be between 1 and {MAX_COUNT}")
    out = args.out.resolve()
    if out == TESTDATA.resolve() or TESTDATA.resolve() in out.parents:
        ap.error(f"--out must not be inside {TESTDATA}: the perf files are never stored in the repository's data set")
    providers = generate(out, args.count)
    print(f"generated {len(providers)} providers under {out} (seed {SEED}): {DAT}, {PROVIDER_SQL}, {REVAL_SQL}")
    print(summary(providers))


if __name__ == "__main__":
    main(sys.argv[1:])
