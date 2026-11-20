"""MAM-47 AC-4 (parity group U-10, question Q-130 default A): the licence-board stub must know the licence of every
application that the test data defines as a VALID individual.

Gap: modern/testdata/fixed-width/appinput-baseline.dat record 1 (application 1006133288, "valid IN" by the generator) carries
licence SD20001 / TX, but the stub knows only the six sample licences and SD10001-SD10007, so screening gets "not found".

The stub mappings are evaluated OFFLINE from modern/infra/wiremock/mappings/**/*.json the way WireMock does (method, urlPath,
query-parameter and header matchers; the lowest priority number wins; `{{request.query.x}}` templates rendered, compose runs
WireMock with --global-response-templating). No stack is needed. "Valid" is not a list of NPIs: it is the data set's own
definition, tools/data/coverage_report.py (the PRVENR01 validation E101-E104, predicate `valid_any`), cross-checked with
testdata/coverage.csv. Records are decoded with the exported layout through tools/data/pemdata.py.

Run: modern/kag/.venv/Scripts/python.exe -m pytest -q -p no:cacheprovider modern/tools/checks/tests/test_intake_licences_known_to_stub.py
"""
import csv
import json
import re
import sys
from pathlib import Path
from urllib.parse import parse_qs, urlencode, urlsplit

import pytest

REPO = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(REPO / "modern/tools/data"))
import coverage_report  # noqa: E402  the data set's own definition of a valid application
from pemdata import TESTDATA  # noqa: E402

MAPPINGS = REPO / "modern/infra/wiremock/mappings"
README = REPO / "modern/infra/wiremock/README.md"
BASELINE = "fixed-width/appinput-baseline.dat"
REGISTER_RULE = "R-PRVENR01-cbl-00007"  # coverage.csv: "Register a validated provider as PENDING / UNSCREENED"
DEFAULT_PRIORITY = 5                    # WireMock: a mapping without "priority" has priority 5; the lowest number wins


# ---- a small WireMock evaluator (only what the mapping files use; anything else fails loudly) ----------------------------

def load_mappings():
    mappings = []
    for f in sorted(MAPPINGS.rglob("*.json")):
        doc = json.loads(f.read_text(encoding="utf-8"))
        mappings.extend(doc["mappings"] if "mappings" in doc else [doc])
    return mappings


def value_matches(pattern: dict, values: list[str] | None) -> bool:
    """A positive matcher is true when ANY sent value matches (Java regex `matches` is a full match)."""
    unsupported = set(pattern) - {"equalTo", "matches", "caseInsensitive"}
    if unsupported or not ({"equalTo", "matches"} & set(pattern)):
        raise NotImplementedError(f"value matcher {sorted(pattern)} is not supported by this offline evaluator")
    if values is None:
        return False
    if "equalTo" in pattern:
        want = pattern["equalTo"]
        if pattern.get("caseInsensitive"):
            return any(v.lower() == want.lower() for v in values)
        return want in values
    return any(re.fullmatch(pattern["matches"], v) for v in values)


def request_matches(spec: dict, method: str, path: str, query: dict, headers: dict) -> bool:
    unsupported = set(spec) - {"method", "urlPath", "queryParameters", "headers"}
    if unsupported or "urlPath" not in spec:
        raise NotImplementedError(f"request matcher {sorted(unsupported) or 'url form'} is not supported by this offline evaluator")
    if spec.get("method", "ANY") not in ("ANY", method) or spec["urlPath"] != path:
        return False
    sent = {k.lower(): [v] for k, v in headers.items()}
    return (all(value_matches(p, query.get(n)) for n, p in spec.get("queryParameters", {}).items())
            and all(value_matches(p, sent.get(n.lower())) for n, p in spec.get("headers", {}).items()))


def render(node, query: dict):
    if isinstance(node, dict):
        return {k: render(v, query) for k, v in node.items()}
    if isinstance(node, list):
        return [render(v, query) for v in node]
    if isinstance(node, str):
        out = re.sub(r"\{\{request\.query\.(\w+)\}\}", lambda m: query[m.group(1)][0], node)
        if "{{" in out:
            raise NotImplementedError(f"response template {node!r} is not supported by this offline evaluator")
        return out
    return node


def stub_answer(path_and_query: str, headers: dict | None = None):
    """(status, body, mapping) that WireMock would answer for `GET path?query`."""
    url = urlsplit(path_and_query)
    query = parse_qs(url.query)
    hits = [m for m in load_mappings() if request_matches(m["request"], "GET", url.path, query, headers or {})]
    assert hits, f"no mapping matches GET {path_and_query} (WireMock would answer 404)"
    best = min(m.get("priority", DEFAULT_PRIORITY) for m in hits)
    top = [m for m in hits if m.get("priority", DEFAULT_PRIORITY) == best]
    assert len({json.dumps(m["response"], sort_keys=True) for m in top}) == 1, \
        f"ambiguous: {[m['name'] for m in top]} all match GET {path_and_query} at priority {best}"
    return top[0]["response"]["status"], render(top[0]["response"].get("jsonBody"), query), top[0]


def licence_path(licence: str, state: str) -> str:
    return "/license-board/licenses?" + urlencode({"licenseNo": licence, "state": state})


# ---- the test data's valid individuals ---------------------------------------------------------------------------------

def baseline():
    return coverage_report.Fixture(TESTDATA, "APP")


def valid_record_numbers() -> list[int]:
    """The records the data set itself calls valid (E101-E104 all clear), in file order."""
    return baseline().match("valid_any")


def valid_individual_applications() -> list[dict]:
    """Valid applications of type IN: only the individual view of the layout carries a licence number and state."""
    valid = set(valid_record_numbers())
    return [r for r in baseline().records if r["_n"] in valid and r["PRV-TYPE-CODE"] == "IN"]


# ---- AC-4 ------------------------------------------------------------------------------------------------------------

def test_mam47_ac4_every_valid_intake_individual_has_an_active_licence_on_the_stub():
    individuals = valid_individual_applications()
    assert individuals, f"{BASELINE} defines no valid individual (type IN) application"
    unknown = []
    for r in individuals:
        licence, state = r["PRV-LICENSE-NO"], r["PRV-LICENSE-STATE"]
        status, body, mapping = stub_answer(licence_path(licence, state))
        if not (status == 200 and body.get("found") is True and body.get("status") == "ACTIVE"):
            unknown.append(f"application {r['PRV-NPI']} (record {r['_n']}): licence {licence} / {state} is answered "
                           f"{status} {json.dumps(body)} by the [{mapping['metadata']['answer']}] mapping "
                           f"'{mapping['name']}'; expected found=true, status=ACTIVE")
    assert not unknown, "the licence-board stub does not know the licence of a valid intake individual:\n  " + "\n  ".join(unknown)


@pytest.mark.parametrize("licence,state", [("ZZ99999", "TX"), ("ZZ99999", "NY")])
def test_mam47_ac4_a_licence_no_mapping_knows_is_still_answered_not_found(licence, state):
    status, body, mapping = stub_answer(licence_path(licence, state))
    assert (status, body) == (200, {"source": "LICBOARD", "licenseNo": licence, "state": state, "found": False})
    assert mapping["metadata"]["answer"] == "not-found"


# ---- premises of the test above: its evaluator and its notion of "valid" are not private ------------------------------

ROW = re.compile(r"^\|\s*license-board\s*\|\s*(?P<answer>[a-z-]+)\s*\|\s*`GET (?P<path>[^`]+)`\s*\|\s*(?P<header>`[^`]*`|-)\s*\|"
                 r"\s*(?P<status>\d{3})\s*\|\s*(?P<body>`[^`]*`)\s*\|\s*$")


def documented_licence_board_calls():
    section = README.read_text(encoding="utf-8").split("## Documented calls", 1)[1].split("\n## ", 1)[0]
    return [m.groupdict() for m in (ROW.match(line.strip()) for line in section.splitlines()) if m]


@pytest.mark.parametrize("row", documented_licence_board_calls(), ids=lambda r: f"{r['answer']}-{r['path'].split('?')[1]}-{r['header']}")
def test_mam47_ac4_the_offline_evaluator_gives_the_documented_answer(row):
    name, value = row["header"].strip("`").split(":", 1) if row["header"] != "-" else (None, None)
    status, body, _ = stub_answer(row["path"], {name: value.strip()} if name else None)
    assert (status, body) == (int(row["status"]), json.loads(row["body"].strip("`")))


def test_mam47_ac4_the_valid_applications_are_the_ones_the_coverage_file_registers():
    with (TESTDATA / "coverage.csv").open(encoding="utf-8", newline="") as f:
        rows = [r for r in csv.DictReader(f) if r["rule_id"] == REGISTER_RULE and r["fixture_file"] == BASELINE]
    assert len(rows) == 1, rows
    listed = [int(n) for n in rows[0]["record_ref"].removeprefix("rec:").split(";")]
    assert listed == valid_record_numbers()[:3]  # coverage.csv lists at most three records per rule
