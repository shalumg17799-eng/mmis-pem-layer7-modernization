"""Builds modern/docs/reports/traceability.md (MAM-89): legacy rule -> story -> AC -> tests (with results) -> code.

Generated, never written by hand (MAM-89 AC-5): two runs with no change in the inputs give the same file apart from the
"Generated" line. Run after the tests and after build_rule_trace.py:
    modern/kag/.venv/Scripts/python.exe modern/tools/trace/build_rule_trace.py
    modern/kag/.venv/Scripts/python.exe modern/tools/trace/build_traceability_report.py

Inputs: modern/docs/traceability/rule-trace.csv (rule -> story -> ACs -> tests -> code, from build_rule_trace.py),
modern/docs/architecture/rule-to-service.csv (433 kept + 22 DROPPED rules), modern/docs/analysis/defect-register.md
(decisions D-001..D-029), modern/specs/MAM-*.md (AC Basis lines) and the test results that build_rule_trace.py reads
(JUnit XML of every module, Cucumber features, web-ui/test-results/vitest.json).
"""
import csv
import importlib.util
import re
from datetime import datetime
from pathlib import Path

REPO = Path(__file__).resolve().parents[3]
M = REPO / "modern"
_spec = importlib.util.spec_from_file_location("brt", Path(__file__).with_name("build_rule_trace.py"))
brt = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(brt)

DEFERRED = {"D-021": "its only AC is in the deferred parity story MAM-91"}


def read_csv(p: Path) -> list[dict]:
    with open(p, encoding="utf-8", newline="") as f:
        return list(csv.DictReader(f))


def cell(text: str) -> str:
    return (text or "").replace("|", "/").replace("\n", " ").strip()


def short(path: str) -> str:
    return Path(path).stem


def decisions() -> list[tuple[str, str, str, str]]:
    """(id, finding, recommended, decision) from the defect register table, in id order."""
    out = []
    for line in (M / "docs/analysis/defect-register.md").read_text(encoding="utf-8").splitlines():
        cols = [c.strip() for c in line.strip().strip("|").split("|")]
        if len(cols) >= 6 and re.fullmatch(r"D-\d{3}", cols[0]):
            out.append((cols[0], cols[1], cols[4], cols[5]))
    return sorted(set(out))


def main() -> None:
    owner = {r["rule_id"]: r for r in read_csv(M / "docs/architecture/rule-to-service.csv")}
    trace = {r["rule_id"]: r for r in read_csv(M / "docs/traceability/rule-trace.csv")}
    kept = sorted(r for r, o in owner.items() if o["service"] != "DROPPED")
    dropped = sorted(r for r, o in owner.items() if o["service"] == "DROPPED")
    sp, results = brt.specs(), brt.tests()

    rows, missing = [], []
    for rid in kept:
        t = trace.get(rid, {})
        tests = [x.strip() for x in (t.get("tests") or "").split(" | ") if x.strip()]
        passing = sum(1 for x in tests if x.endswith("PASSED"))
        story = t.get("story", "")
        result = f"PASS ({passing}/{len(tests)})" if passing else ("NOT RUN" if not tests else f"FAIL ({len(tests)} without a pass)")
        code = " ".join(sorted({short(c) for c in (t.get("code_units") or "").split()}))
        rows.append((rid, t.get("program", ""), owner[rid]["service"], story, t.get("acs_citing_rule", ""),
                     "; ".join(sorted(set(tests))), code, result))
        if not passing:
            if not story:
                why = "no story"
            elif not tests:
                why = f"{story}: no test tagged for the AC(s) {t.get('acs_citing_rule') or '-'} yet (built in the build phase; tests in lane 3 / Part D)"
            else:
                why = f"{story}: tests exist but none passed in the latest results"
            missing.append((rid, why))

    lines = ["# Traceability report: legacy rule -> story -> AC -> test -> code (MAM-89)", "",
             f"Generated {datetime.now():%Y-%m-%d %H:%M} by `modern/tools/trace/build_traceability_report.py`; do not edit by hand.",
             "Test results are those on disk when it ran (JUnit XML of every module, Cucumber features, web-ui vitest.json).", "",
             "## 1. Summary", "",
             f"- Kept (non-DROPPED) legacy rules: {len(kept)}; with a story: {sum(1 for r in rows if r[3])}",
             f"- Rules with at least one passing test: {len(kept) - len(missing)}",
             f"- Rules without a passing test: **{len(missing)}** (target 0); every exception is listed in section 3 for the user to accept",
             f"- DROPPED rules: {len(dropped)} (section 4)", ""]

    lines += ["## 2. Every kept rule", "", "| Rule | Program | Service | Story | AC | Tests | Code | Result |", "|---|---|---|---|---|---|---|---|"]
    lines += [f"| {' | '.join(cell(c) for c in r)} |" for r in rows]

    lines += ["", f"## 3. Rules without a passing test ({len(missing)})", ""]
    if missing:
        by = {}
        for rid, why in missing:
            by.setdefault(why, []).append(rid)
        lines += ["| Reason | Rules |", "|---|---|"]
        lines += [f"| {cell(why)} | {len(rids)}: {', '.join(rids)} |" for why, rids in sorted(by.items())]
    else:
        lines.append("None.")

    lines += ["", f"## 4. DROPPED rules ({len(dropped)})", "", "No story or test is expected for these rules.", "",
              "| Rule | Decision |", "|---|---|"]
    for rid in dropped:
        m = re.search(r"D-\d{3}", owner[rid].get("note", ""))
        lines.append(f"| {rid} | {m.group(0) if m else cell(owner[rid].get('note', ''))} |")
    counts = {}
    for rid in dropped:
        m = re.search(r"D-\d{3}", owner[rid].get("note", ""))
        counts[m.group(0) if m else "?"] = counts.get(m.group(0) if m else "?", 0) + 1
    lines += ["", "By decision: " + ", ".join(f"{k}: {v}" for k, v in sorted(counts.items())), ""]

    lines += ["## 5. Decisions D-001..D-029: every FIX or REPLACE decision traced to a passing AC test", "",
              "| Decision | Finding | Kind | Stories and ACs citing it | Passing tests | Status |", "|---|---|---|---|---|---|"]
    for did, finding, recommended, kind in decisions():
        cites = sorted((k, a) for k, s in sp.items() for a, d in s["acs"].items() if re.search(rf"\b{did}\b", d["basis"]))
        passing = [(k, a, sum(1 for x in results.get((k, a), []) if x.endswith("PASSED"))) for k, a in cites]
        npass = sum(p for *_, p in passing)
        fix = any(x.upper().startswith(("FIX", "REPLACE")) for x in (kind, recommended))
        status = ("exception: " + DEFERRED[did]) if did in DEFERRED else ("OK" if npass else "NO PASSING TEST") if fix else "n/a (not FIX/REPLACE)"
        refs = ", ".join(f"{k} {a}" for k, a in cites) or "-"
        lines.append(f"| {did} | {cell(finding)[:90]} | {cell(kind)[:40]} | {refs} | {npass} | {status} |")

    out = M / "docs/reports/traceability.md"
    out.write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")
    print(f"wrote {out.relative_to(REPO).as_posix()}: {len(kept)} kept rules, {len(kept) - len(missing)} with a passing test, "
          f"{len(missing)} without; {len(dropped)} dropped")


if __name__ == "__main__":
    main()
