"""load_11_backlog: the Phase 4 backlog (backlog-planner role), LOCAL mode or Jira.

Inputs: modern/docs/backlog/backlog-draft.md (the "## Epics" table) and modern/specs/*.md (one spec per story or task:
frontmatter + the story description with its acceptance criteria).
Creates Epic, Story, AcceptanceCriterion and CONTAINS (epic -> story), HAS_AC (story -> AC), DERIVED_FROM (story -> rule;
primary=true for the rules in the spec's `rules`, primary=false for "Related rules"). Story dependencies, decisions and
labels are Story properties (no new relationship types).

Run:  python load_11_backlog.py [--dry-run]
"""
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import common  # noqa: E402
from common import GraphData  # noqa: E402

SRC = "backlog-planner"
LOADER = "load_11_backlog.py"
M = common.REPO / "modern"
AC_HEAD = re.compile(r"^### (AC-\d+) (.*)$")


def epics():
    out = []
    text = (M / "docs/backlog/backlog-draft.md").read_text(encoding="utf-8")
    sec = text.split("## Epics", 1)[1].split("\n## ", 1)[0]
    for line in sec.splitlines():
        c = [x.strip() for x in line.strip().strip("|").split("|")]
        if len(c) >= 3 and re.fullmatch(r"L-E\d{2}|[A-Z][A-Z0-9]+-\d+", c[0]):
            out.append(dict(key=c[0], title=c[1], area=c[2]))
    return out


def parse_list(v: str):
    v = v.strip()
    return [x.strip() for x in v.strip("[]").split(",") if x.strip()] if v.startswith("[") else ([v] if v else [])


def specs():
    out = []
    for f in sorted((M / "specs").glob("*.md")):
        t = f.read_text(encoding="utf-8")
        m = re.match(r"---\n(.*?)\n---\n(.*)\Z", t, re.S)
        if not m:
            continue
        fm = {}
        for line in m.group(1).splitlines():
            if ":" in line and not line.startswith(" "):
                k, v = line.split(":", 1)
                fm[k.strip()] = v.split("  #", 1)[0].strip()
        body = m.group(2)
        acs, cur = [], None
        for line in body.splitlines():
            h = AC_HEAD.match(line)
            if h:
                cur = dict(n=h.group(1), title=h.group(2).strip(), lines=[])
                acs.append(cur)
            elif line.startswith("## "):
                cur = None
            elif cur is not None and line.strip():
                cur["lines"].append(line.strip())
        related = re.search(r"^Related rules: (.+)$", body, re.M)
        out.append(dict(key=fm["key"], epic=fm["epic"], service=fm.get("service"), type=fm.get("type", "Story"),
                        title=fm.get("title", "").strip('"'), slug=fm.get("slug"), specVersion=int(fm.get("specVersion", 1)),
                        sha=fm.get("descriptionSha256"), rules=parse_list(fm.get("rules", "")),
                        related=[x.strip() for x in related.group(1).split(",")] if related else [],
                        decisions=parse_list(fm.get("decisions", "")), dependsOn=parse_list(fm.get("dependsOn", "")),
                        mainJourney=fm.get("mainJourney") == "true", labels=parse_list(fm.get("labels", "")), acs=acs,
                        path=common.rel_path(f)))
    return out


def build() -> GraphData:
    g = GraphData(LOADER)
    for e in epics():
        g.node("Epic", e["key"], SRC, title=e["title"], area=e["area"])
    n_ac = 0
    for s in specs():
        g.node("Story", s["key"], SRC, title=s["title"], specVersion=s["specVersion"], service=s["service"], type=s["type"],
               slug=s["slug"], mainJourney=s["mainJourney"], dependsOn=s["dependsOn"], decisions=s["decisions"],
               labels=s["labels"], descriptionSha256=s["sha"], specPath=s["path"])
        g.rel("CONTAINS", "Epic", s["epic"], "Story", s["key"], SRC)
        for a in s["acs"]:
            text = " ".join(x for x in a["lines"] if not x.startswith("Basis:"))
            basis = next((x[len("Basis:"):].strip() for x in a["lines"] if x.startswith("Basis:")), "")
            aid = f"{s['key']}:{a['n']}"
            g.node("AcceptanceCriterion", aid, SRC, title=a["title"], text=text, basis=basis)
            g.rel("HAS_AC", "Story", s["key"], "AcceptanceCriterion", aid, SRC)
            n_ac += 1
        for r in s["rules"]:
            g.rel("DERIVED_FROM", "Story", s["key"], "BusinessRule", r, SRC, primary=True)
        for r in s["related"]:
            if r not in s["rules"]:
                g.rel("DERIVED_FROM", "Story", s["key"], "BusinessRule", r, SRC, primary=False)
    g.meta["epics / items / ACs"] = f"{len(epics())} / {len(specs())} / {n_ac}"
    return g


if __name__ == "__main__":
    sys.exit("load_11_backlog is DISABLED: Neo4j holds the legacy context only (user decision 2026-11-19). "
             "Traceability lives in modern/docs/traceability/ (rule-trace.csv) and in each Jira story's Legacy trace.")
