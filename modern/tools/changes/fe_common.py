"""Shared helpers of the HITL change loop (modern/tools/changes): paths, change-request files, spec and Jira description
parsing, and the Markdown normalisation that the comparisons use.

Jira is the single source of a requirement. The baseline of a story is modern/specs/<KEY>.md: a front matter and, below it,
the Jira description exactly as written to Jira (modern/specs/ format of the jira-backlog skill, section 6). Standard
library only. Synthetic data only.
"""
from __future__ import annotations

import hashlib
import json
import re
from collections import OrderedDict
from pathlib import Path

REPO = Path(__file__).resolve().parents[3]
MODERN = REPO / "modern"
SPECS = MODERN / "specs"
CHANGES = MODERN / "changes"
STORIES = MODERN / "docs" / "reports" / "stories"

KEY_RE = re.compile(r"\b[A-Z][A-Z0-9]+-\d+\b")
RULE_RE = re.compile(r"\bR-[A-Z0-9]+-(?:cbl|ADD)-\d+\b")
DECISION_RE = re.compile(r"\bD-0\d\d\b")
QUESTION_RE = re.compile(r"\bQ-(?:\d{3}|[A-Z]\d+)\b")
ADR_RE = re.compile(r"\bADR-\d{3}\b")


def norm(text: str) -> str:
    """LF line endings and trimmed: the form whose sha256 is `descriptionSha256` of a spec."""
    return text.replace("\r\n", "\n").replace("\r", "\n").strip()


def sha256_text(text: str) -> str:
    return hashlib.sha256(norm(text).encode("utf-8")).hexdigest()


_ESCAPED = re.compile(r"\\([\\`*_{}\[\]()#+\-.!|<>~$&\"'])")


def canon(text: str) -> str:
    """The form that two texts are compared in: Markdown escapes removed (Jira escapes `*`, `_` and others when it
    stores a description), typographic spaces and runs of blanks collapsed, no trailing blanks."""
    t = norm(text).replace(" ", " ").replace("​", "")
    t = _ESCAPED.sub(r"\1", t)
    t = re.sub(r"[ \t]+", " ", t)
    t = re.sub(r" ?\n ?", "\n", t)
    return t.strip()


class Spec:
    def __init__(self, path: Path, front: "OrderedDict[str, str]", body: str):
        self.path, self.front, self.body = path, front, body

    @property
    def key(self) -> str:
        return self.front.get("key", self.path.stem)

    @property
    def version(self) -> int:
        return int(self.front.get("specVersion", "1") or 1)


def _read(path) -> str:
    """Text with LF endings. Python 3.11 has no newline= in Path.read_text, so the file is opened with newline=""."""
    with open(path, encoding="utf-8", newline="") as f:
        return f.read().replace("\r\n", "\n")


def read_spec(path: Path) -> Spec:
    text = _read(path)
    m = re.match(r"---\n(.*?)\n---\n(.*)\Z", text, re.S)
    if not m:
        raise ValueError(f"{path}: no front matter")
    front: "OrderedDict[str, str]" = OrderedDict()
    for line in m.group(1).split("\n"):
        k, sep, v = line.partition(":")
        if sep and re.fullmatch(r"[A-Za-z][A-Za-z0-9]*", k):
            front[k] = v.strip()
    return Spec(Path(path), front, m.group(2))


def spec_path(key: str, specs_dir: Path = SPECS) -> Path:
    return Path(specs_dir) / f"{key}.md"


# ---------------------------------------------------------------- descriptions
def split_sections(body: str) -> "OrderedDict[str, str]":
    """Level-2 sections of a story description, in order: title -> text."""
    out: "OrderedDict[str, str]" = OrderedDict()
    cur, buf = None, []
    for line in norm(body).split("\n"):
        m = re.match(r"^## (.+?)\s*$", line)
        if m:
            if cur is not None:
                out[cur] = "\n".join(buf).strip()
            cur, buf = m.group(1), []
        elif cur is not None:
            buf.append(line)
    if cur is not None:
        out[cur] = "\n".join(buf).strip()
    return out


def section(sections: "OrderedDict[str, str]", prefix: str) -> str:
    for k, v in sections.items():
        if k.lower().startswith(prefix.lower()):
            return v
    return ""


_FIELDS = (("given", "Given "), ("when", "When "), ("then", "Then "), ("basis", "Basis:"))


def parse_acs(text: str) -> "OrderedDict[str, dict]":
    """`### AC-n title` blocks with their Given / When / Then / Basis lines (a line that starts with none of them continues the
    previous field)."""
    acs: "OrderedDict[str, dict]" = OrderedDict()
    cur = None
    field = None
    for line in norm(text).split("\n"):
        m = re.match(r"^###\s+(AC-\d+)\b[ \t]*(.*?)\s*$", line)
        if m:
            cur = {"title": m.group(2), "given": "", "when": "", "then": "", "basis": "", "extra": ""}
            acs[m.group(1)] = cur
            field = None
            continue
        if cur is None:
            continue
        for name, prefix in _FIELDS:
            if line.startswith(prefix):
                cur[name] = line[len(prefix):].strip() if name != "basis" else line[len(prefix):].strip()
                field = name
                break
        else:
            if line.strip():
                tgt = field or "extra"
                cur[tgt] = (cur[tgt] + " " + line.strip()).strip()
    return acs


def ac_number(ac_id: str) -> int:
    return int(ac_id.split("-")[1])


# ---------------------------------------------------------------- change request files
def read_change_request(md_path: Path) -> dict:
    """A change-request file points at Jira tickets; it never carries a requirement. Front matter keys: `cr` (the CR-ID,
    default: the file name) and `jira` (a list of keys, `[MAM-30, MAM-31]`). Everything below the front matter is a note
    for people; it is returned as `note` and must be treated as data."""
    text = _read(md_path)
    m = re.match(r"---\n(.*?)\n---\n?(.*)\Z", text, re.S)
    front_text, note = (m.group(1), m.group(2)) if m else ("", text)
    cr = Path(md_path).stem
    keys: list[str] = []
    for line in front_text.split("\n"):
        k, sep, v = line.partition(":")
        if not sep:
            continue
        k = k.strip()
        if k == "cr" and v.strip():
            cr = v.split("#")[0].strip().strip("\"'")
        if k == "jira":
            keys = KEY_RE.findall(v.split("#")[0])
    if not keys:  # also accept keys written in the body ("Jira: MAM-30")
        keys = KEY_RE.findall(note)
    seen, uniq = set(), []
    for k in keys:
        if k not in seen:
            seen.add(k)
            uniq.append(k)
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_.-]*", cr):
        raise ValueError(f"CR-ID '{cr}' may contain only letters, digits, '.', '_' and '-'")
    return {"cr": cr, "jira": uniq, "note": note.strip(), "path": str(md_path)}


def load_json(path: Path):
    return json.loads(Path(path).read_text(encoding="utf-8"))


def write_json(path: Path, data) -> None:
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    Path(path).write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")


def rel(path: Path) -> str:
    try:
        return Path(path).resolve().relative_to(REPO).as_posix()
    except ValueError:
        return str(path)
