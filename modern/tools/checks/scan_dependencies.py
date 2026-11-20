"""MAM-84 AC-5: known vulnerabilities of the third-party dependencies (Java run time and web UI).

Run from modern/:  kag/.venv/Scripts/python.exe tools/checks/scan_dependencies.py
Writes modern/docs/reports/dependency-scan.md. Exit code 0 when there is no HIGH or CRITICAL finding without a written
justification (modern/tools/checks/dependency-justifications.json: {"<vulnerability id>": "<reason>"}).

How: the scanner asks the public OSV database (https://api.osv.dev, the source that also feeds GitHub advisories) about
each open-source package and version. It sends package coordinates only (for example org.springframework:spring-web
6.2.x); the project's own modules are never sent.
 - Java: every third-party jar inside the deployable infra/local-runtime/build/libs/pem-local-runtime.jar (what really
   runs), mapped to its Maven coordinates through the Gradle module cache.
 - Web UI: every package of web-ui/package-lock.json (run time and build time; build-time ones are marked "dev").
This stands in for "OWASP dependency check and npm audit" of the AC: neither tool is installed on this machine (the OWASP
plugin needs the NVD download and an API key).
"""
import json
import math
import os
import sys
import urllib.request
import zipfile
from datetime import datetime
from pathlib import Path

MODERN = Path(__file__).resolve().parents[2]
JAR = MODERN / "infra/local-runtime/build/libs/pem-local-runtime.jar"
LOCK = MODERN / "web-ui/package-lock.json"
JUSTIFICATIONS = Path(__file__).with_name("dependency-justifications.json")
REPORT = MODERN / "docs/reports/dependency-scan.md"
OSV = "https://api.osv.dev/v1"


def java_packages() -> tuple[list[tuple[str, str]], list[str]]:
    """(Maven "group:artifact", version) of each third-party jar in the boot jar; and the jars that are not in the cache."""
    with zipfile.ZipFile(JAR) as z:
        jars = sorted({Path(n).name for n in z.namelist() if n.startswith("BOOT-INF/lib/") and n.endswith(".jar")})
    cache = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "caches/modules-2/files-2.1"
    index = {}
    for group in cache.iterdir() if cache.exists() else []:
        for artifact in group.iterdir():
            for version in artifact.iterdir():
                index.setdefault(f"{artifact.name}-{version.name}.jar", set()).add((group.name, artifact.name, version.name))
    found, unmapped = [], []
    for jar in jars:
        hits = index.get(jar)
        if not hits:
            unmapped.append(jar)
            continue
        for group, artifact, version in sorted(hits):
            if not group.startswith("gov.medicaid"):
                found.append((f"{group}:{artifact}", version))
    return sorted(set(found)), unmapped


def npm_packages() -> list[tuple[str, str, bool]]:
    lock = json.loads(LOCK.read_text(encoding="utf-8"))
    out = set()
    for path, meta in lock.get("packages", {}).items():
        if path and "node_modules/" in path and meta.get("version") and not meta.get("link"):
            out.add((path.split("node_modules/")[-1], meta["version"], bool(meta.get("dev"))))
    return sorted(out)


def post(url: str, payload: dict) -> dict:
    request = urllib.request.Request(url, json.dumps(payload).encode(), {"Content-Type": "application/json"})
    with urllib.request.urlopen(request, timeout=120) as response:
        return json.load(response)


def get(url: str) -> dict:
    with urllib.request.urlopen(url, timeout=60) as response:
        return json.load(response)


def cvss3(vector: str) -> float | None:
    """Base score of a CVSS v3.x vector."""
    try:
        m = dict(p.split(":") for p in vector.split("/")[1:])
        av = {"N": 0.85, "A": 0.62, "L": 0.55, "P": 0.2}[m["AV"]]
        ac = {"L": 0.77, "H": 0.44}[m["AC"]]
        scope = m["S"] == "C"
        pr = {"N": 0.85, "L": 0.68 if scope else 0.62, "H": 0.5 if scope else 0.27}[m["PR"]]
        ui = {"N": 0.85, "R": 0.62}[m["UI"]]
        c, i, a = ({"H": 0.56, "L": 0.22, "N": 0.0}[m[k]] for k in ("C", "I", "A"))
        iss = 1 - (1 - c) * (1 - i) * (1 - a)
        impact = 7.52 * (iss - 0.029) - 3.25 * (iss - 0.02) ** 15 if scope else 6.42 * iss
        if impact <= 0:
            return 0.0
        expl = 8.22 * av * ac * pr * ui
        raw = min(1.08 * (impact + expl), 10) if scope else min(impact + expl, 10)
        return math.ceil(raw * 10 - 1e-9) / 10
    except (KeyError, ValueError, IndexError):
        return None


def severity(vuln: dict) -> tuple[str, str]:
    """(LOW | MODERATE | HIGH | CRITICAL | UNKNOWN, how it was decided)."""
    best = None
    for s in vuln.get("severity", []):
        if s.get("type", "").startswith("CVSS_V3"):
            score = cvss3(s.get("score", ""))
            if score is not None and (best is None or score > best):
                best = score
    if best is not None:
        label = "CRITICAL" if best >= 9 else "HIGH" if best >= 7 else "MODERATE" if best >= 4 else "LOW"
        return label, f"CVSS {best}"
    named = (vuln.get("database_specific") or {}).get("severity")
    if named:
        return named.upper(), "advisory"
    return "UNKNOWN", "no score"


def main() -> int:
    java, unmapped = java_packages()
    npm = npm_packages()
    queries = [{"package": {"ecosystem": "Maven", "name": n}, "version": v} for n, v in java]
    queries += [{"package": {"ecosystem": "npm", "name": n}, "version": v} for n, v, _ in npm]
    labels = [("Maven", n, v, False) for n, v in java] + [("npm", n, v, dev) for n, v, dev in npm]
    hits = []
    for start in range(0, len(queries), 500):
        results = post(f"{OSV}/querybatch", {"queries": queries[start:start + 500]})["results"]
        for label, result in zip(labels[start:start + 500], results):
            for v in result.get("vulns", []) or []:
                hits.append((label, v["id"]))
    details = {vid: get(f"{OSV}/vulns/{vid}") for vid in sorted({vid for _, vid in hits})}
    justified = json.loads(JUSTIFICATIONS.read_text(encoding="utf-8")) if JUSTIFICATIONS.exists() else {}

    rows, open_high = [], 0
    for (eco, name, version, dev), vid in sorted(hits):
        d = details[vid]
        label, how = severity(d)
        fixed = sorted({e.get("fixed") for a in d.get("affected", []) if a.get("package", {}).get("name") == name
                        for r in a.get("ranges", []) for e in r.get("events", []) if e.get("fixed")})
        aliases = [a for a in d.get("aliases", []) if a.startswith("CVE-")]
        reason = justified.get(vid, "")
        if label in ("HIGH", "CRITICAL", "UNKNOWN") and not reason:
            open_high += 1
        rows.append((label, eco, name, version, "dev" if dev else "run time", vid + (" / " + aliases[0] if aliases else ""), how,
                     (d.get("summary") or "")[:110], ", ".join(fixed[:4]), reason))
    order = {"CRITICAL": 0, "HIGH": 1, "UNKNOWN": 2, "MODERATE": 3, "LOW": 4}
    rows.sort(key=lambda r: (order.get(r[0], 5), r[1], r[2]))
    counts = {}
    for r in rows:
        counts[r[0]] = counts.get(r[0], 0) + 1

    lines = ["# Dependency vulnerability scan (MAM-84 AC-5)", "",
             f"Generated {datetime.now():%Y-%m-%d %H:%M} by `modern/tools/checks/scan_dependencies.py` from the OSV database (https://osv.dev); do not edit by hand.",
             "Stands in for the OWASP dependency check and npm audit named in the AC (neither is installed here). Only open-source",
             "package names and versions are sent; the project's own modules are not.", "",
             f"- Java run time: {len(java)} third-party libraries inside `infra/local-runtime/build/libs/pem-local-runtime.jar` (built {datetime.fromtimestamp(JAR.stat().st_mtime):%Y-%m-%d %H:%M})",
             f"- Web UI: {len(npm)} packages of `web-ui/package-lock.json` ({sum(1 for *_, d in npm if not d)} run time, {sum(1 for *_, d in npm if d)} build time)",
             f"- Findings: {len(rows)} ({', '.join(f'{k} {v}' for k, v in sorted(counts.items(), key=lambda kv: order.get(kv[0], 5))) or 'none'})",
             f"- HIGH, CRITICAL or unscored findings without a written justification: **{open_high}**", ""]
    if unmapped:
        lines += [f"Jars not looked up ({len(unmapped)}): the project's own modules and jars that are not in the Gradle module cache: "
                  + ", ".join(unmapped[:40]) + (" ..." if len(unmapped) > 40 else ""), ""]
    if rows:
        lines += ["| Severity | Ecosystem | Package | Version | Use | Id | Basis | Summary | Fixed in | Justification |", "|---|---|---|---|---|---|---|---|---|---|"]
        lines += ["| " + " | ".join(str(c).replace("|", "/") for c in r) + " |" for r in rows]
    else:
        lines.append("No known vulnerability in any scanned package.")
    REPORT.write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")
    print(f"scanned {len(java)} Java libraries and {len(npm)} npm packages; findings {len(rows)} {counts}; "
          f"HIGH/CRITICAL/unscored without justification: {open_high}; jars not looked up: {len(unmapped)}")
    print(f"wrote {REPORT.relative_to(MODERN.parent).as_posix()}")
    return 0 if open_high == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
