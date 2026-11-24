"""Tests of the Jira-against-spec diff (fe_diff.py). Synthetic stories only."""
import fe_common as c
import fe_diff

BASE = """## Business requirement
A provider is revalidated every five years.

## Acceptance criteria
### AC-1 The date adds five years
Given the clock date is 2029-03-15
When an ANALYST approves provider 1003000111
Then revalidationDueDate is 2034-03-15
Basis: R-PRVRSK01-cbl-00008, D-004

### AC-2 A leap day gives a real date
Given the clock date is 2028-02-29
When the provider is approved
Then revalidationDueDate is 2033-02-28
Basis: R-PRVRSK01-ADD-02

## Out of scope
- The monthly sweep.

## Legacy trace (agent-owned, do not edit)
Rules: R-PRVRSK01-cbl-00008

## Technical notes (agent-owned)
Service: provider-service.
"""


def run(jira_body, jira_title="T", spec_title="T"):
    return fe_diff.diff(BASE, jira_body, spec_title, jira_title)


def test_identical_text_is_no_difference():
    d = run(BASE)
    assert d["noDifference"] is True and d["identical"] is True
    assert d["acs"]["changed"] == d["acs"]["added"] == d["acs"]["removed"] == []
    assert d["acs"]["unchanged"] == ["AC-1", "AC-2"]
    assert "no difference" in fe_diff.summary(d)


def test_crlf_and_trailing_blanks_are_no_difference():
    d = run(BASE.replace("\n", "\r\n") + "\r\n\r\n")
    assert d["noDifference"] is True and d["identical"] is True


def test_markdown_escapes_and_spacing_only_is_no_difference_but_not_identical():
    jira = BASE.replace("Basis: R-PRVRSK01-ADD-02", "Basis:  R-PRVRSK01-ADD\\-02").replace("2034-03-15", "2034\\-03\\-15")
    d = run(jira)
    assert d["identical"] is False and d["noDifference"] is True
    assert d["formattingOnly"] is True


def test_changed_then_is_reported_by_ac_and_field_with_old_and_new():
    d = run(BASE.replace("Then revalidationDueDate is 2034-03-15", "Then revalidationDueDate is 2032-03-15"))
    assert d["noDifference"] is False
    [ch] = d["acs"]["changed"]
    assert ch["id"] == "AC-1" and ch["behaviour"] is True
    assert ch["fields"]["then"] == {"old": "revalidationDueDate is 2034-03-15", "new": "revalidationDueDate is 2032-03-15"}
    assert d["acs"]["unchanged"] == ["AC-2"]
    assert "AC-1" in fe_diff.summary(d) and "1 changed" in fe_diff.summary(d)


def test_changed_basis_only_is_not_a_behaviour_change():
    d = run(BASE.replace("Basis: R-PRVRSK01-cbl-00008, D-004", "Basis: R-PRVRSK01-cbl-00008, D-004, D-030"))
    [ch] = d["acs"]["changed"]
    assert ch["behaviour"] is False and list(ch["fields"]) == ["basis"]


def test_added_ac_takes_the_next_number_and_is_listed_with_its_text():
    added = BASE.replace("## Out of scope", "### AC-3 A new rule\nGiven g\nWhen w\nThen t\nBasis: D-001\n\n## Out of scope")
    d = run(added)
    [a] = d["acs"]["added"]
    assert a["id"] == "AC-3" and a["then"] == "t" and a["title"] == "A new rule"
    assert d["acs"]["removed"] == [] and d["acs"]["changed"] == []


def test_removed_ac_is_listed_with_its_old_text():
    removed = BASE.replace(BASE[BASE.index("### AC-2"):BASE.index("## Out of scope")], "")
    d = run(removed)
    [r] = d["acs"]["removed"]
    assert r["id"] == "AC-2" and r["then"] == "revalidationDueDate is 2033-02-28"


def test_an_ac_id_that_is_reused_for_other_text_is_changed_not_added():
    d = run(BASE.replace("A leap day gives a real date", "Something else").replace("Then revalidationDueDate is 2033-02-28", "Then x"))
    [ch] = d["acs"]["changed"]
    assert ch["id"] == "AC-2" and {"title", "then"} <= set(ch["fields"])


def test_business_requirement_and_out_of_scope_changes_are_reported():
    d = run(BASE.replace("every five years", "every three years").replace("- The monthly sweep.", "- The weekly sweep."))
    assert d["sections"]["businessRequirement"]["changed"] is True
    assert d["sections"]["outOfScope"]["changed"] is True
    assert d["noDifference"] is False


def test_agent_owned_sections_are_reported_apart_and_never_make_a_requirement_change():
    d = run(BASE.replace("Service: provider-service.", "Service: other."))
    assert d["sections"]["technicalNotes"]["changed"] is True
    assert d["agentOwnedOnly"] is True
    assert d["requirementChanged"] is False


def test_title_change_is_reported():
    d = run(BASE, jira_title="New title", spec_title="Old title")
    assert d["title"] == {"changed": True, "old": "Old title", "new": "New title"}
    assert d["requirementChanged"] is True


def test_sha_of_a_spec_body_is_the_front_matter_sha(tmp_path):
    p = tmp_path / "MAM-1.md"
    body = BASE
    p.write_text("---\nkey: MAM-1\nspecVersion: 1\ndescriptionSha256: " + c.sha256_text(body) + "\n---\n" + body, encoding="utf-8")
    s = c.read_spec(p)
    assert c.sha256_text(s.body) == s.front["descriptionSha256"]
    assert s.key == "MAM-1" and s.version == 1


def test_every_real_spec_matches_its_own_hash():
    bad = [p.name for p in sorted(c.SPECS.glob("MAM-*.md")) if c.sha256_text(c.read_spec(p).body) != c.read_spec(p).front["descriptionSha256"]]
    assert bad == []
