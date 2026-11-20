#!/usr/bin/env python3
"""MAM-44 AC-6: how many of the verified rules do the synthetic datasets exercise?

Reads every rule of modern/docs/architecture/rule-to-service.csv that is not DROPPED (433 today, QF-9 default A),
and for each one names the fixture records that supply the input state its Given/When needs. Nothing is asserted by
hand: a rule is mapped to (fixture file, predicate on a record) and the row of coverage.csv is the first records of
the REAL file, read back from disk, that satisfy the predicate. A rule whose predicate matches no record is listed
as uncovered, as is every rule whose trigger is not data (faults, environment, screen events), each with a reason.

Outputs (modern/testdata/):
  coverage.csv        rule_id,fixture_file,record_ref,basis        covered rules only
  coverage-gaps.csv   rule_id,reason                              uncovered rules
record_ref is 'rec:N;N;N' (record numbers, 1-based, at most three) or 'all' (the whole file, a run-level rule).
basis is 'record' (a specific record triggers the branch), 'run' (any run over the file exercises it: opening,
reading, end of file, totals), 'lookup' (the NPI of a record is the key the rule looks up) or 'date' (a business date
of scenarios/business-dates.csv drives the date arithmetic).

Usage:  python modern/tools/data/coverage_report.py            check: recompute from the files on disk, compare with
                                                                coverage*.csv, exit 1 below 95% or on any difference
        python modern/tools/data/coverage_report.py --write    rewrite coverage.csv and coverage-gaps.csv
"""
import sys

sys.dont_write_bytecode = True

import argparse
import collections
import csv
import io
from datetime import date
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from pemdata import BUSINESS_DATE, MODERN, NO_DATE, TESTDATA, Layout, read_records

RULES_CSV = MODERN / "docs/architecture/rule-to-service.csv"
THRESHOLD = 0.95

FIXTURES = {
    "APP": ("fixed-width/appinput-baseline.dat", "APPINPUT"),
    "SEED": ("fixed-width/provider-master-seed.dat", "PRVRECRD"),
    "EDGE": ("fixed-width/provider-master-edge.dat", "PRVRECRD"),
    "SAN": ("fixed-width/sancin-baseline.dat", "SANCIN"),
    "SCN": ("scenarios/appinput-scenarios.dat", "APPINPUT"),
    "NEGAPP": ("negative/appinput-bad-length.dat", "APPINPUT"),
    "NEGSAN": ("negative/sancin-malformed.dat", "SANCIN"),
    "DATES": ("scenarios/business-dates.csv", None),
}

TIER_PREFIXES = {"332", "251", "341", "291", "225"}


# ---- loading the fixtures from disk -------------------------------------------------------------------------------

class Fixture:
    def __init__(self, root: Path, key: str):
        self.key = key
        self.rel, layout = FIXTURES[key]
        path = root / self.rel
        if layout is None:
            with path.open(encoding="utf-8", newline="") as f:
                self.records = [dict(r, _n=i + 1) for i, r in enumerate(csv.DictReader(f))]
        else:
            lay = Layout(layout)
            self.records = [dict(lay.parse(r), _n=i + 1, _len=len(r), _lrecl=lay.length)
                            for i, r in enumerate(read_records(path))]
            if layout == "APPINPUT":
                _classify_applications(self.records)

    def match(self, pred: str):
        """Record numbers satisfying the predicate, or 'all' for a run-level predicate."""
        if pred == "all":
            return "all" if self.records else []
        if pred == "no_errors":      # file-level: every application of the file passes the enrolment validation
            return "all" if self.records and all(not r["_codes"] for r in self.records) else []
        fn = PREDICATES[self.key][pred]
        return [r["_n"] for r in self.records if fn(r, CTX)]


def _classify_applications(records):
    """The enrolment validation of PRVENR01 as the modern service does it (E101-E104), in file order."""
    validated = set()
    for r in records:
        codes = set()
        if r["_len"] != r["_lrecl"]:
            codes.add("UNREADABLE")
        else:
            npi = r["PRV-NPI"]
            well_formed = len(npi) == 10 and npi.isdigit()
            if not well_formed:
                codes.add("E101")
            elif npi in validated:
                codes.add("E103")
            if r["PRV-TYPE-CODE"] not in ("IN", "GR", "FA"):
                codes.add("E102")
            if not r["PRV-TAXONOMY"]:
                codes.add("E104")
            if not codes:
                validated.add(npi)
        r["_codes"] = codes


class Context:
    """What predicates need from other files: the seeded NPIs and statuses, the expected scenario outcomes."""
    seed_status: dict = {}
    expected: dict = {}


CTX = Context()


def _real_date(text):
    try:
        date(int(text[:4]), int(text[4:6]), int(text[6:8]))
        return True
    except (ValueError, IndexError):
        return False


def _master(**extra):
    p = {
        "status=A": lambda r, c: r["PRV-ENROLL-STATUS"] == "A",
        "status=T": lambda r, c: r["PRV-ENROLL-STATUS"] == "T",
        "status=P": lambda r, c: r["PRV-ENROLL-STATUS"] == "P",
        "status=D": lambda r, c: r["PRV-ENROLL-STATUS"] == "D",
        "status=S": lambda r, c: r["PRV-ENROLL-STATUS"] == "S",
        "flag=U": lambda r, c: r["PRV-SCREEN-FLAG"] == "U",
        "flag=P": lambda r, c: r["PRV-SCREEN-FLAG"] == "P",
        "flag=F": lambda r, c: r["PRV-SCREEN-FLAG"] == "F",
        "tier=H": lambda r, c: r["PRV-RISK-TIER"] == "H",
        "tier=M": lambda r, c: r["PRV-RISK-TIER"] == "M",
        "tier=L": lambda r, c: r["PRV-RISK-TIER"] == "L",
        "tax=332": lambda r, c: r["PRV-TAXONOMY"][:3] == "332",
        "tax=251": lambda r, c: r["PRV-TAXONOMY"][:3] == "251",
        "tax=341": lambda r, c: r["PRV-TAXONOMY"][:3] == "341",
        "tax=291": lambda r, c: r["PRV-TAXONOMY"][:3] == "291",
        "tax=225": lambda r, c: r["PRV-TAXONOMY"][:3] == "225",
        "tax=other": lambda r, c: r["PRV-TAXONOMY"] != "" and r["PRV-TAXONOMY"][:3] not in TIER_PREFIXES,
        "blank_tax": lambda r, c: r["PRV-TAXONOMY"] == "",
        "unscreened_pending": lambda r, c: r["PRV-SCREEN-FLAG"] == "U" and r["PRV-ENROLL-STATUS"] == "P",
        "active_due_le_today": lambda r, c: (r["PRV-ENROLL-STATUS"] == "A" and r["PRV-REVAL-DUE-DATE"] != NO_DATE
                                             and r["PRV-REVAL-DUE-DATE"] <= BUSINESS_DATE),
        "active_due_gt_today": lambda r, c: (r["PRV-ENROLL-STATUS"] == "A" and r["PRV-REVAL-DUE-DATE"] != NO_DATE
                                             and r["PRV-REVAL-DUE-DATE"] > BUSINESS_DATE),
        "active_no_due": lambda r, c: r["PRV-ENROLL-STATUS"] == "A" and r["PRV-REVAL-DUE-DATE"] == NO_DATE,
        "active_future_year": lambda r, c: (r["PRV-ENROLL-STATUS"] == "A"
                                            and r["PRV-EFFECTIVE-DATE"][:4] > BUSINESS_DATE[:4]),
        "active_pre2000": lambda r, c: r["PRV-ENROLL-STATUS"] == "A" and r["PRV-EFFECTIVE-DATE"][:4] < "2000",
        "active_flag_F": lambda r, c: r["PRV-ENROLL-STATUS"] == "A" and r["PRV-SCREEN-FLAG"] == "F",
        "active_in": lambda r, c: r["PRV-ENROLL-STATUS"] == "A" and r["PRV-TYPE-CODE"] == "IN",
        "active_org": lambda r, c: r["PRV-ENROLL-STATUS"] == "A" and r["PRV-TYPE-CODE"] in ("GR", "FA"),
    }
    p.update(extra)
    return p


def _app():
    return {
        "valid_any": lambda r, c: not r["_codes"],
        "npi_blank": lambda r, c: "E101" in r["_codes"],
        "type_invalid": lambda r, c: "E102" in r["_codes"],
        "tax_blank": lambda r, c: "E104" in r["_codes"],
        "dup_npi": lambda r, c: "E103" in r["_codes"],
        "dup_only": lambda r, c: r["_codes"] == {"E103"},
        "dup_invalid": lambda r, c: {"E103", "E102"} <= r["_codes"],
        "invalid_any": lambda r, c: bool(r["_codes"] - {"UNREADABLE"}),
        "owner_present": lambda r, c: r["PRV-TYPE-CODE"] in ("GR", "FA") and bool(r["PRV-OWNER-NPI"]),
        "bad_length": lambda r, c: "UNREADABLE" in r["_codes"],
        "scn_pass": lambda r, c: c.expected.get(r["PRV-NPI"]) == "PASS",
        "scn_fail": lambda r, c: c.expected.get(r["PRV-NPI"]) == "FAIL",
        "not_in_seed": lambda r, c: r["PRV-NPI"] not in c.seed_status,
    }


def _san():
    known = lambda r, c: r["SAN-NPI"] in c.seed_status
    return {
        "act=T": lambda r, c: r["SAN-ACTION"] == "T",
        "act=S": lambda r, c: r["SAN-ACTION"] == "S",
        "act=R": lambda r, c: r["SAN-ACTION"] == "R",
        "applied_any": lambda r, c: r["SAN-ACTION"] in ("T", "S", "R") and known(r, c),
        "unknown_npi": lambda r, c: not known(r, c),
        "invalid_action": lambda r, c: r["SAN-ACTION"] not in ("T", "S", "R"),
        "act=R_on_PD": lambda r, c: r["SAN-ACTION"] == "R" and c.seed_status.get(r["SAN-NPI"]) in ("P", "D"),
        "bad_date": lambda r, c: not _real_date(r["SAN-EFF-DATE"]),
        "bad_length": lambda r, c: r["_len"] != r["_lrecl"],
    }


PREDICATES = {
    "APP": _app(), "SCN": _app(), "NEGAPP": _app(),
    "SEED": _master(), "EDGE": _master(),
    "SAN": _san(), "NEGSAN": _san(),
    "DATES": {n: (lambda n: lambda r, c: r["label"] == n)(n.replace("_", "-"))
              for n in ("frozen", "sweep", "leap_day", "century_pivot")},
}


# ---- the rule table: rule -> (fixture, predicate) ---------------------------------------------------------------------

def _ids(prog, rules, items):
    out = []
    for it in items:
        if isinstance(it, tuple):                       # an inclusive range; numbers that are no rule are skipped
            out += [r for r in (f"R-{prog}-cbl-{n:05d}" for n in range(it[0], it[1] + 1)) if r in rules]
        else:
            rid = f"R-{prog}-cbl-{it:05d}" if isinstance(it, int) else f"R-{prog}-{it}"
            if rid not in rules:
                raise SystemExit(f"rule table names {rid}, which is not a non-DROPPED rule")
            out.append(rid)
    return out


FAULT = "an I/O fault (a file status other than OK or end of file), which no data record can cause; fault injection " \
        "belongs to the service tests (D-024)"


def build_table(rules):
    """Returns (covered, gaps): covered[rule] = (fixture, predicate, tag or None); gaps[rule] = reason."""
    covered, gaps = {}, {}

    def cov(prog, fixture, pred, *items, tag=None):
        for rid in _ids(prog, rules, items):
            if rid in covered or rid in gaps:
                raise SystemExit(f"rule table assigns {rid} twice")
            covered[rid] = (fixture, pred, tag)

    def gap(prog, reason, *items):
        for rid in _ids(prog, rules, items):
            if rid in covered or rid in gaps:
                raise SystemExit(f"rule table assigns {rid} twice")
            gaps[rid] = reason

    # PRVENR01: the enrolment intake (APPINPUT)
    p = "PRVENR01"
    cov(p, "APP", "all", "ADD-01", 1, 2, 9, (10, 22), 24, (60, 65))
    cov(p, "APP", "valid_any", 7, 8, 30, 39, 40, 41, 42, 56, 57)
    cov(p, "APP", "npi_blank", 3, 25, 26, 27, 28, 43, 54)
    cov(p, "APP", "type_invalid", 4, 29, 31, 32, 33, 34, 44, 53, "ADD-05")
    cov(p, "APP", "tax_blank", 5, 35, 36, 37, 45, 55)
    cov(p, "APP", "invalid_any", 6, 38, 46, 47, 48, 49, 50, 52, 66, 68, "ADD-06", "ADD-08")
    cov(p, "APP", "dup_invalid", 51)
    cov(p, "APP", "dup_npi", 58, "ADD-04")
    cov(p, "APP", "owner_present", "ADD-02")
    cov(p, "NEGAPP", "bad_length", 23, "ADD-03")
    cov(p, "NEGAPP", "all", "ADD-07")
    cov(p, "SCN", "no_errors", 67)
    gap(p, "a master write failure other than a duplicate key is a storage fault, not a data state (D-024)", 59)

    # PRVSCR01: screening (master records with flag U; the scenarios give pass and fail outcomes)
    p = "PRVSCR01"
    cov(p, "SEED", "all", 1, 2, 12, (13, 19), 23, 62)
    cov(p, "SEED", "flag=U", 3, 6, 7, 8, 9, 10, 11, 20, 21, 29, 30, 32, 33, 34, 35, 36, 38, 40, 42, 43, 44, 45, 49, 52,
        53, 54, 55, 56, 57, 58, 59, 60, "ADD-02")
    cov(p, "SEED", "flag=P", 22)
    cov(p, "SEED", "unscreened_pending", "ADD-03")
    cov(p, "SCN", "scn_pass", 31, 39, 41, 46, 50)
    cov(p, "SCN", "scn_fail", 37, 47, 48, 51, 61, "ADD-01", "ADD-06")
    gap(p, "READ NEXT status other than end of file: " + FAULT, "ADD-04")
    gap(p, "REWRITE and WRITE status not tested: " + FAULT, "ADD-05")

    # PRVSAN01: sanctions (SANCIN against the seeded master)
    p = "PRVSAN01"
    cov(p, "SAN", "all", 1, 2, 10, 11, 12, 13, 18, 19, 22, 45, 56, 57, 58)
    cov(p, "SAN", "act=T", 4, 14, 25, 26, 27, 28, 40, 46, 54, "ADD-03")
    cov(p, "SAN", "act=S", 5, 15, 29, 30, 31, 32, 41, 47, 55)
    cov(p, "SAN", "act=R", 6, 16, 33, 34, 35, 36, 37, 39, 42, 48)
    cov(p, "SAN", "applied_any", 7, 8, 17, 20, 43, 44, 50, 51, 52, "ADD-07")
    cov(p, "SAN", "unknown_npi", 3, 21, 23, 24, "ADD-01", "ADD-06")
    cov(p, "SAN", "invalid_action", 49, "ADD-02")
    cov(p, "SAN", "act=R_on_PD", "ADD-04")
    gap(p, "the notice program is absent (PRVNT* do not exist in the estate): an environment condition, not a data "
           "state; the modern equivalent is a notification failure, covered by service fault tests (D-012)", 9, 38, 53)
    gap(p, "REWRITE status not tested: " + FAULT, "ADD-05")

    # PRVRSK01: risk tier by taxonomy prefix and the five-year due date
    p = "PRVRSK01"
    cov(p, "SEED", "all", 1, 2, 3, 7, (10, 21), 38, 48, "ADD-04", "ADD-07")
    cov(p, "SEED", "tax=332", 4, 22, 31)
    cov(p, "SEED", "tax=251", 23)
    cov(p, "SEED", "tax=341", 5, 25, 26, 32)
    cov(p, "SEED", "tax=291", 27)
    cov(p, "SEED", "tax=225", 28)
    cov(p, "SEED", "tax=other", 6, 30, 33)
    cov(p, "SEED", "tier=H", 24, 35)
    cov(p, "SEED", "tier=M", 29, 36)
    cov(p, "SEED", "tier=L", 34, 37)
    cov(p, "SEED", "status=T", "ADD-01")
    cov(p, "EDGE", "blank_tax", "ADD-03")
    cov(p, "DATES", "frozen", 8, 9, (39, 43), (45, 47))
    cov(p, "DATES", "century_pivot", 44)
    cov(p, "DATES", "leap_day", "ADD-02")
    gap(p, "READ NEXT status other than end of file: " + FAULT, "ADD-05")
    gap(p, "REWRITE status not tested: " + FAULT, "ADD-06")

    # PRVREV01: revalidation sweep
    p = "PRVREV01"
    cov(p, "SEED", "all", 1, 2, 7, 9, (11, 14), (22, 24))
    cov(p, "DATES", "frozen", 8, 10)
    cov(p, "SEED", "status=A", 3, 15)
    cov(p, "SEED", "active_due_le_today", (4, 6), (16, 21), "ADD-02")
    cov(p, "SEED", "active_due_gt_today", "ADD-01")
    cov(p, "EDGE", "active_no_due", "ADD-03")
    gap(p, "READ and WRITE statuses not tested: " + FAULT, "ADD-04")

    # PRVTMS01: T-MSIS extract
    p = "PRVTMS01"
    cov(p, "DATES", "frozen", 1, 2, 10, 17, 18, (19, 24), 34, (41, 43), 48, "ADD-07")
    cov(p, "SEED", "all", 3, (14, 16), (25, 29), (53, 55))
    cov(p, "SEED", "status=A", (7, 9), (11, 13), 30, 31, 33, (38, 40), (44, 47), (49, 52), "ADD-05")
    cov(p, "SEED", "status=T", 4, 32)
    cov(p, "EDGE", "active_future_year", 5, 6, (35, 37))
    cov(p, "EDGE", "active_pre2000", "ADD-02")
    cov(p, "SEED", "status=S", "ADD-04")
    gap(p, "a source line crosses column 72, so the program may not compile as shipped (D-021): a build-time "
           "condition that no input data reaches", "ADD-01")
    gap(p, "fields declared but never used: there is no behaviour to exercise, so no input state can cover it", "ADD-03")
    gap(p, "READ and WRITE statuses not tested: " + FAULT, "ADD-06")

    # PRVDIR01: directory extract
    p = "PRVDIR01"
    cov(p, "SEED", "all", 1, 2, 3, 9, (10, 17), 29)
    cov(p, "SEED", "status=A", 4, 5, 7, 8, (18, 22), 24, (26, 28), "ADD-04", "ADD-05")
    cov(p, "SEED", "active_in", 6, 23, "ADD-02")
    cov(p, "SEED", "active_org", 25)
    cov(p, "EDGE", "active_flag_F", "ADD-01")
    gap(p, "OPEN and WRITE statuses not tested: " + FAULT, "ADD-03")

    # PRVCICS1: provider inquiry by NPI (replaced by the console, D-022); the seeded NPIs are the lookup keys
    p = "PRVCICS1"
    cov(p, "SEED", "all", 1, 2, 3, 5, 10, 11, 12, 13, 15, 17, 18, 19, 21, 22, 23, (41, 45), "ADD-02", "ADD-07",
        tag="lookup")
    cov(p, "SCN", "not_in_seed", 4, 24, 25, 36)
    cov(p, "SEED", "status=A", 6, 26, 30, 31, 37, "ADD-01")
    cov(p, "SEED", "status=T", 7, 27, 32, 33, 38)
    cov(p, "SEED", "status=P", 8, 28, 34, 39)
    cov(p, "SEED", "status=D", 9, 29, 35, 40)
    cov(p, "SEED", "status=S", "ADD-06")
    gap(p, "RECEIVE MAP / MAPFAIL handling of a 3270 screen, replaced by the web console (D-022): a terminal "
           "protocol condition, not a data state", "ADD-03")
    gap(p, "READ response IOERR or NOTAUTH: an I/O or security fault, not a data state (D-024)", "ADD-04")
    gap(p, "no authorisation check: a role matter (Keycloak roles, D-023), tested with tokens, not with data", "ADD-05")
    gap(p, "first visit of a terminal session with no NPI in the communication area: a session state, no data record "
           "is involved", 14)
    gap(p, "opening the PINQ transaction shows the blank map: a screen event, no data record is involved", 16)
    gap(p, "NPI input not received (an empty request): a request without a value cannot be a fixture record; covered "
           "by the console form tests", 20)
    return covered, gaps


# ---- computing and writing the coverage ---------------------------------------------------------------------------

def load_rules():
    with RULES_CSV.open(encoding="utf-8", newline="") as f:
        return [r["rule_id"] for r in csv.DictReader(f) if r["service"] != "DROPPED"]


def compute(root: Path):
    rules_in_order = load_rules()
    rules = set(rules_in_order)
    covered_spec, gap_spec = build_table(rules)
    missing = rules - set(covered_spec) - set(gap_spec)
    if missing:
        raise SystemExit(f"{len(missing)} rules are neither mapped nor listed as gaps: {sorted(missing)[:5]} ...")

    fixtures = {k: Fixture(root, k) for k in FIXTURES}
    CTX.seed_status = {r["PRV-NPI"]: r["PRV-ENROLL-STATUS"] for r in fixtures["SEED"].records}
    CTX.expected = {r["npi"]: r["expected_outcome"] for r in _read_csv(root / "scenarios/expected-outcomes.csv")}

    rows, gaps = [], []
    for rid in rules_in_order:
        if rid in gap_spec:
            gaps.append([rid, gap_spec[rid]])
            continue
        fixture_key, pred, tag = covered_spec[rid]
        fx = fixtures[fixture_key]
        hit = fx.match(pred)
        if not hit:
            gaps.append([rid, f"no record of {fx.rel} satisfies '{pred}'"])
            continue
        ref = "all" if hit == "all" else "rec:" + ";".join(str(n) for n in hit[:3])
        basis = tag or ("date" if fixture_key == "DATES" else "run" if hit == "all" else "record")
        rows.append([rid, fx.rel, ref, basis])
    return rows, gaps, len(rules_in_order)


def _read_csv(path: Path):
    with path.open(encoding="utf-8", newline="") as f:
        return list(csv.DictReader(f))


def _csv_text(header, rows):
    buf = io.StringIO()
    w = csv.writer(buf, lineterminator="\n")
    w.writerow(header)
    w.writerows(rows)
    return buf.getvalue()


def write(root: Path) -> None:
    rows, gaps, _ = compute(root)
    (root / "coverage.csv").write_bytes(_csv_text(["rule_id", "fixture_file", "record_ref", "basis"], rows).encode())
    (root / "coverage-gaps.csv").write_bytes(_csv_text(["rule_id", "reason"], gaps).encode())


def summary(rows, gaps, total):
    covered = len(rows)
    by_basis = collections.Counter(r[3] for r in rows)
    by_prog = collections.defaultdict(lambda: [0, 0])
    for r in rows:
        by_prog[r[0].split("-")[1]][0] += 1
    for g in gaps:
        by_prog[g[0].split("-")[1]][1] += 1
    lines = [f"rules (non-DROPPED): {total}", f"covered:             {covered} ({100.0 * covered / total:.2f}%)",
             f"uncovered:           {len(gaps)}", "covered by basis:    " + ", ".join(f"{k} {v}" for k, v in sorted(by_basis.items())),
             "by program (covered/uncovered): " + ", ".join(f"{k} {v[0]}/{v[1]}" for k, v in sorted(by_prog.items()))]
    return "\n".join(lines)


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--root", type=Path, default=TESTDATA, help="testdata root (default modern/testdata)")
    ap.add_argument("--write", action="store_true", help="rewrite coverage.csv and coverage-gaps.csv")
    args = ap.parse_args()
    rows, gaps, total = compute(args.root)
    if args.write:
        write(args.root)
        print("written: coverage.csv, coverage-gaps.csv")
    else:
        want = {"coverage.csv": _csv_text(["rule_id", "fixture_file", "record_ref", "basis"], rows),
                "coverage-gaps.csv": _csv_text(["rule_id", "reason"], gaps)}
        for name, text in want.items():
            have = (args.root / name).read_bytes().decode() if (args.root / name).exists() else None
            if have != text:
                print(f"FAIL: {name} on disk differs from the recomputed coverage (run with --write)")
                sys.exit(1)
    print(summary(rows, gaps, total))
    if len(rows) < THRESHOLD * total:
        print(f"FAIL: coverage below {THRESHOLD:.0%}")
        sys.exit(1)
    print("OK: coverage at or above 95%; every uncovered rule is listed in coverage-gaps.csv with a reason")


if __name__ == "__main__":
    main()
