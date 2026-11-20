"""MAM-44: the deterministic synthetic datasets under modern/testdata/ (tools modern/tools/data/{generate, check_datasets,
coverage_report, scan_no_real_data}.py). Lane 3 tests (2026-11-19), written after the tools: one or more tests per acceptance
criterion, each named test_mam44_acN_* and marked "[MAM-44 AC-n]" in its docstring.

How the datasets are checked
  * The generator is run into temp folders (--out); the committed files are only READ. An autouse guard fails the module if a
    single committed file under modern/testdata changes while the tests run.
  * The field checks (AC-2 to AC-5, AC-7, AC-8) are written here from the spec and the exported layout JSON, with their own
    record reader, NPI check and mapping tables; they do not call the tools' reader. The tools' own checkers are run too
    (check_datasets.py, coverage_report.py, scan_no_real_data.py and its --self-test), and fed bad data on purpose, so a
    checker that stopped checking would fail a test.
  * AC-8 LIMIT: the round trip through the shared Java library (libs/fixed-width, FixedWidthCodec.read then write) needs
    javac or Gradle, and no Java is run from pytest. Here AC-8 is checked as far as Python can: every record of every dataset
    has the exact length of its layout (read from the exported layout JSON), the file framing is one LF per record, every
    date and digit field is what the codec's validation accepts, and the layout describes every byte of the record (so a
    parse followed by a write cannot change a byte). The Java round trip itself stays covered by tools/data/RoundTrip.java,
    run by hand (see the MAM-44 report); this file does not claim it passed.

Run: modern/kag/.venv/Scripts/python.exe -m pytest -q modern/tools/checks/tests/test_synthetic_datasets.py
"""
import csv
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
from datetime import date
from functools import lru_cache
from pathlib import Path

import pytest

sys.dont_write_bytecode = True

REPO = Path(__file__).resolve().parents[4]
MODERN = REPO / "modern"
TESTDATA = MODERN / "testdata"
TOOLS = MODERN / "tools/data"
LAYOUTS = MODERN / "libs/fixed-width/src/main/resources/layouts"
FEED = REPO / "aws-transform/test-data"
RULES = MODERN / "docs/architecture/rule-to-service.csv"

BUSINESS_DATE = "20260115"          # the frozen business date, CCYYMMDD
NO_DATE = "99991231"                # README convention: "no due date" is written as 99991231 (the codec rejects blank and zero dates)
GENERATED_FILES = {                 # the 13 files the generator writes (README, MAM-44 report)
    "fixed-width/appinput-baseline.dat", "fixed-width/provider-master-seed.dat", "fixed-width/provider-master-edge.dat",
    "fixed-width/sancin-baseline.dat", "scenarios/appinput-scenarios.dat", "scenarios/expected-outcomes.csv",
    "scenarios/provider-type-mapping.csv", "scenarios/business-dates.csv", "negative/appinput-bad-length.dat",
    "negative/sancin-malformed.dat", "coverage.csv", "coverage-gaps.csv", "manifest.csv"}
# which layout each dataset is read with (spec AC-2 to AC-4, AC-8); written here, compared with manifest.csv in AC-1
LAYOUT_OF = {
    "fixed-width/appinput-baseline.dat": "APPINPUT", "fixed-width/provider-master-seed.dat": "PRVRECRD",
    "fixed-width/provider-master-edge.dat": "PRVRECRD", "fixed-width/sancin-baseline.dat": "SANCIN",
    "scenarios/appinput-scenarios.dat": "APPINPUT", "negative/appinput-bad-length.dat": "APPINPUT",
    "negative/sancin-malformed.dat": "SANCIN"}
LAYOUT_LENGTH = {"APPINPUT": 200, "PRVRECRD": 130, "SANCIN": 80}      # spec AC-2, AC-3, AC-4 and D-007


# ---- helpers: running the tools, hashing, reading the datasets --------------------------------------------------------

def run_tool(script: str, *args, cwd: Path | None = None, env_extra: dict | None = None) -> subprocess.CompletedProcess:
    env = dict(os.environ, PYTHONDONTWRITEBYTECODE="1", **(env_extra or {}))
    return subprocess.run([sys.executable, str(TOOLS / script), *map(str, args)], capture_output=True, text=True,
                          errors="replace", env=env, cwd=cwd)


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def tree_hashes(root: Path) -> dict:
    return {p.relative_to(root).as_posix(): sha256(p) for p in sorted(root.rglob("*"))
            if p.is_file() and "__pycache__" not in p.parts}


@lru_cache(maxsize=None)
def layout(name: str) -> dict:
    return json.loads((LAYOUTS / f"{name}.json").read_text(encoding="utf-8"))


def view_of(rec: bytes, lay: dict) -> str | None:
    disc = lay["discriminator"]
    if not disc:
        return None
    f = next(f for f in lay["fields"] if f["name"] == disc)
    return rec[f["start"] - 1:f["start"] - 1 + f["length"]].decode("latin-1").strip()


def parse(rec: bytes, lay: dict, all_views: bool = False) -> dict:
    """Field name -> text with trailing spaces removed; fields of a REDEFINES view other than the record's are left out
    unless all_views. Positions come from the exported layout JSON only."""
    view, out = view_of(rec, lay), {}
    for f in lay["fields"]:
        if f["views"] and not all_views and view not in f["views"]:
            continue
        out[f["name"]] = rec[f["start"] - 1:f["start"] - 1 + f["length"]].decode("latin-1").rstrip(" ")
    return out


def records(path: Path) -> list:
    """The records of a generated .dat file: one per LF line, never CRLF, a final LF."""
    data = path.read_bytes()
    assert b"\r" not in data, f"{path.name} holds a CR"
    lines = data.split(b"\n")
    assert lines[-1] == b"", f"{path.name} does not end with a line feed"
    return lines[:-1]


def rows(root: Path, rel: str) -> list:
    return [parse(r, layout(LAYOUT_OF[rel])) for r in records(root / rel)]


def csv_rows(path: Path) -> list:
    with path.open(encoding="utf-8", newline="") as f:
        return list(csv.DictReader(f))


def real_date(text: str) -> bool:
    try:
        date(int(text[:4]), int(text[4:6]), int(text[6:8]))
    except ValueError:
        return False
    return len(text) == 8 and text.isdigit()


def npi_valid(npi: str) -> bool:
    """NPI check digit: Luhn over 80840 + the ten digits (the check digit is the last one)."""
    if not (len(npi) == 10 and npi.isdigit()):
        return False
    total = 0
    for i, ch in enumerate(reversed("80840" + npi)):
        d = int(ch)
        if i % 2 == 1:
            d = d * 2 - 9 if d * 2 > 9 else d * 2
        total += d
    return total % 10 == 0


def with_check_digit(first9: str) -> str:
    return next(first9 + str(c) for c in range(10) if npi_valid(first9 + str(c)))


assert npi_valid("1234567893") and not npi_valid("1234567894")      # the worked example of the NPI algorithm: 123456789 + 3


def reserved(npi: str) -> bool:
    return 1003000000 <= int(npi) <= 1003000999 or 1990000000 <= int(npi) <= 1990000999


def feed_applications() -> list:
    out = []
    for line in (FEED / "providers.txt").read_text(encoding="utf-8").splitlines():
        if line.strip() and not line.startswith("#"):
            npi, name, ftype, lic, state, owner = line.split("|")
            out.append({"npi": npi, "name": name, "type": ftype, "license": lic, "state": state, "owner": owner})
    return out


def feed_npis_and_names() -> tuple:
    """The fabricated NPIs and names of aws-transform/test-data that scenarios/ may repeat verbatim (QF-12)."""
    apps = feed_applications()
    npis = {a["npi"] for a in apps} | {a["owner"] for a in apps if a["owner"]}
    for line in (FEED / "leie.txt").read_text(encoding="utf-8").splitlines():
        if line.strip() and not line.startswith("#"):
            npis.add(line.split("|")[0])
    return npis, {a["name"] for a in apps}


# ---- fixtures ---------------------------------------------------------------------------------------------------------

@pytest.fixture(scope="module", autouse=True)
def committed_datasets_are_never_rewritten():
    """Guard: the committed tree under modern/testdata is byte for byte the same after the module ran."""
    before = tree_hashes(TESTDATA)
    yield
    assert tree_hashes(TESTDATA) == before, "a test or a tool changed a committed file under modern/testdata"


@pytest.fixture(scope="module")
def fresh_runs(tmp_path_factory):
    """The generator run twice into temp folders: different working folders and different PYTHONHASHSEED."""
    base = tmp_path_factory.mktemp("generated")
    runs = []
    for i, hash_seed in enumerate(("1", "2"), start=1):
        out, cwd = base / f"run{i}", base / f"cwd{i}"
        cwd.mkdir()
        proc = run_tool("generate.py", "--out", out, cwd=cwd, env_extra={"PYTHONHASHSEED": hash_seed})
        assert proc.returncode == 0, proc.stdout + proc.stderr
        runs.append(out)
    return runs


@pytest.fixture(params=["committed", "fresh"])
def root(request, fresh_runs) -> Path:
    """The committed datasets, and the tree a fresh generator run wrote into a temp folder."""
    return TESTDATA if request.param == "committed" else fresh_runs[0]


def copy_tree(dest: Path, with_csv: bool = True) -> Path:
    """A scratch copy of the committed generated files, for the negative controls."""
    for sub in ("fixed-width", "scenarios", "negative"):
        shutil.copytree(TESTDATA / sub, dest / sub)
    if with_csv:
        for name in ("manifest.csv", "coverage.csv", "coverage-gaps.csv"):
            shutil.copy(TESTDATA / name, dest / name)
    return dest


# ---- AC-1: deterministic, seed and business date documented -----------------------------------------------------------

def test_mam44_ac1_two_generator_runs_give_byte_identical_files(fresh_runs):
    """[MAM-44 AC-1] two runs (other folder, other PYTHONHASHSEED) write the same files with equal sha256."""
    a, b = (tree_hashes(r) for r in fresh_runs)
    assert GENERATED_FILES <= set(a), sorted(GENERATED_FILES - set(a))
    assert a == b


def test_mam44_ac1_the_generator_writes_only_the_documented_files(fresh_runs):
    """[MAM-44 AC-1] the output folder holds exactly the 13 generated files (nothing else, no stray temp file)."""
    assert set(tree_hashes(fresh_runs[0])) == GENERATED_FILES


def test_mam44_ac1_the_committed_files_equal_a_fresh_run(fresh_runs):
    """[MAM-44 AC-1] every generated file in modern/testdata has the sha256 of the same file of a fresh run."""
    fresh = tree_hashes(fresh_runs[0])
    committed = {rel: sha256(TESTDATA / rel) for rel in fresh if (TESTDATA / rel).is_file()}
    assert committed == fresh


def test_mam44_ac1_manifest_lists_size_records_and_sha256_of_every_generated_file(root):
    """[MAM-44 AC-1] manifest.csv names every other generated file with its layout, record count, bytes and sha256."""
    manifest = {r["path"]: r for r in csv_rows(root / "manifest.csv")}
    assert set(manifest) == GENERATED_FILES - {"manifest.csv"}
    for rel, r in manifest.items():
        data = (root / rel).read_bytes()
        assert r["sha256"] == hashlib.sha256(data).hexdigest() and int(r["bytes"]) == len(data), rel
        lines = data.count(b"\n")
        assert int(r["records"]) == (lines if rel.endswith(".dat") else lines - 1), rel
        assert r["layout"] == LAYOUT_OF.get(rel, "CSV"), rel


def test_mam44_ac1_the_readme_records_the_seed_and_the_frozen_business_date():
    """[MAM-44 AC-1] README.md names seed 20260115 and the business date 2026-01-15, and the generator uses that seed."""
    readme = (TESTDATA / "README.md").read_text(encoding="utf-8")
    seed = re.search(r"^\| Seed \| (\d+)", readme, re.M)
    frozen = re.search(r"^\| Frozen business date \| (\d{4}-\d{2}-\d{2})", readme, re.M)
    assert seed and seed.group(1) == "20260115"
    assert frozen and frozen.group(1) == "2026-01-15"
    pemdata = (TOOLS / "pemdata.py").read_text(encoding="utf-8")
    assert re.search(r"^SEED = 20260115\b", pemdata, re.M) and re.search(r'^BUSINESS_DATE = "20260115"', pemdata, re.M)


def test_mam44_ac1_the_frozen_business_date_is_the_application_date_of_every_intake_record():
    """[MAM-44 AC-1] the frozen date 2026-01-15 is really in the files: every application's effective date is 20260115."""
    for rel in ("fixed-width/appinput-baseline.dat", "scenarios/appinput-scenarios.dat"):
        assert {r["PRV-EFFECTIVE-DATE"] for r in rows(TESTDATA, rel)} == {BUSINESS_DATE}, rel


def test_mam44_ac1_the_business_dates_file_starts_with_the_frozen_date_and_names_three_run_dates(root):
    """[MAM-44 AC-1] scenarios/business-dates.csv: the frozen 2026-01-15 first, then three named run dates, all real dates."""
    rows_ = csv_rows(root / "scenarios/business-dates.csv")
    assert (rows_[0]["label"], rows_[0]["business_date"]) == ("frozen", "2026-01-15") and len(rows_) == 4
    assert all(real_date(r["business_date"].replace("-", "")) for r in rows_)


def test_mam44_ac1_check_datasets_confirms_determinism_with_two_fresh_processes(tmp_path):
    """[MAM-44 AC-1] the shipped checker (two scratch runs, committed tree, manifest, README) exits 0 with no FAIL line."""
    proc = run_tool("check_datasets.py", "--scratch", tmp_path)
    assert proc.returncode == 0, proc.stdout + proc.stderr
    assert not [l for l in proc.stdout.splitlines() if l.startswith("FAIL")], proc.stdout
    assert "PASS: AC-1 two generator runs give byte-identical files" in proc.stdout


# ---- AC-2: enrolment intake covers every validation branch ------------------------------------------------------------

def classify(app_rows: list) -> list:
    """The enrolment validation (E101 to E104, D-016) in file order: the set of error codes of each application. A duplicate
    is an NPI already accepted earlier in the file."""
    accepted, out = set(), []
    for r in app_rows:
        npi, codes = r["PRV-NPI"], set()
        if not (len(npi) == 10 and npi.isdigit()):
            codes.add("E101")
        elif npi in accepted:
            codes.add("E103")
        if r["PRV-TYPE-CODE"] not in ("IN", "GR", "FA"):
            codes.add("E102")
        if not r["PRV-TAXONOMY"]:
            codes.add("E104")
        if not codes:
            accepted.add(npi)
        out.append(codes)
    return out


def test_mam44_ac2_every_intake_record_is_200_bytes(root):
    """[MAM-44 AC-2] every record of appinput-baseline.dat is 200 bytes (the APPINPUT layout length)."""
    recs = records(root / "fixed-width/appinput-baseline.dat")
    assert layout("APPINPUT")["length"] == 200
    assert recs and {len(r) for r in recs} == {200}


@pytest.mark.parametrize("branch", ["valid IN", "valid GR", "valid FA", "E101", "E102", "E104", "E103"])
def test_mam44_ac2_the_baseline_holds_a_record_for_each_validation_branch(root, branch):
    """[MAM-44 AC-2] one valid IN, GR and FA; one missing NPI (E101), invalid type (E102), missing taxonomy (E104), duplicate NPI (E103)."""
    app_rows = rows(root, "fixed-width/appinput-baseline.dat")
    seen = set()
    for r, codes in zip(app_rows, classify(app_rows)):
        seen |= codes or {"valid " + r["PRV-TYPE-CODE"]}
    assert branch in seen, sorted(seen)


def test_mam44_ac2_the_missing_npi_record_has_a_blank_npi_and_the_missing_taxonomy_record_a_blank_taxonomy():
    """[MAM-44 AC-2] E101 is a blank NPI field and E104 a blank taxonomy field (not a malformed value in disguise)."""
    app_rows = rows(TESTDATA, "fixed-width/appinput-baseline.dat")
    codes = classify(app_rows)
    assert any(c == {"E101"} and r["PRV-NPI"] == "" for r, c in zip(app_rows, codes))
    assert any(c == {"E104"} and r["PRV-TAXONOMY"] == "" for r, c in zip(app_rows, codes))
    assert any(c == {"E102"} and r["PRV-TYPE-CODE"] not in ("IN", "GR", "FA", "") for r, c in zip(app_rows, codes))


def test_mam44_ac2_the_duplicate_repeats_the_npi_of_an_earlier_valid_application():
    """[MAM-44 AC-2] the E103 record carries the NPI of an earlier record that is itself valid."""
    app_rows = rows(TESTDATA, "fixed-width/appinput-baseline.dat")
    codes = classify(app_rows)
    first_valid = {r["PRV-NPI"] for r, c in zip(app_rows, codes) if not c}
    dups = [r["PRV-NPI"] for r, c in zip(app_rows, codes) if "E103" in c]
    assert dups and set(dups) <= first_valid


def test_mam44_ac2_the_valid_applications_are_new_providers_not_seeded_ones():
    """[MAM-44 AC-2] a valid application must not collide with the stored provider master (that would be E103, not valid)."""
    app_rows = rows(TESTDATA, "fixed-width/appinput-baseline.dat")
    valid = {r["PRV-NPI"] for r, c in zip(app_rows, classify(app_rows)) if not c}
    seeded = {r["PRV-NPI"] for r in rows(TESTDATA, "fixed-width/provider-master-seed.dat")}
    assert len(valid) >= 3 and not (valid & seeded)


# ---- AC-3: provider master seed covers every state --------------------------------------------------------------------

def test_mam44_ac3_every_provider_master_record_is_130_bytes(root):
    """[MAM-44 AC-3] every record of provider-master-seed.dat is 130 bytes (D-007), and so is the edge file."""
    assert layout("PRVRECRD")["length"] == 130
    for rel in ("fixed-width/provider-master-seed.dat", "fixed-width/provider-master-edge.dat"):
        recs = records(root / rel)
        assert recs and {len(r) for r in recs} == {130}, rel


@pytest.mark.parametrize("column,wanted", [("PRV-ENROLL-STATUS", set("PATDS")), ("PRV-SCREEN-FLAG", set("UPF")),
                                           ("PRV-RISK-TIER", set("LMH"))])
def test_mam44_ac3_the_seed_holds_every_status_screen_flag_and_risk_tier(root, column, wanted):
    """[MAM-44 AC-3] statuses P A T D S, screen flags U P F, risk tiers L M H are all present."""
    present = {r[column] for r in rows(root, "fixed-width/provider-master-seed.dat")}
    assert wanted <= present, sorted(wanted - present)


@pytest.mark.parametrize("prefix", ["332", "251", "341", "291", "225"])
def test_mam44_ac3_the_seed_holds_each_risk_taxonomy_prefix(root, prefix):
    """[MAM-44 AC-3] taxonomy prefixes 332, 251, 341, 291 and 225 each appear."""
    assert prefix in {r["PRV-TAXONOMY"][:3] for r in rows(root, "fixed-width/provider-master-seed.dat")}


def test_mam44_ac3_the_seed_holds_a_taxonomy_prefix_outside_the_five(root):
    """[MAM-44 AC-3] at least one taxonomy whose prefix is none of 332, 251, 341, 291, 225 (the Limited-tier case)."""
    prefixes = {r["PRV-TAXONOMY"][:3] for r in rows(root, "fixed-width/provider-master-seed.dat") if r["PRV-TAXONOMY"]}
    assert prefixes - {"332", "251", "341", "291", "225"}


@pytest.mark.parametrize("relation", ["before", "equal", "after"])
def test_mam44_ac3_revalidation_due_dates_fall_before_on_and_after_the_business_date(root, relation):
    """[MAM-44 AC-3] due dates before, equal to and after 2026-01-15 (the no-due-date value 99991231 does not count)."""
    dues = [r["PRV-REVAL-DUE-DATE"] for r in rows(root, "fixed-width/provider-master-seed.dat")
            if r["PRV-REVAL-DUE-DATE"] != NO_DATE]
    test = {"before": lambda d: d < BUSINESS_DATE, "equal": lambda d: d == BUSINESS_DATE,
            "after": lambda d: d > BUSINESS_DATE}[relation]
    assert any(test(d) for d in dues), dues


def test_mam44_ac3_every_date_is_eight_digits_with_a_four_digit_year(root):
    """[MAM-44 AC-3] effective and due dates are real CCYYMMDD dates, year 1900 or later: never YYMMDD, never YYDDD (D-004)."""
    for rel in ("fixed-width/provider-master-seed.dat", "fixed-width/provider-master-edge.dat"):
        for n, r in enumerate(rows(root, rel), 1):
            for col in ("PRV-EFFECTIVE-DATE", "PRV-REVAL-DUE-DATE"):
                v = r[col]
                assert re.fullmatch(r"\d{8}", v) and real_date(v) and int(v[:4]) >= 1900, (rel, n, col, v)


def test_mam44_ac3_the_risk_tier_of_each_provider_follows_its_taxonomy_prefix():
    """[MAM-44 AC-3] legacy PRVRSK01: 332 and 251 High, 341 291 225 Moderate, any other Limited; a pending or denied provider has no tier yet."""
    expected = {"332": "H", "251": "H", "341": "M", "291": "M", "225": "M"}
    for n, r in enumerate(rows(TESTDATA, "fixed-width/provider-master-seed.dat"), 1):
        assert r["PRV-RISK-TIER"] in ("", expected.get(r["PRV-TAXONOMY"][:3], "L")), (n, r["PRV-TAXONOMY"], r["PRV-RISK-TIER"])
        if r["PRV-ENROLL-STATUS"] in ("A", "T", "S"):
            assert r["PRV-RISK-TIER"] != "", n


def test_mam44_ac3_npis_are_unique_and_match_the_16_providers_of_the_sql_seed_in_order():
    """[MAM-44 AC-3] 16 providers, unique NPIs, the same NPIs in the same order as seed/provider_db/01-providers.sql."""
    npis = [r["PRV-NPI"] for r in rows(TESTDATA, "fixed-width/provider-master-seed.dat")]
    sql = (TESTDATA / "seed/provider_db/01-providers.sql").read_text(encoding="utf-8")
    sql_npis = re.findall(r"^\s+\('[0-9a-f-]{36}', '(\d{10})'", sql, re.M)
    assert len(npis) == len(set(npis)) == 16
    assert npis == sql_npis


def test_mam44_ac3_check_datasets_confirms_the_seed_and_the_edge_file():
    """[MAM-44 AC-3] the shipped checker's AC-3 lines all pass (statuses, flags, tiers, prefixes, dates, SQL seed, edge file)."""
    proc = run_tool("check_datasets.py", "--skip-determinism")
    lines = [l for l in proc.stdout.splitlines() if l.startswith(("PASS: AC-3", "FAIL: AC-3"))]
    assert proc.returncode == 0, proc.stdout
    assert len(lines) >= 10 and all(l.startswith("PASS") for l in lines)


# ---- AC-4: sanction input covers every action -------------------------------------------------------------------------

def test_mam44_ac4_every_sanction_record_is_80_bytes(root):
    """[MAM-44 AC-4] every record of sancin-baseline.dat is 80 bytes."""
    recs = records(root / "fixed-width/sancin-baseline.dat")
    assert layout("SANCIN")["length"] == 80
    assert recs and {len(r) for r in recs} == {80}


@pytest.mark.parametrize("action", ["T", "S", "R"])
def test_mam44_ac4_the_baseline_holds_each_sanction_action(root, action):
    """[MAM-44 AC-4] terminate T, suspend S and reinstate R each appear."""
    assert action in {r["SAN-ACTION"] for r in rows(root, "fixed-width/sancin-baseline.dat")}


def test_mam44_ac4_the_baseline_holds_a_record_for_an_unknown_npi(root):
    """[MAM-44 AC-4] a well-formed record whose NPI is in neither the provider master seed nor the edge file."""
    known = {r["PRV-NPI"] for rel in ("fixed-width/provider-master-seed.dat", "fixed-width/provider-master-edge.dat")
             for r in rows(root, rel)}
    unknown = [r for r in rows(root, "fixed-width/sancin-baseline.dat") if r["SAN-NPI"] not in known]
    assert unknown and all(re.fullmatch(r"\d{10}", r["SAN-NPI"]) for r in unknown)


def test_mam44_ac4_the_baseline_holds_a_record_with_an_invalid_action_code(root):
    """[MAM-44 AC-4] an action that is not T, S or R (D-026), on a provider that exists."""
    seeded = {r["PRV-NPI"] for r in rows(root, "fixed-width/provider-master-seed.dat")}
    invalid = [r for r in rows(root, "fixed-width/sancin-baseline.dat") if r["SAN-ACTION"] not in ("T", "S", "R")]
    assert invalid and any(r["SAN-NPI"] in seeded for r in invalid)


def test_mam44_ac4_every_sanction_effective_date_is_a_real_ccyymmdd_date(root):
    """[MAM-44 AC-4] effective dates are 8-digit real dates with a 4-digit year (D-004)."""
    for r in rows(root, "fixed-width/sancin-baseline.dat"):
        assert re.fullmatch(r"\d{8}", r["SAN-EFF-DATE"]) and real_date(r["SAN-EFF-DATE"]), r["SAN-EFF-DATE"]


# ---- AC-5: the eight sample applications become legacy intake records -------------------------------------------------

QF2_MAPPING = {"IND": "IN", "PT": "IN", "GRP": "GR", "DME": "GR", "HOSP": "FA", "HOS": "FA", "AMB": "FA", "LAB": "FA",
               "HHA": "FA"}                                                     # Q-079 / QF-2 option A (default applied)
EIGHT_NPIS = ["1003000111", "1003000222", "1003000333", "1003000444", "1003000555", "1003000666", "1003000777", "1003000888"]


def test_mam44_ac5_the_feed_holds_the_eight_applications_the_spec_names():
    """[MAM-44 AC-5] aws-transform/test-data/providers.txt has exactly the eight NPIs of the spec (the input of this AC)."""
    assert [a["npi"] for a in feed_applications()] == EIGHT_NPIS


def test_mam44_ac5_a_200_byte_intake_record_exists_for_each_of_the_eight_npis(root):
    """[MAM-44 AC-5] scenarios/appinput-scenarios.dat: eight records of 200 bytes, one per NPI, in the order of the feed."""
    recs = records(root / "scenarios/appinput-scenarios.dat")
    assert {len(r) for r in recs} == {200}
    assert [r["PRV-NPI"] for r in rows(root, "scenarios/appinput-scenarios.dat")] == EIGHT_NPIS


def test_mam44_ac5_each_feed_type_code_is_mapped_to_in_gr_or_fa_by_qf2(root):
    """[MAM-44 AC-5] IND and PT to IN; GRP and DME to GR; HOSP, HOS, AMB, LAB and HHA to FA."""
    for app, r in zip(feed_applications(), rows(root, "scenarios/appinput-scenarios.dat")):
        assert r["PRV-TYPE-CODE"] == QF2_MAPPING[app["type"]], (app["npi"], app["type"], r["PRV-TYPE-CODE"])


def test_mam44_ac5_the_type_mapping_file_is_the_qf2_table_and_covers_every_feed_code(root):
    """[MAM-44 AC-5] scenarios/provider-type-mapping.csv equals the QF-2 option A table, one row per feed code."""
    mapping = {r["feed_code"]: r["legacy_type"] for r in csv_rows(root / "scenarios/provider-type-mapping.csv")}
    assert mapping == QF2_MAPPING
    assert {a["type"] for a in feed_applications()} <= set(mapping)


def test_mam44_ac5_synthetic_distinct_medicaid_ids_and_taxonomies_are_added(root):
    """[MAM-44 AC-5] a 9-digit Medicaid ID (all different) and a taxonomy on every record; 207Q00000X for 1003000111."""
    scn = rows(root, "scenarios/appinput-scenarios.dat")
    ids = [r["PRV-MEDICAID-ID"] for r in scn]
    assert all(re.fullmatch(r"\d{9}", i) for i in ids) and len(set(ids)) == 8
    assert all(re.fullmatch(r"[0-9A-Z]{10}", r["PRV-TAXONOMY"]) for r in scn)
    assert next(r for r in scn if r["PRV-NPI"] == "1003000111")["PRV-TAXONOMY"] == "207Q00000X"


def test_mam44_ac5_the_feed_names_are_carried_over_verbatim(root):
    """[MAM-44 AC-5] an individual reads LAST + space + FIRST, an organisation its legal name: the feed name exactly."""
    for app, r in zip(feed_applications(), rows(root, "scenarios/appinput-scenarios.dat")):
        name = f"{r['PRV-LAST-NAME']} {r['PRV-FIRST-NAME']}".strip() if r["PRV-TYPE-CODE"] == "IN" else r["PRV-LEGAL-NAME"]
        assert name == app["name"], app["npi"]


def test_mam44_ac5_the_expected_outcome_list_names_each_application(root):
    """[MAM-44 AC-5] 444 FAIL E203, 555 FAIL E202, 111 222 333 777 888 PASS, 666 has no expected outcome yet (QF-14)."""
    got = {r["npi"]: (r["expected_outcome"], r["expected_code"]) for r in csv_rows(root / "scenarios/expected-outcomes.csv")}
    want = {n: ("PASS", "") for n in ("1003000111", "1003000222", "1003000333", "1003000777", "1003000888")}
    want.update({"1003000444": ("FAIL", "E203"), "1003000555": ("FAIL", "E202"), "1003000666": ("NONE", "")})
    assert got == want


# ---- AC-6: coverage of at least 95% of the rules ----------------------------------------------------------------------

def non_dropped_rules() -> list:
    return [r["rule_id"] for r in csv_rows(RULES) if r["service"] != "DROPPED"]


def fixture_record_count(root: Path, rel: str) -> int:
    return len(records(root / rel)) if rel.endswith(".dat") else len(csv_rows(root / rel))


def test_mam44_ac6_the_rule_list_holds_433_rules_that_are_not_dropped():
    """[MAM-44 AC-6] the denominator: 433 non-DROPPED rules in docs/architecture/rule-to-service.csv, each listed once."""
    rules = non_dropped_rules()
    assert len(rules) == 433 and len(set(rules)) == 433


def test_mam44_ac6_coverage_csv_has_the_columns_rule_id_fixture_file_and_record_ref(root):
    """[MAM-44 AC-6] the header starts with rule_id, fixture_file, record_ref."""
    with (root / "coverage.csv").open(encoding="utf-8", newline="") as f:
        assert next(csv.reader(f))[:3] == ["rule_id", "fixture_file", "record_ref"]


def test_mam44_ac6_at_least_95_percent_of_the_rules_have_a_fixture_row(root):
    """[MAM-44 AC-6] distinct covered rules, all of them non-DROPPED rules, are at least 95% of the 433."""
    rules = set(non_dropped_rules())
    covered = [r["rule_id"] for r in csv_rows(root / "coverage.csv")]
    assert len(covered) == len(set(covered)), "a rule has two rows"
    assert set(covered) <= rules, sorted(set(covered) - rules)[:5]
    assert len(covered) / len(rules) >= 0.95, f"{len(covered)} of {len(rules)}"


def test_mam44_ac6_every_uncovered_rule_is_listed_with_a_reason_and_nothing_is_lost_or_double_counted(root):
    """[MAM-44 AC-6] covered and listed-as-uncovered rules partition the 433; each uncovered row has a reason of real text."""
    rules = set(non_dropped_rules())
    covered = {r["rule_id"] for r in csv_rows(root / "coverage.csv")}
    gaps = csv_rows(root / "coverage-gaps.csv")
    gap_ids = [g["rule_id"] for g in gaps]
    assert len(gap_ids) == len(set(gap_ids)) and not (covered & set(gap_ids))
    assert covered | set(gap_ids) == rules
    assert all(len(g["reason"].strip()) >= 15 for g in gaps), [g["rule_id"] for g in gaps if len(g["reason"].strip()) < 15]


def test_mam44_ac6_every_coverage_row_points_at_a_real_fixture_file_and_record(root):
    """[MAM-44 AC-6] fixture_file exists; record_ref is 'all' or up to three record numbers inside that file; basis is a known kind."""
    for r in csv_rows(root / "coverage.csv"):
        rel = r["fixture_file"]
        assert (root / rel).is_file(), r
        ref = r["record_ref"]
        if ref != "all":
            m = re.fullmatch(r"rec:(\d+(?:;\d+){0,2})", ref)
            assert m, r
            count = fixture_record_count(root, rel)
            assert all(1 <= int(n) <= count for n in m.group(1).split(";")), (r, count)
        assert r["basis"] in ("record", "run", "lookup", "date"), r


def test_mam44_ac6_the_coverage_report_tool_confirms_the_measure(root):
    """[MAM-44 AC-6] coverage_report.py recomputes the rows from the files: exit 0, 433 rules, at least 95.00% covered."""
    proc = run_tool("coverage_report.py", "--root", root)
    assert proc.returncode == 0, proc.stdout + proc.stderr
    assert "rules (non-DROPPED): 433" in proc.stdout
    pct = float(re.search(r"covered:\s+\d+ \(([\d.]+)%\)", proc.stdout).group(1))
    assert pct >= 95.0 and "OK: coverage at or above 95%" in proc.stdout


def test_mam44_ac6_coverage_is_computed_from_the_files_so_an_emptied_fixture_makes_the_check_fail(tmp_path):
    """[MAM-44 AC-6] negative control: with provider-master-edge.dat emptied, the recomputation no longer matches
    coverage.csv (exit 1); rewriting it moves those rules to the gap list with a reason and coverage drops below 95%."""
    scratch = copy_tree(tmp_path / "tree")
    (scratch / "fixed-width/provider-master-edge.dat").write_bytes(b"")
    stale = run_tool("coverage_report.py", "--root", scratch)
    assert stale.returncode == 1 and "differs from the recomputed coverage" in stale.stdout
    rewritten = run_tool("coverage_report.py", "--root", scratch, "--write")
    assert rewritten.returncode == 1 and "FAIL: coverage below 95%" in rewritten.stdout
    gaps = csv_rows(scratch / "coverage-gaps.csv")
    emptied = [g for g in gaps if "no record of fixed-width/provider-master-edge.dat" in g["reason"]]
    assert len(gaps) > 20 and len(emptied) >= 5
    assert all(g["reason"].strip() for g in gaps)


# ---- AC-7: no real data -----------------------------------------------------------------------------------------------

def generated_data_files(root: Path):
    for rel in sorted(GENERATED_FILES):
        yield rel, root / rel


def all_npis(root: Path):
    """Every NPI value of the generated files: (relative path, where, value). Fixed-width files by layout position, the
    CSV files by every ten-digit number in the text."""
    for rel, path in generated_data_files(root):
        if rel.endswith(".dat"):
            lay = layout(LAYOUT_OF[rel])
            for n, rec in enumerate(records(path), 1):
                for field, v in parse(rec, lay, all_views=True).items():
                    if field in ("PRV-NPI", "PRV-OWNER-NPI", "SAN-NPI") and (v or field == "SAN-NPI"):
                        yield rel, f"record {n} {field}", v
        else:
            text = re.sub(r"[0-9a-f]{64}", "", path.read_text(encoding="utf-8"))
            for v in sorted(set(re.findall(r"(?<!\d)\d{10}(?!\d)", text))):
                yield rel, "text", v


def test_mam44_ac7_every_generated_npi_has_a_valid_check_digit_and_lies_outside_the_reserved_ranges(root):
    """[MAM-44 AC-7] Luhn with the 80840 prefix, outside 1003000000-999 and 1990000000-999, except the fabricated
    test-data NPIs, which are allowed verbatim in scenarios/ and only there."""
    allowed, _ = feed_npis_and_names()
    seen = 0
    for rel, where, v in all_npis(root):
        seen += 1
        if rel.startswith("scenarios/") and v in allowed:
            continue
        assert v not in allowed, f"{rel} {where}: test-data NPI {v} outside scenarios/"
        assert npi_valid(v), f"{rel} {where}: {v} has no valid check digit"
        assert not reserved(v), f"{rel} {where}: {v} is in a reserved range"
    assert seen > 50


def test_mam44_ac7_the_test_data_npis_are_used_in_scenarios_and_nowhere_else(root):
    """[MAM-44 AC-7] the eight applicant NPIs and the LEIE owner 1990000111 appear in scenarios/, and in no other generated file."""
    allowed, _ = feed_npis_and_names()
    where = {}
    for rel, _, v in all_npis(root):
        if v in allowed:
            where.setdefault(v, set()).add(rel.split("/")[0])
    assert set(EIGHT_NPIS) <= set(where) and "1990000111" in where
    assert all(dirs == {"scenarios"} for dirs in where.values()), where


def test_mam44_ac7_generated_names_are_obviously_fake_and_scenario_names_are_the_feed_names(root):
    """[MAM-44 AC-7] first name TESTPROV for individuals, legal names starting SAMPLE for organisations; in scenarios/ only the feed's names."""
    _, feed_names = feed_npis_and_names()
    for rel, path in generated_data_files(root):
        if not rel.endswith(".dat"):
            continue
        lay = layout(LAYOUT_OF[rel])
        for n, rec in enumerate(records(path), 1):
            r = parse(rec, lay)
            typ = r.get("PRV-TYPE-CODE")
            if typ is None:
                continue                                           # SANCIN holds no names
            if rel.startswith("scenarios/"):
                name = f"{r['PRV-LAST-NAME']} {r['PRV-FIRST-NAME']}".strip() if typ == "IN" else r["PRV-LEGAL-NAME"]
                assert name in feed_names, (rel, n, name)
            elif typ == "IN":
                assert r["PRV-FIRST-NAME"] == "TESTPROV", (rel, n, r["PRV-FIRST-NAME"])
            elif typ in ("GR", "FA"):
                assert r["PRV-LEGAL-NAME"].startswith("SAMPLE "), (rel, n, r["PRV-LEGAL-NAME"])
                assert r["PRV-DBA-NAME"] == "" or r["PRV-DBA-NAME"].startswith("SAMPLE "), (rel, n, r["PRV-DBA-NAME"])
            else:                                                  # an invalid type (E102): the bytes read as one of the two views
                a = parse(rec, lay, all_views=True)
                assert a["PRV-FIRST-NAME"] == "TESTPROV" or a["PRV-LEGAL-NAME"].startswith("SAMPLE "), (rel, n, typ)


def test_mam44_ac7_no_street_address_email_or_ssn_shaped_number_is_generated(root):
    """[MAM-44 AC-7] the only address is 123 TEST ST, e-mail only at example.test or providers.pem.local, no ###-##-####."""
    street = re.compile(r"\b\d{1,5} [A-Z][A-Z ]{1,30} (?:ST|STREET|AVE|AVENUE|RD|ROAD|BLVD|DR|DRIVE|LN|LANE)\b")
    for rel, path in generated_data_files(root):
        text = re.sub(r"[0-9a-f]{64}", "", path.read_bytes().decode("latin-1"))
        assert all(m == "123 TEST ST" for m in street.findall(text)), rel
        assert all(m.endswith(("@example.test", "@providers.pem.local")) for m in re.findall(r"[\w.+-]+@[\w-]+(?:\.[\w-]+)+", text)), rel
        assert not re.search(r"(?<!\d)\d{3}-\d{2}-\d{4}(?!\d)", text), rel


def test_mam44_ac7_the_scan_passes_on_the_datasets(root):
    """[MAM-44 AC-7] scan_no_real_data.py over the committed tree and over a fresh run: exit 0, 'no real data found'."""
    proc = run_tool("scan_no_real_data.py", "--root", root)
    assert proc.returncode == 0, proc.stdout + proc.stderr
    assert proc.stdout.startswith("OK: no real data found") and "FAIL" not in proc.stdout


def test_mam44_ac7_the_scan_self_test_catches_its_five_injected_violations():
    """[MAM-44 AC-7] --self-test: the clean copy passes, each of five injected violations is reported, exit 0."""
    proc = run_tool("scan_no_real_data.py", "--self-test")
    lines = proc.stdout.splitlines()
    assert proc.returncode == 0, proc.stdout + proc.stderr
    assert "PASS: the clean copy reports 0 findings" in lines
    assert len([l for l in lines if l.startswith("PASS: injected")]) == 5 and not [l for l in lines if l.startswith("FAIL")]


def put(path: Path, record_no: int, field: str, value: str) -> None:
    """Overwrite one field of one record of a fixed-width file by layout position (the negative controls' injection)."""
    rel = path.parent.name + "/" + path.name
    lay = layout(LAYOUT_OF[rel])
    f = next(f for f in lay["fields"] if f["name"] == field)
    recs = records(path)
    rec = bytearray(recs[record_no - 1])
    rec[f["start"] - 1:f["start"] - 1 + f["length"]] = value.ljust(f["length"]).encode("ascii")
    recs[record_no - 1] = bytes(rec)
    path.write_bytes(b"".join(r + b"\n" for r in recs))


def bump_last_digit(npi: str) -> str:
    """The same NPI with another last digit: the check digit is unique, so the result is never valid."""
    return npi[:9] + str((int(npi[9]) + 1) % 10)


def append_line(path: Path, line: str) -> None:
    path.write_bytes(path.read_bytes() + (line + "\n").encode("ascii"))


assert not ({with_check_digit("100300099"), with_check_digit("100300093"), with_check_digit("199000044")} & feed_npis_and_names()[0])
EDGE, SEED, SCN = "fixed-width/provider-master-edge.dat", "fixed-width/provider-master-seed.dat", "scenarios/appinput-scenarios.dat"
BAD_CASES = {
    "an NPI with an invalid check digit": (
        lambda t: put(t / EDGE, 1, "PRV-NPI", bump_last_digit(rows(t, EDGE)[0]["PRV-NPI"])), "check digit"),
    "a Luhn-valid NPI in the reserved range 1003000xxx": (
        lambda t: put(t / EDGE, 1, "PRV-NPI", with_check_digit("100300099")), "reserved range"),
    "a Luhn-valid NPI in the reserved range 1990000xxx": (
        lambda t: put(t / EDGE, 2, "PRV-NPI", with_check_digit("199000044")), "reserved range"),
    "a test-data NPI outside scenarios/": (lambda t: put(t / SEED, 2, "PRV-NPI", "1003000222"), "only allowed verbatim in scenarios/"),
    "an NPI that is not ten digits": (lambda t: put(t / EDGE, 3, "PRV-NPI", "12345"), "not 10 digits"),
    "an individual with a real-looking first name": (lambda t: put(t / EDGE, 1, "PRV-FIRST-NAME", "MARIA"), "TESTPROV"),
    "an organisation whose legal name is not SAMPLE": (lambda t: put(t / EDGE, 5, "PRV-LEGAL-NAME", "ACME HEALTH LLC"), "SAMPLE"),
    "a scenario name that is not in the feed": (lambda t: put(t / SCN, 1, "PRV-LAST-NAME", "GARCIA"), "not a name of aws-transform"),
    "a street address in a CSV file": (lambda t: append_line(t / "coverage-gaps.csv", "R-X,lives at 456 OAK AVE"), "street address"),
    "an e-mail address in a CSV file": (lambda t: append_line(t / "coverage-gaps.csv", "R-X,mail jane.doe@gmail.com"), "e-mail address"),
    "an SSN-shaped number in a CSV file": (lambda t: append_line(t / "coverage-gaps.csv", "R-X,id 123-45-6789"), "social-security"),
    "a reserved-range NPI in a CSV file": (
        lambda t: append_line(t / "coverage-gaps.csv", "R-X,npi " + with_check_digit("100300093")), "reserved range"),
}


@pytest.mark.parametrize("case", sorted(BAD_CASES))
def test_mam44_ac7_the_scan_fails_on_each_kind_of_non_synthetic_data(tmp_path, case):
    """[MAM-44 AC-7] negative control, injected by this test (not the tool's own self-test): exit 1 and a FAIL line naming the problem."""
    inject, expected = BAD_CASES[case]
    scratch = copy_tree(tmp_path / "tree")
    assert run_tool("scan_no_real_data.py", "--root", scratch).returncode == 0          # the copy is clean before the injection
    inject(scratch)
    proc = run_tool("scan_no_real_data.py", "--root", scratch)
    assert proc.returncode == 1, proc.stdout
    assert any(l.startswith("FAIL:") and expected in l for l in proc.stdout.splitlines()), proc.stdout


def test_mam44_ac7_the_scan_does_not_flag_properly_fabricated_values(tmp_path):
    """[MAM-44 AC-7] control for false alarms: a valid new NPI (10061xxxxx), 123 TEST ST and an example.test address pass."""
    scratch = copy_tree(tmp_path / "tree")
    put(scratch / EDGE, 1, "PRV-NPI", with_check_digit("100619999"))
    append_line(scratch / "coverage-gaps.csv", "R-X,send to 123 TEST ST or ops@example.test")
    proc = run_tool("scan_no_real_data.py", "--root", scratch)
    assert proc.returncode == 0, proc.stdout


def test_mam44_ac7_the_scan_fails_when_there_is_nothing_to_scan(tmp_path):
    """[MAM-44 AC-7] an empty folder is not a pass: exit 1, 'no generated file found'."""
    (tmp_path / "empty").mkdir()
    proc = run_tool("scan_no_real_data.py", "--root", tmp_path / "empty")
    assert proc.returncode == 1 and "no generated file found" in proc.stdout


# ---- AC-8: every file parses with the shared library (Python side only; the Java round trip is RoundTrip.java) -----------

DATASETS = sorted(p.relative_to(TESTDATA).as_posix() for sub in ("fixed-width", "scenarios") for p in (TESTDATA / sub).glob("*.dat"))


def test_mam44_ac8_every_dataset_under_fixed_width_and_scenarios_has_a_layout_here_and_in_the_manifest():
    """[MAM-44 AC-8] no dataset is left unchecked: each .dat under fixed-width/ and scenarios/ is mapped to a layout, and
    manifest.csv names the same layout."""
    manifest = {r["path"]: r["layout"] for r in csv_rows(TESTDATA / "manifest.csv")}
    assert DATASETS and set(DATASETS) <= set(LAYOUT_OF), sorted(set(DATASETS) - set(LAYOUT_OF))
    assert all(manifest[rel] == LAYOUT_OF[rel] for rel in DATASETS)


def test_mam44_ac8_the_exported_layouts_have_the_lengths_the_spec_names():
    """[MAM-44 AC-8] PRVRECRD 130 (provider master, D-007), APPINPUT 200, SANCIN 80, read from the exported layout JSON."""
    assert {n: layout(n)["length"] for n in LAYOUT_LENGTH} == LAYOUT_LENGTH


@pytest.mark.parametrize("rel", DATASETS)
def test_mam44_ac8_every_record_has_the_exact_length_of_its_layout(rel):
    """[MAM-44 AC-8] each record is as long as the layout of the file, one LF after each record and nothing else."""
    length = layout(LAYOUT_OF[rel])["length"]
    data = (TESTDATA / rel).read_bytes()
    recs = records(TESTDATA / rel)
    assert recs and {len(r) for r in recs} == {length}, (rel, sorted({len(r) for r in recs}))
    assert len(data) == len(recs) * (length + 1)


@pytest.mark.parametrize("rel", DATASETS)
def test_mam44_ac8_every_date_and_digit_field_is_what_the_codec_accepts(rel):
    """[MAM-44 AC-8] the validation of FixedRecord.validate: every DATE_CCYYMMDD field is 8 digits and a possible date,
    every other digit field is numeric (fields of the other REDEFINES views are not checked), for each record."""
    lay = layout(LAYOUT_OF[rel])
    for n, rec in enumerate(records(TESTDATA / rel), 1):
        view = view_of(rec, lay)
        for f in lay["fields"]:
            if f["type"] == "ALPHANUMERIC" or (f["views"] and view not in f["views"]):
                continue
            raw = rec[f["start"] - 1:f["start"] - 1 + f["length"]].decode("latin-1")
            assert raw.isdigit(), (rel, n, f["name"], raw)
            if f["type"] == "DATE_CCYYMMDD":
                assert real_date(raw), (rel, n, f["name"], raw)


@pytest.mark.parametrize("rel", [r for r in DATASETS if LAYOUT_OF[r] == "PRVRECRD"])
def test_mam44_ac8_every_provider_master_record_selects_one_of_the_three_views(rel):
    """[MAM-44 AC-8] the type code IN, GR or FA picks the REDEFINES view of the provider master (130 bytes)."""
    lay = layout("PRVRECRD")
    assert {view_of(r, lay) for r in records(TESTDATA / rel)} <= {"IN", "GR", "FA"}


@pytest.mark.parametrize("name", sorted(LAYOUT_LENGTH))
def test_mam44_ac8_the_layout_describes_every_byte_of_the_record(name):
    """[MAM-44 AC-8] the fields of a layout cover bytes 1 to its length with no gap, so reading all its fields and writing
    them back cannot change a byte."""
    lay = layout(name)
    covered = set()
    for f in lay["fields"]:
        assert f["start"] >= 1 and f["start"] + f["length"] - 1 <= lay["length"], f["name"]
        covered |= set(range(f["start"], f["start"] + f["length"]))
    assert covered == set(range(1, lay["length"] + 1)), sorted(set(range(1, lay["length"] + 1)) - covered)


def test_mam44_ac8_the_negative_files_hold_exactly_the_records_the_codec_must_refuse():
    """[MAM-44 AC-8] D-024 counterpart: appinput-bad-length.dat has one 199-byte record (3rd), sancin-malformed.dat one
    79-byte record (3rd) and an impossible date (2nd); every other record is as long as its layout."""
    app = records(TESTDATA / "negative/appinput-bad-length.dat")
    san = records(TESTDATA / "negative/sancin-malformed.dat")
    assert [len(r) for r in app] == [200, 200, 199, 200]
    assert [len(r) for r in san] == [80, 80, 79, 80]
    assert not real_date(parse(san[1], layout("SANCIN"))["SAN-EFF-DATE"])


def test_mam44_ac8_the_java_round_trip_is_the_hand_run_tool_and_reads_the_manifest():
    """[MAM-44 AC-8] LIMIT of this file: the codec round trip is not run here. RoundTrip.java must exist and drive
    FixedWidthCodec.read and write over every .dat file of manifest.csv, so the by-hand run covers what pytest cannot."""
    source = (TOOLS / "RoundTrip.java").read_text(encoding="utf-8")
    assert "manifest.csv" in source and "FixedWidthCodec.forLayout" in source
    assert "codec.read(" in source and "codec.write(" in source and "negative/" in source
