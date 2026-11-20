"""Run one legacy parity run on the Hercules / TK5 lab and write the golden files.

    python run_legacy.py <run> [--attempt N]
    run: day-baseline | day-valid | mth | dir | san

Output: modern/parity/golden/<job>/<run-id>/{inputs,before,after,logs}/ and manifest.json.
Datasets are captured by IDCAMS PRINT DUMP in the job itself and rebuilt byte for byte from the hex
(parse_dump.py); the BEFORE captures are proved equal to the loaded inputs (CP1047 encode).
Only the container "mmis-hercules" (docker exec / cp) and the card reader socket are used.
"""
import datetime
import hashlib
import json
import pathlib
import re
import subprocess
import sys

import build_decks as B
import herc_lib as H
import parse_dump as P

ROOT = pathlib.Path(__file__).resolve().parents[2]
GOLDEN = ROOT / "parity" / "golden"
PY = ROOT / "kag" / ".venv" / "Scripts" / "python.exe"
CONVERTER = ROOT / "tools" / "data" / "ebcdic_to_ascii.py"
sys.path.insert(0, str(ROOT / "tools" / "data"))
import ebcdic_to_ascii as E  # noqa: E402

CODEPAGE = "1047"
MONTHS = {m: i for i, m in enumerate("JAN FEB MAR APR MAY JUN JUL AUG SEP OCT NOV DEC".split(), 1)}

# dump key -> (folder, file name, record length, what it is)
RUNS = {
    "day-baseline": dict(job="PRVDAY01", tag="baseline", maps={
        "APPS": ("before", "appinput.ebcdic", 200, "APPINPUT as loaded (PRVLDAP1)"),
        "BEFORE": ("before", "provider-master.ebcdic", 130, "provider master as loaded (PRVSEED1, unloaded by PRVUNLD1)"),
        "APPERR": ("after", "apperror.ebcdic", 250, "APPERROR written by STEP010 PRVENR01"),
        "AFT010": ("after", "provider-master-after-step010.ebcdic", 130, "master after STEP010 PRVENR01"),
        "SCRNOUT": ("after", "scrnout.ebcdic", 67, "SCRNOUT written by STEP030 PRVSCR01"),
        "AFT030": ("after", "provider-master-after-step030.ebcdic", 130, "master after STEP030 PRVSCR01"),
        "AFT040": ("after", "provider-master-after-step040.ebcdic", 130, "master after STEP040 PRVRSK01 (final)")}),
    "day-valid": dict(job="PRVDAY01", tag="valid-only", maps=None),  # same maps as day-baseline
    "mth": dict(job="PRVMTH01", tag="seed", maps={
        "BEFORE": ("before", "provider-master.ebcdic", 130, "provider master as loaded"),
        "REVALQ": ("after", "revalq.ebcdic", 80, "REVALQ written by STEP010 PRVREV01"),
        "TMSIS": ("after", "tmsisout.ebcdic", 120, "TMSISOUT written by STEP020 PRVTMS01"),
        "ARCHIVE": ("after", "tmsis-archive-copy.ebcdic", 120, "STEP030 IEBGENER copy (temporary dataset; the GDG generation is BLOCKED)"),
        "AFT030": ("after", "provider-master-after.ebcdic", 130, "master after the job")}),
    "dir": dict(job="PRVDIR01", tag="seed", maps={
        "BEFORE": ("before", "provider-master.ebcdic", 130, "provider master as loaded"),
        "DIROUT": ("after", "dirout.ebcdic", 100, "DIROUT written by PRVDIR01"),
        "AFT030": ("after", "provider-master-after.ebcdic", 130, "master after the job")}),
    "san": dict(job="PRVSAN01", tag="sancin-baseline", maps={
        "SANCIN": ("before", "sancin.ebcdic", 80, "SANCIN as loaded (IEBGENER from cards)"),
        "BEFORE": ("before", "provider-master.ebcdic", 130, "provider master as loaded"),
        "AFT030": ("after", "provider-master-after.ebcdic", 130, "master after PRVSAN01")}),
}
RUNS["day-valid"]["maps"] = RUNS["day-baseline"]["maps"]


LAB_DIFFS = {
    "jcl": [
        "JOB card: lab removes NOTIFY=&SYSUID and REGION=0M and adds USER=HERC01,PASSWORD (lab JCL comment); THIS RUN has no USER/PASSWORD (see parityRunAdditions)",
        "PRVDAY01: IF (STEP010.RC = 0) THEN / ENDIF does not exist in 3.8j JCL; STEPs 015-040 carry COND=(0,LT,STEP010) (lab, same meaning)",
        "&DATE and &BATCH_DATE are undefined in the legacy decks (D-020); the lab fills them (hercjob.py --set); this run uses PARM BATCH_ID=<run date> and temporary dataset names, so no date appears in a dataset name",
        "PRVMSTR: VSAM KSDS MMPRV.PROVIDER.MASTER.KSDS -> ISAM dataset, 130-byte records, 10-byte key at offset 0 (the 1972 COBOL cannot use VSAM; record length 130 not 121, D-007)",
        "PRVDAY01 STEP005 added by the lab for reruns (scratch the day's outputs); dropped in this run (temporary datasets)",
        "PRVDAY01 STEP015 PRVUNLD1 added by the lab: unloads the ISAM master to a flat file for the SORT (the OS/360 SORT and IDCAMS cannot open ISAM)",
        "PRVDAY01 STEP020 SORT: the legacy INCLUDE COND=(23,1,CH,EQ,C'U') card is not supported by the OS/360 SORT (IER005A) and its offset 23 is inside the taxonomy (flag is at 34); lab control cards: SORT FIELDS=(1,10,CH,A), RECORD TYPE=F,LENGTH=(130); SORTLIB, SORTWKnn, DCBs added; SORTOUT never read (D-009 DROP)",
        "PRVMTH01 STEP030: UNIT=TAPE,LABEL=(1,SL) -> UNIT=SYSDA, GDG MMPRV.TMSIS.ARCHIVE(+1) with a model DCB (lab); NOT reproducible in this run (BLOCKED, anonymous job)",
        "PRVSAN01 and PRVDIR01: the legacy estate has no JCL; the lab decks follow the DD names of the programs",
    ],
    "cobol": [
        "all programs are the lab port 'TK5 / OS ANS COBOL V2 LAB PORT' compiled by IKFCBL00 (job PRVBLD01); per-program line diffs against source-code/BATCH are in parity/golden/lab-cobol-diff/ (copybooks are flattened into the source, so the raw diffs are large)",
        "PRVENR01: VSAM KSDS -> ISAM, FILE STATUS -> INVALID KEY / AT END, EVALUATE / END-IF / SET TO TRUE / STRING rewritten, BATCH_ID taken from the JCL PARM instead of an environment variable; paragraph order and GO TO flow unchanged (lab header)",
        "PRVRSK01: EVALUATE / FUNCTION MOD / ACCEPT FROM DATE / reference modification rewritten for the V2 compiler; the lab states ALTER, flow and the 2-digit-year arithmetic are unchanged",
        "PRVREV01, PRVRSK01, PRVTMS01: ACCEPT ... FROM DATE YYYYMMDD / FROM DAY (not in V2) replaced by the MVS CURRENT-DATE register (MM/DD/YY, 2-digit year only): the business date is the MVS system date",
        "PRVSAN01: V2 only allows CALL 'literal', so the dynamic CALL WS-NOTICE-PGM is dispatched on the action code to CALL 'PRVNTTRM' / 'PRVNTSUS' / 'PRVNTRST' / 'PRVNTGEN'; the four PRVNT* programs never existed in the estate and exist in the lab as stubs that DISPLAY a line",
        "PRVDIR01, PRVREV01, PRVSCR01: the lab blanks the output record for every row (QSAM buffer slots)",
        "PRVTMS01 line 114 (MOVE WS-TODAY-JUL TO WS-T-PERIOD-YYDDD, 73 columns, identifier past column 72, D-021): the lab port re-spaces the statement so the identifier ends inside column 72; source-code/ is unchanged",
        "PRVSCR01: control flow unchanged, the four checks are still the stubs that return P (D-005)",
        "lab-only helper programs (not in the legacy estate): PRVSEED1 (master loader), PRVLDAP1 (application loader), PRVUNLD1 (master unloader), PRVDUMP1 (list), PRVNTTRM/SUS/RST/GEN (notice stubs)",
    ],
    "parityRunAdditions": [
        "no USER/PASSWORD on the job card: the lab account password is not available to the parity checker (a blank password was rejected: IEF722I INVALID PASSWORD GIVEN, job 185); an anonymous job cannot create a permanent dataset (IEF197I SYSTEM ERROR DURING ALLOCATION, jobs 186-191)",
        "therefore ONE job per run, every dataset temporary (&&name), inputs as in-stream cards through the lab loaders PRVSEED1 / PRVLDAP1 (two cards per record), outputs printed by IDCAMS PRINT DUMP in the same job; the lab's own MMPRV datasets were not touched",
        "added read-only steps LOADnnn (load + capture BEFORE) and CAPnnn (capture after steps); the lab steps keep their names, programs, DD names and DCBs",
        "the ISAM master is loaded in ascending NPI order (the lab loader needs it): inputs/provider-master-seed-sorted.dat is the seed file sorted by NPI, same 16 records",
    ],
}


def sha(b):
    return hashlib.sha256(b).hexdigest()


def build(run, today):
    batch = today.strftime("%Y%m%d")
    if run == "day-baseline":
        return B.deck_prvday01(B.app_records(), batch, "baseline"), {"appinput.dat": B.app_records()}
    if run == "day-valid":
        return B.deck_prvday01(B.app_records(3), batch, "valid-only"), {"appinput.dat": B.app_records(3)}
    if run == "mth":
        return B.deck_prvmth01("seed"), {}
    if run == "dir":
        return B.deck_prvdir01("seed"), {}
    if run == "san":
        return B.deck_prvsan01("sancin-baseline"), {"sancin.dat": B.sancin_cards()}
    raise SystemExit("unknown run " + run)


def step_results(section):
    rows, on = [], False
    for l in section:
        if "IEFACTRT" in l:
            on = True
            continue
        if on:
            if "IEF404I" in l or "IEF453I" in l or "$HASP395" in l or "IEF450I" in l:
                rows.append(l.strip())
                if "IEF404I" in l or "IEF453I" in l:
                    break
                continue
            t = l.split()
            if "JOB" in t and len(t) >= 6:
                rows.append(" ".join(t[3:]))
    return rows


def main():
    which = sys.argv[1]
    attempt = int(sys.argv[sys.argv.index("--attempt") + 1]) if "--attempt" in sys.argv else 1
    cfg = RUNS[which]
    now_utc = datetime.datetime.now(datetime.timezone.utc)
    deck, text_inputs = build(which, now_utc)
    scratch = pathlib.Path(sys.argv[sys.argv.index("--scratch") + 1]) if "--scratch" in sys.argv else pathlib.Path(".")
    scratch.mkdir(parents=True, exist_ok=True)
    if "--reuse-job" in sys.argv:
        # re-process the output of a job that already ran (no new submission, so no extra attempt)
        jobno = int(sys.argv[sys.argv.index("--reuse-job") + 1])
        t_submit = sys.argv[sys.argv.index("--submitted-utc") + 1]
        now_utc = datetime.datetime.strptime(t_submit, "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=datetime.timezone.utc)
        deck, text_inputs = build(which, now_utc)
        logtext = H.docker("exec", H.CONTAINER, "sh", "-c",
                           "grep -a -e 'JOB  %d ' -e 'device 3505' %s | tail -n 60" % (jobno, H.LOG)).decode("latin-1")
        prt_path = scratch / ("prt00e-%s-job%d.txt" % (which, jobno))
        print("reusing job", jobno, "from", prt_path)
    else:
        waited, free = H.wait_memory()
        print("memory ok: free %d MB, waited %d s" % (free, waited))
        off = H.log_size()
        t_submit = H.mvs_date_time()
        ncards = H.submit(deck)
        print("submitted %d cards at %s" % (ncards, t_submit))
        jobno, logtext = H.wait_for_job(cfg["job"], off, timeout=300)
        print("job", cfg["job"], "number", jobno)
        prt_path = scratch / ("prt00e-%s-job%d.txt" % (which, jobno))
        H.fetch_prt(str(prt_path))
    section = H.job_section(prt_path.read_text(encoding="latin-1"), jobno)
    rows = step_results(section)
    print("\n".join(rows))
    m = re.search(r"(\d\d) ([A-Z]{3}) (\d\d) JOB EXECUTION DATE", "\n".join(section))
    if not m:
        raise SystemExit("job date line not found; job output in " + str(prt_path))
    jdate = datetime.date(2000 + int(m.group(3)), MONTHS[m.group(2)], int(m.group(1)))
    run_id = "%s-%s" % (jdate.strftime("%Y%m%d"), cfg["tag"])
    base = GOLDEN / cfg["job"] / run_id
    for d in ("inputs", "before", "after", "logs"):
        (base / d).mkdir(parents=True, exist_ok=True)
    dumps = P.parse(section)
    failed = [r for r in rows if "JCL ERROR" in r or "IEF453I" in r]
    files, problems, not_produced = [], [], []

    # inputs (ASCII, LF) as loaded
    inputs = {}
    seed_sorted = B.seed_records()
    inputs["provider-master-seed-sorted.dat"] = seed_sorted if which != "day-valid" or True else None
    inputs.update(text_inputs)
    for name, recs in inputs.items():
        data = ("\n".join(recs) + "\n").encode("ascii")
        (base / "inputs" / name).write_bytes(data)
        files.append(dict(path="inputs/" + name, role="input as sent (ASCII, one record per line, LF)",
                          recordLength=len(recs[0]), records=len(recs), bytes=len(data), sha256=sha(data)))

    expect_loaded = {"BEFORE": [E.encode(r.encode("ascii"), CODEPAGE) for r in seed_sorted]}
    if "appinput.dat" in text_inputs:
        expect_loaded["APPS"] = [E.encode(r.encode("ascii"), CODEPAGE) for r in text_inputs["appinput.dat"]]
    if "sancin.dat" in text_inputs:
        expect_loaded["SANCIN"] = [E.encode(r.encode("ascii"), CODEPAGE) for r in text_inputs["sancin.dat"]]

    proofs = {}
    for key, (folder, fname, lrecl, what) in cfg["maps"].items():
        recs = dumps.get(key)
        if recs is None:
            producer = {"SCRNOUT": "STEP030", "AFT030": "CAP030", "AFT040": "CAP040"}.get(key)
            if producer and any(("%s *FLUSH*" % producer) in r or (producer + " ") in r and "*FLUSH*" in r for r in rows):
                not_produced.append(dict(dataset=fname, because="step %s was bypassed (COND=(0,LT,STEP010), STEP010 ended with RC=4): "
                                         "the legacy job never created it" % producer))
            else:
                problems.append("%s not printed (dataset not created or step bypassed)" % key)
            continue
        bad = [i + 1 for i, r in enumerate(recs) if len(r) != lrecl]
        if bad:
            problems.append("%s: records with a length other than %d: %s" % (key, lrecl, bad[:5]))
        data = b"".join(recs)
        (base / folder / fname).write_bytes(data)
        entry = dict(path="%s/%s" % (folder, fname), role=what, recordLength=lrecl, records=len(recs),
                     bytes=len(data), sha256=sha(data), codePage=CODEPAGE)
        if key in expect_loaded:
            ok = recs == expect_loaded[key]
            proofs[key] = ok
            entry["equalsLoadedInput"] = ok
            if not ok:
                problems.append("%s differs from what was loaded" % key)
        files.append(entry)

    # ASCII conversion next to every EBCDIC file (originals unchanged)
    for e in files:
        if not e["path"].endswith(".ebcdic"):
            continue
        src = base / e["path"]
        dst = base / (e["path"] + ".ascii")
        if e["records"] == 0:
            dst.write_bytes(b"")
            e["ascii"] = dict(path=e["path"] + ".ascii", sha256=sha(b""), note="empty file")
            continue
        r = subprocess.run([str(PY), str(CONVERTER), str(src), str(dst), "--codepage", CODEPAGE,
                            "--lrecl", str(e["recordLength"])], capture_output=True, text=True)
        if r.returncode == 0:
            e["ascii"] = dict(path=e["path"] + ".ascii", sha256=sha(dst.read_bytes()))
        else:
            e["ascii"] = dict(error=(r.stdout + r.stderr).strip())
            problems.append("converter refused %s: %s" % (e["path"], e["ascii"]["error"]))

    # logs
    (base / "logs" / ("submitted-%s.jcl" % cfg["job"])).write_text(deck, newline="\n")
    (base / "logs" / ("job%d-spool.txt" % jobno)).write_text("\n".join(section) + "\n", newline="\n", encoding="latin-1")
    (base / "logs" / "hercules-log-lines.txt").write_text(logtext, newline="\n", encoding="latin-1")
    sysout = [l.rstrip() for l in section if re.match(r"^(PRV[A-Z0-9]+: |IER0|IER1|IEB|IDC000)", l)]
    (base / "logs" / "program-sysout.txt").write_text("\n".join(sysout) + "\n", newline="\n")
    (base / "logs" / "step-return-codes.txt").write_text("\n".join(rows) + "\n", newline="\n")

    manifest = dict(
        job=cfg["job"], runId=run_id, attempt=attempt, mvsJobNumber=jobno,
        businessDate=jdate.isoformat(),
        businessDateNote="MVS system date of the run (CURRENT-DATE special register, no PARM or control card exists in the lab programs); "
                         "the frozen date 2026-01-15 could not be used because the MVS system date must not be changed",
        batchIdParm=now_utc.strftime("%Y%m%d") if cfg["job"] == "PRVDAY01" else None,
        submittedUtc=t_submit, codePage=CODEPAGE,
        codePageProof="card with [ ] ^ loaded through the reader came back as AD BD 5F (037 would give BA BB B0); "
                      "| came back as 6A (Hercules table; CP1047 has 4F) - the character does not occur in the data",
        method="IDCAMS PRINT INFILE(DDnn) DUMP printed in the job itself; bytes rebuilt from the hex (parse_dump.py); "
               "master captured through lab program PRVUNLD1 (IDCAMS cannot open ISAM)",
        user="none (anonymous job: the lab account password is not available; permanent datasets are refused)",
        stepReturnCodes=rows, files=files, notProducedByLegacy=not_produced, problems=problems,
        blocked=([dict(item="GDG archive generation MMPRV.TMSIS.ARCHIVE(+1) written by STEP030",
                       error="IEF197I SYSTEM ERROR DURING ALLOCATION for every named permanent dataset of an anonymous job "
                             "(PARDG1 job 187 NEW,CATLG; PARDG3 job 189 NEW,KEEP; PARDG4 job 190 and PARDG5 job 191 with VOL=SER); "
                             "IEF453I JOB FAILED - JCL ERROR",
                       substitute="STEP030 IEBGENER ran and wrote a temporary copy (after/tmsis-archive-copy.ebcdic); "
                                  "it is the same records as TMSISOUT, so the GDG content is known, only the catalog generation is not")]
                 if cfg["job"] == "PRVMTH01" else []),
        mvsDateNote="the golden business date 2026-01-15 needs the MVS system date to be changed (forbidden); the jobs ran on the real date",
        labDifferences=LAB_DIFFS,
        beforeEqualsLoaded=proofs,
        labJcl=dict(file="logs/submitted-%s.jcl" % cfg["job"], sha256=sha(deck.encode("ascii")),
                    derivedFrom="JES2 JCL listing of the lab job of 2026-11-19 in the printer spool "
                                "(the *-LAB.jcl stubs are not in the container or the repository)"),
    )
    (base / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", newline="\n")
    print("\nwritten", base)
    print("problems:", problems or "none")
    if failed:
        print("JOB FAILED:", failed)
        sys.exit(2)


if __name__ == "__main__":
    main()
