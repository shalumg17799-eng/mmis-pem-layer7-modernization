#!/usr/bin/env python3
"""MAM-91: compare the legacy (golden) files with the modern files, record by record and field by field.

Usage (from the repository root or from modern/):
    python modern/tools/parity/compare_parity.py [--json modern/parity/compare-result.json] [--quiet]

What it does
- Reads the golden runs (modern/parity/golden/<job>/<run-id>/: the .ebcdic.ascii files made with
  tools/data/ebcdic_to_ascii.py, and the manifest) and the modern runs (modern/parity/modern/<job>/<run-id>/: the
  .ascii record streams and the manifest).
- Checks the premises: every file a manifest lists exists with the recorded size and sha256; every golden .ascii
  equals its original .ebcdic converted again with the recorded code page; the APPINPUT and SANCIN inputs are
  byte-identical on both sides (the same bytes were uploaded); both sides name the same business date.
  The provider master input is NOT shared: the legacy run loaded the .dat, the modern stack is seeded from SQL
  (infra/scripts/seed.sh) and the .dat in the modern run folder is only a copy made by run_modern.py, equal by
  construction. Those checks are reported as information only; the real comparison of the two master inputs is
  the master-before file pair.
- Splits each file into records of the layout's length, using the layouts the codec library uses
  (libs/fixed-width/src/main/resources/layouts/*.json). A size that is not a whole number of records is a
  difference (kind "length").
- Matches the records by key (the NPI; the NPI plus its n-th occurrence in files where an NPI can repeat) and
  compares byte by byte, then field by field with the layout. A record on one side only is a difference; a file
  on one side only is a difference; a different record order is a difference.
- Normalises nothing, except the fields a manifest lists under "runDependentFields" (no manifest does). A
  business field is never normalised.
- Classifies every difference with the table RULES: EXPECTED only when an approved decision D-001..D-029
  explains it; everything else is UNEXPECTED, with the side that looks wrong and the likely cause. This tool
  only proposes; the user decides (parity-testing skill, section 5).
- Prints one block per file and writes the whole result as JSON.

Standard library only. Synthetic data only.
"""
import sys

sys.dont_write_bytecode = True

import argparse
import hashlib
import importlib.util
import json
import re
from collections import OrderedDict
from pathlib import Path

REPO = Path(__file__).resolve().parents[3]
MODERN = REPO / "modern"
LAYOUT_DIR = MODERN / "libs/fixed-width/src/main/resources/layouts"
GOLDEN = MODERN / "parity/golden"
MODERN_RUNS = MODERN / "parity/modern"
DEFAULT_JSON = MODERN / "parity/compare-result.json"
CONVERTER = MODERN / "tools/data/ebcdic_to_ascii.py"


# ---------------------------------------------------------------------------------------------------------
# layouts
# ---------------------------------------------------------------------------------------------------------

class Layout:
    """A fixed-width layout read from its exported JSON file: field name, start byte, length, REDEFINES views."""

    def __init__(self, name):
        d = json.loads((LAYOUT_DIR / f"{name}.json").read_text(encoding="utf-8"))
        self.name = d["name"]
        self.length = d["length"]
        self.discriminator = d["discriminator"]
        count = {}
        for f in d["fields"]:
            count[f["name"]] = count.get(f["name"], 0) + 1
        self.fields = []
        for f in d["fields"]:
            # a name used twice in one layout (FILLER) is told apart by its start byte
            label = f["name"] if count[f["name"]] == 1 else f"{f['name']}@{f['start']}"
            self.fields.append(dict(f, label=label))
        self.views = {v for f in self.fields for v in f["views"]}

    @staticmethod
    def text(rec):
        return rec.decode("latin-1")

    def raw(self, rec, f):
        return self.text(rec)[f["start"] - 1:f["start"] - 1 + f["length"]]

    def by_name(self, name):
        return next(f for f in self.fields if f["name"] == name)

    def discr(self, rec):
        if not self.discriminator:
            return None
        return self.raw(rec, self.by_name(self.discriminator)).strip()

    def fields_for(self, a, b):
        """Fields to compare for a pair of records: the fields of no view, plus the REDEFINES view both records
        select; when they select different views (or none) the 80-byte variant as a whole."""
        common = [f for f in self.fields if not f["views"]]
        if not self.views:
            return common
        plain = [f for f in common if f["name"] != "PRV-VARIANT"]
        variant = [f for f in common if f["name"] == "PRV-VARIANT"]
        da, db = self.discr(a), self.discr(b)
        if da == db and da in self.views:
            return plain + [f for f in self.fields if da in f["views"]]
        return plain + variant


_LAYOUTS = {}


def layout(name):
    if name not in _LAYOUTS:
        _LAYOUTS[name] = Layout(name)
    return _LAYOUTS[name]


KEY_FIELD = {"PRVRECRD": "PRV-NPI", "REVALQ": "REV-NPI", "TMSISOUT": "WS-T-NPI", "DIROUT": "DIR-NPI",
             "SCRNREC": "SCR-NPI", "APPERROR": None}


def record_key_npi(lay, rec):
    """The NPI of a record. APPERROR is free text '<NPI> TYPE:<t> ERR:...': the NPI is its first 10 bytes."""
    if lay.name == "APPERROR":
        return lay.text(rec)[:10].strip()
    return lay.raw(rec, lay.by_name(KEY_FIELD[lay.name])).strip()


# ---------------------------------------------------------------------------------------------------------
# the runs and their file pairs: label, layout, legacy file (golden, converted), modern file
# ---------------------------------------------------------------------------------------------------------

def P(label, lay, legacy, modern):
    return dict(label=label, layout=lay, legacy=legacy, modern=modern)


RUNS = [
    dict(job="PRVDAY01", run="20261120-baseline", files=[
        P("master-before", "PRVRECRD", "before/provider-master.ebcdic.ascii", "before/provider-master.ascii"),
        P("apperror", "APPERROR", "after/apperror.ebcdic.ascii", "after/apperror.ascii"),
        P("master-after-step010", "PRVRECRD", "after/provider-master-after-step010.ebcdic.ascii",
          "after/provider-master-after-step010.ascii"),
        P("scrnout", "SCRNREC", "after/scrnout.ebcdic.ascii", "after/scrnout.ascii"),
        P("master-after-step030", "PRVRECRD", "after/provider-master-after-step030.ebcdic.ascii",
          "after/provider-master-after-step030.ascii"),
        P("master-after-step040", "PRVRECRD", "after/provider-master-after-step040.ebcdic.ascii",
          "after/provider-master-after-step040.ascii"),
    ]),
    dict(job="PRVDAY01", run="20261120-valid-only", files=[
        P("master-before", "PRVRECRD", "before/provider-master.ebcdic.ascii", "before/provider-master.ascii"),
        P("apperror", "APPERROR", "after/apperror.ebcdic.ascii", "after/apperror.ascii"),
        P("master-after-step010", "PRVRECRD", "after/provider-master-after-step010.ebcdic.ascii",
          "after/provider-master-after-step010.ascii"),
        P("scrnout", "SCRNREC", "after/scrnout.ebcdic.ascii", "after/scrnout.ascii"),
        P("master-after-step030", "PRVRECRD", "after/provider-master-after-step030.ebcdic.ascii",
          "after/provider-master-after-step030.ascii"),
        P("master-after-step040", "PRVRECRD", "after/provider-master-after-step040.ebcdic.ascii",
          "after/provider-master-after-step040.ascii"),
    ]),
    dict(job="PRVMTH01", run="20261120-seed", files=[
        P("master-before", "PRVRECRD", "before/provider-master.ebcdic.ascii", "before/provider-master.ascii"),
        P("revalq", "REVALQ", "after/revalq.ebcdic.ascii", "after/revalq.ascii"),
        P("tmsisout", "TMSISOUT", "after/tmsisout.ebcdic.ascii", "after/tmsisout.ascii"),
        P("tmsis-archive-copy", "TMSISOUT", "after/tmsis-archive-copy.ebcdic.ascii", "after/tmsis-archive-copy.ascii"),
        P("master-after", "PRVRECRD", "after/provider-master-after.ebcdic.ascii", "after/provider-master-after.ascii"),
    ]),
    dict(job="PRVDIR01", run="20261120-seed", files=[
        P("master-before", "PRVRECRD", "before/provider-master.ebcdic.ascii", "before/provider-master.ascii"),
        P("dirout", "DIROUT", "after/dirout.ebcdic.ascii", "after/dirout.ascii"),
        P("master-after", "PRVRECRD", "after/provider-master-after.ebcdic.ascii", "after/provider-master-after.ascii"),
    ]),
    dict(job="PRVSAN01", run="20261120-sancin-baseline", files=[
        P("master-before", "PRVRECRD", "before/provider-master.ebcdic.ascii", "before/provider-master.ascii"),
        P("master-after", "PRVRECRD", "after/provider-master-after.ebcdic.ascii", "after/provider-master-after.ascii"),
    ]),
]


# ---------------------------------------------------------------------------------------------------------
# classification: the proposed decision for every difference
# ---------------------------------------------------------------------------------------------------------
# First matching rule wins. cls EXPECTED needs an approved decision D-001..D-029 (the "decision" field).
# Everything not matched is UNEXPECTED, group U-0 (no known cause). "side" says which side looks wrong
# (modern, legacy, none = a design difference no decision covers yet, so the user decides).

def rule(rid, cls, group, decision, why, side=None, category=None, **m):
    return dict(id=rid, cls=cls, group=group, decision=decision, why=why, side=side, category=category, m=m)


def _blank(v):
    return v in ("", None)


# per run: the NPIs both sides queued in REVALQ (filled while the REVALQ pair is compared)
CTX = {"queued": {}}


def _queued(d):
    return d["key"] in CTX["queued"].get((d["job"], d["run"]), ())


RULES = [
    # --- EXPECTED: an approved decision explains it ------------------------------------------------------
    rule("E-D001-ERRROW-TWICE", "EXPECTED", "E-1", "D-001",
         "the legacy fall-through writes the error row of the same invalid application a second time; the modern "
         "run writes exactly one row per invalid application",
         files=["apperror"], kind="legacy-only"),
    rule("E-D001-STOP", "EXPECTED", "E-1", "D-001",
         "the legacy load stops at the first invalid application (record 4), so records 5-8 are never read; the "
         "modern run processes all applications and rejects each invalid one with one row (D-016 supplies the "
         "reason: E102 type, E103 duplicate, E104 taxonomy)",
         files=["apperror"], kind="modern-only"),
    rule("E-D001-BAD-ON-MASTER", "EXPECTED", "E-1", "D-001",
         "the legacy fall-through also writes the invalid application (no NPI) to the master with the status "
         "bytes of the input; the modern run never registers an invalid application",
         files=["master-after-step010"], kind="legacy-only", when=lambda d: d["key"] == "(blank)"),
    rule("E-D003-DUE-DATE", "EXPECTED", "E-2", "D-003",
         "PRVRSK01 resets the due date of every provider to today + 5 years on every daily run (20311120 here); "
         "the modern system sets a due date only at approval or a completed revalidation (D-003), so it leaves "
         "the date as it was (or unset) in this run",
         files=["master-after-step040"], kind="field", field="PRV-REVAL-DUE-DATE", legacy=r"20311120"),
    rule("E-D003-REVALIDATED", "EXPECTED", "E-2", "D-003",
         "the month-end sweep queued this provider in both systems (REVALQ is identical); the legacy PRVREV01 only "
         "queues it and leaves the master unchanged, while in the modern system the re-screening (reason "
         "REVALIDATION) runs by events, passes and completes the revalidation, and D-003 sets the due date at each "
         "completed revalidation (business date + 5 years = 20311120); D-029 gives the case its own state",
         files=["master-after"], kind="field", field="PRV-REVAL-DUE-DATE", modern=r"20311120", when=_queued),
    rule("E-D005-SCR-FIELDS", "EXPECTED", "E-3", "D-005",
         "the legacy stub rows carry no source, no verdict date and no detail; the replaced screening service "
         "fills them from its mocked sources (the field contents are the Q-045/C-7 default)",
         files=["scrnout"], kind="field", field=r"SCR-(SOURCE|VERDICT-DATE|DETAIL)", legacy=r"",
         when=lambda d: not _blank(d["modern"])),
    rule("E-D006-SUSPEND", "EXPECTED", "E-4", "D-006",
         "sanction S gives DENIED in the legacy (PRVSAN01.cbl:109-110) and the new SUSPENDED status in the modern "
         "system",
         files=["master-after"], kind="field", field="PRV-ENROLL-STATUS", legacy=r"D", modern=r"S"),
    rule("E-D028-DIR-NAME", "EXPECTED", "E-5", "D-028",
         "the legacy name is the first 60 bytes of PRV-VARIANT (an individual: last name, first name and the "
         "start of the licence number; an organisation: legal name and the start of the DBA name); the modern "
         "name is composed (first + last, or the legal name)",
         files=["dirout"], kind="field", field="DIR-NAME"),
    rule("E-D026-NO-GENERAL-NOTICE", "EXPECTED", "E-6", "D-026",
         "an unknown action code X is rejected by the modern run (no change, no notice); the legacy applies "
         "nothing but still queues the general notice PRVNTGEN (D-012: notification-service replaces the CALL)",
         files=["notices"], kind="legacy-only", when=lambda d: d["field"] == "GEN"),

    # --- UNEXPECTED, with a known cause --------------------------------------------------------------------
    rule("U-NO-DATE", "UNEXPECTED", "U-1", None,
         "two conventions for 'no due date': the test-data files, and so the legacy run, carry 99991231 (Q-106 "
         "default A, DEFAULT APPLIED, because the codec rejects zeros), while the modern export writes 00000000 as "
         "MAM-43 AC-6 specifies (D-029: zero means unset) from an SQL seed that holds NULL; MAM-50 AC-1 'seed equals "
         "the .dat' therefore does not hold byte for byte; the same value pair appears for the new providers after "
         "STEP010 (the intake record carries 99991231, the modern provider has no due date)",
         side="test data (Q-106) against MAM-43 AC-6", category="SPEC-CONFLICT", kind="field",
         field="PRV-REVAL-DUE-DATE", legacy=r"99991231", modern=r"00000000"),
    rule("U-EFFDATE", "UNEXPECTED", "U-2", None,
         "effective date of a new provider: the legacy keeps the effective date of the intake record (20260115, "
         "D-025); the modern system sets the registration date (20261120), Q-015/Q-A1 default A, status DEFAULT "
         "APPLIED - revisit, not an approved D decision",
         side="modern", category="NEEDS-DECISION", files=["master-after-step010", "master-after-step040"],
         kind="field", field="PRV-EFFECTIVE-DATE", legacy=r"20260115", modern=r"20261120"),
    rule("U-SNAPSHOT-TIMING", "UNEXPECTED", "U-3", None,
         "snapshot timing: the legacy file is the master right after STEP010 (flag U, no tier); the modern "
         "system registers, screens and tiers a new provider by events within seconds, so the modern snapshot "
         "already holds the result of STEP030/STEP040 (no stop between them is possible); no D decision covers "
         "the event chain replacing the step sequence",
         side="none", category="NEEDS-DECISION", files=["master-after-step010"], kind="field",
         field=r"PRV-(SCREEN-FLAG|RISK-TIER)", legacy=r"U|"),
    rule("U-TIER-ON-PASS", "UNEXPECTED", "U-4", None,
         "the legacy PRVRSK01 sets a tier on every record of the file, whatever its status and screen flag; the "
         "modern risk-service assigns a tier only after a PASS screening (MAM-35 AC-6: a failed screening gets "
         "no tier), so denied or failed providers keep a blank tier; MAM-35 is an approved story, but no D "
         "decision states the rule",
         side="none", category="NEEDS-DECISION", files=["master-after-step040"], kind="field",
         field="PRV-RISK-TIER", legacy=r"[LMH]", modern=r""),
    rule("U-SCRNOUT-NEW", "UNEXPECTED", "U-5", None,
         "the legacy PRVSCR01 screens every provider with flag U in one pass, new providers included; in the "
         "modern system a new provider gets its INITIAL screening at registration (event), so its rows are not "
         "in the screening run's file; they exist in the screening service (logs/screenings-of-new-providers.json)",
         side="none", category="NEEDS-DECISION", files=["scrnout"], kind="legacy-only"),
    rule("U-COND-GATE", "UNEXPECTED", "U-6", None,
         "the legacy job never produced this file: STEP010 ended with RC=4 (the first invalid application) and "
         "the JCL bypassed STEP015-STEP040 (COND); the modern run is not gated on the intake result and went on "
         "to screening and tiering; D-001 and D-024 are the nearest decisions but neither states that gate",
         side="none", category="NEEDS-DECISION", files=["scrnout", "master-after-step040"], kind="file-modern-only"),
    rule("U-NO-SNAPSHOT", "UNEXPECTED", "U-7", None,
         "the legacy file is a lab capture between two steps (CAP030, after PRVSCR01); the modern system has no "
         "step between screening and tiering (events), so no such state can be exported; it is not a job output",
         side="none", category="NOT-COMPARABLE", files=["master-after-step030"], kind="file-legacy-only"),
    rule("U-ARCHIVE-COPY", "UNEXPECTED", "U-8", None,
         "the legacy GDG generation MMPRV.TMSIS.ARCHIVE(+1) could not be created (BLOCKED, IEF197I in the "
         "anonymous job); only a temporary IEBGENER copy exists (byte-identical to TMSISOUT); the modern archive "
         "generation (16) is recorded in tmsis-extract-list.json but its content was not exported",
         side="none", category="CAPTURE-GAP", files=["tmsis-archive-copy"], kind="file-legacy-only"),
    rule("U-NOTICE-APPROVED", "UNEXPECTED", "U-9", None,
         "reinstating (R) a PENDING provider makes it ACTIVE (D-027 preserves the legacy rule); the modern "
         "notification-service then also sends an APPROVED notice besides the RST notice; the legacy sends only "
         "the reinstatement notice; no D decision covers the extra notice",
         side="none", category="NEEDS-DECISION", files=["notices"], kind="modern-only"),
    rule("U-LICENCE-MOCK", "UNEXPECTED", "U-10", None,
         "the modern screening fails application 1006133288 (an individual whose licence SD20001 tools/data/"
         "generate.py:161 calls 'valid IN'): the licence-board stub knows only SD10001-SD10007 and the sample "
         "licences of license_board.txt, so LICBOARD answers not found and the unknown-licence default (Q-041/C-3 "
         "default A, DEFAULT APPLIED - revisit, not an approved decision) gives F. D-005 only replaces the four "
         "stubs by a real screening service; it does not say what an unknown licence gives. The legacy stubs pass "
         "everybody (P). The same cause gives the F after STEP010 (counted under U-3) and the missing tier "
         "(counted under U-4) of this provider",
         side="test data / mock (SD20001 missing from the licence-board stub)", category="TEST-DATA-GAP",
         files=["master-after-step040"], kind="field", field="PRV-SCREEN-FLAG", legacy=r"P", modern=r"F",
         when=lambda d: d["key"] == "1006133288"),
]


def classify(d):
    for r in RULES:
        m = r["m"]
        if "files" in m and d["file"] not in m["files"]:
            continue
        if "kind" in m and d["kind"] != m["kind"]:
            continue
        if "field" in m and not re.fullmatch(m["field"], d.get("field") or ""):
            continue
        if "legacy" in m and not re.fullmatch(m["legacy"], d.get("legacy") or ""):
            continue
        if "modern" in m and not re.fullmatch(m["modern"], d.get("modern") or ""):
            continue
        if "when" in m and not m["when"](d):
            continue
        d.update(cls=r["cls"], group=r["group"], decision=r["decision"], why=r["why"], rule=r["id"],
                 side=r["side"], category=r["category"])
        return d
    d.update(cls="UNEXPECTED", group="U-0", decision=None, rule="none", side="unknown", category="UNCLASSIFIED",
             why="no approved decision and no known cause matches this difference")
    return d


# ---------------------------------------------------------------------------------------------------------
# reading and comparing
# ---------------------------------------------------------------------------------------------------------

def sha256(b):
    return hashlib.sha256(b).hexdigest()


def load_json(p):
    return json.loads(p.read_text(encoding="utf-8")) if p.is_file() else None


def split_records(data, length):
    n, rest = divmod(len(data), length)
    return [data[i * length:(i + 1) * length] for i in range(n)], rest


def keyed(lay, records):
    """(NPI, n-th occurrence) -> record, in file order."""
    seen, out = {}, OrderedDict()
    for rec in records:
        k = record_key_npi(lay, rec)
        seen[k] = seen.get(k, 0) + 1
        out[(k, seen[k])] = rec
    return out, {k for k, n in seen.items() if n > 1}


def shown(v):
    return v.rstrip(" ")


def key_text(k, dups):
    npi = k[0] or "(blank)"
    return f"{npi}#{k[1]}" if k[0] in dups or k[1] > 1 else npi


def side_info(path, lay):
    if path is None or not path.is_file():
        return dict(exists=False, path=None, records=None, bytes=None, sha256=None, recordLength=lay.length)
    data = path.read_bytes()
    recs, rest = split_records(data, lay.length)
    return dict(exists=True, path=str(path.relative_to(REPO)).replace("\\", "/"), records=len(recs),
                bytes=len(data), sha256=sha256(data), recordLength=lay.length, trailingBytes=rest)


def compare_file(job, run, pair, gdir, mdir, normalised):
    lay = layout(pair["layout"])
    gpath, mpath = gdir / pair["legacy"], mdir / pair["modern"]
    ginfo, minfo = side_info(gpath, lay), side_info(mpath, lay)
    res = dict(job=job, run=run, file=pair["label"], layout=lay.name, recordLength=lay.length, legacy=ginfo,
               modern=minfo, bytesIdentical=None, firstDiffOffset=None, matched=0, identical=0, differingRecords=0,
               differingFields=0, identicalFields=0, legacyOnly=0, modernOnly=0, normalisedFields=0,
               differences=[])

    def add(kind, key=None, field=None, legacy=None, modern=None, **extra):
        d = dict(job=job, run=run, file=pair["label"], kind=kind, key=key, field=field, legacy=legacy, modern=modern)
        d.update(extra)
        res["differences"].append(classify(d))
        return d

    if not ginfo["exists"] or not minfo["exists"]:
        if ginfo["exists"] or minfo["exists"]:
            side = "legacy" if ginfo["exists"] else "modern"
            add(f"file-{side}-only", legacy=f"{ginfo['records']} records" if ginfo["exists"] else None,
                modern=f"{minfo['records']} records" if minfo["exists"] else None)
        else:
            res["neitherSide"] = True
        return res

    gdata, mdata = gpath.read_bytes(), mpath.read_bytes()
    res["bytesIdentical"] = gdata == mdata
    if gdata != mdata:
        res["firstDiffOffset"] = next((i for i, (a, b) in enumerate(zip(gdata, mdata)) if a != b),
                                      min(len(gdata), len(mdata)))
    for side, info in (("legacy", ginfo), ("modern", minfo)):
        if info["trailingBytes"]:
            add("length", field=f"{side} file size", legacy=str(ginfo["bytes"]), modern=str(minfo["bytes"]),
                note=f"{info['bytes']} bytes is not a whole number of {lay.length}-byte records")
    grecs, _ = split_records(gdata, lay.length)
    mrecs, _ = split_records(mdata, lay.length)
    gk, gdups = keyed(lay, grecs)
    mk, mdups = keyed(lay, mrecs)
    dups = gdups | mdups
    for k, grec in gk.items():
        if k not in mk:
            res["legacyOnly"] += 1
            add("legacy-only", key=key_text(k, dups), legacy=shown(lay.text(grec)), modern=None,
                npi=k[0] or "(blank)", occurrence=k[1])
    for k, mrec in mk.items():
        if k not in gk:
            res["modernOnly"] += 1
            add("modern-only", key=key_text(k, dups), legacy=None, modern=shown(lay.text(mrec)),
                npi=k[0] or "(blank)", occurrence=k[1])
    common = [k for k in gk if k in mk]
    res["matched"] = len(common)
    if pair["label"] == "revalq":
        CTX["queued"][(job, run)] = {k[0] for k in common}
    if [k for k in gk if k in mk] != [k for k in mk if k in gk]:
        add("order", field="record order", legacy="ascending in file order of the legacy file",
            modern="differs from the legacy order for the matched keys")
    for k in common:
        grec, mrec = gk[k], mk[k]
        if grec == mrec:
            res["identical"] += 1
            res["identicalFields"] += len(lay.fields_for(grec, mrec))
            continue
        res["differingRecords"] += 1
        diffs = 0
        for f in lay.fields_for(grec, mrec):
            gv, mv = lay.raw(grec, f), lay.raw(mrec, f)
            if gv == mv:
                res["identicalFields"] += 1
                continue
            if f["name"] in normalised:
                res["normalisedFields"] += 1
                continue
            diffs += 1
            add("field", key=key_text(k, dups), field=f["label"], legacy=shown(gv), modern=shown(mv),
                npi=k[0] or "(blank)", occurrence=k[1], width=f["length"], start=f["start"])
        res["differingFields"] += diffs
        if diffs == 0 and not normalised:
            add("unmapped-bytes", key=key_text(k, dups), field="bytes not covered by a field",
                legacy=shown(lay.text(grec)), modern=shown(lay.text(mrec)))
    return res


def summarise(res):
    """Counts per class at the level of units: an identical record is MATCH; a differing record is UNEXPECTED
    when one of its differing fields is, else EXPECTED; a record on one side only, a file on one side only and
    any file-level difference are one unit each."""
    c = dict(MATCH=res["identical"], EXPECTED=0, UNEXPECTED=0)
    by_record = {}
    for d in res["differences"]:
        if d["kind"] in ("field", "unmapped-bytes"):
            by_record.setdefault(d["key"], []).append(d)
        else:
            c[d["cls"]] += 1
    for key, ds in by_record.items():
        c["UNEXPECTED" if any(x["cls"] == "UNEXPECTED" for x in ds) else "EXPECTED"] += 1
    fe = sum(1 for d in res["differences"] if d["kind"] == "field" and d["cls"] == "EXPECTED")
    fu = sum(1 for d in res["differences"] if d["kind"] == "field" and d["cls"] == "UNEXPECTED")
    res["counts"] = c
    res["fieldCounts"] = dict(identical=res["identicalFields"], EXPECTED=fe, UNEXPECTED=fu)
    return res


# ---------------------------------------------------------------------------------------------------------
# premises
# ---------------------------------------------------------------------------------------------------------

def load_converter():
    spec = importlib.util.spec_from_file_location("ebcdic_to_ascii", CONVERTER)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


MASTER_INPUT_PREFIX = "provider-master"


def master_seed_vs_modern_before(gdir, mdir):
    """The master the legacy run loaded (inputs/provider-master-seed-sorted.dat, LF between records removed)
    against the master the modern stack held before its run (the SQL seed, exported). Matched by NPI; nothing is
    normalised. None when a file is missing."""
    gp, mp = gdir / "inputs/provider-master-seed-sorted.dat", mdir / "before/provider-master.ascii"
    if not gp.is_file() or not mp.is_file():
        return None
    lay = layout("PRVRECRD")
    grecs, _ = split_records(gp.read_bytes().replace(b"\n", b""), lay.length)
    mrecs, _ = split_records(mp.read_bytes(), lay.length)
    gk, _ = keyed(lay, grecs)
    mk, _ = keyed(lay, mrecs)
    keys, fields = [], []
    for k, grec in gk.items():
        mrec = mk.get(k)
        if mrec is None or mrec != grec:
            keys.append(k[0])
            if mrec is not None:
                fields += [f["name"] for f in lay.fields_for(grec, mrec) if lay.raw(grec, f) != lay.raw(mrec, f)]
    keys += [k[0] for k in mk if k not in gk]
    return dict(records=len(gk), differingRecords=len(keys), keys=keys, fields=sorted(set(fields)))


def check_premises(job, run, gdir, mdir, gman, mman, conv):
    out = []

    def chk(name, ok, detail="", info=False, info_only=False):
        # info: a check that may fail without being a defect (reported as information when it does);
        # info_only: a fact worth stating that proves nothing, never counted as ok whatever its outcome
        out.append(dict(job=job, run=run, check=name, ok=bool(ok) or info or info_only,
                        info=info_only or (info and not ok), detail=detail))

    chk("both manifests exist", gman is not None and mman is not None)
    if gman is None or mman is None:
        return out
    chk("same business date", gman["businessDate"] == mman["businessDate"],
        f"legacy {gman['businessDate']}, modern {mman['businessDate']}")
    code_page = gman.get("codePage")
    for f in gman["files"]:
        p = gdir / f["path"]
        if not p.is_file():
            chk(f"golden file exists: {f['path']}", False)
            continue
        data = p.read_bytes()
        chk(f"golden size and sha256 equal the manifest: {f['path']}",
            len(data) == f["bytes"] and sha256(data) == f["sha256"])
        if f.get("recordLength"):
            sep = 1 if f["path"].startswith("inputs/") else 0   # inputs are sent one record per line (LF)
            chk(f"golden size = records x (record length + {sep} line end): {f['path']}",
                len(data) == f["records"] * (f["recordLength"] + sep), f"{f['records']} x {f['recordLength'] + sep}")
        a = f.get("ascii")
        if a:
            ap = gdir / a["path"]
            ok = ap.is_file() and sha256(ap.read_bytes()) == a["sha256"]
            chk(f"golden .ascii equals the manifest: {a['path']}", ok)
            if ok and code_page:
                try:
                    again = conv.convert(data, code_page)
                    chk(f"golden .ascii equals the .ebcdic converted again with code page {code_page}: {a['path']}",
                        again == ap.read_bytes())
                except ValueError as e:
                    chk(f"golden .ebcdic converts with code page {code_page}: {f['path']}", False, str(e))
    for f in mman["files"]:
        p = mdir / f["path"]
        if not p.is_file():
            chk(f"modern file exists: {f['path']}", False)
            continue
        data = p.read_bytes()
        chk(f"modern size and sha256 equal the manifest: {f['path']}",
            len(data) == f["bytes"] and sha256(data) == f["sha256"])
        if f.get("recordLength") and f["path"].endswith(".ascii"):
            chk(f"modern size = records x record length: {f['path']}",
                len(data) == f["records"] * f["recordLength"], f"{f['records']} x {f['recordLength']}")
    gin, min_ = gdir / "inputs", mdir / "inputs"
    for p in sorted(gin.glob("*.dat")):
        q = min_ / p.name
        if p.name.startswith(MASTER_INPUT_PREFIX):
            # the legacy run loaded this file; the modern stack was seeded from SQL, so a copy in the modern run
            # folder (made by run_modern.py copy_input) equals it by construction and proves nothing
            seed = master_seed_vs_modern_before(gdir, mdir)
            copy = "a copy is in the modern run folder (equal by construction, proves nothing)" if q.is_file()                 else "no copy in the modern run folder"
            if seed:
                real = (f"the real modern input is the SQL seed (infra/scripts/seed.sh): its exported BEFORE master "
                        f"differs from this file in {seed['differingRecords']} of {seed['records']} records "
                        f"({', '.join(seed['fields'])}; keys {', '.join(seed['keys'])}), see master-before and U-1")
            else:
                real = "the real modern input is the SQL seed (infra/scripts/seed.sh); the BEFORE exports were not found"
            chk(f"master input NOT shared (legacy loaded the .dat, modern seeded from SQL; information only): "
                f"inputs/{p.name}", True, f"{copy}; {real}", info_only=True)
        elif q.is_file():
            chk(f"input byte-identical on both sides: inputs/{p.name}", p.read_bytes() == q.read_bytes(),
                sha256(p.read_bytes())[:16])
        else:
            chk(f"input byte-identical on both sides: inputs/{p.name}", False,
                "not copied to the modern run folder", info=True)
    # the legacy BEFORE files are the loaded inputs, checked here on the converted bytes (not only the manifest flag)
    for before, inp in (("before/provider-master.ebcdic.ascii", "inputs/provider-master-seed-sorted.dat"),
                        ("before/appinput.ebcdic.ascii", "inputs/appinput.dat"),
                        ("before/sancin.ebcdic.ascii", "inputs/sancin.dat")):
        if (gdir / before).is_file() and (gdir / inp).is_file():
            chk(f"legacy BEFORE equals the input sent, records back to back: {before} = {inp}",
                (gdir / before).read_bytes() == (gdir / inp).read_bytes().replace(b"\n", b""))
    # the legacy BEFORE master and BEFORE applications are the loaded inputs (proof recorded by the legacy run)
    for f in gman["files"]:
        if f.get("equalsLoadedInput") is not None:
            chk(f"legacy BEFORE equals the loaded input (manifest): {f['path']}", f["equalsLoadedInput"] is True)
    return out


def run_dependent(man):
    """Field names a manifest lists as run-dependent (none of the manifests does)."""
    names = set()
    for key in ("runDependentFields", "runDependent"):
        v = (man or {}).get(key)
        if isinstance(v, list):
            names |= {x for x in v if isinstance(x, str)}
    return names


# ---------------------------------------------------------------------------------------------------------
# supplementary: sanction notices (legacy SYSOUT lines vs notification-service)
# ---------------------------------------------------------------------------------------------------------

def compare_notices(gdir, mdir, job, run):
    sysout, notices = gdir / "logs/program-sysout.txt", mdir / "logs/notices.json"
    if not sysout.is_file() or not notices.is_file():
        return None
    legacy = []
    for line in sysout.read_text(encoding="utf-8", errors="replace").splitlines():
        m = re.match(r"PRVNT(TRM|SUS|RST|GEN): .* NPI (\d{10})", line.strip())
        if m:
            legacy.append((m.group(2), m.group(1)))
    modern = [(i["npi"], i["template"]) for i in json.loads(notices.read_text(encoding="utf-8"))["items"]]
    res = dict(job=job, run=run, file="notices", layout="(supplementary: notice lines, not a file)",
               recordLength=None, legacy=dict(exists=True, records=len(legacy), path=str(sysout.relative_to(REPO))),
               modern=dict(exists=True, records=len(modern), path=str(notices.relative_to(REPO))),
               bytesIdentical=None, firstDiffOffset=None, matched=0, identical=0, differingRecords=0,
               differingFields=0, identicalFields=0, legacyOnly=0, modernOnly=0, normalisedFields=0,
               differences=[], supplementary=True)
    lset, mset = sorted(legacy), sorted(modern)
    for item in lset:
        if item in mset:
            res["matched"] += 1
            res["identical"] += 1
        else:
            res["legacyOnly"] += 1
            res["differences"].append(classify(dict(job=job, run=run, file="notices", kind="legacy-only",
                                                    key=item[0], field=item[1], legacy=f"{item[1]} {item[0]}",
                                                    modern=None)))
    for item in mset:
        if item not in lset:
            res["modernOnly"] += 1
            res["differences"].append(classify(dict(job=job, run=run, file="notices", kind="modern-only",
                                                    key=item[0], field=item[1], legacy=None,
                                                    modern=f"{item[1]} {item[0]}")))
    return summarise(res)


# ---------------------------------------------------------------------------------------------------------
# main
# ---------------------------------------------------------------------------------------------------------

def screenings_context(mdir):
    """Modern INITIAL screening results of the providers registered by the intake run, by NPI, for the report."""
    d = load_json(mdir / "logs/screenings-of-new-providers.json")
    out = {}
    for npi, v in (d or {}).items():
        items = (v.get("screenings") or {}).get("items") or []
        if items:
            out[npi] = " ".join(f"{r['source']}={r['result']}" for r in items[0]["results"])
    return out


def main(argv=None):
    ap = argparse.ArgumentParser(description="Compare the legacy and the modern PEM batch output files.")
    ap.add_argument("--json", type=Path, default=DEFAULT_JSON, help="where to write the result (JSON)")
    ap.add_argument("--quiet", action="store_true", help="print only the summary")
    a = ap.parse_args(argv)
    conv = load_converter()

    files, premises, notices, screenings = [], [], [], {}
    for r in RUNS:
        job, run = r["job"], r["run"]
        gdir, mdir = GOLDEN / job / run, MODERN_RUNS / job / run
        gman, mman = load_json(gdir / "manifest.json"), load_json(mdir / "manifest.json")
        premises += check_premises(job, run, gdir, mdir, gman, mman, conv)
        normalised = run_dependent(gman) | run_dependent(mman)
        for pair in r["files"]:
            res = compare_file(job, run, pair, gdir, mdir, normalised)
            if res.get("neitherSide"):
                continue
            res["legacyNotProducedBecause"] = None
            if not res["legacy"]["exists"] and gman:
                for n in gman.get("notProducedByLegacy", []):
                    if pair["legacy"].split("/")[-1].replace(".ebcdic.ascii", "") in n["dataset"]:
                        res["legacyNotProducedBecause"] = n["because"]
            files.append(summarise(res))
        if (mdir / "logs/screenings-of-new-providers.json").is_file():
            screenings[f"{job}/{run}"] = screenings_context(mdir)
        n = compare_notices(gdir, mdir, job, run)
        if n:
            notices.append(n)

    # attach the modern screening of a new provider to the legacy-only SCRNOUT rows (context for the report)
    for f in files:
        if f["file"] == "scrnout":
            ctx = screenings.get(f"{f['job']}/{f['run']}", {})
            for d in f["differences"]:
                if d["kind"] == "legacy-only" and d.get("npi") in ctx:
                    d["modernScreeningOfThisNpi"] = ctx[d["npi"]]

    allres = files + notices
    totals = dict(MATCH=0, EXPECTED=0, UNEXPECTED=0)
    for f in files:
        for k in totals:
            totals[k] += f["counts"][k]
    suppl = dict(MATCH=0, EXPECTED=0, UNEXPECTED=0)
    for f in notices:
        for k in suppl:
            suppl[k] += f["counts"][k]
    unexpected = [d for f in allres for d in f["differences"] if d["cls"] == "UNEXPECTED"]
    expected = [d for f in allres for d in f["differences"] if d["cls"] == "EXPECTED"]
    groups = {}
    for d in unexpected + expected:
        g = groups.setdefault(d["group"], dict(group=d["group"], cls=d["cls"], decision=d["decision"],
                                               category=d["category"], side=d["side"], why=d["why"], count=0))
        g["count"] += 1
    result = dict(
        tool="modern/tools/parity/compare_parity.py", businessDate="2026-11-20",
        normalised="nothing (no manifest lists a run-dependent field); the record separator of the modern API "
                   "files is removed when the manifest's .ascii record stream is written, no field is changed",
        premisesOk=sum(1 for p in premises if p["ok"] and not p["info"]), premisesFailed=[p for p in premises if not p["ok"]],
        premisesInfo=[p for p in premises if p["info"]],
        premiseChecks=len(premises), premises=premises, totals=totals, supplementaryTotals=suppl, groups=sorted(groups.values(),
                                                                                              key=lambda g: g["group"]),
        files=allres, screeningsOfNewProviders=screenings, rules=[{k: v for k, v in r.items() if k != "m"}
                                                                   | {"files": r["m"].get("files"),
                                                                      "kind": r["m"].get("kind"),
                                                                      "field": r["m"].get("field")} for r in RULES])
    a.json.parent.mkdir(parents=True, exist_ok=True)
    a.json.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8", newline="\n")

    print(f"premise checks: {result['premisesOk']} ok, {len(result['premisesFailed'])} failed, "
          f"{len(result['premisesInfo'])} information only (of {len(premises)})")
    for p in result["premisesInfo"]:
        print(f"  INFO {p['job']}/{p['run']}: {p['check']} -- {p['detail']}")
    for p in result["premisesFailed"]:
        print(f"  FAILED {p['job']}/{p['run']}: {p['check']} {p['detail']}")
    print("normalised fields: none (no manifest lists a run-dependent field)")
    for f in allres:
        c, fc = f["counts"], f["fieldCounts"]
        lg, md = f["legacy"], f["modern"]
        tag = " [supplementary]" if f.get("supplementary") else ""
        print(f"\n{f['job']}/{f['run']}  {f['file']}{tag}  ({f['layout']}"
              f"{', ' + str(f['recordLength']) + ' bytes' if f['recordLength'] else ''})")
        print(f"  records: legacy {lg.get('records') if lg.get('exists') else 'file absent'}, "
              f"modern {md.get('records') if md.get('exists') else 'file absent'}; bytes identical: "
              f"{f['bytesIdentical']}"
              + (f" (first difference at byte {f['firstDiffOffset']})" if f["firstDiffOffset"] is not None else ""))
        print(f"  matched {f['matched']}, identical records {f['identical']}, differing records "
              f"{f['differingRecords']}, differing fields {f['differingFields']}, legacy only {f['legacyOnly']}, "
              f"modern only {f['modernOnly']}")
        print(f"  classes (records/units): MATCH {c['MATCH']}, EXPECTED {c['EXPECTED']}, UNEXPECTED "
              f"{c['UNEXPECTED']}; fields: identical {fc['identical']}, EXPECTED {fc['EXPECTED']}, "
              f"UNEXPECTED {fc['UNEXPECTED']}")
        if not a.quiet:
            for d in f["differences"]:
                lv = d["legacy"] if d["legacy"] is not None else "-"
                mv = d["modern"] if d["modern"] is not None else "-"
                lv = lv if len(lv) <= 60 else lv[:57] + "..."
                mv = mv if len(mv) <= 60 else mv[:57] + "..."
                print(f"    {d['cls']:10} {d['group']:4} {d['kind']:16} key={d.get('key')} field={d.get('field')} "
                      f"legacy={lv!r} modern={mv!r} {d['decision'] or ''}")
    print(f"\nTOTAL (six files): MATCH {totals['MATCH']}, EXPECTED {totals['EXPECTED']}, "
          f"UNEXPECTED {totals['UNEXPECTED']}")
    print(f"SUPPLEMENTARY (notices): MATCH {suppl['MATCH']}, EXPECTED {suppl['EXPECTED']}, "
          f"UNEXPECTED {suppl['UNEXPECTED']}")
    print("\ngroups:")
    for g in result["groups"]:
        print(f"  {g['group']:4} {g['cls']:10} {g['decision'] or '-':6} x{g['count']:<3} {g['category'] or ''}")
    print(f"\nresult written to {a.json}")
    return 0 if not result["premisesFailed"] else 1


if __name__ == "__main__":
    sys.exit(main())
