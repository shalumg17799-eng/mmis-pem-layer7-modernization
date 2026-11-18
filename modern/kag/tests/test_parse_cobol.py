"""Unit tests for the COBOL program parser (real programs from source-code/)."""
import pytest
from conftest import read
from parsers import parse_cobol

PROGRAMS = {
    "PRVENR01": "BATCH", "PRVSCR01": "BATCH", "PRVRSK01": "BATCH", "PRVREV01": "BATCH",
    "PRVTMS01": "BATCH", "PRVSAN01": "BATCH", "PRVDIR01": "BATCH", "PRVDEAD1": "BATCH", "PRVCICS1": "ONLINE",
}


@pytest.mark.parametrize("prog,folder", PROGRAMS.items())
def test_program_id_matches_member(prog, folder):
    p = parse_cobol(read(f"{folder}/{prog}.cbl"))
    assert p["program_id"] == prog
    assert p["paragraphs"], "every program has a procedure division with paragraphs"


def test_prvenr01_paragraphs_and_fallthrough_label():
    p = parse_cobol(read("BATCH/PRVENR01.cbl"))
    names = [x["name"] for x in p["paragraphs"]]
    assert names == ["MAIN-CONTROL", "INITIALIZE-PROGRAM", "PROCESS-APPLICATIONS", "VALIDATE-APPLICATION",
                     "INVALID-APP", "WRITE-PROVIDER-RECORD", "WRITE-ERROR-RECORD", "TERMINATE-PROGRAM"]
    inv = next(x for x in p["paragraphs"] if x["name"] == "INVALID-APP")
    assert inv["start"] == 155 and inv["end"] == 155  # AP-01: the label has no statement, so control falls through
    assert all(a["start"] <= a["end"] for a in p["paragraphs"])


def test_prvenr01_copies_selects_and_declared_length():
    p = parse_cobol(read("BATCH/PRVENR01.cbl"))
    assert sorted(c["name"] for c in p["copies"]) == ["PRVRECRD", "PRVRECRD", "WRKSTORE"]
    assert [(s["file"], s["assign"]) for s in p["selects"]] == [
        ("APP-INPUT-FILE", "APPINPUT"), ("PRV-MASTER-FILE", "PRVMSTR"), ("APP-ERROR-FILE", "APPERROR")]
    recs = {r.name: r for r in p["records"]}
    m = recs["PRV-MASTER-RECORD"]
    assert m.fd == "PRV-MASTER-FILE" and m.declared_length == 121 and m.copies == ["PRVRECRD"]
    assert recs["APP-INPUT-RECORD"].declared_length == 200 and recs["APP-INPUT-RECORD"].length == 200
    assert recs["APP-ERROR-RECORD"].declared_length == 250 and recs["APP-ERROR-RECORD"].length == 250


def test_every_batch_fd_declares_121_for_the_master():
    for prog in ("PRVENR01", "PRVSCR01", "PRVRSK01", "PRVREV01", "PRVTMS01", "PRVSAN01", "PRVDIR01"):
        recs = {r.name: r for r in parse_cobol(read(f"BATCH/{prog}.cbl"))["records"]}
        assert recs["PRV-MASTER-RECORD"].declared_length == 121, prog


def test_prvsan01_dynamic_call_is_detected_and_comment_is_ignored():
    p = parse_cobol(read("BATCH/PRVSAN01.cbl"))
    assert p["calls"] == [{"target": "WS-NOTICE-PGM", "dynamic": True, "line": 134}]  # AP-05; line 16 is a comment


def test_prvrsk01_has_the_alter_target_paragraphs():
    p = parse_cobol(read("BATCH/PRVRSK01.cbl"))
    names = [x["name"] for x in p["paragraphs"]]
    for n in ("APPLY-TIER-PARA", "LIMITED-TIER", "MODERATE-TIER", "HIGH-TIER", "TIER-DONE", "SET-REVALIDATION-DATE"):
        assert n in names
    assert next(x for x in p["paragraphs"] if x["name"] == "APPLY-TIER-PARA")["start"] == 103


def test_prvcics1_cics_facts():
    p = parse_cobol(read("ONLINE/PRVCICS1.cbl"))
    assert p["cics"] == {"transid": "PINQ", "map": "PRVMAP1", "mapset": "PRVSET1", "file": "PRVPROVN"}


def test_prvdead1_has_no_files():
    p = parse_cobol(read("BATCH/PRVDEAD1.cbl"))
    assert p["selects"] == [] and p["copies"] == [] and p["calls"] == []


def test_total_lines_are_one_less_than_aws_asset_counts():
    # AWS assets.csv "Total lines" counts the empty string after the final line ending, so it is
    # always the real line count + 1 (checked for all nine programs; e.g. PRVENR01 = 189 lines, AWS says 190).
    aws = {"PRVENR01": 190, "PRVSCR01": 150, "PRVRSK01": 138, "PRVSAN01": 139, "PRVTMS01": 118,
           "PRVREV01": 95, "PRVDIR01": 91, "PRVDEAD1": 52, "PRVCICS1": 74}
    for prog, n in aws.items():
        folder = PROGRAMS[prog]
        assert parse_cobol(read(f"{folder}/{prog}.cbl"))["total_lines"] == n - 1, prog


def test_section_level_copy_is_not_attached_to_the_previous_record():
    # Regression: COPY WRKSTORE sits right after WORKING-STORAGE SECTION and must not attach to the FD record before it.
    p = parse_cobol(read("BATCH/PRVENR01.cbl"))
    recs = {r.name: r for r in p["records"]}
    assert recs["APP-ERROR-RECORD"].copies == []
    assert recs["WS-PRV-WORK"].copies == ["PRVRECRD"] and recs["WS-PRV-WORK"].length == 0
    assert any(c["name"] == "WRKSTORE" for c in p["copies"])  # still recorded as a program-level COPY
