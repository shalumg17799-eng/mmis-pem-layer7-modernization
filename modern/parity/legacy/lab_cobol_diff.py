"""List the differences between the COBOL compiled in the Hercules lab and source-code/BATCH/*.cbl (AC-8).

The lab sources are read from the SOURCE listings of the lab build job PRVBLD01 (job 154, 2026-11-19) in the
printer spool: five-digit statement number, then the card image shifted by eight columns. Nothing under
source-code/ is changed; the diffs are written to parity/golden/lab-cobol-diff/.

    python lab_cobol_diff.py <spool file> <output dir>
"""
import difflib
import pathlib
import re
import sys

import herc_lib as H

ROOT = pathlib.Path(__file__).resolve().parents[2]
SRC = ROOT.parent / "source-code" / "BATCH"
PROGS = ["PRVDEAD1", "PRVDIR01", "PRVENR01", "PRVREV01", "PRVRSK01", "PRVSAN01", "PRVSCR01", "PRVTMS01"]
LAB_ONLY = ["PRVDUMP1", "PRVLDAP1", "PRVNTGEN", "PRVNTRST", "PRVNTSUS", "PRVNTTRM", "PRVSEED1", "PRVUNLD1"]


def lab_sources(spool_text, jobno=154):
    sec = H.job_section(spool_text, jobno)
    progs, cur = {}, None
    for l in sec:
        m = re.match(r"^\d{5}\s+(?:\S+\s+)?PROGRAM-ID\.\s+(\w+)\.", l) or re.match(r"^\d{5}\s+PROGRAM-ID\.\s+(\w+)\.", l)
        if m:
            cur = m.group(1)
            progs.setdefault(cur, {})
        if cur is None:
            continue
        m = re.match(r"^(\d{5}) (.*)$", l)
        if m:
            progs[cur][int(m.group(1))] = l[8:80].rstrip()
    # the first lines (before PROGRAM-ID) of each listing are comments: pick them up by line number order
    return progs


def lab_listing_blocks(spool_text, jobno=154):
    """Return {PROGRAM-ID: [card images]} using the listing blocks that start at statement 00001."""
    sec = H.job_section(spool_text, jobno)
    blocks, cur = [], None
    for l in sec:
        m = re.match(r"^(\d{5}) ", l)
        if not m:
            continue
        n = int(m.group(1))
        if n == 1:
            cur = []
            blocks.append(cur)
        if cur is not None:
            cur.append((n, l[8:80].rstrip()))
    out = {}
    for b in blocks:
        text = "\n".join(t for _, t in b)
        m = re.search(r"PROGRAM-ID\.\s+(\w+)\.", text)
        if m and m.group(1) not in out:
            seen, lines = set(), []
            for n, t in b:
                if n not in seen:
                    seen.add(n)
                    lines.append(t)
            out[m.group(1)] = lines
    return out


def main():
    spool = pathlib.Path(sys.argv[1]).read_text(encoding="latin-1")
    out = pathlib.Path(sys.argv[2])
    out.mkdir(parents=True, exist_ok=True)
    blocks = lab_listing_blocks(spool)
    summary = []
    for p in PROGS:
        lab = blocks[p]
        orig = (SRC / (p + ".cbl")).read_bytes().decode("ascii").replace("\r\n", "\n").split("\n")
        orig = [o.rstrip() for o in orig]
        while orig and orig[-1] == "":
            orig.pop()
        past72 = [(i + 1, o) for i, o in enumerate(orig) if len(o) > 72]
        orig72 = [o[:72] for o in orig]
        sm = difflib.SequenceMatcher(None, orig72, lab, autojunk=False)
        added = removed = changed = 0
        for tag, i1, i2, j1, j2 in sm.get_opcodes():
            if tag == "replace":
                changed += max(i2 - i1, j2 - j1)
            elif tag == "delete":
                removed += i2 - i1
            elif tag == "insert":
                added += j2 - j1
        diff = list(difflib.unified_diff(orig72, lab, "source-code/BATCH/%s.cbl (cols 1-72)" % p,
                                         "lab %s (compiled, PRVBLD01 job 154)" % p, lineterm="", n=0))
        (out / (p + ".diff")).write_text("\n".join(diff) + "\n", newline="\n")
        lab_notes = [t.strip() for t in lab if re.search(r"\bLAB\b|PORT|NOT IN V2|NOT IN THE V2|REPLACES", t)]
        summary.append(dict(program=p, originalLines=len(orig), labLines=len(lab), added=added, removed=removed,
                            changed=changed, originalLinesPastColumn72=past72, labNotes=lab_notes))
    import json
    (out / "summary.json").write_text(json.dumps(summary, indent=2) + "\n", newline="\n")
    for s in summary:
        print(s["program"], "orig", s["originalLines"], "lab", s["labLines"], "+%d -%d ~%d" % (s["added"], s["removed"], s["changed"]),
              "past72:", [n for n, _ in s["originalLinesPastColumn72"]])
    print("lab-only programs found:", [p for p in LAB_ONLY if p in blocks])


if __name__ == "__main__":
    main()
