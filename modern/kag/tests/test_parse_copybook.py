"""Unit tests for the copybook / data-description parser (real copybooks from source-code/)."""
from conftest import read
from parsers import parse_copybook, pic_length, split_statements, code_lines


def test_pic_length():
    assert pic_length("X(10)") == 10
    assert pic_length("9(08)") == 8
    assert pic_length("X") == 1
    assert pic_length("S9(4)", "COMP") == 2
    assert pic_length("S9(9)", "COMP") == 4
    assert pic_length("S9(5)", "COMP-3") == 3


def test_split_statements_respects_dots_and_quotes():
    code = [(1, "01  A. 05 B PIC X(2) VALUE 'A.B'."), (2, "EXEC SQL DECLARE MMPRV.PROVIDER TABLE END-EXEC.")]
    stmts = [s for _, s in split_statements(code)]
    assert stmts == ["01 A", "05 B PIC X(2) VALUE 'A.B'", "EXEC SQL DECLARE MMPRV.PROVIDER TABLE END-EXEC"]


def test_prvrecrd_layout_is_130_bytes():
    rec = parse_copybook(read("COPYBOOKS/PRVRECRD.cpy"))["records"]
    assert [r.name for r in rec] == ["PROVIDER-RECORD"]
    r = rec[0]
    assert r.length == 130  # the copybook, not the 121 declared in the FDs and IDCAMS
    f = {i.name: i for i in r.walk()}
    assert (f["PRV-NPI"].start, f["PRV-NPI"].length) == (1, 10)
    assert (f["PRV-TYPE-CODE"].start, f["PRV-TYPE-CODE"].length) == (20, 2)
    assert (f["PRV-TAXONOMY"].start, f["PRV-TAXONOMY"].length) == (22, 10)
    assert f["PRV-ENROLL-STATUS"].start == 33
    assert f["PRV-SCREEN-FLAG"].start == 34
    assert (f["PRV-VARIANT"].start, f["PRV-VARIANT"].length) == (51, 80)


def test_redefines_overlays_and_does_not_add():
    r = parse_copybook(read("COPYBOOKS/PRVRECRD.cpy"))["records"][0]
    f = {i.name: i for i in r.walk()}
    assert f["PRV-INDIVIDUAL-DATA"].redefines == "PRV-VARIANT"
    assert f["PRV-ORG-DATA"].redefines == "PRV-VARIANT"
    assert (f["PRV-INDIVIDUAL-DATA"].start, f["PRV-INDIVIDUAL-DATA"].length) == (51, 80)
    assert (f["PRV-ORG-DATA"].start, f["PRV-ORG-DATA"].length) == (51, 80)
    assert f["PRV-OWNER-NPI"].start == 121  # last 10 bytes: lost if the record were 121 bytes (D-007)
    assert f["PRV-LAST-NAME"].start == 51 and f["PRV-LEGAL-NAME"].start == 51


def test_88_levels_attach_to_parent_field():
    r = parse_copybook(read("COPYBOOKS/PRVRECRD.cpy"))["records"][0]
    f = {i.name: i for i in r.walk()}
    assert f["PRV-TYPE-CODE"].values == ["PRV-INDIVIDUAL=IN", "PRV-GROUP=GR", "PRV-FACILITY=FA"]
    assert "PRV-ACTIVE=A" in f["PRV-ENROLL-STATUS"].values
    assert f["PRV-SCREEN-FLAG"].values == ["PRV-UNSCREENED=U", "PRV-SCREEN-PASS=P", "PRV-SCREEN-FAIL=F"]


def test_scrnrec_is_67_bytes():
    r = parse_copybook(read("COPYBOOKS/SCRNREC.cpy"))["records"][0]
    assert r.name == "SCREENING-RECORD" and r.length == 67
    assert {i.name: i.length for i in r.walk()} == {
        "SCR-NPI": 10, "SCR-SOURCE": 8, "SCR-RESULT": 1, "SCR-VERDICT-DATE": 8, "SCR-DETAIL": 40}


def test_errcodes_has_ok_plus_nine_error_codes():
    r = parse_copybook(read("COPYBOOKS/ERRCODES.cpy"))["records"][0]
    names = [i.name for i in r.walk()]
    assert names[0] == "EC-OK" and len(names) == 10  # EC-OK + E101-E104 + E201-E205
    assert "EC-MISSING-NPI" in names and "EC-DEBARRED" in names
    assert r.length == 40  # ten PIC X(04) fields


def test_wrkstore_nests_two_copybooks():
    cb = parse_copybook(read("COPYBOOKS/WRKSTORE.cpy"))
    assert [c["name"] for c in cb["copies"]] == ["SCRNREC", "ERRCODES"]  # anti-pattern AP-09
    assert [c["line"] for c in cb["copies"]] == [22, 23]


def test_dclprov_skips_exec_sql_and_handles_comp_group():
    r = parse_copybook(read("COPYBOOKS/DCLPROV.cpy"))["records"][0]
    assert r.name == "DCLPROVIDER"
    f = {i.name: i for i in r.walk()}
    assert f["PRVT-LEGAL-NAME-LEN"].length == 2  # S9(4) COMP
    assert f["PRVT-LEGAL-NAME"].length == 62
    assert r.length == 115
