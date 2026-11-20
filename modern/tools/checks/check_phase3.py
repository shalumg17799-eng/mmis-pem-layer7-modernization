"""Phase 3 orchestrator checks (phase-3-architecture command, step 3). Run from the repo root:

    modern/kag/.venv/Scripts/python.exe modern/tools/checks/check_phase3.py

Checks: rule coverage, field coverage, mapped columns exist in the data model, every endpoint lists roles, every event has a
producer and at least one consumer, every API call of a screen exists in a contract, the ADRs exist. Contract linting is
separate: `npx @redocly/cli lint modern/contracts/openapi/*.yaml` and `npx @asyncapi/cli validate modern/contracts/asyncapi/events.yaml`.
Exit code 1 if any check fails.
"""
import csv
import re
import sys
from collections import Counter
from pathlib import Path

import yaml

REPO = Path(__file__).resolve().parents[3]
M = REPO / "modern"
sys.path.insert(0, str(M / "kag" / "loaders"))
import load_01_source  # noqa: E402  (parsed legacy fields, the same source as data-layouts.md)

results = []


def check(name, problems):
    results.append((name, not problems, problems))


def read_csv(p):
    with open(p, encoding="utf-8", newline="") as f:
        return list(csv.DictReader(f))


# --- known services (overview service table)
services = set(re.findall(r"^\| ([a-z-]+(?:-service|-gateway|-ui)) \|", (M / "docs/architecture/overview.md").read_text(encoding="utf-8"), re.M))

# 1. rule coverage
ver = read_csv(M / "docs/analysis/rule-verification.csv")
r2s = read_csv(M / "docs/architecture/rule-to-service.csv")
cnt = Counter(r["rule_id"] for r in r2s)
own = {r["rule_id"]: r["service"] for r in r2s}
p = [f"{r['rule_id']} appears {cnt[r['rule_id']]} times" for r in ver if cnt[r["rule_id"]] != 1]
p += [f"{r['rule_id']} is DROPPED but owned by {own.get(r['rule_id'])}" for r in ver if r["status"] == "DROPPED" and own.get(r["rule_id"]) != "DROPPED"]
p += [f"unknown rule {x}" for x in cnt if x not in {r['rule_id'] for r in ver}]
p += [f"{x}: unknown service {s}" for x, s in own.items() if s != "DROPPED" and s not in services]
check(f"Rule coverage: {len(ver)} verified rules, each exactly once in rule-to-service.csv (DROPPED rules as DROPPED)", p)

# 2. field coverage
g = load_01_source.build()
expected = set()
for (rt, fl, fk, tl, tk) in g.rels:
    if rt == "HAS_FIELD":
        f = g.nodes[("Field", tk)]
        expected.add((fk, f["name"] if f["name"] != "FILLER" else f"FILLER@L{f['line']}"))
expected |= {(k, k.split(":")[1]) for (lab, k), pr in g.nodes.items() if lab == "Record" and pr.get("section") == "FILE"
             and not pr.get("viaCopybook") and not any(r[0] == "HAS_FIELD" and r[2] == k for r in g.rels)}
f2c = read_csv(M / "docs/architecture/field-to-column.csv")
got = Counter((r["record"], r["field"]) for r in f2c)
p = [f"{k} missing" for k in sorted(expected) if got[k] == 0] + [f"{k} appears {n} times" for k, n in got.items() if n > 1]
p += [f"{r['record']}:{r['field']} is DROP without a reason" for r in f2c if r["service"] == "DROP" and not r["transform"].strip()]
p += [f"{r['record']}:{r['field']} has no table/column" for r in f2c if r["service"] != "DROP" and not (r["table"] and r["column"])]
check(f"Field coverage: {len(expected)} legacy fields and flat records, each mapped to a column or DROP with a reason", p)

# 3. mapped columns exist in the data model
cols = {}
for sql in (M / "docs/architecture/data-model").glob("*.sql"):
    for t, body in re.findall(r"CREATE TABLE (\w+) \((.*?)\n\);", sql.read_text(encoding="utf-8"), re.S):
        cols[(sql.stem, t)] = {m.group(1) for m in re.finditer(r"^\s+([a-z_]+)\s+[A-Z]", body, re.M)}
p = [f"{r['service']}.{r['table']}.{r['column']}" for r in f2c if r["service"] != "DROP" and r["column"] not in cols.get((r["service"], r["table"]), set())]
check(f"Every mapped column exists in its service's data model ({len(cols)} tables)", p)

# 4. every endpoint lists roles
ops = set()
p = []
for f in sorted((M / "contracts/openapi").glob("*.yaml")):
    d = yaml.safe_load(f.read_text(encoding="utf-8"))
    for path, item in d["paths"].items():
        for meth, o in item.items():
            ops.add(f"{meth.upper()} {path}")
            roles = o.get("x-roles") or []
            sec = o.get("security")
            if not roles:
                p.append(f"{f.stem} {meth.upper()} {path}: no x-roles")
            elif roles == ["PUBLIC"]:
                if sec != []:
                    p.append(f"{f.stem} {meth.upper()} {path}: PUBLIC but secured")
            elif sec != [{"bearerAuth": roles}]:
                p.append(f"{f.stem} {meth.upper()} {path}: security roles differ from x-roles")
check(f"Every endpoint lists its roles ({len(ops)} operations)", p)

# 5. every event has a producer and at least one consumer
ev = yaml.safe_load((M / "contracts/asyncapi/events.yaml").read_text(encoding="utf-8"))
send, recv = Counter(), Counter()
for o in ev["operations"].values():
    name = o["messages"][0]["$ref"].split("/")[-1]
    (send if o["action"] == "send" else recv)[name] += 1
    if o["x-service"] not in services:
        send["?unknown service " + o["x-service"]] += 1
p = [f"{m}: {send[m]} producers, {recv[m]} consumers" for m in ev["components"]["messages"] if send[m] != 1 or recv[m] < 1]
p += [k for k in send if k.startswith("?")]
check(f"Every event has exactly one producer and at least one consumer ({len(ev['components']['messages'])} events)", p)

# 6. every API call of a screen exists in a contract
inv = (M / "docs/ux/screen-inventory.md").read_text(encoding="utf-8")
calls = re.findall(r"`((?:GET|POST|PUT|PATCH|DELETE) [^`]+)`", inv)
check(f"Every API call in screen-inventory.md exists in a contract ({len(calls)} calls)", [c for c in calls if c not in ops])

# 7. ADRs present
adrs = sorted((M / "docs/adr").glob("ADR-*.md"))
want = [f"ADR-{n:03d}" for n in range(1, 11)]
check("ADR-001 to ADR-010 exist with a Status line", [w for w in want if not any(a.name.startswith(w) and "Status:" in a.read_text(encoding="utf-8") for a in adrs)])

width = max(len(n) for n, _, _ in results)
for name, ok, probs in results:
    print(f"{'OK  ' if ok else 'FAIL'}  {name}")
    for x in probs[:10]:
        print(f"        - {x}")
print(f"\n{sum(ok for _, ok, _ in results)} of {len(results)} checks pass.")
sys.exit(0 if all(ok for _, ok, _ in results) else 1)
