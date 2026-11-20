"""MAM-84 AC-4: forged and expired tokens and a token of another realm are refused with 401 (RUNNING stack, gateway).

Run from modern/:  kag/.venv/Scripts/python.exe -m pytest tools/checks/live/test_security_tokens.py -q
Read-only: it changes no data and needs no stack lock. The expired-token test waits until a real token of the realm
has expired (the realm's access token lifespan, 15 minutes), so this file takes about 16 minutes.
"""
import time

import jwt
import pytest
from cryptography.hazmat.primitives.asymmetric import rsa

import pemlive as live

PROTECTED_PATH = "/api/v1/providers?size=1"  # ANALYST may read it


@pytest.fixture(scope="module")
def analyst_token():
    return live.tokens()["ANALYST"]


def status_with(token: str) -> int:
    return live.call("GET", PROTECTED_PATH, token)[0]


def test_mam84_ac4_a_valid_token_is_accepted(analyst_token):
    """[MAM-84 AC-4] control: the real token is accepted, so the refusals below are caused by the token alone."""
    assert status_with(analyst_token) == 200


def test_mam84_ac4_token_signed_by_another_key_is_401(analyst_token):
    """[MAM-84 AC-4] the same header (key id included) and claims, signed with a key that is not the realm's."""
    other_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    header = jwt.get_unverified_header(analyst_token)
    forged = jwt.encode(live.claims(analyst_token), other_key, algorithm="RS256", headers={"kid": header.get("kid"), "typ": header.get("typ", "JWT")})
    assert forged != analyst_token
    assert status_with(forged) == 401


def test_mam84_ac4_token_with_changed_claims_is_401(analyst_token):
    """[MAM-84 AC-4] the real signature under changed claims (ADMIN added to the roles)."""
    head, _, signature = analyst_token.split(".")
    claims = live.claims(analyst_token)
    claims.setdefault("realm_access", {}).setdefault("roles", []).append("ADMIN")
    payload = jwt.utils.base64url_encode(__import__("json").dumps(claims, separators=(",", ":")).encode()).decode()
    assert status_with(f"{head}.{payload}.{signature}") == 401


def test_mam84_ac4_unsigned_token_is_401(analyst_token):
    """[MAM-84 AC-4] alg "none": the claims without any signature."""
    unsigned = jwt.encode(live.claims(analyst_token), key=None, algorithm="none")
    assert status_with(unsigned) == 401


def test_mam84_ac4_token_of_another_realm_is_401():
    """[MAM-84 AC-4] a token that Keycloak really issued, but for the realm master."""
    token = live.master_realm_token()
    assert live.claims(token)["iss"].endswith("/realms/master")
    assert status_with(token) == 401


def test_mam84_ac4_expired_token_is_401(analyst_token):
    """[MAM-84 AC-4] a real token of the realm after its expiry time (waits for it, at most the token lifespan)."""
    expires = live.claims(analyst_token)["exp"]
    assert expires - time.time() < 3600, "the access token lifespan is longer than an hour: shorten the wait another way"
    assert status_with(analyst_token) == 200  # still valid now
    refused_from = expires + 60 + 5  # Spring allows 60 s of clock skew after "exp" by default
    while time.time() < refused_from:
        time.sleep(min(30, max(1, refused_from - time.time())))
    assert status_with(analyst_token) == 401
