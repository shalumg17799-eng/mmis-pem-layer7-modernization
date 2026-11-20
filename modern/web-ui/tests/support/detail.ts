// MAM-80 / MAM-82 / MAM-90 component-test helpers for the console provider detail page /console/providers/:npi:
// the provider record, and the answers of the services behind its panels. Synthetic data only.
import { screen, waitFor, within } from '@testing-library/react';
import { response, type ApiCall, type Handler } from './fakeApi';
import { provider, type Provider, type UserName } from './fixtures';
import { api, defaultRoutes, installApi, renderApp, signedInAs, text, type Rendered } from './world';

export const RIVERA_NPI = '1003000111';

/** 1003000111 as in the acceptance criteria: Active, screen flag Pass, risk tier Limited, individual details. */
export function rivera(overrides: Partial<Provider> = {}): Provider {
  return provider({
    npi: RIVERA_NPI,
    medicaidId: 'TX0000111',
    providerType: 'IN',
    taxonomy: '207Q00000X',
    riskTier: 'L',
    enrollStatus: 'A',
    screenFlag: 'P',
    effectiveDate: '2026-03-02',
    revalidationDueDate: '2031-03-02',
    individual: { lastName: 'RIVERA', firstName: 'MARIA', licenseNo: 'MD12345', licenseState: 'TX' },
    ...overrides,
  });
}

/** 1003000444: a group whose owner 1990000111 is on the OIG list. */
export function clinic(overrides: Partial<Provider> = {}): Provider {
  return provider({
    npi: '1003000444',
    medicaidId: 'TX0000444',
    providerType: 'GR',
    taxonomy: '193200000X',
    riskTier: null,
    enrollStatus: 'P',
    screenFlag: 'F',
    individual: undefined,
    organization: { legalName: 'EXCLUDED CLINIC LLC', dbaName: 'EXCLUDED CLINIC', ownerNpi: '1990000111' },
    ...overrides,
  });
}

export const detailPath = (npi: string): string => `/console/providers/${npi}`;
export const providerRoute = (npi: string): string => `GET /api/v1/providers/${npi}`;

/** Open the detail page of `record` as `who` (default analyst1); `routes` add or replace gateway answers. */
export async function openDetail(record: Provider, routes: Record<string, Handler> = {}, who: UserName = 'analyst1'): Promise<Rendered> {
  signedInAs(who);
  installApi({ ...defaultRoutes(), [providerRoute(record.npi)]: () => response(200, record), ...routes });
  const rendered = renderApp(detailPath(record.npi));
  await screen.findByRole('heading', { level: 1, name: 'Provider detail' });
  return rendered;
}

/** The list "Provider record": the region with that name (a key-value list since the UI polish, Q-097; the name recordTable is kept). */
export async function recordTable(): Promise<HTMLElement> {
  return waitFor(() => screen.getByRole('region', { name: 'Provider record' }));
}

/** The terms and values of the list: [dt, dd] of every row. */
export function recordRows(table: HTMLElement): Array<[string, string]> {
  return Array.from(table.querySelectorAll('dl > div')).map((row) => [text(row.querySelector('dt')), text(row.querySelector('dd'))] as [string, string]);
}

/** The status banner: the h2 that starts with "Status: ". */
export function banner(): HTMLElement[] {
  return screen.queryAllByRole('heading', { level: 2 }).filter((heading) => text(heading).startsWith('Status: '));
}

/** The section (panel) whose h2 heading is `name`, once it exists. */
export async function findPanel(name: string): Promise<HTMLElement> {
  return waitFor(() => {
    const heading = screen.queryAllByRole('heading', { level: 2 }).find((candidate) => text(candidate) === name);
    const section = heading?.closest('section');
    if (!section) throw new Error(`no panel "${name}" yet`);
    return section;
  });
}

export function hasPanel(name: string): boolean {
  return screen.queryAllByRole('heading', { level: 2 }).some((candidate) => text(candidate) === name);
}

/** Body rows of every table of a panel as cell texts. */
export function panelRows(panel: HTMLElement): string[][] {
  return Array.from(panel.querySelectorAll('tbody tr')).map((row) => Array.from(row.children).map((cell) => text(cell)));
}

export function callsTo(path: string): ApiCall[] {
  return api.to('GET', path);
}

export function paramsOf(call: ApiCall | undefined): Record<string, string> {
  return Object.fromEntries(call?.url.searchParams.entries() ?? []);
}

export { within };
