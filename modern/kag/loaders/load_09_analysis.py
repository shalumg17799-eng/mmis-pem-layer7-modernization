"""load_09_analysis: the Phase 2 legacy analysis (written by the legacy-analyst role).

Inputs (all in modern/docs/analysis/):
  * rule-verification.csv  -> verification status, evidence and note on every AWS rule; the added rules (R-<PROGRAM>-ADD-nn)
  * defect-register.md     -> Defect nodes, AFFECTS the programs / jobs / copybooks / datasets in their evidence and every rule
                              whose note cites them
  * questions.md           -> Question nodes, ABOUT the defects and rules they name
Regulation nodes (42 CFR 455.x) come from the citations inside the rule texts, linked with GOVERNED_BY.

Run:  python load_09_analysis.py [--dry-run]
"""
import csv
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import common  # noqa: E402
import load_05_business_rules  # noqa: E402
from common import GraphData  # noqa: E402

SRC = "legacy-analyst"
LOADER = "load_09_analysis.py"
A = common.REPO / "modern" / "docs" / "analysis"
REG_TITLES = {  # summaries from the pem-domain skill, section 5 (not quoted from eCFR)
    "455.410": "Providers must be screened and enrolled", "455.414": "Revalidate every provider at least every 5 years",
    "455.416": "Grounds to deny or terminate enrolment", "455.436": "Check the federal databases (SSA DMF, NPPES, LEIE, SAM)",
    "455.450": "Screening by risk level (limited, moderate, high)"}
SPECIAL_DECISION = {"D-006": "FIX", "D-007": "FIX", "D-008": "PRESERVE", "D-019": "FIX", "D-025": "PRESERVE", "D-027": "PRESERVE"}
RULE_ID = re.compile(r"R-PRV[A-Z0-9]{5}-(?:cbl|ADD)-\d+")


def folder(prog: str) -> str:
    return "ONLINE" if prog == "PRVCICS1" else "BATCH"


def defects():
    out = []
    for line in (A / "defect-register.md").read_text(encoding="utf-8").splitlines():
        if line.startswith("| D-0"):
            c = [x.strip() for x in line.strip().strip("|").split("|")]
            out.append(dict(id=c[0], title=c[1], evidence=c[2], legacy=c[3], recommended=c[4], decisionText=c[5], by=c[6], on=c[7]))
    return out


def decision(d) -> str:
    t = d["decisionText"].upper()
    for k in ("REPLACE", "PRESERVE", "DROP", "FIX"):
        if t.startswith(k):
            return k
    return SPECIAL_DECISION.get(d["id"], "OPEN")


def targets(evidence: str):
    t = set()
    for m in re.finditer(r"(PRV[A-Z0-9]{5})\.cbl", evidence):
        t.add(("Program", m.group(1)))
    for m in re.finditer(r"(?:JCL|VSAM|PROCS)/(?:PROCS/)?([A-Z0-9]+)\.jcl", evidence):
        t.add(("Job", m.group(1)))
    for m in re.finditer(r"COPYBOOKS/([A-Z0-9]+)\.cpy", evidence):
        t.add(("Copybook", m.group(1)))
    if "DB2/PRVDDL01.sql" in evidence:
        t.add(("Dataset", "MMPRV.PROVIDER"))
    return t


def questions():
    text = (A / "questions.md").read_text(encoding="utf-8")
    out = []
    for block in re.split(r"\n(?=## Q-\d{3}\.)", text):
        head = block.splitlines()[0] if block.startswith("## Q-") else ""
        m = (re.match(r"## (Q-\d{3})\. (.+?) \(raised [^,]+, status: (.+)\)\s*$", head)
             # later headings put the context first: "## Q-nnn. (context, date) title (status: ...)"
             or re.match(r"## (Q-\d{3})\. \([^()]*\) (.+?) \(status: (.+)\)\s*$", head))
        if not m:
            continue
        st = m.group(3).upper()
        status = "ANSWERED" if "ANSWERED" in st else "DEFERRED" if "DEFAULT APPLIED" in st else "OPEN"
        rec = next((re.sub(r"\*\*", "", l).strip() for l in block.splitlines() if "**Recommended" in l), "")
        ans = next((re.sub(r"\*\*", "", l).strip() for l in block.splitlines() if l.startswith(("**Answer", "**Final answer"))), "")
        opts = [l.strip() for l in block.splitlines() if l.startswith(("Options:", "| A", "| B", "| C", "| **A"))]
        about = set(re.findall(r"\bD-0\d\d\b", block))
        rules = []
        for line in block.splitlines():
            if "Affected rules" in line or RULE_ID.search(line):
                prefix = None
                for tok in re.split(r"[,;]\s*", line):
                    full = RULE_ID.search(tok)
                    if full:
                        rules.append(full.group(0))
                        prefix = full.group(0).rsplit("-", 1)[0]
                    elif prefix and re.fullmatch(r"-\d{5}\.?", tok.strip()):
                        rules.append(f"{prefix}{tok.strip().rstrip('.')}")
        out.append(dict(id=m.group(1), text=m.group(2), status=status, recommended=rec,
                        answer=ans or (("Default applied: " + rec) if status == "DEFERRED" else ""),
                        options=opts, defects=sorted(about), rules=sorted(set(rules))))
    return out


def build() -> GraphData:
    g = GraphData(LOADER)
    aws_rules = {k: v for (lab, k), v in load_05_business_rules.build().nodes.items() if lab == "BusinessRule"}
    rows = list(csv.DictReader(open(A / "rule-verification.csv", encoding="utf-8", newline="")))
    rule_ids = set()
    for r in rows:
        rid, prog = r["rule_id"], r["program"]
        a, b = int(r["start_line"]), int(r["end_line"])
        evidence = f"source-code/{folder(prog)}/{prog}.cbl:{a}-{b}" if a > 0 else ""
        props = dict(verification=r["status"], evidence=evidence, paragraph=r["paragraph"] if r["paragraph"] != "-" else None,
                     verificationNote=r["note"])
        if "-ADD-" in rid:
            g.node("BusinessRule", rid, SRC, program=prog, type="Added rule (legacy-analyst)",
                   name=re.split(r"(?<=\.)\s", r["note"])[0][:120], description=r["note"], given="", when="", then="", **props)
        else:
            g.set_props("BusinessRule", rid, **props)
        g.rel("IMPLEMENTED_IN", "BusinessRule", rid, "Program", prog, SRC if "-ADD-" in rid else "aws-transform",
              lines=f"{a}-{b}" if a > 0 else None)
        rule_ids.add(rid)

    for d in defects():
        g.node("Defect", d["id"], SRC, title=d["title"], legacyBehaviour=d["legacy"], decision=decision(d),
               decisionDetail=d["decisionText"], recommended=d["recommended"], evidence=d["evidence"],
               decidedBy=d["by"], decidedOn=d["on"], defaultApplied="DEFAULT APPLIED" in d["decisionText"])
        for label, key in sorted(targets(d["evidence"])):
            g.rel("AFFECTS", "Defect", d["id"], label, key, SRC)
        for r in rows:
            if re.search(rf"\b{d['id']}\b", r["note"]):
                g.rel("AFFECTS", "Defect", d["id"], "BusinessRule", r["rule_id"], SRC)

    for q in questions():
        g.node("Question", q["id"], SRC, text=q["text"], options=q["options"] or None, recommended=q["recommended"],
               status=q["status"], answer=q["answer"])
        for did in q["defects"]:
            if ("Defect", did) in g.nodes:   # D-030 and later are conventions recorded below the register table, not Defect nodes
                g.rel("ABOUT", "Question", q["id"], "Defect", did, SRC)
        for rid in q["rules"]:
            if rid in rule_ids:
                g.rel("ABOUT", "Question", q["id"], "BusinessRule", rid, SRC)

    cite = re.compile(r"42\s*CFR\s*(?:Part\s*)?455\.(\d+)")
    for rid, p in aws_rules.items():
        text = " ".join(str(p.get(k) or "") for k in ("description", "given", "when", "then", "name"))
        for n in sorted(set(cite.findall(text))):
            reg = f"42CFR455.{n}"
            g.node("Regulation", reg, SRC, title=REG_TITLES.get(f"455.{n}", "title not verified (cited by AWS rule text)"),
                   titleSource="pem-domain skill summary" if f"455.{n}" in REG_TITLES else None)
            g.rel("GOVERNED_BY", "BusinessRule", rid, "Regulation", reg, SRC)
    g.meta["rules updated / added"] = f"{sum(1 for r in rows if '-ADD-' not in r['rule_id'])} / {sum(1 for r in rows if '-ADD-' in r['rule_id'])}"
    g.meta["defects / questions"] = f"{len(defects())} / {len(questions())}"
    return g


if __name__ == "__main__":
    common.run(__file__, build)
