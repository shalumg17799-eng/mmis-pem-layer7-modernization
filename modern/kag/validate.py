"""Validate the PEM knowledge graph: every check is  name | expected | actual | OK.

  python validate.py --offline              compute the checks from the loaders in memory (no Neo4j connection)
  python validate.py                        run the same checks as Cypher against the database (NEO4J_* variables)
  python validate.py --render-cypher        print validate.cypher (kept in sync by tests/test_validate.py)
  python validate.py --idempotency          online: count, run every loader again, count again; fail if anything changed
  python validate.py ... --report FILE      also write the table as markdown

Expected values come from the kag-ontology skill section 6 and from facts established independently of the loaders.
They are NEVER edited to make a check pass: if a count differs, the difference and its reason are reported.
"""
from __future__ import annotations

import argparse
import json
import sys
from datetime import datetime, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent / "loaders"))
import common  # noqa: E402
from common import GraphData  # noqa: E402
import csv  # noqa: E402
import re  # noqa: E402

ANALYSIS = common.REPO / "modern" / "docs" / "analysis"

PER_PROGRAM = {"PRVENR01": 68, "PRVSCR01": 62, "PRVSAN01": 58, "PRVTMS01": 55, "PRVRSK01": 48,
               "PRVCICS1": 45, "PRVDIR01": 29, "PRVREV01": 24, "PRVDEAD1": 14}


def _cy(v):
    return "true" if v is True else "false" if v is False else f"'{v}'" if isinstance(v, str) else str(v)


def nodes_where(name, expected, label, **where):
    w = " AND ".join(f"n.{k} = {_cy(v)}" for k, v in where.items())
    cypher = f"MATCH (n:{label}) {'WHERE ' + w + ' ' if w else ''}RETURN count(n) AS actual"
    return dict(name=name, expected=expected, cypher=cypher,
                py=lambda N, R: sum(1 for (l, _), p in N.items() if l == label and all(p.get(k) == v for k, v in where.items())))


def rels_of(name, expected, rtype):
    return dict(name=name, expected=expected, cypher=f"MATCH ()-[r:{rtype}]->() RETURN count(r) AS actual",
                py=lambda N, R: sum(1 for k in R if k[0] == rtype))


def build_checks() -> list[dict]:
    c = []
    c.append(dict(name="Program nodes, missing:false, kind batch or cics", expected=9,
                  cypher="MATCH (n:Program) WHERE n.missing = false AND n.kind IN ['batch','cics'] RETURN count(n) AS actual",
                  py=lambda N, R: sum(1 for (l, _), p in N.items() if l == "Program" and p.get("missing") is False and p.get("kind") in ("batch", "cics"))))
    c.append(nodes_where("Copybook nodes", 5, "Copybook"))
    c.append(nodes_where("Job nodes with role 'pipeline' (PRVDAY01, PRVMTH01)", 2, "Job", role="pipeline"))
    c.append(nodes_where("Job nodes that are a cataloged proc (PRVSCRPR)", 1, "Job", proc=True))
    c.append(nodes_where("Job nodes in total [explained: 2 pipeline + 1 proc + the 2 VSAM-definition jobs PRVVSM01/02, which the skill's '2 plus proc' leaves out]", 5, "Job"))
    c.append(dict(name="BusinessRule nodes from AWS (ruleId contains -cbl-)", expected=403,
                  cypher="MATCH (r:BusinessRule) WHERE r.ruleId CONTAINS '-cbl-' RETURN count(r) AS actual",
                  py=lambda N, R: sum(1 for (l, k) in N if l == "BusinessRule" and "-cbl-" in k)))
    for prog, n in PER_PROGRAM.items():
        c.append(dict(name=f"AWS BusinessRule nodes for {prog}", expected=n,
                      cypher=f"MATCH (r:BusinessRule {{program: '{prog}'}}) WHERE r.ruleId CONTAINS '-cbl-' RETURN count(r) AS actual",
                      py=lambda N, R, prog=prog: sum(1 for (l, k), p in N.items() if l == "BusinessRule" and "-cbl-" in k and p.get("program") == prog)))
    c.append(dict(name="BusinessRule nodes without IMPLEMENTED_IN", expected=0,
                  cypher="MATCH (r:BusinessRule) WHERE NOT (r)-[:IMPLEMENTED_IN]->(:Program) RETURN count(r) AS actual",
                  py=lambda N, R: sum(1 for (l, k) in N if l == "BusinessRule" and not any(r[0] == "IMPLEMENTED_IN" and r[2] == k for r in R))))
    c.append(nodes_where("Program nodes with missing:true", 4, "Program", missing=True))
    c.append(dict(name="Names of the missing programs", expected="PRVNTGEN,PRVNTRST,PRVNTSUS,PRVNTTRM",
                  cypher="MATCH (p:Program {missing: true}) WITH p.name AS n ORDER BY n RETURN reduce(s = '', x IN collect(n) | s + CASE WHEN s = '' THEN '' ELSE ',' END + x) AS actual",
                  py=lambda N, R: ",".join(sorted(k for (l, k), p in N.items() if l == "Program" and p.get("missing") is True))))
    c.append(nodes_where("AntiPattern nodes", 12, "AntiPattern"))
    # --- additional facts established independently of the loaders (CLAUDE.md sections 5 to 8)
    c.append(dict(name="PRVRECRD record length in bytes (copybook; FDs and IDCAMS say 121)", expected=130,
                  cypher="MATCH (r:Record {id: 'PRVRECRD:PROVIDER-RECORD'}) RETURN r.length AS actual",
                  py=lambda N, R: N[("Record", "PRVRECRD:PROVIDER-RECORD")].get("length")))
    c.append(dict(name="Records whose FD length differs from their layout (7 batch FDs: 121 vs 130)", expected=7,
                  cypher="MATCH (r:Record) WHERE r.declaredLength IS NOT NULL AND r.declaredLength <> r.length RETURN count(r) AS actual",
                  py=lambda N, R: sum(1 for (l, _), p in N.items() if l == "Record" and p.get("declaredLength") is not None and p["declaredLength"] != p.get("length"))))
    c.append(nodes_where("Step nodes (PRVDAY01 5, PRVMTH01 3, PRVVSM01 1, PRVVSM02 1, proc PRVSCRPR 1)", 11, "Step"))
    c.append(rels_of("RUNS relationships (AWS lists 11 'Exec program' edges)", 11, "RUNS"))
    c.append(rels_of("INCLUDES relationships (AWS lists 16 + 2 COPY edges)", 18, "INCLUDES"))
    c.append(rels_of("REDEFINES relationships (PRVRECRD)", 2, "REDEFINES"))
    c.append(nodes_where("KeyFunctionality nodes [explained: AWS repeats the id PRVTMS01:t, so 85 become 84]", 84, "KeyFunctionality"))
    c.append(rels_of("PART_OF relationships [explained: 88 of 403 rules sit in no key functionality in the AWS output]", 315, "PART_OF"))
    c.append(dict(name="AWS BusinessRule nodes without PART_OF", expected=88,
                  cypher="MATCH (r:BusinessRule) WHERE r.ruleId CONTAINS '-cbl-' AND NOT (r)-[:PART_OF]->(:KeyFunctionality) RETURN count(r) AS actual",
                  py=lambda N, R: sum(1 for (l, k) in N if l == "BusinessRule" and "-cbl-" in k and not any(r[0] == "PART_OF" and r[2] == k for r in R))))
    c.append(dict(name="DB2 columns as Field nodes (the DDL defines 26; AWS's dictionary lists 22)", expected=26,
                  cypher="MATCH (f:Field) WHERE f.id STARTS WITH 'PRVDDL01:' RETURN count(f) AS actual",
                  py=lambda N, R: sum(1 for (l, k) in N if l == "Field" and k.startswith("PRVDDL01:"))))
    c.append(dict(name="Programs that have paragraphs (the 9 COBOL programs)", expected=9,
                  cypher="MATCH (p:Program)-[:HAS_PARAGRAPH]->() WITH DISTINCT p RETURN count(p) AS actual",
                  py=lambda N, R: len({r[2] for r in R if r[0] == "HAS_PARAGRAPH"})))
    c.append(dict(name="Transaction PINQ is flagged as missing its CICS definition", expected=True,
                  cypher="MATCH (t:Transaction {id: 'PINQ'}) RETURN t.missingDefinition AS actual",
                  py=lambda N, R: N[("Transaction", "PINQ")].get("missingDefinition")))
    c += phase2_checks()
    return c


def phase2_checks() -> list[dict]:
    """Phase 2: the graph must say exactly what modern/docs/analysis says (expected values are read from those files)."""
    rows = list(csv.DictReader(open(ANALYSIS / "rule-verification.csv", encoding="utf-8", newline="")))
    aws = [r for r in rows if "-cbl-" in r["rule_id"]]
    n_def = sum(1 for l in (ANALYSIS / "defect-register.md").read_text(encoding="utf-8").splitlines() if l.startswith("| D-0"))
    n_q = len(re.findall(r"^## Q-\d{3}\.", (ANALYSIS / "questions.md").read_text(encoding="utf-8"), re.M))
    c = []
    for st in ("VERIFIED", "MISMATCH", "NOT_FOUND", "DROPPED"):
        c.append(dict(name=f"AWS rules with verification {st} (as in rule-verification.csv)", expected=sum(1 for r in aws if r["status"] == st),
                      cypher=f"MATCH (r:BusinessRule) WHERE r.ruleId CONTAINS '-cbl-' AND r.verification = '{st}' RETURN count(r) AS actual",
                      py=lambda N, R, st=st: sum(1 for (l, k), p in N.items() if l == "BusinessRule" and "-cbl-" in k and p.get("verification") == st)))
    c.append(dict(name="Rules still UNVERIFIED", expected=0,
                  cypher="MATCH (r:BusinessRule {verification: 'UNVERIFIED'}) RETURN count(r) AS actual",
                  py=lambda N, R: sum(1 for (l, _), p in N.items() if l == "BusinessRule" and p.get("verification") == "UNVERIFIED")))
    c.append(dict(name="Added rules R-<PROGRAM>-ADD-nn (as in rule-verification.csv)", expected=len(rows) - len(aws),
                  cypher="MATCH (r:BusinessRule) WHERE r.ruleId CONTAINS '-ADD-' RETURN count(r) AS actual",
                  py=lambda N, R: sum(1 for (l, k) in N if l == "BusinessRule" and "-ADD-" in k)))
    c.append(dict(name="VERIFIED or MISMATCH rules without evidence", expected=0,
                  cypher="MATCH (r:BusinessRule) WHERE r.verification IN ['VERIFIED','MISMATCH'] AND coalesce(r.evidence, '') = '' RETURN count(r) AS actual",
                  py=lambda N, R: sum(1 for (l, _), p in N.items() if l == "BusinessRule" and p.get("verification") in ("VERIFIED", "MISMATCH") and not p.get("evidence"))))
    c.append(dict(name="Defect nodes (as in defect-register.md)", expected=n_def, cypher="MATCH (d:Defect) RETURN count(d) AS actual",
                  py=lambda N, R: sum(1 for (l, _) in N if l == "Defect")))
    c.append(dict(name="Defects still OPEN", expected=0, cypher="MATCH (d:Defect {decision: 'OPEN'}) RETURN count(d) AS actual",
                  py=lambda N, R: sum(1 for (l, _), p in N.items() if l == "Defect" and p.get("decision") == "OPEN")))
    c.append(dict(name="Defects that AFFECT nothing", expected=0,
                  cypher="MATCH (d:Defect) WHERE NOT (d)-[:AFFECTS]->() RETURN count(d) AS actual",
                  py=lambda N, R: sum(1 for (l, k) in N if l == "Defect" and not any(r[0] == "AFFECTS" and r[2] == k for r in R))))
    c.append(dict(name="MISMATCH or NOT_FOUND rules with no defect and no question", expected=0,
                  cypher="MATCH (r:BusinessRule) WHERE r.verification IN ['MISMATCH','NOT_FOUND'] AND NOT (r)<-[:AFFECTS]-(:Defect) AND NOT (r)<-[:ABOUT]-(:Question) RETURN count(r) AS actual",
                  py=lambda N, R: sum(1 for (l, k), p in N.items() if l == "BusinessRule" and p.get("verification") in ("MISMATCH", "NOT_FOUND")
                                      and not any(r[0] in ("AFFECTS", "ABOUT") and r[4] == k for r in R))))
    c.append(dict(name="Question nodes (as in questions.md)", expected=n_q, cypher="MATCH (q:Question) RETURN count(q) AS actual",
                  py=lambda N, R: sum(1 for (l, _) in N if l == "Question")))
    c.append(dict(name="Regulations cited by no rule", expected=0,
                  cypher="MATCH (g:Regulation) WHERE NOT (g)<-[:GOVERNED_BY]-() RETURN count(g) AS actual",
                  py=lambda N, R: sum(1 for (l, k) in N if l == "Regulation" and not any(r[0] == "GOVERNED_BY" and r[4] == k for r in R))))
    return c



def render_cypher() -> str:
    out = ["// PEM knowledge graph validation queries. GENERATED by `python validate.py --render-cypher`;",
           "// tests/test_validate.py fails if this file drifts from validate.py. Each query returns one column, `actual`.", ""]
    for ch in build_checks():
        out += [f"// {ch['name']} | expected: {ch['expected']}", ch["cypher"] + ";", ""]
    return "\n".join(out)


def offline_graph():
    u = GraphData("offline")
    for mod in common.LOADERS:
        import importlib
        u.merge(importlib.import_module(mod).build())
    nodes = {k: dict(v) for k, v in u.nodes.items()}
    for k, props in u.updates.items():
        if k in nodes:
            nodes[k].update(props)
    return nodes, u.rels


def evaluate(mode: str) -> list[dict]:
    checks = build_checks()
    if mode == "offline":
        N, R = offline_graph()
        for ch in checks:
            ch["actual"] = ch["py"](N, R)
    else:
        driver, db = common.connect()
        try:
            with driver.session(database=db) as s:
                for ch in checks:
                    rec = s.run(ch["cypher"]).single()
                    ch["actual"] = rec["actual"] if rec else None
        finally:
            driver.close()
    for ch in checks:
        ch["ok"] = ch["actual"] == ch["expected"]
    return checks


def counts_online() -> dict:
    driver, db = common.connect()
    try:
        with driver.session(database=db) as s:
            n = {r["l"]: r["c"] for r in s.run("MATCH (n) UNWIND labels(n) AS l RETURN l, count(*) AS c")}
            r = {r["t"]: r["c"] for r in s.run("MATCH ()-[r]->() RETURN type(r) AS t, count(*) AS c")}
    finally:
        driver.close()
    return {"nodes": dict(sorted(n.items())), "relationships": dict(sorted(r.items()))}


def reload_all():
    """Run every loader again, in order (the same writes as load_all.sh, in this process)."""
    import importlib
    driver, db = common.connect()
    try:
        for mod in common.LOADERS:
            common.apply(importlib.import_module(mod).build(), driver, db)
    finally:
        driver.close()


def table(checks) -> str:
    rows = ["| Check | Expected | Actual | OK |", "|---|---|---|---|"]
    for ch in checks:
        rows.append(f"| {ch['name']} | {ch['expected']} | {ch['actual']} | {'OK' if ch['ok'] else 'DIFFERS'} |")
    return "\n".join(rows)


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--offline", action="store_true")
    ap.add_argument("--render-cypher", action="store_true")
    ap.add_argument("--idempotency", action="store_true", help="re-run every loader and prove no count changes")
    ap.add_argument("--report", metavar="FILE", help="write the check table as markdown")
    a = ap.parse_args()
    if a.render_cypher:
        print(render_cypher())
        return 0
    if a.idempotency:
        before = counts_online()
        reload_all()
        after = counts_online()
        diff = []
        for kind in ("nodes", "relationships"):
            for key in sorted(set(before[kind]) | set(after[kind])):
                if before[kind].get(key) != after[kind].get(key):
                    diff.append(f"{kind[:-1]} {key}: {before[kind].get(key)} -> {after[kind].get(key)}")
        total = sum(before["nodes"].values()), sum(before["relationships"].values())
        print(f"IDEMPOTENT: re-running every loader changed no count ({total[0]} nodes, {total[1]} relationships)"
              if not diff else "CHANGED: " + "; ".join(diff))
        return 0 if not diff else 1
    mode = "offline" if a.offline else "online"
    checks = evaluate(mode)
    bad = [c for c in checks if not c["ok"]]
    for ch in checks:
        print(f"{'OK     ' if ch['ok'] else 'DIFFERS'}  {ch['name'][:92]:92s} expected={ch['expected']!s:>6}  actual={ch['actual']!s:>6}")
    print(f"\n{len(checks) - len(bad)} of {len(checks)} checks match ({mode}).")
    if a.report:
        head = (f"# Knowledge graph validation report\n\nMode: **{mode}**"
                + (" (computed from the loaders in memory; nothing has been loaded into Neo4j)" if mode == "offline"
                   else " (Cypher run against the dedicated database)")
                + f"\nGenerated: {datetime.now(timezone.utc).isoformat(timespec='seconds')}\n\n")
        Path(a.report).write_text(head + table(checks) + "\n", encoding="utf-8")
        print("report written:", a.report)
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
