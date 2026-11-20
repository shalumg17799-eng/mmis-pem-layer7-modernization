"""Export the legacy record layouts from the PEM knowledge graph (MAM-42 AC-1).

Writes modern/libs/fixed-width/src/main/resources/layouts/<NAME>.json, one file per legacy file layout, from the
Record and Field nodes the kag loaders built from the COBOL source. Each file names the read query that produced it.
The layouts are never typed by hand: run this again when the graph changes.

Read only: the session is opened in READ mode and every query is a MATCH ... RETURN.

Usage (repository root, the NEO4J_* variables loaded; the database is medicaid-kag, see PROGRESS.md Q-002):
    NEO4J_DATABASE=medicaid-kag modern/kag/.venv/Scripts/python.exe modern/tools/data/export_layouts.py

Rules that are not in the graph (each from a recorded decision):
- Field types: PIC X -> ALPHANUMERIC, PIC 9 -> NUMERIC; a PIC 9(08) field named *-DATE is a CCYYMMDD date (D-004) and
  a PIC 9(05) field named *YYDDD* is a YYDDD date, which only the T-MSIS row has (D-015).
- The PRV-VARIANT REDEFINES views are chosen by PRV-TYPE-CODE: PRV-INDIVIDUAL-DATA for IN, PRV-ORG-DATA for GR and FA
  (D-008; the copybook comments "Individual provider view" and "Group / Facility view"). Group items are not exported.
- APPINPUT is PIC X(200): the PRVRECRD fields in bytes 1-130 and one RESERVED field after them (D-025).
- An elementary record with no sub-fields (APPERROR, PIC X(250)) is one alphanumeric field named after the record.
"""
import json
import os
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[3]
OUT = REPO / "modern/libs/fixed-width/src/main/resources/layouts"

# layout file name -> (record id in the graph, the master record it carries, or None)
LAYOUTS = {
    "PRVRECRD": ("PRVRECRD:PROVIDER-RECORD", None),
    "SCRNREC": ("SCRNREC:SCREENING-RECORD", None),
    "APPINPUT": ("PRVENR01:APP-INPUT-RECORD", "PRVRECRD:PROVIDER-RECORD"),
    "APPERROR": ("PRVENR01:APP-ERROR-RECORD", None),
    "SANCIN": ("PRVSAN01:SANCTION-IN-RECORD", None),
    "REVALQ": ("PRVREV01:REVAL-OUT-RECORD", None),
    "TMSISOUT": ("PRVTMS01:WS-TMSIS-DETAIL", None),
    "DIROUT": ("PRVDIR01:DIR-OUT-RECORD", None),
}

DISCRIMINATOR = "PRV-TYPE-CODE"
VIEW_GROUPS = {"PRV-INDIVIDUAL-DATA": ["IN"], "PRV-ORG-DATA": ["GR", "FA"]}  # D-008

FIELDS = ("RETURN r.name AS recordName, r.length AS recordLength, f.name AS name, f.level AS level, f.pic AS pic, "
          "f.start AS start, f.length AS length, f.group AS isGroup, f.line AS line ORDER BY f.line")


def read_query(record_id: str, carried: str | None) -> str:
    if carried is None:
        return (f"MATCH (r:Record {{id: '{record_id}'}}) OPTIONAL MATCH (r)-[:HAS_FIELD]->(f:Field) " + FIELDS)
    return (f"MATCH (a:Record {{id: '{record_id}'}}) MATCH (r:Record {{id: '{carried}'}})-[:HAS_FIELD]->(f:Field) "
            "RETURN a.name AS recordName, a.length AS recordLength, r.length AS carriedLength, f.name AS name, "
            "f.level AS level, f.pic AS pic, f.start AS start, f.length AS length, f.group AS isGroup, f.line AS line "
            "ORDER BY f.line")


def field_type(name: str, pic: str) -> str:
    if pic.startswith("X"):
        return "ALPHANUMERIC"
    if re.fullmatch(r"9\(0*8\)", pic) and name.endswith("-DATE"):
        return "DATE_CCYYMMDD"
    if re.fullmatch(r"9\(0*5\)", pic) and "YYDDD" in name:
        return "DATE_YYDDD"
    if re.fullmatch(r"9\(\d+\)", pic):
        return "NUMERIC"
    raise ValueError(f"field {name}: PIC {pic} is not a supported fixed-width type")


def fields_of(rows: list) -> list:
    out, group, group_level = [], None, None
    for row in rows:
        if row["name"] is None:
            continue
        if group is not None and row["level"] <= group_level:
            group = None
        if row["isGroup"]:
            group, group_level = row["name"], row["level"]
            continue
        views = VIEW_GROUPS.get(group, []) if group else []
        out.append({"name": row["name"], "start": row["start"], "length": row["length"],
                    "type": field_type(row["name"], row["pic"]), "views": views})
    return out


def layout(session, name: str) -> dict:
    record_id, carried = LAYOUTS[name]
    query = read_query(record_id, carried)
    rows = [dict(r) for r in session.run(query)]
    if not rows:
        raise SystemExit(f"{record_id} is not in the graph")
    length = rows[0]["recordLength"]
    fields = fields_of(rows)
    if carried is not None:
        carried_length = rows[0]["carriedLength"]
        fields.append({"name": "RESERVED", "start": carried_length + 1, "length": length - carried_length,
                       "type": "ALPHANUMERIC", "views": []})
    if not fields:
        fields = [{"name": rows[0]["recordName"], "start": 1, "length": length, "type": "ALPHANUMERIC", "views": []}]
    has_views = any(f["views"] for f in fields)
    return {"name": name, "source": record_id, "length": length, "readQuery": query,
            "discriminator": DISCRIMINATOR if has_views else None, "fields": fields}


def main() -> int:
    from neo4j import GraphDatabase
    missing = [v for v in ("NEO4J_URI", "NEO4J_USERNAME", "NEO4J_PASSWORD", "NEO4J_DATABASE") if not os.environ.get(v)]
    if missing:
        sys.exit("Cannot connect: environment variable(s) not set: " + ", ".join(missing))
    driver = GraphDatabase.driver(os.environ["NEO4J_URI"],
                                  auth=(os.environ["NEO4J_USERNAME"], os.environ["NEO4J_PASSWORD"]))
    OUT.mkdir(parents=True, exist_ok=True)
    try:
        with driver.session(database=os.environ["NEO4J_DATABASE"], default_access_mode="READ") as s:
            for name in LAYOUTS:
                data = layout(s, name)
                (OUT / f"{name}.json").write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8", newline="\n")
                print(f"{name}: {data['length']} bytes, {len(data['fields'])} fields <- {data['source']}")
    finally:
        driver.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
