"""Build the JCL decks of the legacy parity runs (Hercules / TK5 lab, MVS 3.8j).

The decks are reconstructed from the JES2 JCL listings of the lab jobs of 2026-11-19 (jobs 154-183 in the
printer spool of the container), because the *-LAB.jcl stubs are in neither the container nor this repository.
Step names, program names, DD names and DCB attributes are the lab's. What differs, and why:

  * no USER/PASSWORD: the lab account's password is not available to the parity checker; an anonymous job may not
    create a permanent dataset (IEF197I), so EVERY dataset is temporary (&&name), the inputs arrive as in-stream
    cards (the lab loaders PRVSEED1 and PRVLDAP1, two cards per record) and the before/after captures are printed
    by IDCAMS PRINT DUMP in the same job;
  * dataset names: &&APPS, &&MSTR(PRIME) ... instead of MMPRV.PROV.APPS.D<date> and MMPRV.PROVIDER.MASTER.ISAM
    (the lab's own datasets are never touched);
  * STEP005 (scratch the day's outputs, for reruns) is dropped: temporary datasets need no scratching;
  * capture steps (names LOADnnn and CAPnnn) are added; they only read.
"""
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[2]
FW = ROOT / "testdata" / "fixed-width"

MSTR_DCB = ("//PRVMSTR  DD DSN=&&MSTR(PRIME),DISP=(NEW,PASS,DELETE),UNIT=SYSDA,\n"
            "//             SPACE=(CYL,(5),,CONTIG),\n"
            "//             DCB=(DSORG=IS,RECFM=FB,LRECL=130,BLKSIZE=1300,\n"
            "//             KEYLEN=10,RKP=0,OPTCD=Y,CYLOFL=2)\n")

# the SORT control cards exactly as the lab job 165 printed them (IER070I)
SORT_CARDS = [
    "  SORT FIELDS=(1,10,CH,A)            COL  1-10  NPI",
    "* LAB: LEGACY CARD  INCLUDE COND=(23,1,CH,EQ,C'U')  IS NOT SUPPORTED BY",
    "* THIS OS/360 SORT (IER005A).  NOTE ALSO THAT COL 23 IS INSIDE THE",
    "* TAXONOMY CODE - PRV-SCREEN-FLAG IS COL 34 (LATENT LEGACY DEFECT).",
    "  RECORD TYPE=F,LENGTH=(130)",
    "  END",
]


def read_records(path, n):
    recs = [r for r in pathlib.Path(path).read_bytes().decode("ascii").split("\n") if r]
    for r in recs:
        assert len(r) == n, (path, len(r))
    return recs


def seed_records():
    """The 16 seed providers, in ascending NPI order (the ISAM load needs ascending keys)."""
    return sorted(read_records(FW / "provider-master-seed.dat", 130), key=lambda r: r[:10])


def app_records(count=None):
    recs = read_records(FW / "appinput-baseline.dat", 200)
    for r in recs:
        assert r[130:] == " " * 70, "bytes 131-200 must be blank for the two-card loader"
    return recs[:count] if count else recs


def two_cards(recs):
    cards = []
    for r in recs:
        cards.append(r[:80])
        cards.append(r[80:130].ljust(80))
    return cards


def sancin_cards():
    return read_records(FW / "sancin-baseline.dat", 80)


def header(jobname, purpose, notes):
    s = "//%-8s JOB (MMIS,PARITY),'%s',\n" % (jobname, purpose)
    s += "//             CLASS=A,MSGCLASS=A,MSGLEVEL=(1,1),\n//             REGION=4096K\n"
    for n in notes:
        s += "//*  " + n + "\n"
    s += "//JOBLIB   DD DSN=MMPRV.PROD.LOADLIB,DISP=SHR\n"
    return s


def step(name, pgm, extra=""):
    return "//%-8s EXEC PGM=%s%s\n" % (name, pgm, extra)


def load_master(step_name="LOAD010"):
    return (step(step_name, "PRVSEED1", ",REGION=4096K") + "//SYSOUT   DD SYSOUT=A\n" + MSTR_DCB
            + "//SEEDIN   DD *\n" + "\n".join(two_cards(seed_records())) + "\n/*\n")


def unload(step_name, flat, cond=""):
    return (step(step_name, "PRVUNLD1", ",REGION=4096K" + cond) + "//SYSOUT   DD SYSOUT=A\n"
            + "//PRVMSTR  DD DSN=&&MSTR,DISP=(OLD,PASS)\n"
            + "//FLATOUT  DD DSN=&&%s,DISP=(NEW,PASS,DELETE),\n" % flat
            + "//            UNIT=SYSDA,SPACE=(TRK,(5,5)),\n"
            + "//            DCB=(RECFM=FB,LRECL=130,BLKSIZE=1300)\n")


def dump(step_name, names, cond=""):
    s = step(step_name, "IDCAMS", cond) + "//SYSPRINT DD SYSOUT=A\n"
    for i, n in enumerate(names, 1):
        s += "//DD%02d     DD DSN=&&%s,DISP=(OLD,PASS)\n" % (i, n)
    s += "//SYSIN    DD *\n"
    for i in range(1, len(names) + 1):
        s += " PRINT INFILE(DD%02d) DUMP\n" % i
    return s + "/*\n"


def deck_prvday01(apps, batch_id, tag):
    """PRVDAY01: STEP010 PRVENR01, STEP015 PRVUNLD1 (lab), STEP020 SORT, STEP030 PRVSCR01, STEP040 PRVRSK01."""
    c = ",COND=(0,LT,STEP010)"
    d = header("PRVDAY01", "DAILY ENROLLMENT", [
        "PARITY RUN " + tag + ": PRVDAY01 STEPS OF THE LAB DECK, TEMPORARY",
        "DATASETS ONLY (ANONYMOUS JOB), LOAD AND CAP STEPS ADDED"])
    d += load_master()
    d += (step("LOAD020", "PRVLDAP1", ",REGION=4096K") + "//SYSOUT   DD SYSOUT=A\n"
          + "//APPOUT   DD DSN=&&APPS,DISP=(NEW,PASS,DELETE),UNIT=SYSDA,\n"
          + "//             SPACE=(TRK,(5,5)),\n"
          + "//             DCB=(RECFM=FB,LRECL=200,BLKSIZE=6000)\n"
          + "//APPCARD  DD *\n" + "\n".join(two_cards(apps)) + "\n/*\n")
    d += unload("LOAD030", "BEFORE")
    d += dump("LOAD040", ["APPS", "BEFORE"])
    d += (step("STEP010", "PRVENR01", ",PARM='BATCH_ID=%s'" % batch_id)
          + "//STEPLIB  DD DSN=MMPRV.PROD.LOADLIB,DISP=SHR\n"
          + "//APPINPUT DD DSN=&&APPS,DISP=(OLD,PASS)\n"
          + "//PRVMSTR  DD DSN=&&MSTR,DISP=(OLD,PASS)\n"
          + "//APPERROR DD DSN=&&APPERR,DISP=(NEW,PASS,DELETE),\n"
          + "//            UNIT=SYSDA,SPACE=(CYL,(5,2)),\n"
          + "//            DCB=(RECFM=FB,LRECL=250,BLKSIZE=25000)\n"
          + "//SYSOUT   DD SYSOUT=*\n//SYSPRINT DD SYSOUT=*\n")
    d += unload("CAP010", "AFT010")
    d += dump("CAP011", ["APPERR", "AFT010"])
    d += (step("STEP015", "PRVUNLD1", c) + "//PRVMSTR  DD DSN=&&MSTR,DISP=(OLD,PASS)\n"
          + "//FLATOUT  DD DSN=&&MSTFLAT,DISP=(NEW,PASS,DELETE),\n"
          + "//            UNIT=SYSDA,SPACE=(CYL,(5,2)),\n"
          + "//            DCB=(RECFM=FB,LRECL=130,BLKSIZE=1300)\n"
          + "//SYSOUT   DD SYSOUT=*\n")
    d += (step("STEP020", "SORT", ",REGION=1024K" + c) + "//SORTLIB  DD DSN=SYS1.SORTLIB,DISP=SHR\n"
          + "//SORTIN   DD DSN=&&MSTFLAT,DISP=(OLD,DELETE)\n"
          + "//SORTOUT  DD DSN=&&PRVSORT,DISP=(NEW,PASS,DELETE),\n"
          + "//            UNIT=SYSDA,SPACE=(CYL,(20,5)),\n"
          + "//            DCB=(RECFM=FB,LRECL=130,BLKSIZE=1300)\n"
          + "//SORTWK01 DD UNIT=SYSDA,SPACE=(CYL,(5,2))\n"
          + "//SORTWK02 DD UNIT=SYSDA,SPACE=(CYL,(5,2))\n"
          + "//SORTWK03 DD UNIT=SYSDA,SPACE=(CYL,(5,2))\n"
          + "//SYSIN    DD *\n" + "\n".join(SORT_CARDS) + "\n/*\n"
          + "//SYSOUT   DD SYSOUT=*\n//SYSPRINT DD SYSOUT=*\n")
    d += (step("STEP030", "PRVSCR01", c) + "//PRVMSTR  DD DSN=&&MSTR,DISP=(OLD,PASS)\n"
          + "//SCRNOUT  DD DSN=&&SCRNOUT,DISP=(NEW,PASS,DELETE),\n"
          + "//            UNIT=SYSDA,SPACE=(CYL,(10,5)),\n"
          + "//            DCB=(RECFM=FB,LRECL=67,BLKSIZE=6700)\n"
          + "//SYSOUT   DD SYSOUT=*\n")
    d += unload("CAP030", "AFT030", c)
    d += (step("STEP040", "PRVRSK01", c) + "//PRVMSTR  DD DSN=&&MSTR,DISP=(OLD,PASS)\n"
          + "//SYSOUT   DD SYSOUT=*\n")
    d += unload("CAP040", "AFT040", c)
    d += step("STEP099", "IEFBR14", ",COND=(0,LT)") + "//SYSOUT   DD SYSOUT=*\n"
    d += dump("CAP099", ["SCRNOUT", "AFT030", "AFT040"], c)
    return d + "//\n"


def deck_prvmth01(tag):
    """PRVMTH01: STEP010 PRVREV01, STEP020 PRVTMS01, STEP030 IEBGENER (GDG +1 replaced by a temporary copy)."""
    d = header("PRVMTH01", "MONTH-END PROVIDER", [
        "PARITY RUN " + tag + ": PRVMTH01 STEPS OF THE LAB DECK, TEMPORARY",
        "DATASETS ONLY. STEP030 WRITES &&ARCHIVE, NOT THE GDG (BLOCKED)"])
    d += load_master()
    d += unload("LOAD030", "BEFORE")
    d += dump("LOAD040", ["BEFORE"])
    d += (step("STEP010", "PRVREV01") + "//PRVMSTR  DD DSN=&&MSTR,DISP=(OLD,PASS)\n"
          + "//REVALQ   DD DSN=&&REVALQ,DISP=(NEW,PASS,DELETE),\n"
          + "//            UNIT=SYSDA,SPACE=(CYL,(5,2)),\n"
          + "//            DCB=(RECFM=FB,LRECL=80,BLKSIZE=8000)\n"
          + "//SYSOUT   DD SYSOUT=*\n")
    d += (step("STEP020", "PRVTMS01") + "//PRVMSTR  DD DSN=&&MSTR,DISP=(OLD,PASS)\n"
          + "//TMSISOUT DD DSN=&&TMSIS,DISP=(NEW,PASS,DELETE),\n"
          + "//            UNIT=SYSDA,SPACE=(CYL,(20,5)),\n"
          + "//            DCB=(RECFM=FB,LRECL=120,BLKSIZE=12000)\n"
          + "//SYSOUT   DD SYSOUT=*\n")
    d += (step("STEP030", "IEBGENER") + "//SYSUT1   DD DSN=&&TMSIS,DISP=(OLD,PASS)\n"
          + "//SYSUT2   DD DSN=&&ARCHIVE,DISP=(NEW,PASS,DELETE),\n"
          + "//            UNIT=SYSDA,SPACE=(CYL,(2,1)),\n"
          + "//            DCB=(RECFM=FB,LRECL=120,BLKSIZE=12000)\n"
          + "//SYSIN    DD DUMMY\n//SYSPRINT DD SYSOUT=*\n//SYSOUT   DD SYSOUT=*\n")
    d += unload("CAP030", "AFT030")
    d += dump("CAP099", ["REVALQ", "TMSIS", "ARCHIVE", "AFT030"])
    return d + "//\n"


def deck_prvdir01(tag):
    """PRVDIR01 (ad-hoc lab deck): the directory extract of the master."""
    d = header("PRVDIR01", "PROVIDER DIRECTORY", [
        "PARITY RUN " + tag + ": PRVDIR01, TEMPORARY DATASETS ONLY"])
    d += load_master()
    d += unload("LOAD030", "BEFORE")
    d += dump("LOAD040", ["BEFORE"])
    d += (step("STEP010", "PRVDIR01") + "//PRVMSTR  DD DSN=&&MSTR,DISP=(OLD,PASS)\n"
          + "//DIROUT   DD DSN=&&DIROUT,DISP=(NEW,PASS,DELETE),\n"
          + "//            UNIT=SYSDA,SPACE=(CYL,(2,1)),\n"
          + "//            DCB=(RECFM=FB,LRECL=100,BLKSIZE=10000)\n"
          + "//SYSOUT   DD SYSOUT=*\n")
    d += unload("CAP030", "AFT030")
    d += dump("CAP099", ["DIROUT", "AFT030"])
    return d + "//\n"


def deck_prvsan01(tag):
    """PRVSAN01 (ad-hoc lab deck): sanctions applied to the master, SANCIN from cards."""
    d = header("PRVSAN01", "APPLY SANCTIONS", [
        "PARITY RUN " + tag + ": PRVSAN01, TEMPORARY DATASETS ONLY"])
    d += load_master()
    d += (step("LOAD020", "IEBGENER") + "//SYSPRINT DD SYSOUT=A\n//SYSIN    DD DUMMY\n"
          + "//SYSUT1   DD *\n" + "\n".join(sancin_cards()) + "\n/*\n"
          + "//SYSUT2   DD DSN=&&SANCIN,DISP=(NEW,PASS,DELETE),\n"
          + "//            UNIT=SYSDA,SPACE=(TRK,(1,1)),\n"
          + "//            DCB=(RECFM=FB,LRECL=80,BLKSIZE=3120)\n")
    d += unload("LOAD030", "BEFORE")
    d += dump("LOAD040", ["SANCIN", "BEFORE"])
    d += (step("STEP010", "PRVSAN01") + "//PRVMSTR  DD DSN=&&MSTR,DISP=(OLD,PASS)\n"
          + "//SANCIN   DD DSN=&&SANCIN,DISP=(OLD,PASS)\n"
          + "//SYSOUT   DD SYSOUT=*\n")
    d += unload("CAP030", "AFT030")
    d += dump("CAP099", ["AFT030"])
    return d + "//\n"


if __name__ == "__main__":
    import sys
    import herc_lib as H
    for name, deck in (("day-baseline", deck_prvday01(app_records(), "20261120", "baseline")),
                       ("day-valid", deck_prvday01(app_records(3), "20261120", "valid-only")),
                       ("mth", deck_prvmth01("seed")), ("dir", deck_prvdir01("seed")),
                       ("san", deck_prvsan01("sancin-baseline"))):
        bad = H.check_jcl_columns(deck)
        print(name, "cards:", len(H.split_lines(deck)), "cards past col 71:", len(bad))
        if "--print" in sys.argv:
            print(deck)
