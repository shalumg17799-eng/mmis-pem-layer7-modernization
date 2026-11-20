"""Builds modern/docs/reports/requirement-checklist.md: the Part C requirement checklist of the FINAL PLAN (2026-11-19).

Sources (all in the repo, nothing is typed by hand):
- modern/specs/MAM-*.md            the 78 Jira items (64 of type Story = the business stories), service, title, rules
- modern/docs/reports/stories/     <KEY>.md (Outcome line, Files created/changed), <KEY>.tests.sha256, <KEY>/trace.json
- modern/docs/traceability/rule-trace.csv   rule -> story -> tests (run build_rule_trace.py first)
- modern/docs/architecture/rule-to-service.csv   the 433 non-DROPPED rules
- modern/services/*/src/main        business code per service
- modern/web-ui/src/app/screens.tsx + App.tsx   the routed screens
- modern/docs/analysis/questions.md, modern/docs/PROGRESS.md   open questions and BLOCKED items
Run: modern/kag/.venv/Scripts/python.exe modern/tools/trace/build_checklist.py
"""
from __future__ import annotations

import csv
import json
import re
from datetime import datetime
from pathlib import Path

REPO = Path(__file__).resolve().parents[3]
M = REPO / "modern"
STORIES = M / "docs/reports/stories"
SERVICES = ["provider-service", "enrollment-service", "screening-service", "risk-service", "revalidation-service",
            "sanctions-service", "notification-service", "directory-service", "tmsis-service"]
JOURNEYS = {"J1": "MAM-69", "J2": "MAM-72", "J3": "MAM-74", "J4": "MAM-76", "J5": "MAM-79", "J6": "MAM-81", "J7": "MAM-83"}
CODE = re.compile(r"(?:modern/)?((?:services|libs|web-ui|infra|testdata|tools)/[\w./{}*,-]+\.(?:java|sql|tsx?|py|sh|json|ya?ml|css))")


def specs() -> dict[str, dict]:
    out = {}
    for f in sorted(M.glob("specs/MAM-*.md"), key=lambda p: int(p.stem.split("-")[1])):
        fm = f.read_text(encoding="utf-8").split("---\n", 2)[1]
        meta = dict(line.split(": ", 1) for line in fm.splitlines() if ": " in line)
        out[meta["key"]] = {"title": meta.get("title", "").strip('"'), "service": meta.get("service", ""),
                            "type": meta.get("type", ""), "rules": [r.strip() for r in meta.get("rules", "").strip("[]").split(",") if r.strip()]}
    return out


def source_index(service: str) -> dict[str, list[str]]:
    """Base name -> main source files of the story's service (Java classes, SQL migrations, web-ui components)."""
    roots = {"web-ui": [M / "web-ui/src"]}.get(service) or [M / "services" / service / "src/main", M / service / "src/main"]
    index: dict[str, list[str]] = {}
    for root in roots:
        if root.exists():
            for p in root.rglob("*"):
                if p.suffix in (".java", ".sql", ".tsx", ".ts") and p.is_file():
                    index.setdefault(p.stem, []).append(p.relative_to(M).as_posix())
    return index


def report(key: str, service: str = "") -> dict:
    f = STORIES / f"{key}.md"
    text = f.read_text(encoding="utf-8") if f.exists() else ""
    outcome = next((l.split(":", 1)[1].strip() for l in text.splitlines() if l.startswith("Outcome:")), "")
    files = text.split("Files created/changed:", 1)[1].split("Checks run:", 1)[0] if "Files created/changed:" in text else ""
    code = sorted({m for m in CODE.findall(files) if "/test/" not in m and "/tests/" not in m and "/e2e/" not in m})
    if text and service:  # class or file names named in the report, matched to real main sources of the service
        index = source_index(service)
        for name in set(re.findall(r"\b([A-Z][A-Za-z0-9]+|V\d+__\w+)\b", text)):
            code = sorted(set(code) | set(index.get(name, [])))
    tj = STORIES / key / "trace.json"
    if tj.exists():
        try:
            code = sorted(set(code) | {c.removeprefix("modern/") for c in json.loads(tj.read_text(encoding="utf-8")).get("codeUnits", [])})
        except json.JSONDecodeError:
            pass
    # DONE = built with its tests (TDD); BUILT = built in the FINAL PLAN build phase, tests in Part D
    return {"exists": bool(text), "outcome": outcome, "built": outcome.upper().startswith(("DONE", "BUILT")),
            "tested": outcome.upper().startswith("DONE"),
            "tests": (STORIES / f"{key}.tests.sha256").exists(), "code": code}


def main() -> None:
    sp = specs()
    rep = {k: report(k, sp[k]["service"]) for k in sp}
    business = [k for k, s in sp.items() if s["type"] == "Story"]
    lines = [f"# Requirement checklist (FINAL PLAN Part C)", "",
             f"Generated {datetime.now():%Y-%m-%d %H:%M} by `modern/tools/trace/build_checklist.py` from the repo; do not edit by hand.", ""]

    built = [k for k in business if rep[k]["built"]]
    tested = [k for k in built if rep[k]["tested"]]
    lines += [f"## 1. Business stories: {len(built)} of {len(business)} built", "",
              f"{len(tested)} built test-first (Outcome DONE); {len(built) - len(tested)} built in the build phase with their "
              "tests in Part D (Outcome BUILT).", "",
              "| Story | Service | Title | Built | Outcome (story report) |", "|---|---|---|---|---|"]
    for k in business:
        r = rep[k]
        lines.append(f"| {k} | {sp[k]['service']} | {sp[k]['title']} | {'yes' if r['built'] else 'no'} | "
                     f"{(r['outcome'] or 'no report yet').replace('|', '/')[:140]} |")
    tasks = [k for k, s in sp.items() if s["type"] != "Story"]
    lines += ["", f"Tasks (not business stories): " + ", ".join(f"{k} ({'built' if rep[k]['built'] else 'open'})" for k in tasks), ""]

    owner = {r["rule_id"]: r["service"] for r in csv.DictReader(open(M / "docs/architecture/rule-to-service.csv", encoding="utf-8"))}
    kept = sorted(r for r, svc in owner.items() if svc != "DROPPED")
    trace = {r["rule_id"]: r for r in csv.DictReader(open(M / "docs/traceability/rule-trace.csv", encoding="utf-8"))}
    gaps = []
    for rid in kept:
        t = trace.get(rid, {})
        story = t.get("story", "")
        if not story:
            gaps.append((rid, "", "no story"))
        elif not rep.get(story, {}).get("built"):
            gaps.append((rid, story, "story not built"))
        elif not rep[story]["code"]:
            gaps.append((rid, story, "no code unit recorded for the story"))
    lines += [f"## 2. Legacy rules: {len(kept) - len(gaps)} of {len(kept)} non-dropped rules reach built code; {len(gaps)} gaps", ""]
    if gaps:
        by = {}
        for rid, story, why in gaps:
            by.setdefault((story, why), []).append(rid)
        lines += ["| Story | Gap | Rules |", "|---|---|---|"]
        for (story, why), rids in sorted(by.items()):
            lines.append(f"| {story or '-'} | {why} | {len(rids)}: {', '.join(rids[:6])}{' ...' if len(rids) > 6 else ''} |")
        lines.append("")

    lines += ["## 3. Services with business code", "", "| Service | Main classes | Stories built / total |", "|---|---|---|"]
    for svc in SERVICES:
        n = len(list((M / "services" / svc / "src/main/java").rglob("*.java"))) if (M / "services" / svc).exists() else 0
        st = [k for k in business if sp[k]["service"] == svc]
        lines.append(f"| {svc} | {n} | {sum(rep[k]['built'] for k in st)} / {len(st)} |")
    lines.append("")

    screens = M / "web-ui/src/app/screens.tsx"
    rows = re.findall(r"\{ path: '([^']+)', title: '([^']+)'.*?element: <(\w+)", screens.read_text(encoding="utf-8")) if screens.exists() else []
    app = (M / "web-ui/src/app/App.tsx").read_text(encoding="utf-8") if (M / "web-ui/src/app/App.tsx").exists() else ""
    if "/directory" in app:
        rows.append(("/directory", "Find a Medicaid provider (public)", "PublicDirectory"))
    lines += [f"## 4. Screens: {sum(e != 'Placeholder' for _, _, e in rows)} of {len(rows)} built", "",
              "| Route | Title | Component |", "|---|---|---|"]
    lines += [f"| {p} | {t} | {e}{' (placeholder)' if e == 'Placeholder' else ''} |" for p, t, e in rows]
    lines += ["", "UX overhaul status per screen: see the UI agent's group reports and `modern/docs/reports/ui-screenshots/`.", ""]

    lines += ["## 5. Journeys", "", "| Journey | Story | Test | Story built | Click steps |", "|---|---|---|---|---|"]
    for j, k in JOURNEYS.items():
        test = next(iter(sorted((M / "web-ui/e2e/journeys").glob(f"{j.lower()}-*.spec.ts"))), None)
        lines.append(f"| {j} | {k} | {test.name if test else 'not written'} | {'yes' if rep[k]['built'] else 'no'} | "
                     f"modern/docs/ux/journeys.md#{j}{' and modern/docs/DEMO.md' if j == 'J1' else ''} |")
    lines.append("")

    q = (M / "docs/analysis/questions.md").read_text(encoding="utf-8")
    open_q = re.findall(r"^## (Q-\d+)\.(.*?)status: (OPEN[^)]*)\)", q, re.M)
    lines += [f"## 6. Open questions ({len(open_q)}) and BLOCKED items", ""]
    lines += [f"- {qid}: {title.strip()[:120]} ({status})" for qid, title, status in open_q]
    blocked = [l for l in (M / "docs/PROGRESS.md").read_text(encoding="utf-8").splitlines() if l.startswith("| 20") and "BLOCKED" in l]
    lines += [f"- PROGRESS: {l[:200]}" for l in blocked[:20]] or ["- no BLOCKED rows"]
    out = M / "docs/reports/requirement-checklist.md"
    out.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"wrote {out.relative_to(REPO).as_posix()}: stories built {len(built)}/{len(business)}, rule gaps {len(gaps)}, "
          f"screens {sum(e != 'Placeholder' for _, _, e in rows)}/{len(rows)}, open questions {len(open_q)}")


if __name__ == "__main__":
    main()
