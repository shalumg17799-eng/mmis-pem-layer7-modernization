"""load_03_lineage: which jobs, steps and programs read, write or rewrite which datasets.

Inputs: aws-transform/02-data-analysis/data-lineage/jcl_to_dsn.csv and program_to_dsn.csv.
Record layouts of datasets (USES_LAYOUT) are derived from our own parse: JCL DD -> program SELECT/ASSIGN ->
FD -> record (-> COPY member). Datasets created by load_01 (VSAM definitions, DB2 tables) keep their type.

Run:  python load_03_lineage.py [--dry-run]
"""
import csv
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import common  # noqa: E402
import load_01_source  # noqa: E402
from common import GraphData, record_id, source_model, stem  # noqa: E402

SRC = "aws-transform"
LOADER = "load_03_lineage.py"
ACCESS = {"READ": "READS", "WRITE": "WRITES", "UPDATE": "REWRITES", "CREATE": "WRITES", "DELETE": "WRITES",
          "DEFINE": "WRITES"}
LIN = common.AWS / "02-data-analysis" / "data-lineage"


def rows(name: str):
    with open(LIN / name, encoding="utf-8", newline="") as f:
        return list(csv.DictReader(f))


def rel_types(access: str) -> list[str]:
    out = []
    for t in access.split(";"):
        r = ACCESS.get(t.strip().upper())
        if r and r not in out:
            out.append(r)
    return out


def dataset_type(aws_type: str, dsn: str):
    if aws_type in ("VSAM KSDS", "VSAM AIX"):
        return aws_type
    if aws_type == "GDG Base":
        return "GDG"
    if aws_type == "Dataset":
        if re.search(r"\.(DATA|INDEX|PATH)$", dsn):
            return "VSAM COMPONENT"
        return "TEMP" if "&&" in dsn else "SEQ"
    return None


def layout_for(prog: str, dd: str):
    """Record id describing the data in the dataset a program opens through DD `dd`, or None."""
    m = source_model()
    p = m["programs"].get(prog)
    if not p:
        return None
    sel = next((s for s in p["selects"] if s["assign"] == dd), None)
    recs = [r for r in p["records"] if sel and r.fd == sel["file"]]
    if not recs:
        return None
    r = recs[0]
    if r.length == 0 and len(r.copies) == 1 and r.copies[0] in m["copybooks"]:
        cb = r.copies[0]
        return record_id(cb, m["copybooks"][cb]["records"][0].name)
    return record_id(prog, r.name)


def build() -> GraphData:
    g = GraphData(LOADER)
    m = source_model()
    known = load_01_source.build().nodes  # datasets already typed from the VSAM / DB2 definitions
    jcl_rows, prog_rows = rows("jcl_to_dsn.csv"), rows("program_to_dsn.csv")

    prog_access = {}  # (program, dsn) -> relationship types, straight from program_to_dsn.csv
    for r in prog_rows:
        prog_access.setdefault((stem(r["File Name"]), r["Data Source Name"]), [])
        for t in rel_types(r["Access Type"]):
            if t not in prog_access[(stem(r["File Name"]), r["Data Source Name"])]:
                prog_access[(stem(r["File Name"]), r["Data Source Name"])].append(t)

    dcb = {(j, s["name"], d["dd"]): d["lrecl"] for j, jd in m["jcl"].items() for s in jd["steps"] for d in s["dds"] if d["lrecl"]}
    dd_names, layouts = {}, {}
    for r in jcl_rows:
        dsn = r["Data Source Name"]
        if not dsn:
            continue
        job, step, prog, dd = stem(r["JCL File"]), r["Step Name"], stem(r["Program Name"]), r["DD Name"]
        system = r["System Program"] == "Yes"
        if ("Dataset", dsn) not in known:
            lr = dcb.get((job, step, dd))
            g.node("Dataset", dsn, SRC, type=dataset_type(r["Data Source Type"], dsn), recordLength=lr,
                   temporary=True if "&&" in dsn else None)
        if dd and not system:
            dd_names.setdefault(dsn, set()).add(dd)
            lay = layout_for(prog, dd)
            if lay:
                layouts[dsn] = lay
        types = rel_types(r["Access Type"]) if system else prog_access.get((prog, dsn), [])
        sid = f"{job}:{step}"
        for t in types:
            g.rel(t, "Step", sid, "Dataset", dsn, SRC, ddName=dd or None)
    for dsn, ds in dd_names.items():
        g.set_props("Dataset", dsn, ddName=sorted(ds)[0], ddNames=sorted(ds) if len(ds) > 1 else None) \
            if ("Dataset", dsn) in known else g.node("Dataset", dsn, SRC, ddName=sorted(ds)[0],
                                                     ddNames=sorted(ds) if len(ds) > 1 else None)
    for dsn, rid in layouts.items():
        g.rel("USES_LAYOUT", "Dataset", dsn, "Record", rid, SRC)

    for (prog, dsn), types in prog_access.items():
        for t in types:
            g.rel(t, "Program", prog, "Dataset", dsn, SRC)

    g.meta["jcl_to_dsn rows / program_to_dsn rows"] = f"{len(jcl_rows)} / {len(prog_rows)}"
    g.meta["datasets with a derived record layout"] = len(layouts)
    return g


if __name__ == "__main__":
    common.run(__file__, build)
