"""Builds modern/docs/traceability/rule-trace.csv: legacy rule -> Jira story -> ACs -> tests (with results) -> code.

Traceability lives in files and in each Jira story's "Legacy trace" section, never in Neo4j (user decision 2026-11-19;
Neo4j holds the legacy context only). Run after every story (tdd-workflow DoD):  python modern/tools/trace/build_rule_trace.py

Inputs: modern/docs/analysis/rule-verification.csv, modern/docs/architecture/rule-to-service.csv, modern/specs/MAM-*.md
(rules, related rules, ACs and their Basis lines), JUnit XML of every module and modern/web-ui/test-results/vitest.json
(tests named "[MAM-n AC-m] ..."), Cucumber features (@MAM-n @AC-m), modern/docs/reports/stories/<KEY>/trace.json (codeUnits).
"""
import csv
import json
import re
import xml.etree.ElementTree as ET
from collections import defaultdict
from pathlib import Path

REPO = Path(__file__).resolve().parents[3]
M = REPO / "modern"
TAG = re.compile(r"\[(MAM-\d+) (AC-\d+(?:\s*[/,]\s*AC-\d+)*)\]")


def tags_of(text: str) -> list:
    """(key, ac) pairs of a test name: "[MAM-n AC-m]" and lists such as "[MAM-n AC-1/AC-2/AC-3]"."""
    return [(key, ac) for key, acs in TAG.findall(text) for ac in re.findall(r"AC-\d+", acs)]
RANGE = re.compile(r"\bMAM-(\d+) AC-(\d+)\.\.AC-(\d+)")


def read_csv(p):
    with open(p, encoding="utf-8", newline="") as f:
        return list(csv.DictReader(f))


def specs():
    out = {}
    for f in sorted((M / "specs").glob("MAM-*.md")):
        t = f.read_text(encoding="utf-8")
        _, fm, body = t.split("---\n", 2)
        meta = dict(l.split(": ", 1) for l in fm.splitlines() if ": " in l)
        lst = lambda v: [x.strip() for x in v.strip("[]").split(",") if x.strip()]
        acs = {}
        for m in re.finditer(r"^### (AC-\d+) (.*?)$(.*?)(?=^### AC-|^## )", body, re.S | re.M):
            basis = re.search(r"^Basis: (.*)$", m.group(3), re.M)
            acs[m.group(1)] = dict(title=m.group(2).strip(), basis=basis.group(1) if basis else "")
        rel = re.search(r"^Related rules: (.+)$", body, re.M)
        out[meta["key"]] = dict(title=meta.get("title", "").strip('"'), rules=lst(meta.get("rules", "")),
                                related=[x.strip() for x in rel.group(1).split(",")] if rel else [],
                                built=bool(meta.get("testsSha256", "").strip()), acs=acs)
    return out


def tests():
    """(key, ac) -> list of 'test name: RESULT'"""
    res = defaultdict(list)
    for xml in M.glob("*/*/build/test-results/test/*.xml"):
        for tc in ET.parse(xml).getroot().iter("testcase"):
            r = "FAILED" if (tc.find("failure") is not None or tc.find("error") is not None) else "SKIPPED" if tc.find("skipped") is not None else "PASSED"
            name = tc.get("name", "")
            for key, ac in tags_of(name):
                res[(key, ac)].append(f"{tc.get('classname', '').rsplit('.', 1)[-1]}: {r}")
            res[("scenario", name)].append(r)
    for f in M.glob("services/*/src/test/resources/features/*.feature"):
        key, tags = None, []
        for line in f.read_text(encoding="utf-8").splitlines():
            s = line.strip()
            if s.startswith("@"):
                tags = s.split()
                key = key or next((t[1:] for t in tags if re.fullmatch(r"@MAM-\d+", t)), None)
            elif s.startswith(("Scenario:", "Scenario Outline:")):
                name = s.split(":", 1)[1].strip()
                r = (res.get(("scenario", name)) or ["NOT RUN"])[0]
                for t in tags:
                    if re.fullmatch(r"@AC-\d+", t) and key:
                        res[(key, t[1:])].append(f"{f.name} '{name}': {r}")
                tags = []
    v = M / "web-ui/test-results/vitest.json"
    if v.exists():
        for r in json.loads(v.read_text(encoding="utf-8")).get("testResults", []):
            for a in r.get("assertionResults", []):
                for key, ac in tags_of(a.get("fullName", "")):
                    res[(key, ac)].append(f"{Path(r['name']).name}: {a['status'].upper()}")
    # pytest JUnit XML of the tool tests (pytest --junitxml=modern/tools/checks/test-results/pytest.xml): names test_mamN_acM_...
    for xml in sorted((M / "tools/checks/test-results").glob("*.xml")):
        for tc in ET.parse(xml).getroot().iter("testcase"):
            r = "FAILED" if (tc.find("failure") is not None or tc.find("error") is not None) else "SKIPPED" if tc.find("skipped") is not None else "PASSED"
            m = re.match(r"test_mam(\d+)_ac(\d+)", tc.get("name", ""))
            if m:
                res[(f"MAM-{m.group(1)}", f"AC-{m.group(2)}")].append(f"{tc.get('classname', '').rsplit('.', 1)[-1]}: {r}")
    # Playwright JSON reports (PLAYWRIGHT_JSON_OUTPUT_NAME=test-results/playwright*.json): titles tagged "[MAM-n AC-m]",
    # journeys titled "MAM-n AC-a..AC-b: ..." (every AC of the range).
    for pj in sorted((M / "web-ui/test-results").glob("playwright*.json")):
        def walk(suite, titles):
            for s in suite.get("suites", []):
                walk(s, titles + [s.get("title", "")])
            for spec in suite.get("specs", []):
                full = " ".join(titles + [spec.get("title", "")])
                last = [r.get("status") for t in spec.get("tests", []) for r in t.get("results", [])[-1:]]
                status = "PASSED" if last and all(x == "passed" for x in last) else "SKIPPED" if last and all(x == "skipped" for x in last) else "FAILED"
                tags = set(tags_of(full))
                for key, a, b in RANGE.findall(full):
                    tags |= {(f"MAM-{key}", f"AC-{n}") for n in range(int(a), int(b) + 1)}
                for key, ac in sorted(tags):
                    res[(key, ac)].append(f"{Path(spec.get('file', '')).name}: {status}")
        walk(json.loads(pj.read_text(encoding="utf-8")), [])
    return res


def main():
    ver = {r["rule_id"]: r for r in read_csv(M / "docs/analysis/rule-verification.csv")}
    owner = {r["rule_id"]: r["service"] for r in read_csv(M / "docs/architecture/rule-to-service.csv")}
    sp, tr = specs(), tests()
    primary, related = {}, defaultdict(list)
    for k, s in sp.items():
        for r in s["rules"]:
            primary[r] = k
        for r in s["related"]:
            related[r].append(k)
    code = {}
    for t in (M / "docs/reports/stories").glob("*/trace.json"):
        d = json.loads(t.read_text(encoding="utf-8"))
        code[d.get("story") or t.parent.name] = d.get("codeUnits", [])
    out = M / "docs/traceability/rule-trace.csv"
    out.parent.mkdir(parents=True, exist_ok=True)
    rows = 0
    with open(out, "w", encoding="utf-8", newline="") as fh:
        w = csv.writer(fh, lineterminator="\n")
        w.writerow(["rule_id", "program", "verification", "owner_service", "story", "story_title", "story_built",
                    "acs_citing_rule", "tests", "tests_passing", "code_units", "related_stories"])
        for rid in sorted(set(ver) | set(owner)):
            svc = owner.get(rid, "")
            k = primary.get(rid, "")
            s = sp.get(k)
            acs = [a for a, d in (s["acs"].items() if s else []) if rid in d["basis"]] or (list(s["acs"]) if s else [])
            t = [x for a in acs for x in tr.get((k, a), [])]
            w.writerow([rid, ver.get(rid, {}).get("program", ""), ver.get(rid, {}).get("status", ""), svc, k,
                        s["title"] if s else ("dropped" if svc == "DROPPED" else ""), "yes" if s and s["built"] else "no",
                        " ".join(acs), " | ".join(t), sum(1 for x in t if x.endswith("PASSED")),
                        " ".join(code.get(k, [])), " ".join(related.get(rid, []))])
            rows += 1
    covered = sum(1 for r in owner if owner[r] != "DROPPED" and r in primary)
    print(f"wrote {out.relative_to(REPO).as_posix()}: {rows} rules; non-dropped rules with a story: {covered} of "
          f"{sum(1 for r in owner if owner[r] != 'DROPPED')}; stories built: {sum(1 for s in sp.values() if s['built'])} of {len(sp)}")


if __name__ == "__main__":
    main()
