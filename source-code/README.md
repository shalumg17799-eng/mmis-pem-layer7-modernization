# MMIS Provider Enrollment Mainframe Demo — Tier 4

**Domain:** Medicaid / MMIS Layer-7 — Provider Enrollment & Management (PEM)
**Emulator:** Hercules / TK5 / MVS 3.8j
**CAST Usage:** Live reverse-engineering
**Demo Format:** Mixed (live CAST + scripted code walkthrough)
**Modernization Target:** Modular FHIR/microservices on AWS/Azure/GCP (see `../../modern/`)

> This is the deliberately-aged "before" estate. The seeded legacy anti-patterns below are
> the point — they are what CAST Imaging flags and what the modern rebuild eliminates.
> The business scope is defined in `../../USE-CASE-PEM.md`.

---

## File Manifest

```
MMIS-PROVIDER-DEMO-TIER4/
├── COPYBOOKS/    5 files — shared data structures
├── BATCH/        8 files — batch COBOL programs
├── ONLINE/       1 file  — CICS provider inquiry (PINQ)
├── JCL/          2 files — job streams
│   └── PROCS/    1 file  — cataloged procedure
├── VSAM/         2 files — IDCAMS definitions
├── DB2/          1 file  — DDL
└── REXX/         1 file  — SDSF automation
```

## What Runs on Hercules vs. CAST-Only

| File | Runs on Hercules (lab stub) | CAST Analyzes |
|------|:---:|:---:|
| All BATCH/*.cbl | ✅ (as `*-LAB.jcl`) | ✅ |
| JCL/*.jcl + PROCS | ✅ | ✅ |
| VSAM/*.jcl (IDCAMS) | ✅ | ✅ |
| REXX/PRVAUTOM.rexx | ✅ | ✅ |
| ONLINE/PRVCICS1.cbl (CICS) | ❌ Reference only | ✅ |
| DB2/PRVDDL01.sql | ❌ Reference only | ✅ |

---

## Anti-Pattern Index — What CAST Will Flag

| Anti-Pattern | File | Description |
|---|---|---|
| **Paragraph fall-through** | `BATCH/PRVENR01.cbl` | `INVALID-APP` label has no `EXIT` — applications that FAIL validation still fall through into `WRITE-PROVIDER-RECORD` and get enrolled as PENDING. A provider enrolled despite failing prerequisites = improper-payment exposure. Bug since 1998. |
| **Dead code (flag never set)** | `BATCH/PRVSCR01.cbl` | `WS-RECIPROCITY-ENABLED` hardcoded `N`; `CHECK-MEDICARE-RECIPROCITY` (PECOS reliance per 42 CFR 455.410) never reached. Feature specified 2014, flag never wired. |
| **ALTER statement** | `BATCH/PRVRSK01.cbl` | `ALTER APPLY-TIER-PARA TO PROCEED TO HIGH-TIER/MODERATE-TIER` — runtime-rewritten GO TO target invisible to static analysis. |
| **2-digit year (YY) arithmetic** | `BATCH/PRVRSK01.cbl` | Revalidation due date = `YY + 5` with a `< 70` pivot. Breaks for any 5-year window crossing 2070 (42 CFR 455.414 revalidation mis-sorts). |
| **Dynamic CALL (target in variable)** | `BATCH/PRVSAN01.cbl` | `CALL WS-NOTICE-PGM` — notice program name built at runtime; CAST shows call target as `?`. |
| **Cross-app VSAM coupling** | `BATCH/PRVSAN01.cbl` | PEMAPP2 (Sanctions) opens & REWRITEs `PRVMSTR`, the Provider Master KSDS **owned by PEMAPP1**, with no API boundary. **The key modernization blocker.** |
| **YYDDD Julian date arithmetic** | `BATCH/PRVTMS01.cbl` | T-MSIS period/enrollment age use 2-digit-year Julian dates; cross-year comparisons sort wrong → a common T-MSIS data-quality reject. |
| **Dead code (orphaned + flag off)** | `BATCH/PRVDEAD1.cbl` | No JCL references it (orphan node); and `WS-PAPER-FP-ENABLED` hardcoded `N` (paper fingerprint cards discontinued 2019 for LiveScan). Doubly dead. |
| **Nested copybook** | `COPYBOOKS/WRKSTORE.cpy` | Includes both `SCRNREC` and `ERRCODES`. Changing `SCRNREC` forces recompile of every WRKSTORE user. |
| **REDEFINES across 3 provider types** | `COPYBOOKS/PRVRECRD.cpy` | Individual / Group / Facility redefine the same 80-byte `PRV-VARIANT`. 6+ programs share it. |
| **Symbolic-parameter override** | `JCL/PROCS/PRVSCRPR.jcl` | `&HLQ`/`&DTE` overridden per call site; effective DSNs differ and are hard to trace. |
| **Dual system of record** | `DB2/PRVDDL01.sql` + VSAM | Provider identity lives in BOTH DB2 and the VSAM master; they drift. The MDM golden record resolves this. |

---

## Application Boundaries (CAST Will Surface These)

### PEMAPP1 — Provider Enrollment & Screening
**Owns:** `MMPRV.PROVIDER.MASTER.KSDS` (a.k.a. `PRVPROVN`), `MMPRV.SCREEN.RESULTS.KSDS`, DB2 `MMPRV.PROVIDER`
**Programs:** PRVENR01, PRVSCR01, PRVRSK01, PRVREV01, PRVTMS01, PRVDEAD1
**CICS txn:** PINQ (PRVCICS1)

### PEMAPP2 — Sanctions & Directory
**Owns:** `MMPRV.SANCTION.MASTER.KSDS`
**Shares (cross-app):** `MMPRV.PROVIDER.MASTER.KSDS` (owned by PEMAPP1)
**Programs:** PRVSAN01, PRVDIR01

**The cross-app coupling is the key modernization blocker.** PEMAPP2 cannot be migrated to a
cloud Sanctions/Directory service without either (1) PEMAPP1 exposing a Provider API, or
(2) migrating both applications at once. This is the provider-data analog of the
"terminated-but-still-paid provider" improper-payment gap.

---

## CAST Loading Instructions

1. Create a CAST Imaging application: `MMIS-PROVIDER-DEMO`.
2. Technology root **COBOL** → `BATCH/` and `ONLINE/`.
3. Technology root **JCL** → `JCL/` (including `PROCS/`).
4. Technology root **REXX** → `REXX/`.
5. Copybooks path → `COPYBOOKS/`.
6. Add a **second application** `MMIS-SANCTIONS-DEMO` with `BATCH/PRVSAN01.cbl` (+ PRVDIR01).
7. Run analysis — CAST auto-detects the cross-application VSAM coupling on the provider master.

**Key CAST views for the demo:**
- Call graph from `PRVRSK01` → shows the unresolved ALTER target.
- Dependency map of the Provider Master KSDS → PEMAPP1 + PEMAPP2 both writing it.
- Dead-code report → finds PRVDEAD1 (orphan) and the dead paragraphs in PRVSCR01/PRVDEAD1.
- Object-level coupling → `PRVRECRD` referenced by 6+ programs.

---

## Hercules Run Order

```
1. PRVVSM01 — Define Provider Master KSDS + AIX
2. PRVVSM02 — Define Screening + Sanction KSDS
3. PRVDDL01 — DB2 provider schema (reference)
4. PRVDAY01 — Daily enrollment (PRVENR01 → SORT → PRVSCR01 → PRVRSK01)
5. PRVSAN01 — Apply sanctions/terminations (PEMAPP2 cross-app)
6. PRVMTH01 — Month-end (PRVREV01 revalidation + PRVTMS01 T-MSIS)
```

---

## Proposed Microservice Decomposition (the "after")

```
Mainframe PEMAPP1 (Enrollment & Screening)  →  Cloud Target  (modern/src/*)
──────────────────────────────────────────────────────────────────────────
CICS PINQ (PRVCICS1)            →  provider self-service portal + analyst console
PRVENR01 + PRVDAY01 pipeline    →  enrollment-orchestrator  (workflow/BPMN)
PRVSCR01 (+ external feeds)     →  screening-engine         (parallel source calls)
PRVRSK01                        →  rules-risk-engine        (Limited/Moderate/High)
PRVREV01                        →  revalidation & continuous-monitoring service
PRVTMS01                        →  T-MSIS extract (EDW / data lake)
DB2 PROVIDER + VSAM master      →  provider-mdm golden record (Aurora) + FHIR API

Mainframe PEMAPP2 (Sanctions & Directory)   →  Cloud Target
──────────────────────────────────────────────────────────────────────────
PRVSAN01                        →  sanctions/exclusion service
PRVDIR01                        →  provider-directory service (FHIR PractitionerRole)
                                   Both consume the provider-mdm API
                                   (eliminating the cross-app VSAM coupling)
```

**Migration sequence:** provider-mdm → screening-engine → enrollment-orchestrator →
rules-risk-engine → sanctions/directory → decommission VSAM master.

---

*Tier-4 PEM legacy estate — Radiant Digital — 2026. Scope: `../../USE-CASE-PEM.md`.*
