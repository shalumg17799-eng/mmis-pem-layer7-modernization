"""load_07_anti_patterns: the 12 seeded anti-patterns (README of the estate, CLAUDE.md section 6).

Line numbers are NOT hard-coded: each pattern is located in the source with an explicit regex, so a drift between
the documentation and the code shows up as a failing test (tests/test_loaders.py) rather than a wrong graph.
HAS_ANTIPATTERN goes from the affected Program, Copybook, Job or Dataset (the ontology only names Program; the
others are an additive extension, see README).

Run:  python load_07_anti_patterns.py [--dry-run]
"""
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import common  # noqa: E402
from common import GraphData, source_model  # noqa: E402
from parsers import code_lines  # noqa: E402

SRC = "source-code"
LOADER = "load_07_anti_patterns.py"


def cobol_hits(prog_or_cpy: str, *patterns: str) -> list[int]:
    m = source_model()
    text = (m["programs"].get(prog_or_cpy) or {}).get("text")
    if text is None:
        text = (common.SRC / m["copybooks"][prog_or_cpy]["path"].split("/", 1)[1]).read_text(encoding="utf-8", errors="replace")
    return sorted({ln for ln, c in code_lines(text) for p in patterns if re.search(p, c.strip())})


def raw_hits(rel_path: str, pattern: str, skip=("//*", "--")) -> list[int]:
    text = (common.REPO / rel_path).read_text(encoding="utf-8", errors="replace")
    return [i for i, l in enumerate(text.splitlines(), 1) if re.search(pattern, l) and not l.startswith(skip)]


def para_range(prog: str, name: str) -> str:
    p = next(x for x in source_model()["programs"][prog]["paragraphs"] if x["name"] == name)
    return f"{p['start']}-{p['end']}"


def patterns() -> list[dict]:
    jcl = source_model()["jcl"]["PRVSCRPR"]["path"]
    ddl = source_model()["ddl_path"]
    return [
        dict(id="AP-01", name="Paragraph fall-through", program="PRVENR01", label="Program", key="PRVENR01",
             lines=[*cobol_hits("PRVENR01", r"GO TO INVALID-APP", r"^INVALID-APP\.")]),
        dict(id="AP-02", name="Dead code: flag never set", program="PRVSCR01", label="Program", key="PRVSCR01",
             lines=cobol_hits("PRVSCR01", r"WS-RECIPROCITY-ENABLED\s+PIC", r"IF WS-RECIPROCITY-ON", r"^CHECK-MEDICARE-RECIPROCITY\.")),
        dict(id="AP-03", name="ALTER statement", program="PRVRSK01", label="Program", key="PRVRSK01",
             lines=cobol_hits("PRVRSK01", r"ALTER APPLY-TIER-PARA", r"^APPLY-TIER-PARA\.")),
        dict(id="AP-04", name="2-digit-year arithmetic", program="PRVRSK01", label="Program", key="PRVRSK01",
             lines=[para_range("PRVRSK01", "SET-REVALIDATION-DATE"), *cobol_hits("PRVRSK01", r"IF WS-DUE-YY < 70")]),
        dict(id="AP-05", name="Dynamic CALL", program="PRVSAN01", label="Program", key="PRVSAN01",
             lines=cobol_hits("PRVSAN01", r"CALL WS-NOTICE-PGM")),
        dict(id="AP-06", name="Cross-app VSAM coupling", program="PRVSAN01", label="Program", key="PRVSAN01",
             also=[("Program", "PRVDIR01")],
             lines=cobol_hits("PRVSAN01", r"ASSIGN TO PRVMSTR", r"REWRITE PRV-MASTER-RECORD")),
        dict(id="AP-07", name="YYDDD Julian dates", program="PRVTMS01", label="Program", key="PRVTMS01",
             lines=cobol_hits("PRVTMS01", r"ACCEPT WS-TODAY-JUL FROM DAY", r"IF WS-ENROLL-YY > WS-TODAY-YY")),
        dict(id="AP-08", name="Orphan program and dead flag", program="PRVDEAD1", label="Program", key="PRVDEAD1",
             lines=cobol_hits("PRVDEAD1", r"WS-PAPER-FP-ENABLED\s+PIC", r"IF WS-PAPER-FP-ON")),
        dict(id="AP-09", name="Nested copybook", program="WRKSTORE", label="Copybook", key="WRKSTORE",
             lines=cobol_hits("WRKSTORE", r"^COPY (SCRNREC|ERRCODES)")),
        dict(id="AP-10", name="REDEFINES across 3 provider types", program="PRVRECRD", label="Copybook", key="PRVRECRD",
             lines=cobol_hits("PRVRECRD", r"\bREDEFINES\b")),
        dict(id="AP-11", name="Symbolic-parameter override", program="PRVSCRPR", label="Job", key="PRVSCRPR",
             lines=raw_hits(jcl, r"&(HLQ|DTE)")),
        dict(id="AP-12", name="Dual system of record", program="PRVDDL01 / PRVVSM01", label="Dataset", key="MMPRV.PROVIDER",
             also=[("Dataset", "MMPRV.PROVIDER.MASTER.KSDS")],
             lines=raw_hits(ddl, r"CREATE TABLE MMPRV\.PROVIDER \(")),
    ]


def build() -> GraphData:
    g = GraphData(LOADER)
    for ap in patterns():
        g.node("AntiPattern", ap["id"], SRC, name=ap["name"], program=ap["program"], lines=[str(x) for x in ap["lines"]])
        for label, key in [(ap["label"], ap["key"]), *ap.get("also", [])]:
            g.rel("HAS_ANTIPATTERN", label, key, "AntiPattern", ap["id"], SRC)
    return g


if __name__ == "__main__":
    common.run(__file__, build)
