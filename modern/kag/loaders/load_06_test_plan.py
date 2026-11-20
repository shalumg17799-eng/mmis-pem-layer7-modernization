"""load_06_test_plan: the AWS Transform test plan (4 test cases, one per JCL job).

The ontology has no label for AWS's planned test cases, so they are loaded as TestCase nodes with
level='aws-plan' and a COVERS relationship to the Job (DEFAULT APPLIED - revisit, see questions.md Q3).
Test cases that WE write later use ids like `<service>:<class>#<method>` and levels unit/acceptance/contract/e2e.

Run:  python load_06_test_plan.py [--dry-run]
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import common  # noqa: E402
from common import GraphData, stem  # noqa: E402

SRC = "aws-transform"
LOADER = "load_06_test_plan.py"


def build() -> GraphData:
    g = GraphData(LOADER)
    plan = json.loads((common.AWS / "06-test-plan" / "test-plan.json").read_text(encoding="utf-8"))
    for t in plan:
        tid = "aws:" + t["id"]
        m = t.get("testCaseMetrics", {})
        g.node("TestCase", tid, SRC, level="aws-plan", service="aws-transform", name=t["name"],
               linesOfCode=t.get("linesOfCode"), complexityScore=t.get("complexityScore"),
               cyclomaticComplexity=t.get("cyclomaticComplexity"), businessRuleCount=m.get("businessRuleCount"),
               preferredOrder=t.get("preferredOrder"),
               datasets=[f"{d['name']} ({d['direction']})" for d in t.get("datasets", [])] or None)
        for ep in t.get("entryPoints", []):
            g.rel("COVERS", "TestCase", tid, "Job", stem(ep["name"]), SRC)
    g.meta["test cases"] = len(plan)
    return g


if __name__ == "__main__":
    sys.exit("load_06_test_plan is DISABLED: Neo4j holds the legacy context only (user decision 2026-11-19). "
             "Traceability lives in modern/docs/traceability/ (rule-trace.csv) and in each Jira story's Legacy trace.")
