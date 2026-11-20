"""Test protection without git commits (user rule, 2026-11-18; tdd-workflow skill, section 2, steps 3 and 5).

  record  <KEY> <file>...  After the red step. Writes modern/docs/reports/stories/<KEY>.tests.sha256 (sha256sum format,
                           one "<sha256>  <path>" line per file, sorted by path) and keeps a restore copy of each file in
                           git's object store (`git hash-object -w`: no commit, no index change), listed in
                           modern/docs/reports/stories/<KEY>/red-blobs.txt.
  check   <KEY>            After every green attempt. Recomputes the hashes of the story's test files and lists test
                           files that are in no *.tests.sha256 file. Prints OK and exits 0 only when nothing differs.
  restore <KEY>            Puts back the red version of every CHANGED or MISSING file of the story. Test files that are
                           in no hash file are listed; --delete-new also deletes them.

Paths are relative to the repository root. Test files are everything under these folders of modern/:
services/*/src/test, libs/*/src/test, libs/test-support, web-ui/tests, web-ui/e2e (build output excluded).
"""
import argparse
import hashlib
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[3]
STORIES = REPO / "modern/docs/reports/stories"
SKIP_DIRS = {"build", "node_modules", ".gradle", "test-results", "playwright-report", "blob-report", "__pycache__"}


def rel(p: Path) -> str:
    return p.resolve().relative_to(REPO).as_posix()


def sha256(p: Path) -> str:
    return hashlib.sha256(p.read_bytes()).hexdigest()


def test_roots():
    m = REPO / "modern"
    yield from (m / "services").glob("*/src/test")
    yield from (m / "libs").glob("*/src/test")
    yield m / "libs/test-support"
    yield m / "web-ui/tests"
    yield m / "web-ui/e2e"


def all_test_files():
    out = set()
    for root in test_roots():
        if not root.is_dir():
            continue
        for p in root.rglob("*"):
            if p.is_file() and not (SKIP_DIRS & set(p.relative_to(root).parts[:-1])):
                out.add(rel(p))
    return out


def read_hashes(f: Path) -> dict:
    out = {}
    for line in f.read_text(encoding="utf-8").splitlines():
        if line.strip():
            h, path = line.split("  ", 1)
            out[path] = h
    return out


def hash_file(key: str) -> Path:
    return STORIES / f"{key}.tests.sha256"


def blob_file(key: str) -> Path:
    return STORIES / key / "red-blobs.txt"


def record(key: str, files: list[str]) -> int:
    paths = sorted({rel(Path(f)) for f in files})
    missing = [p for p in paths if not (REPO / p).is_file()]
    if missing:
        print("not a file:", *missing, sep="\n  ")
        return 2
    hash_file(key).parent.mkdir(parents=True, exist_ok=True)
    blob_file(key).parent.mkdir(parents=True, exist_ok=True)
    hash_file(key).write_text("".join(f"{sha256(REPO / p)}  {p}\n" for p in paths), encoding="utf-8", newline="\n")
    blobs = []
    for p in paths:
        b = subprocess.run(["git", "hash-object", "-w", "--", p], cwd=REPO, capture_output=True, text=True, check=True).stdout.strip()
        blobs.append(f"{b}  {p}\n")
    blob_file(key).write_text("".join(blobs), encoding="utf-8", newline="\n")
    print(f"recorded {len(paths)} test files for {key} -> {rel(hash_file(key))}")
    return 0


def compare(key: str):
    expected = read_hashes(hash_file(key))
    changed = [p for p, h in expected.items() if (REPO / p).is_file() and sha256(REPO / p) != h]
    missing = [p for p in expected if not (REPO / p).is_file()]
    known = set()
    for f in STORIES.glob("*.tests.sha256"):
        known |= set(read_hashes(f))
    # Only test files of the modules this story touches count as NEW, so a story built in parallel in another module
    # (another lane) never makes this check fail or gets its tests deleted by a restore.
    roots = {module_root(p) for p in expected}
    new = sorted(p for p in all_test_files() - known if module_root(p) in roots)
    return changed, missing, new


def module_root(path: str) -> str:
    """modern/services/<svc>, modern/libs/<lib> or modern/web-ui: the module a test file belongs to."""
    parts = path.split("/")
    return "/".join(parts[:2]) if parts[1] == "web-ui" else "/".join(parts[:3])


def check(key: str) -> int:
    if not hash_file(key).is_file():
        print(f"no hash file for {key}: run 'record' after the red step")
        return 2
    changed, missing, new = compare(key)
    for label, items in (("CHANGED", changed), ("MISSING", missing), ("NEW (in no hash file)", new)):
        for p in items:
            print(f"{label}: {p}")
    if changed or missing or new:
        print(f"FAILED: a test was changed, removed or added after the red step of {key}; restore it, then redo the green step")
        return 1
    print(f"OK: {len(read_hashes(hash_file(key)))} test files of {key} unchanged; no unrecorded test files")
    return 0


def restore(key: str, delete_new: bool) -> int:
    changed, missing, new = compare(key)
    blobs = {}
    for line in blob_file(key).read_text(encoding="utf-8").splitlines():
        b, p = line.split("  ", 1)
        blobs[p] = b
    for p in changed + missing:
        data = subprocess.run(["git", "cat-file", "-p", blobs[p]], cwd=REPO, capture_output=True, check=True).stdout
        (REPO / p).parent.mkdir(parents=True, exist_ok=True)
        (REPO / p).write_bytes(data)
        print(f"restored: {p}")
    for p in new:
        if delete_new:
            (REPO / p).unlink()
            print(f"deleted (in no hash file): {p}")
        else:
            print(f"in no hash file (not deleted; use --delete-new): {p}")
    return check(key)


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    r = sub.add_parser("record")
    r.add_argument("key")
    r.add_argument("files", nargs="+")
    c = sub.add_parser("check")
    c.add_argument("key")
    s = sub.add_parser("restore")
    s.add_argument("key")
    s.add_argument("--delete-new", action="store_true")
    a = ap.parse_args()
    if a.cmd == "record":
        return record(a.key, a.files)
    if a.cmd == "check":
        return check(a.key)
    return restore(a.key, a.delete_new)


if __name__ == "__main__":
    sys.exit(main())
