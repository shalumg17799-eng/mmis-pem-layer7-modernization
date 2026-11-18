"""load_00_constraints: create the uniqueness constraints and indexes (idempotent).

Run:  python load_00_constraints.py [--dry-run]
"""
import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import common  # noqa: E402


def statements() -> list[str]:
    text = (common.KAG / "constraints.cypher").read_text(encoding="utf-8")
    body = "\n".join(l for l in text.splitlines() if not l.strip().startswith("//"))
    return [s.strip() for s in body.split(";") if s.strip()]


def missing_constraints(stmts: list[str]) -> list[str]:
    """Labels in common.LABEL_KEYS that have no uniqueness constraint on their key property."""
    joined = " ".join(stmts)
    return [f"{lab}.{key}" for lab, key in common.LABEL_KEYS.items()
            if f"(n:{lab}) REQUIRE n.{key} IS UNIQUE" not in joined]


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()
    stmts = statements()
    gaps = missing_constraints(stmts)
    if args.dry_run:
        print("== load_00_constraints --dry-run (no Neo4j connection)")
        print(f"statements: {len(stmts)} ({sum('CONSTRAINT' in s for s in stmts)} constraints, "
              f"{sum(s.startswith('CREATE INDEX') for s in stmts)} indexes)")
        print("labels without a uniqueness constraint:", gaps or "none")
        sys.exit(1 if gaps else 0)
    if gaps:
        sys.exit("constraints.cypher is missing: " + ", ".join(gaps))
    driver, db = common.connect()
    try:
        with driver.session(database=db) as s:
            for st in stmts:
                s.run(st).consume()
    finally:
        driver.close()
    print(f"== load_00_constraints: ran {len(stmts)} statements (idempotent)")


if __name__ == "__main__":
    main()
