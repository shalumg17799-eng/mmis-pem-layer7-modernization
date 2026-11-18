"""load_02_dependencies: cross-check the parsed source against the AWS dependency graph.

AWS (aws-transform/03-decomposition/dependency-graph.json) and our parsers (load_01) are two independent
views of the same estate. This loader marks every COPY edge both agree on (`confirmedByAws`), creates any
edge only AWS has (`onlyInAws`), and records the disagreements as facts. CALL edges are loaded by
load_08_missing together with the programs they target (all of them are missing).

Run:  python load_02_dependencies.py [--dry-run]
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import common  # noqa: E402
from common import GraphData, source_model, stem  # noqa: E402

SRC = "aws-transform"
LOADER = "load_02_dependencies.py"


def build() -> GraphData:
    g = GraphData(LOADER)
    graph = json.loads((common.AWS / "03-decomposition" / "dependency-graph.json").read_text(encoding="utf-8"))
    m = source_model()

    parsed_inc = {("Program", p, c["name"]) for p, d in m["programs"].items() for c in d["copies"]}
    parsed_inc |= {("Copybook", n, c["name"]) for n, d in m["copybooks"].items() for c in d["copies"]}

    aws_inc = set()
    for e in graph:
        if e["type"] not in ("COB", "CPY"):
            continue
        for d in e["dependencies"]:
            if d["dependencyType"] == "Copy" and d["type"] == "CPY":
                aws_inc.add(("Program" if e["type"] == "COB" else "Copybook", stem(e["name"]), stem(d["name"])))

    for label, frm, to in sorted(aws_inc):
        if (label, frm, to) in parsed_inc:
            g.rel("INCLUDES", label, frm, "Copybook", to, SRC, confirmedByAws=True)
        else:
            g.rel("INCLUDES", label, frm, "Copybook", to, SRC, onlyInAws=True)

    # step-level "Exec program" edges: AWS has job -> program, our parse has job:step -> program
    aws_exec = {(stem(e["name"]), stem(d["name"])) for e in graph if e["type"] in ("JCL", "PROC")
                for d in e["dependencies"] if d["dependencyType"] == "Exec program"}
    parsed_exec = {(j, (s["program"] or s["proc"])) for j, d in m["jcl"].items() for s in d["steps"]}

    g.meta["COPY edges: parsed / AWS / only parsed / only AWS"] = (
        f"{len(parsed_inc)} / {len(aws_inc)} / {len(parsed_inc - aws_inc)} / {len(aws_inc - parsed_inc)}")
    g.meta["EXEC edges (job -> program): parsed / AWS / only parsed / only AWS"] = (
        f"{len(parsed_exec)} / {len(aws_exec)} / {len(parsed_exec - aws_exec)} / {len(aws_exec - parsed_exec)}")
    g.meta["AWS graph entries"] = len(graph)
    return g


if __name__ == "__main__":
    common.run(__file__, build)
