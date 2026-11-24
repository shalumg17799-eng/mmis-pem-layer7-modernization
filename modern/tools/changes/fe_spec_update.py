"""Updates the spec copy `modern/specs/<KEY>.md` to the Jira description of a snapshot (HITL change loop, step /fe-approve).

  python modern/tools/changes/fe_spec_update.py <KEY> <modern/changes/<CR-ID>/jira/<KEY>.json>

The body becomes the Jira description (LF line endings, trimmed, one final newline), `specVersion` goes up by one,
`descriptionSha256` and `jiraUpdated` follow the snapshot and `title` follows the Jira summary. Every other front matter key is kept:
`builtCommit` and `testsSha256` are set afterwards by `modern/tools/trace/close_story.py`. A `jiraDescriptionDiffers` note is removed
(the spec equals Jira again). Refuses when the snapshot equals the baseline. It runs on the change branch only; it never writes Jira.
"""
from __future__ import annotations

import sys

sys.dont_write_bytecode = True
sys.path.insert(0, __import__("os").path.dirname(__import__("os").path.abspath(__file__)))  # so that `python -I` finds fe_common

import fe_common as c  # noqa: E402
import fe_diff  # noqa: E402


def update(spec_file, snap: dict):
    spec = c.read_spec(spec_file)
    d = fe_diff.diff(spec.body, snap["description"], spec.front.get("title", "").strip('"'), snap.get("summary"))
    if d["noDifference"]:
        raise ValueError(f"{spec.key}: the Jira description equals the baseline spec; nothing to update")
    body = c.norm(snap["description"]) + "\n"
    front = dict(spec.front)
    front["specVersion"] = str(spec.version + 1)
    front["descriptionSha256"] = c.sha256_text(body)
    if snap.get("updated"):
        front["jiraUpdated"] = snap["updated"]
    if snap.get("summary"):
        front["title"] = '"' + snap["summary"].replace('"', '\\"') + '"'
    front.pop("jiraDescriptionDiffers", None)
    lines = [f"{k}: {v}" if v != "" else f"{k}:" for k, v in front.items()]
    text = "---\n" + "\n".join(lines) + "\n---\n" + body
    with open(spec_file, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)
    return c.read_spec(spec_file)


def main(argv=None) -> int:
    a = list(sys.argv[1:] if argv is None else argv)
    if len(a) != 2:
        print(__doc__, file=sys.stderr)
        return 2
    key, snap_path = a
    try:
        s = update(c.spec_path(key), fe_diff.load_snapshot(snap_path))
    except (OSError, ValueError) as e:
        print(f"error: {e}", file=sys.stderr)
        return 2
    print(f"{key}: specVersion {s.version}, descriptionSha256 {s.front['descriptionSha256'][:16]}..., jiraUpdated {s.front.get('jiraUpdated')}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
