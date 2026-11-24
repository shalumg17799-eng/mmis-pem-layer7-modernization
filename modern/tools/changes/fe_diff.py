"""Diff of a Jira story against its baseline spec, by acceptance-criterion id (HITL change loop, step /fe-analyze).

  python modern/tools/changes/fe_diff.py <modern/specs/KEY.md> <jira-snapshot.json> [--json out.json] [--markdown out.md]

The snapshot is a JSON file written by the /fe-analyze command from the Jira read-only fetch:
  {"key": "MAM-30", "summary": "<title>", "description": "<the description, Markdown, exactly as Jira returns it>",
   "updated": "<Jira timestamp>", "status": "<status name>"}

What is compared, in this order: the title; the business requirement; every AC by id (added / removed / changed, a changed one
with the fields given, when, then, basis, title and the old and new text); the out-of-scope list; the two agent-owned sections
(legacy trace, technical notes), which are reported but are never a requirement change. Texts are compared after the Markdown
normalisation of fe_common.canon (line endings, escapes such as `\\*`, blanks), so a description that Jira has re-formatted
without changing a word is "no difference" and is flagged `formattingOnly`.

Exit code: 0 = no difference, 10 = differences, 2 = the input cannot be read.
Standard library only. Synthetic data only.
"""
from __future__ import annotations

import argparse
import json
import sys

sys.dont_write_bytecode = True
sys.path.insert(0, __import__("os").path.dirname(__import__("os").path.abspath(__file__)))  # so that `python -I` finds fe_common

import fe_common as c  # noqa: E402

BEHAVIOUR = ("given", "when", "then")
AC_FIELDS = ("title", "given", "when", "then", "basis", "extra")


def _same(a: str, b: str) -> bool:
    return c.canon(a) == c.canon(b)


def diff(spec_body: str, jira_body: str, spec_title: str | None = None, jira_title: str | None = None) -> dict:
    s_sec, j_sec = c.split_sections(spec_body), c.split_sections(jira_body)
    s_acs = c.parse_acs(c.section(s_sec, "Acceptance criteria"))
    j_acs = c.parse_acs(c.section(j_sec, "Acceptance criteria"))

    added = [dict(id=i, **j_acs[i]) for i in j_acs if i not in s_acs]
    removed = [dict(id=i, **s_acs[i]) for i in s_acs if i not in j_acs]
    changed, unchanged = [], []
    for i in s_acs:
        if i not in j_acs:
            continue
        fields = {f: {"old": s_acs[i][f], "new": j_acs[i][f]} for f in AC_FIELDS if not _same(s_acs[i][f], j_acs[i][f])}
        if fields:
            changed.append({"id": i, "fields": fields, "behaviour": any(f in fields for f in BEHAVIOUR),
                            "title": j_acs[i]["title"] or s_acs[i]["title"]})
        else:
            unchanged.append(i)
    order = lambda i: c.ac_number(i)  # noqa: E731
    added.sort(key=lambda a: order(a["id"]))
    removed.sort(key=lambda a: order(a["id"]))
    changed.sort(key=lambda a: order(a["id"]))

    def sec(name_prefix: str) -> dict:
        old, new = c.section(s_sec, name_prefix), c.section(j_sec, name_prefix)
        return {"changed": not _same(old, new), "old": old, "new": new}

    sections = {
        "businessRequirement": sec("Business requirement"),
        "outOfScope": sec("Out of scope"),
        "legacyTrace": sec("Legacy trace"),
        "technicalNotes": sec("Technical notes"),
    }
    s_titles, j_titles = set(s_sec), set(j_sec)
    title = {"changed": bool(jira_title is not None and spec_title is not None and not _same(spec_title, jira_title)),
             "old": spec_title, "new": jira_title}

    requirement_changed = bool(
        added or removed or changed or title["changed"]
        or sections["businessRequirement"]["changed"] or sections["outOfScope"]["changed"])
    agent_owned_changed = sections["legacyTrace"]["changed"] or sections["technicalNotes"]["changed"]
    other_sections = sorted((s_titles ^ j_titles) - {k for k in s_titles | j_titles
                                                   if k.startswith(("Business", "Acceptance", "Out of scope", "Legacy trace", "Technical notes"))})
    no_difference = not requirement_changed and not agent_owned_changed and not other_sections
    identical = c.sha256_text(spec_body) == c.sha256_text(jira_body) and (not title["changed"])
    return {
        "specSha256": c.sha256_text(spec_body),
        "jiraSha256": c.sha256_text(jira_body),
        "identical": identical,
        "noDifference": no_difference,
        "formattingOnly": no_difference and not identical,
        "requirementChanged": requirement_changed,
        "agentOwnedOnly": agent_owned_changed and not requirement_changed,
        "otherSectionsDiffer": other_sections,
        "title": title,
        "sections": sections,
        "acs": {"added": added, "removed": removed, "changed": changed, "unchanged": unchanged},
    }


def summary(d: dict) -> str:
    if d["noDifference"]:
        return "no difference" + (" (the text differs only in formatting)" if d["formattingOnly"] else "")
    a = d["acs"]
    parts = []
    if a["changed"] or a["added"] or a["removed"]:
        parts.append(f"{len(a['changed'])} changed, {len(a['added'])} added, {len(a['removed'])} removed AC(s): "
                     + ", ".join(x["id"] for x in a["changed"] + a["added"] + a["removed"]))
    if d["title"]["changed"]:
        parts.append("title changed")
    for k, label in (("businessRequirement", "business requirement"), ("outOfScope", "out of scope")):
        if d["sections"][k]["changed"]:
            parts.append(f"{label} changed")
    if d["agentOwnedOnly"] or (d["sections"]["legacyTrace"]["changed"] or d["sections"]["technicalNotes"]["changed"]):
        parts.append("agent-owned section(s) changed (not a requirement)")
    if d["otherSectionsDiffer"]:
        parts.append("other sections differ: " + ", ".join(d["otherSectionsDiffer"]))
    return "; ".join(parts) or "differences found"


def _q(text: str) -> str:
    return text.replace("\n", " ").strip() or "(empty)"


def render_markdown(key: str, d: dict) -> str:
    out = [f"### {key}: {summary(d)}", ""]
    out.append(f"Baseline sha256 `{d['specSha256'][:16]}...`, Jira sha256 `{d['jiraSha256'][:16]}...`"
               + (", identical." if d["identical"] else "."))
    if d["noDifference"]:
        return "\n".join(out) + "\n"
    out.append("")
    if d["title"]["changed"]:
        out += [f"- **Title:** `{d['title']['old']}` -> `{d['title']['new']}`"]
    a = d["acs"]
    for ch in a["changed"]:
        kind = "behaviour" if ch["behaviour"] else "text/basis only"
        out.append(f"- **{ch['id']} changed** ({kind}): {ch['title']}")
        for f, v in ch["fields"].items():
            out.append(f"  - {f}: `{_q(v['old'])}` -> `{_q(v['new'])}`")
    for ad in a["added"]:
        out.append(f"- **{ad['id']} added:** {ad['title']}")
        for f in ("given", "when", "then", "basis"):
            out.append(f"  - {f}: `{_q(ad[f])}`")
    for rm in a["removed"]:
        out.append(f"- **{rm['id']} removed:** {rm['title']}")
        for f in ("given", "when", "then", "basis"):
            out.append(f"  - was {f}: `{_q(rm[f])}`")
    for k, label in (("businessRequirement", "Business requirement"), ("outOfScope", "Out of scope")):
        s = d["sections"][k]
        if s["changed"]:
            out.append(f"- **{label} changed:** `{_q(s['old'])}` -> `{_q(s['new'])}`")
    for k, label in (("legacyTrace", "Legacy trace"), ("technicalNotes", "Technical notes")):
        if d["sections"][k]["changed"]:
            out.append(f"- {label} (agent-owned, not a requirement) differs in Jira: kept as reported, not acted on.")
    if d["otherSectionsDiffer"]:
        out.append("- Other sections that exist on one side only: " + ", ".join(d["otherSectionsDiffer"]))
    return "\n".join(out) + "\n"


def load_snapshot(path) -> dict:
    snap = c.load_json(path)
    if "description" not in snap:
        raise ValueError(f"{path}: the snapshot has no 'description'")
    return snap


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("spec")
    ap.add_argument("snapshot")
    ap.add_argument("--json")
    ap.add_argument("--markdown")
    a = ap.parse_args(argv)
    try:
        spec = c.read_spec(a.spec)
        snap = load_snapshot(a.snapshot)
    except (OSError, ValueError, json.JSONDecodeError) as e:
        print(f"cannot read the input: {e}", file=sys.stderr)
        return 2
    d = diff(spec.body, snap["description"], spec.front.get("title", "").strip('"'), snap.get("summary"))
    d.update(key=spec.key, specVersion=spec.version, specJiraUpdated=spec.front.get("jiraUpdated"),
             jiraUpdated=snap.get("updated"), jiraStatus=snap.get("status"))
    if a.json:
        c.write_json(a.json, d)
    md = render_markdown(spec.key, d)
    if a.markdown:
        open(a.markdown, "w", encoding="utf-8", newline="\n").write(md)
    print(md)
    return 0 if d["noDifference"] else 10


if __name__ == "__main__":
    sys.exit(main())
