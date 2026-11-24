"""Re-records the test hashes of a story after the test-writer changed, added or removed test files (HITL change loop, /fe-approve).

  python modern/tools/changes/fe_tests.py record-all <KEY> [--add <file> ...] [--remove <file> ...]

`modern/tools/checks/test_protection.py record` writes exactly the files it is given, so a story that keeps most of its tests and
changes two of them must be recorded with ALL its files. This helper takes the files in `modern/docs/reports/stories/<KEY>.tests.sha256`
that still exist, adds the `--add` files, drops the `--remove` files, and calls `test_protection.py record`. Run it only after the
red step (the changed tests are the new baseline that the production code must not touch), never after the green step.
"""
from __future__ import annotations

import argparse
import subprocess
import sys

sys.dont_write_bytecode = True
sys.path.insert(0, __import__("os").path.dirname(__import__("os").path.abspath(__file__)))  # so that `python -I` finds fe_common

import fe_common as c  # noqa: E402


def merged_list(recorded: list, add: list, remove: list, exists) -> list:
    """The files to record: recorded and still present, plus added, minus removed (repo-relative paths, sorted, no duplicates)."""
    out = {p for p in recorded if exists(p)} | set(add)
    return sorted(out - set(remove))


def recorded_files(key: str) -> list:
    p = c.STORIES / f"{key}.tests.sha256"
    if not p.exists():
        return []
    with open(p, encoding="utf-8") as f:
        return [line.split(None, 1)[1].strip() for line in f if line.strip()]


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    sub = ap.add_subparsers(dest="cmd", required=True)
    r = sub.add_parser("record-all")
    r.add_argument("key")
    r.add_argument("--add", nargs="*", default=[])
    r.add_argument("--remove", nargs="*", default=[])
    a = ap.parse_args(argv)
    files = merged_list(recorded_files(a.key), [c.rel(c.REPO / f) if not f.startswith("modern/") else f for f in a.add],
                        [f for f in a.remove], lambda p: (c.REPO / p).is_file())
    if not files:
        print("nothing to record", file=sys.stderr)
        return 2
    tp = c.MODERN / "tools" / "checks" / "test_protection.py"
    return subprocess.run([sys.executable, "-I", str(tp), "record", a.key, *files], cwd=c.REPO).returncode


if __name__ == "__main__":
    sys.exit(main())
