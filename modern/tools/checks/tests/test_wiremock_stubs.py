"""MAM-47: WireMock stubs of the external screening sources, called on the running stack (WireMock on localhost:8280).

Fabricated hits (valid check digit, never seeded, never a journey applicant): SAM debarred 1005999010, SSA DMF deceased
1005999028, NPPES deactivated 1005999036. Error and slow answers are selected with the header X-Pem-Stub.

Run: modern/kag/.venv/Scripts/python.exe -m pytest -q modern/tools/checks/tests/test_wiremock_stubs.py
"""
import json
import re
import time
import urllib.error
import urllib.request
from pathlib import Path

import pytest

BASE = "http://localhost:8280"
REPO = Path(__file__).resolve().parents[4]
WIREMOCK = REPO / "modern/infra/wiremock"
SOURCES = ["leie", "sam", "nppes", "license-board", "ssa-dmf"]
J1_NPI, J1_LICENCE = "1003000111", "MD12345"
LEIE_EXCLUDED = "1990000111"
SAM_DEBARRED, SSA_DECEASED, NPPES_DEACTIVATED = "1005999010", "1005999028", "1005999036"
SEEDED_LICENCES = [f"SD1000{i}" for i in range(1, 8)]
OTHER_NPIS = ["1003000222", "1003000444", "1005940865", "1005947811"]


def call(path: str, header: str | None = None, timeout: float = 20):
    req = urllib.request.Request(BASE + path, headers={"X-Pem-Stub": header} if header else {})
    started = time.monotonic()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            status, raw = r.status, r.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        status, raw = e.code, e.read().decode("utf-8")
    elapsed = time.monotonic() - started
    try:
        body = json.loads(raw) if raw else None
    except json.JSONDecodeError:
        body = raw
    return status, body, elapsed


def query(source: str, npi: str = J1_NPI, licence: str = J1_LICENCE, state: str = "TX") -> str:
    return {
        "leie": f"/leie/exclusions?npi={npi}",
        "sam": f"/sam/debarments?npi={npi}",
        "nppes": f"/nppes/providers?npi={npi}",
        "license-board": f"/license-board/licenses?licenseNo={licence}&state={state}",
        "ssa-dmf": f"/ssa-dmf/deaths?npi={npi}",
    }[source]


# ---- AC-1 ------------------------------------------------------------------------------------------------------------

def test_mam47_ac1_five_sources_with_hit_clean_error_and_slow_stubs():
    status, body, _ = call("/__admin/mappings")
    assert status == 200
    answers = {}
    for m in body["mappings"]:
        meta = m.get("metadata") or {}
        answers.setdefault(meta.get("source"), set()).add(meta.get("answer"))
    for source in SOURCES:
        assert (WIREMOCK / "mappings" / source).is_dir(), f"folder mappings/{source}"
        assert {"hit", "clean", "error", "slow"} <= answers.get(source, set()), f"{source}: {answers.get(source)}"


# ---- AC-2 ------------------------------------------------------------------------------------------------------------

def test_mam47_ac2_the_j1_applicant_is_clean_on_every_source():
    assert call(query("leie"))[:2] == (200, {"source": "OIGLEIE", "npi": J1_NPI, "excluded": False})
    assert call(query("sam"))[:2] == (200, {"source": "SAMGOV", "npi": J1_NPI, "debarred": False})
    assert call(query("nppes"))[:2] == (200, {"source": "NPPES", "npi": J1_NPI, "found": True, "active": True})
    assert call(query("ssa-dmf"))[:2] == (200, {"source": "SSADMF", "npi": J1_NPI, "deceased": False})
    assert call(query("license-board"))[:2] == (200, {"source": "LICBOARD", "licenseNo": J1_LICENCE, "state": "TX",
                                                      "found": True, "status": "ACTIVE", "expiry": "2028-09"})


# ---- AC-3 ------------------------------------------------------------------------------------------------------------

def test_mam47_ac3_leie_excludes_1990000111_with_type_and_date():
    assert call(query("leie", npi=LEIE_EXCLUDED))[:2] == (200, {
        "source": "OIGLEIE", "npi": LEIE_EXCLUDED, "excluded": True, "exclusionType": "1128(a)(1)",
        "description": "PROGRAM-RELATED FRAUD", "exclusionDate": "2023-01-15"})


@pytest.mark.parametrize("npi", OTHER_NPIS + [SAM_DEBARRED, SSA_DECEASED])
def test_mam47_ac3_leie_answers_every_other_npi_clean(npi):
    assert call(query("leie", npi=npi))[:2] == (200, {"source": "OIGLEIE", "npi": npi, "excluded": False})


# ---- AC-4 ------------------------------------------------------------------------------------------------------------

SAMPLE_LICENCES = {"MD12345": ("ACTIVE", "2028-09"), "DM55500": ("ACTIVE", "2027-12"), "HS22200": ("ACTIVE", "2029-01"),
                   "GR11100": ("ACTIVE", "2028-06"), "PT99900": ("EXPIRED", "2024-01"), "HP30300": ("ACTIVE", "2029-11"),
                   "AM44400": ("ACTIVE", "2028-05")}


@pytest.mark.parametrize("licence", sorted(SAMPLE_LICENCES))
def test_mam47_ac4_the_sample_licences_answer_as_in_the_file(licence):
    status, expiry = SAMPLE_LICENCES[licence]
    assert call(query("license-board", licence=licence))[:2] == (200, {
        "source": "LICBOARD", "licenseNo": licence, "state": "TX", "found": True, "status": status, "expiry": expiry})


@pytest.mark.parametrize("licence", SEEDED_LICENCES)
def test_mam47_ac4_every_seeded_licence_answers_active(licence):
    status, body, _ = call(query("license-board", licence=licence))
    assert status == 200 and body["found"] is True and body["status"] == "ACTIVE" and body["licenseNo"] == licence


@pytest.mark.parametrize("licence,state", [("XX00000", "TX"), ("MD12345", "CA"), ("SD10008", "TX"), ("ZZ99999", "NY")])
def test_mam47_ac4_any_other_licence_is_not_found(licence, state):
    assert call(query("license-board", licence=licence, state=state))[:2] == (200, {
        "source": "LICBOARD", "licenseNo": licence, "state": state, "found": False})


# ---- AC-5 / AC-6 -----------------------------------------------------------------------------------------------------

def test_mam47_ac5_sam_is_clean_except_the_documented_debarred_entity():
    readme = (WIREMOCK / "README.md").read_text(encoding="utf-8")
    assert SAM_DEBARRED in readme
    status, body, _ = call(query("sam", npi=SAM_DEBARRED))
    assert status == 200 and body["debarred"] is True and body["npi"] == SAM_DEBARRED and body.get("entityName")
    for npi in OTHER_NPIS + [LEIE_EXCLUDED, SSA_DECEASED]:
        assert call(query("sam", npi=npi))[:2] == (200, {"source": "SAMGOV", "npi": npi, "debarred": False})


def test_mam47_ac6_ssa_dmf_is_clean_except_the_documented_deceased_npi():
    readme = (WIREMOCK / "README.md").read_text(encoding="utf-8")
    assert SSA_DECEASED in readme
    status, body, _ = call(query("ssa-dmf", npi=SSA_DECEASED))
    assert status == 200 and body["deceased"] is True and body["npi"] == SSA_DECEASED and body.get("dateOfDeath")
    for npi in OTHER_NPIS + [LEIE_EXCLUDED, SAM_DEBARRED]:
        assert call(query("ssa-dmf", npi=npi))[:2] == (200, {"source": "SSADMF", "npi": npi, "deceased": False})


def test_mam47_nppes_answers_the_documented_deactivated_npi():
    readme = (WIREMOCK / "README.md").read_text(encoding="utf-8")
    assert NPPES_DEACTIVATED in readme
    status, body, _ = call(query("nppes", npi=NPPES_DEACTIVATED))
    assert status == 200 and body["found"] is True and body["active"] is False and body.get("deactivationDate")


# ---- AC-7 ------------------------------------------------------------------------------------------------------------

@pytest.mark.parametrize("source", SOURCES)
def test_mam47_ac7_the_error_stub_answers_500(source):
    assert call(query(source), header="error")[0] == 500


@pytest.mark.parametrize("source", SOURCES)
def test_mam47_ac7_the_slow_stub_answers_only_after_more_than_5_seconds(source):
    status, _, elapsed = call(query(source), header="slow")
    assert status == 200
    assert elapsed > 5.0, f"{source} answered after {elapsed:.1f}s"


# ---- AC-8 ------------------------------------------------------------------------------------------------------------

ROW = re.compile(r"^\|\s*(?P<source>[a-z-]+)\s*\|\s*(?P<answer>[a-z]+)\s*\|\s*`GET (?P<path>[^`]+)`\s*\|\s*(?P<header>`[^`]*`|-)\s*\|"
                 r"\s*(?P<status>\d{3})\s*\|\s*(?P<body>`[^`]*`|-)\s*\|\s*$")


def documented_calls():
    readme = (WIREMOCK / "README.md").read_text(encoding="utf-8")
    section = readme.split("## Documented calls", 1)[1].split("\n## ", 1)[0]
    return [m.groupdict() for m in (ROW.match(line.strip()) for line in section.splitlines()) if m]


def test_mam47_ac8_the_readme_documents_every_source_and_answer():
    rows = documented_calls()
    for source in SOURCES:
        assert {"hit", "clean", "error", "slow"} <= {r["answer"] for r in rows if r["source"] == source}, source


def test_mam47_ac8_every_documented_call_returns_exactly_the_documented_answer():
    rows = documented_calls()
    assert rows
    for r in rows:
        header = r["header"].strip("`").split(":", 1)[1].strip() if r["header"] != "-" else None
        status, body, elapsed = call(r["path"], header=header)
        assert status == int(r["status"]), r
        if r["body"] != "-":
            assert body == json.loads(r["body"].strip("`")), r
        if r["answer"] == "slow":
            assert elapsed > 5.0, r
