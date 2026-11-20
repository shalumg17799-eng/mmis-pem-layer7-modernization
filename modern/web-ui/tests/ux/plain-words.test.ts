// UX overhaul 9, plain words (user decision B-iv item 2, 2026-11-19): what the services send in capitals and as codes is shown in
// plain words, sentence case, with the code in brackets where it adds something. Screening sources by name (Licence board, OIG
// exclusion list (LEIE), SAM.gov debarment, SSA death records), a failed check with its error code in full, details in sentence case
// with plain dates, audit actions and notice templates by name. Only the display changes: the functions are pure and the data is
// not touched. A word the page does not know is shown as it came, never as "undefined" or blank.
import { describe, expect, it } from 'vitest';
import {
  auditActionText,
  failedSourceText,
  noticeName,
  screeningDetailText,
  sourceName,
  statusWithCatchAll,
  withCode,
} from '../../src/shared/format/format';

describe('[UX-OVERHAUL 9] the screening sources by name', () => {
  it.each([
    ['NPPES', 'NPI registry (NPPES)'],
    ['OIGLEIE', 'OIG exclusion list (LEIE)'],
    ['SAMGOV', 'SAM.gov debarment'],
    ['LICBOARD', 'Licence board'],
    ['SSADMF', 'SSA death records'],
    ['PECOS', 'Medicare enrolment (PECOS)'],
  ])('[UX-OVERHAUL 9] %s is shown as "%s"', (code, name) => {
    expect(sourceName(code)).toBe(name);
  });

  it('[UX-OVERHAUL 9] a source the page does not know is shown as its code', () => {
    expect(sourceName('NEWSRC')).toBe('NEWSRC');
  });
});

describe('[UX-OVERHAUL 9] a failed source with its error code in full (D-016)', () => {
  it.each([
    ['LICBOARD', 'E202', 'Licence board: E202 licence expired'],
    ['OIGLEIE', 'E201', 'OIG exclusion list (LEIE): E201 excluded'],
    ['OIGLEIE', 'E203', 'OIG exclusion list (LEIE): E203 owner excluded'],
    ['SSADMF', 'E204', 'SSA death records: E204 deceased'],
    ['SAMGOV', 'E205', 'SAM.gov debarment: E205 debarred'],
  ])('[UX-OVERHAUL 9] %s with %s reads "%s"', (source, code, expected) => {
    expect(failedSourceText(source, code)).toBe(expected);
  });

  it('[UX-OVERHAUL 9] an error code the page does not know is still shown, without a meaning', () => {
    expect(failedSourceText('SAMGOV', 'E999')).toBe('SAM.gov debarment: E999');
  });

  it.each([[null], [undefined], ['']])('[UX-OVERHAUL 9] no error code (%s) gives the name of the source alone', (code) => {
    expect(failedSourceText('LICBOARD', code)).toBe('Licence board');
  });
});

describe('[UX-OVERHAUL 9] the detail of a source in sentence case with plain dates', () => {
  it.each([
    ['NOT EXCLUDED', 'Not excluded'],
    ['NOT DEBARRED', 'Not debarred'],
    ['NOT DECEASED', 'Not deceased'],
    ['NOT APPLICABLE', 'Not applicable'],
    ['COULD NOT BE REACHED', 'Could not be reached'],
    ['LICENCE NOT FOUND', 'Licence not found'],
  ])('[UX-OVERHAUL 9] "%s" is shown as "%s"', (detail, shown) => {
    expect(screeningDetailText(detail)).toBe(shown);
  });

  it.each([
    ['LICENCE MD12345 TX ACTIVE UNTIL 2028-09', 'Licence active until Sep 2028'],
    ['LICENCE MD12345 TX ACTIVE UNTIL 2028-09-30', 'Licence active until 30 Sep 2028'],
    ['LICENCE MD12345 TX EXPIRED 2025-01-31', 'Licence expired 31 Jan 2025'],
    ['DECEASED 2025-03-04', 'Deceased 4 Mar 2025'],
  ])('[UX-OVERHAUL 9] "%s" is shown as "%s" (the licence number and state are in the record; a month or a day is a plain date)', (detail, shown) => {
    expect(screeningDetailText(detail)).toBe(shown);
  });

  it.each([
    ['NPI 1003000444 AND OWNER 1990000111 EXCLUDED', 'NPI 1003000444 and owner 1990000111 excluded'],
    ['OWNER 1990000111 EXCLUDED 1128A1 SINCE 2020-05-01', 'Owner 1990000111 excluded 1128A1 since 1 May 2020'],
  ])('[UX-OVERHAUL 9] "%s": words that carry a digit and "NPI" keep their form, the rest is lower case: "%s"', (detail, shown) => {
    expect(screeningDetailText(detail)).toBe(shown);
  });

  it('[UX-OVERHAUL 9] a detail that already has lower-case letters is shown as it is', () => {
    expect(screeningDetailText('Owner 1990000111 is on the OIG exclusion list')).toBe('Owner 1990000111 is on the OIG exclusion list');
  });

  it.each([[null], [undefined], ['']])('[UX-OVERHAUL 9] no detail (%s) is an empty text, never "undefined"', (detail) => {
    expect(screeningDetailText(detail)).toBe('');
  });
});

describe('[UX-OVERHAUL 9] audit actions and notice templates by name, the code in brackets', () => {
  it.each([
    ['REGISTERED', 'Provider registered (REGISTERED)'],
    ['SCREENING_VERDICT', 'Screening result recorded (SCREENING_VERDICT)'],
    ['RISK_TIER_ASSIGNED', 'Risk tier assigned (RISK_TIER_ASSIGNED)'],
    ['APPROVED', 'Enrolment approved (APPROVED)'],
    ['DENIED', 'Enrolment denied (DENIED)'],
    ['TERMINATED', 'Provider terminated (TERMINATED)'],
    ['SUSPENDED', 'Provider suspended (SUSPENDED)'],
    ['REINSTATED', 'Provider reinstated (REINSTATED)'],
    ['REVALIDATION_PASSED', 'Revalidation passed (REVALIDATION_PASSED)'],
    ['NOTICE_SENT', 'Notice sent (NOTICE_SENT)'],
  ])('[UX-OVERHAUL 9] the audit action %s reads "%s"', (action, shown) => {
    expect(auditActionText(action)).toBe(shown);
  });

  it('[UX-OVERHAUL 9] an audit action the page does not know is shown in sentence case with its code', () => {
    expect(auditActionText('APPROVE')).toBe('Approve (APPROVE)');
    expect(auditActionText('REVALIDATION_STARTED')).toBe('Revalidation started (REVALIDATION_STARTED)');
  });

  it('[UX-OVERHAUL 9] an audit action that is not a code at all is shown as it came', () => {
    expect(auditActionText('manual correction')).toBe('manual correction');
  });

  it.each([
    ['APPROVED', 'Enrolment approved (APPROVED)'],
    ['REJECTED', 'Application rejected (REJECTED)'],
    ['DENIED', 'Enrolment denied (DENIED)'],
    ['TRM', 'Termination notice (TRM)'],
    ['SUS', 'Suspension notice (SUS)'],
    ['RST', 'Reinstatement notice (RST)'],
    ['GEN', 'General notice (GEN)'],
    ['REVALIDATION_DUE', 'Revalidation due (REVALIDATION_DUE)'],
  ])('[UX-OVERHAUL 9] the notice template %s reads "%s"', (template, shown) => {
    expect(noticeName(template)).toBe(shown);
  });

  it('[UX-OVERHAUL 9] a notice template the page does not know is shown as its code', () => {
    expect(noticeName('XYZ')).toBe('XYZ');
  });
});

describe('[UX-OVERHAUL 9] legacy codes with their meaning (frontend-stack section 6)', () => {
  it.each([
    ['P', 'Pending (P)'],
    ['A', 'Active (A)'],
    ['T', 'Terminated (T)'],
    ['D', 'Denied (D)'],
    ['S', 'Suspended (S)'],
  ])('[UX-OVERHAUL 9] the enrolment status %s reads "%s" on the console', (status, shown) => {
    expect(statusWithCatchAll(status)).toBe(shown);
  });

  it('[UX-OVERHAUL 9] a status outside P, A, T, D and S keeps the legacy catch-all word with the real stored value', () => {
    expect(statusWithCatchAll('X')).toBe('Denied (X)');
    expect(statusWithCatchAll('')).toBe('Denied (blank)');
  });

  it('[UX-OVERHAUL 9] withCode gives the meaning and the code, and "Unknown" for a code it has no meaning for', () => {
    expect(withCode('M', { M: 'Moderate' })).toBe('Moderate (M)');
    expect(withCode('Q', { M: 'Moderate' })).toBe('Unknown (Q)');
  });
});
