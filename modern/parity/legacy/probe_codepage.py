"""Parity lane step 1: code-page probe, IDCAMS PRINT DUMP format test and temporary-ISAM test.

The job runs without USER/PASSWORD (the lab account's password is not available to the
parity checker; an anonymous job may not create permanent datasets, IEF197I), so every
dataset is a temporary one (&&name) and everything is printed in the same job.

  STEP010  IEBGENER  cards -> &&PROBE (80 byte records, characters on which 037 and 1047 differ)
  STEP020  IDCAMS    PRINT DUMP of &&PROBE
  STEP030  PRVSEED1  two seed providers (2 cards each) -> temporary ISAM master &&MSTR(PRIME)
  STEP040  PRVUNLD1  &&MSTR -> flat &&FLAT (130 bytes)
  STEP050  IDCAMS    PRINT DUMP of &&FLAT

Run:  python probe_codepage.py <out-dir>
"""
import pathlib
import sys

import herc_lib as H

ROOT = pathlib.Path(__file__).resolve().parents[2]
SEED = ROOT / "testdata" / "fixed-width" / "provider-master-seed.dat"

CARDS = [
    "ABCDEFGHIJKLMNOPQRSTUVWXYZ 0123456789 :",
    "[ ] ^ ! | { } \\ ~ ` $ # @ _ < > - ' = + ; , . ? % & * ( )",
    "XPROBE [] ^ ! | ",
]

DECK = """//PARPRB02 JOB (MMIS,PARITY),'PARITY PROBE',
//             CLASS=A,MSGCLASS=A,MSGLEVEL=(1,1),
//             REGION=4096K
//*  PARITY LANE WORK JOB (NOT PART OF THE LEGACY ESTATE)
//*  CODE PAGE PROBE AND TEMPORARY ISAM TEST, TEMPORARY DATASETS ONLY
//JOBLIB   DD DSN=MMPRV.PROD.LOADLIB,DISP=SHR
//STEP010  EXEC PGM=IEBGENER
//SYSPRINT DD SYSOUT=A
//SYSIN    DD DUMMY
//SYSUT1   DD *
{cards}
/*
//SYSUT2   DD DSN=&&PROBE,DISP=(NEW,PASS,DELETE),
//            UNIT=SYSDA,SPACE=(TRK,(1,1)),
//            DCB=(RECFM=FB,LRECL=80,BLKSIZE=3120)
//STEP020  EXEC PGM=IDCAMS
//SYSPRINT DD SYSOUT=A
//DD01     DD DSN=&&PROBE,DISP=(OLD,DELETE)
//SYSIN    DD *
 PRINT INFILE(DD01) DUMP
/*
//STEP030  EXEC PGM=PRVSEED1,REGION=4096K
//SYSOUT   DD SYSOUT=A
//PRVMSTR  DD DSN=&&MSTR(PRIME),DISP=(NEW,PASS,DELETE),UNIT=SYSDA,
//             SPACE=(CYL,(1),,CONTIG),
//             DCB=(DSORG=IS,RECFM=FB,LRECL=130,BLKSIZE=1300,
//             KEYLEN=10,RKP=0,OPTCD=Y,CYLOFL=2)
//SEEDIN   DD *
{seed}
/*
//STEP040  EXEC PGM=PRVUNLD1,REGION=4096K
//SYSOUT   DD SYSOUT=A
//PRVMSTR  DD DSN=&&MSTR,DISP=(OLD,DELETE)
//FLATOUT  DD DSN=&&FLAT,DISP=(NEW,PASS,DELETE),
//            UNIT=SYSDA,SPACE=(TRK,(1,1)),
//            DCB=(RECFM=FB,LRECL=130,BLKSIZE=1300)
//STEP050  EXEC PGM=IDCAMS
//SYSPRINT DD SYSOUT=A
//DD01     DD DSN=&&FLAT,DISP=(OLD,DELETE)
//SYSIN    DD *
 PRINT INFILE(DD01) DUMP
/*
//
"""


def seed_cards(n=2):
    recs = sorted(l for l in SEED.read_bytes().decode("ascii").split("\n") if l)[:n]
    cards = []
    for r in recs:
        assert len(r) == 130
        cards.append(r[:80])
        cards.append(r[80:130].ljust(80))
    return recs, cards


if __name__ == "__main__":
    out = pathlib.Path(sys.argv[1])
    out.mkdir(parents=True, exist_ok=True)
    recs, cards = seed_cards()
    deck = DECK.format(cards="\n".join(CARDS), seed="\n".join(cards))
    (out / "probe02-deck.jcl").write_text(deck, newline="\n")
    (out / "probe02-seed-records.txt").write_text("\n".join(recs) + "\n", newline="\n")
    waited, free = H.wait_memory()
    print("memory ok (free %d MB, waited %d s)" % (free, waited))
    off = H.log_size()
    n = H.submit(deck)
    print("submitted %d cards at %s" % (n, H.mvs_date_time()))
    jobno, text = H.wait_for_job("PARPRB02", off)
    print("job number", jobno)
    (out / "probe02-log-lines.txt").write_text(text, newline="\n")
    H.fetch_prt(str(out / "prt00e-after-probe02.txt"))
