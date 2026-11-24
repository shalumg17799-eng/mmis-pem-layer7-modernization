"""Time marks and the closing report of a change request (HITL change loop, step /fe-approve).

  python modern/tools/changes/fe_report.py mark   <CR-ID> <step> [--base <commit>] [--branch <name>]
  python modern/tools/changes/fe_report.py report <CR-ID> [--gradle :services:x:check ...] [--ui]

`mark` appends a time stamp to `modern/changes/<CR-ID>/state.json` (the first mark is `start`, with the commit the branch was cut
from). `report` writes `modern/changes/<CR-ID>/report.md` from the files of the change and from git: the Jira change (diff.json),
plan against actual (impact.json against `git diff --name-status <base>..HEAD`), the red evidence of the test-writer, the test
results of the affected modules, the test-protection check, the time taken and the git diff summary. It writes no Jira, no code and
does not push. Standard library only.
"""
from __future__ import annotations

import argparse
import datetime as dt
import json
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

sys.dont_write_bytecode = True
sys.path.insert(0, __import__("os").path.dirname(__import__("os").path.abspath(__file__)))  # so that `python -I` finds fe_common

import fe_common as c  # noqa: E402


def _now() -> str:
    return dt.datetime.now().astimezone().isoformat(timespec="seconds")


def mark(changes_dir, cr: str, step: str, base: str | None = None, branch: str | None = None) -> dict:
    p = Path(changes_dir) / cr / "state.json"
    st = c.load_json(p) if p.exists() else {"cr": cr, "marks": []}
    if base:
        st["base"] = base
    if branch:
        st["branch"] = branch
    st["marks"].append({"step": step, "at": _now()})
    c.write_json(p, st)
    return st


def plan_vs_actual(planned: list, touched: list) -> dict:
    p, t = set(planned), set(touched)
    return {"plannedAndTouched": sorted(p & t), "plannedNotTouched": sorted(p - t), "touchedNotPlanned": sorted(t - p)}


def junit_counts(directory) -> dict:
    tot = {"files": 0, "tests": 0, "failures": 0, "errors": 0, "skipped": 0}
    for f in sorted(Path(directory).glob("*.xml")):
        try:
            r = ET.parse(f).getroot()
        except ET.ParseError:
            continue
        tot["files"] += 1
        for k in ("tests", "failures", "errors", "skipped"):
            tot[k] += int(r.get(k) or 0)
    return tot


def _git(*args) -> str:
    return subprocess.run(["git", *args], cwd=c.REPO, capture_output=True, text=True, check=False).stdout


def _red_section(key: str, cr: str) -> str:
    p = c.STORIES / key / "red-report.md"
    if not p.exists():
        return ""
    text = open(p, encoding="utf-8", errors="replace", newline="").read().replace("\r\n", "\n")
    parts = re.split(r"(?m)^(?=## )", text)
    hit = [s for s in parts if cr in s.split("\n", 1)[0]]
    return hit[-1].strip() if hit else ""


def _minutes(a: str, b: str) -> float:
    return round((dt.datetime.fromisoformat(b) - dt.datetime.fromisoformat(a)).total_seconds() / 60, 1)


def build(cr: str, changes_dir=c.CHANGES, gradle=(), ui=False) -> Path:
    out = Path(changes_dir) / cr
    state = c.load_json(out / "state.json") if (out / "state.json").exists() else {"marks": []}
    diffs = c.load_json(out / "diff.json") if (out / "diff.json").exists() else {}
    impact = c.load_json(out / "impact.json") if (out / "impact.json").exists() else {}
    base = state.get("base", "HEAD~1")
    touched = [ln.split("\t")[-1] for ln in _git("diff", "--name-status", f"{base}..HEAD").splitlines() if ln.strip()]
    touched = [t for t in touched if not t.startswith("modern/changes/")]
    planned = []
    for imp in impact.values():
        planned += [t for a in imp["acs"].values() for t in a["tests"]] + imp["productionFiles"]["byRule"] + imp["productionFiles"]["storyLevel"]
    pva = plan_vs_actual(sorted(set(planned)), touched)

    L = [f"# Change report {cr}", "", f"Branch `{state.get('branch', 'cr/' + cr)}` from `{base}`. Written {_now()} by `fe_report.py`. **Local only: no deploy, no push, no Jira change.**", "",
         "## 1. The Jira change", ""]
    for key, d in diffs.items():
        L.append(f"### {key}")
        a = d["acs"]
        L.append(f"- changed ACs: {', '.join(x['id'] for x in a['changed']) or 'none'}; added: {', '.join(x['id'] for x in a['added']) or 'none'}; removed: {', '.join(x['id'] for x in a['removed']) or 'none'}")
        for ch in a["changed"]:
            for f, v in ch["fields"].items():
                L.append(f"  - {ch['id']} {f}: `{v['old'] or '(empty)'}` -> `{v['new'] or '(empty)'}`")
        L.append(f"- Jira timestamp {d.get('jiraUpdated')}; Jira sha256 `{d['jiraSha256'][:16]}...`")
    L += ["", "## 2. Plan against actual", "",
          f"- planned and touched ({len(pva['plannedAndTouched'])}): " + (", ".join(f"`{x}`" for x in pva["plannedAndTouched"]) or "none"),
          f"- planned, not touched ({len(pva['plannedNotTouched'])}): " + (", ".join(f"`{x}`" for x in pva["plannedNotTouched"][:40]) or "none"),
          f"- touched, not planned ({len(pva['touchedNotPlanned'])}): " + (", ".join(f"`{x}`" for x in pva["touchedNotPlanned"]) or "none"), ""]
    L += ["## 3. Every file touched (git `" + base + "..HEAD`, without modern/changes/)", ""] + [f"- `{t}`" for t in touched] + [""]
    L += ["## 4. Red evidence (the test-writer's record for this change)", ""]
    for key in diffs:
        sec = _red_section(key, cr)
        L.append(f"### {key}\n\n" + (sec if sec else f"**MISSING**: no section of `modern/docs/reports/stories/{key}/red-report.md` names {cr}."))
        L.append("")
    L += ["## 5. Green: test results of the affected modules (result files on disk now)", ""]
    for task in gradle:
        m = re.match(r":(services|libs):([^:]+):", task)
        if m:
            d = c.MODERN / m.group(1) / m.group(2) / "build" / "test-results" / "test"
            L.append(f"- `{task}`: {junit_counts(d) if d.exists() else 'no result files'}")
    if ui:
        v = c.MODERN / "web-ui" / "test-results" / "vitest.json"
        if v.exists():
            j = c.load_json(v)
            L.append(f"- web UI (Vitest): {j.get('numPassedTests')} passed of {j.get('numTotalTests')}, {j.get('numFailedTests')} failed")
        else:
            L.append("- web UI (Vitest): no vitest.json on disk")
    L += ["", "## 6. Test protection (no recorded test changed unnoticed)", ""]
    for key in diffs:
        r = subprocess.run([sys.executable, "-I", str(c.MODERN / "tools" / "checks" / "test_protection.py"), "check", key], cwd=c.REPO, capture_output=True, text=True)
        L.append(f"- `{key}`: {(r.stdout + r.stderr).strip().splitlines()[-1] if (r.stdout + r.stderr).strip() else 'no output'}")
    marks = state.get("marks", [])
    L += ["", "## 7. Time taken", ""]
    if len(marks) >= 2:
        L.append(f"Total {_minutes(marks[0]['at'], marks[-1]['at'])} minutes ({marks[0]['at']} to {marks[-1]['at']}).")
        L += [f"- {marks[i]['step']} -> {marks[i + 1]['step']}: {_minutes(marks[i]['at'], marks[i + 1]['at'])} min" for i in range(len(marks) - 1)]
    else:
        L.append("Fewer than two time marks were recorded.")
    L += ["", "## 8. Git diff summary", "", "```", _git("diff", "--stat", f"{base}..HEAD").strip(), "```", ""]
    path = out / "report.md"
    path.write_text("\n".join(L) + "\n", encoding="utf-8", newline="\n")
    return path


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    sub = ap.add_subparsers(dest="cmd", required=True)
    m = sub.add_parser("mark")
    m.add_argument("cr")
    m.add_argument("step")
    m.add_argument("--base")
    m.add_argument("--branch")
    r = sub.add_parser("report")
    r.add_argument("cr")
    r.add_argument("--gradle", nargs="*", default=[])
    r.add_argument("--ui", action="store_true")
    a = ap.parse_args(argv)
    if a.cmd == "mark":
        st = mark(c.CHANGES, a.cr, a.step, a.base, a.branch)
        print(f"{a.cr}: mark '{a.step}' at {st['marks'][-1]['at']}")
    else:
        print(f"report written: {c.rel(build(a.cr, gradle=a.gradle, ui=a.ui))}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
