// MAM-87 component-test helpers (a new support file: the MAM-62 support files are recorded and stay untouched).
// A small provider-service + screening-service world: the provider the console loads, its screening results, and the
// answer of POST /api/v1/providers/{npi}/approval. Synthetic data only (aws-transform/test-data/providers.txt).
import { screen } from '@testing-library/react';
import type { components as ScreeningSchemas } from '../../src/shared/api/generated/screening-service';
import { response, type ApiCall, type Handler } from './fakeApi';
import { provider, type Provider, type UserName } from './fixtures';
import { api, defaultRoutes, installApi, renderApp, signedInAs, type Rendered } from './world';

export type Screening = ScreeningSchemas['schemas']['Screening'];
export type ScreeningPage = ScreeningSchemas['schemas']['ScreeningPage'];
export type SourceResult = ScreeningSchemas['schemas']['SourceResult'];

export const RIVERA_NPI = '1003000111';
export const HOSPICE_NPI = '1003000333';
export const CLINIC_NPI = '1003000444';

/** 1003000111: Pending (P), screen flag Pass (P) and a passed screening (AC-1, AC-2). */
export function pendingPass(overrides: Partial<Provider> = {}): Provider {
  return provider({
    npi: RIVERA_NPI,
    medicaidId: 'TX0000111',
    providerType: 'IN',
    taxonomy: '207Q00000X',
    riskTier: null,
    enrollStatus: 'P',
    screenFlag: 'P',
    effectiveDate: '2026-03-02',
    revalidationDueDate: null,
    individual: { lastName: 'RIVERA', firstName: 'MARIA', licenseNo: 'MD12345', licenseState: 'TX' },
    ...overrides,
  });
}

/** 1003000444: Pending (P), screen flag Fail (F); its owner 1990000111 is on the OIG exclusion list (AC-3, AC-4). */
export function pendingFail(overrides: Partial<Provider> = {}): Provider {
  return provider({
    npi: CLINIC_NPI,
    medicaidId: 'TX0000444',
    providerType: 'GR',
    taxonomy: '193200000X',
    riskTier: null,
    enrollStatus: 'P',
    screenFlag: 'F',
    effectiveDate: '2026-03-02',
    revalidationDueDate: null,
    individual: undefined,
    organization: { legalName: 'EXCLUDED CLINIC LLC', dbaName: 'EXCLUDED CLINIC', ownerNpi: '1990000111' },
    ...overrides,
  });
}

/** 1003000333: Pending (P), screen flag Unscreened (U): screening has not finished (AC-4). */
export function pendingUnscreened(overrides: Partial<Provider> = {}): Provider {
  return provider({
    npi: HOSPICE_NPI,
    medicaidId: 'TX0000333',
    providerType: 'FA',
    taxonomy: '251G00000X',
    riskTier: null,
    enrollStatus: 'P',
    screenFlag: 'U',
    effectiveDate: '2026-03-02',
    revalidationDueDate: null,
    individual: undefined,
    organization: { legalName: 'SUNSET HOSPICE INC', dbaName: 'SUNSET HOSPICE', ownerNpi: '1003000111' },
    ...overrides,
  });
}

const VERDICT_AT = '2026-03-02T09:14:00Z';

export function source(name: SourceResult['source'], result: SourceResult['result'], detail: string, errorCode: SourceResult['errorCode'] = null): SourceResult {
  return { source: name, result, verdictAt: VERDICT_AT, detail, errorCode };
}

export function screening(npi: string, result: 'PASS' | 'FAIL', results: SourceResult[], overrides: Partial<Screening> = {}): Screening {
  return {
    screeningId: '5c1f0a9e-3b7d-4a52-9e64-0d8f2b6a7c10',
    npi,
    reason: 'INITIAL',
    status: 'COMPLETED',
    result,
    results,
    startedAt: '2026-03-02T09:13:00Z',
    completedAt: VERDICT_AT,
    ...overrides,
  };
}

export function screeningPage(...items: Screening[]): ScreeningPage {
  return { items, page: 0, size: 20, total: items.length };
}

/** A passed screening: four sources, all Pass (P). */
export function passedScreening(npi: string = RIVERA_NPI): ScreeningPage {
  return screeningPage(
    screening(npi, 'PASS', [
      source('LICBOARD', 'P', 'MD12345 active to 09/2028'),
      source('OIGLEIE', 'P', 'No exclusion'),
      source('SAMGOV', 'P', 'Not debarred'),
      source('SSADMF', 'P', 'No death record'),
    ]),
  );
}

/** The failed screening of journey J3: OIGLEIE fails with E203 (owner excluded). */
export function failedScreening(npi: string = CLINIC_NPI): ScreeningPage {
  return screeningPage(
    screening(npi, 'FAIL', [
      source('LICBOARD', 'P', 'GR11100 active to 09/2028'),
      source('OIGLEIE', 'F', 'Owner 1990000111 is on the OIG exclusion list', 'E203'),
      source('SAMGOV', 'P', 'Not debarred'),
      source('SSADMF', 'P', 'No death record'),
    ]),
  );
}

export const NO_SCREENINGS: ScreeningPage = { items: [], page: 0, size: 20, total: 0 };

export const approvalPath = (npi: string): string => `/console/providers/${npi}/approval`;
export const detailPath = (npi: string): string => `/console/providers/${npi}`;
export const decisionRoute = (npi: string): string => `POST /api/v1/providers/${npi}/approval`;

/** The decisions sent to the API for one NPI. */
export function decisions(npi: string): ApiCall[] {
  return api.to('POST', `/api/v1/providers/${npi}/approval`);
}

export function sentDecision(call: ApiCall | undefined): Record<string, unknown> {
  if (!call?.body) throw new Error('no POST .../approval with a body was sent');
  return JSON.parse(call.body) as Record<string, unknown>;
}

/** What provider-service answers for a decision on a pending provider: APPROVE gives Active with due date approval + 5 years. */
export function decided(record: Provider, decision: unknown, approvedOn = '2026-03-05'): Provider {
  if (decision === 'APPROVE') {
    const due = `${Number(approvedOn.slice(0, 4)) + 5}${approvedOn.slice(4)}`;
    return { ...record, enrollStatus: 'A', revalidationDueDate: due, version: (record.version ?? 0) + 1 };
  }
  return { ...record, enrollStatus: 'D', version: (record.version ?? 0) + 1 };
}

export interface ApprovalWorld {
  routes: Record<string, Handler>;
  /** The provider as the service holds it now: GET answers with it, a successful decision changes it. */
  current: () => Provider;
}

/**
 * The gateway for one approval page. `decision` answers POST .../approval; by default the service decides the way the
 * contract says (APPROVE -> A with due date +5 years, DENY -> D) and later GETs return the decided provider.
 */
export function approvalWorld(record: Provider, screenings: ScreeningPage, decision?: Handler): ApprovalWorld {
  let current = record;
  const decide: Handler =
    decision ??
    ((call) => {
      const body = JSON.parse(call.body ?? '{}') as { decision?: unknown };
      current = decided(current, body.decision);
      return response(200, current);
    });
  return {
    current: () => current,
    routes: {
      ...defaultRoutes(),
      [`GET /api/v1/providers/${record.npi}`]: () => response(200, current),
      'GET /api/v1/screenings': () => response(200, screenings),
      [decisionRoute(record.npi)]: decide,
    },
  };
}

/** Sign in as `who`, serve `world` and open a console page. Resolves when the heading of the page is on the screen. */
export async function openConsole(who: UserName, route: string, world: ApprovalWorld, heading: string): Promise<Rendered> {
  signedInAs(who);
  installApi(world.routes);
  const rendered = renderApp(route);
  await screen.findByRole('heading', { level: 1, name: heading });
  return rendered;
}

/** The approval page of `npi`, after its decision form (or its message) has loaded. */
export async function openApproval(who: UserName, world: ApprovalWorld, npi: string): Promise<Rendered> {
  return openConsole(who, approvalPath(npi), world, 'Approve or deny');
}

export const approveRadio = (): HTMLInputElement => screen.getByRole('radio', { name: /^Approve/ });
export const denyRadio = (): HTMLInputElement => screen.getByRole('radio', { name: /^Deny/ });
export const reasonBox = (): HTMLTextAreaElement => screen.getByLabelText(/^Reason/);
export const recordButton = (): HTMLElement => screen.getByRole('button', { name: 'Record decision' });
