"""MAM-84 AC-6: no password, token or key in any tracked or untracked file; modern/infra/.env.local is ignored.

Run from the repository root or from modern/:  modern/kag/.venv/Scripts/python.exe modern/tools/checks/scan_secrets.py
Exit code 0 = clean. It never prints a secret value: findings name the file, the line and the kind only.

Checks:
 1. git check-ignore modern/infra/.env.local  -> the file with the generated local passwords is ignored
 2. no VALUE of modern/infra/.env.local (8 characters or more) appears in any tracked or untracked, non-ignored file
 3. no file holds a private key block, a cloud or VCS access token, a signed JWT, or a literal password / secret /
    token assignment (placeholders such as ${NAME}, <...>, "changeme"-style words and test fixtures listed below are fine)
"""
import re
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[3]
ENV_LOCAL = REPO / "modern/infra/.env.local"
MAX_BYTES = 3_000_000

PATTERNS = {
    "private key block": re.compile(rb"-----BEGIN (?:RSA |EC |DSA |OPENSSH |ENCRYPTED )?PRIVATE KEY-----"),
    "AWS access key id": re.compile(rb"\b(?:AKIA|ASIA)[0-9A-Z]{16}\b"),
    "GitHub token": re.compile(rb"\bgh[pousr]_[A-Za-z0-9]{30,}\b"),
    "Atlassian / Slack style token": re.compile(rb"\b(?:ATATT|xox[baprs]-)[A-Za-z0-9_\-=]{20,}"),
    "signed JWT": re.compile(rb"\beyJ[A-Za-z0-9_-]{10,}\.eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{20,}"),
    "literal credential assignment": re.compile(
        rb"(?i)\b(?:password|passwd|secret|api[_-]?key|access[_-]?token|client[_-]?secret)\b\s*[:=]\s*[\"']?(?P<v>[^\s\"'#,;]{8,})"),
}
# values of the "literal credential assignment" pattern that are not secrets: references, placeholders, code
HARMLESS_VALUE = re.compile(
    rb"(?i)^(?:\$\{|\$\(|\$[A-Z_]|<|\{\{|%|env\[|e\[|os\.environ|process\.env|passwordOf|password\)|secret\)|token\)|null|none|true|false|"
    rb"string|required|changeme|change-me|example|dummy|placeholder|redacted|\*{3,}|x{4,}|\.\.\.|not-a-|wrong-|"
    rb"[a-z_]+\(|[a-z_.]+\[|[A-Za-z_.]+$)")
# files that may hold credential-shaped text on purpose, each with the reason
ALLOWED_FILES = {
    "modern/tools/checks/scan_secrets.py": "this scanner (its own patterns)",
    "modern/libs/service-kit/src/test/java/gov/medicaid/pem/kit/security/BearerProblemHandlerTest.java":
        "a constant named SECRET holding a made-up internal detail; the test proves it is not leaked (checked 2026-11-20)",
}


def files() -> list[Path]:
    out = subprocess.run(["git", "ls-files", "--cached", "--others", "--exclude-standard", "-z"], cwd=REPO, capture_output=True, check=True).stdout
    return [REPO / name.decode("utf-8", "replace") for name in out.split(b"\0") if name]


def main() -> int:
    problems = []
    ignored = subprocess.run(["git", "check-ignore", "-q", "modern/infra/.env.local"], cwd=REPO).returncode == 0
    print(("PASS" if ignored else "FAIL") + "  modern/infra/.env.local is git-ignored")
    if not ignored:
        problems.append(".env.local is not ignored")

    values = {}
    if ENV_LOCAL.exists():
        for line in ENV_LOCAL.read_text(encoding="utf-8").splitlines():
            if "=" in line and not line.startswith("#"):
                key, value = line.split("=", 1)
                if len(value) >= 8:
                    values[key] = value.encode()
    scanned, value_hits, pattern_hits = 0, [], []
    for path in files():
        rel = path.relative_to(REPO).as_posix()
        if not path.is_file() or path.stat().st_size > MAX_BYTES:
            continue
        data = path.read_bytes()
        scanned += 1
        for key, value in values.items():
            if value in data:
                value_hits.append(f"{rel}: holds the value of {key}")
        if rel in ALLOWED_FILES:
            continue
        for number, line in enumerate(data.splitlines(), 1):
            for kind, pattern in PATTERNS.items():
                match = pattern.search(line)
                if not match:
                    continue
                if kind == "literal credential assignment" and HARMLESS_VALUE.match(match.group("v")):
                    continue
                pattern_hits.append(f"{rel}:{number}: {kind}")
    print(("PASS" if not value_hits else "FAIL") + f"  no value of .env.local ({len(values)} values checked) in {scanned} tracked or untracked files")
    for hit in value_hits:
        print("      " + hit)
    print(("PASS" if not pattern_hits else "FAIL") + "  no key block, access token, signed JWT or literal credential in any file")
    for hit in pattern_hits[:60]:
        print("      " + hit)
    if len(pattern_hits) > 60:
        print(f"      ... and {len(pattern_hits) - 60} more")
    problems += value_hits + pattern_hits
    print(f"RESULT: {'CLEAN' if not problems else str(len(problems)) + ' finding(s)'}")
    return 0 if not problems else 1


if __name__ == "__main__":
    sys.exit(main())
