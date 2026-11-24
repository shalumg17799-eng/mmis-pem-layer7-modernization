"""Impact analysis of a changed Jira story (HITL change loop, step /fe-analyze). Read-only on the repository.

For every acceptance criterion that the diff (fe_diff.py) marks added, changed or removed it finds, from the files that the
earlier phases wrote (the traceability of rule -> story -> AC -> test -> code):
  - the tests that must change, be added or be removed (only tests whose AC changed),
  - the production files likely to change (code of the rules the AC cites, then the rest of the story's code, then the files
    that mention the fields the AC names),
  - the contracts (OpenAPI paths, AsyncAPI events), web UI files, batch jobs (modern batch endpoints and legacy programs)
    and journeys that the AC touches,
  - the services and the web UI whose checks must run, other stories that cite the same rules, and the legacy context of the
    cited rules in the knowledge graph (Neo4j, read-only; reported as "unavailable" when it cannot be reached).
Nothing here edits a file. Standard library only (the Neo4j driver is imported only when a query is made).
"""
from __future__ import annotations

import csv
import functools
import os
import re
import sys

sys.dont_write_bytecode = True
sys.path.insert(0, __import__("os").path.dirname(__import__("os").path.abspath(__file__)))  # so that `python -I` finds fe_common

import fe_common as c  # noqa: E402

TEST_ROOTS = ("services/*/src/test", "libs/*/src/test", "libs/test-support", "web-ui/src", "web-ui/tests", "web-ui/e2e")
TEST_EXT = (".java", ".kt", ".feature", ".ts", ".tsx")
SKIP_DIRS = {"node_modules", "build", ".gradle", "dist", "test-results", "playwright-report", "__pycache__", ".git"}
KAG_DATABASE = "medicaid-kag"   # Q-002; NEO4J_DATABASE is not used because older sessions held a misspelled value

PATH_RE = re.compile(r"/(?:api/v\d+|fhir|actuator)[A-Za-z0-9_/{}.\-]*")
CAMEL_UP = re.compile(r"\b[A-Z][a-z0-9]+(?:[A-Z][a-z0-9]+)+\b")
CAMEL_LOW = re.compile(r"\b[a-z]+(?:[A-Z][a-z0-9]+)+\b")
PROGRAM_RE = re.compile(r"\bPRV[A-Z0-9]{5}\b")
STEP_RE = re.compile(r"\bPRV[A-Z]{3}0\d\s+STEP\d+\b")


def references(text: str) -> dict:
    return {
        "rules": sorted(set(c.RULE_RE.findall(text))),
        "decisions": sorted(set(c.DECISION_RE.findall(text))),
        "questions": sorted(set(c.QUESTION_RE.findall(text))),
        "adrs": sorted(set(c.ADR_RE.findall(text))),
        "journeys": sorted(set(re.findall(r"journeys\.md#(J\d+)", text))),
    }


# ---------------------------------------------------------------- tests
def _walk(root_glob: str):
    for root in c.MODERN.glob(root_glob):
        for dirpath, dirnames, filenames in os.walk(root):
            dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
            for f in filenames:
                if f.endswith(TEST_EXT):
                    yield os.path.join(dirpath, f)


@functools.lru_cache(maxsize=1)
def _test_files() -> tuple:
    seen, out = set(), []
    for g in TEST_ROOTS:
        for p in _walk(g):
            if p not in seen:
                seen.add(p)
                out.append(p)
    return tuple(sorted(out))


def find_tests(key: str, ac_ids: list) -> dict:
    """Test files that cite `<key> AC-n` in a test name (`[MAM-30 AC-4]`, also the range `AC-1..AC-8`) or carry the Cucumber
    tags `@<key>` and `@AC-n`. byAc: AC id -> files. storyLevel: files that cite the key but no AC (shared support)."""
    want = {a for a in ac_ids}
    tag = re.compile(r"(?<![\w-])%s\s+AC-(\d+)(?:\s*\.\.\s*(?:AC-)?(\d+))?" % re.escape(key))
    by_ac = {a: [] for a in want}
    story_level = []
    for p in _test_files():
        try:
            with open(p, encoding="utf-8", errors="replace") as f:
                text = f.read()
        except OSError:
            continue
        if key not in text:
            continue
        acs = set()
        for m in tag.finditer(text):
            lo, hi = int(m.group(1)), int(m.group(2) or m.group(1))
            acs.update(f"AC-{n}" for n in range(lo, hi + 1))
        if p.endswith(".feature") and re.search(r"@%s\b" % re.escape(key), text):
            acs.update(f"AC-{n}" for n in map(int, re.findall(r"@AC-(\d+)\b", text)))
        r = c.rel(p)
        if not acs:
            story_level.append(r)
            continue
        for a in want & acs:
            by_ac[a].append(r)
    return {"byAc": {a: sorted(v) for a, v in by_ac.items()}, "storyLevel": sorted(story_level)}


# ---------------------------------------------------------------- traceability
@functools.lru_cache(maxsize=1)
def _rule_rows() -> dict:
    p = c.MODERN / "docs" / "traceability" / "rule-trace.csv"
    if not p.exists():
        return {}
    with open(p, encoding="utf-8", newline="") as f:
        return {r["rule_id"]: r for r in csv.DictReader(f)}


def _story_trace(key: str) -> dict:
    p = c.STORIES / key / "trace.json"
    return c.load_json(p) if p.exists() else {}


def _spec_rules(front: dict) -> set:
    return set(c.RULE_RE.findall(front.get("rules", "")))


def _contract_hits(tokens_paths: set, tokens_events: set) -> list:
    hits = []
    norm = lambda p: re.sub(r"/(?:\d+|\{[^}]+\})(?=/|$)", "/{}", p.split("?")[0].rstrip("/"))  # noqa: E731
    wanted = {norm(p): p for p in tokens_paths}
    for f in sorted((c.MODERN / "contracts" / "openapi").glob("*.yaml")):
        for n, line in enumerate(f.read_text(encoding="utf-8", errors="replace").split("\n"), 1):
            m = re.match(r"^  (/\S+):\s*$", line)
            if m and norm(m.group(1)) in wanted:
                hits.append({"file": c.rel(f), "line": n, "token": wanted[norm(m.group(1))], "kind": "path"})
    for f in sorted((c.MODERN / "contracts" / "asyncapi").glob("*.yaml")):
        text = f.read_text(encoding="utf-8", errors="replace").split("\n")
        for tok in sorted(tokens_events):
            for n, line in enumerate(text, 1):
                if re.search(r"\b%s\b" % re.escape(tok), line):
                    hits.append({"file": c.rel(f), "line": n, "token": tok, "kind": "event"})
                    break
    return hits


def _ui_hits(identifiers: set, limit: int = 12) -> list:
    root = c.MODERN / "web-ui" / "src"
    if not root.exists() or not identifiers:
        return []
    scored = []
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        for f in filenames:
            if not f.endswith((".ts", ".tsx")) or ".test." in f or f.endswith(".d.ts"):
                continue
            p = os.path.join(dirpath, f)
            with open(p, encoding="utf-8", errors="replace") as fh:
                text = fh.read()
            found = sorted(i for i in identifiers if re.search(r"\b%s\b" % re.escape(i), text))
            if found:
                scored.append((len(found), c.rel(p), found))
    scored.sort(key=lambda x: (-x[0], x[1]))
    return [{"file": f, "names": names} for _, f, names in scored[:limit]]


def _service_of(path: str) -> str | None:
    m = re.match(r"modern/services/([^/]+)/", path)
    return m.group(1) if m else None


def _lib_of(path: str) -> str | None:
    m = re.match(r"modern/libs/([^/]+)/", path)
    return m.group(1) if m else None


# ---------------------------------------------------------------- legacy context (Neo4j, read-only)
def legacy_context(rule_ids: list) -> dict:
    uri, user, pw = os.environ.get("NEO4J_URI"), os.environ.get("NEO4J_USERNAME"), os.environ.get("NEO4J_PASSWORD")
    if not (uri and user and pw):
        return {"unavailable": "NEO4J_URI, NEO4J_USERNAME and NEO4J_PASSWORD are not all set in this environment"}
    if not rule_ids:
        return {"database": KAG_DATABASE, "rules": []}
    db = os.environ.get("PEM_KAG_DATABASE") or KAG_DATABASE
    try:
        from neo4j import GraphDatabase  # imported here: the tool works without the driver
    except ImportError:
        return {"unavailable": "the neo4j Python driver is not installed (use modern/kag/.venv)"}
    cypher = (
        "MATCH (r:BusinessRule) WHERE r.ruleId IN $ids "
        "OPTIONAL MATCH (r)-[:IMPLEMENTED_IN]->(p:Program) OPTIONAL MATCH (d:Defect)-[:AFFECTS]->(r) OPTIONAL MATCH (q:Question)-[:ABOUT]->(r) "
        "RETURN r.ruleId AS id, r.name AS name, r.verification AS verification, r.evidence AS evidence, r.paragraph AS paragraph, "
        "collect(DISTINCT p.name) AS programs, collect(DISTINCT d.id) AS defects, collect(DISTINCT q.id) AS questions ORDER BY id")
    try:
        with GraphDatabase.driver(uri, auth=(user, pw), connection_timeout=8) as drv:
            records, _, _ = drv.execute_query(cypher, ids=list(rule_ids), database_=db, routing_="r")
        return {"database": db, "rules": [dict(r) for r in records]}
    except Exception as e:  # noqa: BLE001 - any connection or query problem is reported, never fatal
        return {"unavailable": f"{type(e).__name__}: {str(e)[:160]}", "database": db}


# ---------------------------------------------------------------- the analysis
def _why(kind: str, entry: dict) -> str:
    if kind == "changed":
        parts = [f"{f}: `{v['old'] or '(empty)'}` -> `{v['new'] or '(empty)'}`" for f, v in entry["fields"].items()]
        return "; ".join(parts)
    if kind == "added":
        return f"new AC: {entry.get('title', '')}"
    return f"AC removed from Jira: {entry.get('title', '')}"


def analyse(key: str, diff: dict, neo4j: bool = True) -> dict:
    spec = c.read_spec(c.spec_path(key))
    spec_sections = c.split_sections(spec.body)
    spec_acs = c.parse_acs(c.section(spec_sections, "Acceptance criteria"))
    tech = c.section(spec_sections, "Technical notes")
    legacy_trace = c.section(spec_sections, "Legacy trace")
    trace = _story_trace(key)
    rows = _rule_rows()

    affected = {}
    for kind, items in (("changed", diff["acs"]["changed"]), ("added", diff["acs"]["added"]), ("removed", diff["acs"]["removed"])):
        for e in items:
            affected[e["id"]] = (kind, e)
    ids = sorted(affected, key=c.ac_number)
    tests = find_tests(key, ids)

    acs, all_text, all_rules = {}, [], set()
    for i in ids:
        kind, e = affected[i]
        old = spec_acs.get(i, {})
        texts = [old.get(f, "") for f in ("title", "given", "when", "then", "basis", "extra")]
        if kind == "changed":
            texts += [v["new"] for v in e["fields"].values()]
        elif kind == "added":
            texts += [e.get(f, "") for f in ("title", "given", "when", "then", "basis", "extra")]
        text = "\n".join(t for t in texts if t)
        refs = references(text)
        all_text.append(text)
        all_rules.update(refs["rules"])
        action = {"changed": "change tests", "added": "add tests", "removed": "remove tests"}[kind]
        acs[i] = {"kind": kind, "action": action, "behaviour": bool(e.get("behaviour", kind != "changed")),
                  "refs": refs, "tests": tests["byAc"].get(i, []), "why": _why(kind, e), "title": e.get("title", "")}
    big = "\n".join(all_text)

    by_rule = sorted({u for r in all_rules for u in rows.get(r, {}).get("code_units", "").split() if u})
    story_units = sorted(u for u in trace.get("codeUnits", []) if u not in by_rule)
    identifiers = {t for t in CAMEL_LOW.findall(big) if len(t) > 5}
    contracts = _contract_hits(set(PATH_RE.findall(big)) | set(PATH_RE.findall(tech)),
                               {t for t in CAMEL_UP.findall(big + "\n" + tech) if len(t) > 6})
    batch_endpoints = sorted(set(re.findall(r"/api/v\d+/batch/[a-z0-9-]+", big + "\n" + tech)))
    programs = sorted(set(PROGRAM_RE.findall(legacy_trace)) | {r.split("-")[1] for r in all_rules if r.split("-")[1].startswith("PRV")})
    job_steps = sorted({m.group(0).strip() for m in STEP_RE.finditer(legacy_trace)})

    journey_ids = set()
    for a in acs.values():
        journey_ids.update(a["refs"]["journeys"])
    journeys = []
    jdir = c.MODERN / "web-ui" / "e2e" / "journeys"
    for jf in sorted(jdir.glob("j*.spec.ts")) if jdir.exists() else []:
        m = re.match(r"j(\d+)-", jf.name)
        jid = f"J{m.group(1)}" if m else jf.stem
        text = jf.read_text(encoding="utf-8", errors="replace")
        why = []
        if jid in journey_ids:
            why.append("cited in the Basis of a changed AC")
        if re.search(r"(?<![\w-])%s\b" % re.escape(key), text):
            why.append(f"the journey test names {key}")
        if spec.front.get("mainJourney") == "true" and jid in ("J1", "J2"):
            why.append("main-journey story: J1 and J2 are the always-run journeys")
        if why:
            journeys.append({"journey": jid, "file": c.rel(jf), "reason": "; ".join(why)})

    all_files = by_rule + story_units + [t for a in acs.values() for t in a["tests"]]
    services = {s for s in (_service_of(f) for f in all_files) if s}
    if spec.front.get("service") and spec.front["service"] not in ("e2e", "platform", "web-ui"):
        services.add(spec.front["service"])
    libs = {x for x in (_lib_of(f) for f in all_files) if x}
    ui_tests = any(f.startswith("modern/web-ui/") for f in all_files)
    checks = {"gradle": sorted(f":services:{s}:check" for s in services) + sorted(f":libs:{l}:check" for l in libs),
              "ui": bool(ui_tests or _ui_hits(identifiers, 1)), "journeys": sorted({j["journey"] for j in journeys})}

    mine = _spec_rules(spec.front)
    shared = sorted(p.stem for p in c.SPECS.glob("MAM-*.md") if p.stem != key and (_spec_rules(c.read_spec(p).front) & all_rules))
    return {
        "key": key, "title": spec.front.get("title", "").strip('"'), "service": spec.front.get("service"), "specVersion": spec.version,
        "acs": acs, "storyLevelTests": tests["storyLevel"],
        "productionFiles": {"byRule": by_rule, "storyLevel": story_units},
        "contracts": contracts,
        "webUi": _ui_hits(identifiers),
        "batch": {"modernEndpoints": batch_endpoints, "legacyPrograms": programs, "jobSteps": job_steps},
        "journeys": journeys, "checks": checks,
        "rules": sorted(all_rules), "storyRules": sorted(mine), "sharedRuleStories": shared,
        "identifiers": sorted(identifiers),
        "legacyContext": legacy_context(sorted(all_rules)) if neo4j else {"unavailable": "not queried (neo4j=False)"},
    }
