# Open business questions

## Q-001. Does "migrate as is" include reproducing the legacy defects? (raised 2026-11-18, status: ANSWERED 2026-11-18, REVISED)

**Final answer (user, 2026-11-18): Q-001 is NOT bug-for-bug.** The earlier "yes" (recorded as option A) is **superseded**.
Every item D-001 to D-020 in `.claude/skills/pem-legacy-estate/SKILL.md` section 4 takes its **recommended default**
(FIX, DROP, REPLACE or PRESERVE exactly as listed there). The four open items are decided as:
D-006 = a new SUSPENDED status; D-007 = the record is 130 bytes; D-008 = keep IN/GR/FA plus a mapping table;
D-019 = an analyst approval sets a provider to ACTIVE. Each is logged as "accepted recommended default, user, 2026-11-18".
The COBOL itself is never edited (`source-code/` stays read-only). The options table below is kept as history.

The user asked for the application to be migrated "as it is, no changes in the COBOL". The COBOL stays untouched
(`source-code/` is read-only). What is not yet decided is the behaviour of the new Java services where the legacy
code is defective (see `CLAUDE.md` sections 6 and 7; 12 seeded anti-patterns, several with compliance impact:
invalid applications reaching the master, 2-digit-year revalidation dates after 2065, stubbed screening).

| Option | Meaning | Effect |
|---|---|---|
| A (recommended default) | Bug-for-bug: reproduce legacy behaviour exactly, including defects. Each defect is logged in `defect-register.md` as "preserved by user decision". | Phase 9 parity against the Hercules outputs can pass; defects stay visible and can be fixed later by an explicit decision. |
| B | Same business behaviour, but fix the defects now. | Parity will show intended differences; each fix needs its own approved decision. |
| C | Bug-for-bug by default, with a per-defect switch to the corrected behaviour. | Parity passes; fixes can be turned on without a rewrite; more code and tests. |

Decide per defect in Phase 2 (the legacy-analyst lists them with line evidence). A single answer for all is fine too.

## Q-002. Which Neo4j database holds the PEM graph? (raised 2026-11-18, status: ANSWERED 2026-11-18: option A, `medicaid-kag`; `medicade-kag` was a typo)

You gave `NEO4J_DATABASE=medicade-kag`. That database does not exist on the KAG instance. An **empty** database
`medicaid-kag` (online, read-write, 0 nodes, no constraints, created 2026-11-18 12:03) does, and looks like the one made for
this project. The other similar names on the instance (`nfcukag`, `rain-kag`) belong to other work and are not touched.

| Option | Effect |
|---|---|
| **A (recommended)**: use `medicaid-kag` | No new database; fix `NEO4J_DATABASE=medicaid-kag` in `.env`. |
| B: create a new database literally named `medicade-kag` | Leaves `medicaid-kag` unused. |

**Answer (user, 2026-11-18): use `medicaid-kag`; `medicade-kag` was a typo.** Fix `NEO4J_DATABASE` in `.env`.
The graph is rebuilt entirely by scripts, so switching later is a re-run of `load_all.sh`.

## Q-003. Small additions to the graph ontology (raised 2026-11-18, status: ANSWERED 2026-11-18: recommended default accepted by the user)

The kag-ontology skill has no home for four things the load needed. Applied by default, all additive (no label or key changed):

1. The AWS test plan: `TestCase` with `level='aws-plan'` and a `COVERS` relationship to the `Job`.
2. A COPY inside a copybook (`WRKSTORE` includes `SCRNREC` and `ERRCODES`): `INCLUDES` also from a `Copybook`.
3. `HAS_ANTIPATTERN` also from a `Copybook`, `Job` or `Dataset` (AP-09, AP-10, AP-11, AP-12 are not programs).
4. `Job.role` (`pipeline`, `vsam-definition`, `proc`), so the skill's "2 jobs plus the proc" check still holds.
5. `AFFECTS` from a Defect also to a `Job`, `Copybook` or `Dataset` (D-009, D-014, D-020 are not about one program).
6. Question `status` uses the kit's term `DEFERRED` when a default was applied (Phase 2 command, step 6).
7. `Table -[:HAS_COLUMN]-> Column` (the ontology has both labels but no link between them).
8. `Record -[:MAPS_TO]-> Column` for the three flat file records with no sub-fields (APPINPUT, APPERROR, TMSISOUT).
9. `BusinessRule.owner = 'DROPPED'` for rules that rule-to-service.csv drops (instead of an OWNED_BY link).

**Recommended answer: accept all nine.** The alternative is to drop them and leave those facts out of the graph.

## Q-004. Where does per-story working state live? (raised 2026-11-18, status: ANSWERED 2026-11-18: recommended default accepted by the user)

You asked for no separate folder for temporary files. The kit kept per-story state in a hidden `.work/<KEY>/` folder
(`red-report.md`, `red-commit`, `trace.json`). Applied by default: those files are audit records, so they live in
`modern/docs/reports/stories/<KEY>/`, committed with the story. Only the path text changed in 8 files under `.claude/`.

**Recommended answer: accept.** The alternative is to restore `.work/` (git-ignored) and keep them out of the repo.

## Q-005. What do the risk-tier taxonomy prefixes mean? (raised 2026-11-18 in Phase 2, status: ANSWERED 2026-11-18: recommended default accepted by the user)

Why it matters: PRVRSK01 tiers providers by the first 3 characters of the taxonomy (332, 251 High; 341, 291, 225 Moderate; anything else
Limited). AWS's rules attach meanings (substance-abuse facility, home-health agency, laboratory, therapy) that are **not in the source**.
Affected rules: R-PRVRSK01-cbl-00022, -00023, -00026, -00027, -00028.
Options: A) keep the legacy prefix table exactly, as data, with no meanings attached; B) confirm AWS's meanings and add them as labels.
**Recommended and applied: A.** The risk-service holds the table as data (pem-domain skill, section 3), so labels can be added later without code.

## Q-006. Should a sanction depend on the provider's current status? (raised 2026-11-18, status: ANSWERED 2026-11-18: recommended default accepted by the user; defect D-027)

Why it matters: the legacy applies terminate, suspend and reinstate whatever the current status is, so a reinstate can make a DENIED or
PENDING provider ACTIVE. Affected rules: R-PRVSAN01-cbl-00004, -00006, -00016, -00034; R-PRVSAN01-ADD-04.
Options: A) preserve the legacy rule (apply as written); B) allow reinstate only from TERMINATED or SUSPENDED, and terminate/suspend only from
ACTIVE or PENDING.
**Recommended and applied: A** (no new business rule without your decision). With D-006 a suspend now sets SUSPENDED; with D-026 unknown codes are rejected.

## Q-007. What should the T-MSIS extract contain? (raised 2026-11-18, status: ANSWERED 2026-11-18: recommended default accepted by the user)

Why it matters: PRVTMS01 reports only ACTIVE providers, with status hard-coded A, a 120-byte row of which 30 bytes carry data, and a
"period" that is the run date in YYDDD form, not a reporting month. The real CMS T-MSIS provider file format is not in the estate.
Affected rules: R-PRVTMS01-cbl-00043; R-PRVTMS01-ADD-04, -05, -07.
Options: A) preserve the legacy extract exactly in the legacy-format adapter (D-015) and keep ISO dates internally; B) build the real CMS
T-MSIS provider file (needs the CMS specification from the business).
**Recommended and applied: A.** B can be a later story once the specification is provided.

## Q-008. What should the provider directory publish? (raised 2026-11-18, status: ANSWERED 2026-11-18: recommended default accepted by the user)

Why it matters: the legacy directory row has only Medicaid ID, NPI, type, risk tier and a 60-byte name (D-028); a FHIR PractitionerRole
also wants the specialty (taxonomy), and addresses and phones that the legacy master does not hold. Affected rules: R-PRVDIR01-cbl-00005,
-00006; R-PRVDIR01-ADD-01, -02, -04.
Options: A) keep the legacy flat-file row for the MCOs (with the name composed properly, D-028) and expose FHIR resources built only from
fields the legacy master holds (NPI, Medicaid ID, type, name, taxonomy as specialty, licence); B) add address, phone and network data
(new data the business must source).
**Recommended and applied: A.** Same filter as legacy: active providers only.

## Q-009. What is the layout of the enrolment intake record? (raised 2026-11-18, status: ANSWERED 2026-11-18: recommended default accepted by the user; defect D-025)

Why it matters: APPINPUT is a 200-byte record that PRVENR01 reads INTO the 130-byte PRVRECRD layout; bytes 131-200 are ignored and no
other intake layout exists. Affected rules: R-PRVENR01-cbl-00019; R-PRVENR01-ADD-01.
Options: A) bytes 1-130 are PRVRECRD, bytes 131-200 reserved, exactly as the legacy reads it; B) the business supplies the real intake layout.
**Recommended and applied: A.** In the new system applications arrive through the portal and the enrolment API; the legacy-format
reader exists only to compare with legacy runs (Phase 9).

## Q-010. Do you accept the defaults applied to the new defects D-021 to D-029? (raised 2026-11-18, status: ANSWERED 2026-11-18: recommended default accepted by the user)

Why it matters: Phase 2 found nine defects that were not in the estate skill. Each has a recommended default, already applied (see
`defect-register.md`): D-021 FIX, D-022 REPLACE, D-023 FIX, D-024 FIX, D-025 see Q-009, D-026 FIX, D-027 see Q-006, D-028 FIX, D-029 FIX.
**Recommended: accept all.** Reply per ID to change any of them.

## Q-011. Which seven end-to-end journeys? (raised 2026-11-18 in Phase 3, status: ANSWERED 2026-11-18: recommended default accepted by the user)

Why it matters: the ui-developer brief asks for "the 7 end-to-end journeys" but no file in the kit lists them; Phase 8 turns them into Playwright tests.
Options: A) the seven in `modern/docs/ux/journeys.md` (enrolment, rejection, failed screening and denial, analyst inquiry, sanction and reinstatement,
revalidation, month-end extracts and public directory); B) a list you provide.
**Recommended and applied: A.**

## Q-012. How long must data be kept? (raised 2026-11-18 in Phase 3, status: ANSWERED 2026-11-18: recommended default accepted by the user)

Why it matters: the architect brief marks data retention as an OPEN question; the estate defines no retention period.
Options: A) delete or archive nothing until the business sets a policy; B) a retention period you give (per data type).
**Recommended and applied: A** (`modern/docs/architecture/nfr.md`).

## Q-013. Jira project MAM has no "In Review" status: what marks a story as built and waiting for review? (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

MAM's workflow has To Do, In Progress and Done only (board 1831: three columns).

Options: A) keep it In Progress with the label `in-review` and the report comment; B) the user adds an In Review status/column to board 1831 and the agents move stories there.

**Recommended: A now, B when the column exists** (the agents switch automatically).

## Q-014. Where does the shared event, security and observability code live? (raised 2026-11-18 in Phase 5, status: DEFAULT APPLIED - revisit)

Options: A) one library `libs/service-kit` (ADR-012); B) separate `libs/events` plus other small libraries; C) copy the code into each service.

**Recommended: A** (one auto-configured library, no business rules in it; plt-events AC-8 aligned to it).

## Q-015. (group A) Q-A1. Provider effective date at registration. ApplicationValidated carries no effective date, but every provider record needs one (legacy PRV-EFFECTIVE-DATE, bytes 35-42, read from the intake record, D-025). What is a newly registered prov... (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

Q-A1. Provider effective date at registration. ApplicationValidated carries no effective date, but every provider record needs one (legacy PRV-EFFECTIVE-DATE, bytes 35-42, read from the intake record, D-025). What is a newly registered provider's effective date?

Options: A) The registration date (the date provider-service processes the event), kept unchanged afterwards | B) The approval date: the registration date until the analyst approves, then the approval date | C) A new effective-date field on the application and on ApplicationValidated, supplied by the applicant or the legacy file (contract change, needs approval)

**Recommended: A**

## Q-016. (group A) Q-A2. Which states allow approve and deny, and with which error? The decideProvider summary says an analyst decides on 'a pending provider whose screening passed', but journey J3 has the analyst deny a provider whose screening FAILED, and D... (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

Q-A2. Which states allow approve and deny, and with which error? The decideProvider summary says an analyst decides on 'a pending provider whose screening passed', but journey J3 has the analyst deny a provider whose screening FAILED, and D-019 only says approval follows a Pass screening.

Options: A) Both decisions need status PENDING (otherwise 409); APPROVE also needs screen flag P and DENY needs flag P or F (otherwise 422; an unscreened provider U cannot be decided). The decideProvider summary is reworded in a later contract revision (needs approval) | B) Both decisions only when the flag is P (J3 would not work as written) | C) DENY on any PENDING provider, including an unscreened one

**Recommended: A**

## Q-017. (group A) Q-A3. Due date for an approval or completed revalidation on 29 February. The legacy copies month and day and can produce the impossible date 20290229 (R-PRVRSK01-ADD-02). (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

Options: A) The last day of February of the due year (2028-02-29 gives 2033-02-28, what java.time plusYears(5) returns, ADR-009); never later than five years | B) 1 March of the due year (2033-03-01)

**Recommended: A**

## Q-018. (group A) Q-A4. What does a re-screening of an ACTIVE provider change? D-003 sets the due date at each completed revalidation and overview main flow 4 says provider-service sets it on ScreeningCompleted (REVALIDATION), but no rule says from which dat... (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

Q-A4. What does a re-screening of an ACTIVE provider change? D-003 sets the due date at each completed revalidation and overview main flow 4 says provider-service sets it on ScreeningCompleted (REVALIDATION), but no rule says from which date, how other services learn the new date, or what a failure does. Group C raised the same question for revalidation-service (C-10) with the same default.

Options: A) PASS in a REVALIDATION screening: due date = the date provider-service processes it + 5 years, announced as ProviderStatusChanged with from = to = A and the new revalidationDueDate; a FAIL (or any MANUAL result) changes only the screen flag (P or F), never the status or the due date | B) A FAIL also moves the provider to SUSPENDED or TERMINATED automatically (a new business rule) | C) A new event for due-date changes instead of A to A (contract change, needs approval)

**Recommended: A**

## Q-019. (group A) Q-A5. Must a risk tier exist before an analyst can approve? RiskTierAssigned and the approval come from different flows, so the tier can arrive after the screening result. (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

Options: A) No: approval needs status PENDING and screen flag P only; the tier is saved whenever it arrives | B) Yes: approval is refused with 422 until a tier is recorded

**Recommended: A**

## Q-020. (group A) Q-A6. What does a sanction's effective date do on the provider record? The status-change request carries an effective date and a reason; the provider record also has an end date (DB2 END_DATE) that no legacy program sets. (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

Options: A) provider-service validates the date, records it with the reason and sanction id in the audit entry, and leaves endDate untouched (sanctions-service keeps the sanction history, D-018) | B) TERMINATE sets endDate to the effective date and REINSTATE clears it | C) TERMINATE and SUSPEND set endDate and REINSTATE clears it

**Recommended: A**

## Q-021. (group A) Q-A7. Who receives a notice and at which address? The missing PRVNT* programs have only the header hint 'notifies downstream (Claims, MCO)'; the provider master holds no email address; the journeys expect the notice in Mailpit and in the pr... (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

Q-A7. Who receives a notice and at which address? The missing PRVNT* programs have only the header hint 'notifies downstream (Claims, MCO)'; the provider master holds no email address; the journeys expect the notice in Mailpit and in the provider's portal; a notice needs an NPI.

Options: A) The provider only, by email to a synthetic address derived from the NPI (1003000111@providers.pem.local, captured by Mailpit); Claims and MCO are not notified (no interface exists in the estate); a rejected application without an NPI (E101) has nobody to address and gets no notice | B) Add a contact email to the application and the provider record (contract change, needs approval) | C) As A, and also notify Claims and MCO (needs an interface specification from the business)

**Recommended: A**

## Q-022. (group A) Q-A8. Which event produces which notice, and who writes the wording? (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

Options: A) SanctionApplied T, S, R gives TRM, SUS, RST (any other action: see Q-A10); ProviderStatusChanged P to A gives APPROVED and P to D gives DENIED, every other transition gives no notice (sanction notices come from SanctionApplied, so no provider gets two); ApplicationRejected gives REJECTED; RevalidationDue gives REVALIDATION_DUE. The subjects 'Enrolment approved' (J1) and 'Application rejected' (J2) are fixed; all other subjects and all bodies (including which sanction details appear) are placeholder wording seeded as data for the business to replace; a body always contains the NPI. Known side effect while D-027 stands: reinstating a PENDING provider (P to A) also triggers APPROVED | B) Add a cause field (APPROVAL, DENIAL, SANCTION) to ProviderStatusChanged so approvals and reinstatements can be told apart (contract change, needs approval) | C) The business supplies the wording before the story is built

**Recommended: A**

## Q-023. (group A) Q-A9. ExtractProduced has no NPI and no recipient. The contract makes notification-service its consumer and lists an EXTRACT_READY template, but a notice needs an NPI (Notice.npi is required) and no rule says who is told that an extract is ... (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

Q-A9. ExtractProduced has no NPI and no recipient. The contract makes notification-service its consumer and lists an EXTRACT_READY template, but a notice needs an NPI (Notice.npi is required) and no rule says who is told that an extract is ready.

Options: A) Consume the event (idempotently) and send nothing until the business names a recipient; the template stays listed but unused | B) Email a configured operations mailbox and do not list it in /api/v1/notices (needs a mailbox address and wording) | C) Remove the consumer from the contract (contract change, needs approval)

**Recommended: A**

## Q-024. (group A) Q-A10. What happens to a sanction with an action outside T, S and R? The legacy sends the general notice PRVNTGEN for any other action (R-PRVSAN01-cbl-00049), but D-026 (FIX) rejects unknown action codes and SanctionApplied.action only allo... (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

Q-A10. What happens to a sanction with an action outside T, S and R? The legacy sends the general notice PRVNTGEN for any other action (R-PRVSAN01-cbl-00049), but D-026 (FIX) rejects unknown action codes and SanctionApplied.action only allows T, S, R, so such an event can only reach notification-service through a defect upstream. The contract still lists the GEN template.

Options: A) Treat the event as invalid: log an error with the event id and the action, send no email, publish no NoticeSent, acknowledge it and carry on; GEN stays seeded and listed but is not sent for any event (the decision D-026 wins over the legacy rule, Q-001) | B) Keep the legacy fallback: send the GEN notice for any other action | C) As A, and remove GEN from the template list (contract change, needs approval)

**Recommended: A**

## Q-025. (group B) Q-B1. What does POST /api/v1/applications return when validation fails, and how does the portal find the stored rejected application? (affects enr-submit AC-2 to AC-7 and enr-status). The contract lists both '201 Application' and '422 Busin... (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

Q-B1. What does POST /api/v1/applications return when validation fails, and how does the portal find the stored rejected application? (affects enr-submit AC-2 to AC-7 and enr-status). The contract lists both '201 Application' and '422 Business validation failed', and journey J2 needs a stored REJECTED application to show on the status page.

Options: A) 422 problem+json listing every failed check in errors; the rejected application is stored as REJECTED and found with GET /api/v1/applications?status=REJECTED; no contract change | B) 201 with the Application body (status REJECTED, errors, applicationId); no contract change, but the contract's 422 is then unused | C) 422 as in A plus the applicationId in the problem body; needs a contract change (user approval)

**Recommended: A**

## Q-026. (group B) Q-B2. How is an application 'own' to a PROVIDER user (enr-status AC-1, AC-2)? Nothing in the architecture ties a Keycloak PROVIDER user to an NPI. (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

Options: A) The authenticated user who submitted it (column submitted_by in the enrollment data model); applications loaded from a legacy file belong to the user or service account that started the run, so PROVIDER users never see them | B) An NPI claim in the Keycloak token (needs a realm and token design that does not exist yet)

**Recommended: A**

## Q-027. (group B) Q-B3. What is a duplicate NPI (E103) and how is it counted (enr-submit AC-5, enr-intake-run-2 AC-3)? The legacy detected duplicates only when writing the master, wrote no error row, and counted them as skipped; D-016 makes E103 a validation... (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

Q-B3. What is a duplicate NPI (E103) and how is it counted (enr-submit AC-5, enr-intake-run-2 AC-3)? The legacy detected duplicates only when writing the master, wrote no error row, and counted them as skipped; D-016 makes E103 a validation result; enrollment-service must not read provider data.

Options: A) A duplicate is an NPI that already has a VALIDATED application in the service's own data (an earlier REJECTED one does not count). It is rejected with E103, gets one error row like any rejected application, and is counted as skipped (not errored) in a run, so the counts keep the legacy meaning and add up | B) As A, but counted as errored; the skipped count is then zero except for re-runs (Q-B11) | C) As A, but also ask provider-service whether the NPI is registered (adds a service dependency that is not in the design) | D) As the legacy: skipped silently with no E103 and no error row (contradicts D-016)

**Recommended: A**

## Q-028. (group B) Q-B4. What does the APPERROR file hold for an application that fails more than one check, and what is in bytes 41-250 (enr-intake-run AC-6)? The legacy row has 40 meaningful bytes and leaves bytes 41-250 undefined; D-001 asks for exactly on... (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

Q-B4. What does the APPERROR file hold for an application that fails more than one check, and what is in bytes 41-250 (enr-intake-run AC-6)? The legacy row has 40 meaningful bytes and leaves bytes 41-250 undefined; D-001 asks for exactly one error row per rejected application.

Options: A) One 250-byte row per rejected application (not per failed check): bytes 1-40 as the legacy row (NPI, ' TYPE:', type code, ' ERR:VALIDATION-FAILED'), bytes 41-250 spaces; the individual codes are in the application and in ApplicationRejected | B) One row per failed check, with the E-code in bytes 41-44 | C) One row per application with all its E-codes listed from byte 41

**Recommended: A**

## Q-029. (group B) Q-B5. What happens when the NPI is present but is not 10 digits (enr-submit AC-2)? The contract says the NPI is validated as 'present (E101), 10 digits'; the legacy checks only for blank. (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

Options: A) Error E101 on npi with a message saying the NPI must be 10 digits; the application is stored as REJECTED | B) 400 invalid request with no error code, and nothing stored | C) Accept it (legacy behaviour; only a blank NPI fails)

**Recommended: A**

## Q-030. (group B) Q-B6. What is the event subject when the NPI is missing or malformed (E101)? The event envelope requires a 10-digit NPI as subject and message key (enr-submit AC-2, enr-intake-run AC-4). (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

Options: A) Publish no ApplicationRejected for an E101 rejection; the error stays in the application and in the APPERROR file | B) Publish it with a placeholder subject such as 0000000000 (invented value; needs a contract note) | C) Relax the Envelope.subject pattern (contract change, user approval)

**Recommended: A**

## Q-031. (group B) Q-B7. What does each feed provider-type code map to (D-008; enr-submit AC-7)? The mapping content exists nowhere: the test feed lists IND, GRP, HOSP, AMB, HOS, LAB, PT, DME, HHA with only a short description each, and journey J7 expects SUN... (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

Q-B7. What does each feed provider-type code map to (D-008; enr-submit AC-7)? The mapping content exists nowhere: the test feed lists IND, GRP, HOSP, AMB, HOS, LAB, PT, DME, HHA with only a short description each, and journey J7 expects SUNSET HOSPICE INC (feed code HOS) to be an active provider. The PRVRECRD type field is 2 bytes, so only 2-character codes (IN, GR, FA, PT) can appear in a legacy-format intake file; longer feed codes arrive through the online API or a converted feed.

Options: A) Seed this proposed mapping as data for the business to confirm: IND to IN, PT to IN, GRP to GR, DME to GR, HOSP to FA, HOS to FA, AMB to FA, LAB to FA, HHA to FA; the table can be changed without code | B) Seed only IND to IN, GRP to GR, HOSP to FA; every other feed code fails E102 until the business adds rows (J7's hospice could then not be enrolled from the feed) | C) Wait for the business to supply the full mapping before Phase 6

**Recommended: A (the ACs only use IND, GRP and HOSP, which are the least ambiguous)**

## Q-032. (group B) Q-B8. How does a run show its batch id and the legacy return code? The BatchRun contract has no batchId and no return code (enr-intake-run AC-1, AC-2; enr-intake-run-2 AC-4 to AC-6; san-intake-run AC-5). (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

Options: A) The batch id appears in the run's start log line and in the intake_run table, not in the BatchRun body; the legacy return code is derived for the parity report: COMPLETED with errored 0 is 0, COMPLETED with errored above 0 is 4, FAILED is 8; the legacy DISPLAY messages are kept as JSON log lines; no contract change | B) Add batchId and returnCode to BatchRun (contract change, user approval)

**Recommended: A**

## Q-033. (group B) Q-B9. Does the file-based intake also run on a schedule (ADR-004 says jobs have a cron)? No file location is defined for a scheduled run of the enrolment intake or the sanction intake. (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

Options: A) No cron for these two file-based jobs: a run starts through the endpoint with an uploaded file and an explicit business date | B) A cron reads a configured drop folder (location and file naming would have to be decided first)

**Recommended: A**

## Q-034. (group B) Q-B10. How is an unknown sanction action reported (san-record AC-5)? FieldError.code is required and limited to E101 to E205, none of which fits; D-026 only says to reject with an error; journey J5 says 'rejected with a field error'. (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

Options: A) 422 problem+json with no legacy code whose detail names the field action and the allowed values T, S and R; the console shows the message at the action field; no contract change | B) 422 problem+json with an errors entry for field action and no code; needs FieldError.code to become optional in the sanctions contract (contract change, user approval) | C) Add a new code (for example E301) to the shared error-code list (contract change, user approval) | D) Reuse an existing code such as E102 (wrong meaning, not recommended)

**Recommended: A**

## Q-035. (group B) Q-B11. What does a second run for the same business date do (enr-intake-run-2 AC-8, san-intake-run AC-7)? nfr.md (Availability) says batch jobs can be re-run for a business date and are idempotent, and ADR-004 says jobs can be re-run for a ... (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

Q-B11. What does a second run for the same business date do (enr-intake-run-2 AC-8, san-intake-run AC-7)? nfr.md (Availability) says batch jobs can be re-run for a business date and are idempotent, and ADR-004 says jobs can be re-run for a date. Without a rule, re-loading an APPINPUT file rejects every earlier valid application as a duplicate (E103) and sends 'Application rejected' notices, and re-loading a SANCIN file records every sanction and notice twice.

Options: A) Idempotent per business date: a record already processed by an earlier run of the same job and business date (the same 200-byte APPINPUT record; for SANCIN the same NPI, action, reason and effective date) is counted as skipped and creates nothing (no application or sanction, no error row, no event); new records are processed normally, so a re-run after a FAILED run finishes the file | B) Each run is independent: records are processed again (re-loaded applications fail E103 and get notices; sanctions and their notices are recorded twice) | C) Refuse a second run for a business date that already has a COMPLETED run (422); a run after a FAILED run processes the whole file again

**Recommended: A**

## Q-036. (group B) Q-B12. What happens to a SANCIN record whose effective date is not a valid CCYYMMDD date (san-intake-run AC-4)? The legacy never checks it, and D-004 and D-024 ask for explicit 4-digit dates and explicit error handling. (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

Options: A) Reject the record like an unknown action: it is counted as rejected, nothing is applied, and the run continues | B) Fail the whole run at that record

**Recommended: A**

## Q-037. (group B) Q-B13. Which HTTP status does POST /api/v1/sanctions return when provider-service cannot apply the status change because it is unreachable or answers with a server error (san-record AC-7)? D-024 asks that the failure is surfaced and told ap... (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

Q-B13. Which HTTP status does POST /api/v1/sanctions return when provider-service cannot apply the status change because it is unreachable or answers with a server error (san-record AC-7)? D-024 asks that the failure is surfaced and told apart from not found; the sanctions contract lists no 5xx response.

Options: A) 503 problem+json whose detail names the NPI, for both an unreachable provider-service and a server error from it; nothing is recorded and no event is published; the 503 response is added to the contract's documented responses at the next revision (additive, user approval) | B) 500 problem+json (generic server error), no contract documentation | C) 502 when provider-service answered with an error and 503 when it could not be reached

**Recommended: A**

## Q-038. (group B) Q-B14. On a hard failure inside a run (an unreadable record, or a record that cannot be stored or applied), does the run stop or go on (enr-intake-run-2 AC-5, AC-6; san-intake-run AC-6)? The legacy PRVENR01 set RC 8 and went on after a writ... (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

Q-B14. On a hard failure inside a run (an unreadable record, or a record that cannot be stored or applied), does the run stop or go on (enr-intake-run-2 AC-5, AC-6; san-intake-run AC-6)? The legacy PRVENR01 set RC 8 and went on after a write error, could loop forever on a read error, and PRVSAN01 never tested the REWRITE; D-024 only asks for explicit handling and a surfaced failure.

Options: A) Stop at that record: status FAILED with an error text naming the record number (and its NPI when known); earlier records keep their results, later records are not processed; the failing record is counted as errored in the enrolment run; a re-run for the same business date (Q-B11) finishes the file | B) Count the record as errored and go on with the next one (the legacy PRVENR01 behaviour after a write error), ending FAILED | C) Roll back the whole run (all or nothing), ending FAILED

**Recommended: A**

## Q-039. (group C) C-1. When an external screening source returns an error or does not answer in time, what should the screening record? The WireMock design has a 500-error stub and a slow stub for every source so that the 'retry and manual-review paths' can ... (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

C-1. When an external screening source returns an error or does not answer in time, what should the screening record? The WireMock design has a 500-error stub and a slow stub for every source so that the 'retry and manual-review paths' can be tested (synthetic-data skill section 5), but no document says what the result is. Affects scr-screen-sources-2 AC-1 and AC-2, and scr-owner AC-5.

Options: A) Each source call has a 2-second limit and one retry, and the four sources are called in parallel (CLAUDE.md section 10), so a screening ends in under 5 s (nfr.md). A source that still has no answer gets result C (the legacy SCR-COND value) with no error code and a detail saying it could not be reached. Because the legacy rule is that anything not P fails (R-PRVSCR01-cbl-00010), the screening result is FAIL; it lands in the analyst's failed-screening queue (the manual-review path) and the analyst can start a MANUAL re-screening later. | B) Keep the screening RUNNING and retry until the source answers (no verdict while a source is down). | C) Treat an unanswered source as P and flag it for manual review (a provider could be enrolled without a real check).

**Recommended: A**

## Q-040. (group C) C-2. How does screening-service get the licence number, licence state, owner NPI and name of the provider? ProviderRegistered carries only NPI, Medicaid ID, type, taxonomy and display name (events.yaml), but the licence check, the owner che... (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

C-2. How does screening-service get the licence number, licence state, owner NPI and name of the provider? ProviderRegistered carries only NPI, Medicaid ID, type, taxonomy and display name (events.yaml), but the licence check, the owner check (E203) and the SAM lookup (the sample SAM feed is keyed by entity_name) need more. Affects scr-screen-sources AC-1 and AC-5, scr-screen-sources-2 AC-5, scr-rescreen AC-5.

Options: A) screening-service calls provider-service GET /api/v1/providers/{npi} with a SYSTEM token (allowed by the 1.0.0 contract and nfr.md) and uses the individual licence number and state and the organisation owner NPI; the name sent to SAM is composed as in D-028 (individual: first + last; organisation: legal name). No contract change. | B) Add licence, owner NPI and name fields to the ProviderRegistered event (an AsyncAPI change that needs your approval). | C) screening-service keeps its own copy of the provider data, fed by events (a new read model and more events).

**Recommended: A**

## Q-041. (group C) C-3. What does the licence check return when the licence is not known to the board, and for groups and facilities? The legacy master holds a licence number only for individuals (IN). The sample licence-board feed also lists licences for gro... (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

C-3. What does the licence check return when the licence is not known to the board, and for groups and facilities? The legacy master holds a licence number only for individuals (IN). The sample licence-board feed also lists licences for groups and facilities (for example GR11100), but neither the legacy record nor the provider API 1.0.0 carries them. No error code exists for 'licence not found' (E202 is 'licence expired'). Affects scr-screen-sources-2 AC-3 and AC-4, scr-verdict AC-3.

Options: A) Individuals: a licence the board does not know gives LICBOARD F with no error code and detail LICENCE NOT FOUND (sample 1003000666). Groups and facilities (GR, FA): the check is not applicable and LICBOARD is P with detail NOT APPLICABLE, so organisations can still be enrolled (journey J7 expects active hospices in the directory). | B) As A for individuals, but report E202 for an unknown licence (stretches the meaning of E202); organisations as A. | C) Require a licence for every type: add a licence number to the organisation data (contract and data-model change) and fail organisations that have none.

**Recommended: A**

## Q-042. (group C) C-4. Is NPPES a fifth screening source? The contract lists NPPES among the source codes and a WireMock NPPES stub is planned, and the pem-domain skill's summary of 42 CFR 455.436 (unverified) names NPPES among the federal databases; but the... (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

C-4. Is NPPES a fifth screening source? The contract lists NPPES among the source codes and a WireMock NPPES stub is planned, and the pem-domain skill's summary of 42 CFR 455.436 (unverified) names NPPES among the federal databases; but the legacy has four checks (licence, LEIE, SAM, SSA DMF), D-005 replaces those four stubs, journey J1 says 'all four sources', and no error code exists for an NPPES failure. Affects scr-screen-sources AC-1.

Options: A) No: four sources as in the legacy; the NPPES stub stays unused in this release; a fifth source can be a later story once the business confirms 455.436. | B) Yes: add an NPPES check as a fifth source (a new business rule that needs an error code and your decision).

**Recommended: A**

## Q-043. (group C) C-5. Which owners are screened against the LEIE for a group or facility? The legacy comment says 'NPI + 5%+ owners', but the legacy master holds one owner NPI (PRV-OWNER-NPI, bytes 121-130) and provider-service API 1.0.0 exposes only the or... (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

C-5. Which owners are screened against the LEIE for a group or facility? The legacy comment says 'NPI + 5%+ owners', but the legacy master holds one owner NPI (PRV-OWNER-NPI, bytes 121-130) and provider-service API 1.0.0 exposes only the organisation's ownerNpi (its provider_owner table with ownership percentages is not exposed by any endpoint). Affects scr-owner AC-1 and AC-2.

Options: A) Only the owner NPI on the provider record is screened, with no percentage threshold. A list of all 5%+ owners becomes a later story if the business wants it. | B) Screen every owner in provider-service's provider_owner table with 5% or more (needs a new provider-service endpoint: contract change).

**Recommended: A**

## Q-044. (group C) C-6. If both the provider and its owner are on the LEIE, which error code does the single OIGLEIE result carry (the design stores one result per source)? Affects scr-owner AC-4. (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

Options: A) E201 (the provider's own exclusion first), with a detail that names the owner as well. | B) E203 (owner excluded).

**Recommended: A**

## Q-045. (group C) C-7. What do the SCRNOUT fields that the legacy never filled contain? The legacy never set SOURCE, VERDICT-DATE or DETAIL (R-PRVSCR01-ADD-02) and D-005 says screening rows now carry them. Affects scr-batch-run AC-3. (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

Options: A) SOURCE = the source code (LICBOARD, OIGLEIE, SAMGOV, SSADMF), VERDICT-DATE = the run's business date as CCYYMMDD, DETAIL = the first 40 characters of the detail (the file field is 40 bytes, the API detail allows 60). The Phase 9 comparison ignores these three fields. | B) Leave them blank or zero as the legacy wrote them (byte-for-byte parity, but the file has no audit value).

**Recommended: A**

## Q-046. (group C) C-8. How does an administrator read the result (status, counts, error) of a screening run and of a revalidation sweep? The 1.0.0 contracts have only the POST (202, status RUNNING) and the file download for these two jobs, while D-024 requir... (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

C-8. How does an administrator read the result (status, counts, error) of a screening run and of a revalidation sweep? The 1.0.0 contracts have only the POST (202, status RUNNING) and the file download for these two jobs, while D-024 requires a job result per run and ADR-004 (accepted) says every run records its counts and failure reason and failures are visible in the console; enrolment and sanction intake already have GET .../{runId}. A contract change needs your approval (java-microservices skill section 6), so scr-batch-run AC-5 and AC-6 and rev-sweep AC-6 and AC-8 name the proposed call and are blocked until you approve it (the other ACs of these stories use the 1.0.0 contract only). The directory extract has the same gap (not in this group).

Options: A) Add GET /api/v1/batch/screening-runs/{runId} and GET /api/v1/batch/revalidation-sweeps/{runId} (ADMIN, returning BatchRun; counts read, screened, skipped, failed for screening and queued, noDueDate for the sweep), like intake-runs and sanction-intake-runs. The architect updates the two contracts to 1.1.0 and the batch-jobs screen. | B) No contract change: the run finishes before the POST answers and the POST response already shows the final BatchRun (COMPLETED or FAILED) while keeping status code 202 (a long HTTP call for large volumes). | C) Results only in logs and metrics (not visible in the console; conflicts with ADR-004).

**Recommended: A**

## Q-047. (group C) C-9. How does PUT /api/v1/risk-rules behave? The body carries version and effectiveFrom, and the contract has a 422 for business validation. Affects rsk-rules-admin AC-2, AC-3 and AC-5, rsk-assess-api AC-6. (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

Options: A) The version in the body must equal the current version (otherwise 422); the saved table becomes the next version and applies to every assessment from that moment; effectiveFrom is stored as the change date (injected Clock); a duplicate prefix gives 422; nobody is re-tiered by the change. | B) The version in the body is ignored (last write wins) and effectiveFrom may be a future date from which the new version applies.

**Recommended: A**

## Q-048. (group C) C-10. How does revalidation-service learn the next due date after a revalidation is completed? The due date is owned by provider-service (ADR-002) and its 1.0.0 API has no call to set it; the only carrier of a due date to revalidation-servi... (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

C-10. How does revalidation-service learn the next due date after a revalidation is completed? The due date is owned by provider-service (ADR-002) and its 1.0.0 API has no call to set it; the only carrier of a due date to revalidation-service is ProviderStatusChanged.revalidationDueDate. Affects rev-cases AC-7 and rev-sweep AC-3; the provider-service stories (prv-screen-verdict, prv-approve) must cover the provider side.

Options: A) provider-service sets the next due date itself when it consumes ScreeningCompleted (REVALIDATION, PASS) and publishes ProviderStatusChanged (status unchanged, from A to A, with the new revalidationDueDate); revalidation-service updates its tracked due date from that event. No contract change. | B) revalidation-service computes the date and calls provider-service (a new endpoint: contract change).

**Recommended: A**

## Q-049. (group C) C-11. What should the sweep do with an ACTIVE provider that has no due date? The legacy queues it, because a zero date is not after today (R-PRVREV01-ADD-03); D-029 lists this as part of the defect. Affects rev-sweep AC-6. (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

Options: A) Do not queue it; count it in the run as noDueDate so that the data gap is visible and fixed (every approval sets a due date, D-003). | B) Queue it like the legacy.

**Recommended: A**

## Q-050. (group C) C-12. What happens when the re-screening of a revalidation FAILS? The completion contract accepts result PASS or FAIL, D-003 says the due date is set 'at each completed revalidation' and journey J6 says 'on PASS the case completes and the n... (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

C-12. What happens when the re-screening of a revalidation FAILS? The completion contract accepts result PASS or FAIL, D-003 says the due date is set 'at each completed revalidation' and journey J6 says 'on PASS the case completes and the next due date is set', but no document says what a FAIL does. Affects rev-cases AC-5.

Options: A) The case is COMPLETED (closed), the due date is not moved (only a passed revalidation counts as completed for D-003) and no status change is made automatically (sanctions stay the supervisor's decision). The FAIL shows in GET /api/v1/screenings?result=FAIL, and if the provider is still ACTIVE and overdue it gets a new case at the next sweep. | B) Automatically suspend or terminate the provider (a new business rule). | C) Keep the case open until an analyst decides.

**Recommended: A**

## Q-051. (group C) C-13. When does a revalidation case move from DUE to IN_SCREENING? No event says that a re-screening has started (revalidation-service consumes only ScreeningCompleted). Affects rev-cases-2 AC-1; with A, a case is DUE only briefly, so rev-s... (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

C-13. When does a revalidation case move from DUE to IN_SCREENING? No event says that a re-screening has started (revalidation-service consumes only ScreeningCompleted). Affects rev-cases-2 AC-1; with A, a case is DUE only briefly, so rev-sweep AC-2 and rev-cases-2 AC-4 check for an open case (DUE or IN_SCREENING) and the 'Revalidation due' work queue of journey J6 should list both open states.

Options: A) When its RevalidationDue event has been published (the re-screening has been requested): DUE = opened, IN_SCREENING = requested, COMPLETED = result received. | B) Only an analyst sets IN_SCREENING (not recommended). | C) Drop the IN_SCREENING status (contract change).

**Recommended: A**

## Q-052. (group C) C-14. When is a revalidation case CANCELLED? The status exists in the contract but nothing says when it is used. Affects rev-cases-2 AC-2, AC-3 and AC-4. (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

Options: A) When the provider's status leaves ACTIVE (T, D or S) while the case is DUE or IN_SCREENING (the legacy sweep only considers ACTIVE providers); a reinstated provider that is still overdue gets a new case at the next sweep. | B) Never in this release (the status is reserved).

**Recommended: A**

## Q-053. (group C) C-15. Does risk-service assign a tier on every PASS screening, or only on the first (INITIAL) one? The overview (flow 2) and the RiskTierAssigned contract say 'on PASS' / 'after a PASS screening' without naming the reason; REVALIDATION and ... (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

C-15. Does risk-service assign a tier on every PASS screening, or only on the first (INITIAL) one? The overview (flow 2) and the RiskTierAssigned contract say 'on PASS' / 'after a PASS screening' without naming the reason; REVALIDATION and MANUAL screenings also end in ScreeningCompleted PASS. Affects rsk-assign-tier AC-5 (and indirectly rsk-rules-admin AC-5).

Options: A) Every PASS screening (INITIAL, REVALIDATION or MANUAL) is assessed with the rule set in force and produces one RiskTierAssigned; provider-service keeps the latest tier. A rule-set change thus reaches a provider at its next passed screening. | B) Only an INITIAL PASS assigns a tier; later screenings keep the first tier until a separate re-tiering is decided.

**Recommended: A**

## Q-054. (group D) OQ-D1. Where does directory-service get the name parts and the licence? ProviderRegistered and ProviderStatusChanged carry a displayName, the type, the taxonomy and the Medicaid ID, but no contract says who composes that displayName or how,... (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

OQ-D1. Where does directory-service get the name parts and the licence? ProviderRegistered and ProviderStatusChanged carry a displayName, the type, the taxonomy and the Medicaid ID, but no contract says who composes that displayName or how, and they carry no name parts or licence; D-028 composes the directory name from the name fields, the FHIR Practitioner shows the licence (Q-008), and directory_entry has columns for last name, first name, legal name and licence. D-013 says directory-service calls provider-service's API. Applied in dir-projection AC-2/AC-3, dir-fhir AC-2, dir-extract AC-5.

Options: A: when it processes a provider event, directory-service reads the full provider with GET /api/v1/providers/{npi} (SYSTEM role), composes the display name itself (D-028) and stores the name parts and licence; the displayName carried by the events is not used by the directory (no contract change) | B: ProviderRegistered and ProviderStatusChanged are extended with the name parts and the licence (contract revision, needs your approval) | C: use only the displayName carried by the events and publish no licence in FHIR

**Recommended: A: when it processes a provider event, directory-service reads the full provider with GET /api/v1/providers/{npi} (SYSTEM role), composes the display name itself (D-028) and stores the name parts and licence; the displayName carried by the events is not used by the directory (no contract change)**

## Q-055. (group D) OQ-D2. How exactly is the composed directory name written? D-028 says individual: first + last; organisation: legal name. It does not give the separator or say what happens to the DBA name. Applied in dir-projection AC-3, dir-extract AC-5, ... (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

OQ-D2. How exactly is the composed directory name written? D-028 says individual: first + last; organisation: legal name. It does not give the separator or say what happens to the DBA name. Applied in dir-projection AC-3, dir-extract AC-5, dir-search AC-3.

Options: A: first name, one space, last name, each without trailing spaces (MARIA RIVERA); the legal name without trailing spaces for a group or facility; the DBA name is never included; letter case unchanged | B: last name first with a comma (RIVERA, MARIA) | C: first and last name for an individual, legal name plus DBA name for an organisation

**Recommended: A: first name, one space, last name, each without trailing spaces (MARIA RIVERA); the legal name without trailing spaces for a group or facility; the DBA name is never included; letter case unchanged**

## Q-056. (group D) OQ-D3. How does the public directory search match and order? The contract says only 'Name contains', a taxonomy filter and a type filter, and gives no sort order or how filters combine. Applied in dir-search AC-3, AC-5, AC-6. (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

Options: A: case-insensitive 'contains' on the display name; exact match on taxonomy and type; all filters given must match; results in ascending NPI order, so paging is stable and equals the DIROUT order | B: case-sensitive contains; ordered by display name | C: starts-with match on the display name; ordered by NPI

**Recommended: A: case-insensitive 'contains' on the display name; exact match on taxonomy and type; all filters given must match; results in ascending NPI order, so paging is stable and equals the DIROUT order**

## Q-057. (group D) OQ-D4. Byte details of the downloadable DIROUT and TMSISOUT files that the copybooks do not fix: the legacy DIROUT FILLER (bytes 83-100) has no VALUE clause, an empty field (for example a provider with no risk tier) has no defined content, ... (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

OQ-D4. Byte details of the downloadable DIROUT and TMSISOUT files that the copybooks do not fix: the legacy DIROUT FILLER (bytes 83-100) has no VALUE clause, an empty field (for example a provider with no risk tier) has no defined content, and the contracts do not say how records are separated (the provider master export says one per line). Applied in dir-extract AC-3/AC-4, tms-extract AC-1/AC-2.

Options: A: one record per line (line feed), the line break not counted in the 100 or 120 bytes, as the provider master export already does; the DIROUT filler and any empty field are spaces | B: records back to back with no line break, spaces as in A | C: as A but the DIROUT filler is binary zeros, as an unset record area may hold

**Recommended: A: one record per line (line feed), the line break not counted in the 100 or 120 bytes, as the provider master export already does; the DIROUT filler and any empty field are spaces**

## Q-058. (group D) OQ-D5. FHIR mapping details that ADR-006 and Q-008 leave open: the identifier system for the NPI and the Medicaid ID, how group and facility are told apart on Organization, and what a PractitionerRole points to (the legacy master holds no l... (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

OQ-D5. FHIR mapping details that ADR-006 and Q-008 leave open: the identifier system for the NPI and the Medicaid ID, how group and facility are told apart on Organization, and what a PractitionerRole points to (the legacy master holds no link from an individual to an employing organisation). Applied in dir-fhir AC-1 to AC-4.

Options: A: the NPI uses the HL7 NPI system (http://hl7.org/fhir/sid/us-npi) and the Medicaid ID a PEM-defined system; Practitioner and PractitionerRole only for type IN, a PractitionerRole = practitioner reference + specialty (the taxonomy code) with no organisation; Organization only for GR and FA with the legacy type code kept as the organisation type; the licence (number and state) as a Practitioner qualification | B: build no FHIR resource until the business supplies a profile | C: as A, but a PractitionerRole references the owner NPI as its organisation (not an employer link)

**Recommended: A: the NPI uses the HL7 NPI system (http://hl7.org/fhir/sid/us-npi) and the Medicaid ID a PEM-defined system; Practitioner and PractitionerRole only for type IN, a PractitionerRole = practitioner reference + specialty (the taxonomy code) with no organisation; Organization only for GR and FA with the legacy type code kept as the organisation type; the licence (number and state) as a Practitioner qualification**

## Q-059. (group D) OQ-D6. What does running an extract again for the same business date do? ADR-004 says jobs can be re-run for a date and nfr.md says they are idempotent; the legacy GDG always added a generation (+1). Applied in dir-extract AC-6, tms-run-arc... (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

OQ-D6. What does running an extract again for the same business date do? ADR-004 says jobs can be re-run for a date and nfr.md says they are idempotent; the legacy GDG always added a generation (+1). Applied in dir-extract AC-6, tms-run-archive AC-4.

Options: A: every start creates a new run with its own file (and, for T-MSIS, its own new archive generation); with unchanged data the files are identical; nothing is overwritten or deleted (Q-012) | B: a second run for the same business date replaces the first run's file and archive generation | C: a second run for the same business date is rejected with 409 Conflict

**Recommended: A: every start creates a new run with its own file (and, for T-MSIS, its own new archive generation); with unchanged data the files are identical; nothing is overwritten or deleted (Q-012)**

## Q-060. (group D) OQ-D7. Where are the figures of a run shown? BatchRun.counts is an open map of integers and there is no field for the archive generation or for warnings; the legacy only DISPLAYed the row counts and the future-enrolment warning. Applied in ... (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

OQ-D7. Where are the figures of a run shown? BatchRun.counts is an open map of integers and there is no field for the archive generation or for warnings; the legacy only DISPLAYed the row counts and the future-enrolment warning. Applied in dir-extract AC-1, tms-run-archive AC-2/AC-4, tms-future-enrolment AC-1 to AC-6.

Options: A: counts.rows (both extracts), counts.archiveGeneration and counts.futureEnrolments (T-MSIS), plus the JSON log line 'PRVTMS01: FUTURE ENROLLMENT? NPI <npi>' (the legacy DISPLAY text) for each flagged provider; no contract change | B: add archiveGeneration and warning fields to BatchRun (contract revision, needs your approval) | C: log only, as the legacy DISPLAY did

**Recommended: A: counts.rows (both extracts), counts.archiveGeneration and counts.futureEnrolments (T-MSIS), plus the JSON log line 'PRVTMS01: FUTURE ENROLLMENT? NPI <npi>' (the legacy DISPLAY text) for each flagged provider; no contract change**

## Q-061. (group D) OQ-D8. ExtractProduced is a run-level event, but the accepted 1.0.0 envelope requires the subject and the message key to be a 10-digit NPI, and tmsis-service's draft outbox_event.subject is CHAR(10) (NPI); an extract has no NPI. Applied in ... (raised 2026-11-18 in Phase 4, status: ANSWERED 2026-11-19 17:50 by the user: B - the run id is the subject and message key of ExtractProduced; events.yaml version bumped and documented; tmsis-service outbox subject column widened; every consumer (notification-service EXTRACT_READY) accepts it; the event is then published)

OQ-D8. ExtractProduced is a run-level event, but the accepted 1.0.0 envelope requires the subject and the message key to be a 10-digit NPI, and tmsis-service's draft outbox_event.subject is CHAR(10) (NPI); an extract has no NPI. Applied in tms-run-archive AC-5 (the AC asserts only the data fields, so it holds under A or B).

Options: A: use a placeholder NPI 0000000000 as subject and key (no contract change, but a fake provider identifier in the event stream) | B: revise events.yaml so the subject and key of ExtractProduced are the run id, and widen the outbox subject column (contract and data-model revision, needs your approval) | C: drop ExtractProduced and let notification-service poll the run list

**Recommended: B: revise events.yaml so the subject and key of ExtractProduced are the run id, and widen the outbox subject column (contract and data-model revision, needs your approval)**

## Q-062. (group D) OQ-D9. When is an enrolment 'in the future'? The legacy compares only the year (R-PRVTMS01-cbl-00005: 2-digit enrolment year greater than the 2-digit current year, strictly, R-PRVTMS01-cbl-00035). D-004 fixes the year width only; comparing ... (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

OQ-D9. When is an enrolment 'in the future'? The legacy compares only the year (R-PRVTMS01-cbl-00005: 2-digit enrolment year greater than the 2-digit current year, strictly, R-PRVTMS01-cbl-00035). D-004 fixes the year width only; comparing the full date would be a new business rule. Applied in tms-future-enrolment AC-1, AC-3.

Options: A: the full enrolment effective date is strictly later than the business date of the run (new, finer rule) | B: the 4-digit enrolment year is strictly greater than the 4-digit year of the business date (the legacy granularity with D-004 applied)

**Recommended: B: the 4-digit enrolment year is strictly greater than the 4-digit year of the business date (the legacy granularity with D-004 applied)**

## Q-063. (group D) OQ-D10. directory-service has only POST /api/v1/batch/directory-extracts and the file download; unlike the T-MSIS, intake and sanctions batch services it has no call to read or list runs, yet D-024 requires failures to be surfaced, and the ... (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

OQ-D10. directory-service has only POST /api/v1/batch/directory-extracts and the file download; unlike the T-MSIS, intake and sanctions batch services it has no call to read or list runs, yet D-024 requires failures to be surfaced, and the scheduled run of ADR-004 cannot be observed at all. Applied in dir-extract AC-1, AC-7 and its out-of-scope list.

Options: A: the start call runs the extract to completion before it answers and returns the final BatchRun (COMPLETED or FAILED, with counts and error) with HTTP 202; the file is downloadable only for COMPLETED runs (404 otherwise); scheduled runs are configured but have no AC; no contract change | B: add GET /api/v1/batch/directory-extracts and GET /api/v1/batch/directory-extracts/{runId} (contract revision, needs your approval)

**Recommended: A: the start call runs the extract to completion before it answers and returns the final BatchRun (COMPLETED or FAILED, with counts and error) with HTTP 202; the file is downloadable only for COMPLETED runs (404 otherwise); scheduled runs are configured but have no AC; no contract change**

## Q-064. (group D) OQ-D11. dir-projection only consumes events, so its ACs can be observed only through the public search call that dir-search is about. Applied in dir-projection AC-1 to AC-8. (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

Options: A: dir-projection delivers the unfiltered page of GET /api/v1/directory/providers (page and size defaults) so that its ACs are testable; dir-search then adds the name, type and taxonomy filters, input validation and the other checks | B: merge dir-projection and dir-search into one story (more than 8 ACs, so it would have to be split again)

**Recommended: A: dir-projection delivers the unfiltered page of GET /api/v1/directory/providers (page and size defaults) so that its ACs are testable; dir-search then adds the name, type and taxonomy filters, input validation and the other checks**

## Q-065. (group D) OQ-D12. What does a FHIR search with a malformed identifier return? The contract defines identifier as a free 'system|value' string with a 400 'Invalid request' response, and the NPI is 10 digits everywhere else in the contracts; FHIR serve... (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

OQ-D12. What does a FHIR search with a malformed identifier return? The contract defines identifier as a free 'system|value' string with a 400 'Invalid request' response, and the NPI is 10 digits everywhere else in the contracts; FHIR servers often answer an unmatched identifier with an empty Bundle instead. Applied in dir-fhir AC-8.

Options: A: an NPI identifier whose value is not 10 digits is rejected with 400 application/problem+json; a well-formed NPI that matches nothing gives an empty searchset Bundle (total 0) | B: any identifier that matches nothing, malformed or not, gives an empty searchset Bundle (total 0)

**Recommended: A: an NPI identifier whose value is not 10 digits is rejected with 400 application/problem+json; a well-formed NPI that matches nothing gives an empty searchset Bundle (total 0)**

## Q-066. (QE-1, group E) After a rejected submit (422), how does the portal reach the stored rejected application for the status page (ui-portal-apply AC-5, ui-portal-status)? The enrollment contract lists 201 and 422, the 422 body carries no application id, and jo... (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

After a rejected submit (422), how does the portal reach the stored rejected application for the status page (ui-portal-apply AC-5, ui-portal-status)? The enrollment contract lists 201 and 422, the 422 body carries no application id, and journey J2 step 3 needs the status page of the stored REJECTED application. Same question as Q-B1 in the enrollment group (whose default A also stores the rejection and lists it with GET /api/v1/applications).

Options: A) The form keeps its error summary and links to the portal home list (My applications), where GET /api/v1/applications lists the rejected application with a View link; no contract change | B) The 422 body carries the applicationId and the portal opens the status page directly (contract change, needs user approval) | C) After a 422 the portal looks up the newest REJECTED application with GET /api/v1/applications?status=REJECTED and opens it (no contract change, but ambiguous when several exist)

**Recommended: A**

## Q-067. (QE-2, group E) Where do the notice names in the portal and console come from? The Notice contract has only a template code (TRM, SUS, RST, GEN, APPROVED, DENIED, REJECTED, REVALIDATION_DUE, EXTRACT_READY), no subject text; the template subjects exist only... (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

Where do the notice names in the portal and console come from? The Notice contract has only a template code (TRM, SUS, RST, GEN, APPROVED, DENIED, REJECTED, REVALIDATION_DUE, EXTRACT_READY), no subject text; the template subjects exist only behind GET /api/v1/notice-templates, which is ADMIN only; journeys J1 and J2 name only 'Enrolment approved' and 'Application rejected' (ui-portal-notices AC-1 to AC-3, ui-console-detail-2 AC-4).

Options: A) The UI keeps one label table, shown as 'label (code)': APPROVED 'Enrolment approved', REJECTED 'Application rejected' (journeys), DENIED 'Enrolment denied', TRM 'Termination notice', SUS 'Suspension notice', RST 'Reinstatement notice', GEN 'General notice', REVALIDATION_DUE 'Revalidation due'; an unknown code is shown as the code only; the business can confirm or reword the table | B) Add a subject field to the Notice contract and show it (contract change, needs user approval) | C) Open GET /api/v1/notice-templates to PROVIDER and staff roles and show each template's subject (contract and nfr.md change, needs user approval) | D) Show only the template code

**Recommended: A**

## Q-068. (QE-3, group E) What does the console show for an enrolment status other than P, A, T, D or S (blank or unknown)? The verified legacy rule labels anything unrecognised DENIED (R-PRVCICS1-cbl-00009, -00029, -00035); D-006 only adds the separate SUSPENDED la... (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

What does the console show for an enrolment status other than P, A, T, D or S (blank or unknown)? The verified legacy rule labels anything unrecognised DENIED (R-PRVCICS1-cbl-00009, -00029, -00035); D-006 only adds the separate SUSPENDED label; the 1.0.0 contract allows only P, A, T, D and S, so this can only arise from bad data or a test double (ui-console-detail AC-6).

Options: A) Keep the legacy catch-all label and show the stored value: 'Denied (X)' or 'Denied (blank)' | B) Show 'Unknown status (X)' and never the word Denied for an unrecognised value | C) Show 'Denied (D)' as the legacy does, hiding the stored value

**Recommended: A**

## Q-069. (QE-4, group E) Which provider-detail panels does each role see (ui-console-detail-2 AC-6)? The screen is for ANALYST, SUPERVISOR and ADMIN, but GET /api/v1/providers/{npi}/audit allows only SUPERVISOR and ADMIN, and GET /api/v1/revalidations allows only A... (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

Which provider-detail panels does each role see (ui-console-detail-2 AC-6)? The screen is for ANALYST, SUPERVISOR and ADMIN, but GET /api/v1/providers/{npi}/audit allows only SUPERVISOR and ADMIN, and GET /api/v1/revalidations allows only ANALYST and SUPERVISOR. Same question as QF-11 in the platform group.

Options: A) Show the audit panel only to SUPERVISOR and ADMIN and the revalidation panel only to ANALYST and SUPERVISOR; the other panels to all three roles; a hidden panel is never requested (no contract change) | B) Widen the audit endpoint to ANALYST and the revalidations endpoint to ADMIN (contract and nfr.md change, needs user approval)

**Recommended: A**

## Q-070. (QE-5, group E) Does the 'Revalidation due' queue show only cases in status DUE, or all open cases (ui-console-queues AC-3)? The sweep opens a case as DUE and the case moves to IN_SCREENING as soon as re-screening is requested, so a DUE-only list would be ... (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

Does the 'Revalidation due' queue show only cases in status DUE, or all open cases (ui-console-queues AC-3)? The sweep opens a case as DUE and the case moves to IN_SCREENING as soon as re-screening is requested, so a DUE-only list would be empty almost at once, although journey J6 step 2 expects the due providers to appear.

Options: A) Show open cases: DUE and IN_SCREENING (two list calls), with the state written in the Case column; COMPLETED and CANCELLED are not shown | B) Show only DUE cases

**Recommended: A**

## Q-071. (QE-6, group E) May an analyst deny a pending provider whose screening FAILED (ui-console-approval AC-1, AC-3, AC-4)? The provider-service contract summary says approve or deny applies to 'a pending provider whose screening passed', but journey J3 has the ... (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

May an analyst deny a pending provider whose screening FAILED (ui-console-approval AC-1, AC-3, AC-4)? The provider-service contract summary says approve or deny applies to 'a pending provider whose screening passed', but journey J3 has the analyst deny provider 1003000444 after a FAIL, and D-019 only requires a Pass screening for approval. The provider group (prv-approve AC-3, AC-4) already follows option A.

Options: A) Approve is offered only when the screen flag is Pass (P); Deny is offered whenever the screen flag is Pass (P) or Fail (F); neither is offered while Unscreened (U); the service has the final say (409 or 422) | B) Both approve and deny only after a Pass screening (journey J3 would then need the provider-service to deny failed providers by another route) | C) Approve and deny are both offered in any pending state

**Recommended: A**

## Q-072. (QE-7, group E) How does the batch page learn that a screening run, revalidation sweep or directory extract has finished (ui-console-batch AC-4, AC-6)? Only the enrolment intake, the sanction intake and the T-MSIS extract have a status or list endpoint in ... (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

How does the batch page learn that a screening run, revalidation sweep or directory extract has finished (ui-console-batch AC-4, AC-6)? Only the enrolment intake, the sanction intake and the T-MSIS extract have a status or list endpoint in contract 1.0.0; the other three return a 202 BatchRun and offer only a file download (question C-8 of the screening/risk/revalidation group proposes a run-status call for the sweep).

Options: A) Show the BatchRun from the 202 answer; the Download button asks for the file and a 404 is shown as 'The file is not available (yet)', so the administrator can try again; no contract change | B) Add GET run endpoints for the three jobs (contract change, needs user approval) and poll them | C) Show only the 202 answer and offer no download until the page is reloaded

**Recommended: A**

## Q-073. (QE-8, group E) Who may change the risk rules in the console (ui-console-risk-rules AC-6)? The skeleton title said supervisor (and the realm-pem.json role description gives SUPERVISOR 'risk rules'), but PUT /api/v1/risk-rules is ADMIN only in the contract ... (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

Who may change the risk rules in the console (ui-console-risk-rules AC-6)? The skeleton title said supervisor (and the realm-pem.json role description gives SUPERVISOR 'risk rules'), but PUT /api/v1/risk-rules is ADMIN only in the contract and nfr.md, and the screen inventory and wireframe say ADMIN; rsk-rules-admin follows ADMIN.

Options: A) ADMIN only, as in the contract, nfr.md and screen inventory; the story title is corrected to 'admin' and the realm role description should be aligned in plt-keycloak | B) SUPERVISOR also (contract, nfr.md and screen inventory change, needs user approval)

**Recommended: A**

## Q-074. (QE-9, group E) What exactly is the 'Failed screening' queue (ui-console-queues AC-2)? A provider with screen flag Fail stays Fail after it is denied, so a list by screen flag alone would keep denied providers forever. (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

Options: A) Providers with status Pending (P) and screen flag Fail (F); once the analyst denies the provider it leaves the list (journey J3) | B) Every provider with screen flag Fail (F), whatever the status

**Recommended: A**

## Q-075. (QE-10, group E) Where is the analyst's last NPI kept (ui-console-search AC-6)? The legacy kept it in the CICS COMMAREA for the terminal session; the screen inventory says 'kept as the last search (replaces the COMMAREA)'. (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

Options: A) In the browser's session storage of the tab: it survives navigating to the detail page and back, and is empty in a new tab or after the browser is closed; no API change | B) On the server per user (a new API and table, contract change, needs user approval) | C) In the browser's permanent local storage, which would keep an NPI across days

**Recommended: A**

## Q-076. (QE-11, group E) Which synthetic Medicaid ID does provider 1003000111 carry? The wireframes show TX0000111 (used in ui-portal-apply AC-2, ui-portal-record AC-1, ui-console-search AC-3, ui-console-detail AC-1), the enrollment group uses M00000111 and the pro... (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

Which synthetic Medicaid ID does provider 1003000111 carry? The wireframes show TX0000111 (used in ui-portal-apply AC-2, ui-portal-record AC-1, ui-console-search AC-3, ui-console-detail AC-1), the enrollment group uses M00000111 and the provider group MCD000111; the sample feed has no Medicaid ID and dat-synthetic assigns one.

Options: A) UI story tests use TX0000111 as their own fixture value (it only has to round-trip through the page); the e2e journeys use whatever value dat-synthetic assigns, and the merged backlog aligns the service stories to that one value | B) Fix TX0000111 now as the dat-synthetic value for every group | C) Fix M00000111 or MCD000111 instead and change the wireframes

**Recommended: A**

## Q-077. (QE-12, group E) How is screening result C shown (ui-console-detail-2 AC-1)? SCRNREC.cpy names the value SCR-COND (data-layouts.md: SCR-COND=C) but no decision gives it a business meaning, and the frontend rule asks for every code with its meaning. (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

Options: A) 'Conditional (C)', taken from the 88-level name SCR-COND; the business can reword it | B) Show the code 'C' only until the business defines it

**Recommended: A**

## Q-078. (QF-1, group F) How is a PROVIDER user tied to their own NPI? The contracts and nfr.md say a PROVIDER sees only their own provider, applications and notices, but no document says how a token identifies that NPI. (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

Options: A) Keycloak user attribute npi released as the access-token claim npi; provider1 gets NPI 1003000111 (the J1 applicant) | B) a link table in provider-service from the Keycloak subject to the NPI | C) the provider types their NPI and an analyst confirms the link (a new business process)

**Recommended: A**

## Q-079. (QF-2, group F) Which legacy provider type (IN, GR or FA) does each feed code map to (D-008)? The table provider_type_mapping is to be seeded in Phase 5 but its rows are defined nowhere; the feed uses IND, GRP, HOSP, HOS, AMB, LAB, PT, DME and HHA. (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

Options: A) IND->IN, PT->IN, GRP->GR, DME->GR, HOSP->FA, HOS->FA, AMB->FA, LAB->FA, HHA->FA (business to confirm) | B) IND->IN, PT->IN, GRP->GR and every other code -> FA | C) the business supplies the table

**Recommended: A**

## Q-080. (QF-3, group F) Where does the email address of a notice come from? The provider record holds no address (Q-008), yet J1 must show the notice in Mailpit; notice.recipient is nullable and its source is not defined. (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

Options: A) a synthetic local address built from the NPI (<npi>@example.test), stored on the notice; no address is added to the provider record | B) the email of the Keycloak user of the PROVIDER role | C) the business supplies a contact address field (new data)

**Recommended: A**

## Q-081. (QF-4, group F) Where does the event backbone code live? The layout has libs/fixed-width and libs/test-support only; nine services each need the envelope, the outbox poller and the idempotent-consumer helper (each already has its own outbox_event and proce... (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

Where does the event backbone code live? The layout has libs/fixed-width and libs/test-support only; nine services each need the envelope, the outbox poller and the idempotent-consumer helper (each already has its own outbox_event and processed_event tables).

Options: A) one small shared module libs/events used by all services, recorded in a new ADR by the architect | B) each service implements it in its own infrastructure package

**Recommended: A**

## Q-082. (QF-5, group F) Which CI setup? The repository has a GitHub remote (nothing pushed) and the devops-engineer may only write under modern/.github, but GitHub runs workflows only from the repository root .github/workflows. (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

Options: A) write modern/.github/workflows/ci.yml plus modern/docs/runbooks/ci.md with the same steps; the workflow runs on GitHub only after the user copies or moves it to the repository root | B) write the workflow at the repository root .github/workflows/ci.yml (outside the agents' write scope; needs the user's OK) | C) no GitHub Actions: modern/docs/runbooks/ci.md only

**Recommended: A**

## Q-083. (QF-6, group F) How do the journeys stay repeatable? Journeys reuse NPI 1003000111 (J1 and J2 apply with it, J4 searches for it) and Phase 8 runs each journey three times in a row; a second J1 run on the same data would fail with E103 duplicate NPI. (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

Options: A) the e2e harness restores the seed state before every journey (make seed empties each service's business tables and reloads the seed, so it can be repeated); journeys that need earlier data (J4 needs 1003000111) create it through API set-up calls | B) journeys run once in order J1 to J7 on one stack; three runs in a row means three clean cycles of make reset, make up, make seed | C) change the journeys to use unique NPIs per run

**Recommended: A**

## Q-084. (QF-7, group F) Which actuator endpoints are open? Container health checks need /actuator/health without a token, but the skill exposes health, info and metrics and says every endpoint has a role test. (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

Options: A) /actuator/health is public; info and metrics need an ADMIN token | B) all actuator endpoints are public in the local profile | C) all actuator endpoints need a token and health checks use a local-only management port

**Recommended: A**

## Q-085. (QF-8, group F) Are the Postgres user passwords, the Keycloak admin password and the SYSTEM service-account client secret also generated randomly into modern/infra/.env.local? The user's decision of 2026-11-18 names the four Keycloak test users; the phase ... (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

Are the Postgres user passwords, the Keycloak admin password and the SYSTEM service-account client secret also generated randomly into modern/infra/.env.local? The user's decision of 2026-11-18 names the four Keycloak test users; the phase file lists the Keycloak admin password in the same step, .gitignore already mentions Postgres, and the databases and the service account need secrets too.

Options: A) yes, every local secret is generated into the git-ignored modern/infra/.env.local and never printed | B) the user types them into the root .env

**Recommended: A**

## Q-086. (QF-9, group F) What are 'the verified rules' for the 95% data coverage and for the traceability report? rule-verification.csv has 455 rows (391 VERIFIED, 40 MISMATCH, 3 NOT_FOUND, 21 DROPPED); rule-to-service.csv marks 22 as DROPPED (it also drops R-PRVDE... (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

What are 'the verified rules' for the 95% data coverage and for the traceability report? rule-verification.csv has 455 rows (391 VERIFIED, 40 MISMATCH, 3 NOT_FOUND, 21 DROPPED); rule-to-service.csv marks 22 as DROPPED (it also drops R-PRVDEAD1-ADD-01 by D-011).

Options: A) every rule of rule-to-service.csv that is not DROPPED (433 today), each with its recorded verification status | B) only rules with status VERIFIED (391 today) | C) every rule of rule-verification.csv that is not DROPPED (434 today)

**Recommended: A**

## Q-087. (QF-10, group F) How is the 10,000-provider smoke run compared with the target 'monthly extract and sweep: 100,000 providers in under 15 minutes'? (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

Options: A) each job must finish within the proportional share, 90 seconds for 10,000 providers, and the report says it is a projection | B) also run a 100,000-provider load once (slower, more disk) | C) report the times only, with no pass or fail until the business sets targets

**Recommended: A**

## Q-088. (QF-11, group F) Who may see the audit history in the provider detail? The screen console.provider-detail is for ANALYST, SUPERVISOR and ADMIN and journey J4 lists audit for the analyst, but GET /api/v1/providers/{npi}/audit allows SUPERVISOR and ADMIN only... (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

Who may see the audit history in the provider detail? The screen console.provider-detail is for ANALYST, SUPERVISOR and ADMIN and journey J4 lists audit for the analyst, but GET /api/v1/providers/{npi}/audit allows SUPERVISOR and ADMIN only (contract and nfr.md). Also GET /api/v1/revalidations excludes ADMIN although the detail screen allows ADMIN.

Options: A) show the audit panel only to SUPERVISOR and ADMIN, and the revalidation panel only to ANALYST and SUPERVISOR; the other panels are shown to all three roles (no contract change) | B) widen the audit endpoint to ANALYST and the revalidations endpoint to ADMIN (contract change, needs the user's approval)

**Recommended: A**

## Q-089. (QF-12, group F) The sample feed and journey NPIs (1003000111 to 1003000888, 1990000111, 1990000999) do not all have a valid NPI check digit, and the feed names (for example RIVERA MARIA MD) are fabricated but not of the obviously fake TESTPROV style, while... (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

The sample feed and journey NPIs (1003000111 to 1003000888, 1990000111, 1990000999) do not all have a valid NPI check digit, and the feed names (for example RIVERA MARIA MD) are fabricated but not of the obviously fake TESTPROV style, while the synthetic-data skill asks for valid check digits and obviously fake names. No legacy rule or decision validates the check digit (E101 only checks presence).

Options: A) generated NPIs satisfy the check digit and lie outside the reserved ranges 1003000000-1003000999 and 1990000000-1990000999; the feed NPIs and names from aws-transform/test-data are used verbatim (fabricated samples) only in scenarios and journeys, are never generated or seeded, and no story validates the check digit | B) replace the feed and journey NPIs and names with generated ones (changes journeys.md and the sample feeds)

**Recommended: A**

## Q-090. (QF-13, group F) What happens to an event a consumer cannot process (a poison message)? ADR-003 promises at-least-once delivery and D-024 asks for failures to be surfaced, but no document says whether such a message blocks the topic, is skipped or is parked... (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

What happens to an event a consumer cannot process (a poison message)? ADR-003 promises at-least-once delivery and D-024 asks for failures to be surfaced, but no document says whether such a message blocks the topic, is skipped or is parked.

Options: A) retry a configured number of times with back-off, then publish it unchanged to the dead-letter topic <topic>.DLT and log an error with its event id and correlation id; later messages keep flowing; replay tooling is a later story | B) log the error and skip the message (it is lost for that consumer) | C) stop consuming the partition until someone fixes the cause

**Recommended: A**

## Q-091. (QF-14, group F) What is the expected outcome of sample application 1003000666 (no name, type IND, licence XX00000 / TX, which is not on the licence board)? No validation rule covers a missing name (E101-E104 only), and no document says how screening classi... (raised 2026-11-18 in Phase 4, status: DEFAULT APPLIED - revisit)

What is the expected outcome of sample application 1003000666 (no name, type IND, licence XX00000 / TX, which is not on the licence board)? No validation rule covers a missing name (E101-E104 only), and no document says how screening classifies a licence that is not found (result F, or C for manual review).

Options: A) the group F fixtures assert no outcome for 1003000666; the stub answers not found for XX00000 / TX and the screening stories decide the classification, after which the expected-outcome list is updated | B) expect the application to be VALIDATED and screening to FAIL on LICBOARD with E202 | C) expect the application to be VALIDATED and the LICBOARD result to be C (manual review)

**Recommended: A**

## Q-092. (Stage B, 2026-11-19) Which address does a notice email go to: <npi>@providers.pem.local or <npi>@example.test? (raised 2026-11-19 while building MAM-59 and MAM-69, status: DEFAULT APPLIED - revisit)

Two defaults that were both applied disagree: Q-A7 (option A) and MAM-59 AC-1 say 1003000111@providers.pem.local; Q-080 (QF-3, option A) and MAM-69 AC-7 say 1003000111@example.test. notification-service is built and tested with @providers.pem.local (MAM-59).

Options: A) Keep <npi>@providers.pem.local (MAM-59, built and tested); MAM-69 AC-7 is read with that address and its Jira text is corrected through the change loop | B) Switch to <npi>@example.test (change notification-service and MAM-59 AC-1) | C) Make the domain a setting (both stories then name the configured value)

**Recommended: A**

## Q-093. (Part 1, 2026-11-19) May the test-writer correct two ambiguous MAM-87 test locators? (raised 2026-11-19 while building MAM-87, status: ANSWERED 2026-11-19 by the user: A)

Two recorded MAM-87 tests look up "the" table cell named "Pass (P)" for a provider whose screen flag is Pass. The page
correctly shows five such cells: the Screen flag, plus the four per-source results that the same story requires
(e2e/console-approval.spec.ts line 146 asserts each source row reads "Pass (P)"). Exact errors:
- Vitest tests/console/approval-page.test.tsx "[MAM-87 AC-7] the status and the screen flag are written as text plus code,
  in table cells of their own": `Found multiple elements with the role "cell" and name "Pass (P)"`.
- Playwright e2e/console-approval.spec.ts "[MAM-87 AC-7] the decision is a labelled group of radio buttons, the Reason has a
  visible label, status and flag are text plus code": strict mode violation, `getByRole('cell', { name: 'Pass (P)' })`
  resolved to 5 elements.
No honest change to the page can satisfy both lines; the user ruled "do not edit the tests" for MAM-87, so they stay red.

Options: A) The test-writer scopes both lookups to the provider table (`within(screen.getByRole('table', { name: 'Provider' }))`
and `page.getByRole('table', { name: 'Provider' }).getByRole('cell', ...)`), documents it in MAM-87/red-report.md and
re-records the hashes; the assertion itself is unchanged | B) Leave both tests failing and accept MAM-87 with 2 known red tests |
C) Change the page so that per-source results are not written "Pass (P)" (contradicts line 146 of the same spec)

**Recommended: A**

## Q-094. (Part 1, 2026-11-19) How does the J1 journey test prove the Kafka events named in MAM-69 AC-2, AC-3, AC-5 and AC-7? (raised 2026-11-19 by the MAM-69 code review, status: ANSWERED 2026-11-19 by the user: A, the default)

The ACs name event payload facts (ProviderRegistered P/U, ScreeningCompleted INITIAL PASS, RiskTierAssigned L,
ProviderStatusChanged P to A with the due date, NoticeSent APPROVED). The J1 Playwright test proves that each event was
published and consumed in the running stack through its effect (screening exists, tier L, directory row, Mailpit mail,
portal notice). A host Kafka probe would need a new npm dependency (kafkajs); adding it was refused by the session's
permission policy, and running a console consumer inside the 384 MiB Kafka container risks an out-of-memory kill.
Each payload is already proven on its real topic by the owning service's Cucumber test: MAM-17 (ProviderRegistered on
pem.provider.events with status P and flag U), MAM-25 (ScreeningCompleted INITIAL PASS with the four sources), MAM-35
(RiskTierAssigned L), MAM-27 (ProviderStatusChanged P to A with the due date), MAM-59 / MAM-58 (NoticeSent).

Options: A) J1 proves the events by their effects; the payloads are proven by the named service tests (cited in the J1
test header) | B) Allow the dev dependency kafkajs@2.2.4 in modern/web-ui and add a read-only topic probe to J1 |
C) Add an audit read endpoint and assert the audit trail instead (new contract)

**Recommended: A** (B if the user allows the dependency)

## Q-095. (Part 1, 2026-11-19) May a PROVIDER submit an application for an NPI other than the one in their token? (raised 2026-11-19 by the MAM-64 code review, status: ANSWERED 2026-11-19 by the user: type IN may apply only under the NPI in the login token, any other NPI gets 403; GR and FA unchanged for now; built test-first)

POST /api/v1/applications (MAM-46) never compares the NPI with the token's npi claim, and the portal form lets the NPI be
typed freely, so provider1 can register a Pending provider under someone else's NPI. Reads stay safe (own-only).

Options: A) For type IN, the portal prefills and locks the NPI from the claim and enrollment-service answers 403 on a
mismatch | B) Allow any NPI (a provider may apply for a group or facility they own) and add an owner rule later |
C) Allow any NPI for GR/FA only, A for IN

**Recommended: A** (C if group applications by an individual provider are expected)

## Q-096. (Part 2, 2026-11-19) May the sanctions contract list 503 for POST /api/v1/sanctions? (raised 2026-11-19 while building MAM-52, status: ANSWERED 2026-11-19 by the user: A; sanctions-service.yaml 1.0.1 lists the 503 of recordSanction, web-ui types regenerated, api:check green)

MAM-52 AC-7 and the Q-B13 default require 503 problem+json when provider-service cannot apply the status change, but
`modern/contracts/openapi/sanctions-service.yaml` lists no 503 response for createSanction. The service answers 503 as the
AC says; the contract was not edited (contract changes need the user's approval).

Options: A) Add the 503 response to createSanction in sanctions-service.yaml (additive, contract 1.0.1) | B) Answer 502 or
500 instead and change MAM-52 AC-7 | C) Leave the contract as it is

**Recommended: A**

## Q-097. (Part 2, 2026-11-19) May the test-writer correct three more recorded UI tests the way Q-093 did? (raised 2026-11-19 after MAM-66 / MAM-73, status: ANSWERED 2026-11-19 by the user: A, and C as a standing rule with guardrails, see PROGRESS.md)

1. **MAM-64, 19 tests superseded by MAM-66** (Vitest 15: apply-status-page 5, apply-submit 3, apply-failures 6,
   apply-followup 1; Playwright 4 in portal-apply.spec.ts). Their stubs answer only GET /api/v1/applications, and one asserts
   that the by-id endpoint is never called (the interim reading "no GET by id yet", which the code review flagged as locking
   in a workaround). MAM-66 AC-1 requires the status page to call GET /api/v1/applications/{applicationId} (built in MAM-54,
   deployed). The page now does, so these MAM-64 tests get 404 from their stub. Lead re-ran: 16 Vitest failures = these 15
   plus item 2.
2. **MAM-73, 1 test**: "[MAM-73 AC-8] one h1, the landmarks banner and main ..." uses `getByRole('banner')`; in jsdom the
   USWDS government banner's inner `<header>` also gets role banner, so it finds 2 ("Found multiple elements"). In the
   browser there is one banner (the Playwright structure check and axe pass).
3. **MAM-87, 1 flaky Playwright test**: "[MAM-87 AC-6] provider1 sees the access-denied page ..." clears the call log right
   after sign-in while the portal home's GET /api/v1/applications can still be in flight (failed 1 of 5 runs).

Options: A) The test-writer corrects them, only as needed and without weakening any assertion: (1) the MAM-64 stubs also
answer GET /api/v1/applications/{id} with the same application, and the "never calls by-id" assertion becomes "calls it";
(2) `getAllByRole('banner')` with at least one, or the banner scoped to the page header; (3) wait for the home page's
request to settle before clearing the log. Each change documented in the story's red-report.md, confirmed byte-level by
code-reviewer, hashes re-recorded | B) Put the list-based status page back (undoes MAM-66 AC-1) and leave 2 and 3 failing |
C) A standing rule: future recorded tests that are ambiguous, racy or superseded by a later story are corrected under A
without asking each time, and reported in the story's Jira comment

**Recommended: A, and C as a standing rule**

## Q-098. (Part 2, 2026-11-19) Two UI readings applied by default (status: DEFAULT APPLIED - revisit)

- MAM-85: "Revalidation due" asks the revalidation API for DUE and IN_SCREENING with the same page and shows both, so one
  page can show up to 40 rows (the contract takes one status per call). Alternative: a contract change to accept several.
- MAM-70 and MAM-66: a 401 shows the page's own "sign in again" message (AC-6 / AC-5) instead of the shared sign-in
  redirect, as on the apply form (MAM-64 AC-6).

## Q-099. (Part 2, 2026-11-19) Should readiness include the database and Kafka? (raised 2026-11-19 while resuming MAM-36, status: ANSWERED 2026-11-19 by the user: B for now, AC-5 verified per service in tests with a Testcontainers database stopped mid-test, no container stopped; revisit A in Phase 8)

MAM-36 AC-5 expects a service to turn unhealthy when its database is down. The compose healthcheck of `pem-apps` uses the
Spring readiness group, which excludes `db` and Kafka, and the local runtime runs all ten services in one JVM against one
Postgres, so stopping "a service's database" also stops all the others.

Options: A) Add `db` and a Kafka indicator to the readiness group; compose then reports `pem-apps` unhealthy when Postgres
or Kafka is down, and AC-5 is verified on the stack by stopping `pem-postgres-1` (compose project pem) for a minute |
B) Keep readiness as it is; AC-5 is verified per service in tests only (a Testcontainers database stopped mid-test) |
C) Both

**Recommended: A** (the user runs or approves the short Postgres stop)

## Q-100. (Part 2, 2026-11-19) Q-095 and a blank NPI (status: DEFAULT APPLIED - revisit)

An IN application from a PROVIDER with an empty or malformed NPI gets 422 E101 (MAM-46 AC-2), not 403: it is never stored
as VALIDATED, registered or published, so the own-NPI rule is not needed to protect anything.

## Q-101. (FINAL PLAN Part B, 2026-11-19 16:33) How are the UX points that change existing test flows built? (raised 2026-11-19 16:33 under safety rule 7, status: ANSWERED 2026-11-19 16:33 by the user)

- B4 review step: an on-page "Review your answers" summary above "Submit application"; one click still submits (J1, J2,
  MAM-64 unchanged). **User: keep this version.**
- B7 confirmation: a live "You are about to approve / deny ..." summary above "Record decision"; no modal dialog (J1 and
  MAM-87 unchanged). **User: keep this version.**
- B2 analyst dashboard: count tiles at the top of /console/queues plus a Tools panel for supervisor and admin; no new route,
  menu entry or start page (MAM-62 unchanged). **User: keep this version.**
- B3 breadcrumbs: **User: use the real USWDS `<nav aria-label="Breadcrumb">`.** The test-writer scopes the MAM-62 menu
  helpers (tests/support/world.tsx `menuEntries`, e2e/support/page.ts `menuEntries`) to the main menu landmark by its
  accessible name (scoping only, no assertion weakened, Q-097 rule), documents it, code-reviewer confirms byte-level; the
  UI agent adds the breadcrumbs only after that.

## Q-102. (FINAL PLAN batch A1, 2026-11-19 16:54) The revalidation sweep runs on an executor, not as a Spring Batch job (ADR-004) (raised 2026-11-19 16:54 under safety rule 7, status: ANSWERED 2026-11-19 17:50 by the user: A, now - convert the MAM-45 sweep to Spring Batch before the revalidation tests are finalised; API, REVALQ output and behaviour identical; J6 must pass)

ADR-004 (ACCEPTED) decides Spring Batch jobs for intake, screening, revalidation sweep, sanction intake, directory extract
and T-MSIS extract, with the business date as a job parameter and counts + failure reason recorded per run. MAM-45 was
built as an executor-run job with its own run record (counts, failure reason, REVALQ file); it works live (COMPLETED,
exact REVALQ records) but does not use Spring Batch. Until answered, every NEW batch job (MAM-71/75, 32, 55, 56, 57, 67)
follows ADR-004.

Options: A) Retrofit MAM-45 to a Spring Batch job in batch A5 together with the other jobs (keeps ADR-004; small rework,
same API) | B) Keep the executor for MAM-45 and amend ADR-004 to allow it (two styles of batch job) | C) Use the executor
style for every job and supersede ADR-004 (needs a new ADR)

**Recommended: A**

## Q-103. (FINAL PLAN batch A2, 2026-11-19 17:00) MAM-51 AC-5 expects DUE cases that MAM-49 and MAM-48 move on within seconds (raised 2026-11-19 17:00 under safety rule 7, status: ANSWERED 2026-11-19 17:50 by the user: A (one case per seeded due ACTIVE provider in any status, none for others))

MAM-51 AC-5: after the 2026-03-31 sweep on the seeded stack, GET /api/v1/revalidations?status=DUE shows "exactly one DUE
case for each seeded ACTIVE provider whose due date is on or before 2026-03-31". MAM-49 AC-1 moves a case to IN_SCREENING as
soon as its RevalidationDue is published, and MAM-48 AC-4 completes it when the re-screening passes; live, the 4 cases were
IN_SCREENING then COMPLETED within seconds and ?status=DUE was already empty. The sweep itself queued exactly the 4 due
ACTIVE providers (REVALQ 4 records) and nothing else.

Options: A) Read AC-5 as "exactly one case per seeded due ACTIVE provider (in any status: DUE, IN_SCREENING or COMPLETED) and
none for any other provider", checked with GET /api/v1/revalidations without a status filter (like MAM-45 AC-2's "one open
case") | B) Keep cases in DUE until an analyst requests the re-screening (changes MAM-49 AC-1, MAM-28 and journey J6) |
C) Pause automatic re-screening in the seeded demo stack only (configuration flag)

**Recommended: A**

## Q-104. (FINAL PLAN Part B, 2026-11-19 17:50) Timeline step 2 wording (status: ANSWERED 2026-11-19 17:50 by the user)

The status timeline showed step 2 as "Accepted" because recorded MAM-64 tests look the word "Validated" up as a substring on
the page. **User: use "Validated".** The test-writer first narrows the recorded lookups of "Validated" / "Rejected" to the
status badge or heading (scoping only, nothing weakened, Q-097 rule), the code-reviewer confirms byte-level, then the UI
agent changes the step text to "Validated".

## Q-105. (FINAL PLAN A5, 2026-11-19 18:29) Spring Batch tables in four more services, and run-status calls for three batch jobs (raised 2026-11-19 18:29 under safety rule 7, status: ANSWERED 2026-11-19 18:29 by the user)

1. The A5 jobs need the Spring Batch BATCH_* tables in screening_db, sanctions_db, enrollment_db and directory_db (ADR-004,
   Q-102): one additive Flyway migration per service, same 5.2.6 schema as tmsis and revalidation; existing tables untouched.
   **User: approve.**
2. The contracts had no run-status call for revalidation-sweeps, screening-runs and directory-extracts (revalidation already
   implemented GET /{runId}, question C-8), so the batch jobs page (MAM-93) could not follow those runs to COMPLETED.
   **User: add GET /api/v1/batch/{revalidation-sweeps,screening-runs,directory-extracts}/{runId} (BatchRun, ADMIN) to the
   three contracts, version 1.1.0, documented.** Done 2026-11-19 18:29: contracts 1.1.0 with the change noted in each description,
   check_phase3 7/7, web-ui types regenerated, api:check green.

## Q-106. (MAM-44, 2026-11-19 20:30) How a provider with no revalidation due date is written in a fixed-width file (status: DEFAULT APPLIED - revisit)

The SQL seed holds NULL for providers that were never approved (no due date). The PRVRECRD codec (MAM-42, D-004/D-007)
rejects both a blank date and 00000000, and MAM-44 AC-8 needs every record to parse.
Options: A) write 99991231 as "no date", with a blank risk tier, and have readers (MAM-50 AC-1) treat 99991231 as none;
B) change the codec so an all-zero date reads as none (library change, D-007); C) write effective date + 5 years
(contradicts D-003 and the SQL seed). **Default applied: A** (documented in modern/testdata/README.md). Test data only;
no service writes PRVRECRD with a missing date today (the MAM-43 export is the only writer).

## Q-107. (MAM-44 AC-6, 2026-11-19 20:30) Does a run-level fixture row count as rule coverage? (status: DEFAULT APPLIED - revisit)

413 of 433 rules have a fixture row (95.38%); 129 of those rows are run-level or lookup rules ("any run over the file
exercises the rule", basis=run/lookup in testdata/coverage.csv). Counting only record- or date-specific rows gives 65.6%.
Options: A) count run-level rows (AC-6 met); B) count only record/date rows and add records until 95%. **Default applied: A.**

## Q-108. (UI polish, 2026-11-19 20:10) Provider detail shows NPI, name, status, screening result and revalidation due twice (status: DEFAULT APPLIED - revisit)

The "Provider" list and the "Provider record" list on /console/providers/{npi} repeat five fields. Removing the "Provider"
list needs a recorded test changed (tests/console/approval-reach.test.tsx:182 reads its "Name" row), which the Q-097 rule
does not cover (a lead UX fix, not a user decision). Options: A) keep both until Part D, then the test-writer moves that
check to the h2 and the UI drops the list; B) keep both permanently. **Default applied: A** (listed as open).

## Q-109. (UI polish B-iv, 2026-11-19) Record details as summary lists, and the equivalent accessibility check (status: ANSWERED 2026-11-19 by the user)

Polish item 5 turned the key-value tables into USWDS summary lists, so the "record table has header cells" checks of
MAM-68 AC-1/AC-7 and MAM-80 AC-1/AC-8 no longer apply. **User: use the equivalent accessibility check** - the named region
holds a dl, every expected term is a dt with its dd. Applied by the test-writer under Q-097 (reviewer CONFIRMED). Spec
wording ("table with header cells", "Screen flag", "Screened", "OIGLEIE") in MAM-62, 68, 74, 80, 82, 85, 87 to be updated
through the Jira change loop (section 8 of MODERNIZATION.md, not designed yet).

## Q-110. (UI polish, 2026-11-19 19:54) Plain dates and the application reference (status: ANSWERED 2026-11-19 19:54 by the user)

**User:** every displayed date in the plain form "19 Nov 2026" on every screen; the application ID shown as "Reference"
under the Application status heading; one confirmation ("Application received") only. Date fields a user types into keep
MM/DD/YYYY (lead default: tests and toIsoDate use it). This supersedes the MM/DD/YYYY display wording in MAM-66 AC-1 and
MAM-68 AC-6 and the display part of D-004 (APIs and files keep ISO / CCYYMMDD; D-004's 4-digit-year rule is unchanged).
Spec wording to be updated through the Jira change loop.

## Q-111. (lane 3, directory-service, 2026-11-19 21:20) Non-ASCII characters in provider names in the legacy files (status: DEFAULT APPLIED - revisit)

DIROUT (and the other fixed-width files) are written as US-ASCII text (directory-service JdbcDirectoryExtracts); a
non-ASCII letter in a name (for example an accented letter) would come back as "?". The legacy files are single-byte
EBCDIC; what the real files hold for such names is not known. Options: A) keep US-ASCII (uppercase names as in the
synthetic data), decide at the parity run (MAM-91) with real legacy output; B) write ISO-8859-1 now. **Default applied: A.**
No AC covers it; the synthetic data has no such names.

## Q-112. (lane 3, directory-service, 2026-11-19 21:20) HAPI FHIR validator for MAM-65 AC-7 (status: ANSWERED by default, technical)

MAM-65 AC-7 says the Bundles are "checked with the HAPI FHIR validator against FHIR R4"; the validator was not in the
build. Technical default (no business impact; HAPI is an allowed library for directory-service in the java-microservices
skill): add hapi-fhir validation as a test-only dependency, pinned in gradle/libs.versions.toml and recorded in ADR-010;
the test-writer adds the HAPI check next to the structural Fhir4Shape check (additive).

## Q-113. (lane 3, MAM-44 AC-7, 2026-11-19 21:30) Should the no-real-data scan also check last names and free text? (status: DEFAULT APPLIED - revisit)

scan_no_real_data.py checks first names, legal names and DBA names, not individual last names or free text (for example
the sanction reason); a copy with a real-looking last name beside TESTPROV passed. Options: A) leave it: every generated
name carries the TESTPROV / SAMPLE markers and the free text comes from fixed strings in the generator; B) add a whitelist
of every generated last name and reason text and fail on anything else (scan change, test first). **Default applied: A.**

## Q-114. (lane 3, risk-service, 2026-11-19 21:50) The RFC 7807 "instance" member is not in the contracts' Problem schema (status: DEFAULT APPLIED - revisit)

Every service answers problems with the RFC 7807 member "instance" (added by Spring); the Problem schema of all nine
contracts does not list it, so a strict contract check rejects real answers. Options: A) leave the contracts as they are
now (no contract change during the build), contract tests ignore "instance"; B) add an optional "instance" to the Problem
schema of every contract (contract change, minor version). **Default applied: A**; B is proposed for the next contract
version.

## Q-115. (lane 3, MAM-38, 2026-11-19 21:50) Is a risk table with no prefix rules valid? (status: DEFAULT APPLIED - revisit)

No AC says whether the admin may save a table with an empty rules list (only the default tier). Options: A) valid: every
provider then gets the default tier (the code accepts it; three tests document it); B) refuse with 422. **Default applied: A.**

## Q-116. (lane 3, MAM-38, 2026-11-19 21:50) Risk table effective date: UTC clock vs database date (status: DEFAULT APPLIED - revisit)

ManageRiskRules saves effective_from from the UTC Clock; JdbcRiskStore selects effective_from <= current_date of the
database session. In a time zone behind UTC a table saved in the local evening would apply only from local midnight.
The whole stack runs in UTC, so nothing differs today. Options: A) leave it (UTC everywhere, documented); B) select with
the injected Clock's date. **Default applied: A** (no AC covers it; revisit before a non-UTC deployment).

## Q-117. (code review directory-service, 2026-11-19 22:15) Scheduled run of the directory extract (status: DEFAULT APPLIED - revisit)

ADR-004 puts every batch job on a cron in configuration; directory-service has no scheduled run (the MAM-67 spec lists
the scheduled run as out of scope: no call listed its runs, OQ-D10). The same holds for the screening run (MAM-32) and the
revalidation sweep (MAM-45); only the T-MSIS extract has a cron. Options: A) on demand only (batch jobs page / API) until a
story adds the schedules, documented in docs/runbooks/operations.md; B) add crons now like TmsisSchedule without an AC.
**Default applied: A.**

## Q-118. (code review sanctions-service, 2026-11-19 23:45) A sanction is a two-system write (provider-service status, then local history and event) (status: DEFAULT APPLIED - revisit)

If provider-service applies the status change and the local history/outbox transaction then fails, or the answer is lost,
the provider is T/S/A with no history row and no SanctionApplied (no notice). Options: A) record a PENDING sanction first
and retry with the same sanctionId, provider-service idempotent on sanctionId (cross-service change); B) accept the gap for
now: the failure is visible (FAILED run / 503), re-running the intake for the date or re-recording the sanction is the
recovery (documented in docs/runbooks/troubleshooting.md). **Default applied: B**; A proposed for hardening.

## Q-119. (code review sanctions-service, 2026-11-19 23:45) SANCIN record with a malformed NPI (status: DEFAULT APPLIED - revisit)

A SANCIN record whose NPI is blank or not 10 digits is counted as rejected ("RECORD n NPI x REJECTED"); the legacy printed
"PRVSAN01: NPI NOT FOUND <npi>" for any failed lookup (R-PRVSAN01-cbl-00003). No AC or decision covers it. Options: A) keep
it rejected (invalid input is not "not found"); B) count it as not found with the legacy line. **Default applied: A**;
compare with the legacy output at the parity run (MAM-91).

## Q-120. (code review sanctions-service, 2026-11-19 23:45) Upload size limit of the intake files (status: DEFAULT APPLIED - revisit)

No explicit limit is set for the multipart uploads (APPINPUT, SANCIN); Tomcat's 2 MB form limit applies and a larger file
gets an undocumented 413 that is not problem+json. Options: A) keep the implicit 2 MB limit, document it in the operations
runbook, map it to problem+json with a contract change later; B) set an explicit limit and add 413 to the contracts now
(contract change). **Default applied: A** (no contract change during the build).

## Q-121. (MAM-84 AC-7, 2026-11-20 15:45) The gateway serves /actuator/info and /actuator/metrics without a token (status: DEFAULT APPLIED - revisit)

Found by the lead's live check: on port 8080 the api-gateway answers 200 without a token for /actuator, /actuator/info,
/actuator/metrics and /actuator/metrics/{name}; the nine services answer 401 (MAM-36 AC-2). The gateway has no token
check of its own by design (it relays the token, MAM-29 AC-3; pem.security.enabled is false for it), and the kit's
pem-common.yml exposes health, info and metrics for every application. MAM-84 AC-7 says "only health is open".
Options: A) the gateway exposes only health (management.endpoints.web.exposure.include: health in api-gateway.yml), so
info and metrics are simply not there on the gateway (404) and nothing new has to validate tokens; B) give the gateway its
own token validation for /actuator/** so that info and metrics answer 401 without a token and 200 for an ADMIN (a security
filter chain in the gateway, which today excludes Spring Security on purpose because the ten applications share one class
path in the local run mode). **Default applied: A** (smallest safe change; the gateway is stricter than the AC asks: 404
instead of 401). Failing test first: tools/checks/live/test_security.py test_mam84_ac7_gateway_opens_only_health (3 red on
2026-11-20 15:59).

## Q-122. (MAM-84 AC-5, 2026-11-20 16:04) Vulnerable third-party libraries found by the dependency scan (status: DEFAULT APPLIED - revisit)

tools/checks/scan_dependencies.py (OSV database; 107 Java run-time libraries of the deployable, 314 web-ui packages)
found 33 advisories, all in Java libraries: 8 CRITICAL, 11 HIGH, 13 MODERATE, 1 LOW (web UI: none). 17 of the 19 HIGH or
CRITICAL ones have a fix in the same release line: tomcat-embed 10.1.55 -> 10.1.59, jackson 2.21.4 -> 2.21.7,
postgresql 42.7.11 -> 42.7.12, snappy-java 1.1.10.5 -> 1.1.10.9, lz4-java 1.10.1 -> 1.11.4, bcprov-jdk18on 1.80.2 -> 1.85
(also log4j-api 2.24.3 -> 2.25.5 for a MODERATE one). Two CRITICAL ones (spring-webmvc 6.2.19: Server-Sent Events with view
fragments, XsltView) have no fix in the 6.2 line (fixed only in Spring Framework 7.0.9 = Spring Boot 4).
Options: A) raise the patch versions above through version constraints on top of the Spring Boot 3.5.16 BOM (ADR-010
updated), full regression, and accept the two spring-webmvc advisories with a written justification (the features are not
used); B) move to Spring Boot 4 now (a major migration); C) justify everything and change nothing.
**Default applied: A** (justifications in modern/tools/checks/dependency-justifications.json; the upgrade is a build change
by the devops-engineer with a regression run before it is deployed).

## Q-123. (MAM-16 AC-2, 2026-11-20 16:40) enrollment-service and sanctions-service have no adapter.legacy package (status: DEFAULT APPLIED - revisit)

MAM-16 AC-2 and ADR-005 expect the fixed-width readers and writers of a service that keeps a legacy file in the package
gov.medicaid.pem.<svc>.adapter.legacy. provider, screening, revalidation, directory and tmsis have it; in
enrollment-service (APPINPUT reader, APPERROR writer) and sanctions-service (SANCIN reader) that code sits in the
application and infrastructure packages. Behaviour, tests and gates are not affected (a layout rule only).
Options: A) leave it for now and list it as an open item of MAM-16 (no correctness or security impact; moving classes
touches recorded tests that import them); B) move the classes now (a refactoring of two services with re-runs and
re-deploys). **Default applied: A** (the user's rule of 2026-11-20: fix only correctness or security defects now).
MAM-16 stays In Progress with AC-2 open for these two services; every other AC of MAM-16 is met.
