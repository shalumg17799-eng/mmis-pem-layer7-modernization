"""Rebuild the bytes of datasets from IDCAMS PRINT ... DUMP output in the printer spool.

A dump looks like (one record per 'RECORD SEQUENCE NUMBER', 32 bytes per line, hex offsets):

    LISTING OF DATA SET -SYS26324.T110323.RA000.PARPRB02.FLAT
    RECORD SEQUENCE NUMBER - 1
    000000  F1F0F0F5 F9F1F2F7 ...   *1005912765...*

IDCAMS repeats the LISTING line on every new page, so a repeated header of the dataset that is still
open (no IDC0005I yet) continues it. The hex digits are the EBCDIC bytes themselves, so nothing is
translated: the result is the dataset content byte for byte. Every record is checked: offsets must be
contiguous, record numbers in order, and the count must equal the IDC0005I count.
"""
import re

LISTING = re.compile(r"LISTING OF DATA SET -(\S+)")
RECORD = re.compile(r"RECORD SEQUENCE NUMBER - (\d+)")
COUNT = re.compile(r"IDC0005I NUMBER OF RECORDS PROCESSED WAS (\d+)")
LINE = re.compile(r"^\s*([0-9A-F]{6})  ([0-9A-F ]+?)\s+\*")


def parse(lines):
    """Return {dataset qualifier: [record bytes, ...]} in print order.

    The key is the last qualifier of the temporary name (SYSyyddd.Thhmmss.RA000.<JOB>.<NAME> -> NAME).
    A dataset printed again later in the job gets the suffix #2, #3 ...
    """
    out = {}
    open_full = None   # full DSN of the listing that is still open
    key = None
    rec = None
    for raw in lines:
        line = raw.rstrip("\r")
        m = COUNT.search(line)
        if m and key is not None:
            if int(m.group(1)) != len(out[key]):
                raise ValueError("%s: IDC0005I says %s records, parsed %d" % (key, m.group(1), len(out[key])))
            open_full, key, rec = None, None, None
            continue
        m = LISTING.search(line)
        if m:
            full = m.group(1)
            if full == open_full:
                continue                      # page header of the open listing
            name = full.split(".")[-1]
            k, n = name, 1
            while k in out:
                n += 1
                k = "%s#%d" % (name, n)
            out[k] = []
            open_full, key, rec = full, k, None
            continue
        if key is None:
            continue
        m = RECORD.search(line)
        if m:
            rec = bytearray()
            out[key].append(rec)
            if int(m.group(1)) != len(out[key]):
                raise ValueError("record sequence %s out of order in %s" % (m.group(1), key))
            continue
        m = LINE.match(line)
        if m and rec is not None:
            off = int(m.group(1), 16)
            if off != len(rec):
                raise ValueError("dump offset %06X but %d bytes so far in record %d of %s" % (off, len(rec), len(out[key]), key))
            rec.extend(bytes.fromhex(re.sub(r"\s+", "", m.group(2))))
    return {k: [bytes(r) for r in v] for k, v in out.items()}


def lengths(records):
    return sorted(set(len(r) for r in records))
