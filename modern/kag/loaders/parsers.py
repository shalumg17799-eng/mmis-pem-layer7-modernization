"""Simple, explicit parsers for the PEM legacy estate (no third-party dependencies).

  * COBOL / copybooks: fixed format, columns 8-72 carry code, column 7 is the indicator.
  * JCL: logical statements (continuations joined), steps, DDs, IF/ENDIF, IDCAMS instream.
  * DB2 DDL: CREATE TABLE with columns.

Every function takes text and returns plain dicts/dataclasses, so each is unit-tested
in modern/kag/tests/. Line numbers are 1-based and refer to the original file.
"""
from __future__ import annotations

import re
from dataclasses import dataclass, field


# --------------------------------------------------------------------------- COBOL basics
def code_lines(text: str):
    """Yield (lineno, content) for code lines: columns 8-72, comments and blanks skipped."""
    for i, raw in enumerate(text.splitlines(), 1):
        if len(raw) < 8:
            continue
        if raw[6] in "*/":
            continue
        content = raw[7:72]
        if content.strip():
            yield i, content


def split_statements(lines):
    """Join (lineno, content) pairs into period-terminated statements.

    A period ends a statement only when outside a quoted literal and followed by
    whitespace or end of text (so MMPRV.PROVIDER does not split). Returns [(first_lineno, text)].
    """
    out, buf, first, quote = [], [], None, None
    for ln, content in lines:
        if first is None:
            first = ln
        i, n = 0, len(content)
        while i < n:
            ch = content[i]
            if quote:
                buf.append(ch)
                if ch == quote:
                    quote = None
            elif ch in "'\"":
                quote = ch
                buf.append(ch)
            elif ch == "." and (i + 1 == n or content[i + 1] == " "):
                out.append((first, " ".join("".join(buf).split())))
                buf, first = [], None
                i += 1
                while i < n and content[i] == " ":
                    i += 1
                if i < n and first is None:
                    first = ln
                continue
            else:
                buf.append(ch)
            i += 1
        buf.append(" ")
    tail = " ".join("".join(buf).split())
    if tail:
        out.append((first or 0, tail))
    return out


# --------------------------------------------------------------------------- data items
@dataclass
class DataItem:
    level: int
    name: str
    line: int
    pic: str | None = None
    usage: str | None = None
    redefines: str | None = None
    values: list = field(default_factory=list)  # 88-level conditions: "NAME=VALUE"
    children: list = field(default_factory=list)
    length: int = 0
    start: int = 0
    copy: str | None = None  # COPY member written inside this item


@dataclass
class Record:
    name: str
    line: int
    root: DataItem
    fd: str | None = None  # owning FD file name (FILE SECTION), if any
    declared_length: int | None = None  # RECORD CONTAINS n CHARACTERS of that FD
    section: str = "WORKING-STORAGE"
    copies: list = field(default_factory=list)  # COPY members used directly in the 01

    @property
    def length(self) -> int:
        return self.root.length

    def walk(self):
        """All items under the 01, 01 itself excluded, in source order."""
        def rec(n):
            for c in n.children:
                yield c
                yield from rec(c)
        yield from rec(self.root)


_PIC_TOKEN = re.compile(r"([XA9Z])(?:\((\d+)\))?")


def pic_length(pic: str, usage: str | None = None) -> int:
    """Storage bytes of an elementary item (DISPLAY by default; COMP/COMP-3 supported)."""
    s = pic.upper().replace("S", "").replace("V", "")
    digits = sum(int(m.group(2)) if m.group(2) else 1 for m in _PIC_TOKEN.finditer(s))
    u = (usage or "").upper()
    if u in ("COMP", "COMP-4", "COMP-5", "BINARY", "COMPUTATIONAL"):
        return 2 if digits <= 4 else 4 if digits <= 9 else 8
    if u in ("COMP-3", "COMPUTATIONAL-3"):
        return digits // 2 + 1
    return digits


_USAGE = re.compile(r"\b(COMP-3|COMP-4|COMP-5|COMP|BINARY|COMPUTATIONAL-3|COMPUTATIONAL)\b")


def _layout(rec: Record) -> None:
    """Compute length and 1-based start of every item (REDEFINES overlays, never adds)."""
    def calc(n: DataItem):
        for c in n.children:
            calc(c)
        n.length = pic_length(n.pic, n.usage) if n.pic else sum(c.length for c in n.children if not c.redefines)

    def assign(n: DataItem, start: int):
        n.start, off, seen = start, start, {}
        for c in n.children:
            cs = seen[c.redefines].start if c.redefines in seen else off
            assign(c, cs)
            seen[c.name] = c
            if not c.redefines:
                off += c.length

    calc(rec.root)
    assign(rec.root, 1)


def parse_data_division(code):
    """Parse data-description entries from (lineno, content) code lines.

    Returns a list of Record (every 01 level, FD association included).
    """
    records: list[Record] = []
    stack: list[DataItem] = []
    cur_fd, cur_declared, section = None, None, "WORKING-STORAGE"
    last: DataItem | None = None
    for ln, stmt in split_statements(code):
        up = stmt.upper()
        m = re.match(r"(FILE|WORKING-STORAGE|LINKAGE|LOCAL-STORAGE) SECTION$", up)
        if m:
            section = m.group(1)
            if section != "FILE":
                cur_fd = cur_declared = None
            continue
        if up.startswith(("DATA DIVISION", "ENVIRONMENT DIVISION", "IDENTIFICATION DIVISION")):
            continue
        m = re.match(r"(FD|SD)\s+(\S+)", up)
        if m:
            cur_fd = m.group(2)
            d = re.search(r"RECORD CONTAINS (\d+)", up)
            cur_declared = int(d.group(1)) if d else None
            continue
        if up.startswith("EXEC "):
            continue
        m = re.match(r"COPY\s+([A-Z0-9-]+)", up)
        if m:
            target = last or (records[-1].root if records else None)
            if target is not None:
                target.copy = m.group(1)
                if records:
                    records[-1].copies.append(m.group(1))
            continue
        m = re.match(r"(\d{1,2})(?:\s+(\S+))?(.*)$", stmt)
        if not m:
            continue
        level = int(m.group(1))
        name = m.group(2) or "FILLER"
        rest = ((" " + m.group(3)) if m.group(3) else "")
        if name.upper() in ("PIC", "PICTURE", "VALUE", "USAGE", "REDEFINES", "COMP"):
            rest, name = f" {name}{rest}", "FILLER"
        rest = rest.strip()
        if level == 88:
            v = re.search(r"VALUES?\s+(?:ARE\s+|IS\s+)?(.+)$", rest, re.I)
            if last is not None:
                last.values.append(f"{name}={v.group(1).strip().strip(chr(39)).strip(chr(34))}" if v else name)
            continue
        item = DataItem(level=level, name=name.upper(), line=ln)
        pm = re.search(r"\bPIC(?:TURE)?\s+(?:IS\s+)?(\S+)", rest, re.I)
        item.pic = pm.group(1) if pm else None
        um = _USAGE.search(rest.upper())
        item.usage = um.group(1) if um else None
        rm = re.search(r"\bREDEFINES\s+(\S+)", rest, re.I)
        item.redefines = rm.group(1).upper() if rm else None
        if level == 1:
            rec = Record(name=item.name, line=ln, root=item, fd=cur_fd if section == "FILE" else None,
                         declared_length=cur_declared if section == "FILE" else None, section=section)
            records.append(rec)
            stack = [item]
        elif stack:
            while len(stack) > 1 and stack[-1].level >= level:
                stack.pop()
            stack[-1].children.append(item)
            stack.append(item)
        last = item
    for r in records:
        _layout(r)
    return records


# --------------------------------------------------------------------------- COBOL programs
def parse_cobol(text: str) -> dict:
    """Parse one COBOL program. Returns program id, paragraphs, copies, calls, selects, CICS facts, records."""
    cl = list(code_lines(text))
    idx_data = next((i for i, (_, c) in enumerate(cl) if c.strip().upper().startswith("DATA DIVISION")), len(cl))
    idx_proc = next((i for i, (_, c) in enumerate(cl) if c.strip().upper().startswith("PROCEDURE DIVISION")), len(cl))
    env, data, proc = cl[:idx_data], cl[idx_data:idx_proc], cl[idx_proc:]

    pid = None
    for _, c in cl:
        m = re.search(r"PROGRAM-ID\.\s+([A-Z0-9-]+)", c, re.I)
        if m:
            pid = m.group(1).upper()
            break

    paragraphs = []
    starts = [(ln, c.strip()[:-1].upper()) for ln, c in proc[1:]
              if c[0] != " " and re.fullmatch(r"[A-Z0-9][A-Z0-9-]*\.", c.strip(), re.I)]
    last_code_line = proc[-1][0] if proc else 0
    for i, (ln, name) in enumerate(starts):
        nxt = starts[i + 1][0] if i + 1 < len(starts) else None
        end = max((l for l, _ in proc if l >= ln and (nxt is None or l < nxt)), default=ln)
        paragraphs.append({"name": name, "start": ln, "end": end if nxt else last_code_line})

    copies, calls = [], []
    for ln, c in data + proc:
        for m in re.finditer(r"\bCOPY\s+([A-Z0-9-]+)", c, re.I):
            copies.append({"name": m.group(1).upper(), "line": ln})
    proc_text = " ".join(c for _, c in proc)
    for ln, c in proc:
        for m in re.finditer(r"\bCALL\s+(?:'([^']+)'|\"([^\"]+)\"|([A-Z][A-Z0-9-]*))", c, re.I):
            literal = m.group(1) or m.group(2)
            calls.append({"target": (literal or m.group(3)).upper(), "dynamic": literal is None, "line": ln})

    env_text = " ".join(c for _, c in env)
    selects = [{"file": m.group(1).upper(), "assign": m.group(2).upper()}
               for m in re.finditer(r"SELECT\s+(\S+)\s+ASSIGN\s+TO\s+([A-Z0-9-]+)", env_text, re.I)]

    cics = {}
    for key, pat in (("transid", r"TRANSID\('([^']+)'\)"), ("map", r"\bMAP\('([^']+)'\)"),
                     ("mapset", r"MAPSET\('([^']+)'\)"), ("file", r"\bFILE\('([^']+)'\)")):
        m = re.search(pat, proc_text, re.I)
        if m:
            cics[key] = m.group(1).upper()

    return {
        "program_id": pid,
        "total_lines": len(text.splitlines()),
        "code_lines": len(cl),
        "paragraphs": paragraphs,
        "copies": copies,
        "calls": calls,
        "selects": selects,
        "cics": cics,
        "records": parse_data_division(data),
    }


def parse_copybook(text: str) -> dict:
    """Parse one copybook: its records (01 levels) and nested COPY members."""
    cl = list(code_lines(text))
    copies = [{"name": m.group(1).upper(), "line": ln} for ln, c in cl
              for m in re.finditer(r"\bCOPY\s+([A-Z0-9-]+)", c, re.I)]
    return {"records": parse_data_division(cl), "copies": copies, "total_lines": len(text.splitlines())}


# --------------------------------------------------------------------------- JCL
def jcl_statements(text: str):
    """Logical JCL statements: [(lineno, name, op, operands)], comments skipped, continuations joined.
    Instream data (after DD * ... /*) is returned separately as {step_dd: [lines]} via parse_jcl."""
    stmts, cur = [], None
    for i, raw in enumerate(text.splitlines(), 1):
        if raw.startswith("//*"):
            continue
        if raw.startswith("//"):
            body = raw[2:72].rstrip()
            if cur and cur["cont"] and body[:1] == " ":
                cur["operands"] += " " + body.strip()
                cur["cont"] = body.rstrip().endswith(",")
                continue
            m = re.match(r"(\S*)\s+(\S+)\s*(.*)$", body)
            if not m:
                continue
            cur = {"line": i, "name": m.group(1), "op": m.group(2).upper(), "operands": m.group(3).strip()}
            cur["cont"] = cur["operands"].endswith(",")
            stmts.append(cur)
    return stmts


def parse_jcl(text: str) -> dict:
    """Parse a JOB or a cataloged PROC. Returns kind, name, steps (with DDs, condition), IDCAMS defines."""
    stmts = jcl_statements(text)
    kind = "job"
    name = None
    steps, ifs = [], []
    cur = None
    for s in stmts:
        op, nm, ops = s["op"], s["name"], s["operands"]
        if op == "JOB":
            name = nm
        elif op == "PROC":
            kind, name = "proc", nm
        elif op == "IF":
            ifs.append(re.sub(r"\s+THEN\s*$", "", ops, flags=re.I).strip("() ").strip() or ops)
        elif op == "ENDIF":
            if ifs:
                ifs.pop()
        elif op == "EXEC":
            pm = re.search(r"\bPGM=([A-Z0-9#@$]+)", ops, re.I)
            cm = re.search(r"\bCOND=(\(.*?\)|\S+?)(?:,|$)", ops, re.I)
            first = ops.split(",")[0].strip()
            cur = {"name": nm, "seq": len(steps) + 1, "line": s["line"],
                   "program": pm.group(1).upper() if pm else None,
                   "proc": None if pm else first.upper(),
                   "condition": ("IF " + " AND ".join(ifs)) if ifs else (("COND=" + cm.group(1)) if cm else None),
                   "dds": []}
            steps.append(cur)
        elif op == "DD" and cur is not None:
            dsn = re.search(r"\bDSN=([^,\s]+)", s["operands"], re.I)
            cur["dds"].append({"dd": nm or None, "dsn": dsn.group(1) if dsn else None, "line": s["line"]})
    sysin = _instream(text)
    return {"kind": kind, "name": name, "steps": steps, "idcams": parse_idcams(sysin) if "IDCAMS" in text.upper() else []}


def _instream(text: str) -> str:
    out, on = [], False
    for raw in text.splitlines():
        if raw.startswith("/*"):
            on = False
        elif raw.startswith("//"):
            on = bool(re.search(r"\bDD\s+\*", raw))
        elif on:
            out.append(raw)
    return "\n".join(out)


def parse_idcams(sysin: str) -> list:
    """DEFINE CLUSTER / ALTERNATEINDEX / PATH commands -> [{kind, name, keys, key_offset, record_size, relate}]."""
    flat = re.sub(r"\s+-\s*\n", " ", sysin)
    flat = " ".join(flat.split())
    out = []
    for part in re.split(r"(?=\bDEFINE\s+(?:CLUSTER|ALTERNATEINDEX|PATH)\b)", flat):
        m = re.match(r"DEFINE\s+(CLUSTER|ALTERNATEINDEX|PATH)\s*\(\s*NAME\(([^)]+)\)", part)
        if not m:
            continue
        keys = re.search(r"KEYS\((\d+)\s+(\d+)\)", part)
        rec = re.search(r"RECORDSIZE\((\d+)\s+(\d+)\)", part)
        rel = re.search(r"RELATE\(([^)]+)\)|PATHENTRY\(([^)]+)\)", part)
        out.append({"kind": m.group(1), "name": m.group(2).strip(),
                    "key_length": int(keys.group(1)) if keys else None,
                    "key_offset": int(keys.group(2)) if keys else None,
                    "record_size": int(rec.group(2)) if rec else None,
                    "relate": (rel.group(1) or rel.group(2)).strip() if rel else None})
    return out


# --------------------------------------------------------------------------- DB2 DDL
def parse_ddl(text: str) -> list:
    """CREATE TABLE statements -> [{name, line, columns:[{name, type, position, line}]}]."""
    tables = []
    lines = text.splitlines()
    i = 0
    while i < len(lines):
        m = re.match(r"\s*CREATE\s+TABLE\s+(\S+)\s*\(", lines[i], re.I)
        if m:
            tbl = {"name": m.group(1).upper(), "line": i + 1, "columns": []}
            i += 1
            while i < len(lines) and not re.match(r"\s*\)", lines[i]):
                body = lines[i].split("--")[0].strip().rstrip(",").strip()
                if body and not re.match(r"(PRIMARY|FOREIGN|CONSTRAINT|UNIQUE)\b", body, re.I):
                    cm = re.match(r"(\S+)\s+([A-Z]+(?:\(\d+(?:,\s*\d+)?\))?)", body, re.I)
                    if cm:
                        tbl["columns"].append({"name": cm.group(1).upper(), "type": cm.group(2).upper().replace(" ", ""),
                                               "position": len(tbl["columns"]) + 1, "line": i + 1})
                i += 1
            tables.append(tbl)
        i += 1
    return tables
