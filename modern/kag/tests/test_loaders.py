"""Offline tests for the loaders and the validation script. No Neo4j connection is made."""
import csv
import importlib
import re

import pytest
import common
import load_00_constraints
import validate
from conftest import AWS, KAG

NODE_PROPS_NEEDED = {"source", "loader"}


def build(name):
    return importlib.import_module(name).build()


@pytest.mark.parametrize("name", common.LOADERS)
def test_every_loader_resolves_all_endpoints(name):
    assert build(name).unresolved(common.universe_upto(name)) == []


@pytest.mark.parametrize("name", common.LOADERS)
def test_build_is_deterministic_so_reloading_changes_nothing(name):
    a, b = build(name), build(name)
    assert (a.node_counts(), a.rel_counts(), len(a.updates)) == (b.node_counts(), b.rel_counts(), len(b.updates))
    assert a.nodes == b.nodes and a.rels == b.rels


@pytest.mark.parametrize("name", common.LOADERS)
def test_every_node_and_relationship_has_provenance(name):
    g = build(name)
    assert all(NODE_PROPS_NEEDED <= set(p) for p in g.nodes.values())
    assert all(NODE_PROPS_NEEDED <= set(p) for p in g.rels.values())
    assert all(p["source"] in {"source-code", "aws-transform", "legacy-analyst", "architect", "backlog-planner", "build", "human"}
               for p in list(g.nodes.values()) + list(g.rels.values()))


def test_constraints_cover_every_label():
    assert load_00_constraints.missing_constraints(load_00_constraints.statements()) == []


def test_business_rules_match_the_aws_json_exactly():
    g = build("load_05_business_rules")
    assert g.node_counts()["BusinessRule"] == 403
    assert g.meta["rules per program"] == {"PRVCICS1": 45, "PRVDEAD1": 14, "PRVDIR01": 29, "PRVENR01": 68, "PRVREV01": 24,
                                           "PRVRSK01": 48, "PRVSAN01": 58, "PRVSCR01": 62, "PRVTMS01": 55}
    assert g.meta["rules AWS attached to no key functionality (no PART_OF)"] == 88
    assert g.meta["key-functionality ids repeated within a program (merged into one node)"] == ["PRVTMS01:t"]


def test_anti_pattern_lines_match_the_documented_locations():
    # CLAUDE.md section 6 documents these line numbers; the loader finds them in the source with regexes.
    g = build("load_07_anti_patterns")
    lines = {k[1]: p["lines"] for k, p in g.nodes.items() if k[0] == "AntiPattern"}
    assert lines["AP-01"] == ["132", "139", "144", "155"]
    assert lines["AP-02"] == ["61", "97", "140"]
    assert lines["AP-03"] == ["86", "90", "92", "103"]
    assert lines["AP-04"] == ["126-137", "131"]
    assert lines["AP-05"] == ["134"]
    assert lines["AP-06"] == ["34", "115"]
    assert lines["AP-07"] == ["80", "107"]
    assert lines["AP-08"] == ["29", "37"]
    assert lines["AP-09"] == ["22", "23"]
    assert lines["AP-10"] == ["34", "41"]
    assert lines["AP-11"] and lines["AP-12"]
    assert len(lines) == 12


def test_dictionary_rows_without_a_parsed_counterpart_are_only_fillers():
    g = build("load_04_data_dictionary")
    assert len(g.unmatched) == 6 and all("FILLER" in u for u in g.unmatched)


def test_aws_dictionary_misses_four_db2_columns():
    rows = list(csv.DictReader(open(AWS / "02-data-analysis/data-dictionary/data_dictionary_ddl.csv", encoding="utf-8")))
    assert len(rows) == 22
    cols = build("load_01_source").node_counts()["Field"]
    ddl = [k for k in build("load_01_source").nodes if k[0] == "Field" and k[1].startswith("PRVDDL01:")]
    assert len(ddl) == 26 and cols > 26


def test_aws_and_parsed_dependency_edges_agree():
    g = build("load_02_dependencies")
    assert g.meta["COPY edges: parsed / AWS / only parsed / only AWS"] == "18 / 18 / 0 / 0"
    assert g.meta["EXEC edges (job -> program): parsed / AWS / only parsed / only AWS"] == "11 / 11 / 0 / 0"


def test_master_ksds_uses_the_130_byte_layout():
    g = build("load_03_lineage")
    assert ("USES_LAYOUT", "Dataset", "MMPRV.PROVIDER.MASTER.KSDS", "Record", "PRVRECRD:PROVIDER-RECORD") in g.rels


def test_analysis_loader_matches_the_analysis_files():
    g = build("load_09_analysis")
    assert g.node_counts()["BusinessRule"] == 52          # the added rules; the 403 AWS rules are updated, not created
    assert sum(1 for k in g.updates if k[0] == "BusinessRule") == 403
    assert g.node_counts()["Defect"] == 29 and g.node_counts()["Question"] >= 10
    assert all(p["decision"] in {"FIX", "PRESERVE", "DROP", "REPLACE"} for (l, _), p in g.nodes.items() if l == "Defect")
    affected = {r[2] for r in g.rels if r[0] == "AFFECTS"}
    assert affected == {k for (l, k) in g.nodes if l == "Defect"}   # every defect affects something


def test_validate_offline_all_checks_match():
    checks = validate.evaluate("offline")
    assert [c["name"] for c in checks if not c["ok"]] == []
    assert len(checks) >= 30


def test_validate_cypher_file_is_in_sync():
    assert (KAG / "validate.cypher").read_text(encoding="utf-8").strip() == validate.render_cypher().strip()


def test_connect_names_missing_variables_but_never_values(monkeypatch):
    for v in common.ENV_VARS:
        monkeypatch.delenv(v, raising=False)
    monkeypatch.setenv("NEO4J_PASSWORD", "super-secret-value")
    with pytest.raises(SystemExit) as e:
        common.connect()
    msg = str(e.value)
    assert "NEO4J_URI" in msg and "NEO4J_USERNAME" in msg and "NEO4J_DATABASE" in msg and "super-secret-value" not in msg


@pytest.mark.parametrize("db", ["neo4j", "system", "NEO4J"])
def test_connect_refuses_the_default_databases(monkeypatch, db):
    for v, val in zip(common.ENV_VARS, ("neo4j://127.0.0.1:7687", "u", "p", db)):
        monkeypatch.setenv(v, val)
    with pytest.raises(SystemExit) as e:
        common.connect()
    assert "dedicated" in str(e.value)


def test_no_secret_assignment_in_any_kag_file():
    pattern = re.compile("NEO4J_" + r"PASSWORD\s*=\s*\S")
    offenders = []
    for p in KAG.rglob("*"):
        if p.is_file() and ".venv" not in p.parts and "__pycache__" not in p.parts and p.suffix in {".py", ".sh", ".md", ".cypher", ".txt", ".json"}:
            if pattern.search(p.read_text(encoding="utf-8", errors="replace")):
                offenders.append(str(p.relative_to(KAG)))
    assert offenders == []
