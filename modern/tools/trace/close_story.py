"""Definition-of-Done bookkeeping for a story (tdd-workflow section 6), run when the story is closed:

- writes modern/docs/reports/stories/<KEY>/trace.json (story, ACs, test files, test IDs per AC from the latest results,
  code units, rule IDs); an existing trace.json keeps its codeUnits and gains the missing fields;
- sets `testsSha256:` (sha256 of modern/docs/reports/stories/<KEY>.tests.sha256) and `builtCommit: pending-user-commit`
  in modern/specs/<KEY>.md, keeping the file's line endings;
then run build_rule_trace.py to refresh the rule trace.

Usage: modern/kag/.venv/Scripts/python.exe modern/tools/trace/close_story.py MAM-65 MAM-67 [--code path ...]
Code units: given with --code, else kept from an existing trace.json, else the main source files named in the story
report's "Files created/changed" (same matcher as build_checklist.py).
"""
import argparse
import hashlib
import importlib.util
import json
import re
from pathlib import Path

REPO = Path(__file__).resolve().parents[3]
M = REPO / "modern"
STORIES = M / "docs/reports/stories"


def _load(name: str):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(f"{name}.py"))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("keys", nargs="+")
    ap.add_argument("--code", nargs="*", default=None)
    a = ap.parse_args()
    brt, chk = _load("build_rule_trace"), _load("build_checklist")
    specs, results = brt.specs(), brt.tests()
    for key in a.keys:
        sums = STORIES / f"{key}.tests.sha256"
        if not sums.exists():
            raise SystemExit(f"{key}: no {sums.name}; record the tests first")
        test_files = [line.split(None, 1)[1].strip() for line in sums.read_text(encoding="utf-8").splitlines() if line.strip()]
        s = specs[key]
        tj = STORIES / key / "trace.json"
        old = json.loads(tj.read_text(encoding="utf-8")) if tj.exists() else {}
        code = a.code if a.code is not None else old.get("codeUnits") or [
            f"modern/{c}" for c in chk.report(key, chk.specs()[key]["service"])["code"]]
        trace = {
            "story": key,
            "acs": sorted(s["acs"], key=lambda x: int(x.split("-")[1])),
            "tests": test_files,
            "testIds": {ac: sorted(set(results.get((key, ac), []))) for ac in sorted(s["acs"], key=lambda x: int(x.split("-")[1]))},
            "codeUnits": sorted(set(code)),
            "rules": s["rules"],
        }
        tj.parent.mkdir(parents=True, exist_ok=True)
        tj.write_text(json.dumps(trace, indent=2) + "\n", encoding="utf-8", newline="\n")
        digest = hashlib.sha256(sums.read_bytes()).hexdigest()
        sp = M / "specs" / f"{key}.md"
        raw = sp.read_bytes().decode("utf-8")
        nl = "\r\n" if "\r\n" in raw else "\n"
        raw = re.sub(r"^testsSha256:.*$", f"testsSha256: {digest}", raw, count=1, flags=re.M)
        raw = re.sub(r"^builtCommit:.*$", "builtCommit: pending-user-commit", raw, count=1, flags=re.M)
        raw = raw.replace("\r\n", "\n").replace("\n", nl)
        sp.write_bytes(raw.encode("utf-8"))
        npass = sum(1 for ids in trace["testIds"].values() for t in ids if t.endswith("PASSED"))
        print(f"{key}: trace.json ({len(test_files)} test files, {npass} passing test results, {len(trace['codeUnits'])} code units, "
              f"{len(trace['rules'])} rules); spec testsSha256={digest[:12]}..., builtCommit=pending-user-commit")


if __name__ == "__main__":
    main()
