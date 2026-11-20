"""Helpers for the parity lane: talk to the Hercules / TK5 lab container.

Only docker commands that name the container "mmis-hercules" are used (exec, cp) and the
published card reader socket (localhost:3507 -> 3505). Nothing here stops, restarts or
reconfigures the container, and nothing touches the MVS system date.
"""
import re
import socket
import subprocess
import time

CONTAINER = "mmis-hercules"
READER = ("127.0.0.1", 3507)
LOG = "/app/mvs/log/3033.log"
PRT = "/app/mvs/prt/prt00e.txt"


def docker(*args, timeout=180):
    r = subprocess.run(["docker", *args], capture_output=True, timeout=timeout)
    if r.returncode != 0:
        raise RuntimeError("docker %s failed (%d): %s" % (" ".join(args), r.returncode, r.stderr.decode("latin-1", "replace")))
    return r.stdout


def log_size():
    return int(docker("exec", CONTAINER, "sh", "-c", "wc -c < " + LOG).strip())


def log_since(offset):
    return docker("exec", CONTAINER, "sh", "-c", "tail -c +%d %s" % (offset + 1, LOG)).decode("latin-1")


def prt_size():
    return int(docker("exec", CONTAINER, "sh", "-c", "wc -c < " + PRT).strip())


def fetch_prt(dest):
    docker("cp", CONTAINER + ":" + PRT, dest)


def mvs_date_time():
    """MVS follows the container clock (TZOFFSET +0000)."""
    return docker("exec", CONTAINER, "date", "-u", "+%Y-%m-%dT%H:%M:%SZ").decode().strip()


def split_lines(deck):
    lines = deck.replace("\r", "").split("\n")
    if lines and lines[-1] == "":
        lines.pop()
    return lines


def check_jcl_columns(deck):
    """JCL statements (// and /*) must not use columns 72-80 (continuation column and sequence field)."""
    bad = []
    for n, line in enumerate(split_lines(deck), 1):
        if (line.startswith("//") or line.startswith("/*")) and len(line.rstrip()) > 71:
            bad.append((n, len(line.rstrip()), line))
    return bad


def submit(deck):
    """Send a JCL deck (text, LF line ends, every line <= 80 columns) to the card reader socket."""
    lines = split_lines(deck)
    bad = check_jcl_columns(deck)
    if bad:
        raise ValueError("JCL cards past column 71: %r" % (bad,))
    for n, line in enumerate(lines, 1):
        if len(line) > 80:
            raise ValueError("card %d is %d columns: %r" % (n, len(line), line))
        if not line.isascii():
            raise ValueError("card %d has non-ASCII text" % n)
    payload = "".join(line.ljust(80) + "\n" for line in lines).encode("ascii")
    with socket.create_connection(READER, timeout=30) as s:
        s.sendall(payload)
        s.shutdown(socket.SHUT_WR)
    return len(lines)


def wait_for_job(name, log_offset, timeout=240, poll=3):
    """Wait until '$HASP250 <name> IS PURGED' (output printed) appears after log_offset.

    Returns (jobno, new_log_text). Raises TimeoutError with the log tail if it never appears.
    """
    t0 = time.time()
    text = ""
    while time.time() - t0 < timeout:
        text = log_since(log_offset)
        m = re.search(r"JOB\s+(\d+)\s+\$HASP250 %s\s+IS PURGED" % re.escape(name), text)
        if m:
            return int(m.group(1)), text
        time.sleep(poll)
    raise TimeoutError("job %s not purged after %ds; log tail:\n%s" % (name, timeout, text[-1500:]))


def job_section(prt_text, jobno):
    """Return the printed lines of one job (from its START banner to its END banner)."""
    lines = prt_text.split("\n")
    start = end = None
    for i, l in enumerate(lines):
        if re.search(r"START\s+JOB\s+%d\s" % jobno, l) and start is None:
            start = i
        if re.search(r"\sEND\s+JOB\s+%d\s" % jobno, l):
            end = i
    if start is None:
        raise LookupError("no START banner for job %d" % jobno)
    return lines[start:(end + 1) if end is not None else len(lines)]


def free_mb():
    r = subprocess.run(["powershell", "-NoProfile", "-Command",
                        "[int]((Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory/1024)"],
                       capture_output=True, timeout=60)
    return int(r.stdout.decode().strip())


def wait_memory(pause_below=1024, poll=60, log=print):
    """If free memory is under pause_below wait (poll seconds at a time) until it is back above it.

    Returns (seconds waited, free MB at the end).
    """
    waited = 0
    f = free_mb()
    while f < pause_below:
        log("free memory %d MB < %d MB, waiting %d s" % (f, pause_below, poll))
        time.sleep(poll)
        waited += poll
        f = free_mb()
    return waited, f
