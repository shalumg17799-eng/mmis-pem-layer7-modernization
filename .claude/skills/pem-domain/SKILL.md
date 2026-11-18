---
name: pem-domain
description: Medicaid Provider Enrollment and Management (PEM) business domain. Covers terms, statuses, risk tiers, screening sources, error codes, the 42 CFR 455 anchors and FHIR resources for the directory. Use when designing, testing or building any business behaviour.
---

# PEM business domain

## 1. Terms

| Term | Meaning |
|---|---|
| NPI | National Provider Identifier, 10 digits, the provider's key |
| Medicaid ID | State-assigned provider ID, 9 characters (legacy alternate key) |
| Taxonomy | 10-character provider specialty code; the first 3 characters drive the legacy risk tier |
| Provider type | Legacy codes `IN` individual, `GR` group, `FA` facility |
| Screening | Checking a provider (and owners) against licence boards and exclusion/death lists |
| LEIE | HHS-OIG List of Excluded Individuals/Entities |
| SAM | SAM.gov federal exclusions (debarment) |
| NPPES | The national NPI registry |
| SSA DMF | Social Security Death Master File |
| Revalidation | Re-screening every enrolled provider at least every 5 years |
| Sanction | An action on an enrolled provider: terminate, suspend, reinstate |
| T-MSIS | CMS data feed that states submit; legacy produces a provider extract for it |
| Directory | Public list of active providers |

## 2. Legacy status values (from PRVRECRD)

| Field | Values |
|---|---|
| Enrollment status | `P` pending, `A` active, `T` terminated, `D` denied (plus `S` suspended if D-006 is approved) |
| Screen flag | `U` unscreened, `P` pass, `F` fail |
| Risk tier | `L` limited, `M` moderate, `H` high |
| Screening result source | `NPPES`, `OIGLEIE`, `SAMGOV`, `LICBOARD`, `SSADMF`, `PECOS`; result `P`, `F`, `C` |
| Sanction action (SANCIN) | `T` terminate, `S` suspend (legacy sets DENIED; see D-006), `R` reinstate |

## 3. Legacy risk-tier rule (PRVRSK01, to be verified in Phase 2)

Taxonomy prefix `332` or `251` → High. Prefix `341`, `291` or `225` → Moderate.
Anything else → Limited. Implement it as a **data-driven table** in risk-service, not as
if/else code, so the business can change it.

## 4. Error codes (ERRCODES.cpy)

`E101` missing NPI · `E102` invalid type · `E103` duplicate NPI · `E104` missing taxonomy ·
`E201` excluded · `E202` licence expired · `E203` owner excluded · `E204` deceased ·
`E205` debarred. Keep these codes as stable API error identifiers.

## 5. Regulatory anchors (42 CFR Part 455, Subpart E)

| Section | What it requires (summary) |
|---|---|
| 455.410 | Providers must be screened and enrolled |
| 455.414 | Revalidate every provider at least every 5 years |
| 455.416 | Grounds to deny or terminate enrolment |
| 455.436 | Check the federal databases (SSA DMF, NPPES, LEIE, SAM); LEIE and SAM at least monthly |
| 455.450 | Screening by risk level: limited, moderate, high, each adding checks |

These are summaries. Before writing an acceptance criterion that cites a section, the
agent must quote the regulation from eCFR (ecfr.gov) in the story or the ADR, or mark the
citation as unverified. Never invent requirements beyond legacy behaviour and approved
decisions. Regulatory gaps become questions for the user.

## 6. Provider lifecycle (target, subject to the Phase 2 decisions)

```
Application SUBMITTED
  → VALIDATED (E101–E104 checks)  or REJECTED (errors listed)
  → provider PENDING, screen flag UNSCREENED
  → screening PASS / FAIL (per source; any FAIL = FAIL)
  → risk tier assigned (on PASS)
  → ACTIVE after analyst approval (D-019)  or DENIED
  → revalidation due = approval date + 5 years
  → sanctions: TERMINATED / SUSPENDED / reinstated ACTIVE
```

## 7. FHIR (directory-service and provider-service)

FHIR R4 resources (Da Vinci PDex Plan-Net style): `Practitioner` (individual),
`Organization` (group or facility), `PractitionerRole` (practitioner at an organization,
with specialty = taxonomy), `Location`. Expose them read-only from directory-service
under `/fhir`. Validate them with the HAPI FHIR validator in contract tests.
