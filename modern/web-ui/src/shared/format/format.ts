import type { Application, Provider, SourceResult } from '../api/types';

const MONTH = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

/**
 * The one way every screen shows a date (UX decision 2026-11-19): an ISO date or instant from the API as "19 Nov 2026", the month in
 * letters so that nobody has to guess day and month, the day without a leading zero. The date part is shown as sent (UTC). Date INPUT
 * fields are not shown through this: they keep MM/DD/YYYY and toIsoDate (dates.ts) reads them.
 */
export function formatDate(iso: string): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso);
  const month = match ? MONTH[Number(match[2]) - 1] : undefined;
  return match && month ? `${Number(match[3])} ${month} ${match[1]}` : iso;
}

/** A legacy code is always shown with its meaning, for example "Active (A)" (frontend-stack section 6). */
export function withCode(code: string, labels: Readonly<Record<string, string>>): string {
  return `${labels[code] ?? 'Unknown'} (${code})`;
}

export const APPLICATION_STATUS_LABEL: Record<Application['status'], string> = {
  SUBMITTED: 'Submitted',
  VALIDATED: 'Validated',
  REJECTED: 'Rejected',
};

export const PROVIDER_TYPE_LABEL: Record<Provider['providerType'], string> = {
  IN: 'Individual',
  GR: 'Group',
  FA: 'Facility',
};

/** PRV-ENROLL-STATUS with the new SUSPENDED status (D-006). */
export const ENROLL_STATUS_LABEL: Record<Provider['enrollStatus'], string> = {
  P: 'Pending',
  A: 'Active',
  T: 'Terminated',
  D: 'Denied',
  S: 'Suspended',
};

/** PRV-RISK-TIER (PRVRSK01). */
export const RISK_TIER_LABEL: Record<string, string> = {
  L: 'Limited',
  M: 'Moderate',
  H: 'High',
};

export const SCREEN_FLAG_LABEL: Record<Provider['screenFlag'], string> = {
  U: 'Unscreened',
  P: 'Pass',
  F: 'Fail',
};

/** SCR-RESULT of SCRNREC: P pass, F fail, C conditional (SCR-COND). */
export const SCREEN_RESULT_LABEL: Record<SourceResult['result'], string> = {
  P: 'Pass',
  F: 'Fail',
  C: 'Conditional',
};

/** Name as the legacy screen shows it: LAST FIRST for an individual, the legal name for a group or facility. */
export function providerName(provider: Provider): string {
  if (provider.individual) return [provider.individual.lastName, provider.individual.firstName].filter(Boolean).join(' ');
  return provider.organization?.legalName ?? '';
}

export const APPLICATION_SOURCE_LABEL: Record<Application['source'], string> = {
  PORTAL: 'Portal',
  LEGACY_FILE: 'Legacy file',
};

/** State of a revalidation case (D-029); only open cases are listed in the work queue. */
export const REVALIDATION_STATUS_LABEL: Record<string, string> = {
  DUE: 'Due',
  IN_SCREENING: 'In screening',
  COMPLETED: 'Completed',
  CANCELLED: 'Cancelled',
};

/** Screening error codes E201-E205 (pem-domain section 4, D-016). */
export const SCREEN_ERROR_LABEL: Record<string, string> = {
  E201: 'excluded',
  E202: 'licence expired',
  E203: 'owner excluded',
  E204: 'deceased',
  E205: 'debarred',
};

/** The audit actions the services write (provider-service), in plain words; the code stays in brackets (frontend-stack section 6). */
export const AUDIT_ACTION_LABEL: Record<string, string> = {
  REGISTERED: 'Provider registered',
  SCREENING_VERDICT: 'Screening result recorded',
  RISK_TIER_ASSIGNED: 'Risk tier assigned',
  APPROVED: 'Enrolment approved',
  DENIED: 'Enrolment denied',
  TERMINATED: 'Provider terminated',
  SUSPENDED: 'Provider suspended',
  REINSTATED: 'Provider reinstated',
  REVALIDATION_PASSED: 'Revalidation passed',
  NOTICE_SENT: 'Notice sent',
};

/** "Enrolment approved (APPROVED)"; an action the page does not know is shown in sentence case with its code. */
export function auditActionText(action: string): string {
  const known = AUDIT_ACTION_LABEL[action];
  if (known) return `${known} (${action})`;
  if (!/^[A-Z][A-Z0-9_]*$/.test(action)) return action;
  const words = action.toLowerCase().replaceAll('_', ' ');
  return `${words.charAt(0).toUpperCase()}${words.slice(1)} (${action})`;
}

/** The screening sources by name (D-005); the code stays in brackets where the name does not already carry it. */
export const SOURCE_LABEL: Record<string, string> = {
  NPPES: 'NPI registry (NPPES)',
  OIGLEIE: 'OIG exclusion list (LEIE)',
  SAMGOV: 'SAM.gov debarment',
  LICBOARD: 'Licence board',
  SSADMF: 'SSA death records',
  PECOS: 'Medicare enrolment (PECOS)',
};

/** "OIG exclusion list (LEIE)" for OIGLEIE; a source the page does not know is shown as its code. */
export function sourceName(source: string): string {
  return SOURCE_LABEL[source] ?? source;
}

/** A failed source with its error code in full: "OIG exclusion list (LEIE): E203 owner excluded" (D-016). */
export function failedSourceText(source: string, errorCode: string | undefined | null): string {
  if (!errorCode) return sourceName(source);
  const meaning = SCREEN_ERROR_LABEL[errorCode];
  return `${sourceName(source)}: ${errorCode}${meaning ? ` ${meaning}` : ''}`;
}

/** "2028-09" as "Sep 2028"; "2028-09-30" as "30 Sep 2028"; anything else as it is. */
function detailDate(value: string): string {
  const month = /^(\d{4})-(\d{2})$/.exec(value);
  if (month) return `${MONTH[Number(month[2]) - 1] ?? month[2]} ${month[1]}`;
  return /^\d{4}-\d{2}-\d{2}$/.test(value) ? formatDate(value) : value;
}

/**
 * The detail of a screening source for display: the services write it in capitals ("NOT EXCLUDED", "LICENCE MD12345 TX ACTIVE UNTIL
 * 2028-09"). It is shown in sentence case with the dates as people read them ("Not excluded", "Licence active until Sep 2028"). Only
 * the display changes; the data is not touched. Words that carry a digit (numbers, codes) and "NPI" keep their form, and a detail that
 * already has lower-case letters is shown as it is.
 */
export function screeningDetailText(detail: string | null | undefined): string {
  if (!detail) return '';
  if (/[a-z]/.test(detail)) return detail;
  // the licence number and the state are in the provider record; the display keeps what the check found
  const licence = /^LICENCE \S*\d\S* [A-Z]{2} (.+)$/.exec(detail);
  const text = licence ? `LICENCE ${licence[1] ?? ''}` : detail;
  const words = text.split(/\s+/).map((word) => {
    if (/^\d{4}-\d{2}(-\d{2})?$/.test(word)) return detailDate(word);
    if (/\d/.test(word) || word === 'NPI') return word;
    return word.toLowerCase();
  });
  const sentence = words.join(' ');
  return sentence.charAt(0).toUpperCase() + sentence.slice(1);
}

/** Plain names of the notice templates (D-012); a template the page does not know is shown as its code. */
export const NOTICE_TEMPLATE_LABEL: Record<string, string> = {
  APPROVED: 'Enrolment approved',
  REJECTED: 'Application rejected',
  DENIED: 'Enrolment denied',
  TRM: 'Termination notice',
  SUS: 'Suspension notice',
  RST: 'Reinstatement notice',
  GEN: 'General notice',
  REVALIDATION_DUE: 'Revalidation due',
};

/** "Enrolment approved (APPROVED)", or just the code for an unknown template. */
export function noticeName(template: string): string {
  const label = NOTICE_TEMPLATE_LABEL[template];
  return label ? `${label} (${template})` : template;
}

/**
 * The enrolment status as the console shows it: words and the stored code, "Active (A)". A value that is not P, A, T, D or S keeps
 * the legacy catch-all label "Denied" (PRVCICS1 WS-MSG) with the real stored value, "Denied (X)", "Denied (blank)" (MAM-80 AC-6).
 */
export function statusWithCatchAll(status: string): string {
  const label = (ENROLL_STATUS_LABEL as Record<string, string>)[status] ?? 'Denied';
  return `${label} (${status === '' ? 'blank' : status})`;
}

/** SANCIN actions T, S and R (D-026). */
export const SANCTION_ACTION_LABEL: Record<string, string> = {
  T: 'Terminate',
  S: 'Suspend',
  R: 'Reinstate',
};

/** Reason of a screening (INITIAL, REVALIDATION, MANUAL) and its overall result. */
export const SCREENING_REASON_LABEL: Record<string, string> = { INITIAL: 'Initial', REVALIDATION: 'Revalidation', MANUAL: 'Manual' };
export const SCREENING_OVERALL_LABEL: Record<string, string> = { PASS: 'Pass', FAIL: 'Fail' };
