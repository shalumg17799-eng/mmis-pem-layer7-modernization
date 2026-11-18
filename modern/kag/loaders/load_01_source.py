"""load_01_source: the legacy fact layer parsed straight from source-code/.

Programs, paragraphs, copybooks, records, fields (with byte offsets), jobs, steps, the CICS
transaction, VSAM definitions and the DB2 tables with their columns.
Source of truth: the COBOL / JCL / DDL files, never the AWS output.

Run:  python load_01_source.py [--dry-run]
"""
import re
import sys
from collections import defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import common  # noqa: E402
from common import GraphData, field_id, record_id, source_model  # noqa: E402

SRC = "source-code"
LOADER = "load_01_source.py"
APP2 = {"PRVSAN01", "PRVDIR01"}                       # PEMAPP2: Sanctions & Directory (CLAUDE.md section 3)
PIPELINE_JOBS = {"PRVDAY01", "PRVMTH01"}
UTILITIES = {"SORT", "IEFBR14", "IEBGENER", "IDCAMS"}  # z/OS utilities run by JCL steps


def add_fields(g: GraphData, rid: str, rec):
    ids = {}
    for it in rec.walk():
        fid = field_id(rid, it)
        ids[it.name] = fid
        g.node("Field", fid, SRC, name=it.name, pic=it.pic, start=it.start, length=it.length, level=it.level,
               redefines=it.redefines, values=it.values or None, usage=it.usage, line=it.line, group=it.pic is None)
        g.rel("HAS_FIELD", "Record", rid, "Field", fid, SRC)
    for it in rec.walk():
        if it.redefines and it.redefines in ids:
            g.rel("REDEFINES", "Field", field_id(rid, it), "Field", ids[it.redefines], SRC)


def column_length(col_type: str):
    m = re.match(r"(?:VAR)?CHAR\((\d+)\)", col_type)
    return int(m.group(1)) if m else None


def build() -> GraphData:
    g = GraphData(LOADER)
    m = source_model()
    includes = defaultdict(list)        # (from label, from, to) -> [lines]
    first_record = {}                   # copybook -> (record id, length)

    # --- copybooks, their records and fields
    for name, cb in m["copybooks"].items():
        g.node("Copybook", name, SRC, path=cb["path"], loc=cb["total_lines"])
        for rec in cb["records"]:
            rid = record_id(name, rec.name)
            g.node("Record", rid, SRC, name=rec.name, owner=name, section="COPYBOOK", length=rec.length,
                   nestedCopybooks=rec.copies or None)
            g.rel("DEFINES", "Copybook", name, "Record", rid, SRC)
            add_fields(g, rid, rec)
            first_record.setdefault(name, (rid, rec.length))
        for c in cb["copies"]:
            includes[("Copybook", name, c["name"])].append(c["line"])

    # --- COBOL programs
    declared_mismatch = []
    for name, p in m["programs"].items():
        calls_dynamic = sorted({c["target"] for c in p["calls"] if c["dynamic"]})
        g.node("Program", name, SRC, path=p["path"], kind=p["kind"], app="PEMAPP2" if name in APP2 else "PEMAPP1",
               missing=False, loc=p["total_lines"], codeLines=p["code_lines"], programId=p["program_id"],
               dynamicCallVariables=calls_dynamic or None,
               files=[f"{s['file']}->{s['assign']}" for s in p["selects"]] or None)
        for para in p["paragraphs"]:
            pid = f"{name}:{para['name']}"
            g.node("Paragraph", pid, SRC, program=name, name=para["name"], startLine=para["start"], endLine=para["end"])
            g.rel("HAS_PARAGRAPH", "Program", name, "Paragraph", pid, SRC)
        for c in p["copies"]:
            includes[("Program", name, c["name"])].append(c["line"])
        for rec in p["records"]:
            rid = record_id(name, rec.name)
            length, via = rec.length, None
            if length == 0 and len(rec.copies) == 1 and rec.copies[0] in first_record:
                via = rec.copies[0]
                length = first_record[via][1]       # the copybook's record length (e.g. PRVRECRD = 130)
            g.node("Record", rid, SRC, name=rec.name, owner=name, section=rec.section, length=length,
                   declaredLength=rec.declared_length, fd=rec.fd, viaCopybook=via)
            g.rel("DEFINES", "Program", name, "Record", rid, SRC)
            add_fields(g, rid, rec)
            if rec.declared_length is not None and rec.declared_length != length:
                declared_mismatch.append(f"{rid}: FD says {rec.declared_length}, layout is {length}")
        if p["cics"].get("transid"):
            c = p["cics"]
            g.node("Transaction", c["transid"], SRC, program=name, map=c.get("map"), mapset=c.get("mapset"),
                   file=c.get("file"))
            g.rel("HANDLED_BY", "Transaction", c["transid"], "Program", name, SRC)

    for name, r in m["rexx"].items():
        g.node("Program", name, SRC, path=r["path"], kind="rexx", app="OPS", missing=False, loc=r["total_lines"])

    for (label, frm, to), lines in includes.items():
        g.rel("INCLUDES", label, frm, "Copybook", to, SRC, lines=sorted(set(lines)))

    # --- JCL jobs, procs and steps; IDCAMS-defined datasets
    for name, j in m["jcl"].items():
        role = "proc" if j["kind"] == "proc" else ("pipeline" if name in PIPELINE_JOBS else "vsam-definition")
        g.node("Job", name, SRC, path=j["path"], proc=j["kind"] == "proc", role=role)
        for s in j["steps"]:
            sid = f"{name}:{s['name']}"
            prog = s["program"] or s["proc"]
            g.node("Step", sid, SRC, job=name, name=s["name"], seq=s["seq"], program=prog, condition=s["condition"],
                   line=s["line"])
            g.rel("HAS_STEP", "Job", name, "Step", sid, SRC)
            if prog in UTILITIES:
                g.node("Program", prog, SRC, kind="utility", app="SYSTEM", missing=False)
            g.rel("RUNS", "Step", sid, "Program", prog, SRC)
        for d in j["idcams"]:
            kind = {"CLUSTER": "VSAM KSDS" if d["key_length"] else "VSAM CLUSTER", "ALTERNATEINDEX": "VSAM AIX",
                    "PATH": "VSAM PATH"}[d["kind"]]
            g.node("Dataset", d["name"], SRC, type=kind, recordLength=d["record_size"], keyLength=d["key_length"],
                   keyOffset=d["key_offset"], relatesTo=d["relate"], definedBy=name)

    # --- DB2 tables: legacy columns are Fields too, so Phase 3 must map or drop every one of them
    for t in m["ddl"]:
        rid = record_id("PRVDDL01", t["name"])
        g.node("Dataset", t["name"], SRC, type="DB2 TABLE", definedIn=m["ddl_path"])
        g.node("Record", rid, SRC, name=t["name"], owner="PRVDDL01", section="DB2")
        g.rel("USES_LAYOUT", "Dataset", t["name"], "Record", rid, SRC)
        for col in t["columns"]:
            fid = f"{rid}:{col['name']}"
            g.node("Field", fid, SRC, name=col["name"], pic=col["type"], length=column_length(col["type"]),
                   position=col["position"], line=col["line"], group=False)
            g.rel("HAS_FIELD", "Record", rid, "Field", fid, SRC)

    g.meta["FD declared length differs from layout"] = f"{len(declared_mismatch)} records " + "; ".join(declared_mismatch[:2]) + " ..."
    g.meta["paragraphs"] = sum(len(p["paragraphs"]) for p in m["programs"].values())
    return g


if __name__ == "__main__":
    common.run(__file__, build)
