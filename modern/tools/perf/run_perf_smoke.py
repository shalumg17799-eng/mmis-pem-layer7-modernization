#!/usr/bin/env python3
"""MAM-88 performance smoke: ONE command that fills the pem stack with 10,000 synthetic providers, measures it against the
targets of modern/docs/architecture/nfr.md (Performance), writes the report, and puts the stack back to the seed state.

    modern/kag/.venv/Scripts/python.exe modern/tools/perf/run_perf_smoke.py --stack-lock-held       (from the repository root)
    modern/kag/.venv/Scripts/python.exe modern/tools/perf/run_perf_smoke.py --dry-run               (prints every step and command)
    modern/kag/.venv/Scripts/python.exe modern/tools/perf/run_perf_smoke.py --render-report out/perf-result.json   (report only)

THE STACK LOCK. This runner does NOT take the shared stack lock (scratchpad stack.lock, PEM_STACK_LOCK); the caller starts it
while it holds the lock, because the run reseeds the stack twice, loads 10,000 providers, puts 20 users and two batch jobs on it
and would break every journey, test or deploy that uses the stack at the same time. To make a start by accident impossible the
real run only starts with --stack-lock-held; without it the runner prints the plan (as --dry-run does) and exits with 2.

WHAT IT DOES, in this order (a time stamp per step; free memory is checked before every heavy step and the run waits in 60 s steps
while it is under 1024 MB, as modern/parity/modern/run_modern.py does, whose Runner class is reused for the seed, the memory gate and the
transport):
  preflight   containers healthy, k6 image present (pulled by its digest when missing), tokens work, does the service honour afterNpi
  (a) seed    bash infra/scripts/seed.sh -> exactly 16 providers
  (b) load    tools/perf/load_perf_dataset.py (generate 10,000, check offline, load provider_db.provider and
              revalidation_db.tracked_provider), then AC-1: 130-byte file of 10,000 records with valid synthetic NPIs and fake
              names, GET /api/v1/providers total >= 10,000, a walk of the loaded NPI block through the API (afterNpi keyset when the
              service honours it, else offset paging) returning exactly the generated NPIs; writes out/npis.json for k6
  (c) AC-2    k6 (grafana/k6 pinned by version and digest, compose service k6, profile perf, on the compose network, 20 users, 60 s each):
              search.js (GET /api/v1/providers) and detail.js (GET /api/v1/providers/{npi}); ANALYST token through the environment
  (d) AC-3    POST /api/v1/applications (PROVIDER token): 40 sequential then 40 with 5 in parallel, each timed; wait until the
              registered providers are screened; then ONE screening (POST /api/v1/screenings, ANALYST) timed from the POST to COMPLETED
  (e) AC-4    T-MSIS extract, then revalidation sweep, each timed from the POST to COMPLETED (ADMIN token). The extract runs FIRST so
              that its time is not disturbed by the re-screenings the sweep starts (one per due provider, by RevalidationDue events)
  (f) restore ALWAYS, also after a failure: wait until the re-screenings of the sweep are finished, bash infra/scripts/seed.sh,
              check 16 providers (API), provider and tracked_provider 16 rows with 0 perf rows, application and revalidation_case empty
It writes modern/tools/perf/out/perf-result.json (git-ignored) and modern/docs/reports/performance-report.md (AC-5): every target of
nfr.md with its measured value, MET / NOT MET, DEFAULT marked as nfr.md marks it, the projection note for the 90 s jobs, the machine
conditions. Nothing is tuned away: a missed target is reported as NOT MET and stays so.

WHAT IT CHANGES ON THE STACK. provider_db.provider (+10,000 rows, then the seed truncates it), revalidation_db.tracked_provider
(+10,000), then every table the seed resets (enrollment, screening, risk, sanctions, notification, directory, revalidation cases,
Mailpit inbox) through the runs it makes; creates 80 applications and their providers (PENDING, screened by events), screenings,
one sweep (about 1,700 cases on a late-2026 business date, one re-screening and one notice each) and one T-MSIS extract. Not reset
by the seed, so they REMAIN: tmsis_db (one extract_run, its extract_rows and one archive generation), the Spring Batch job tables
and the Kafka topics. A throwaway k6 container (--rm, no dependencies) is the only container it starts; no pem container is
stopped, restarted or recreated.

Secrets: passwords are read from infra/.env.local at run time, only to ask Keycloak for tokens; no password and no token is printed
or written (the ANALYST token reaches the k6 container through the environment of the docker compose process only).
"""
import sys

sys.dont_write_bytecode = True

import argparse
import concurrent.futures
import contextlib
import datetime as dt
import http.client
import json
import math
import os
import re
import shlex
import subprocess
import threading
import time
import urllib.parse
import urllib.request
from pathlib import Path

HERE = Path(__file__).resolve().parent
MODERN = HERE.parents[1]
REPO = MODERN.parent
sys.path[:0] = [str(HERE), str(MODERN / "parity" / "modern"), str(MODERN / "tools" / "data")]

import load_perf_dataset as loader  # noqa: E402  generate + check + load, and the row counts
import pemdata  # noqa: E402  NPI check digit, the exported PRVRECRD layout
import run_modern  # noqa: E402  Runner (memory gate, seed, transport hook), git_bash: reused, not copied

OUT = HERE / "out"                                   # git-ignored (.gitignore: modern/tools/perf/out/)
K6_DIR = HERE / "k6"
COMPOSE_FILE = MODERN / "infra" / "docker-compose.yml"
RESULT = OUT / "perf-result.json"
REPORT = MODERN / "docs" / "reports" / "performance-report.md"
NFR = MODERN / "docs" / "architecture" / "nfr.md"

GATEWAY_URL = os.environ.get("PEM_GATEWAY_URL", "http://127.0.0.1:8080")   # 127.0.0.1, not localhost: no IPv6 detour on Windows
KEYCLOAK_URL = os.environ.get("PEM_KEYCLOAK_URL", "http://127.0.0.1:8180")
USERS = {"ADMIN": ("admin1", "KC_ADMIN_PASSWORD"), "ANALYST": ("analyst1", "KC_ANALYST_PASSWORD"),
         "PROVIDER": ("provider1", "KC_PROVIDER_PASSWORD")}
CONTAINERS = ["pem-pem-apps-1", "pem-postgres-1", "pem-keycloak-1", "pem-wiremock-1", "pem-kafka-1", "pem-mailpit-1"]
PERF_FIRST, PERF_LAST = loader.NPI_BLOCK
SUBMIT_NPI_PREFIX = "1008"        # the applications of AC-3: Luhn-valid NPIs 1008nnnnnn, outside the perf block and every other block
SEED_PROVIDERS = 16

# The targets of nfr.md "Performance". `nfr_row` must be the first cell of a row of that table (checked at run time).
TARGETS = [
    {"id": "search", "ac": "AC-2", "nfr_row": "Provider search / detail (p95)", "limit": 500, "unit": "ms",
     "name": "Provider search, GET /api/v1/providers: p95 at 20 concurrent users"},
    {"id": "detail", "ac": "AC-2", "nfr_row": "Provider search / detail (p95)", "limit": 500, "unit": "ms",
     "name": "Provider detail, GET /api/v1/providers/{npi}: p95 at 20 concurrent users"},
    {"id": "submit", "ac": "AC-3", "nfr_row": "Application submit (p95)", "limit": 1000, "unit": "ms",
     "name": "Application submit, POST /api/v1/applications: p95"},
    {"id": "screening", "ac": "AC-3", "nfr_row": "Screening of one provider (mocked sources)", "limit": 5000, "unit": "ms",
     "name": "Screening of one provider against the mocked sources, POST /api/v1/screenings to COMPLETED"},
    {"id": "tmsis", "ac": "AC-4", "nfr_row": "Monthly T-MSIS extract / revalidation sweep", "limit": 90, "unit": "s",
     "name": "T-MSIS extract of 10,000 providers, POST to COMPLETED (projection of 15 min for 100,000)"},
    {"id": "sweep", "ac": "AC-4", "nfr_row": "Monthly T-MSIS extract / revalidation sweep", "limit": 90, "unit": "s",
     "name": "Revalidation sweep of 10,000 providers, POST to COMPLETED (projection of 15 min for 100,000)"},
]
NFR_TEXT_CHECKS = ["500 ms", "20 concurrent users", "under 1 s", "under 5 s", "100,000 providers in under 15 minutes"]


# ---------------------------------------------------------------------------------------------------------------------------
# small pure helpers (unit-tested without a stack)
# ---------------------------------------------------------------------------------------------------------------------------

def utc_now() -> str:
    return dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def clock() -> str:
    return dt.datetime.now(dt.timezone.utc).strftime("%H:%M:%S")


def pctl(values, p):
    """Nearest-rank percentile (ceil(p/100 * n)-th smallest); None for no values."""
    if not values:
        return None
    s = sorted(values)
    return s[max(1, math.ceil(p / 100 * len(s))) - 1]


def stats_ms(seconds):
    """Summary of a list of durations in seconds, in milliseconds."""
    if not seconds:
        return {"n": 0}
    ms = [x * 1000 for x in seconds]
    return {"n": len(ms), "minMs": round(min(ms), 1), "avgMs": round(sum(ms) / len(ms), 1), "p50Ms": round(pctl(ms, 50), 1),
            "p90Ms": round(pctl(ms, 90), 1), "p95Ms": round(pctl(ms, 95), 1), "p99Ms": round(pctl(ms, 99), 1),
            "maxMs": round(max(ms), 1)}


def submit_npi(i: int) -> str:
    """The i-th (from 1) application NPI: 1008 + 5 digits + the NPI check digit (Luhn, prefix 80840)."""
    first9 = f"{SUBMIT_NPI_PREFIX}{i:05d}"
    return first9 + pemdata.npi_check_digit(first9)


def application_body(i: int, owner_npi: str) -> dict:
    """A valid application of a GROUP (type GR): a provider under the PROVIDER role may only apply for an individual (IN) under its
    own NPI (Q-095), but groups and facilities are not restricted, so 80 distinct valid applications need a GR. Synthetic names."""
    return {"npi": submit_npi(i), "medicaidId": f"8{i:08d}", "providerType": "GR", "taxonomy": "193200000X",
            "organization": {"legalName": f"SAMPLE PERF APPLICANT GROUP {i:05d}", "dbaName": f"SAMPLE PERF APPL DBA {i:05d}",
                             "ownerNpi": owner_npi}}


def read_env_value(name: str) -> str:
    """One value of infra/.env.local, read at the moment it is needed; never printed, never stored by this runner."""
    with open(MODERN / "infra" / ".env.local", encoding="utf-8") as f:
        for line in f:
            if line.startswith(name + "="):
                return line.split("=", 1)[1].rstrip("\r\n")
    raise SystemExit(f"{name} is missing in modern/infra/.env.local")


def parse_iso(text):
    return dt.datetime.fromisoformat(text.replace("Z", "+00:00")) if text else None


def server_seconds(run) -> float | None:
    """endedAt - startedAt of a BatchRun, in seconds (the service's own view of the run)."""
    try:
        a, b = parse_iso(run.get("startedAt")), parse_iso(run.get("endedAt"))
        return round((b - a).total_seconds(), 3) if a and b else None
    except (ValueError, AttributeError):
        return None


def read_nfr() -> dict:
    """The Performance section of nfr.md: heading, table header, rows {measure: target text} and whether it marks DEFAULT."""
    text = NFR.read_text(encoding="utf-8")
    m = re.search(r"^## (Performance[^\n]*)\n(.*?)(?=^## |\Z)", text, re.S | re.M)
    if not m:
        raise SystemExit("nfr.md has no 'Performance' section")
    heading, body = m.group(1), m.group(2)
    rows, header = {}, ""
    for line in body.splitlines():
        if not line.startswith("|"):
            continue
        cells = [c.strip() for c in line.strip().strip("|").split("|")]
        if set("".join(cells)) <= set("-: "):
            continue
        if not header:
            header = line
            continue
        rows[cells[0]] = cells[1] if len(cells) > 1 else ""
    section_default = "DEFAULT" in heading.upper() or "DEFAULT" in header.upper()
    return {"heading": heading, "header": header, "rows": rows, "sectionDefault": section_default,
            "missingText": [t for t in NFR_TEXT_CHECKS if t not in text]}


def duration_seconds(text: str) -> int:
    """k6 duration text ('60s', '2m', '1h', '90') in seconds."""
    m = re.fullmatch(r"(\d+)([smh]?)", text.strip())
    if not m:
        raise SystemExit(f"cannot read the duration {text!r} (use 60s, 2m, 1h)")
    return int(m.group(1)) * {"": 1, "s": 1, "m": 60, "h": 3600}[m.group(2)]


def fmt_ms(v):
    return "n/a" if v is None else (f"{v:,.0f} ms" if v >= 100 else f"{v:,.1f} ms")


def fmt_s(v):
    return "n/a" if v is None else f"{v:,.1f} s"


# ---------------------------------------------------------------------------------------------------------------------------
# evaluation of the measurements against the targets, and the report
# ---------------------------------------------------------------------------------------------------------------------------

def evaluate(result: dict) -> list:
    """One entry per target: measured value, text and MET / NOT MET / NOT MEASURED (with why). Pure function of the result."""
    nfr = result.get("nfr") or {}
    out = []
    for t in TARGETS:
        row_default = nfr.get("sectionDefault", True) or "DEFAULT" in (nfr.get("rows", {}).get(t["nfr_row"], "")).upper()
        e = {"id": t["id"], "ac": t["ac"], "name": t["name"], "nfrRow": t["nfr_row"], "nfrTarget": nfr.get("rows", {}).get(t["nfr_row"]),
             "limit": t["limit"], "unit": t["unit"], "default": row_default, "status": "NOT MEASURED", "measured": None,
             "text": "", "reason": ""}
        try:
            if t["id"] in ("search", "detail"):
                k = (result.get("ac2") or {}).get(t["id"])
                if not k or k.get("error"):
                    e["reason"] = (k or {}).get("error") or "the k6 step did not run"
                else:
                    e["measured"] = k["p95Ms"]
                    bad = []
                    if k["p95Ms"] >= t["limit"]:
                        bad.append(f"p95 {fmt_ms(k['p95Ms'])} is not under {t['limit']} ms")
                    if k["failedRate"] >= 0.01:
                        bad.append(f"{k['failedRate'] * 100:.2f} % of the requests failed (limit 1 %)")
                    if k["checksRate"] <= 0.99:
                        bad.append(f"only {k['checksRate'] * 100:.2f} % of the checks passed (needs over 99 %)")
                    if not k["iterations"]:
                        bad.append("no request was made")
                    e["status"] = "NOT MET" if bad else "MET"
                    e["reason"] = "; ".join(bad)
                    e["text"] = (f"p95 {fmt_ms(k['p95Ms'])} (avg {fmt_ms(k['avgMs'])}, p99 {fmt_ms(k['p99Ms'])}, max {fmt_ms(k['maxMs'])}); "
                                 f"{k['requests']:,} requests, {k['requestsPerSecond']:.1f}/s, {k['vus']} users, {k['duration']}, "
                                 f"{k['failedRate'] * 100:.2f} % failed")
            elif t["id"] == "submit":
                s = (result.get("ac3") or {}).get("submit")
                if not s or s.get("error"):
                    e["reason"] = (s or {}).get("error") or "the submit step did not run"
                else:
                    seq, par = s["sequential"], s["concurrent"]
                    e["measured"] = max(seq.get("p95Ms") or 0, par.get("p95Ms") or 0)
                    bad = []
                    for label, ph in (("sequential", seq), ("concurrent", par)):
                        if ph.get("p95Ms") is None:
                            bad.append(f"no successful submit in the {label} phase")
                        elif ph["p95Ms"] >= t["limit"]:
                            bad.append(f"{label} p95 {fmt_ms(ph['p95Ms'])} is not under 1 s")
                    if s["failed"]:
                        bad.append(f"{s['failed']} of {s['total']} submits did not answer 201 VALIDATED")
                    e["status"] = "NOT MET" if bad else "MET"
                    e["reason"] = "; ".join(bad)
                    e["text"] = (f"p95 sequential {fmt_ms(seq.get('p95Ms'))} ({seq['n']} submits, 1 at a time), concurrent "
                                 f"{fmt_ms(par.get('p95Ms'))} ({par['n']} submits, {s['concurrency']} at a time); the worse is shown; "
                                 f"{s['total'] - s['failed']} of {s['total']} answered 201 VALIDATED")
            elif t["id"] == "screening":
                sc = (result.get("ac3") or {}).get("screening")
                if not sc or sc.get("error"):
                    e["reason"] = (sc or {}).get("error") or "the screening step did not run"
                else:
                    e["measured"] = sc["maxMs"]
                    bad = []
                    if sc["maxMs"] >= t["limit"]:
                        bad.append(f"{fmt_ms(sc['maxMs'])} is not under 5 s")
                    if sc["notCompleted"]:
                        bad.append(f"{sc['notCompleted']} screening(s) did not reach COMPLETED")
                    e["status"] = "NOT MET" if bad else "MET"
                    e["reason"] = "; ".join(bad)
                    e["text"] = (f"{fmt_ms(sc['maxMs'])} from the POST to COMPLETED ({sc['n']} screening(s) of one provider each, result "
                                 f"{', '.join(sc['results'])}; the service's own started-to-completed time "
                                 f"{', '.join(fmt_s(x) for x in sc['serverSeconds'])}; polled every {sc['pollSeconds']} s)")
            else:  # tmsis, sweep
                j = (result.get("ac4") or {}).get(t["id"])
                if not j or j.get("error"):
                    e["reason"] = (j or {}).get("error") or "the job step did not run"
                else:
                    e["measured"] = j["seconds"]
                    bad = []
                    if j["status"] != "COMPLETED":
                        bad.append(f"the run ended {j['status']}" + (f" ({j.get('error')})" if j.get("error") else ""))
                    if j["seconds"] >= t["limit"]:
                        bad.append(f"{fmt_s(j['seconds'])} is not under {t['limit']} s")
                    e["status"] = "NOT MET" if bad else "MET"
                    e["reason"] = "; ".join(bad)
                    e["text"] = (f"{fmt_s(j['seconds'])} from the POST to COMPLETED, business date {j['businessDate']} "
                                 f"(the service's own run time {fmt_s(j.get('serverSeconds'))}); counts {json.dumps(j.get('counts'))}")
        except (KeyError, TypeError) as ex:
            e["status"], e["reason"] = "NOT MEASURED", f"result incomplete: {ex!r}"
        out.append(e)
    return out


def render_report(result: dict) -> str:
    """The Markdown report (AC-5) from a result dict. ASCII only."""
    targets = result.get("targets") or evaluate(result)
    met = sum(t["status"] == "MET" for t in targets)
    notmet = [t for t in targets if t["status"] == "NOT MET"]
    notmeas = [t for t in targets if t["status"] == "NOT MEASURED"]
    m = result.get("machine", {})
    nfr = result.get("nfr", {})
    L = []
    a = L.append
    a("# Performance smoke report (MAM-88)")
    a("")
    a(f"Run `{result.get('runId', '?')}` of `modern/tools/perf/run_perf_smoke.py`, started {result.get('startedUtc', '?')}, finished "
      f"{result.get('finishedUtc', '?')} (UTC), git HEAD `{result.get('gitHead', '?')}` plus the uncommitted work tree at the time of the run, "
      f"business date of the jobs {result.get('params', {}).get('businessDate', '?')}.")
    a("")
    a("**Result: %d of %d targets MET, %d NOT MET, %d NOT MEASURED.** %s" % (
        met, len(targets), len(notmet), len(notmeas),
        "Every target below is a DEFAULT of nfr.md (a placeholder until the user gives volumes and targets), so MET and NOT MET are "
        "against placeholders, not against agreed service levels." if all(t["default"] for t in targets) else
        "Targets marked DEFAULT are placeholders until the user gives volumes and targets."))
    if result.get("fatal"):
        a("")
        a(f"**The run stopped early:** {result['fatal']}")
    a("")
    a("## Targets (AC-5)")
    a("")
    a("| # | Target | Limit | Measured | Result | nfr.md |")
    a("|---|---|---|---|---|---|")
    for i, t in enumerate(targets, 1):
        limit = f"under {t['limit']:,} {t['unit']}"
        a(f"| {i} | {t['name']} ({t['ac']}) | {limit} | {t['text'] or ('not measured: ' + t['reason'])} | **{t['status']}** | "
          f"{'DEFAULT' if t['default'] else 'agreed'} |")
    a("")
    a("The `nfr.md` rows behind them (section `%s`):" % nfr.get("heading", "Performance"))
    a("")
    for row, text in (nfr.get("rows") or {}).items():
        a(f"- {row}: {text}")
    if nfr.get("missingText"):
        a("")
        a("**nfr.md has changed since this runner was written:** the text " + ", ".join(f"`{x}`" for x in nfr["missingText"]) +
          " is no longer in it; the limits of the runner may be stale.")
    a("")
    a("## Targets not met (not tuned away)")
    a("")
    if not notmet and not notmeas:
        a("None.")
    for t in notmet:
        a(f"- **{t['name']}**: {t['reason']}. Not tuned and not retried to look better; a fix is a separate Defect story after the "
          "user agrees (out of scope of MAM-88).")
    for t in notmeas:
        a(f"- **{t['name']}**: NOT MEASURED, {t['reason']}.")
    a("")
    a("## The 90 s job target is a projection")
    a("")
    a("nfr.md states the job target for 100,000 providers in under 15 minutes (900 s, 9 ms per provider). The story scales it to the 10,000 "
      "providers of this smoke: **90 s**. That is a linear projection of a target, not a measurement at 100,000 providers (a 100,000-provider "
      "load is out of scope, QF-10 option B). A job that is fast at 10,000 providers can still be slow at 100,000 if its time is not "
      "linear in the number of providers, and a job that misses 90 s here does not by itself miss 15 minutes at 100,000 either.")
    a("")
    ac1 = result.get("ac1") or {}
    a("## Data set (AC-1)")
    a("")
    if ac1:
        n = lambda v: f"{v:,}" if isinstance(v, int) else str(v)  # noqa: E731
        a(f"- Generator: `generate.py perf`, seed 20260115, {n(ac1.get('expected'))} providers; file `provider-master-perf.dat` holds "
          f"{n(ac1.get('datRecords'))} records of {ac1.get('recordLength')} bytes ({n(ac1.get('datBytes'))} bytes).")
        a(f"- NPIs: {ac1.get('invalidNpi')} with an invalid check digit, {ac1.get('duplicateNpi')} duplicates, {ac1.get('reservedNpi')} in a reserved "
          f"range; names: {ac1.get('badNames')} that are not fake (individuals `TESTPROV PERFnnnnn`, organisations `SAMPLE PERF ...`).")
        a(f"- Mix: types {json.dumps(ac1.get('types'))}, statuses {json.dumps(ac1.get('statuses'))}.")
        a(f"- GET /api/v1/providers reported a total of {n(ac1.get('apiTotal'))} (16 seeded + the set; the story needs at least "
          f"{n(ac1.get('expected'))}); the walk of the NPI block through the API ({ac1.get('walkMode')}) returned {n(ac1.get('walkCount'))} "
          f"providers in {ac1.get('walkCalls')} calls ({ac1.get('walkSeconds')} s) and "
          f"{'equals' if ac1.get('walkEqualsFile') else 'DOES NOT EQUAL'} the generated NPIs.")
        a(f"- Keyset paging (`afterNpi`, contract 1.2.0): **{ac1.get('afterNpi', {}).get('verdict', 'not probed')}**"
          f"{' - the walk used offset paging instead' if ac1.get('afterNpi', {}).get('verdict', '').startswith('IGNORED') else ''}.")
        a(f"- AC-1: **{ac1.get('status', 'NOT MEASURED')}**" + (f" ({ac1.get('reason')})" if ac1.get("reason") else "") + ".")
    else:
        a("Not reached.")
    a("")
    a("## How it was measured")
    a("")
    p = result.get("params", {})
    a(f"- **Search and detail (AC-2):** k6 `{result.get('k6Image', '?')}` in a throwaway container on the compose network, calling the gateway "
      f"as `http://pem-apps:8080` (inside Docker: no Docker Desktop port forwarder in the path), {p.get('vus')} virtual users for "
      f"{p.get('duration')} each, think time {p.get('thinkMs')} ms (every user always has a request in flight, so 20 requests are concurrent for the whole "
      "run), ANALYST token, an NPI sample read back through the API. Search mixes 60 % list pages over the whole set (offset paging), 20 % status "
      "filters, 10 % NPI lookups, 5 % Medicaid-ID lookups, 5 % name searches; the target is the p95 of all of them together, the p95 per kind "
      "is listed below. A run only counts when under 1 % of the requests failed and over 99 % of the checks passed, so fast error "
      "answers cannot pass. No warm-up is discarded.")
    for script in ("search", "detail"):
        kinds = ((result.get("ac2") or {}).get(script) or {}).get("kinds") or {}
        if kinds:
            a(f"  - {script}.js p95 per kind: " + ", ".join(f"{k} {fmt_ms(v['p95Ms'])}" for k, v in kinds.items()))
    a(f"- **Submit and screening (AC-3):** a Python client on the host (`{GATEWAY_URL}`, through the published host port, on Docker Desktop its port forwarder; one keep-alive "
      f"connection per worker), nearest-rank percentiles. Submits: {p.get('submitsSequential')} one at a time, then {p.get('submitsConcurrent')} with "
      f"{p.get('submitConcurrency')} at a time, valid group applications under the PROVIDER role; the stricter of the two p95 values is the "
      "result. Screening: POST /api/v1/screenings of a perf individual (ANALYST), polled every 0.1 s until COMPLETED; the time is the wall time from "
      "just before the POST to the poll that saw COMPLETED (an upper bound within one poll interval).")
    a("- **Jobs (AC-4):** POST to the batch endpoint with an ADMIN token, then polled every 0.5 s until COMPLETED (the T-MSIS run through the list "
      "of runs, which is all its contract offers). Wall time of the client, from just before the POST; the service's own started-to-ended time is "
      "listed next to it. The T-MSIS extract ran first and alone; the sweep second (it starts one re-screening per due provider, which the runner "
      "waited for before it reseeded).")
    if result.get("jobs", {}).get("tmsisEarlierRuns"):
        a(f"- tmsis_db already held {result['jobs']['tmsisEarlierRuns']} extract run(s) for this business date (the seed does not reset it): the "
          "extract here is a re-run for that date.")
    a("")
    a("## Machine conditions")
    a("")
    a(f"- Free memory at the start: **{m.get('freeMemoryMbAtStart')} MB** of {m.get('totalMemoryMb')} MB; at the end {m.get('freeMemoryMbAtEnd')} MB. "
      f"The run waited {m.get('memoryWaitSeconds', 0)} s in total for free memory to be at least 1024 MB.")
    a(f"- CPU: {m.get('cpuLogical')} logical processors, load {m.get('cpuLoadPercentAtStart')} % at the start. Processes at the start: "
      f"{m.get('processesAtStart')}; at the end: {m.get('processesAtEnd')}. Running containers at the start: {m.get('runningContainersAtStart')} "
      "(Docker is shared with other projects).")
    a(f"- pem containers at the start: {m.get('pemStatsAtStart')}.")
    a("- Other builds and test runs may have run on this machine while the measurements ran (the runner cannot see them; the stack lock only "
      "keeps other users of the stack away). The pem services run in ONE JVM with a 512 MB heap and C1-only compilation (ADR-011), so these numbers "
      "are those of the local runtime, not of the later deployment.")
    a("")
    a("## Steps and timings")
    a("")
    a("| Step | Started (UTC) | Seconds | Status |")
    a("|---|---|---|---|")
    for s in result.get("steps", []):
        a(f"| {s['title']} | {s['startedUtc']} | {s.get('seconds')} | {s['status']}{' - ' + s['error'] if s.get('error') else ''} |")
    a("")
    rs = result.get("restore") or {}
    a("## State left behind")
    a("")
    a(f"- Restore of the seed state: **{rs.get('status', 'not run')}**. {rs.get('detail', '')}")
    if result.get("residue"):
        a(f"- Not reset by the seed, so left in place: {result['residue']}")
    a("")
    a("## Not verified")
    a("")
    for n in result.get("notVerified", []):
        a(f"- {n}")
    if result.get("problems"):
        a("")
        a("## Problems seen during the run")
        a("")
        for pr in result["problems"]:
            a(f"- {pr}")
    a("")
    return "\n".join(L).replace("\r\n", "\n")


NOT_VERIFIED = [
    "One run on one shared machine: no repetition, so no spread between runs; the numbers move with free memory, CPU and what else runs in Docker.",
    "The jobs are scaled to 10,000 providers; nothing here says how they behave at 100,000.",
    "Availability, failover and a 100,000-provider load are out of scope (MAM-88).",
    "Search and detail were measured from inside the compose network, submit, screening and the jobs from the host: the two vantage points differ by the Docker Desktop port forwarder.",
    "The mocked sources (WireMock) answer immediately for the clean path; real sources will be slower, so the screening time is a lower bound for production.",
]


# ---------------------------------------------------------------------------------------------------------------------------
# the transport: one HTTP client with a token cache, keep-alive per thread (so it can time requests), no secret ever printed
# ---------------------------------------------------------------------------------------------------------------------------

class Api:
    def __init__(self):
        u = urllib.parse.urlparse(GATEWAY_URL)
        self.host, self.port = u.hostname, u.port or 80
        self._tokens = {}
        self._lock = threading.Lock()
        self._local = threading.local()

    def token(self, role: str) -> str:
        with self._lock:
            tok, at = self._tokens.get(role, (None, 0))
            if tok and time.time() - at < 600:  # the realm's access tokens live 900 s
                return tok
            user, variable = USERS[role]
            password = read_env_value(variable)
            body = urllib.parse.urlencode({"grant_type": "password", "client_id": "pem-test", "username": user,
                                           "password": password}).encode()
            del password
            req = urllib.request.Request(KEYCLOAK_URL + "/realms/pem/protocol/openid-connect/token", data=body, method="POST")
            with urllib.request.urlopen(req, timeout=30) as r:
                tok = json.loads(r.read())["access_token"]
            self._tokens[role] = (tok, time.time())
            return tok

    def reset_connection(self):
        c = getattr(self._local, "c", None)
        if c is not None:
            c.close()
        self._local.c = None

    def request(self, role, method, path, payload=None, raw=None, headers=None):
        """(status, body bytes, seconds). A failed GET is retried once on a fresh connection; a POST never is (it may have arrived)."""
        h = {"Authorization": "Bearer " + self.token(role), "Accept": "application/json"}
        body = raw
        if payload is not None:
            body = json.dumps(payload).encode()
            h["Content-Type"] = "application/json"
        h.update(headers or {})
        for attempt in (1, 2):
            c = getattr(self._local, "c", None)
            if c is None:
                c = self._local.c = http.client.HTTPConnection(self.host, self.port, timeout=120)
            t0 = time.perf_counter()
            try:
                c.request(method, path, body=body, headers=h)
                r = c.getresponse()
                data = r.read()
                return r.status, data, time.perf_counter() - t0
            except (http.client.HTTPException, OSError):
                self.reset_connection()
                if method != "GET" or attempt == 2:
                    raise

    def json(self, role, method, path, payload=None):
        st, data, secs = self.request(role, method, path, payload)
        try:
            return st, (json.loads(data) if data else None), secs
        except ValueError:
            return st, {"raw": data[:300].decode("utf-8", "replace")}, secs


# ---------------------------------------------------------------------------------------------------------------------------
# the runner
# ---------------------------------------------------------------------------------------------------------------------------

class Perf(run_modern.Runner):
    """Reuses run_modern.Runner for the memory gate, the seed (and its check of 16 providers) and the log; its __init__ is NOT called
    (it creates the parity run folders), and its transport is replaced by Api so that every call goes through one token cache."""

    def __init__(self, args):
        self.args = args
        self.dry = bool(args.dry_run)
        self.api = Api()
        self.log_lines, self.problems, self.runs, self.notes = [], [], [], []
        self.mem_wait_s = 0
        self.steps = []
        self.result = {}
        self.touched = False
        self.fatal = None
        self.started_epoch = time.time()
        self.started_utc = utc_now()
        self.afternpi_honoured = None
        self.sweep_queued = None
        self.sweep_started = False
        self.dat_records = []
        self.compose_prefix = ["docker", "compose", "--project-name", "pem", "--env-file", "infra/.env.local", "-f", "infra/docker-compose.yml"]

    # ---- parent hooks -----------------------------------------------------------------------------------------------------
    def http(self, method, path, body=None, content_type=None, accept=None):  # used by Runner.seed / provider_total / json_call
        headers = {}
        if content_type:
            headers["Content-Type"] = content_type
        if accept:
            headers["Accept"] = accept
        st, data, _ = self.api.request("ADMIN", method, path, raw=body, headers=headers)
        return st, data

    # ---- steps ---------------------------------------------------------------------------------------------------------------
    @contextlib.contextmanager
    def step(self, key, title):
        rec = {"key": key, "title": title, "startedUtc": utc_now(), "status": "OK"}
        t0 = time.time()
        self.steps.append(rec)
        self.log(f"=== STEP {title}")
        try:
            yield rec
        except BaseException as e:
            rec["status"], rec["error"] = "ERROR", repr(e)[:300]
            self.log(f"=== STEP {title}: ERROR {rec['error']}")
            raise
        finally:
            rec["seconds"] = round(time.time() - t0, 1)
            self.log(f"=== END  {title}: {rec['status']} after {rec['seconds']} s")

    def shell(self, cmd, timeout=900, env=None, cwd=None):
        e = dict(os.environ, MSYS_NO_PATHCONV="1", MSYS2_ARG_CONV_EXCL="*")
        e.update(env or {})
        return subprocess.run(cmd, cwd=cwd or str(MODERN), capture_output=True, text=True, errors="replace", timeout=timeout, env=e)

    def docker_lines(self, *args, timeout=60):
        r = self.shell(["docker", *args], timeout=timeout)
        return r.returncode, (r.stdout + r.stderr).strip()

    # ---- machine conditions -------------------------------------------------------------------------------------------------
    def machine(self) -> dict:
        ps = ("$o=Get-CimInstance Win32_OperatingSystem; $c=Get-CimInstance Win32_Processor; "
              "$p=(Get-Process -Name java,gradle,node,python -ErrorAction SilentlyContinue | Group-Object Name | "
              "ForEach-Object { $_.Name + '=' + $_.Count }) -join ','; "
              "[pscustomobject]@{free=[int]($o.FreePhysicalMemory/1024); total=[int]($o.TotalVisibleMemorySize/1024); "
              "cpus=($c | Measure-Object NumberOfLogicalProcessors -Sum).Sum; load=($c | Measure-Object LoadPercentage -Average).Average; "
              "procs=$p} | ConvertTo-Json -Compress")
        try:
            r = self.shell(["powershell", "-NoProfile", "-Command", ps], timeout=120)
            d = json.loads(r.stdout.strip())
        except (subprocess.TimeoutExpired, ValueError, OSError):
            d = {}
        rc, n = self.docker_lines("ps", "-q")
        rc2, stats = self.docker_lines("stats", "--no-stream", "--format", "{{.Name}} cpu {{.CPUPerc}} mem {{.MemUsage}}", *CONTAINERS[:3],
                                       timeout=90)
        return {"freeMb": d.get("free"), "totalMb": d.get("total"), "cpus": d.get("cpus"), "load": d.get("load"),
                "processes": d.get("procs") or "n/a", "containers": len(n.split()) if rc == 0 and n else "n/a",
                "stats": stats.replace("\n", "; ") if rc2 == 0 else "n/a"}

    # ---- preflight ----------------------------------------------------------------------------------------------------------
    def k6_image(self) -> str:
        m = re.search(r"^\s+image:\s*(grafana/k6:\S+@sha256:[0-9a-f]{64})\s*$", COMPOSE_FILE.read_text(encoding="utf-8"), re.M)
        if not m:
            raise SystemExit("infra/docker-compose.yml has no pinned k6 image (grafana/k6:<version>@sha256:<digest>)")
        return m.group(1)

    def preflight(self):
        for f in (K6_DIR / "search.js", K6_DIR / "detail.js", K6_DIR / "common.js", HERE / "load_perf_dataset.py",
                  MODERN / "infra/scripts/seed.sh", COMPOSE_FILE):
            if not f.is_file():
                raise SystemExit(f"missing file {f}")
        image = self.k6_image()
        self.result["k6Image"] = image
        for c in CONTAINERS:
            rc, out = self.docker_lines("inspect", "-f", "{{.State.Health.Status}}", c)
            if rc != 0 or out != "healthy":
                raise SystemExit(f"container {c} is not healthy ({out[:80]}): the stack is not up, nothing was changed")
        rc, _ = self.docker_lines("image", "inspect", image)
        if rc != 0:
            self.log(f"k6 image not present, pulling {image}")
            rc, out = self.docker_lines("pull", image, timeout=900)
            if rc != 0:
                raise SystemExit(f"could not pull {image}: {out[-300:]}")
        for role in USERS:
            self.api.token(role)
        st, body, _ = self.api.json("ANALYST", "GET", "/api/v1/providers?size=1")
        if st != 200:
            raise SystemExit(f"GET /api/v1/providers answered {st} for ANALYST: the stack is not usable, nothing was changed")
        self.result["providerTotalAtStart"] = body["total"]
        self.afternpi_honoured, verdict = self.probe_afternpi()
        self.result["afterNpiProbe"] = verdict
        self.log(f"afterNpi (keyset paging, contract 1.2.0): {verdict}")
        nfr = read_nfr()
        self.result["nfr"] = nfr
        missing = [t["nfr_row"] for t in TARGETS if t["nfr_row"] not in nfr["rows"]]
        if missing:
            raise SystemExit(f"nfr.md Performance table has no row {missing}: the runner's targets are out of date, nothing was changed")
        if nfr["missingText"]:
            self.problems.append("nfr.md no longer contains " + ", ".join(nfr["missingText"]) + ": the limits of the runner may be stale")
        mc = self.machine()
        self.result["machine"] = {"freeMemoryMbAtStart": mc["freeMb"], "totalMemoryMb": mc["totalMb"], "cpuLogical": mc["cpus"],
                                  "cpuLoadPercentAtStart": mc["load"], "processesAtStart": mc["processes"],
                                  "runningContainersAtStart": mc["containers"], "pemStatsAtStart": mc["stats"]}
        self.log(f"machine at start: {mc['freeMb']} MB free of {mc['totalMb']}, {mc['cpus']} CPUs, load {mc['load']} %, "
                 f"processes {mc['processes']}, {mc['containers']} containers running")

    def probe_afternpi(self):
        """Does the service honour afterNpi? Ask for the first provider F, then for the first after F: an honouring service answers with a
        higher NPI (or nothing); one that ignores the parameter answers F again (Spring ignores unknown query parameters)."""
        st1, b1, _ = self.api.json("ANALYST", "GET", "/api/v1/providers?size=1")
        if st1 != 200 or not b1.get("items"):
            return None, "not probed (no provider to start from)"
        first = b1["items"][0]["npi"]
        st2, b2, _ = self.api.json("ANALYST", "GET", f"/api/v1/providers?afterNpi={first}&size=1")
        if st2 == 400:
            return False, "IGNORED/REJECTED: the service answers 400 to afterNpi (it predates contract 1.2.0)"
        if st2 != 200:
            return None, f"not probed (answered {st2})"
        items = b2.get("items") or []
        if items and items[0]["npi"] <= first:
            return False, "IGNORED: the service answered the same provider again (it predates the deploy of contract 1.2.0); the walk uses offset paging"
        return True, "honoured"

    # ---- (b) load and AC-1 ----------------------------------------------------------------------------------------------------
    def check_dat(self, path: Path, expected: int) -> dict:
        recs = pemdata.read_records(path)
        lay = pemdata.Layout("PRVRECRD")
        self.dat_records = []
        types, statuses, npis = {}, {}, set()
        bad_len = invalid = dup = reserved = bad_names = 0
        for r in recs:
            if len(r) != lay.length:
                bad_len += 1
                continue
            f = lay.parse(r)
            npi = f["PRV-NPI"]
            invalid += not pemdata.npi_valid(npi)
            reserved += pemdata.npi_reserved(npi)
            dup += npi in npis
            npis.add(npi)
            typ = f["PRV-TYPE-CODE"]
            types[typ] = types.get(typ, 0) + 1
            statuses[f["PRV-ENROLL-STATUS"]] = statuses.get(f["PRV-ENROLL-STATUS"], 0) + 1
            if typ == "IN":
                ok = f["PRV-FIRST-NAME"].startswith("TESTPROV") and f["PRV-LAST-NAME"].startswith("PERF")
                name = f["PRV-LAST-NAME"]
            else:
                ok = f["PRV-LEGAL-NAME"].startswith("SAMPLE PERF ")
                name = f["PRV-LEGAL-NAME"]
            bad_names += not ok
            self.dat_records.append({"npi": npi, "medicaidId": f["PRV-MEDICAID-ID"], "type": typ, "status": f["PRV-ENROLL-STATUS"],
                                     "name": name})
        return {"expected": expected, "datRecords": len(recs), "recordLength": lay.length, "datBytes": path.stat().st_size,
                "badLength": bad_len, "invalidNpi": invalid, "duplicateNpi": dup, "reservedNpi": reserved, "badNames": bad_names,
                "types": dict(sorted(types.items())), "statuses": dict(sorted(statuses.items())), "npis": npis}

    def walk_block(self):
        """Every provider of the perf NPI block through the API. Keyset (afterNpi) when the service honours it, else offset paging."""
        items, calls, t0 = [], 0, time.time()
        if self.afternpi_honoured:
            after = str(int(PERF_FIRST) - 1)
            while True:
                st, body, _ = self.api.json("ANALYST", "GET", f"/api/v1/providers?afterNpi={after}&size=200")
                calls += 1
                if st != 200:
                    raise RuntimeError(f"keyset walk answered {st}")
                page = [i for i in body["items"] if PERF_FIRST <= i["npi"] <= PERF_LAST]
                items += page
                if not body["items"] or len(page) < len(body["items"]) or len(body["items"]) < 200:
                    break
                after = body["items"][-1]["npi"]
            mode = "afterNpi keyset"
        else:
            page_no = 0
            while True:
                st, body, _ = self.api.json("ANALYST", "GET", f"/api/v1/providers?page={page_no}&size=200")
                calls += 1
                if st != 200:
                    raise RuntimeError(f"offset walk answered {st}")
                items += [i for i in body["items"] if PERF_FIRST <= i["npi"] <= PERF_LAST]
                page_no += 1
                if not body["items"] or page_no * 200 >= body["total"]:
                    break
            mode = "offset paging (afterNpi ignored by the service)"
        return items, calls, round(time.time() - t0, 1), mode

    def load(self):
        n = self.args.count
        OUT.mkdir(parents=True, exist_ok=True)
        (OUT / "k6").mkdir(exist_ok=True)
        self.memory_gate("load of the data set")
        r = self.shell([sys.executable, str(HERE / "load_perf_dataset.py"), "--count", str(n), "--out", str(OUT)], timeout=1800,
                       env={"PYTHONDONTWRITEBYTECODE": "1"})
        tail = [ln for ln in (r.stdout + r.stderr).splitlines() if ln.strip()]
        for ln in tail:
            self.log("loader: " + ln[:240])
        if r.returncode != 0:
            raise RuntimeError("load_perf_dataset.py failed: " + " | ".join(tail[-3:])[:400])
        ac1 = self.check_dat(OUT / "provider-master-perf.dat", n)
        npis = ac1.pop("npis")
        ac1["loaderTail"] = tail[-12:]
        st, body, _ = self.api.json("ANALYST", "GET", "/api/v1/providers?size=1")
        ac1["apiTotal"] = body["total"] if st == 200 else None
        ac1["afterNpi"] = {"honoured": self.afternpi_honoured, "verdict": self.result.get("afterNpiProbe")}
        items, calls, secs, mode = self.walk_block()
        got = {i["npi"] for i in items}
        ac1.update({"walkCount": len(got), "walkCalls": calls, "walkSeconds": secs, "walkMode": mode, "walkEqualsFile": got == npis})
        self.log(f"AC-1: file {ac1['datRecords']} records x {ac1['recordLength']} bytes, API total {ac1['apiTotal']}, walk ({mode}) "
                 f"{len(got)} providers in {calls} calls, equal to the file: {got == npis}")
        reasons = []
        if ac1["datRecords"] != n or ac1["badLength"]:
            reasons.append(f"the file holds {ac1['datRecords']} records ({ac1['badLength']} of the wrong length), expected {n}")
        if ac1["invalidNpi"] or ac1["duplicateNpi"] or ac1["reservedNpi"] or ac1["badNames"]:
            reasons.append("NPIs or names break the synthetic-data rules")
        if n < 10_000:
            reasons.append(f"--count {n} is below the 10,000 of the story")
        if not ac1["apiTotal"] or ac1["apiTotal"] < 10_000:
            reasons.append(f"the API total is {ac1['apiTotal']}, not at least 10,000")
        if got != npis:
            reasons.append("the NPIs the API returns for the block differ from the generated file")
        ac1["status"] = "NOT MET" if reasons else "MET"
        ac1["reason"] = "; ".join(reasons)
        self.result["ac1"] = ac1
        # the sample k6 draws from: every 5th generated provider that the API returned (2,000 of 10,000)
        sample = [{"npi": r["npi"], "medicaidId": r["medicaidId"], "name": r["name"]} for r in self.dat_records[::5] if r["npi"] in got]
        (OUT / "npis.json").write_text(json.dumps(sample), encoding="utf-8", newline="\n")
        self.log(f"npis.json written: {len(sample)} NPIs for k6")
        if ac1["status"] != "MET":
            raise RuntimeError("AC-1 not met: " + ac1["reason"])

    # ---- (c) AC-2 ---------------------------------------------------------------------------------------------------------------
    def k6_cmd(self, script: str) -> list:
        a = self.args
        return [*self.compose_prefix, "--profile", "perf", "run", "--rm", "--no-deps", "-T",
                "-e", f"VUS={a.k6_vus}", "-e", f"DURATION={a.k6_duration}", "-e", f"THINK_MS={a.think_ms}",
                "k6", "run", "--quiet", f"/scripts/{script}.js"]

    @staticmethod
    def k6_values(summary: dict, scenario: str) -> dict:
        m = summary["metrics"]
        d = m[f"http_req_duration{{scenario:{scenario}}}"]["values"]
        f = m[f"http_req_failed{{scenario:{scenario}}}"]["values"]
        c = m[f"checks{{scenario:{scenario}}}"]["values"]
        kinds = {}
        for key, v in m.items():
            mm = re.fullmatch(rf"http_req_duration\{{scenario:{scenario},kind:(\w+)\}}", key)
            if mm:
                kinds[mm.group(1)] = {"p95Ms": round(v["values"]["p(95)"], 1), "avgMs": round(v["values"]["avg"], 1),
                                      "maxMs": round(v["values"]["max"], 1)}
        thresholds = {k: {e: bool(x["ok"]) for e, x in v["thresholds"].items()} for k, v in m.items() if v.get("thresholds")}
        return {"p95Ms": round(d["p(95)"], 1), "avgMs": round(d["avg"], 1), "medMs": round(d["med"], 1), "p90Ms": round(d["p(90)"], 1),
                "p99Ms": round(d["p(99)"], 1), "maxMs": round(d["max"], 1), "minMs": round(d["min"], 1),
                "requests": int(f["passes"] + f["fails"]), "failedRate": f["rate"], "failedRequests": int(f["passes"]),
                "checksRate": c["rate"], "iterations": int(m["iterations"]["values"]["count"]) if "iterations" in m else 0,
                "requestsPerSecond": m["http_reqs"]["values"]["rate"], "kinds": dict(sorted(kinds.items())),
                "thresholds": thresholds, "testRunSeconds": round(summary.get("state", {}).get("testRunDurationMs", 0) / 1000, 1)}

    def run_k6(self, script: str) -> dict:
        a = self.args
        summary_file = OUT / "k6" / f"{script}-summary.json"
        summary_file.unlink(missing_ok=True)  # a stale file must never be read as this run's result
        self.memory_gate(f"k6 {script}")
        token = self.api.token("ANALYST")      # fresh (<10 min old), passed in the environment, never on a command line
        cmd = self.k6_cmd(script)
        self.log(f"k6 {script}: {shlex.join(cmd)}   [ANALYST_TOKEN in the environment]")
        try:
            r = self.shell(cmd, timeout=duration_seconds(a.k6_duration) + 240, env={"ANALYST_TOKEN": token})
        finally:
            del token
            rc, ids = self.docker_lines("ps", "-aq", "--filter", "label=com.docker.compose.project=pem", "--filter",
                                        "label=com.docker.compose.service=k6")
            if rc == 0 and ids:
                self.docker_lines("rm", "-f", *ids.split())
        out_lines = [ln for ln in (r.stdout + r.stderr).splitlines() if ln.strip() and "Container pem-k6" not in ln]
        for ln in out_lines[-14:]:
            self.log(f"k6 {script}: {ln[:220]}")
        if r.returncode not in (0, 99):
            raise RuntimeError(f"k6 {script} exited {r.returncode}: " + " | ".join(out_lines[-3:])[:400])
        if not summary_file.is_file():
            raise RuntimeError(f"k6 {script} wrote no summary file (exit {r.returncode})")
        vals = self.k6_values(json.loads(summary_file.read_text(encoding="utf-8")), script)
        vals.update({"exitCode": r.returncode, "thresholdsCrossed": r.returncode == 99, "vus": a.k6_vus, "duration": a.k6_duration,
                     "thinkMs": a.think_ms, "script": f"{script}.js"})
        return vals

    def ac2_k6(self):
        res = self.result.setdefault("ac2", {})
        for script in ("search", "detail"):
            try:
                res[script] = self.run_k6(script)
                self.log(f"AC-2 {script}: p95 {fmt_ms(res[script]['p95Ms'])}, {res[script]['requests']} requests, "
                         f"{res[script]['failedRate'] * 100:.2f} % failed, k6 exit {res[script]['exitCode']}")
            except Exception as e:
                res[script] = {"error": repr(e)[:400]}
                self.problems.append(f"AC-2 {script}: {e!r}"[:400])
                self.log(f"AC-2 {script}: ERROR {e!r}"[:300])

    # ---- (d) AC-3 ---------------------------------------------------------------------------------------------------------------
    def submit_phase(self, label, bodies, concurrency):
        def one(body):
            try:
                st, data, secs = self.api.request("PROVIDER", "POST", "/api/v1/applications", payload=body)
                ok = st == 201 and json.loads(data).get("status") == "VALIDATED"
                code = None if ok else (json.loads(data).get("code") if data[:1] == b"{" else None)
                return {"npi": body["npi"], "status": st, "seconds": secs, "ok": ok, "code": code}
            except Exception as e:  # a failed request is a failed submit, with its time
                return {"npi": body["npi"], "status": 0, "seconds": None, "ok": False, "code": repr(e)[:80]}
        self.api.reset_connection()
        if concurrency <= 1:
            rows = [one(b) for b in bodies]
        else:
            with concurrent.futures.ThreadPoolExecutor(max_workers=concurrency) as pool:
                rows = list(pool.map(one, bodies))
        good = [r["seconds"] for r in rows if r["ok"]]
        st = stats_ms(good)
        st.update({"label": label, "submitted": len(rows), "failed": sum(not r["ok"] for r in rows),
                   "failureSamples": [{k: r[k] for k in ("status", "code")} for r in rows if not r["ok"]][:3]})
        self.log(f"AC-3 submit {label}: {len(rows)} submits, {st['failed']} failed, p95 {fmt_ms(st.get('p95Ms'))}, "
                 f"avg {fmt_ms(st.get('avgMs'))}, max {fmt_ms(st.get('maxMs'))}")
        return st, [r["npi"] for r in rows if r["ok"]]

    def settle_screened(self, npis, timeout):
        """Wait until every submitted provider is registered and screened (flag P or F): the events must not run into the screening timing."""
        t0, pending = time.time(), list(npis)
        while pending and time.time() - t0 < timeout:
            still = []
            for npi in pending:
                st, body, _ = self.api.json("ANALYST", "GET", f"/api/v1/providers/{npi}")
                if not (st == 200 and body.get("screenFlag") in ("P", "F")):
                    still.append(npi)
            pending = still
            if pending:
                time.sleep(3)
        return round(time.time() - t0, 1), len(pending)

    def pick_screening_npi(self) -> str:
        for r in self.dat_records:  # an ACTIVE perf individual (licence in the licence-board stub): the clean path of the four sources
            if r["type"] == "IN" and r["status"] == "A":
                return r["npi"]
        raise RuntimeError("no ACTIVE individual in the perf set to screen")

    def time_screening(self, npi):
        t0 = time.perf_counter()
        st, body, _ = self.api.json("ANALYST", "POST", "/api/v1/screenings", {"npi": npi})
        if st != 202:
            raise RuntimeError(f"POST /api/v1/screenings answered {st}: {json.dumps(body)[:200]}")
        sid, final, polls = body["screeningId"], body, 0
        deadline = t0 + self.args.screening_timeout
        while time.perf_counter() < deadline:
            time.sleep(0.1)
            st2, final, _ = self.api.json("ANALYST", "GET", f"/api/v1/screenings/{sid}")
            polls += 1
            if st2 == 200 and final.get("status") == "COMPLETED":
                break
        elapsed = time.perf_counter() - t0
        done = final.get("status") == "COMPLETED"
        srv = None
        try:
            a, b = parse_iso(final.get("startedAt")), parse_iso(final.get("completedAt"))
            srv = round((b - a).total_seconds(), 3) if a and b else None
        except ValueError:
            pass
        return {"seconds": elapsed, "completed": done, "result": final.get("result"), "serverSeconds": srv, "polls": polls,
                "sources": [r.get("source") for r in final.get("results") or []]}

    def ac3_submit_and_screening(self):
        a = self.args
        res = self.result.setdefault("ac3", {})
        owner = next(r["npi"] for r in self.dat_records if r["type"] == "IN")
        try:
            self.memory_gate("submits")
            n1, n2 = a.submits_sequential, a.submits_concurrent
            seq, ok1 = self.submit_phase("sequential", [application_body(i, owner) for i in range(1, n1 + 1)], 1)
            par, ok2 = self.submit_phase("concurrent", [application_body(i, owner) for i in range(n1 + 1, n1 + n2 + 1)], a.submit_concurrency)
            res["submit"] = {"sequential": seq, "concurrent": par, "concurrency": a.submit_concurrency, "total": n1 + n2,
                             "failed": seq["failed"] + par["failed"]}
            secs, left = self.settle_screened(ok1 + ok2, a.settle_timeout)
            res["settle"] = {"submitted": len(ok1 + ok2), "seconds": secs, "notScreenedAfterTimeout": left}
            self.log(f"AC-3: {len(ok1 + ok2)} new providers registered and screened by events after {secs} s ({left} still unscreened)")
            if left:
                self.problems.append(f"AC-3: {left} submitted providers were still unscreened after {a.settle_timeout} s")
        except Exception as e:
            res["submit"] = {"error": repr(e)[:400]}
            self.problems.append(f"AC-3 submit: {e!r}"[:400])
        try:
            self.memory_gate("screening")
            npi = self.pick_screening_npi()
            runs = [self.time_screening(npi if k == 0 else self.dat_records[k * 50]["npi"]) for k in range(a.screenings)]
            secs = [r["seconds"] for r in runs]
            res["screening"] = {"n": len(runs), "maxMs": round(max(secs) * 1000, 1), "minMs": round(min(secs) * 1000, 1),
                                "notCompleted": sum(not r["completed"] for r in runs),
                                "results": sorted({str(r["result"]) for r in runs}), "serverSeconds": [r["serverSeconds"] for r in runs],
                                "pollSeconds": 0.1, "sources": runs[0]["sources"], "firstProviderNpi": npi}
            self.log(f"AC-3 screening: {[round(s, 2) for s in secs]} s, results {res['screening']['results']}")
        except Exception as e:
            res["screening"] = {"error": repr(e)[:400]}
            self.problems.append(f"AC-3 screening: {e!r}"[:400])

    # ---- (e) AC-4 ---------------------------------------------------------------------------------------------------------------
    def run_job(self, key, start_path, run_path):
        a = self.args
        self.memory_gate(key)
        self.api.token("ADMIN")
        t0 = time.perf_counter()
        st, run, _ = self.api.json("ADMIN", "POST", start_path, {"businessDate": a.business_date})
        if st not in (200, 201, 202):
            raise RuntimeError(f"POST {start_path} answered {st}: {json.dumps(run)[:300]}")
        run_id, final = run["runId"], run
        deadline = t0 + a.job_timeout
        while time.perf_counter() < deadline:
            if run_path:
                st2, final, _ = self.api.json("ADMIN", "GET", f"{run_path}/{run_id}")
                if st2 != 200:
                    raise RuntimeError(f"GET {run_path}/{run_id} answered {st2}")
            else:  # T-MSIS: no GET by id in its contract, the run is found in the list
                st2, page, _ = self.api.json("ADMIN", "GET", "/api/v1/batch/tmsis-extracts?size=200")
                mine = [i for i in (page or {}).get("items", []) if i.get("runId") == run_id] if st2 == 200 else []
                final = mine[0] if mine else {"status": "NOT LISTED"}
            if final.get("status") in ("COMPLETED", "FAILED"):
                break
            time.sleep(0.5)
        elapsed = time.perf_counter() - t0
        out = {"runId": run_id, "status": final.get("status"), "seconds": round(elapsed, 1), "counts": final.get("counts"),
               "error": final.get("error"), "businessDate": a.business_date, "serverSeconds": server_seconds(final),
               "timeoutSeconds": a.job_timeout}
        if final.get("status") not in ("COMPLETED", "FAILED"):
            out["status"] = f"NOT FINISHED after {a.job_timeout} s"
        self.log(f"AC-4 {key}: {out['status']} after {out['seconds']} s, counts {json.dumps(out['counts'])}")
        return out

    def ac4_jobs(self):
        res = self.result.setdefault("ac4", {})
        jobs = self.result.setdefault("jobs", {})
        try:  # T-MSIS first: alone on the stack, not disturbed by the re-screenings of the sweep
            st, page, _ = self.api.json("ADMIN", "GET", "/api/v1/batch/tmsis-extracts?size=200")
            jobs["tmsisRunsBefore"] = (page or {}).get("total") if st == 200 else None
            jobs["tmsisEarlierRuns"] = sum(1 for i in (page or {}).get("items", [])
                                           if i.get("businessDate") == self.args.business_date and i.get("status") == "COMPLETED") if st == 200 else None
            res["tmsis"] = self.run_job("tmsis", "/api/v1/batch/tmsis-extracts", None)
        except Exception as e:
            res["tmsis"] = {"error": repr(e)[:400]}
            self.problems.append(f"AC-4 tmsis: {e!r}"[:400])
        try:
            self.sweep_started = True  # from here on the closing seed waits for the re-screenings, whatever happens to the run
            res["sweep"] = self.run_job("sweep", "/api/v1/batch/revalidation-sweeps", "/api/v1/batch/revalidation-sweeps")
            self.sweep_queued = (res["sweep"].get("counts") or {}).get("queued")
        except Exception as e:
            res["sweep"] = {"error": repr(e)[:400]}
            self.problems.append(f"AC-4 sweep: {e!r}"[:400])

    # ---- (f) restore ------------------------------------------------------------------------------------------------------------
    def total(self, path):
        st, body, _ = self.api.json("ANALYST", "GET", path)
        return body.get("total") if st == 200 and isinstance(body, dict) else None

    def wait_drain(self):
        """The sweep starts one re-screening per case (RevalidationDue events). Wait until no case is DUE or IN_SCREENING, then 20 s of quiet."""
        if not self.sweep_started:
            return "no sweep was started: nothing to wait for"
        t0, end = time.time(), time.time() + self.args.drain_timeout
        while time.time() < end:
            due, scr = self.total("/api/v1/revalidations?status=DUE&size=1"), self.total("/api/v1/revalidations?status=IN_SCREENING&size=1")
            allc = self.total("/api/v1/revalidations?size=1")
            self.log(f"drain: cases DUE {due}, IN_SCREENING {scr}, all {allc} of {self.sweep_queued} queued")
            if due == 0 and scr == 0 and (self.sweep_queued is None or (allc or 0) >= self.sweep_queued):
                time.sleep(20)
                return f"re-screenings finished after {round(time.time() - t0)} s"
            time.sleep(10)
        self.problems.append(f"restore: the re-screenings of the sweep were still running after {self.args.drain_timeout} s; "
                             "the closing seed ran anyway and late events may have left rows behind")
        return f"NOT finished after {self.args.drain_timeout} s"

    def counts(self):
        c = {}
        for db, table in (("provider_db", "provider"), ("revalidation_db", "tracked_provider"), ("revalidation_db", "revalidation_case"),
                          ("enrollment_db", "application")):
            c[f"{db}.{table}"] = loader.count(db, table)
        c["provider_db.provider perf block"] = loader.count("provider_db", "provider", loader.PERF_WHERE)
        c["revalidation_db.tracked_provider perf block"] = loader.count("revalidation_db", "tracked_provider", loader.PERF_WHERE)
        return c

    def restore(self):
        rs = self.result["restore"] = {"status": "FAILED", "detail": ""}
        details = []
        try:
            details.append(self.wait_drain())
            time.sleep(10)  # events of the last requests (risk tiers of the new providers) are through before the tables are truncated
        except BaseException as e:
            details.append(f"drain wait failed: {e!r}"[:200])
        want = {"provider_db.provider": SEED_PROVIDERS, "revalidation_db.tracked_provider": SEED_PROVIDERS,
                "revalidation_db.revalidation_case": 0, "enrollment_db.application": 0,
                "provider_db.provider perf block": 0, "revalidation_db.tracked_provider perf block": 0}
        for attempt in (1, 2):
            try:
                self.seed()
                time.sleep(5)
                got = self.counts()
                total = self.provider_total()
                rs["counts"], rs["apiProviderTotal"] = got, total
                if got == want and total == SEED_PROVIDERS:
                    rs["status"] = "OK"
                    details.append(f"seeded (attempt {attempt}): API total {total}, " + ", ".join(f"{k} {v}" for k, v in got.items()))
                    break
                details.append(f"attempt {attempt}: counts differ from the seed state: {got}, API total {total}")
                time.sleep(15)
            except BaseException as e:
                details.append(f"attempt {attempt} failed: {e!r}"[:300])
                time.sleep(15)
        rs["detail"] = " ".join(details)
        self.log("restore: " + rs["status"] + " " + rs["detail"][:400])

    # ---- the end ----------------------------------------------------------------------------------------------------------------
    def residue(self):
        try:
            runs, rows, arch = (loader.count("tmsis_db", t) for t in ("extract_run", "extract_row", "extract_archive"))
            return (f"tmsis_db now holds {runs} extract run(s), {rows} extract row(s) and {arch} archive generation(s) (this run added one run "
                    "with its rows and one archive generation); the Spring Batch job tables and the Kafka topics keep this run's jobs and events.")
        except Exception as e:
            return f"tmsis_db could not be counted ({e!r}); the extract run of this smoke stays in it, as do the Spring Batch job tables and Kafka topics."

    def finish(self):
        r = self.result
        r.update({"story": "MAM-88", "runId": time.strftime("%Y%m%d-%H%M%S", time.gmtime(self.started_epoch)), "startedUtc": self.started_utc,
                  "finishedUtc": utc_now(), "steps": self.steps, "problems": self.problems, "fatal": self.fatal,
                  "notVerified": NOT_VERIFIED, "params": {
                      "count": self.args.count, "businessDate": self.args.business_date, "vus": self.args.k6_vus,
                      "duration": self.args.k6_duration, "thinkMs": self.args.think_ms,
                      "submitsSequential": self.args.submits_sequential, "submitsConcurrent": self.args.submits_concurrent,
                      "submitConcurrency": self.args.submit_concurrency, "screenings": self.args.screenings,
                      "jobTimeoutSeconds": self.args.job_timeout}})
        head = self.shell(["git", "rev-parse", "--short", "HEAD"])
        r["gitHead"] = head.stdout.strip() or "unknown"
        mc = r.setdefault("machine", {})
        mc["memoryWaitSeconds"] = self.mem_wait_s
        try:
            end = self.machine()
            mc.update({"freeMemoryMbAtEnd": end["freeMb"], "processesAtEnd": end["processes"]})
        except Exception:
            pass
        r["residue"] = self.residue() if self.touched else ""
        r["targets"] = evaluate(r)
        OUT.mkdir(parents=True, exist_ok=True)
        RESULT.write_text(json.dumps(r, indent=2, default=str) + "\n", encoding="utf-8", newline="\n")
        if self.touched:
            report = Path(self.args.report)
            report.parent.mkdir(parents=True, exist_ok=True)
            report.write_text(render_report(r), encoding="utf-8", newline="\n")
            self.log(f"wrote {RESULT} and {report}")
        else:  # the run stopped in the preflight: nothing was measured, an earlier report is not overwritten
            self.log(f"wrote {RESULT}; no report written (nothing was measured)")
        for t in r["targets"]:
            self.log(f"{t['status']:<12} {t['name']}: {t['text'] or t['reason']}"[:300])

    def run(self) -> int:
        self.started_utc = utc_now()
        code = 0
        try:
            with self.step("preflight", "preflight (containers, k6 image, tokens, afterNpi, machine)"):
                self.preflight()
            self.touched = True
            with self.step("seed", "(a) normal seed"):
                self.seed()
            with self.step("load", "(b) load the providers and check AC-1"):
                self.load()
            for key, title, fn in (("ac2", "(c) AC-2 k6: search and detail, 20 users", self.ac2_k6),
                                   ("ac3", "(d) AC-3 submit and screening", self.ac3_submit_and_screening),
                                   ("ac4", "(e) AC-4 T-MSIS extract and revalidation sweep", self.ac4_jobs)):
                with self.step(key, title):
                    fn()
        except BaseException as e:  # keep going to the restore and the report, then say why it stopped
            self.fatal = repr(e)[:400]
            self.log(f"STOPPED: {self.fatal}")
            code = 1
        finally:
            try:
                if self.touched:
                    with self.step("restore", "(f) restore the seed state"):
                        self.restore()
                    if self.result.get("restore", {}).get("status") != "OK":
                        code = 1
            except BaseException as e:
                self.problems.append(f"restore raised {e!r}"[:300])
                code = 1
            try:
                self.finish()
            except BaseException as e:
                print(f"could not write the result and report: {e!r}", file=sys.stderr)
                code = 1
        if any(s["status"] == "ERROR" for s in self.steps) or any(t["status"] == "NOT MEASURED" for t in self.result.get("targets", [])):
            code = 1
        return code

    # ---- the plan ---------------------------------------------------------------------------------------------------------------
    def plan(self):
        a = self.args
        p = print
        p("DRY RUN: nothing below is executed, the stack is not touched (no docker call, no HTTP call, no file written).")
        p(f"gateway {GATEWAY_URL}, Keycloak {KEYCLOAK_URL}, output folder {OUT}, report {a.report}")
        p("")
        p("0. preflight (read-only)")
        p(f"   docker inspect -f {{{{.State.Health.Status}}}} {' '.join(CONTAINERS)}     every container must be healthy")
        p(f"   docker image inspect {self.k6_image()} ; docker pull it when missing   (pinned by version and digest, read from infra/docker-compose.yml)")
        p("   tokens of admin1, analyst1, provider1 (passwords read from infra/.env.local at run time, never printed)")
        p("   GET /api/v1/providers?size=1 and the afterNpi probe (?afterNpi=<first npi>&size=1): honoured or ignored by the running service")
        p("   machine conditions: free memory, CPUs, load, java/gradle/node/python processes, running containers, docker stats of 3 pem containers")
        nfr = read_nfr()
        p(f"   nfr.md section '{nfr['heading']}': marks DEFAULT = {nfr['sectionDefault']}; rows found: "
          f"{', '.join(r for r in nfr['rows'])}; every target row present: {all(t['nfr_row'] in nfr['rows'] for t in TARGETS)}; "
          f"limit text no longer in nfr.md: {nfr['missingText'] or 'none'}")
        p("1. (a) normal seed")
        p("   memory gate (wait while free memory < 1024 MB, 60 s steps); bash infra/scripts/seed.sh; GET /api/v1/providers?size=1 -> total 16")
        p("2. (b) load and AC-1")
        p(f"   memory gate; {shlex.join([sys.executable, str(HERE / 'load_perf_dataset.py'), '--count', str(a.count), '--out', str(OUT)])}")
        p("      = generate.py perf (seed 20260115), offline check against the Flyway migrations, docker exec -i pem-postgres-1 psql --single-transaction")
        p("        into provider_db.provider and revalidation_db.tracked_provider, counts before and after (exit 1 unless the perf block holds exactly --count rows)")
        p(f"   AC-1: provider-master-perf.dat = {a.count} x 130-byte records, valid NPI check digits, no duplicate, none reserved, fake names (TESTPROV / SAMPLE);")
        p("         GET /api/v1/providers total >= 10,000; walk of the NPI block "
          f"{loader.NPI_BLOCK[0]}-{loader.NPI_BLOCK[1]} through the API (afterNpi keyset if honoured, else page/size=200) = the generated NPIs; writes out/npis.json (2,000 sampled NPIs)")
        p("3. (c) AC-2 k6, each after a memory gate, ANALYST token in the environment of the command only:")
        for s in ("search", "detail"):
            p("   ANALYST_TOKEN=<token of analyst1> " + shlex.join(self.k6_cmd(s)))
        p("   read out/k6/<script>-summary.json; target p95 < 500 ms, failed requests < 1 %, checks > 99 %")
        p("4. (d) AC-3 (memory gate first)")
        p(f"   POST /api/v1/applications as provider1 (PROVIDER): {a.submits_sequential} one at a time, then {a.submits_concurrent} with {a.submit_concurrency} in parallel;")
        p("   body = " + json.dumps(application_body(1, "<npi of a perf individual>")))
        p(f"   NPIs {submit_npi(1)} ... {submit_npi(a.submits_sequential + a.submits_concurrent)} (1008nnnnnn, valid check digit); expect 201 and status VALIDATED; p95 nearest-rank, target < 1 s")
        p(f"   then wait up to {a.settle_timeout} s until every new provider has screen flag P or F (GET /api/v1/providers/<npi>)")
        p(f"   then POST /api/v1/screenings {{\"npi\": <an ACTIVE perf individual>}} as analyst1 x{a.screenings}; poll GET /api/v1/screenings/<id> every 0.1 s until COMPLETED; target < 5 s")
        p("5. (e) AC-4 (memory gate before each), ADMIN token, business date " + a.business_date)
        p(f"   T-MSIS extract first: POST /api/v1/batch/tmsis-extracts {{\"businessDate\": \"{a.business_date}\"}}; poll GET /api/v1/batch/tmsis-extracts?size=200 every 0.5 s until the run is COMPLETED (timeout {a.job_timeout} s); target < 90 s")
        p(f"   then the sweep: POST /api/v1/batch/revalidation-sweeps; poll GET /api/v1/batch/revalidation-sweeps/<runId> every 0.5 s (timeout {a.job_timeout} s); target < 90 s")
        p("6. (f) ALWAYS, also after a failure of any step above")
        p(f"   wait (up to {a.drain_timeout} s) until GET /api/v1/revalidations shows no case DUE or IN_SCREENING, then 20 s of quiet;")
        p("   bash infra/scripts/seed.sh (memory gate first); counts via docker exec -i pem-postgres-1 psql: provider 16, tracked_provider 16, revalidation_case 0, application 0, perf block 0 in both;")
        p("   GET /api/v1/providers?size=1 -> total 16; up to 2 attempts. There is no separate unload: the seed TRUNCATEs both tables the loader fills.")
        p("7. writes out/perf-result.json and " + str(a.report) + " (every nfr.md target, MET / NOT MET, DEFAULT, projection note, machine conditions)")
        p("   exit code 0: every step ran and the seed state is restored (targets may still be NOT MET, see the report); 1: a step failed or the restore failed")


def parse_args(argv=None):
    ap = argparse.ArgumentParser(description="MAM-88 performance smoke (see the header of this file). Start it under the stack lock.")
    ap.add_argument("--stack-lock-held", action="store_true", help="REQUIRED for a real run: you hold the shared stack lock")
    ap.add_argument("--dry-run", action="store_true", help="print every step and command, touch nothing")
    ap.add_argument("--render-report", metavar="RESULT_JSON", help="only render the report from an existing perf-result.json (no stack needed)")
    ap.add_argument("--report", default=str(REPORT), help="report file (default modern/docs/reports/performance-report.md)")
    ap.add_argument("--count", type=int, default=10_000, help="providers to load (default 10000, the story's size)")
    ap.add_argument("--business-date", default=dt.date.today().isoformat(), help="business date of the two jobs (default: today)")
    ap.add_argument("--k6-vus", type=int, default=20)
    ap.add_argument("--k6-duration", default="60s")
    ap.add_argument("--think-ms", type=int, default=0, help="pause between two requests of one k6 user (default 0: always one in flight)")
    ap.add_argument("--submits-sequential", type=int, default=40)
    ap.add_argument("--submits-concurrent", type=int, default=40)
    ap.add_argument("--submit-concurrency", type=int, default=5)
    ap.add_argument("--screenings", type=int, default=1, help="screenings to time (default 1: one screening of one provider)")
    ap.add_argument("--screening-timeout", type=int, default=60)
    ap.add_argument("--settle-timeout", type=int, default=240, help="seconds to wait for the submitted providers to be screened")
    ap.add_argument("--job-timeout", type=int, default=900, help="seconds a job may take before it is reported as not finished (15 minutes)")
    ap.add_argument("--drain-timeout", type=int, default=900, help="seconds to wait for the sweep's re-screenings before the closing seed")
    a = ap.parse_args(argv)
    if a.submit_concurrency < 1 or a.submits_sequential < 1 or a.submits_concurrent < 1 or a.screenings < 1:
        ap.error("counts must be at least 1")
    return a


def main(argv=None) -> int:
    a = parse_args(argv)
    if a.render_report:
        result = json.loads(Path(a.render_report).read_text(encoding="utf-8"))
        result["targets"] = evaluate(result)
        Path(a.report).parent.mkdir(parents=True, exist_ok=True)
        Path(a.report).write_text(render_report(result), encoding="utf-8", newline="\n")
        print(f"report written to {a.report}")
        return 0
    perf = Perf(a)
    if a.dry_run:
        perf.plan()
        return 0
    if not a.stack_lock_held:
        perf.plan()
        print("\nNOT STARTED: a real run changes the shared stack. Take the stack lock, then start again with --stack-lock-held.", file=sys.stderr)
        return 2
    print("MAM-88 performance smoke: this run reseeds the pem stack twice and loads 10,000 providers; the stack lock must be held by the caller.")
    return perf.run()


if __name__ == "__main__":
    sys.exit(main())
