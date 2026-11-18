"""Shared plumbing for the PEM knowledge-graph loaders (kag-ontology skill, sections 4 and 5).

* GraphData collects nodes, relationships and update-only changes in memory.
* Every loader exposes build() -> GraphData and ends with  run(__file__, build).
* `--dry-run` prints what WOULD be written and never connects to Neo4j.
* A real run connects ONLY through NEO4J_URI / NEO4J_USERNAME / NEO4J_PASSWORD / NEO4J_DATABASE
  (values are never printed) and writes with idempotent, batched MERGE statements.
"""
from __future__ import annotations

import argparse
import importlib
import os
import sys
from collections import Counter, defaultdict
from datetime import datetime, timezone
from functools import lru_cache
from pathlib import Path

KAG = Path(__file__).resolve().parents[1]
REPO = KAG.parents[1]
SRC = REPO / "source-code"
AWS = REPO / "aws-transform"
AWS_PREFIX = "MMIS-PROVIDER-DEMO-TIER4/"  # in AWS output this means source-code/

ENV_VARS = ("NEO4J_URI", "NEO4J_USERNAME", "NEO4J_PASSWORD", "NEO4J_DATABASE")
FORBIDDEN_DATABASES = {"neo4j", "system"}  # the PEM graph lives only in its dedicated database
BATCH = 500

LOADERS = ["load_01_source", "load_02_dependencies", "load_03_lineage", "load_04_data_dictionary",
           "load_05_business_rules", "load_06_test_plan", "load_07_anti_patterns", "load_08_missing"]

# label -> key property (one uniqueness constraint each, see constraints.cypher)
LABEL_KEYS = {
    "Program": "name", "Paragraph": "id", "Copybook": "name", "Record": "id", "Field": "id", "Dataset": "dsn",
    "Job": "name", "Step": "id", "Transaction": "id", "AntiPattern": "id",
    "BusinessRule": "ruleId", "KeyFunctionality": "id", "Defect": "id", "Question": "id", "Regulation": "id",
    "Service": "name", "ApiEndpoint": "id", "Event": "type", "Table": "id", "Column": "id", "UiScreen": "id",
    "Epic": "key", "Story": "key", "AcceptanceCriterion": "id", "TestCase": "id", "CodeUnit": "id", "Decision": "id",
}


def rel_path(p: Path) -> str:
    """Repo-relative path with forward slashes (e.g. source-code/BATCH/PRVENR01.cbl)."""
    return p.resolve().relative_to(REPO).as_posix()


def aws_to_repo_path(p: str) -> str:
    return "source-code/" + p[len(AWS_PREFIX):] if p.startswith(AWS_PREFIX) else p


def stem(name: str) -> str:
    """PRVENR01.cbl -> PRVENR01"""
    return name.rsplit(".", 1)[0] if "." in name else name


def _clean(props: dict) -> dict:
    out = {}
    for k, v in props.items():
        if v is None:
            continue
        out[k] = list(v) if isinstance(v, (tuple, set)) else v
    return out


class GraphData:
    """In-memory result of one loader: nodes, relationships and update-only property changes."""

    def __init__(self, loader: str):
        self.loader = loader
        self.nodes: dict = {}      # (label, key) -> props
        self.rels: dict = {}       # (type, from_label, from_key, to_label, to_key) -> props
        self.updates: dict = {}    # (label, key) -> props (MATCH ... SET only: never creates a node)
        self.meta: dict = {}       # free-form facts a loader wants to report (not written to the graph)

    def node(self, label: str, key, source: str, **props):
        k = (label, key)
        cur = self.nodes.setdefault(k, {"source": source, "loader": self.loader})
        cur.update(_clean(props))
        cur[LABEL_KEYS[label]] = key
        return key

    def rel(self, rtype: str, fl: str, fk, tl: str, tk, source: str, **props):
        cur = self.rels.setdefault((rtype, fl, fk, tl, tk), {"source": source, "loader": self.loader})
        cur.update(_clean(props))

    def set_props(self, label: str, key, **props):
        self.updates.setdefault((label, key), {}).update(_clean(props))

    def merge(self, other: "GraphData"):
        for k, v in other.nodes.items():
            self.nodes.setdefault(k, {}).update(v)
        for k, v in other.rels.items():
            self.rels.setdefault(k, {}).update(v)
        for k, v in other.updates.items():
            self.updates.setdefault(k, {}).update(v)
        return self

    def node_counts(self) -> Counter:
        return Counter(label for (label, _k) in self.nodes)

    def rel_counts(self) -> Counter:
        return Counter(r[0] for r in self.rels)

    def unresolved(self, universe: "GraphData") -> list:
        """Relationship endpoints and update targets that no loader in `universe` creates."""
        bad = []
        for (rt, fl, fk, tl, tk) in self.rels:
            for lab, key in ((fl, fk), (tl, tk)):
                if (lab, key) not in universe.nodes:
                    bad.append(f"{rt}: missing {lab} '{key}'")
        for (lab, key) in self.updates:
            if (lab, key) not in universe.nodes:
                bad.append(f"update: missing {lab} '{key}'")
        return bad


def universe_upto(name: str) -> GraphData:
    """Union of every loader's build() up to and including `name` (used by dry-run endpoint checks)."""
    u = GraphData("universe")
    for mod in LOADERS:
        u.merge(importlib.import_module(mod).build())
        if mod == name:
            break
    return u


def connect():
    missing = [v for v in ENV_VARS if not os.environ.get(v)]
    if missing:
        sys.exit("Cannot connect: environment variable(s) not set: " + ", ".join(missing))
    db = os.environ["NEO4J_DATABASE"]
    if db.lower() in FORBIDDEN_DATABASES:
        sys.exit("Refusing to write: NEO4J_DATABASE must be the dedicated PEM database, not a default one.")
    from neo4j import GraphDatabase  # imported lazily so --dry-run and the tests need no driver
    driver = GraphDatabase.driver(os.environ["NEO4J_URI"],
                                  auth=(os.environ["NEO4J_USERNAME"], os.environ["NEO4J_PASSWORD"]))
    driver.verify_connectivity()
    return driver, db


def _chunks(rows, n=BATCH):
    for i in range(0, len(rows), n):
        yield rows[i:i + n]


def apply(g: GraphData, driver, database: str) -> dict:
    """Write g with idempotent MERGE statements. Provenance is set on create only, so re-runs change nothing."""
    now = datetime.now(timezone.utc).isoformat(timespec="seconds")
    written = Counter()
    with driver.session(database=database) as s:
        by_label = defaultdict(list)
        for (label, key), props in g.nodes.items():
            p = dict(props)
            src, ldr = p.pop("source"), p.pop("loader")
            by_label[label].append({"key": key, "props": p, "source": src, "loader": ldr})
        for label, rows in by_label.items():
            q = (f"UNWIND $rows AS row MERGE (n:`{label}` {{`{LABEL_KEYS[label]}`: row.key}}) "
                 "ON CREATE SET n.source = row.source, n.loader = row.loader, n.loadedAt = $now "
                 "SET n += row.props RETURN count(n) AS c")
            for ch in _chunks(rows):
                written["node:" + label] += s.run(q, rows=ch, now=now).single()["c"]
        by_rel = defaultdict(list)
        for (rt, fl, fk, tl, tk), props in g.rels.items():
            p = dict(props)
            src, ldr = p.pop("source"), p.pop("loader")
            by_rel[(rt, fl, tl)].append({"from": fk, "to": tk, "props": p, "source": src, "loader": ldr})
        for (rt, fl, tl), rows in by_rel.items():
            q = (f"UNWIND $rows AS row MATCH (a:`{fl}` {{`{LABEL_KEYS[fl]}`: row.from}}) "
                 f"MATCH (b:`{tl}` {{`{LABEL_KEYS[tl]}`: row.to}}) MERGE (a)-[r:`{rt}`]->(b) "
                 "ON CREATE SET r.source = row.source, r.loader = row.loader, r.loadedAt = $now "
                 "SET r += row.props RETURN count(r) AS c")
            n = 0
            for ch in _chunks(rows):
                n += s.run(q, rows=ch, now=now).single()["c"]
            if n != len(rows):
                raise RuntimeError(f"{rt} {fl}->{tl}: {len(rows)} rows but only {n} matched endpoints")
            written["rel:" + rt] += n
        by_upd = defaultdict(list)
        for (label, key), props in g.updates.items():
            by_upd[label].append({"key": key, "props": props})
        for label, rows in by_upd.items():
            q = (f"UNWIND $rows AS row MATCH (n:`{label}` {{`{LABEL_KEYS[label]}`: row.key}}) "
                 "SET n += row.props RETURN count(n) AS c")
            n = 0
            for ch in _chunks(rows):
                n += s.run(q, rows=ch).single()["c"]
            if n != len(rows):
                raise RuntimeError(f"update {label}: {len(rows)} rows but only {n} nodes matched")
            written["update:" + label] += n
    return dict(written)


def print_counts(g: GraphData, title: str):
    print(f"== {title}")
    print("nodes:")
    for k, v in sorted(g.node_counts().items()):
        print(f"  {k:22s}{v:6d}")
    print("relationships:")
    for k, v in sorted(g.rel_counts().items()):
        print(f"  {k:22s}{v:6d}")
    if g.updates:
        print(f"update-only property changes: {len(g.updates)}")
    for k, v in g.meta.items():
        print(f"fact: {k} = {v}")


def run(loader_file: str, build):
    """Entry point shared by every loader: `python load_0x_*.py [--dry-run]`."""
    name = Path(loader_file).stem
    ap = argparse.ArgumentParser(description=f"{name}: load part of the PEM knowledge graph")
    ap.add_argument("--dry-run", action="store_true", help="parse and count only; never connects to Neo4j")
    args = ap.parse_args()
    g = build()
    if args.dry_run:
        print_counts(g, f"{name} --dry-run (no Neo4j connection)")
        bad = g.unresolved(universe_upto(name))
        if bad:
            print(f"UNRESOLVED endpoints: {len(bad)}")
            for b in bad[:15]:
                print("  ", b)
            sys.exit(1)
        print("endpoint check: every relationship endpoint and update target is created by this or an earlier loader")
        return
    driver, db = connect()
    try:
        written = apply(g, driver, db)
    finally:
        driver.close()
    print(f"== {name}: wrote to the dedicated database")
    for k, v in sorted(written.items()):
        print(f"  {k:32s}{v:6d}")


# --------------------------------------------------------------------------- parsed source model (cached)
@lru_cache(maxsize=1)
def source_model() -> dict:
    """Parse every legacy source file once. Read-only: source-code/ is never modified."""
    from parsers import parse_cobol, parse_copybook, parse_ddl, parse_jcl

    def read(p: Path) -> str:
        return p.read_text(encoding="utf-8", errors="replace")

    m = {"programs": {}, "copybooks": {}, "jcl": {}, "ddl": [], "ddl_path": None, "rexx": {}}
    for folder, kind in (("BATCH", "batch"), ("ONLINE", "cics")):
        for p in sorted((SRC / folder).glob("*.cbl")):
            m["programs"][p.stem] = {**parse_cobol(read(p)), "path": rel_path(p), "kind": kind, "text": read(p)}
    for p in sorted((SRC / "COPYBOOKS").glob("*.cpy")):
        m["copybooks"][p.stem] = {**parse_copybook(read(p)), "path": rel_path(p)}
    jcls = sorted((SRC / "JCL").glob("*.jcl")) + sorted((SRC / "JCL" / "PROCS").glob("*.jcl")) + sorted((SRC / "VSAM").glob("*.jcl"))
    for p in jcls:
        m["jcl"][p.stem] = {**parse_jcl(read(p)), "path": rel_path(p), "folder": p.parent.name}
    ddl = SRC / "DB2" / "PRVDDL01.sql"
    m["ddl"], m["ddl_path"] = parse_ddl(read(ddl)), rel_path(ddl)
    for p in sorted((SRC / "REXX").glob("*.rexx")):
        m["rexx"][p.stem] = {"path": rel_path(p), "total_lines": len(read(p).splitlines())}
    return m


def record_id(owner: str, record_name: str) -> str:
    return f"{owner}:{record_name}"


def field_id(rec_id: str, item) -> str:
    return f"{rec_id}:{item.name}" if item.name != "FILLER" else f"{rec_id}:FILLER@L{item.line}"
