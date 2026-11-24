"""The /fe-analyze step of the HITL change loop: change-request file + Jira snapshots -> diff, impact analysis and plan.md.

  python modern/tools/changes/fe_plan.py modern/changes/<CR>.md [--changes-dir modern/changes] [--no-neo4j]

Reads `modern/changes/<CR>.md` (it only points at the Jira tickets), then for every ticket the snapshot
`modern/changes/<CR-ID>/jira/<KEY>.json` that the command wrote from the read-only Jira fetch, compares it with
`modern/specs/<KEY>.md` (fe_diff.py), runs the impact analysis (fe_impact.py) for the tickets that differ, and writes into
`modern/changes/<CR-ID>/`: diff.json, impact.json, plan.md. It changes nothing else: no source, no test, no spec, no git, no Jira.

Exit code: 0 = no difference (nothing to build), 10 = a plan was written, 3 = a snapshot is missing, 2 = unreadable input.
Standard library only.
"""
from __future__ import annotations

import argparse
import datetime as dt
import sys
from pathlib import Path

sys.dont_write_bytecode = True
sys.path.insert(0, __import__("os").path.dirname(__import__("os").path.abspath(__file__)))  # so that `python -I` finds fe_common

import fe_common as c  # noqa: E402
import fe_diff  # noqa: E402
import fe_impact  # noqa: E402


def _bullets(items, empty="none") -> str:
    return "\n".join(f"- `{i}`" for i in items) if items else f"- {empty}"


def _ac_block(ac_id: str, a: dict) -> list:
    out = [f"- **{ac_id}** ({a['kind']}{'' if a['behaviour'] else ', text or basis only'}): {a['title']}", f"  - {a['why']}"]
    r = a["refs"]
    cited = [*r["rules"], *r["decisions"], *r["questions"], *r["adrs"]]
    if cited:
        out.append("  - cites: " + ", ".join(cited))
    return out


def render_ticket(key: str, d: dict, imp: dict | None) -> str:
    out = [f"## Ticket {key}", ""]
    out.append(f"Baseline: `modern/specs/{key}.md` version {d.get('specVersion')}, Jira timestamp {d.get('specJiraUpdated')}. "
               f"Jira now: updated {d.get('jiraUpdated')}, status {d.get('jiraStatus')}.")
    out += ["", "### 1. What changed in Jira", "", fe_diff.render_markdown(key, d).split("\n", 2)[2] if not d["noDifference"] else "no difference", ""]
    if d["noDifference"] or imp is None:
        return "\n".join(out)
    out += ["### 2. Impact (rule -> story -> AC -> test -> code)", "",
            f"Story: {imp['title']} (service `{imp['service']}`). Rules cited by the affected ACs: "
            + (", ".join(imp["rules"]) or "none") + ".", ""]
    for i, a in imp["acs"].items():
        out += _ac_block(i, a)
    out += ["", "### 3. Tests that must change (only tests whose AC changed)", "",
            "| Test file | ACs | Action | Why |", "|---|---|---|---|"]
    rows = {}
    for i, a in imp["acs"].items():
        for t in a["tests"]:
            rows.setdefault(t, []).append((i, a))
    for t, items in sorted(rows.items()):
        out.append(f"| `{t}` | {', '.join(i for i, _ in items)} | {', '.join(sorted({a['action'] for _, a in items}))} | "
                   + "; ".join(f"{i}: {a['why']}" for i, a in items).replace("|", "/") + " |")
    new_acs = [i for i, a in imp["acs"].items() if a["kind"] == "added"]
    no_test = [i for i, a in imp["acs"].items() if not a["tests"] and a["kind"] != "added"]
    if not rows:
        out.append("| (no existing test cites these ACs) | | | |")
    for i in new_acs:
        out.append(f"| NEW test for {i} | {i} | add tests | no test can exist yet: {imp['acs'][i]['why']} |")
    if no_test:
        out += ["", "Changed or removed ACs with NO test that cites them: " + ", ".join(no_test) + " (the test-writer must find or write the tests; flagged as a risk below)."]
    if imp["storyLevelTests"]:
        out += ["", "Where new tests of this story usually go (story-level files that cite the key but no AC):", _bullets(imp["storyLevelTests"][:8])]
    unchanged_files = sorted(
        {t for ac in (d["acs"]["unchanged"]) for t in fe_impact.find_tests(key, [ac])["byAc"].get(ac, [])} - set(rows))
    out += ["", f"Tests of unchanged ACs ({', '.join(d['acs']['unchanged']) or 'none'}) are not touched: {len(unchanged_files)} test file(s) cite only unchanged ACs of this story."]
    pf = imp["productionFiles"]
    out += ["", "### 4. Files likely to change (production code)", "",
            "Most likely (code of the rules that the changed ACs cite, from the rule trace):", _bullets(pf["byRule"], "no rule of the changed ACs has a code trace"),
            "", "Possible (the rest of the story's code units, from its trace.json):", _bullets(pf["storyLevel"])]
    out += ["", "### 5. Contracts, web UI, batch jobs and journeys", "", "**Contracts** (an AC names this path or event; a contract change needs your approval, tdd-workflow section 7):"]
    out += [f"- `{h['file']}:{h['line']}` {h['kind']} `{h['token']}`" for h in imp["contracts"]] or ["- none found"]
    out += ["", "**Web UI** (files that mention the fields the changed ACs name; a heuristic, not a trace):"]
    out += [f"- `{h['file']}` ({', '.join(h['names'])})" for h in imp["webUi"]] or ["- none found"]
    b = imp["batch"]
    out += ["", "**Batch jobs**: modern endpoints " + (", ".join(f"`{e}`" for e in b["modernEndpoints"]) or "none")
            + "; legacy programs " + (", ".join(b["legacyPrograms"]) or "none") + "; legacy job steps " + (", ".join(b["jobSteps"]) or "none") + "."]
    out += ["", "**Journeys that must run:**"]
    out += [f"- {j['journey']} (`{j['file']}`): {j['reason']}" for j in imp["journeys"]] or ["- none cited; J1 and J2 are run anyway as the smoke check"]
    out += ["", "### 6. Checks that /fe-approve will run", ""]
    out += [f"- `./gradlew --no-daemon {t}`" for t in imp["checks"]["gradle"]] or ["- no Gradle module identified"]
    out += ["- `cd modern/web-ui && npm test` (the web UI unit tests)" if imp["checks"]["ui"] else "- web UI tests: not expected to be affected (run anyway if a UI file is touched)"]
    out += ["- the test-protection check for the story after every green attempt: `python modern/tools/checks/test_protection.py check " + key + "`"]
    ctx = imp["legacyContext"]
    out += ["", "### 7. Legacy context (knowledge graph, read-only)", ""]
    if "unavailable" in ctx:
        out.append(f"Not available: {ctx['unavailable']}. The legacy facts in section 2 come from the rule trace and the spec only.")
    else:
        for r in ctx["rules"]:
            out.append(f"- `{r['id']}` {r.get('verification')}: {r.get('name')}; programs {', '.join(r.get('programs') or []) or '-'}; "
                       f"paragraph {r.get('paragraph') or '-'}; evidence {r.get('evidence') or '-'}; defects {', '.join(r.get('defects') or []) or '-'}; questions {', '.join(r.get('questions') or []) or '-'}")
        if not ctx["rules"]:
            out.append(f"- no cited rule found in `{ctx.get('database')}`")
    out += ["", "### 8. Risks and points to confirm", ""]
    risks = []
    if imp["sharedRuleStories"]:
        risks.append("Other stories cite the same rules and may be affected; their tests are not planned, run their service checks if the shared code changes: " + ", ".join(imp["sharedRuleStories"]) + ".")
    if no_test:
        risks.append("No test cites " + ", ".join(no_test) + ": the change cannot be proven red until a test exists.")
    if imp["contracts"] and any(a["behaviour"] for a in imp["acs"].values()):
        risks.append("The changed ACs touch contracted paths or events: if the contract itself must change, stop and ask (a contract change is not part of this plan).")
    if any(a["kind"] == "removed" for a in imp["acs"].values()):
        risks.append("A removed AC: the tests of that AC are removed by the test-writer and the recorded hashes are re-recorded; the AC number is never reused.")
    if d["sections"]["legacyTrace"]["changed"] or d["sections"]["technicalNotes"]["changed"]:
        risks.append("Agent-owned sections (legacy trace, technical notes) differ in Jira: they are not a requirement and are not acted on.")
    if d["title"]["changed"]:
        risks.append("The title changed: the spec title is updated, no test depends on it.")
    risks.append("The Jira text in the snapshot was copied by the assistant from the Jira reply: compare the sha256 `" + d["jiraSha256"][:16] + "...` with a fresh read if a difference looks wrong.")
    out += [f"- {r}" for r in risks]
    return "\n".join(out) + "\n"


def run(md_path, changes_dir=c.CHANGES, neo4j: bool = True):
    cr = c.read_change_request(md_path)
    out_dir = Path(changes_dir) / cr["cr"]
    if not cr["jira"]:
        print(f"{md_path}: no Jira key in the front matter (jira: [MAM-30]) and none in the text", file=sys.stderr)
        return 2, None
    results, missing = {}, []
    for key in cr["jira"]:
        snap_path = out_dir / "jira" / f"{key}.json"
        if not snap_path.exists():
            missing.append(key)
            continue
        spec = c.read_spec(c.spec_path(key))
        snap = fe_diff.load_snapshot(snap_path)
        d = fe_diff.diff(spec.body, snap["description"], spec.front.get("title", "").strip('"'), snap.get("summary"))
        d.update(key=key, specVersion=spec.version, specJiraUpdated=spec.front.get("jiraUpdated"),
                 jiraUpdated=snap.get("updated"), jiraStatus=snap.get("status"))
        results[key] = (d, None if d["noDifference"] else fe_impact.analyse(key, d, neo4j=neo4j))
    if missing:
        print("Jira snapshot missing for: " + ", ".join(missing) + f" (expected at {out_dir / 'jira'}/<KEY>.json, written from the read-only Jira fetch)", file=sys.stderr)
        return 3, None
    c.write_json(out_dir / "diff.json", {k: v[0] for k, v in results.items()})
    c.write_json(out_dir / "impact.json", {k: v[1] for k, v in results.items() if v[1]})
    changed = [k for k, v in results.items() if not v[0]["noDifference"]]
    now = dt.datetime.now().astimezone().strftime("%Y-%m-%d %H:%M %Z")
    lines = [f"# Change plan {cr['cr']}", "",
             f"Generated {now} by `modern/tools/changes/fe_plan.py` from `{c.rel(md_path)}`. Tickets: {', '.join(cr['jira'])}. "
             "Jira is the only source of the requirement; the note in the change-request file is not read as one.", ""]
    if changed:
        lines += [f"**Status: PLAN. Waiting for you: STOP here and run `/fe-approve {cr['cr']}` to build it on the local branch `cr/{cr['cr']}`, or `/fe-discard {cr['cr']}`.**", ""]
    else:
        lines += [f"**Status: NO CHANGE. The ticket(s) {', '.join(cr['jira'])} equal the baseline spec: nothing to build, `/fe-approve` does not apply.**", ""]
    lines += ["## Summary", "", "| Ticket | Result |", "|---|---|"]
    lines += [f"| {k} | {fe_diff.summary(v[0])} |" for k, v in results.items()]
    lines.append("")
    if cr["note"]:
        lines += ["## Note in the change-request file (data, not a requirement)", "", *("> " + ln for ln in cr["note"].split("\n")), ""]
    for k, (d, imp) in results.items():
        lines.append(render_ticket(k, d, imp))
    lines += ["", "## Next step", "",
              (f"`/fe-approve {cr['cr']}` (build on `cr/{cr['cr']}`: tests first and red, then code until green, local commit only, no deploy, no push, no Jira change) "
               f"or `/fe-discard {cr['cr']}`. **STOP: nothing has been changed except the files in `modern/changes/{cr['cr']}/`.**") if changed
              else "Nothing to do: the Jira description equals the baseline spec. `/fe-approve` does not apply."]
    plan = out_dir / "plan.md"
    plan.parent.mkdir(parents=True, exist_ok=True)
    plan.write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")
    print(f"plan written: {c.rel(plan)}; " + "; ".join(f"{k}: {fe_diff.summary(v[0])}" for k, v in results.items()))
    return (10 if changed else 0), plan


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("change_request")
    ap.add_argument("--changes-dir", default=str(c.CHANGES))
    ap.add_argument("--no-neo4j", action="store_true")
    a = ap.parse_args(argv)
    try:
        code, _ = run(a.change_request, a.changes_dir, neo4j=not a.no_neo4j)
    except (OSError, ValueError) as e:
        print(f"error: {e}", file=sys.stderr)
        return 2
    return code


if __name__ == "__main__":
    sys.exit(main())
