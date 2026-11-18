"""load_08_missing: everything the estate references but the AWS input did not contain.

Input: aws-transform/01-code-analysis/missing-dependencies.csv (21 rows: Name, Type, Path, Parents, Link Types).
  * Missing Program (4)  -> Program with missing=true, plus the dynamic CALLS edge from the caller.
  * Missing CICS / BMS   -> properties on Transaction PINQ (missingDefinition, mapsetMissing, fileDefinitionMissing).
  * Missing Dataset (6)  -> missing=true on the Dataset (created here if lineage did not have it, e.g. the GDG +1 generation).
  * Missing Source File  -> DD names a program opens that no JCL in the estate defines: Program.unresolvedDDs.

Run:  python load_08_missing.py [--dry-run]
"""
import csv
import sys
from collections import Counter, defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import common  # noqa: E402
from common import GraphData, stem  # noqa: E402

SRC = "aws-transform"
LOADER = "load_08_missing.py"


def build() -> GraphData:
    g = GraphData(LOADER)
    with open(common.AWS / "01-code-analysis" / "missing-dependencies.csv", encoding="utf-8", newline="") as f:
        rows = list(csv.DictReader(f))
    unresolved = defaultdict(list)
    for r in rows:
        name, typ = r["Name"], r["Type"]
        parents = [stem(p.strip()) for p in r["Parents"].split(",") if p.strip()]
        if typ == "Missing Program":
            g.node("Program", name, SRC, kind="missing", missing=True, app="UNKNOWN",
                   missingReason="called dynamically by " + ", ".join(parents))
            for p in parents:
                g.rel("CALLS", "Program", p, "Program", name, SRC, dynamic=True, via="WS-NOTICE-PGM" if p == "PRVSAN01" else None)
        elif typ == "Missing CICS Transaction Definition":
            g.set_props("Transaction", name, missingDefinition=True)
        elif typ == "Missing BMS":
            g.set_props("Transaction", "PINQ", mapsetMissing=True, missingMapset=name)
        elif typ == "Missing CICS Dataset Definition":
            g.set_props("Transaction", "PINQ", fileDefinitionMissing=True, missingFileDefinition=name)
        elif typ == "Missing Dataset":
            g.node("Dataset", name, SRC, missing=True, type="GDG GENERATION" if "(+" in name else None,
                   missingReason="referenced by " + ", ".join(parents))
        elif typ == "Missing Source File":
            for p in parents:
                unresolved[p].append(name)
    for prog, dds in unresolved.items():
        g.set_props("Program", prog, unresolvedDDs=sorted(set(dds)))
    g.meta["missing-dependencies.csv rows by type"] = dict(Counter(r["Type"] for r in rows))
    return g


if __name__ == "__main__":
    common.run(__file__, build)
