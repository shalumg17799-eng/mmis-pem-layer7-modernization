"""load_05_business_rules: the 403 AWS business rules and their key functionalities.

Input: aws-transform/05-business-documentation/MMIS-PROVIDER-DEMO-TIER4/**/*-cbl.json (keys all_rules, key_functionalities).
Every rule starts UNVERIFIED with an empty `evidence`; Phase 2 verifies each against the COBOL and fills both.
The AWS rule JSON carries no source line numbers, so there is nothing to put in `lines` yet.

Run:  python load_05_business_rules.py [--dry-run]
"""
import json
import sys
from collections import Counter
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import common  # noqa: E402
from common import GraphData, stem  # noqa: E402

SRC = "aws-transform"
LOADER = "load_05_business_rules.py"
BRE = common.AWS / "05-business-documentation" / "MMIS-PROVIDER-DEMO-TIER4"


def build() -> GraphData:
    g = GraphData(LOADER)
    per_program, no_kf, dup_kf = Counter(), 0, []
    for f in sorted(BRE.glob("*/*-cbl.json")):
        d = json.loads(f.read_text(encoding="utf-8"))
        prog = stem(d["program_name"])
        rule_ids = set()
        for r in d["all_rules"]:
            ac = r.get("Acceptance_Criteria") or {}
            g.node("BusinessRule", r["Rule_Id"], SRC, program=prog, type=r.get("Rule_Type"), name=r.get("Rule_Name"),
                   description=r.get("Rule_Description"), given=ac.get("Given"), when=ac.get("When"), then=ac.get("Then"),
                   verification="UNVERIFIED", evidence="")
            g.rel("IMPLEMENTED_IN", "BusinessRule", r["Rule_Id"], "Program", prog, SRC)
            rule_ids.add(r["Rule_Id"])
            per_program[prog] += 1
        in_kf, seen_kf = set(), set()

        def walk(kf, parent):
            kid = f"{prog}:{kf['id']}"
            if kid in seen_kf:
                dup_kf.append(kid)
            seen_kf.add(kid)
            g.node("KeyFunctionality", kid, SRC, program=prog, awsId=kf["id"], title=kf.get("title"), level=kf.get("level"),
                   description=kf.get("description"), parent=parent)
            for r in kf.get("rules", []):
                g.rel("PART_OF", "BusinessRule", r["rule_id"], "KeyFunctionality", kid, SRC)
                in_kf.add(r["rule_id"])
            for sub in kf.get("sub_functionalities", []):
                walk(sub, kid)

        for kf in d["key_functionalities"]:
            walk(kf, None)
        no_kf += len(rule_ids - in_kf)
    g.meta["rules per program"] = dict(sorted(per_program.items()))
    g.meta["rules AWS attached to no key functionality (no PART_OF)"] = no_kf
    g.meta["key-functionality ids repeated within a program (merged into one node)"] = dup_kf
    return g


if __name__ == "__main__":
    common.run(__file__, build)
