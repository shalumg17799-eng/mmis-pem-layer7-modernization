"""load_12_trace: traceability after each story (kag-curator role; tdd-workflow section 6).

Inputs:
  * JUnit XML results of every module: modern/{services,libs}/*/build/test-results/test/*.xml. A test whose display name
    starts with "[<KEY> AC-<n>]" (or carries several "[<KEY> AC-<n>]" tags) VERIFIES that acceptance criterion.
  * Cucumber feature files services/*/src/test/resources/features/<KEY>.feature: each scenario tagged @AC-<n> VERIFIES
    <KEY>:AC-<n>; its result comes from the JUnit XML of the Cucumber suite (matched by scenario name).
  * Web UI: modern/web-ui/test-results/vitest.json (component tests named "[<KEY> AC-<n>] ...").
  * modern/docs/reports/stories/<KEY>/trace.json: {"story", "codeUnits": [fully qualified classes or web-ui files]}.
Creates TestCase {id, level, result, file} -[:VERIFIES]-> AcceptanceCriterion and Story -[:IMPLEMENTED_BY]-> CodeUnit.

Run:  python load_12_trace.py [--dry-run]
"""
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import common  # noqa: E402
from common import GraphData  # noqa: E402

SRC = "build"
LOADER = "load_12_trace.py"
M = common.REPO / "modern"
TAG = re.compile(r"\[(MAM-\d+) (AC-\d+)\]")


def level_of(classname: str) -> str:
    c = classname.lower()
    if ".domain." in c or ".application." in c:
        return "unit"
    if "contract" in c:
        return "contract"
    return "integration"


def junit_results():
    """(module, classname, name, result)"""
    out = []
    for xml in sorted(M.glob("*/*/build/test-results/test/*.xml")):
        module = xml.parts[-6]
        for tc in ET.parse(xml).getroot().iter("testcase"):
            failed = tc.find("failure") is not None or tc.find("error") is not None
            skipped = tc.find("skipped") is not None
            out.append((module, tc.get("classname", ""), tc.get("name", ""), "FAILED" if failed else "SKIPPED" if skipped else "PASSED"))
    return out


def features():
    """(module, key, ac, scenario name, file)"""
    out = []
    for f in sorted(M.glob("services/*/src/test/resources/features/*.feature")):
        module = f.parts[-6]
        key, tags = None, []
        for line in f.read_text(encoding="utf-8").splitlines():
            s = line.strip()
            if s.startswith("@"):
                tags = s.split()
                key = key or next((t[1:] for t in tags if re.fullmatch(r"@MAM-\d+", t)), None)
            elif s.startswith(("Scenario:", "Scenario Outline:")):
                name = s.split(":", 1)[1].strip()
                for t in tags:
                    if re.fullmatch(r"@AC-\d+", t) and key:
                        out.append((module, key, t[1:], name, common.rel_path(f)))
                tags = []
    return out


def vitest_results():
    f = M / "web-ui/test-results/vitest.json"
    if not f.exists():
        return []
    rep = json.loads(f.read_text(encoding="utf-8"))
    return [(Path(r["name"]).name, a.get("fullName") or a.get("title", ""), "PASSED" if a["status"] == "passed" else a["status"].upper())
            for r in rep.get("testResults", []) for a in r.get("assertionResults", [])]


def build() -> GraphData:
    g = GraphData(LOADER)
    known_acs = {k for (lab, k) in __import__("load_11_backlog").build().nodes if lab == "AcceptanceCriterion"}

    def link(tid, key, ac):
        aid = f"{key}:{ac}"
        if aid in known_acs:
            g.rel("VERIFIES", "TestCase", tid, "AcceptanceCriterion", aid, SRC)

    results = junit_results()
    by_name = {(m, n): r for (m, c, n, r) in results}
    for (m, c, n, r) in results:
        tags = TAG.findall(n)
        if tags:
            tid = f"{m}:{c}#{n[:180]}"
            g.node("TestCase", tid, SRC, level=level_of(c), result=r, module=m)
            for key, ac in tags:
                link(tid, key, ac)
    for (m, key, ac, name, path) in features():
        tid = f"{m}:{path.rsplit('/', 1)[-1]}#{name}"
        g.node("TestCase", tid, SRC, level="acceptance", result=by_name.get((m, name), "NOT RUN"), module=m, file=path)
        link(tid, key, ac)
    for (file, name, r) in vitest_results():
        tags = TAG.findall(name)
        if tags:
            tid = f"web-ui:{file}#{name[:180]}"
            g.node("TestCase", tid, SRC, level="component", result=r, module="web-ui")
            for key, ac in tags:
                link(tid, key, ac)
    for t in sorted((M / "docs/reports/stories").glob("*/trace.json")):
        d = json.loads(t.read_text(encoding="utf-8"))
        key = d.get("story") or t.parent.name
        for cu in d.get("codeUnits", []):
            cid = cu if ":" in cu else f"{key}:{cu}"
            g.node("CodeUnit", cid, SRC, path=cu)
            g.rel("IMPLEMENTED_BY", "Story", key, "CodeUnit", cid, SRC)
    g.meta["test cases / code units"] = f"{sum(1 for (l, _) in g.nodes if l == 'TestCase')} / {sum(1 for (l, _) in g.nodes if l == 'CodeUnit')}"
    return g


if __name__ == "__main__":
    sys.exit("load_12_trace is DISABLED: Neo4j holds the legacy context only (user decision 2026-11-19). "
             "Traceability lives in modern/docs/traceability/ (rule-trace.csv) and in each Jira story's Legacy trace.")
