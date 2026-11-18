"""Unit tests for the JCL (jobs, procs, IDCAMS) and DB2 DDL parsers (real files from source-code/)."""
from conftest import read
from parsers import parse_ddl, parse_idcams, parse_jcl


def test_prvday01_steps_conditions_and_programs():
    j = parse_jcl(read("JCL/PRVDAY01.jcl"))
    assert j["kind"] == "job" and j["name"] == "PRVDAY01"
    steps = {s["name"]: s for s in j["steps"]}
    assert list(steps) == ["STEP010", "STEP020", "STEP030", "STEP040", "STEP099"]
    assert [s["program"] for s in j["steps"]] == ["PRVENR01", "SORT", "PRVSCR01", "PRVRSK01", "IEFBR14"]
    assert [s["seq"] for s in j["steps"]] == [1, 2, 3, 4, 5]
    assert steps["STEP010"]["condition"] is None
    assert steps["STEP020"]["condition"] == "IF STEP010.RC = 0"  # steps 020-040 only if STEP010 RC=0
    assert steps["STEP040"]["condition"] == "IF STEP010.RC = 0"
    assert steps["STEP099"]["condition"] == "COND=(0,LT)"


def test_prvday01_dds_and_symbolic_dsn_names():
    j = parse_jcl(read("JCL/PRVDAY01.jcl"))
    dds = {(s["name"], d["dd"]): d["dsn"] for s in j["steps"] for d in s["dds"]}
    assert dds[("STEP010", "APPINPUT")] == "MMPRV.PROV.APPS.D&DATE"
    assert dds[("STEP010", "PRVMSTR")] == "MMPRV.PROVIDER.MASTER.KSDS"
    assert dds[("STEP020", "SORTOUT")] == "&&PRVSORT"
    assert dds[("STEP030", "SCRNOUT")] == "MMPRV.SCREEN.RESULTS.D&DATE"


def test_prvmth01_steps():
    j = parse_jcl(read("JCL/PRVMTH01.jcl"))
    assert [(s["name"], s["program"]) for s in j["steps"]] == [
        ("STEP010", "PRVREV01"), ("STEP020", "PRVTMS01"), ("STEP030", "IEBGENER")]
    d = {(s["name"], x["dd"]): x["dsn"] for s in j["steps"] for x in s["dds"]}
    assert d[("STEP030", "SYSUT2")] == "MMPRV.TMSIS.ARCHIVE(+1)"


def test_prvscrpr_is_a_proc_running_prvscr01():
    j = parse_jcl(read("JCL/PROCS/PRVSCRPR.jcl"))
    assert j["kind"] == "proc" and j["name"] == "PRVSCRPR"
    assert [(s["name"], s["program"]) for s in j["steps"]] == [("SCREEN", "PRVSCR01")]
    assert {x["dd"]: x["dsn"] for x in j["steps"][0]["dds"]}["PRVMSTR"] == "&HLQ..PROVIDER.MASTER.KSDS"


def test_idcams_provider_master_definition():
    j = parse_jcl(read("VSAM/PRVVSM01.jcl"))
    assert [s["program"] for s in j["steps"]] == ["IDCAMS"]
    d = {x["name"]: x for x in j["idcams"]}
    ksds = d["MMPRV.PROVIDER.MASTER.KSDS"]
    assert (ksds["kind"], ksds["key_length"], ksds["key_offset"], ksds["record_size"]) == ("CLUSTER", 10, 0, 121)
    aix = d["MMPRV.PROVIDER.MASTER.AIX"]
    assert (aix["kind"], aix["key_length"], aix["key_offset"], aix["relate"]) == (
        "ALTERNATEINDEX", 9, 10, "MMPRV.PROVIDER.MASTER.KSDS")
    assert d["MMPRV.PROVIDER.MASTER.PATH"]["kind"] == "PATH"


def test_idcams_screen_and_sanction_clusters():
    d = {x["name"]: x for x in parse_jcl(read("VSAM/PRVVSM02.jcl"))["idcams"]}
    assert (d["MMPRV.SCREEN.RESULTS.KSDS"]["key_length"], d["MMPRV.SCREEN.RESULTS.KSDS"]["record_size"]) == (18, 67)
    assert (d["MMPRV.SANCTION.MASTER.KSDS"]["key_length"], d["MMPRV.SANCTION.MASTER.KSDS"]["record_size"]) == (10, 80)


def test_parse_idcams_handles_continuation_dashes():
    sysin = "  DEFINE CLUSTER ( NAME(A.B) -\n   KEYS(4 2) -\n   RECORDSIZE(10 20) )\n"
    assert parse_idcams(sysin) == [{"kind": "CLUSTER", "name": "A.B", "key_length": 4, "key_offset": 2,
                                    "record_size": 20, "relate": None}]


def test_ddl_tables_and_columns():
    t = {x["name"]: x for x in parse_ddl(read("DB2/PRVDDL01.sql"))}
    assert list(t) == ["MMPRV.PROVIDER", "MMPRV.PROVIDER_OWNER", "MMPRV.SCREENING", "MMPRV.SANCTION"]
    assert [len(x["columns"]) for x in t.values()] == [11, 5, 5, 5]  # AWS's dictionary lists only 22 of these 26
    cols = {c["name"]: c for c in t["MMPRV.PROVIDER"]["columns"]}
    assert cols["NPI"]["type"] == "CHAR(10)" and cols["LEGAL_NAME"]["type"] == "VARCHAR(60)"
    assert "SCREEN_FLAG" in cols and "END_DATE" in cols  # present in the DDL, absent from DCLPROV
    assert t["MMPRV.PROVIDER_OWNER"]["columns"][3]["type"] == "DECIMAL(5,2)"
