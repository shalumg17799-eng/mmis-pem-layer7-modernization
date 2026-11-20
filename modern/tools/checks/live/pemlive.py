"""Helpers for the live checks (MAM-84): tokens and calls against the RUNNING PEM stack. Never prints a secret.

The live checks need: ./pem.sh up && ./pem.sh seed. Passwords are read at run time from modern/infra/.env.local.
"""
import base64
import json
import os
import shutil
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from pathlib import Path

import yaml

MODERN = Path(__file__).resolve().parents[3]
GATEWAY = os.environ.get("PEM_GATEWAY_URL", "http://127.0.0.1:8080")
KEYCLOAK = os.environ.get("PEM_KEYCLOAK_URL", "http://127.0.0.1:8180")
ROLES = ["PROVIDER", "ANALYST", "SUPERVISOR", "ADMIN", "SYSTEM"]
OWN_NPI = "1003000111"  # provider1 (Keycloak claim npi)
SEEDED_ACTIVE_NPI = "1005940865"  # TESTPROV ALPHA, ACTIVE (modern/testdata/README.md)
OTHER_NPI = "1003000333"  # a synthetic NPI of another provider (aws-transform/test-data/providers.txt)


def env() -> dict:
    out = {}
    for line in (MODERN / "infra/.env.local").read_text(encoding="utf-8").splitlines():
        if "=" in line and not line.startswith("#"):
            key, value = line.split("=", 1)
            out[key] = value
    return out


def _token(realm: str, data: dict) -> str:
    request = urllib.request.Request(f"{KEYCLOAK}/realms/{realm}/protocol/openid-connect/token", urllib.parse.urlencode(data).encode())
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.load(response)["access_token"]


def tokens() -> dict:
    e = env()

    def user(name: str, variable: str) -> str:
        return _token("pem", {"grant_type": "password", "client_id": "pem-test", "username": name, "password": e[variable]})

    return {
        "PROVIDER": user("provider1", "KC_PROVIDER_PASSWORD"),
        "ANALYST": user("analyst1", "KC_ANALYST_PASSWORD"),
        "SUPERVISOR": user("supervisor1", "KC_SUPERVISOR_PASSWORD"),
        "ADMIN": user("admin1", "KC_ADMIN_PASSWORD"),
        "SYSTEM": _token("pem", {"grant_type": "client_credentials", "client_id": "pem-services", "client_secret": e["PEM_SERVICES_CLIENT_SECRET"]}),
    }


def master_realm_token() -> str:
    """A token of another realm (master), validly signed by that realm."""
    e = env()
    return _token("master", {"grant_type": "password", "client_id": "admin-cli", "username": e.get("KC_BOOTSTRAP_ADMIN_USERNAME", "admin"), "password": e["KC_BOOTSTRAP_ADMIN_PASSWORD"]})


def claims(token: str) -> dict:
    payload = token.split(".")[1]
    return json.loads(base64.urlsafe_b64decode(payload + "=" * (-len(payload) % 4)))


def call(method: str, path: str, token: str | None = None, body=None, multipart: bool = False, base: str = GATEWAY, timeout: int = 30):
    """Returns (status, headers, body bytes). body: a JSON-serialisable value; multipart=True sends an empty form."""
    headers = {}
    data = None
    if token:
        headers["Authorization"] = "Bearer " + token
    if multipart:
        headers["Content-Type"] = "multipart/form-data; boundary=pemlive"
        data = b"--pemlive--\r\n"
    elif body is not None:
        headers["Content-Type"] = "application/json"
        data = json.dumps(body).encode()
    request = urllib.request.Request(base + path, data=data, method=method.upper(), headers=headers)
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            return response.status, dict(response.headers), response.read()
    except urllib.error.HTTPError as error:
        return error.code, dict(error.headers), error.read()


def operations() -> list[dict]:
    """Every operation of the nine OpenAPI contracts with its x-roles."""
    out = []
    for file in sorted((MODERN / "contracts/openapi").glob("*.yaml")):
        document = yaml.safe_load(file.read_text(encoding="utf-8"))
        for path, item in document["paths"].items():
            for method, operation in item.items():
                if method not in ("get", "post", "put", "patch", "delete"):
                    continue
                roles = operation.get("x-roles") or []
                roles = [roles] if isinstance(roles, str) else list(roles)
                content = list((operation.get("requestBody") or {}).get("content", {}))
                out.append({"service": file.stem, "method": method.upper(), "path": path, "roles": sorted(roles),
                            "public": "PUBLIC" in roles, "multipart": any("multipart" in c for c in content),
                            "json": any("json" in c for c in content)})
    return out


def role_table() -> list[dict]:
    """The Security table of modern/docs/architecture/nfr.md: Service | Method | Path | Roles."""
    rows, inside = [], False
    for line in (MODERN / "docs/architecture/nfr.md").read_text(encoding="utf-8").splitlines():
        if line.startswith("## "):
            inside = line.startswith("## Security")
        if inside and line.startswith("|") and "`/" in line:
            cells = [c.strip() for c in line.strip().strip("|").split("|")]
            rows.append({"service": cells[0], "method": cells[1].upper(), "path": cells[2].strip("`"),
                         "roles": sorted(r.strip() for r in cells[3].split(","))})
    return rows


def concrete(path: str, npi: str = SEEDED_ACTIVE_NPI) -> str:
    """The path with its parameters filled: {npi} with a seeded provider, every other id with a random UUID."""
    out = path.replace("{npi}", npi)
    while "{" in out:
        start, end = out.index("{"), out.index("}")
        out = out[:start] + str(uuid.uuid4()) + out[end + 1:]
    return out


def op_id(op: dict) -> str:
    return f"{op['method']} {op['path']}"


class StackLock:
    """The live stack is shared (deploys, journeys, the parity run). PEM_STACK_LOCK names a lock folder; unset = no lock."""

    def __init__(self, owner: str):
        self.path = os.environ.get("PEM_STACK_LOCK")
        self.owner = owner

    def __enter__(self):
        if self.path:
            deadline = time.time() + 3600
            while True:
                try:
                    os.mkdir(self.path)
                    break
                except FileExistsError:
                    if time.time() > deadline:
                        raise RuntimeError(f"the stack lock {self.path} is still held after 60 minutes")
                    time.sleep(15)
            Path(self.path, "owner.txt").write_text(f"{self.owner} {time.strftime('%Y-%m-%d %H:%M:%S')}\n", encoding="utf-8")
        return self

    def __exit__(self, *_):
        if self.path:
            Path(self.path, "owner.txt").unlink(missing_ok=True)
            os.rmdir(self.path)


def seed() -> None:
    """Back to the seed state (modern/infra/scripts/seed.sh); raises if the script fails."""
    # shutil.which searches PATH (Git Bash); a bare "bash" would start Windows' own bash.exe (WSL) from System32
    bash = os.environ.get("PEM_BASH") or shutil.which("bash") or "bash"
    subprocess.run([bash, "infra/scripts/seed.sh"], cwd=MODERN, check=True, capture_output=True, timeout=600)
