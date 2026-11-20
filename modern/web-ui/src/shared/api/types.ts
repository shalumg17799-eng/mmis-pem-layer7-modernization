// Types and endpoint paths come from the generated contracts (npm run api:gen); nothing here is hand-written API shape.
import type { components as DirectoryComponents, paths as DirectoryPaths } from './generated/directory-service';
import type { components as EnrollmentComponents, paths as EnrollmentPaths } from './generated/enrollment-service';
import type { components as ProviderComponents, paths as ProviderPaths } from './generated/provider-service';
import type { components as NotificationComponents, paths as NotificationPaths } from './generated/notification-service';
import type { components as RevalidationComponents, paths as RevalidationPaths } from './generated/revalidation-service';
import type { components as RiskComponents, paths as RiskPaths } from './generated/risk-service';
import type { components as SanctionComponents, paths as SanctionPaths } from './generated/sanctions-service';
import type { components as ScreeningComponents, paths as ScreeningPaths } from './generated/screening-service';

export type Application = EnrollmentComponents['schemas']['Application'];
export type ApplicationPage = EnrollmentComponents['schemas']['ApplicationPage'];
export type ApplicationRequest = EnrollmentComponents['schemas']['ApplicationRequest'];
/** One validation error of an application or decision: the legacy code (E101-E205), the field and the message of the service. */
export type FieldError = EnrollmentComponents['schemas']['FieldError'];
/** RFC 7807 problem details; `errors` lists one entry per failed check (D-001). */
export type Problem = EnrollmentComponents['schemas']['Problem'];
export type Provider = ProviderComponents['schemas']['Provider'];
export type DirectoryEntry = DirectoryComponents['schemas']['DirectoryEntry'];
export type DirectoryPage = DirectoryComponents['schemas']['DirectoryPage'];
export type Notice = NotificationComponents['schemas']['Notice'];
export type NoticePage = NotificationComponents['schemas']['NoticePage'];
export type RiskRuleSet = RiskComponents['schemas']['RiskRuleSet'];
export type RiskRule = RiskComponents['schemas']['RiskRule'];
export type Sanction = SanctionComponents['schemas']['Sanction'];
export type SanctionPage = SanctionComponents['schemas']['SanctionPage'];
export type SanctionRequest = SanctionComponents['schemas']['SanctionRequest'];
export type AuditEntry = ProviderComponents['schemas']['AuditEntry'];
export type AuditPage = ProviderComponents['schemas']['AuditPage'];
export type ProviderPage = ProviderComponents['schemas']['ProviderPage'];
export type RevalidationCase = RevalidationComponents['schemas']['RevalidationCase'];
export type RevalidationCasePage = RevalidationComponents['schemas']['RevalidationCasePage'];
export type ApprovalRequest = ProviderComponents['schemas']['ApprovalRequest'];
export type Screening = ScreeningComponents['schemas']['Screening'];
export type ScreeningPage = ScreeningComponents['schemas']['ScreeningPage'];
export type SourceResult = ScreeningComponents['schemas']['SourceResult'];

/** enrollment-service listApplications (a PROVIDER sees only their own applications). */
export const LIST_APPLICATIONS = '/api/v1/applications' satisfies keyof EnrollmentPaths;

/** enrollment-service submitApplication (same path as listApplications, method POST). */
export const SUBMIT_APPLICATION = '/api/v1/applications' satisfies keyof EnrollmentPaths;

/** provider-service getProvider. */
const GET_PROVIDER = '/api/v1/providers/{npi}' satisfies keyof ProviderPaths;

export function providerPath(npi: string): string {
  return GET_PROVIDER.replace('{npi}', encodeURIComponent(npi));
}

/** provider-service decideProvider: approve (Active, due date +5 years) or deny a pending provider (D-019, D-003). */
const DECIDE_PROVIDER = '/api/v1/providers/{npi}/approval' satisfies keyof ProviderPaths;

export function decisionPath(npi: string): string {
  return DECIDE_PROVIDER.replace('{npi}', encodeURIComponent(npi));
}

/** screening-service listScreenings, filtered by NPI: every screening with its per-source results. */
const LIST_SCREENINGS = '/api/v1/screenings' satisfies keyof ScreeningPaths;

export function screeningsPath(npi: string): string {
  return `${LIST_SCREENINGS}?npi=${encodeURIComponent(npi)}`;
}

/** provider-service searchProviders: the work queues filter by status and screen flag. */
export const SEARCH_PROVIDERS = '/api/v1/providers' satisfies keyof ProviderPaths;

/** revalidation-service listRevalidationCases (D-029: one case per provider with its own state). */
export const LIST_REVALIDATIONS = '/api/v1/revalidations' satisfies keyof RevalidationPaths;

/** The path with its query string; parameters that are undefined or empty are left out (the service defaults apply). */
export function withQuery(path: string, params: Record<string, string | number | undefined>): string {
  const query = new URLSearchParams();
  for (const [name, value] of Object.entries(params)) {
    if (value !== undefined && value !== '') query.set(name, String(value));
  }
  const text = query.toString();
  return text === '' ? path : `${path}?${text}`;
}

/** The screening-service list for one NPI, optionally only the failed screenings. */
export function screeningsFor(npi: string, result?: 'PASS' | 'FAIL'): string {
  return withQuery('/api/v1/screenings' satisfies keyof ScreeningPaths, { npi, result });
}

/** directory-service searchDirectory: public, active providers only (D-028, D-006). */
export const SEARCH_DIRECTORY = '/api/v1/directory/providers' satisfies keyof DirectoryPaths;

/** notification-service listNotices: a PROVIDER gets only the notices of the NPI in the token. */
export const LIST_NOTICES = '/api/v1/notices' satisfies keyof NotificationPaths;

/** enrollment-service getApplication: staff read any application, a PROVIDER only their own (403 otherwise, 404 when unknown). */
const GET_APPLICATION = '/api/v1/applications/{applicationId}' satisfies keyof EnrollmentPaths;

export function applicationPath(applicationId: string): string {
  return GET_APPLICATION.replace('{applicationId}', encodeURIComponent(applicationId));
}

/** sanctions-service: GET lists the history (ANALYST, SUPERVISOR, ADMIN), POST records a sanction (SUPERVISOR). */
export const SANCTIONS = '/api/v1/sanctions' satisfies keyof SanctionPaths;

/** provider-service getProviderAudit (SUPERVISOR, ADMIN). */
const GET_PROVIDER_AUDIT = '/api/v1/providers/{npi}/audit' satisfies keyof ProviderPaths;

export function auditPath(npi: string): string {
  return GET_PROVIDER_AUDIT.replace('{npi}', encodeURIComponent(npi));
}

/** risk-service: GET the current table, PUT replaces it with a new version (ADMIN, data-driven table of PRVRSK01). */
export const RISK_RULES = '/api/v1/risk-rules' satisfies keyof RiskPaths;
