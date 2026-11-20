# User journeys

**Accepted by the user on 2026-11-18 (Q-011):** the kit asks for "the 7 end-to-end journeys" without listing them. These seven follow the legacy flows and the decisions;
each becomes a Playwright test with an axe scan in Phase 8. Screens: `screen-inventory.md`. Synthetic data only.

## J1. A provider applies and is enrolled (D-001, D-005, D-019, D-003)
1. portal.home: the provider signs in (PROVIDER) and chooses Apply.
2. portal.apply: enters NPI 1003000111, Medicaid ID, type Individual (IN), taxonomy 207Q00000X, name and licence; submits.
3. portal.application-status: status Validated.
4. (system) provider-service registers the provider Pending / Unscreened; screening passes on all four sources; risk-service assigns Limited (L).
5. console.work-queues: an ANALYST sees the provider under Pending approval.
6. console.approval: approves; the status becomes Active (A) and the revalidation due date approval + 5 years.
7. portal.notices: the provider sees "Enrolment approved"; portal.provider-record shows Active (A).

## J2. An application is rejected (D-001, D-016)
1. portal.apply: the provider leaves the taxonomy empty and submits.
2. portal.apply: the error summary at the top links to the field; E104 is shown next to the taxonomy; nothing is registered.
3. portal.application-status: status Rejected with exactly one error row (E104).
4. portal.notices: "Application rejected".

## J3. Screening fails and the analyst denies (D-005, D-019)
1. (system) provider 1003000444, whose owner 1990000111 is on the (mocked) OIG LEIE, is registered and screened: FAIL, E203 owner excluded.
2. console.work-queues: the ANALYST sees it under Failed screening.
3. console.provider-detail: the screening table shows OIGLEIE Fail (F) with the detail.
4. console.approval: denies with a reason; the status becomes Denied (D); a notice is sent.

## J4. Analyst inquiry, the PINQ replacement (D-022, D-023)
1. console.provider-search: the ANALYST searches by NPI 1003000111 (the typed NPI is the key), then by Medicaid ID.
2. console.provider-detail: every PRVRECRD field with its meaning, the status as text and code, screening per source, sanctions, revalidation, notices, audit.
3. console.provider-search: an unknown NPI shows "No provider found" (the message PINQ never sent).
4. A PROVIDER user cannot open console routes (403), and sees only their own record in the portal.

## J5. A supervisor suspends and reinstates a provider (D-006, D-018, D-026, D-012)
1. console.provider-detail: the SUPERVISOR opens an active provider and chooses Record sanction.
2. console.record-sanction: action Suspend (S), reason, effective date; recorded; the status becomes Suspended (S); notice SUS sent.
3. public.directory: the provider no longer appears.
4. console.record-sanction: action Reinstate (R); the status becomes Active (A); notice RST sent; the history shows both actions.
5. An unknown action code is rejected with a field error.

## J6. Monthly revalidation (D-003, D-029)
1. console.batch-jobs: the ADMIN runs the revalidation sweep for business date 03/31/2026.
2. console.work-queues: providers due appear under Revalidation due (one case each).
3. (system) re-screening runs; on PASS the case completes and the next due date is set.
4. console.batch-jobs: the REVALQ file of the run can be downloaded.

## J7. Month-end extracts and the public directory (D-015, D-028, Q-007, Q-008)
1. console.batch-jobs: the ADMIN runs the T-MSIS extract for 03/31/2026 and downloads the 120-byte TMSISOUT file; an archive generation is added.
2. console.batch-jobs: runs the directory extract and downloads the 100-byte DIROUT file.
3. public.directory: anyone searches "HOSPICE" and sees only active providers, with a properly composed name.
