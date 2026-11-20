"""MAM-84: security checks against the RUNNING stack, through the gateway (AC-1, AC-2, AC-3, AC-7).

Run from modern/:  kag/.venv/Scripts/python.exe -m pytest tools/checks/live/test_security.py -q
Needs ./pem.sh up && ./pem.sh seed. A stack that is down makes the tests FAIL (they are never skipped).

The module changes data (two rejected applications and their notices) and puts the stack back to the seed state at
the end. PEM_STACK_LOCK=<folder> makes it wait for, and hold, the shared stack lock while it runs.

What "allowed" means here: a role listed for an operation gets a harmless request (a read, or a body that the service
refuses after it has authorised the caller), so nothing is approved, sanctioned or run by this module. That a listed
role can really perform each write is proven by the journeys J1 to J7 (web-ui/e2e/journeys).
"""
import json
import time

import pytest

import pemlive as live

OPS = live.operations()
PROTECTED = [op for op in OPS if not op["public"]]
PUBLIC = [op for op in OPS if op["public"]]
ALLOWED = [(op, role) for op in PROTECTED for role in live.ROLES if role in op["roles"]]
FORBIDDEN = [(op, role) for op in PROTECTED for role in live.ROLES if role not in op["roles"]]
# The ten applications of the local run (ADR-011): the gateway and the nine services on their host ports.
APPS = {"api-gateway": 8080, "provider-service": int(live.env().get("PEM_PROVIDER_HOST_PORT", "18081")),
        "enrollment-service": 8082, "screening-service": 8083, "risk-service": 8084, "revalidation-service": 8085,
        "sanctions-service": 8086, "notification-service": 8087, "directory-service": 8088, "tmsis-service": 8089}


def application(npi: str, taxonomy: str) -> dict:
    return {"npi": npi, "medicaidId": "TX0000" + npi[-3:], "providerType": "IN", "taxonomy": taxonomy,
            "individual": {"lastName": "SECTEST", "firstName": "ROLE", "licenseNo": "MD12345", "licenseState": "TX"}}


def request_for(op: dict, role: str, allowed: bool) -> dict:
    """The request this module sends for one operation and role (see the module text for 'harmless')."""
    key = (op["method"], op["path"])
    npi = live.OWN_NPI if (role == "PROVIDER" and allowed) else live.SEEDED_ACTIVE_NPI
    path = live.concrete(op["path"], npi)
    if op["multipart"]:
        return {"path": path, "multipart": True}
    if not op["json"]:
        return {"path": path}
    if key == ("POST", "/api/v1/applications"):
        # allowed: an empty taxonomy is refused with E104 after the caller is authorised (a REJECTED application is kept);
        # forbidden: a complete application, so that a 403 is not hidden by a validation error
        own = live.OWN_NPI if role == "PROVIDER" else live.OTHER_NPI
        return {"path": path, "body": application(own, "" if allowed else "207Q00000X")}
    if key == ("POST", "/api/v1/providers/{npi}/approval"):
        # the seeded provider is ACTIVE already: an allowed role is refused by the state rule, nothing changes
        return {"path": path, "body": {"decision": "APPROVE"}}
    if allowed and "/batch/" in op["path"]:
        return {"path": path, "body": {"businessDate": "not-a-date"}}  # never starts a run
    return {"path": path, "body": {}}


@pytest.fixture(scope="session")
def stack():
    with live.StackLock("lead MAM-84 live security checks"):
        try:
            yield
        finally:
            live.seed()


@pytest.fixture(scope="session")
def tok(stack):
    return live.tokens()


# ---------------------------------------------------------------------------------------------------------- AC-2
def test_mam84_ac2_contracts_and_role_table_do_not_drift():
    """[MAM-84 AC-2] every operation has the same roles in the contracts (x-roles) and in the nfr.md Security table."""
    contracts = {(op["service"], op["method"], op["path"]): op["roles"] for op in OPS}
    table = {(row["service"], row["method"], row["path"]): row["roles"] for row in live.role_table()}
    problems = [f"only in the contracts: {k}" for k in sorted(set(contracts) - set(table))]
    problems += [f"only in nfr.md: {k}" for k in sorted(set(table) - set(contracts))]
    problems += [f"roles differ for {k}: contract {contracts[k]}, nfr.md {table[k]}" for k in sorted(contracts) if k in table and contracts[k] != table[k]]
    assert not problems, "\n".join(problems)
    assert len(contracts) >= 39  # the table of AC-1 had 39 rows before the three run-status calls of Q-105


def test_mam84_ac2_drift_is_detected():
    """[MAM-84 AC-2] the comparison fails, naming the operation, when a role differs (control)."""
    contracts = {(op["service"], op["method"], op["path"]): op["roles"] for op in OPS}
    table = dict(contracts)
    key = sorted(table)[0]
    table[key] = sorted(set(table[key]) ^ {"ADMIN"})
    differing = [k for k in contracts if contracts[k] != table[k]]
    assert differing == [key]


# ---------------------------------------------------------------------------------------------------------- AC-1
@pytest.mark.parametrize("op", PUBLIC, ids=live.op_id)
def test_mam84_ac1_public_endpoint_answers_200_without_a_token(op, stack):
    """[MAM-84 AC-1] the four PUBLIC endpoints answer 200 without a token."""
    query = "?name=HOSPICE" if "directory/providers" in op["path"] else ""
    status, _, _ = live.call(op["method"], live.concrete(op["path"]) + query)
    assert status == 200


def test_mam84_ac1_there_are_four_public_endpoints():
    """[MAM-84 AC-1] exactly the directory search and the three FHIR searches are PUBLIC."""
    assert sorted(op["path"] for op in PUBLIC) == ["/api/v1/directory/providers", "/fhir/Organization", "/fhir/Practitioner", "/fhir/PractitionerRole"]


@pytest.mark.parametrize("op", PROTECTED, ids=live.op_id)
def test_mam84_ac1_no_token_is_401(op, stack):
    """[MAM-84 AC-1] a call with no token to a protected endpoint gets 401."""
    request = request_for(op, "ANALYST", allowed=False)
    status, _, _ = live.call(op["method"], request["path"], None, request.get("body"), request.get("multipart", False))
    assert status == 401


@pytest.mark.parametrize("op,role", FORBIDDEN, ids=lambda v: v if isinstance(v, str) else live.op_id(v))
def test_mam84_ac1_role_not_listed_is_403(op, role, tok):
    """[MAM-84 AC-1] every role that is not listed for the endpoint gets 403."""
    request = request_for(op, role, allowed=False)
    status, _, body = live.call(op["method"], request["path"], tok[role], request.get("body"), request.get("multipart", False))
    assert status == 403, body[:200]


@pytest.mark.parametrize("op,role", ALLOWED, ids=lambda v: v if isinstance(v, str) else live.op_id(v))
def test_mam84_ac1_listed_role_is_not_401_or_403(op, role, tok):
    """[MAM-84 AC-1] every role listed for the endpoint gets an answer that is not 401 or 403 (a PROVIDER on its own NPI)."""
    request = request_for(op, role, allowed=True)
    status, _, body = live.call(op["method"], request["path"], tok[role], request.get("body"), request.get("multipart", False))
    assert status not in (401, 403), body[:200]
    assert status < 500, body[:200]


# ---------------------------------------------------------------------------------------------------------- AC-3
@pytest.fixture(scope="session")
def own_and_other_data(tok):
    """One rejected application (and its notice) of provider1 and one of another provider."""
    for role, npi in (("PROVIDER", live.OWN_NPI), ("ANALYST", live.OTHER_NPI)):
        status, _, body = live.call("POST", "/api/v1/applications", tok[role], application(npi, ""))
        assert status == 422, body[:200]
    deadline = time.time() + 120
    while time.time() < deadline:
        seen = {}
        for npi in (live.OWN_NPI, live.OTHER_NPI):
            status, _, body = live.call("GET", f"/api/v1/notices?npi={npi}", tok["ANALYST"])
            seen[npi] = status == 200 and len(json.loads(body)["items"]) > 0
        if all(seen.values()):
            return
        time.sleep(3)
    raise AssertionError("the REJECTED notices of both providers were not listed within 120 s")


def items_of(body: bytes) -> list:
    return json.loads(body)["items"]


def test_mam84_ac3_provider_cannot_read_another_provider(tok):
    """[MAM-84 AC-3] provider1 asking for another NPI gets 403."""
    status, _, _ = live.call("GET", f"/api/v1/providers/{live.SEEDED_ACTIVE_NPI}", tok["PROVIDER"])
    assert status == 403


@pytest.mark.parametrize("path", ["/api/v1/applications", "/api/v1/notices"])
def test_mam84_ac3_lists_hold_only_own_items(path, tok, own_and_other_data):
    """[MAM-84 AC-3] without a filter the lists contain only provider1's own items (and the other provider has items)."""
    status, _, body = live.call("GET", path, tok["PROVIDER"])
    assert status == 200
    npis = {item["npi"] for item in items_of(body)}
    assert npis == {live.OWN_NPI}
    status, _, body = live.call("GET", f"{path}?npi={live.OTHER_NPI}", tok["ANALYST"])
    assert status == 200 and len(items_of(body)) > 0  # control: the other provider's items exist


@pytest.mark.parametrize("path", ["/api/v1/applications", "/api/v1/notices"])
def test_mam84_ac3_filter_for_another_provider_shows_nothing_foreign(path, tok, own_and_other_data):
    """[MAM-84 AC-3] with an npi filter for another provider, provider1 is refused or sees only own items."""
    status, _, body = live.call("GET", f"{path}?npi={live.OTHER_NPI}", tok["PROVIDER"])
    assert status in (200, 403), body[:200]
    if status == 200:
        assert all(item["npi"] == live.OWN_NPI for item in items_of(body))


# ---------------------------------------------------------------------------------------------------------- AC-7
@pytest.mark.parametrize("app", sorted(APPS), ids=str)
def test_mam84_ac7_health_is_open(app):
    """[MAM-84 AC-7] /actuator/health answers 200 without a token."""
    status, _, _ = live.call("GET", "/actuator/health", base=f"http://127.0.0.1:{APPS[app]}")
    assert status == 200


@pytest.mark.parametrize("endpoint", ["info", "metrics"])
@pytest.mark.parametrize("app", sorted(a for a in APPS if a != "api-gateway"), ids=str)
def test_mam84_ac7_info_and_metrics_need_a_token_on_a_service(app, endpoint):
    """[MAM-84 AC-7] /actuator/info and /actuator/metrics answer 401 without a token."""
    status, _, _ = live.call("GET", f"/actuator/{endpoint}", base=f"http://127.0.0.1:{APPS[app]}")
    assert status == 401


@pytest.mark.parametrize("endpoint", ["info", "metrics", "metrics/http.server.requests", "env", "beans", "loggers", "heapdump", "threaddump", "mappings"])
def test_mam84_ac7_gateway_opens_only_health(endpoint):
    """[MAM-84 AC-7] the gateway, which does not check tokens itself (it relays them), serves nothing but health:
    every other actuator endpoint is refused (401) or not there (404), never 200 (question Q-121)."""
    status, _, _ = live.call("GET", f"/actuator/{endpoint}", base=f"http://127.0.0.1:{APPS['api-gateway']}")
    assert status in (401, 404)


def test_mam84_ac7_admin_reads_info_and_metrics_on_a_service(tok):
    """[MAM-84 AC-7] control: with an ADMIN token info and metrics answer 200 (MAM-36 AC-2)."""
    for endpoint in ("info", "metrics"):
        status, _, _ = live.call("GET", f"/actuator/{endpoint}", tok["ADMIN"], base=f"http://127.0.0.1:{APPS['enrollment-service']}")
        assert status == 200
