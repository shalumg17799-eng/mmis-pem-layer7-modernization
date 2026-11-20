"""load_10_architecture: the Phase 3 design (architect and ui-developer roles).

Inputs: modern/docs/architecture/{overview.md, rule-to-service.csv, field-to-column.csv, data-model/*.sql},
modern/contracts/openapi/*.yaml, modern/contracts/asyncapi/events.yaml, modern/docs/adr/ADR-*.md, modern/docs/ux/screen-inventory.md.
Creates Service, ApiEndpoint, Event, Table, Column, Decision, UiScreen and EXPOSES, PUBLISHES, CONSUMES, OWNS, HAS_COLUMN,
OWNED_BY (rule -> service), MAPS_TO (field -> column; a dropped field gets dropped=true), CALLS (screen -> endpoint).

Run:  python load_10_architecture.py [--dry-run]
"""
import csv
import re
import sys
from pathlib import Path

import yaml

sys.path.insert(0, str(Path(__file__).resolve().parent))
import common  # noqa: E402
from common import GraphData  # noqa: E402

SRC = "architect"
LOADER = "load_10_architecture.py"
M = common.REPO / "modern"


def read_csv(p):
    with open(p, encoding="utf-8", newline="") as f:
        return list(csv.DictReader(f))


def services():
    out = {}
    for line in (M / "docs/architecture/overview.md").read_text(encoding="utf-8").splitlines():
        m = re.match(r"^\| ([a-z-]+(?:-service|-gateway|-ui)) \| (\d+) \| ([a-z_]+) \|", line)
        if m:
            out[m.group(1)] = (int(m.group(2)), m.group(3))
    return out


def endpoints():
    out = []
    for f in sorted((M / "contracts/openapi").glob("*.yaml")):
        d = yaml.safe_load(f.read_text(encoding="utf-8"))
        for path, item in d["paths"].items():
            for meth, o in item.items():
                out.append(dict(service=f.stem, method=meth.upper(), path=path, operationId=o["operationId"],
                                roles=o.get("x-roles", []), summary=o.get("summary", "")))
    return out


def tables():
    out = []
    for sql in sorted((M / "docs/architecture/data-model").glob("*.sql")):
        for t, body in re.findall(r"CREATE TABLE (\w+) \((.*?)\n\);", sql.read_text(encoding="utf-8"), re.S):
            cols = [(m.group(1), m.group(2)) for m in re.finditer(r"^\s+([a-z_]+)\s+([A-Z][A-Z0-9(),]*(?: [A-Z]+)?)", body, re.M)]
            out.append((sql.stem, t, cols))
    return out


def screens():
    out = []
    for line in (M / "docs/ux/screen-inventory.md").read_text(encoding="utf-8").splitlines():
        c = [x.strip() for x in line.strip().strip("|").split("|")]
        if len(c) == 7 and c[0] in ("portal", "console", "public"):
            out.append(dict(id=f"{c[0]}.{c[1]}", route=c[2].strip("`"), roles=[r.strip() for r in c[3].split(",")], purpose=c[4],
                            calls=re.findall(r"`((?:GET|POST|PUT|PATCH|DELETE) [^`]+)`", c[5]), serves=c[6]))
    return out


def build() -> GraphData:
    g = GraphData(LOADER)
    svc = services()
    for name, (port, db) in svc.items():
        g.node("Service", name, SRC, port=port, database=db)
    ep_ids = {}
    for e in endpoints():
        eid = f"{e['service']}:{e['method']} {e['path']}"
        g.node("ApiEndpoint", eid, SRC, operationId=e["operationId"], method=e["method"], path=e["path"], roles=e["roles"], summary=e["summary"])
        g.rel("EXPOSES", "Service", e["service"], "ApiEndpoint", eid, SRC)
        ep_ids[f"{e['method']} {e['path']}"] = eid
    ev = yaml.safe_load((M / "contracts/asyncapi/events.yaml").read_text(encoding="utf-8"))
    topics = {name: ch["address"] for name, ch in ev["channels"].items()}
    for name, msg in ev["components"]["messages"].items():
        prod_op = next(o for o in ev["operations"].values() if o["action"] == "send" and o["messages"][0]["$ref"].endswith("/" + name))
        g.node("Event", name, SRC, topic=topics[prod_op["channel"]["$ref"].split("/")[-1]], producer=msg["x-producer"], messageKey=msg.get("x-key"))
        g.rel("PUBLISHES", "Service", msg["x-producer"], "Event", name, SRC)
        for c in msg["x-consumers"]:
            g.rel("CONSUMES", "Service", c, "Event", name, SRC)
    for service, table, cols in tables():
        tid = f"{service}:{table}"
        g.node("Table", tid, SRC, service=service, name=table)
        g.rel("OWNS", "Service", service, "Table", tid, SRC)
        for c, typ in cols:
            g.node("Column", f"{tid}:{c}", SRC, name=c, type=typ)
            g.rel("HAS_COLUMN", "Table", tid, "Column", f"{tid}:{c}", SRC)
    for r in read_csv(M / "docs/architecture/rule-to-service.csv"):
        if r["service"] == "DROPPED":
            g.set_props("BusinessRule", r["rule_id"], owner="DROPPED")
        else:
            g.rel("OWNED_BY", "BusinessRule", r["rule_id"], "Service", r["service"], SRC, storyHint=r["story_hint"], note=r["note"])
    for r in read_csv(M / "docs/architecture/field-to-column.csv"):
        label, key = ("Field", f"{r['record']}:{r['field']}") if r["field"] != r["record"].split(":")[1] else ("Record", r["record"])
        if r["service"] == "DROP":
            g.set_props(label, key, dropped=True, dropReason=r["transform"])
        else:
            g.rel("MAPS_TO", label, key, "Column", f"{r['service']}:{r['table']}:{r['column']}", SRC, transform=r["transform"] or None)
    for f in sorted((M / "docs/adr").glob("ADR-*.md")):
        t = f.read_text(encoding="utf-8")
        st = re.search(r"^Status: (\w+)", t, re.M)
        g.node("Decision", f.name[:7], SRC, title=t.splitlines()[0].split(": ", 1)[1], status=st.group(1) if st else "UNKNOWN", path=common.rel_path(f))
    for s in screens():
        g.node("UiScreen", s["id"], SRC, route=s["route"], roles=s["roles"], purpose=s["purpose"], serves=s["serves"])
        for c in s["calls"]:
            g.rel("CALLS", "UiScreen", s["id"], "ApiEndpoint", ep_ids[c], SRC)
    g.meta["services / endpoints / events / tables"] = f"{len(svc)} / {len(ep_ids)} / {len(ev['components']['messages'])} / {len(tables())}"
    return g


if __name__ == "__main__":
    sys.exit("load_10_architecture is DISABLED: Neo4j holds the legacy context only (user decision 2026-11-19). "
             "Traceability lives in modern/docs/traceability/ (rule-trace.csv) and in each Jira story's Legacy trace.")
