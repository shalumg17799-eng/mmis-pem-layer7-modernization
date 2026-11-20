#!/usr/bin/env python3
"""MAM-88: generate the performance data set and load it into the running pem stack (ONE script).

  python modern/tools/perf/load_perf_dataset.py [--count 10000] [--out DIR] [--dry-run]

1. generates the set with `generate.py perf` (seed 20260115, deterministic) into a folder git ignores: modern/tools/perf/out/ when
   .gitignore covers it, else <system temp>/pem-perf-out; --out overrides, but a folder inside the repository that git does not
   ignore is refused. The 10,000-record files are never stored in the repository.
2. checks the generated SQL OFFLINE against the Flyway migrations of the two target tables (every NOT NULL column without a default
   is given, no column unknown, each value fits its type length and CHECK list, unique columns unique, at most 1,000 rows per
   statement, additive and re-runnable). A failed check stops the run before anything touches the stack.
3. loads, exactly as infra/scripts/seed.sh does (docker exec -i pem-postgres-1 psql -v ON_ERROR_STOP=1 --single-transaction -q -U
   pem_admin -d <db> < file; no password is read, none is printed):
     provider-perf.sql      -> provider_db.provider
     revalidation-perf.sql  -> revalidation_db.tracked_provider
   and prints the row counts before and after. A second run is a no-op (ON CONFLICT DO NOTHING).
--dry-run does steps 1 and 2 and PRINTS the commands step 3 would run; it never calls docker.

Which store each service reads (read from the services' code, 2026-11-20), and so what is loaded:
  provider_db.provider          GET /api/v1/providers and /{npi} (provider-service, JPA). Also read, only THROUGH provider-service's
                                REST API: tmsis-service (ACTIVE pages; it never reads the provider database), screening-service
                                (provider facts for a screening), directory-service (profile facts). So one table feeds all of them.
  revalidation_db.tracked_provider  the revalidation sweep (ExecuteSweep -> JdbcRevalidationStore.trackedProviders) reads ONLY this
                                read model, which ProviderStatusChanged events fill; a SQL load of provider_db sends no event, so it
                                is loaded directly (revalidation-perf.sql).
  not loaded                    enrollment_db (a submit checks duplicates in its own application table, and the story submits new
                                applications), directory_db.directory_entry (the directory list is not one of the story's
                                measurements; events fill it), screening/risk/sanctions/notification (their rows are made by the runs).
Removing the set: `bash modern/infra/scripts/seed.sh`. Its provider_db file TRUNCATEs provider (and provider_owner, audit_log,
outbox_event) and its revalidation_db file TRUNCATEs tracked_provider (and revalidation_case, sweep_run, outbox_event): both tables
this script fills are reset, so there is no separate unload. NOT reset by the seed (no seed folder): tmsis_db, i.e. the extract runs
and rows a measured T-MSIS extract leaves behind, and the Spring Batch job tables of the services.
"""
import sys

sys.dont_write_bytecode = True

import argparse
import os
import re
import shlex
import subprocess
import tempfile
from datetime import date
from pathlib import Path

REPO = Path(__file__).resolve().parents[3]
MODERN = REPO / "modern"
GENERATOR = MODERN / "tools/data/generate.py"
DEFAULT_OUT = MODERN / "tools/perf/out"
SERVICES = MODERN / "services"
MIGRATIONS = {"provider": SERVICES / "provider-service/src/main/resources/db/migration/provider",
              "tracked_provider": SERVICES / "revalidation-service/src/main/resources/db/migration/revalidation"}

CONTAINER = "pem-postgres-1"             # the compose project "pem" Postgres, as in infra/scripts/seed.sh; no other container
PG_USER = "pem_admin"
FILES = [  # (file, database, table, the count query of the perf block)
    ("provider-perf.sql", "provider_db", "provider"),
    ("revalidation-perf.sql", "revalidation_db", "tracked_provider"),
]
BATCH_ROWS = 1000
NPI_BLOCK = ("1007000000", "1007999999")  # keep equal to perf_dataset.NPI_BLOCK (the loader counts the perf rows by it)

TOKEN = re.compile(r"DATE '[^']*'|'(?:[^']|'')*'|NULL")


class Failed(Exception):
    pass


# ---- the output folder -------------------------------------------------------------------------------------------------------

def git_ignored(path: Path) -> bool | None:
    """True when git ignores the path, False when it does not, None when the path is outside the repository or git is missing."""
    try:
        r = subprocess.run(["git", "-C", str(REPO), "check-ignore", "-q", str(path)], capture_output=True)
    except OSError:
        return None
    return {0: True, 1: False}.get(r.returncode)


def inside_repo(path: Path) -> bool:
    return REPO.resolve() == path.resolve() or REPO.resolve() in path.resolve().parents


def choose_out(arg: Path | None) -> Path:
    if arg is not None:
        out = arg.resolve()
        if inside_repo(out) and git_ignored(out / "provider-perf.sql") is not True:
            raise Failed(f"refusing --out {out}: it is inside the repository and git does not ignore it "
                         "(the perf files are never stored in the repository); pick a folder outside it")
        return out
    if git_ignored(DEFAULT_OUT / "provider-perf.sql") is True:
        return DEFAULT_OUT
    fallback = Path(tempfile.gettempdir()) / "pem-perf-out"
    print(f"note: {DEFAULT_OUT.relative_to(REPO).as_posix()}/ is not covered by .gitignore, so the files go to {fallback}")
    print("      (add the line 'modern/tools/perf/out/' to .gitignore to use the repository folder, or pass --out)")
    return fallback


# ---- the offline check of the generated SQL against the Flyway migrations -----------------------------------------------------

def read_ddl(table: str) -> dict:
    """column -> {type, size, not_null, default, generated, unique, allowed} from the CREATE TABLE of the migration files,
    plus the ALTER TABLE ... ADD COLUMN of later versions."""
    folder = MIGRATIONS[table]
    files = sorted(folder.glob("V*__*.sql"), key=lambda p: int(re.match(r"V(\d+)__", p.name).group(1)))
    if not files:
        raise Failed(f"no migration files in {folder}")
    text = "\n".join(p.read_text(encoding="utf-8") for p in files)
    m = re.search(rf"CREATE TABLE {table}\s*\((.*?)\n\);", text, re.S)
    if not m:
        raise Failed(f"CREATE TABLE {table} not found in {folder}")
    cols = {}

    def add(line: str):
        line = re.sub(r"--.*$", "", line).strip().rstrip(",")
        mm = re.match(r"(\w+)\s+(\w+)(?:\((\d+)(?:,\d+)?\))?(.*)$", line, re.I)
        if not mm or mm.group(1).upper() in ("CONSTRAINT", "UNIQUE", "PRIMARY", "FOREIGN", "CHECK"):
            return
        name, typ, size, rest = mm.group(1), mm.group(2).upper(), mm.group(3), mm.group(4)
        chk = re.search(r"CHECK\s*\(\s*\w+\s+IN\s*\(([^)]*)\)\s*\)", rest, re.I)
        cols[name] = {
            "type": typ, "size": int(size) if size else None,
            "not_null": bool(re.search(r"NOT NULL|PRIMARY KEY", rest, re.I)),
            "default": bool(re.search(r"\bDEFAULT\b", rest, re.I)), "generated": bool(re.search(r"GENERATED", rest, re.I)),
            "unique": bool(re.search(r"\bUNIQUE\b|PRIMARY KEY", rest, re.I)),
            "allowed": set(re.findall(r"'([^']*)'", chk.group(1))) if chk else None}

    for line in m.group(1).splitlines():
        add(line)
    for a in re.finditer(rf"ALTER TABLE {table} ADD COLUMN (.*?);", text, re.S):
        add(a.group(1))
    return cols


def parse_sql(text: str, name: str) -> list:
    """The INSERT statements of a generated file: {table, columns, rows (lists of raw tokens), conflict}. Anything else that is
    not a comment is an error: the file is additive, with no TRUNCATE, DELETE, DROP, UPDATE or ALTER."""
    stmts, cur = [], None
    for n, line in enumerate(text.splitlines(), 1):
        s = line.strip()
        if not s or s.startswith("--"):
            continue
        head = re.fullmatch(r"INSERT INTO (\w+) \(([^)]*)\) VALUES", s)
        if head:
            if cur is not None:
                raise Failed(f"{name}:{n}: an INSERT starts before the previous one ended")
            cur = {"table": head.group(1), "columns": [c.strip() for c in head.group(2).split(",")], "rows": [],
                   "conflict": None}
            continue
        if cur is None:
            raise Failed(f"{name}:{n}: a statement that is not an INSERT: {s[:60]}")
        row = re.fullmatch(r"\((.*?)\),?", s)
        if row:
            inner = row.group(1)
            if re.sub(r"[,\s]", "", TOKEN.sub("", inner)):
                raise Failed(f"{name}:{n}: a value that is not a string, a DATE literal or NULL")
            cur["rows"].append(TOKEN.findall(inner))
        elif re.fullmatch(r"ON CONFLICT\s*(?:\([^)]*\))?\s*DO NOTHING;", s):
            cur["conflict"] = s
            stmts.append(cur)
            cur = None
        else:
            raise Failed(f"{name}:{n}: unexpected line: {s[:60]}")
    if cur is not None:
        raise Failed(f"{name}: the last INSERT has no ON CONFLICT ... DO NOTHING;")
    return stmts


def value_of(tok: str):
    if tok == "NULL":
        return None
    if tok.startswith("DATE '"):
        return ("date", tok[6:-1])
    return ("str", tok[1:-1].replace("''", "'"))


def check_file(path: Path, table: str) -> tuple:
    """Raises Failed on the first problem; returns (rows, statements, set of unique-column values by column, a report line)."""
    ddl = read_ddl(table)
    stmts = parse_sql(path.read_text(encoding="utf-8"), path.name)
    if not stmts:
        raise Failed(f"{path.name}: no INSERT statement")
    columns = stmts[0]["columns"]
    for st in stmts:
        if st["table"] != table or st["columns"] != columns:
            raise Failed(f"{path.name}: every INSERT must target {table} with one column list")
        if not 0 < len(st["rows"]) <= BATCH_ROWS:
            raise Failed(f"{path.name}: a statement has {len(st['rows'])} rows (1 to {BATCH_ROWS} expected)")
    unknown = [c for c in columns if c not in ddl]
    if unknown:
        raise Failed(f"{path.name}: columns {unknown} do not exist in {table} (migrations in {MIGRATIONS[table].relative_to(REPO).as_posix()})")
    required = [c for c, d in ddl.items() if d["not_null"] and not d["default"] and not d["generated"]]
    missing = [c for c in required if c not in columns]
    if missing:
        raise Failed(f"{path.name}: NOT NULL columns without a default are not filled: {missing}")
    defaulted = [c for c in ddl if c not in columns]
    seen = {c: set() for c, d in ddl.items() if d["unique"] and c in columns}
    total = 0
    for st in stmts:
        for tokens in st["rows"]:
            total += 1
            if len(tokens) != len(columns):
                raise Failed(f"{path.name}: a row of {table} has {len(tokens)} values for {len(columns)} columns")
            for col, tok in zip(columns, tokens):
                d, v = ddl[col], value_of(tok)
                if v is None:
                    if d["not_null"]:
                        raise Failed(f"{path.name}: NULL for NOT NULL column {table}.{col}")
                    continue
                kind, val = v
                if d["type"] == "DATE":
                    if kind != "date":
                        raise Failed(f"{path.name}: {table}.{col} is DATE but the value is {tok[:30]}")
                    try:
                        date.fromisoformat(val)
                    except ValueError:
                        raise Failed(f"{path.name}: {table}.{col} is not a real date: {val}") from None
                else:
                    if kind != "str":
                        raise Failed(f"{path.name}: {table}.{col} is {d['type']} but the value is {tok[:30]}")
                    if d["type"] in ("CHAR", "VARCHAR") and d["size"] and len(val) > d["size"]:
                        raise Failed(f"{path.name}: {table}.{col} value '{val[:20]}...' is longer than {d['size']}")
                    if d["type"] == "UUID" and not re.fullmatch(r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", val):
                        raise Failed(f"{path.name}: {table}.{col} is not a UUID: {val}")
                if d["allowed"] is not None and val not in d["allowed"]:
                    raise Failed(f"{path.name}: {table}.{col} value '{val}' violates its CHECK list {sorted(d['allowed'])}")
                if col in seen:
                    if val in seen[col]:
                        raise Failed(f"{path.name}: {table}.{col} value '{val}' appears twice (UNIQUE)")
                    seen[col].add(val)
    conflict_forms = {st["conflict"] for st in stmts}
    report = (f"{path.name}: {len(stmts)} INSERT statements, {total} rows ({max(len(s['rows']) for s in stmts)} at most per "
              f"statement), {len(columns)} columns of {table}; every NOT NULL column without a default is given; "
              f"not given (nullable or defaulted): {', '.join(defaulted)}; unique {', '.join(sorted(seen))} are distinct; "
              f"ends with {' / '.join(sorted(conflict_forms))}")
    return total, seen, report


# ---- generating and loading ---------------------------------------------------------------------------------------------------

def psql_cmd(db: str, *extra: str) -> list:
    return ["docker", "exec", "-i", CONTAINER, "psql", "-v", "ON_ERROR_STOP=1", *extra, "-q", "-U", PG_USER, "-d", db]


def shown(cmd: list, stdin: Path | None = None) -> str:
    text = " ".join(shlex.quote(a) for a in cmd)
    return text + (f" < {shlex.quote(stdin.as_posix())}" if stdin else "")


def run(cmd: list, stdin: Path | None = None, timeout: int = 900) -> subprocess.CompletedProcess:
    try:
        if stdin is None:
            return subprocess.run(cmd, capture_output=True, text=True, errors="replace", timeout=timeout)
        with stdin.open("rb") as f:
            return subprocess.run(cmd, stdin=f, capture_output=True, text=True, errors="replace", timeout=timeout)
    except (OSError, subprocess.TimeoutExpired) as e:
        raise Failed(f"could not run {cmd[0]}: {e}") from e


def count_cmd(db: str, table: str, where: str = "") -> list:
    return psql_cmd(db, "-At", "-c", f"SELECT count(*) FROM {table}{where}")


PERF_WHERE = f" WHERE npi BETWEEN '{NPI_BLOCK[0]}' AND '{NPI_BLOCK[1]}'"


def count(db: str, table: str, where: str = "") -> int:
    r = run(count_cmd(db, table, where), timeout=120)
    if r.returncode != 0 or not r.stdout.strip().isdigit():
        raise Failed(f"count on {db}.{table} failed (exit {r.returncode}): {(r.stdout + r.stderr).strip()[-300:]}")
    return int(r.stdout.strip())


def generate(out: Path, n: int) -> None:
    cmd = [sys.executable, str(GENERATOR), "perf", "--count", str(n), "--out", str(out)]
    r = subprocess.run(cmd, capture_output=True, text=True, errors="replace", env=dict(os.environ, PYTHONDONTWRITEBYTECODE="1"))
    if r.returncode != 0:
        raise Failed(f"generate.py perf failed (exit {r.returncode}): {(r.stdout + r.stderr).strip()[-400:]}")
    for line in r.stdout.strip().splitlines():
        print(line)


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--count", type=int, default=10_000, help="number of providers (default 10000, the story's size)")
    ap.add_argument("--out", type=Path, help="folder for the generated files (default: modern/tools/perf/out if git ignores it)")
    ap.add_argument("--dry-run", action="store_true",
                    help="generate and check offline, print the docker/psql commands that would run, touch no container")
    args = ap.parse_args()
    try:
        out = choose_out(args.out)
        print(f"generating {args.count} providers into {out}")
        generate(out, args.count)
        tables, npis = [], {}
        for fname, db, table in FILES:
            rows, seen, report = check_file(out / fname, table)
            if rows != args.count:
                raise Failed(f"{fname}: {rows} rows, expected {args.count}")
            print("offline check OK  " + report)
            tables.append((fname, db, table))
            npis[table] = seen["npi"]
        if npis["provider"] != npis["tracked_provider"]:
            raise Failed("provider-perf.sql and revalidation-perf.sql do not hold the same NPIs")
        print("offline check OK  both files hold the same NPIs")
        if args.dry_run:
            print("DRY RUN: nothing was run against a container. It WOULD run, in this order:")
            for fname, db, table in tables:
                print(f"  count before:  {shown(count_cmd(db, table))}")
            for fname, db, table in tables:
                print(f"  load {fname}:  {shown(psql_cmd(db, '--single-transaction'), out / fname)}")
            for fname, db, table in tables:
                print(f"  count after:   {shown(count_cmd(db, table))}")
                print(f"  perf rows:     {shown(count_cmd(db, table, PERF_WHERE))}")
            print("then check: perf rows == %d in both tables. Remove the set again with: bash modern/infra/scripts/seed.sh" % args.count)
            return 0
        r = run(["docker", "inspect", "-f", "{{.State.Running}}", CONTAINER], timeout=60)
        if r.returncode != 0 or r.stdout.strip() != "true":
            raise Failed(f"container {CONTAINER} is not running (is the pem stack up?): nothing was loaded")
        before = {t: count(db, t) for _, db, t in tables}
        present = {t: count(db, t, PERF_WHERE) for _, db, t in tables}
        for _, db, t in tables:
            print(f"before: {db}.{t} = {before[t]} rows ({present[t]} of them in the perf NPI block {NPI_BLOCK[0]}-{NPI_BLOCK[1]})")
        if any(present.values()) and any(v != args.count for v in present.values()):
            print("warning: some perf rows are already loaded and ON CONFLICT DO NOTHING keeps them: a different --count "
                  "needs the seed restored first (bash modern/infra/scripts/seed.sh)")
        for fname, db, table in tables:
            r = run(psql_cmd(db, "--single-transaction"), stdin=out / fname)
            if r.returncode != 0:
                done = [d for f, d, _ in tables[:tables.index((fname, db, table))]]
                raise Failed(f"loading {fname} into {db} failed (exit {r.returncode}): {(r.stdout + r.stderr).strip()[-400:]}; "
                             f"{db} keeps its previous state" + (f"; {', '.join(done)} was already loaded: run "
                                                                   "bash modern/infra/scripts/seed.sh to restore" if done else ""))
            print(f"loaded {fname} into {db}")
        ok = True
        for _, db, t in tables:
            after, perf = count(db, t), count(db, t, PERF_WHERE)
            print(f"after:  {db}.{t} = {after} rows ({perf} of them in the perf NPI block); added {after - before[t]}")
            ok = ok and perf == args.count
        if not ok:
            raise Failed(f"the perf NPI block does not hold {args.count} rows in both tables")
        print(f"OK: {args.count} perf providers loaded; remove them with: bash modern/infra/scripts/seed.sh")
        return 0
    except Failed as e:
        print(f"FAILED: {e}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
