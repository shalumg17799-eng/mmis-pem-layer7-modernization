#!/usr/bin/env python3
"""Parity stage 2: run the modern equivalents of the legacy PEM jobs and capture their legacy-format files.

One scenario per golden run (every legacy run started from the seed master, so every modern run starts from the
seed state too: `bash infra/scripts/seed.sh`, then the batch calls through the gateway with an ADMIN token).

  day-baseline     PRVDAY01 20261120-baseline        intake 8 applications, APPERROR, screening run, master export
  day-valid-only   PRVDAY01 20261120-valid-only      intake the first 3 applications, screening run, master export
  month-seed       PRVMTH01 20261120-seed            revalidation sweep (REVALQ), T-MSIS extract (TMSISOUT), export
  dir-seed         PRVDIR01 20261120-seed            directory extract (DIROUT), export
  san-baseline     PRVSAN01 20261120-sancin-baseline sanction intake (SANCIN), export

Rules kept by this script: the admin password is read from infra/.env.local and is never printed or stored; the
token is never printed; the stack must be locked by the caller (stack.lock); memory is checked before every heavy
step (pause 60 s while free memory is below 1024 MB).

Run from modern/:  python parity/modern/run_modern.py <scenario> [--business-date 2026-11-20]
Output: parity/modern/<job>/<run-id>/{inputs,before,after,logs}/ and manifest.json
"""

import argparse
import datetime as dt
import hashlib
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

HERE = os.path.dirname(os.path.abspath(__file__))
MODERN = os.path.abspath(os.path.join(HERE, "..", ".."))
GOLDEN = os.path.join(MODERN, "parity", "golden")
GATEWAY = "http://localhost:8080"
KEYCLOAK = "http://localhost:8180/realms/pem/protocol/openid-connect/token"

# scenario -> (job, run id, golden run folder holding the inputs the legacy run really loaded)
SCENARIOS = {
    "day-baseline": ("PRVDAY01", "20261120-baseline"),
    "day-valid-only": ("PRVDAY01", "20261120-valid-only"),
    "month-seed": ("PRVMTH01", "20261120-seed"),
    "dir-seed": ("PRVDIR01", "20261120-seed"),
    "san-baseline": ("PRVSAN01", "20261120-sancin-baseline"),
}


def git_bash():
    """Git Bash next to git (a plain `bash` on this machine is the WSL relay, which has no distribution)."""
    try:
        exe = subprocess.run(["git", "--exec-path"], capture_output=True, text=True, check=True).stdout.strip()
        cand = os.path.normpath(os.path.join(exe, "..", "..", "..", "usr", "bin", "bash.exe"))
        if os.path.exists(cand):
            return cand
    except (OSError, subprocess.CalledProcessError):
        pass
    return "bash"


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def now():
    return dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


class Runner:
    def __init__(self, scenario, business_date):
        self.scenario = scenario
        self.job, self.run_id = SCENARIOS[scenario]
        self.business_date = business_date
        self.dir = os.path.join(HERE, self.job, self.run_id)
        self.golden = os.path.join(GOLDEN, self.job, self.run_id)
        for sub in ("inputs", "before", "after", "logs"):
            os.makedirs(os.path.join(self.dir, sub), exist_ok=True)
        self.files = []
        self.runs = []
        self.notes = []
        self.problems = []
        self.log_lines = []
        self.mem_wait_s = 0
        self._tok = None
        self._tok_at = 0
        self.started = now()
        self.started_epoch = time.time()

    # ---- infrastructure -------------------------------------------------------------------------
    def log(self, msg):
        line = f"{dt.datetime.now(dt.timezone.utc).strftime('%H:%M:%S')} {msg}"
        self.log_lines.append(line)
        print(line, flush=True)

    @staticmethod
    def free_mb():
        """Free physical memory in MB; a probe that hangs or fails counts as 0 (the caller then waits and asks again)."""
        try:
            out = subprocess.run(
                ["powershell", "-NoProfile", "-Command",
                 "[int]((Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory/1024)"],
                capture_output=True, text=True, timeout=120)
            return int(out.stdout.strip())
        except (subprocess.TimeoutExpired, ValueError, OSError):
            return 0

    def memory_gate(self, what):
        """Pause (60 s steps) while free memory is under 1024 MB; never fail for memory."""
        while True:
            free = self.free_mb()
            if free >= 1024:
                self.log(f"memory ok before {what}: {free} MB free")
                return
            self.log(f"memory {free} MB < 1024 MB before {what}: waiting 60 s")
            time.sleep(60)
            self.mem_wait_s += 60

    def token(self):
        if self._tok and time.time() - self._tok_at < 300:
            return self._tok
        pw = None
        with open(os.path.join(MODERN, "infra", ".env.local"), encoding="utf-8") as f:
            for line in f:
                if line.startswith("KC_ADMIN_PASSWORD="):
                    pw = line.split("=", 1)[1].rstrip("\r\n")
        if not pw:
            raise SystemExit("KC_ADMIN_PASSWORD missing in infra/.env.local")
        body = urllib.parse.urlencode({"grant_type": "password", "client_id": "pem-test",
                                       "username": "admin1", "password": pw}).encode()
        del pw
        req = urllib.request.Request(KEYCLOAK, data=body, method="POST")
        with urllib.request.urlopen(req, timeout=30) as r:
            self._tok = json.loads(r.read())["access_token"]
        self._tok_at = time.time()
        return self._tok

    def http(self, method, path, body=None, content_type=None, accept=None):
        headers = {"Authorization": "Bearer " + self.token()}
        if content_type:
            headers["Content-Type"] = content_type
        if accept:
            headers["Accept"] = accept
        req = urllib.request.Request(GATEWAY + path, data=body, method=method, headers=headers)
        try:
            with urllib.request.urlopen(req, timeout=120) as r:
                return r.status, r.read()
        except urllib.error.HTTPError as e:
            return e.code, e.read()

    def json_call(self, method, path, payload=None):
        body = None if payload is None else json.dumps(payload).encode()
        status, data = self.http(method, path, body, "application/json" if body else None, "application/json")
        try:
            parsed = json.loads(data) if data else None
        except ValueError:
            parsed = {"raw": data.decode("utf-8", "replace")[:500]}
        return status, parsed

    def multipart_call(self, path, file_name, file_bytes, fields):
        boundary = "----parity" + uuid.uuid4().hex
        parts = []
        for k, v in fields.items():
            parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{k}"\r\n\r\n{v}\r\n'.encode())
        parts.append((f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{file_name}"\r\n'
                      f'Content-Type: application/octet-stream\r\n\r\n').encode() + file_bytes + b"\r\n")
        parts.append(f"--{boundary}--\r\n".encode())
        status, data = self.http("POST", path, b"".join(parts), f"multipart/form-data; boundary={boundary}",
                                 "application/json")
        try:
            return status, json.loads(data)
        except ValueError:
            return status, {"raw": data.decode("utf-8", "replace")[:500]}

    # ---- stack ---------------------------------------------------------------------------------
    def seed(self):
        self.memory_gate("seed")
        out = subprocess.run([git_bash(), "infra/scripts/seed.sh"], cwd=MODERN, capture_output=True, text=True,
                             timeout=300)
        if out.returncode != 0:
            raise SystemExit("seed failed: " + out.stderr[-500:])
        self.log("seed.sh done: " + out.stdout.strip().splitlines()[-2])
        total = self.provider_total()
        if total != 16:
            raise SystemExit(f"seed state wrong: {total} providers")
        self.log("seed state: 16 providers")

    def provider_total(self):
        st, body = self.json_call("GET", "/api/v1/providers?size=1")
        return body["total"] if st == 200 else -1

    def providers(self):
        out, page = [], 0
        while True:
            st, body = self.json_call("GET", f"/api/v1/providers?size=200&page={page}")
            if st != 200:
                raise RuntimeError(f"GET providers -> {st}")
            out.extend(body["items"])
            if len(out) >= body["total"] or not body["items"]:
                return out
            page += 1

    def export_master(self):
        st, data = self.http("GET", "/api/v1/legacy/provider-master", accept="text/plain")
        if st != 200:
            raise RuntimeError(f"legacy export -> {st}")
        return data

    def export_stable(self, tries=12, gap=3):
        """The export twice in a row, gap seconds apart, with identical bytes (events have settled)."""
        prev = self.export_master()
        for _ in range(tries):
            time.sleep(gap)
            cur = self.export_master()
            if cur == prev:
                return cur
            prev = cur
        self.problems.append("master export did not become stable in %d s" % (tries * gap))
        return prev

    def wait_for(self, what, predicate, timeout=180, gap=2):
        end = time.time() + timeout
        while time.time() < end:
            ok, info = predicate()
            if ok:
                self.log(f"{what}: done ({info})")
                return True
            time.sleep(gap)
        self.log(f"{what}: TIMEOUT after {timeout} s ({info})")
        self.problems.append(f"{what}: timeout after {timeout} s ({info})")
        return False

    # ---- files ---------------------------------------------------------------------------------
    def put(self, rel, data, role, lrecl=None, golden=None, extra=None):
        path = os.path.join(self.dir, rel)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "wb") as f:
            f.write(data)
        entry = {"path": rel.replace("\\", "/"), "role": role, "bytes": len(data), "sha256": sha256(data)}
        if lrecl:
            entry["recordLength"] = lrecl
            entry["records"] = len(data) // lrecl if lrecl and len(data) % lrecl == 0 else None
            if len(data) % lrecl:
                self.problems.append(f"{rel}: {len(data)} bytes is not a multiple of {lrecl}")
        if golden:
            entry["goldenCounterpart"] = golden
        if extra:
            entry.update(extra)
        self.files.append(entry)
        return entry

    def put_records(self, rel_stem, delivered, lrecl, role, golden):
        """As delivered by the API (one record per line) and as a record stream (records back to back, no line
        end, exactly like the converted golden files). Only the line ends are removed; no field is touched."""
        if b"\n" in delivered:
            lines = delivered.split(b"\n")
            if lines and lines[-1] == b"":
                lines.pop()
            lines = [ln[:-1] if ln.endswith(b"\r") else ln for ln in lines]
            ends = "LF between records"
        else:  # APPERROR, SCRNOUT, REVALQ, TMSISOUT and DIROUT come back to back, like the legacy files
            lines = [delivered[i:i + lrecl] for i in range(0, len(delivered), lrecl)]
            ends = "none (records back to back)"
        bad = [i + 1 for i, ln in enumerate(lines) if len(ln) != lrecl]
        if bad:
            self.problems.append(f"{rel_stem}: records {bad[:10]} are not {lrecl} bytes")
        stream = b"".join(lines)
        self.put(rel_stem + ".delivered", delivered, role + " (as delivered by the API, one record per line)")
        return self.put(rel_stem + ".ascii", stream, role + " (record stream, records back to back)", lrecl,
                        golden, {"deliveredLineEnds": ends, "badRecordLengths": bad})

    def copy_input(self, golden_name, our_name=None):
        src = os.path.join(self.golden, "inputs", golden_name)
        with open(src, "rb") as f:
            data = f.read()
        lines = [ln for ln in data.split(b"\n") if ln]
        self.put("inputs/" + (our_name or golden_name), data, "input as sent to the legacy run (same bytes)",
                 extra={"records": len(lines), "recordLength": len(lines[0]) if lines else 0,
                        "lineEnds": "LF after every record"})
        return data

    # ---- batch run helpers -----------------------------------------------------------------------
    def follow(self, label, start_path, payload=None, multipart=None, run_path=None, timeout=240):
        """Start a run, follow it to COMPLETED or FAILED, record id, counts, error."""
        self.memory_gate(label)
        if multipart:
            st, run = self.multipart_call(start_path, *multipart)
        else:
            st, run = self.json_call("POST", start_path, payload)
        self.log(f"{label}: POST {start_path} -> {st}")
        rec = {"step": label, "startPath": start_path, "startHttpStatus": st}
        if st not in (200, 201, 202):
            rec.update({"status": "NOT STARTED", "error": json.dumps(run)[:400]})
            self.runs.append(rec)
            self.problems.append(f"{label}: start answered {st}: {json.dumps(run)[:300]}")
            return rec
        run_id = run["runId"]
        rec["runId"] = run_id
        poll_path = (run_path or start_path) + "/" + run_id if run_path != "-" else None
        final = run
        end = time.time() + timeout
        while poll_path and time.time() < end and final.get("status") not in ("COMPLETED", "FAILED"):
            time.sleep(2)
            st2, final = self.json_call("GET", poll_path)
            if st2 != 200:
                final = {"status": "UNKNOWN", "error": f"poll answered {st2}"}
                break
        rec.update({"status": final.get("status"), "businessDate": final.get("businessDate"),
                    "counts": final.get("counts"), "error": final.get("error"),
                    "startedAt": final.get("startedAt"), "endedAt": final.get("endedAt"),
                    "job": final.get("job")})
        self.log(f"{label}: run {run_id} {rec['status']} counts={rec['counts']} error={rec['error']}")
        if poll_path and rec["status"] != "COMPLETED":  # without a GET by id the caller polls the list itself
            self.problems.append(f"{label}: run {run_id} ended {rec['status']}: {rec['error']}")
        self.runs.append(rec)
        return rec

    def download(self, path, accept="text/plain"):
        st, data = self.http("GET", path, accept=accept)
        if st != 200:
            self.problems.append(f"GET {path} -> {st}")
            return None
        return data

    # ---- scenarios ---------------------------------------------------------------------------------
    def settle_new_providers(self, seed_npis, expected_new, label):
        """Wait until the registered providers are screened (flag not U) and tiered (events, ADR-003)."""
        def pred():
            ps = self.providers()
            new = [p for p in ps if p["npi"] not in seed_npis]
            # a provider that failed screening (flag F) is not tiered: the tier follows a PASS (risk-service)
            pending = [p["npi"] for p in new
                       if p.get("screenFlag") == "U" or (p.get("screenFlag") == "P" and not p.get("riskTier"))]
            return (len(new) >= expected_new and not pending,
                    f"new={len(new)}/{expected_new}, unscreened-or-untiered={pending}")
        return self.wait_for(label, pred, timeout=180)

    def settle_all_screened(self, label):
        def pred():
            ps = self.providers()
            bad = [p["npi"] for p in ps if p.get("screenFlag") == "U"
                   or (p.get("screenFlag") == "P" and not p.get("riskTier"))]
            return (not bad, f"unscreened-or-untiered={bad}")
        return self.wait_for(label, pred, timeout=180)

    def day(self, app_file):
        self.seed()
        seed_npis = {p["npi"] for p in self.providers()}
        before = self.export_stable()
        self.put_records("before/provider-master", before, 130, "provider master export BEFORE (seed state, 16 providers)",
                         "before/provider-master.ebcdic.ascii")
        apps = self.copy_input(app_file, "appinput.dat")
        n_apps = len([ln for ln in apps.split(b"\n") if ln])
        first_len = len(apps.split(b"\n")[0])
        self.log(f"APPINPUT: {n_apps} records of {first_len} bytes, same bytes as the legacy run")

        run = self.follow("PRVENR01 intake", "/api/v1/batch/intake-runs",
                          multipart=("appinput.dat", apps, {"businessDate": self.business_date,
                                                            "batchId": self.business_date.replace("-", "")}))
        if run.get("runId"):
            err = self.download(f"/api/v1/batch/intake-runs/{run['runId']}/error-file")
            if err is not None:
                self.put_records("after/apperror", err, 250, "APPERROR written by the intake run",
                                 "after/apperror.ebcdic.ascii")
        written = (run.get("counts") or {}).get("written", 0)
        self.settle_new_providers(seed_npis, written, "new providers registered, screened and tiered by events")
        step010 = self.export_stable()
        self.put_records("after/provider-master-after-step010", step010, 130,
                         "provider master after the intake and the event-driven registration, screening and tiering "
                         "(the modern system cannot stop between STEP010 and STEP030/040)",
                         "after/provider-master-after-step010.ebcdic.ascii")

        scr = self.follow("PRVSCR01 screening run", "/api/v1/batch/screening-runs",
                          payload={"businessDate": self.business_date})
        if scr.get("runId"):
            res = self.download(f"/api/v1/batch/screening-runs/{scr['runId']}/results-file")
            if res is not None:
                self.put_records("after/scrnout", res, 67, "SCRNOUT written by the screening run",
                                 "after/scrnout.ebcdic.ascii")
        self.settle_all_screened("every provider screened and tiered after the screening run")
        self.capture_event_side(seed_npis)
        final = self.export_stable()
        self.put_records("after/provider-master-after-step040", final, 130,
                         "provider master after the screening run and the event-driven tiering (final)",
                         "after/provider-master-after-step040.ebcdic.ascii")

    def capture_event_side(self, seed_npis):
        """What the event-driven path did (no legacy file exists for it): the screenings of the providers that the
        intake registered, and the application rows with their error codes."""
        rows = {}
        for p in self.providers():
            if p["npi"] in seed_npis:
                continue
            st, body = self.json_call("GET", f"/api/v1/screenings?npi={p['npi']}&size=20")
            rows[p["npi"]] = {"http": st, "screenings": body}
        self.put("logs/screenings-of-new-providers.json", json.dumps(rows, indent=2).encode(),
                 "GET /api/v1/screenings?npi=... for every provider registered by the intake (screened by events)")
        st, body = self.json_call("GET", "/api/v1/applications?size=50")
        self.put("logs/applications.json", json.dumps({"http": st, "body": body}, indent=2).encode(),
                 "GET /api/v1/applications after the run: status and error codes of every application")

    def month(self):
        self.seed()
        before = self.export_stable()
        self.put_records("before/provider-master", before, 130, "provider master export BEFORE (seed state)",
                         "before/provider-master.ebcdic.ascii")
        self.copy_input("provider-master-seed-sorted.dat")
        sweep = self.follow("PRVREV01 revalidation sweep", "/api/v1/batch/revalidation-sweeps",
                            payload={"businessDate": self.business_date})
        if sweep.get("runId"):
            q = self.download(f"/api/v1/batch/revalidation-sweeps/{sweep['runId']}/queue-file")
            if q is not None:
                self.put_records("after/revalq", q, 80, "REVALQ written by the sweep", "after/revalq.ebcdic.ascii")
        tm = self.follow("PRVTMS01 T-MSIS extract", "/api/v1/batch/tmsis-extracts",
                         payload={"businessDate": self.business_date}, run_path="-")
        if tm.get("runId"):
            # the run is polled through the list (there is no GET by id in the contract)
            def pred():
                st, page = self.json_call("GET", "/api/v1/batch/tmsis-extracts?size=50")
                items = (page or {}).get("items") or []
                mine = [i for i in items if i.get("runId") == tm["runId"]]
                return (bool(mine) and mine[0].get("status") in ("COMPLETED", "FAILED"),
                        mine[0].get("status") if mine else "not listed")
            self.wait_for("T-MSIS run listed as finished", pred, timeout=240)
            st, page = self.json_call("GET", "/api/v1/batch/tmsis-extracts?size=50")
            mine = [i for i in (page or {}).get("items", []) if i.get("runId") == tm["runId"]]
            if mine:
                tm.update({"status": mine[0].get("status"), "counts": mine[0].get("counts"),
                           "error": mine[0].get("error"), "startedAt": mine[0].get("startedAt"),
                           "endedAt": mine[0].get("endedAt")})
                self.log(f"T-MSIS run {tm['runId']} {tm['status']} counts={tm['counts']} error={tm['error']}")
                if tm["status"] != "COMPLETED":
                    self.problems.append(f"T-MSIS run ended {tm['status']}: {tm['error']}")
            self.put("logs/tmsis-extract-list.json", json.dumps(page, indent=2).encode(),
                     "GET /api/v1/batch/tmsis-extracts after the run (runs and archive generations)")
            f = self.download(f"/api/v1/batch/tmsis-extracts/{tm['runId']}/file")
            if f is not None:
                self.put_records("after/tmsisout", f, 120, "TMSISOUT written by the extract",
                                 "after/tmsisout.ebcdic.ascii")
        after = self.export_stable()
        self.put_records("after/provider-master-after", after, 130, "provider master export AFTER",
                         "after/provider-master-after.ebcdic.ascii")

    def directory(self):
        self.seed()
        before = self.export_stable()
        self.put_records("before/provider-master", before, 130, "provider master export BEFORE (seed state)",
                         "before/provider-master.ebcdic.ascii")
        self.copy_input("provider-master-seed-sorted.dat")
        d = self.follow("PRVDIR01 directory extract", "/api/v1/batch/directory-extracts",
                        payload={"businessDate": self.business_date})
        if d.get("runId"):
            f = self.download(f"/api/v1/batch/directory-extracts/{d['runId']}/file")
            if f is not None:
                self.put_records("after/dirout", f, 100, "DIROUT written by the extract", "after/dirout.ebcdic.ascii")
        after = self.export_stable()
        self.put_records("after/provider-master-after", after, 130, "provider master export AFTER",
                         "after/provider-master-after.ebcdic.ascii")

    def sanctions(self):
        self.seed()
        before = self.export_stable()
        self.put_records("before/provider-master", before, 130, "provider master export BEFORE (seed state)",
                         "before/provider-master.ebcdic.ascii")
        self.copy_input("provider-master-seed-sorted.dat")
        san = self.copy_input("sancin.dat")
        run = self.follow("PRVSAN01 sanction intake", "/api/v1/batch/sanction-intake-runs",
                          multipart=("sancin.dat", san, {"businessDate": self.business_date}))
        time.sleep(5)
        after = self.export_stable()
        self.put_records("after/provider-master-after", after, 130,
                         "provider master export AFTER (status changes arrive by events)",
                         "after/provider-master-after.ebcdic.ascii")
        st, page = self.json_call("GET", "/api/v1/sanctions?size=50")
        self.put("logs/sanction-history.json", json.dumps(page, indent=2).encode(),
                 f"GET /api/v1/sanctions after the run (HTTP {st})")
        st, page = self.json_call("GET", "/api/v1/notices?size=50")
        self.put("logs/notices.json", json.dumps(page, indent=2).encode(),
                 f"GET /api/v1/notices after the run (HTTP {st}); modern counterpart of the PRVNT* CALLs")
        return run

    # ---- manifest ----------------------------------------------------------------------------------
    def finish(self, flow_notes):
        docker = subprocess.run(
            ["docker", "inspect", "pem-pem-apps-1", "--format", "{{.Image}} {{.Created}}"],
            capture_output=True, text=True)
        head = subprocess.run(["git", "rev-parse", "--short", "HEAD"], cwd=MODERN, capture_output=True, text=True)
        # the job lines of the modern services (legacy text where the story keeps it)
        since = dt.datetime.fromtimestamp(self.started_epoch, dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
        lg = subprocess.run(["docker", "logs", "--since", since, "pem-pem-apps-1"], capture_output=True, text=True)
        keep = [ln for ln in (lg.stdout + lg.stderr).splitlines()
                if any(k in ln for k in ("PRVENR01", "PRVSCR01", "PRVRSK01", "PRVREV01", "PRVTMS01", "PRVDIR01",
                                         "PRVSAN01", "ERROR"))]
        self.put("logs/modern-job-lines.txt", ("\n".join(keep[:400]) + "\n").encode("utf-8", "replace"),
                 "job lines of pem-pem-apps-1 during the run (filtered; ECS JSON lines)")
        self.put("logs/run-log.txt", ("\n".join(self.log_lines) + "\n").encode(), "log of this script")
        manifest = {
            "job": self.job, "runId": self.run_id, "side": "modern", "scenario": self.scenario,
            "businessDate": self.business_date,
            "businessDateNote": "the date the legacy run really used (MVS CURRENT-DATE of 2026-11-20); passed as the businessDate parameter of every modern run (D-020)",
            "codePage": "n/a (the modern system writes ASCII; the golden files were converted from EBCDIC 1047)",
            "startedUtc": self.started, "finishedUtc": now(),
            "gateway": GATEWAY, "user": "admin1 (ADMIN)", "seedState": "bash infra/scripts/seed.sh, 16 providers, before each run",
            "modernBuild": {"gitHead": head.stdout.strip(), "pemAppsImageAndCreated": docker.stdout.strip(),
                            "workingTree": "uncommitted changes of the lead (no commit in this task)"},
            "memoryPauseSeconds": self.mem_wait_s,
            "runs": self.runs, "flowNotes": list(flow_notes) + [SEED_NOTE], "problems": self.problems,
            "normalised": ["record separator only: the API returns one record per line (LF); the .ascii file holds the "
                           "same records back to back like the converted golden files; no field is changed"],
            "files": self.files,
        }
        with open(os.path.join(self.dir, "manifest.json"), "w", encoding="utf-8", newline="\n") as f:
            json.dump(manifest, f, indent=2)
            f.write("\n")
        self.log(f"manifest written, problems: {len(self.problems)}")


SEED_NOTE = (
    "SEED PREMISE: the SQL seed holds NULL where provider-master-seed.dat has the due date 99991231 for four providers "
    "(1005914035, 1005925213, 1005933068, 1005960046); the export writes an unset date as 00000000, so bytes 43-50 of those "
    "four records differ between the modern and the legacy BEFORE file (the other 12 records and all other bytes are equal).")

FLOW_NOTES = {
    "day": [
        "PRVDAY01 is three programs in sequence on one master; the modern system is event-driven: an accepted application "
        "(ApplicationValidated) registers a PENDING provider, which is screened (ProviderRegistered -> INITIAL screening) and "
        "tiered (ScreeningCompleted -> RiskTierAssigned) within seconds, before the screening run is started. The export "
        "'after-step010' is therefore taken after those events settled, not between PRVENR01 and PRVSCR01.",
        "The screening run (PRVSCR01) screens only providers with flag U that have no INITIAL screening yet: here the two seed "
        "providers 1005925213 and 1005933068; the new providers were screened by the event before the run, so SCRNOUT holds "
        "their rows only if a screening of the run exists.",
        "D-019: nothing becomes ACTIVE without an analyst approval. The legacy run did not make any new provider ACTIVE "
        "(they stay P flag U/P), so no approval was given by hand.",
        "D-003: the modern system does not reset the due date of every provider on each run; the due date is set at approval.",
        "STATE WHEN EACH FILE WAS WRITTEN, modern: APPERROR = written by the intake run itself, before any provider of the file "
        "was registered. after-step010 = after the intake run and after the events registered the validated applications as "
        "PENDING, screened them and tiered the passed ones. SCRNOUT = written by the screening run, which screened only the "
        "two seed providers with flag U (1005925213, 1005933068); the screenings of the new providers happened earlier, by "
        "events (logs/screenings-of-new-providers.json). after-step040 = after the screening run and the events it caused "
        "(screen flag, risk tier).",
        "STATE WHEN EACH FILE WAS WRITTEN, legacy (golden manifests): APPERROR = STEP010 (the load stopped at the first bad "
        "application; only the baseline run). SCRNOUT = STEP030, which screened every provider with flag U in one pass "
        "(the 3 new and the 2 seed providers: 20 rows). after-step010 = master right after PRVENR01; after-step040 = master "
        "after PRVRSK01 (tier and due date rewritten for every record). The baseline legacy run stopped after STEP010 (RC 4), "
        "so it has no SCRNOUT and no tier; the modern baseline run went on through screening and tiering.",
        "Modern screening verdicts come from the mocked sources (D-005): an application whose licence is unknown to the licence "
        "board mock (1006133288, SD20001) fails screening (flag F, no tier); the legacy stubs returned P for everyone.",
    ],
    "month": [
        "Run on the seed state, like the legacy PRVMTH01 (the legacy run did not run PRVDAY01 first).",
        "The GDG generation MMPRV.TMSIS.ARCHIVE(+1) is BLOCKED on the legacy side; the modern archive generation is recorded "
        "in logs/tmsis-extract-list.json.",
    ],
    "dir": ["Run on the seed state, like the legacy PRVDIR01 (ad-hoc lab deck)."],
    "san": ["Run on the seed state, like the legacy PRVSAN01 (ad-hoc lab deck). Status changes reach the provider master by "
            "events; the export is taken after it became stable."],
}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("scenario", choices=sorted(SCENARIOS))
    ap.add_argument("--business-date", default="2026-11-20")
    a = ap.parse_args()
    r = Runner(a.scenario, a.business_date)
    r.log(f"scenario {a.scenario}: job {r.job}, run id {r.run_id}, business date {a.business_date}")
    try:
        if a.scenario == "day-baseline":
            r.day("appinput.dat")
            r.finish(FLOW_NOTES["day"])
        elif a.scenario == "day-valid-only":
            r.day("appinput.dat")
            r.finish(FLOW_NOTES["day"])
        elif a.scenario == "month-seed":
            r.month()
            r.finish(FLOW_NOTES["month"])
        elif a.scenario == "dir-seed":
            r.directory()
            r.finish(FLOW_NOTES["dir"])
        else:
            r.sanctions()
            r.finish(FLOW_NOTES["san"])
    except BaseException as e:  # keep what exists and say why it stopped
        r.problems.append("script stopped: " + repr(e)[:300])
        r.finish(["the run stopped early; see problems"])
        raise


if __name__ == "__main__":
    main()
