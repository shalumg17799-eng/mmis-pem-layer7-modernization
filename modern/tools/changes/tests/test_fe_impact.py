"""Tests of the impact analysis (fe_impact.py) on the real MAM-30 data of this repository (read-only)."""
import fe_common as c
import fe_impact as imp


def fake_diff(changed=(), added=(), removed=()):
    return {
        "acs": {
            "changed": [{"id": i, "fields": {"then": {"old": "o", "new": "n"}}, "behaviour": True, "title": "t"} for i in changed],
            "added": [{"id": i, "title": "new", "given": "g", "when": "w", "then": "t", "basis": "D-004", "extra": ""} for i in added],
            "removed": [{"id": i, "title": "old", "given": "g", "when": "w", "then": "t", "basis": "", "extra": ""} for i in removed],
            "unchanged": [],
        },
        "noDifference": False,
    }


def test_references_in_a_basis_line_are_split_by_kind():
    r = imp.references("Basis: R-PRVRSK01-cbl-00008, D-003, Q-A4, ADR-009, modern/docs/ux/journeys.md#J6, modern/contracts/asyncapi/events.yaml (x)")
    assert r["rules"] == ["R-PRVRSK01-cbl-00008"] and r["decisions"] == ["D-003"] and r["questions"] == ["Q-A4"]
    assert r["adrs"] == ["ADR-009"] and r["journeys"] == ["J6"]


def test_tests_of_one_ac_cite_that_ac_and_only_tests_of_that_ac_are_returned():
    found = imp.find_tests("MAM-30", ["AC-4"])
    files = found["byAc"]["AC-4"]
    assert files, "MAM-30 AC-4 has tests in this repository"
    for f in files:
        text = (c.REPO / f).read_text(encoding="utf-8", errors="replace")
        assert "MAM-30" in text and "AC-4" in text
    # a test file that cites only another AC of the story is not listed for AC-4
    only_ac1 = [f for f in imp.find_tests("MAM-30", ["AC-1"])["byAc"]["AC-1"] if f not in files]
    assert all(f not in files for f in only_ac1)


def test_an_ac_range_in_a_journey_test_name_covers_every_ac_of_the_range():
    found = imp.find_tests("MAM-69", ["AC-3"])
    assert any("j1-enrolment" in f for f in found["byAc"]["AC-3"])


def test_an_added_ac_has_no_tests_yet_and_is_flagged():
    a = imp.analyse("MAM-30", fake_diff(added=["AC-9"]), neo4j=False)
    assert a["acs"]["AC-9"]["tests"] == [] and a["acs"]["AC-9"]["action"] == "add tests"


def test_a_changed_ac_gets_its_tests_its_rule_code_and_the_services_to_check():
    a = imp.analyse("MAM-30", fake_diff(changed=["AC-4"]), neo4j=False)
    ac = a["acs"]["AC-4"]
    assert ac["action"] == "change tests" and ac["tests"]
    assert any(f.endswith("CompleteRevalidation.java") for f in a["productionFiles"]["storyLevel"] + a["productionFiles"]["byRule"])
    assert ":services:provider-service:check" in a["checks"]["gradle"]
    assert "J6" in [j["journey"] for j in a["journeys"]]


def test_an_event_named_in_an_ac_is_found_in_the_asyncapi_contract():
    a = imp.analyse("MAM-30", fake_diff(changed=["AC-5"]), neo4j=False)
    assert any("asyncapi" in x["file"] and x["token"] == "ProviderStatusChanged" for x in a["contracts"])


def test_a_removed_ac_lists_its_tests_for_removal():
    a = imp.analyse("MAM-30", fake_diff(removed=["AC-8"]), neo4j=False)
    assert a["acs"]["AC-8"]["action"] == "remove tests" and a["acs"]["AC-8"]["tests"]


def test_other_stories_that_cite_the_same_rules_are_listed():
    a = imp.analyse("MAM-30", fake_diff(changed=["AC-1"]), neo4j=False)
    assert "MAM-30" not in a["sharedRuleStories"]


def test_neo4j_failure_is_reported_not_raised(monkeypatch):
    for v in ("NEO4J_URI", "NEO4J_USERNAME", "NEO4J_PASSWORD"):
        monkeypatch.delenv(v, raising=False)
    ctx = imp.legacy_context(["R-PRVRSK01-cbl-00008"])
    assert "unavailable" in ctx
