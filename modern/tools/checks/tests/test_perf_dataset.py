"""MAM-88 AC-1, data side only: the deterministic 10,000-provider synthetic set of the performance smoke.
Tests first (2026-11-20): the generator mode these tests drive does not exist yet.

Interface under test (the smallest addition to modern/tools/data/generate.py; every existing command and output stays byte for byte):

    python modern/tools/data/generate.py perf --count 10000 --out <folder>

  --out is required (nothing is ever written into the repository by default); --count defaults to 10000. The seed is the recorded
  one, 20260115 (modern/testdata/README.md). The folder receives exactly three files:
    provider-master-perf.dat   PRVRECRD, 130-byte records, one LF per record, the perf providers
    provider-perf.sql          the same providers as rows of provider_db.provider: ONE column list identical to
                               testdata/seed/provider_db/01-providers.sql, one row per line, additive (no TRUNCATE, no DELETE)
                               and re-runnable (INSERT ... ON CONFLICT DO NOTHING)
    revalidation-perf.sql      the same providers as rows of revalidation_db.tracked_provider (npi, enroll_status,
                               reval_due_date): the sweep reads that read model, not provider_db

  The NPIs cannot come from the 10061xxxxx block of the small set (10061 + 4 digits = 10,000 slots in all, and that block already
  holds the edge, intake and sanction NPIs): the tests below do not name a block, they require NPIs that are unique, valid, outside
  every NPI of the small set, the seed, the reserved ranges and the stub hits (a block such as 1007xxxxxx has the room).

What this file does NOT cover (the live part of the story, run later by the lead alone on the stack): loading the files, the
GET /api/v1/providers total of at least 10,000, the k6 runs, the submit and screening timings, the 90 s jobs and the report
(AC-1 live part, AC-2 to AC-5). Those are measurements by the runner and the report, not pytest.

How the set is checked
  * The generator is run into temp folders (tmp_path); nothing is written into the repository. An autouse guard fails the module if
    a committed file under modern/testdata changes while the tests run.
  * The helpers of test_synthetic_datasets.py are imported, not copied: npi_valid, reserved, records, parse, layout, real_date,
    tree_hashes, sha256, run_tool, all_npis, feed_npis_and_names, GENERATED_FILES. The data tools' own scan rules
    (scan_no_real_data.check_npi, check_names, check_text) are applied too.
  * Two expectations come from files, not from this test: the licences the licence-board stub answers ACTIVE (its mapping
    infra/wiremock/mappings/license-board/clean-seeded.json) and the NPIs the stubs answer with a hit (hit-<npi>.json).

Run: modern/kag/.venv/Scripts/python.exe -m pytest modern/tools/checks/tests/test_perf_dataset.py -q -p no:cacheprovider
"""
import json
import re
import sys
import uuid
from functools import cached_property
from pathlib import Path

import pytest

sys.dont_write_bytecode = True
HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import test_synthetic_datasets as sd  # noqa: E402  helpers of the small-set checks (imported, not copied)

sys.path.insert(0, str(sd.TOOLS))
import scan_no_real_data as scan  # noqa: E402  the data tools' own no-real-data rules

COUNT = 10_000
DAT = "provider-master-perf.dat"
PROVIDER_SQL = "provider-perf.sql"
REVAL_SQL = "revalidation-perf.sql"
PERF_FILES = {DAT, PROVIDER_SQL, REVAL_SQL}
FROZEN = sd.BUSINESS_DATE                 # 20260115, the frozen business date of every fixture
NO_DATE = sd.NO_DATE                      # 99991231: "no due date" in the fixed-width file (README convention), NULL in SQL
SEED_SQL = sd.TESTDATA / "seed/provider_db/01-providers.sql"
STUBS = sd.MODERN / "infra/wiremock/mappings"
# the convention of the 16 seeded providers (testdata/README.md): screen flag by status; tier only for A, T, S
FLAG_OF_STATUS = {"P": "U", "D": "F", "A": "P", "T": "P", "S": "P"}
TIER_OF_PREFIX = {"332": "H", "251": "H", "341": "M", "291": "M", "225": "M"}     # legacy PRVRSK01; any other prefix is L


# ---- running the generator, reading its output --------------------------------------------------------------------------

def run_perf(out: Path, *extra, cwd: Path, hash_seed: str = "1", count: int | None = COUNT):
    args = ["perf"] + (["--count", count] if count is not None else []) + ["--out", out, *extra]
    return sd.run_tool("generate.py", *args, cwd=cwd, env_extra={"PYTHONHASHSEED": hash_seed})


def finished(run) -> Path:
    """The output folder of a generator run; the test fails (not errors) with the reason when the run did not succeed."""
    proc, out = run
    assert proc.returncode == 0, (f"generate.py perf did not run (exit {proc.returncode}): the perf mode is missing or broken\n"
                                  + (proc.stdout + proc.stderr)[-500:])
    return out


TOKEN = re.compile(r"DATE '[^']*'|'(?:[^']|'')*'|NULL")


def sql_value(tok: str):
    if tok == "NULL":
        return None
    if tok.startswith("DATE '"):
        return tok[6:-1]
    return tok[1:-1].replace("''", "'")


def insert_rows(text: str, table: str):
    """(columns, rows as dicts) of every `INSERT INTO <table> (cols) VALUES` of a generated SQL file: one row per line, values only
    quoted strings, DATE literals and NULL, as in testdata/seed/provider_db/01-providers.sql."""
    headers = re.findall(r"INSERT INTO (\w+)\s*\(([^)]*)\)\s*VALUES", text)
    assert headers and {h[0] for h in headers} == {table}, f"expected INSERT INTO {table} only, found {[h[0] for h in headers]}"
    col_lists = {tuple(c.strip() for c in h[1].split(",")) for h in headers}
    assert len(col_lists) == 1, "the INSERT statements do not share one column list"
    cols, rows = list(col_lists.pop()), []
    for line in text.splitlines():
        m = re.match(r"^\s*\((.*)\)\s*[,;]?\s*$", line)
        if not m or line.lstrip().startswith("--"):
            continue
        inner = m.group(1)
        assert not re.sub(r"[,\s]", "", TOKEN.sub("", inner)), f"a value that is not a string, DATE literal or NULL: {line[:120]}"
        toks = TOKEN.findall(inner)
        assert len(toks) == len(cols), f"{len(toks)} values for {len(cols)} columns: {line[:120]}"
        rows.append(dict(zip(cols, map(sql_value, toks))))
    return cols, rows


def iso(d8: str) -> str:
    return f"{d8[:4]}-{d8[4:6]}-{d8[6:]}"


class Perf:
    """The parsed output of one generator run (read lazily, so one broken file fails only the tests that need it)."""

    def __init__(self, out: Path):
        self.out = out

    @cached_property
    def records(self) -> list:
        return sd.records(self.out / DAT)

    @cached_property
    def rows(self) -> list:
        lay = sd.layout("PRVRECRD")
        return [sd.parse(r, lay) for r in self.records]

    @cached_property
    def sql(self) -> str:
        return (self.out / PROVIDER_SQL).read_text(encoding="utf-8")

    @cached_property
    def reval_sql(self) -> str:
        return (self.out / REVAL_SQL).read_text(encoding="utf-8")

    @cached_property
    def provider_table(self):
        return insert_rows(self.sql, "provider")

    @cached_property
    def tracked_table(self):
        return insert_rows(self.reval_sql, "tracked_provider")

    @cached_property
    def active(self) -> list:
        return [r for r in self.rows if r["PRV-ENROLL-STATUS"] == "A"]


_PARSED: dict = {}


def perf(runs) -> Perf:
    out = finished(runs[0])
    return _PARSED.setdefault(out, Perf(out))


def seeded_providers() -> list:
    """(public_id, npi, medicaid_id) of the 16 providers of the normal seed, from its SQL."""
    sql = SEED_SQL.read_text(encoding="utf-8")
    return re.findall(r"^\s+\('([0-9a-f-]{36})', '(\d{10})', '(\d{9})'", sql, re.M)


def small_set_ids() -> tuple:
    """Every NPI and Medicaid ID of the small data set: the seed SQL and every generated file under modern/testdata."""
    npis = {v for _, _, v in sd.all_npis(sd.TESTDATA)} | {n for _, n, _ in seeded_providers()}
    mids = {m for _, _, m in seeded_providers()}
    for rel, layout_name in sd.LAYOUT_OF.items():
        if layout_name == "SANCIN":
            continue
        lay = sd.layout(layout_name)
        mids |= {sd.parse(rec, lay, all_views=True)["PRV-MEDICAID-ID"] for rec in sd.records(sd.TESTDATA / rel)}
    return npis - {""}, mids - {""}


def licence_stub_rule() -> tuple:
    """(regex, state) of the licences the licence-board stub answers ACTIVE, read from its mapping."""
    q = json.loads((STUBS / "license-board/clean-seeded.json").read_text(encoding="utf-8"))["request"]["queryParameters"]
    return re.compile(q["licenseNo"]["matches"]), q["state"]["equalTo"]


def stub_hit_npis() -> set:
    return {m.group(1) for p in STUBS.rglob("hit-*.json") if (m := re.fullmatch(r"hit-(\d{10})\.json", p.name))}


# ---- fixtures ---------------------------------------------------------------------------------------------------------

@pytest.fixture(scope="module", autouse=True)
def committed_datasets_are_never_rewritten():
    """Guard: the committed tree under modern/testdata is byte for byte the same after the module ran."""
    before = sd.tree_hashes(sd.TESTDATA)
    yield
    assert sd.tree_hashes(sd.TESTDATA) == before, "a test or the generator changed a committed file under modern/testdata"


@pytest.fixture(scope="module")
def perf_runs(tmp_path_factory):
    """The perf mode run twice with the recorded seed: other folder, other working folder, other PYTHONHASHSEED."""
    base = tmp_path_factory.mktemp("perf")
    runs = []
    for i, hash_seed in enumerate(("1", "2"), start=1):
        out, cwd = base / f"run{i}", base / f"cwd{i}"
        cwd.mkdir()
        runs.append((run_perf(out, cwd=cwd, hash_seed=hash_seed), out))
    return runs


# ---- AC-1: deterministic, exact files ---------------------------------------------------------------------------------

def test_mam88_ac1_two_runs_with_the_recorded_seed_give_byte_identical_files(perf_runs):
    """[MAM-88 AC-1] two runs (other folder, other PYTHONHASHSEED) of 10,000 providers write the same bytes (equal sha256)."""
    a, b = (sd.tree_hashes(finished(r)) for r in perf_runs)
    assert a == b and len(a) == 3


def test_mam88_ac1_the_output_folder_holds_exactly_the_three_perf_files(perf_runs):
    """[MAM-88 AC-1] provider-master-perf.dat, provider-perf.sql and revalidation-perf.sql, nothing else (no stray temp file)."""
    assert set(sd.tree_hashes(finished(perf_runs[0]))) == PERF_FILES


def test_mam88_ac1_the_130_byte_file_holds_exactly_10000_records_of_130_bytes(perf_runs):
    """[MAM-88 AC-1] D-007: layout length 130, 10,000 records, each 130 bytes, one LF after each and nothing else."""
    ds = perf(perf_runs)
    assert sd.layout("PRVRECRD")["length"] == 130
    assert len(ds.records) == COUNT and {len(r) for r in ds.records} == {130}
    assert (ds.out / DAT).stat().st_size == COUNT * 131


def test_mam88_ac1_every_byte_of_the_130_byte_file_is_printable_ascii(perf_runs):
    """[MAM-88 AC-1] no tab, control character or non-ASCII byte inside a record (the codec reads single-byte text, and a name with a
    quote or an accent would also break the SQL file); the only line end is the LF after each record."""
    bad = [(n, b) for n, rec in enumerate(perf(perf_runs).records, 1) for b in rec if not 0x20 <= b <= 0x7E]
    assert not bad, bad[:5]


def test_mam88_ac1_the_count_option_sets_the_number_of_providers(tmp_path):
    """[MAM-88 AC-1] --count 50 writes 50 records and 50 rows in each SQL file (the 10,000 is a default of --count, not a constant)."""
    out = tmp_path / "fifty"
    proc = run_perf(out, cwd=tmp_path, count=50)
    assert proc.returncode == 0, f"exit {proc.returncode}: {(proc.stdout + proc.stderr)[-500:]}"
    p = Perf(out)
    assert len(p.records) == 50
    assert len(p.provider_table[1]) == 50 and len(p.tracked_table[1]) == 50


def test_mam88_ac1_the_default_count_is_10000(tmp_path):
    """[MAM-88 AC-1] without --count the perf mode writes 10,000 records (the story's size)."""
    out = tmp_path / "default"
    proc = run_perf(out, cwd=tmp_path, count=None)
    assert proc.returncode == 0, f"exit {proc.returncode}: {(proc.stdout + proc.stderr)[-500:]}"
    assert len(sd.records(out / DAT)) == COUNT


def test_mam88_ac1_perf_mode_requires_an_output_folder_and_writes_nothing_into_the_repository(tmp_path):
    """[MAM-88 AC-1] without --out the mode refuses (non-zero exit, the message says --out is required); the 10,000-record files are
    never stored in the repository, so there is no default folder; the committed small set is untouched and the cwd stays empty."""
    before = sd.tree_hashes(sd.TESTDATA)
    proc = sd.run_tool("generate.py", "perf", "--count", 50, cwd=tmp_path)
    assert proc.returncode != 0 and "required" in proc.stderr and "--out" in proc.stderr, proc.stdout + proc.stderr
    assert sd.tree_hashes(sd.TESTDATA) == before and not list(tmp_path.iterdir())


# ---- AC-1: NPIs, Medicaid IDs, public ids -----------------------------------------------------------------------------

def test_mam88_ac1_every_npi_is_unique_ten_digits_with_a_valid_check_digit(perf_runs):
    """[MAM-88 AC-1] 10,000 distinct NPIs of exactly ten digits, each with a valid check digit (Luhn with prefix 80840)."""
    npis = [r["PRV-NPI"] for r in perf(perf_runs).rows]
    assert len(npis) == len(set(npis)) == COUNT
    bad = [n for n in npis if not (re.fullmatch(r"\d{10}", n) and sd.npi_valid(n))]
    assert not bad, bad[:5]


def test_mam88_ac1_no_npi_is_in_a_reserved_journey_range_the_feed_or_a_stub_hit(perf_runs):
    """[MAM-88 AC-1] outside 1003000000-999 and 1990000000-999 (journey applicants), outside the NPIs of aws-transform/test-data
    (verbatim only in scenarios/) and outside every NPI a stub answers with a hit (a perf screening must take the clean path)."""
    npis = {r["PRV-NPI"] for r in perf(perf_runs).rows}
    hits, (feed_npis, _) = stub_hit_npis(), sd.feed_npis_and_names()
    assert hits, "no hit-<npi>.json stub found: the control of this test is broken"
    assert not [n for n in npis if sd.reserved(n)][:5]
    assert not (npis & feed_npis) and not (npis & hits), sorted(npis & (feed_npis | hits))[:5]


def test_mam88_ac1_every_medicaid_id_is_nine_digits_starting_with_9_and_unique(perf_runs):
    """[MAM-88 AC-1] same shape as the generator's Medicaid IDs ('9' + 8 digits), all different (the legacy alternate index key)."""
    mids = [r["PRV-MEDICAID-ID"] for r in perf(perf_runs).rows]
    assert len(set(mids)) == COUNT and all(re.fullmatch(r"9\d{8}", m) for m in mids)


def test_mam88_ac1_every_owner_npi_is_valid_and_belongs_to_an_individual_of_the_set(perf_runs):
    """[MAM-88 AC-1] a group or facility names an owner; the owner is a perf individual (so the set stands alone and loading it
    never needs another provider): valid NPI, type IN, present in the same file. Individuals carry no owner."""
    rows = perf(perf_runs).rows
    persons = {r["PRV-NPI"] for r in rows if r["PRV-TYPE-CODE"] == "IN"}
    for r in rows:
        if r["PRV-TYPE-CODE"] == "IN":
            assert "PRV-OWNER-NPI" not in r, r["PRV-NPI"]
        else:
            owner = r["PRV-OWNER-NPI"]
            assert sd.npi_valid(owner) and owner in persons, (r["PRV-NPI"], owner)


# ---- AC-1: the 16 seeded providers and the small data set cannot clash ------------------------------------------------

def test_mam88_ac1_none_of_the_16_seeded_providers_is_among_the_perf_providers(perf_runs):
    """[MAM-88 AC-1] decision: DISJOINT, not identical. The seed file TRUNCATEs provider and re-inserts exactly the 16 seeded rows
    (make seed restores the seed state), so a perf set that repeated a seeded NPI, Medicaid ID or public_id would clash with the
    seed on the unique columns (npi, public_id). The perf rows are loaded on top of the seed: 16 + 10,000, no clash."""
    seeded = seeded_providers()
    assert len(seeded) == 16
    ds = perf(perf_runs)
    pubs = {r["public_id"] for r in ds.provider_table[1]}
    assert not ({p for p, _, _ in seeded} & pubs)
    assert not ({n for _, n, _ in seeded} & {r["PRV-NPI"] for r in ds.rows})
    assert not ({m for _, _, m in seeded} & {r["PRV-MEDICAID-ID"] for r in ds.rows})


def test_mam88_ac1_no_perf_npi_or_medicaid_id_is_in_the_small_data_set(perf_runs):
    """[MAM-88 AC-1] none of the NPIs of any committed small-set file (edge providers, intake baseline, scenarios, sanctions,
    negative files, seed SQL) nor any of its Medicaid IDs is reused: the perf set never turns an intake record into a duplicate (E103)."""
    npis, mids = small_set_ids()
    seeded = seeded_providers()
    # control: the collected sets hold the 16 seeded providers AND the NPIs and Medicaid IDs of the other small-set files (40 and 41 today)
    assert {n for _, n, _ in seeded} < npis and {m for _, _, m in seeded} < mids, "small_set_ids lost the seeded providers or the other files"
    ds = perf(perf_runs)
    assert not (npis & {r["PRV-NPI"] for r in ds.rows})
    assert not (mids & {r["PRV-MEDICAID-ID"] for r in ds.rows})


def test_mam88_ac1_public_ids_are_unique_uuids(perf_runs):
    """[MAM-88 AC-1] provider.public_id is UNIQUE NOT NULL (provider-service V1): 10,000 distinct, well-formed UUIDs."""
    ids = [r["public_id"] for r in perf(perf_runs).provider_table[1]]
    assert len(ids) == len(set(ids)) == COUNT
    assert all(str(uuid.UUID(i)) == i for i in ids)


def test_mam88_ac1_the_existing_small_data_set_is_unchanged_by_the_perf_mode(perf_runs, tmp_path):
    """[MAM-88 AC-1] after a perf run, the default generator run still writes exactly the 13 documented files, each byte for byte
    the committed file under modern/testdata (the new mode changes no existing output)."""
    finished(perf_runs[0])
    out = tmp_path / "small"
    proc = sd.run_tool("generate.py", "--out", out, cwd=tmp_path)
    assert proc.returncode == 0, proc.stdout + proc.stderr
    fresh = sd.tree_hashes(out)
    assert set(fresh) == sd.GENERATED_FILES
    assert {rel: sd.sha256(sd.TESTDATA / rel) for rel in fresh} == fresh


# ---- AC-1: synthetic-data rules --------------------------------------------------------------------------------------

def test_mam88_ac1_every_name_follows_the_synthetic_naming_rule(perf_runs):
    """[MAM-88 AC-1] synthetic-data skill section 1: first name TESTPROV for every individual (last name upper case, never blank),
    legal name starting 'SAMPLE ' for every group and facility, DBA blank or starting 'SAMPLE '."""
    for n, r in enumerate(perf(perf_runs).rows, 1):
        if r["PRV-TYPE-CODE"] == "IN":
            assert r["PRV-FIRST-NAME"] == "TESTPROV", (n, r["PRV-FIRST-NAME"])
            assert re.fullmatch(r"[A-Z0-9 ]+", r["PRV-LAST-NAME"]), (n, r["PRV-LAST-NAME"])
        else:
            assert r["PRV-LEGAL-NAME"].startswith("SAMPLE "), (n, r["PRV-LEGAL-NAME"])
            assert r["PRV-DBA-NAME"] == "" or r["PRV-DBA-NAME"].startswith("SAMPLE "), (n, r["PRV-DBA-NAME"])


def test_mam88_ac1_the_data_tools_own_no_real_data_rules_find_nothing_in_the_three_files(perf_runs):
    """[MAM-88 AC-1] scan_no_real_data's rules (NPI check digit and reserved ranges, obviously fake names, no street address,
    e-mail or SSN-shaped number) applied to every record of the .dat file and to the text of both SQL files: zero findings."""
    ds = perf(perf_runs)
    allowed_npis, allowed_names = scan.verbatim()
    lay, findings = scan.Layout("PRVRECRD"), []
    rel = "fixed-width/" + DAT
    for n, rec in enumerate(ds.records, 1):                  # the per-record loop of scan_no_real_data.scan, field by field
        f = lay.parse(rec, all_views=True)
        for field, v in f.items():
            if field in scan.NPI_FIELDS:
                if v:                                        # a blank owner is no NPI
                    scan.check_npi(v, rel, f"record {n} {field}", allowed_npis, findings)
            else:
                scan.check_text(v, rel, f"record {n} {field}", allowed_npis, findings)
        scan.check_names(f, rel, n, allowed_names, findings)
    scan.check_text(ds.sql, "seed/provider_db/" + PROVIDER_SQL, "text", allowed_npis, findings)
    scan.check_text(ds.reval_sql, "seed/revalidation_db/" + REVAL_SQL, "text", allowed_npis, findings)
    assert not findings, findings[:5]


def test_mam88_ac1_individual_licences_are_ones_the_licence_board_stub_answers_active(perf_runs):
    """[MAM-88 AC-1] AC-3 screens a perf provider against the mocked sources; any other licence is 'not found' on the stub, which
    would measure a failure path. So every individual carries a licence number and state the stub's clean-seeded mapping accepts
    (SD10001-SD10007 / TX today, read from the mapping)."""
    regex, state = licence_stub_rule()
    people = [r for r in perf(perf_runs).rows if r["PRV-TYPE-CODE"] == "IN"]
    assert people
    bad = [(r["PRV-NPI"], r["PRV-LICENSE-NO"], r["PRV-LICENSE-STATE"]) for r in people
           if not (regex.fullmatch(r["PRV-LICENSE-NO"]) and r["PRV-LICENSE-STATE"] == state)]
    assert not bad, bad[:5]


# ---- AC-1: the mix the later jobs need --------------------------------------------------------------------------------

def test_mam88_ac1_every_status_flag_and_tier_follows_the_seed_convention(perf_runs):
    """[MAM-88 AC-1] status in P A T D S; screen flag by status as in the seed (P:U, D:F, A/T/S:P); risk tier only for A, T, S and
    by the legacy taxonomy rule (332 251 High, 341 291 225 Moderate, any other Limited); pending and denied have no tier."""
    for n, r in enumerate(perf(perf_runs).rows, 1):
        status = r["PRV-ENROLL-STATUS"]
        assert status in FLAG_OF_STATUS, (n, status)
        assert r["PRV-SCREEN-FLAG"] == FLAG_OF_STATUS[status], (n, status, r["PRV-SCREEN-FLAG"])
        assert re.fullmatch(r"[0-9A-Z]{10}", r["PRV-TAXONOMY"]), (n, r["PRV-TAXONOMY"])
        want = TIER_OF_PREFIX.get(r["PRV-TAXONOMY"][:3], "L") if status in ("A", "T", "S") else ""
        assert r["PRV-RISK-TIER"] == want, (n, status, r["PRV-TAXONOMY"], r["PRV-RISK-TIER"])


def test_mam88_ac1_each_of_the_five_enrolment_statuses_occurs_at_least_100_times(perf_runs):
    """[MAM-88 AC-1] minimum 100 each (1%): T-MSIS and the directory must leave the non-active ones out, the sweep must skip them.
    A set that is all ACTIVE would not exercise any filter at 10,000 rows."""
    count = {s: 0 for s in "PATDS"}
    for r in perf(perf_runs).rows:
        count[r["PRV-ENROLL-STATUS"]] += 1
    assert all(c >= 100 for c in count.values()), count


def test_mam88_ac1_at_least_5000_providers_are_active_for_the_tmsis_extract(perf_runs):
    """[MAM-88 AC-1] minimum 5,000 ACTIVE (half the set): the T-MSIS extract reads ACTIVE providers only (PRVTMS01), and the
    90 s target of AC-4 is for an extract that really carries a large share of the 10,000, not a handful."""
    assert len(perf(perf_runs).active) >= 5000


def test_mam88_ac1_at_least_500_active_providers_are_due_on_the_frozen_business_date(perf_runs):
    """[MAM-88 AC-1] minimum 500 ACTIVE with a due date on or before 2026-01-15 (so due on any later sweep date too): the sweep
    (PRVREV01: ACTIVE and due date <= business date) must open a real number of cases, not a handful."""
    due = [r for r in perf(perf_runs).active if r["PRV-REVAL-DUE-DATE"] != NO_DATE and r["PRV-REVAL-DUE-DATE"] <= FROZEN]
    assert len(due) >= 500, len(due)


def test_mam88_ac1_at_least_1000_active_providers_are_not_due_before_2027(perf_runs):
    """[MAM-88 AC-1] minimum 1,000 ACTIVE with a due date from 2027-01-01: not due on any 2026 sweep date, so the sweep's date
    filter is exercised (it must select the due ones, not take every ACTIVE provider)."""
    later = [r for r in perf(perf_runs).active if "20270101" <= r["PRV-REVAL-DUE-DATE"] < NO_DATE]
    assert len(later) >= 1000, len(later)


def test_mam88_ac1_at_least_100_terminated_or_suspended_providers_are_overdue_but_must_not_be_queued(perf_runs):
    """[MAM-88 AC-1] minimum 100 terminated or suspended providers with a due date on or before 2026-01-15: the sweep must skip
    them because they are not ACTIVE, so its status filter is exercised at scale (the expected queue is ACTIVE and due only)."""
    overdue = [r for r in perf(perf_runs).rows if r["PRV-ENROLL-STATUS"] in ("T", "S") and r["PRV-REVAL-DUE-DATE"] <= FROZEN]
    assert len(overdue) >= 100, len(overdue)


def test_mam88_ac1_all_three_provider_types_and_every_risk_taxonomy_prefix_occur(perf_runs):
    """[MAM-88 AC-1] IN, GR and FA at least 500 each (5%): search and detail read both the person and the organisation view of the
    REDEFINES; and each taxonomy prefix 332, 251, 341, 291, 225 plus at least one other prefix appears."""
    rows = perf(perf_runs).rows
    types = {t: sum(1 for r in rows if r["PRV-TYPE-CODE"] == t) for t in ("IN", "GR", "FA")}
    assert all(c >= 500 for c in types.values()) and sum(types.values()) == COUNT, types
    prefixes = {r["PRV-TAXONOMY"][:3] for r in rows}
    assert set(TIER_OF_PREFIX) <= prefixes and prefixes - set(TIER_OF_PREFIX), sorted(prefixes)[:12]


# ---- AC-1: dates ------------------------------------------------------------------------------------------------------

def test_mam88_ac1_dates_are_real_ccyymmdd_dates_and_no_enrolment_starts_after_the_business_date(perf_runs):
    """[MAM-88 AC-1] effective dates are real 8-digit dates from 2000-01-01 to the frozen 2026-01-15 (a future start or a start
    before 2000 would trigger the PRVTMS01 date anomalies of the edge file, not the ordinary extract path); due dates are real
    8-digit dates or the no-date value 99991231."""
    for n, r in enumerate(perf(perf_runs).rows, 1):
        eff, due = r["PRV-EFFECTIVE-DATE"], r["PRV-REVAL-DUE-DATE"]
        assert re.fullmatch(r"\d{8}", eff) and sd.real_date(eff) and "20000101" <= eff <= FROZEN, (n, eff)
        assert re.fullmatch(r"\d{8}", due) and sd.real_date(due), (n, due)


def test_mam88_ac1_active_terminated_and_suspended_have_a_due_date_after_enrolment_pending_and_denied_have_none(perf_runs):
    """[MAM-88 AC-1] seed convention (README): A, T, S carry a real due date later than the effective date (so no ACTIVE provider
    hits the unresolved no-due-date edge, Q-049); pending and denied carry the no-date value 99991231."""
    for n, r in enumerate(perf(perf_runs).rows, 1):
        due = r["PRV-REVAL-DUE-DATE"]
        if r["PRV-ENROLL-STATUS"] in ("A", "T", "S"):
            assert due != NO_DATE and due > r["PRV-EFFECTIVE-DATE"], (n, r["PRV-EFFECTIVE-DATE"], due)
        else:
            assert due == NO_DATE, (n, r["PRV-ENROLL-STATUS"], due)


# ---- AC-1: the provider SQL -------------------------------------------------------------------------------------------

def test_mam88_ac1_the_provider_sql_names_every_one_of_the_10000_npis_once(perf_runs):
    """[MAM-88 AC-1] provider-perf.sql inserts 10,000 rows, one per NPI of the .dat file, none missing, none twice."""
    ds = perf(perf_runs)
    sql_npis = [r["npi"] for r in ds.provider_table[1]]
    assert len(sql_npis) == COUNT and set(sql_npis) == {r["PRV-NPI"] for r in ds.rows}
    assert {r["PRV-NPI"] for r in ds.rows} <= set(re.findall(r"'(\d{10})'", ds.sql))


def test_mam88_ac1_the_provider_sql_uses_the_column_list_of_the_seed_file(perf_runs):
    """[MAM-88 AC-1] the INSERT names exactly the columns of testdata/seed/provider_db/01-providers.sql, in the same order, so the
    rows load into the same table with the same meaning."""
    seed_cols = re.search(r"INSERT INTO provider \(([^)]*)\)", SEED_SQL.read_text(encoding="utf-8")).group(1)
    want = [c.strip() for c in seed_cols.split(",")]
    assert len(want) == 18 and perf(perf_runs).provider_table[0] == want


def test_mam88_ac1_the_provider_sql_rows_equal_the_fixed_width_records(perf_runs):
    """[MAM-88 AC-1] the same providers in both formats: for each NPI the SQL row and the 130-byte record agree on Medicaid ID,
    type, taxonomy, tier (blank = NULL), status, screen flag, effective date, due date (99991231 = NULL) and the person or
    organisation fields of the type's view."""
    ds = perf(perf_runs)
    by_npi = {r["npi"]: r for r in ds.provider_table[1]}
    for r in ds.rows:
        typ, person = r["PRV-TYPE-CODE"], r["PRV-TYPE-CODE"] == "IN"
        want = {
            "medicaid_id": r["PRV-MEDICAID-ID"], "provider_type": typ, "taxonomy": r["PRV-TAXONOMY"],
            "risk_tier": r["PRV-RISK-TIER"] or None, "enroll_status": r["PRV-ENROLL-STATUS"], "screen_flag": r["PRV-SCREEN-FLAG"],
            "effective_date": iso(r["PRV-EFFECTIVE-DATE"]),
            "reval_due_date": None if r["PRV-REVAL-DUE-DATE"] == NO_DATE else iso(r["PRV-REVAL-DUE-DATE"]),
            "last_name": (r["PRV-LAST-NAME"] or None) if person else None,
            "first_name": (r["PRV-FIRST-NAME"] or None) if person else None,
            "license_no": (r["PRV-LICENSE-NO"] or None) if person else None,
            "license_state": (r["PRV-LICENSE-STATE"] or None) if person else None,
            "legal_name": None if person else (r["PRV-LEGAL-NAME"] or None),
            "dba_name": None if person else (r["PRV-DBA-NAME"] or None),
            "owner_npi": None if person else (r["PRV-OWNER-NPI"] or None),
        }
        row = by_npi[r["PRV-NPI"]]
        got = {k: (row[k] or None) for k in want}
        assert got == want, (r["PRV-NPI"], {k: (got[k], want[k]) for k in want if got[k] != want[k]})


def test_mam88_ac1_only_terminated_providers_carry_an_end_date_inside_their_enrolment(perf_runs):
    """[MAM-88 AC-1] seed convention: end_date (no field in the 130-byte record) is NULL except for status T, where it is a real
    date from the effective date to the frozen business date."""
    for row in perf(perf_runs).provider_table[1]:
        if row["enroll_status"] == "T":
            end = row["end_date"]
            assert end and sd.real_date(end.replace("-", "")) and row["effective_date"] <= end <= iso(FROZEN), row["npi"]
        else:
            assert row["end_date"] is None, (row["npi"], row["enroll_status"])


def test_mam88_ac1_both_sql_files_are_additive_and_re_runnable(perf_runs):
    """[MAM-88 AC-1] the files are loaded on top of the seed and may be loaded twice: no TRUNCATE, DELETE, DROP, UPDATE or ALTER
    (the seed's TRUNCATE would wipe the 16 seeded rows); every INSERT ends in ON CONFLICT ... DO NOTHING (the unique npi and
    public_id would otherwise abort a second load, which psql --single-transaction rolls back)."""
    ds = perf(perf_runs)
    for name, text in ((PROVIDER_SQL, ds.sql), (REVAL_SQL, ds.reval_sql)):
        code = "\n".join(l for l in text.splitlines() if l.strip() and not l.lstrip().startswith("--"))     # comments say anything
        statements = [l for l in code.splitlines() if not l.lstrip().startswith("(")]                    # the value rows are data
        assert not [l for l in statements if re.search(r"\b(TRUNCATE|DELETE|DROP|UPDATE|ALTER)\b", l, re.I)], name
        inserts = len(re.findall(r"^\s*INSERT INTO ", code, re.M | re.I))
        assert inserts >= 1 and len(re.findall(r"ON CONFLICT\s*(?:\([^)]*\))?\s*DO NOTHING", code, re.I)) == inserts, name


def test_mam88_ac1_both_sql_files_state_the_recorded_seed_in_their_header(perf_runs):
    """[MAM-88 AC-1] like generate_seed.py's output, the first lines say GENERATED, name the recorded seed (the one in
    testdata/README.md) and say not to edit by hand: the files are never stored in the repository, so the header is the provenance."""
    readme = (sd.TESTDATA / "README.md").read_text(encoding="utf-8")
    seed = re.search(r"^\| Seed \| (\d+)", readme, re.M).group(1)
    ds = perf(perf_runs)
    for name, text in ((PROVIDER_SQL, ds.sql), (REVAL_SQL, ds.reval_sql)):
        head = "\n".join(text.splitlines()[:6])
        assert "GENERATED" in head and f"seed {seed}" in head and "do not edit by hand" in head, (name, head)


# ---- AC-4 data side: the revalidation read model ----------------------------------------------------------------------

def test_mam88_ac1_the_revalidation_sql_tracks_every_perf_provider_with_its_status_and_due_date(perf_runs):
    """[MAM-88 AC-1] AC-4 data side: the sweep reads revalidation_db.tracked_provider (fed by ProviderStatusChanged), which a SQL
    load of provider_db does not fill. revalidation-perf.sql inserts one row per perf NPI with the status and due date of the
    .dat record (99991231 = NULL); the columns are exactly (npi, enroll_status, reval_due_date)."""
    ds = perf(perf_runs)
    cols, rows = ds.tracked_table
    assert cols == ["npi", "enroll_status", "reval_due_date"]
    assert len(rows) == COUNT and len({r["npi"] for r in rows}) == COUNT
    want = {r["PRV-NPI"]: (r["PRV-ENROLL-STATUS"], None if r["PRV-REVAL-DUE-DATE"] == NO_DATE else iso(r["PRV-REVAL-DUE-DATE"]))
            for r in ds.rows}
    assert {r["npi"]: (r["enroll_status"], r["reval_due_date"]) for r in rows} == want


# ---- documentation ----------------------------------------------------------------------------------------------------

def test_mam88_ac1_the_testdata_readme_documents_the_perf_mode_and_its_three_files():
    """[MAM-88 AC-1] modern/testdata/README.md (where the seed is recorded) names the perf command and the three files it writes,
    so the seed and the not-stored-in-the-repository rule stay in one place."""
    readme = (sd.TESTDATA / "README.md").read_text(encoding="utf-8")
    assert "generate.py perf" in readme
    assert all(name in readme for name in PERF_FILES), [n for n in PERF_FILES if n not in readme]
