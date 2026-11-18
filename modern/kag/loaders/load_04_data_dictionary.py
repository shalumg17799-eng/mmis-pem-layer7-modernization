"""load_04_data_dictionary: business definitions for fields, records and DB2 columns.

Inputs: aws-transform/02-data-analysis/data-dictionary/data_dictionary_{cpy,cbl,ddl}.csv.
These rows only ADD a `definition` to nodes our own parsers created in load_01 (update-only: a dictionary row
that matches no parsed field never creates a node). AWS's lengths and positions are cross-checked against ours.

Run:  python load_04_data_dictionary.py [--dry-run]
"""
import csv
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import common  # noqa: E402
import load_01_source  # noqa: E402
from common import GraphData  # noqa: E402

LOADER = "load_04_data_dictionary.py"
DD = common.AWS / "02-data-analysis" / "data-dictionary"


def read(name):
    with open(DD / name, encoding="utf-8", newline="") as f:
        return list(csv.DictReader(f))


def num(v):
    try:
        return int(v)
    except (TypeError, ValueError):
        return None


def build() -> GraphData:
    g = GraphData(LOADER)
    nodes = load_01_source.build().nodes
    stats = {"rows": 0, "matched": 0, "skipped (88-level conditions)": 0, "unmatched": [], "length differs": [],
             "position differs": []}

    def target(rid, field_name, root):
        if field_name == root and ("Record", rid) in nodes:
            return "Record", rid
        fid = f"{rid}:{field_name}"
        return ("Field", fid) if ("Field", fid) in nodes else None

    for fname in ("data_dictionary_cpy.csv", "data_dictionary_cbl.csv"):
        for r in read(fname):
            stats["rows"] += 1
            if r["field_type"] == "CONDITION":
                stats["skipped (88-level conditions)"] += 1
                continue
            rid = f"{r['data_source_name']}:{r['root_record']}"
            t = target(rid, r["field_name"], r["root_record"])
            if not t:
                stats["unmatched"].append(f"{fname[16:19]}:{rid}:{r['field_name']}")
                continue
            stats["matched"] += 1
            g.set_props(t[0], t[1], definition=r["business_definition"] or None)
            ours = nodes[t]
            if num(r["data_length"]) is not None and ours.get("length") not in (None, 0) and num(r["data_length"]) != ours["length"]:
                stats["length differs"].append(f"{t[1]} aws={r['data_length']} ours={ours['length']}")
            if t[0] == "Field" and num(r["field_position"]) is not None and ours.get("start") and num(r["field_position"]) != ours["start"]:
                stats["position differs"].append(f"{t[1]} aws={r['field_position']} ours={ours['start']}")

    for r in read("data_dictionary_ddl.csv"):
        stats["rows"] += 1
        fid = f"PRVDDL01:{r['schema_name']}.{r['table_name']}:{r['field_name']}"
        if ("Field", fid) not in nodes:
            stats["unmatched"].append("ddl:" + fid)
            continue
        stats["matched"] += 1
        g.set_props("Field", fid, definition=r["business_definition"] or None)

    ddl_cols = sum(1 for (lab, _k), p in nodes.items() if lab == "Field" and p.get("pic") and ":MMPRV." in _k)
    g.unmatched = stats["unmatched"]  # full list, used by tests
    g.meta["dictionary rows / matched to a parsed field or record"] = f"{stats['rows']} / {stats['matched']}"
    g.meta["88-level CONDITION rows skipped"] = stats["skipped (88-level conditions)"]
    g.meta["dictionary rows with no parsed counterpart"] = f"{len(stats['unmatched'])} {stats['unmatched'][:4]}"
    g.meta["AWS length differs from parsed length"] = f"{len(stats['length differs'])} {stats['length differs'][:3]}"
    g.meta["AWS position differs from parsed start"] = f"{len(stats['position differs'])} {stats['position differs'][:3]}"
    g.meta["DB2 columns parsed from the DDL / described by AWS"] = f"{ddl_cols} / {sum(1 for k in g.updates if k[0]=='Field' and ':MMPRV.' in k[1])}"
    return g


if __name__ == "__main__":
    common.run(__file__, build)
