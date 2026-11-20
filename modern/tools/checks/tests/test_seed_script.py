"""MAM-50 AC-6: make seed (infra/scripts/seed.sh) stops with a non-zero exit and names the service whose database it
cannot load, and a later run gives the full seed state. Runs against the local stack (container pem-postgres-1).

Run: modern/kag/.venv/Scripts/python.exe -m pytest -q modern/tools/checks/tests/test_seed_script.py
"""
import os
import shutil
import subprocess
from pathlib import Path

REPO = Path(__file__).resolve().parents[4]
SEED = REPO / "modern/infra/scripts/seed.sh"


def git_bash() -> str:
    """Git Bash, never the WSL launcher in System32 or WindowsApps."""
    for candidate in [shutil.which("bash"), r"C:\Program Files\Git\bin\bash.exe",
                      str(Path.home() / "AppData/Local/Programs/Git/bin/bash.exe")]:
        if candidate and Path(candidate).exists() and "system32" not in candidate.lower() \
                and "windowsapps" not in candidate.lower():
            return candidate
    raise RuntimeError("Git Bash not found")


def run_seed(seed_dir: Path) -> subprocess.CompletedProcess:
    env = dict(os.environ, SEED_DIR=seed_dir.as_posix())
    return subprocess.run([git_bash(), SEED.as_posix()], capture_output=True, text=True, env=env, cwd=REPO)


def test_mam50_ac6_unreachable_database_fails_explicitly_and_names_the_service(tmp_path):
    bad = tmp_path / "nonexistent_db"
    bad.mkdir()
    (bad / "01-anything.sql").write_text("SELECT 1;\n", encoding="utf-8")
    r = run_seed(tmp_path)
    out = r.stdout + r.stderr
    assert r.returncode != 0, out
    assert "nonexistent-service" in out and "FAILED" in out, out


def test_mam50_ac6_the_full_seed_loads_after_a_failure():
    r = run_seed(REPO / "modern/testdata/seed")
    out = r.stdout + r.stderr
    assert r.returncode == 0, out
    assert "provider-service" in out and "provider_db" in out, out
