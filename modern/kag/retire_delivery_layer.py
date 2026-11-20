"""retire_delivery_layer.py: Neo4j holds the legacy context only (user decision 2026-11-19).

Exports every node of the delivery labels and every relationship touching them to CSV in modern/docs/traceability/
graph-export/, exports the design properties that design loaders had put on legacy nodes (BusinessRule.owner,
Field.dropped, Field.dropReason), then deletes those nodes, properties and the delivery-label constraints.
Prints the node count per label before and after; the legacy labels must keep exactly their counts.

Run:  python retire_delivery_layer.py [--dry-run]     (NEO4J_* environment variables, like the loaders)
"""
import csv
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent / "loaders"))
import common  # noqa: E402

LEGACY = ["Program", "Paragraph", "Copybook", "Record", "Field", "Dataset", "Job", "Step", "Transaction", "AntiPattern",
          "BusinessRule", "KeyFunctionality", "Defect", "Question", "Regulation"]
DELIVERY = ["Epic", "Story", "AcceptanceCriterion", "TestCase", "CodeUnit", "ApiEndpoint", "Event", "Service", "Table",
            "Column", "UiScreen", "Decision"]
DESIGN_PROPS = {"BusinessRule": ["owner"], "Field": ["dropped", "dropReason"]}
OUT = common.REPO / "modern/docs/traceability/graph-export"


def counts(s):
    return {r["l"]: r["c"] for r in s.run("MATCH (n) UNWIND labels(n) AS l RETURN l, count(*) AS c").data()}


def cell(v):
    return json.dumps(v, ensure_ascii=False) if isinstance(v, (list, dict)) else ("" if v is None else str(v))


def main() -> int:
    dry = "--dry-run" in sys.argv
    driver, db = common.connect()
    with driver.session(database=db) as s:
        before = counts(s)
        print("BEFORE " + ", ".join(f"{k} {before[k]}" for k in sorted(before)))
        OUT.mkdir(parents=True, exist_ok=True)
        for lab in DELIVERY:
            key = common.LABEL_KEYS[lab]
            rows = s.run(f"MATCH (n:`{lab}`) RETURN properties(n) AS p ORDER BY n.`{key}`").data()
            cols = [key] + sorted({k for r in rows for k in r["p"]} - {key})
            with open(OUT / f"{lab}.csv", "w", encoding="utf-8", newline="") as fh:
                w = csv.writer(fh, lineterminator="\n")
                w.writerow(cols)
                for r in rows:
                    w.writerow([cell(r["p"].get(c)) for c in cols])
            print(f"exported {len(rows):5d} {lab}")
        rels = s.run(
            "MATCH (a)-[r]->(b) WHERE any(l IN labels(a) WHERE l IN $d) OR any(l IN labels(b) WHERE l IN $d) "
            "RETURN type(r) AS t, labels(a)[0] AS fl, a AS fa, labels(b)[0] AS tl, b AS tb, properties(r) AS p", d=DELIVERY).data()
        with open(OUT / "relationships.csv", "w", encoding="utf-8", newline="") as fh:
            w = csv.writer(fh, lineterminator="\n")
            w.writerow(["type", "from_label", "from_key", "to_label", "to_key", "properties"])
            for r in rels:
                w.writerow([r["t"], r["fl"], r["fa"].get(common.LABEL_KEYS[r["fl"]]), r["tl"], r["tb"].get(common.LABEL_KEYS[r["tl"]]),
                            json.dumps(r["p"], ensure_ascii=False, default=str)])
        print(f"exported {len(rels):5d} relationships touching the delivery labels")
        with open(OUT / "legacy-design-properties.csv", "w", encoding="utf-8", newline="") as fh:
            w = csv.writer(fh, lineterminator="\n")
            w.writerow(["label", "key", "property", "value"])
            n = 0
            for lab, props in DESIGN_PROPS.items():
                key = common.LABEL_KEYS[lab]
                for p in props:
                    for r in s.run(f"MATCH (n:`{lab}`) WHERE n.`{p}` IS NOT NULL RETURN n.`{key}` AS k, n.`{p}` AS v ORDER BY k").data():
                        w.writerow([lab, r["k"], p, cell(r["v"])])
                        n += 1
        print(f"exported {n:5d} design properties on legacy nodes")
        if dry:
            print("dry run: nothing deleted")
            return 0
        for lab in DELIVERY:
            s.run(f"MATCH (n:`{lab}`) CALL (n) {{ DETACH DELETE n }} IN TRANSACTIONS OF 500 ROWS").consume()
        for lab, props in DESIGN_PROPS.items():
            for p in props:
                s.run(f"MATCH (n:`{lab}`) WHERE n.`{p}` IS NOT NULL REMOVE n.`{p}`").consume()
        for c in s.run("SHOW CONSTRAINTS YIELD name, labelsOrTypes RETURN name, labelsOrTypes").data():
            if any(l in DELIVERY for l in (c["labelsOrTypes"] or [])):
                s.run(f"DROP CONSTRAINT `{c['name']}` IF EXISTS").consume()
        after = counts(s)
        print("AFTER  " + ", ".join(f"{k} {after[k]}" for k in sorted(after)))
        changed = [l for l in LEGACY if before.get(l) != after.get(l)]
        left = [l for l in after if l not in LEGACY]
        print("relationships now:", s.run("MATCH ()-[r]->() RETURN count(r) AS c").single()["c"])
        print("legacy counts unchanged:", not changed, changed or "", "| non-legacy labels left:", left or "none")
    driver.close()
    return 0 if not changed and not left else 1


if __name__ == "__main__":
    sys.exit(main())
