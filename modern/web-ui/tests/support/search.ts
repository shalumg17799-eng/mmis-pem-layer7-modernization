// MAM-77 component-test helpers: the provider search /console/providers answered by a provider-service stub that implements
// GET /api/v1/providers?npi= | medicaidId= | name= (contract searchProviders). Synthetic data only.
import { screen, within } from '@testing-library/react';
import type { UserEvent } from '@testing-library/user-event';
import { response, type ApiCall, type Handler } from './fakeApi';
import type { Provider, UserName } from './fixtures';
import { api, defaultRoutes, installApi, renderApp, signedInAs, text, type Rendered } from './world';
import { providerName } from '../../src/shared/format/format';

export const PROVIDERS_ROUTE = 'GET /api/v1/providers';

export function providerPage(items: Provider[], total: number = items.length) {
  return { items, page: 0, size: 20, total };
}

/** A service that searches `records`: npi and medicaidId are exact, name is "contains" on the name shown by the console. */
export function searchRoutes(records: Provider[], over: Record<string, Handler> = {}): Record<string, Handler> {
  return {
    ...defaultRoutes(),
    [PROVIDERS_ROUTE]: (call) => {
      const npi = call.url.searchParams.get('npi');
      const medicaidId = call.url.searchParams.get('medicaidId');
      const name = call.url.searchParams.get('name')?.toUpperCase();
      const found = records.filter(
        (record) =>
          (!npi || record.npi === npi) && (!medicaidId || record.medicaidId === medicaidId) && (!name || providerName(record).toUpperCase().includes(name)),
      );
      return response(200, providerPage(found));
    },
    ...over,
  };
}

export async function openSearch(routes: Record<string, Handler>, who: UserName = 'analyst1'): Promise<Rendered> {
  signedInAs(who);
  installApi(routes);
  const rendered = renderApp('/console/providers');
  await screen.findByRole('heading', { level: 1, name: 'Provider search' });
  return rendered;
}

export const npiBox = (): HTMLInputElement => screen.getByLabelText('NPI', { exact: true });
export const medicaidBox = (): HTMLInputElement => screen.getByLabelText('Medicaid ID', { exact: true });
export const nameBox = (): HTMLInputElement => screen.getByLabelText('Name contains', { exact: true });
export const searchButton = (): HTMLElement => screen.getByRole('button', { name: 'Search' });

export interface Criteria {
  npi?: string;
  medicaidId?: string;
  name?: string;
}

/** Replace the three inputs by `criteria` (an input that is not mentioned is emptied) and choose Search. */
export async function search(user: UserEvent, criteria: Criteria): Promise<void> {
  const fields: Array<[HTMLInputElement, string | undefined]> = [
    [npiBox(), criteria.npi],
    [medicaidBox(), criteria.medicaidId],
    [nameBox(), criteria.name],
  ];
  for (const [box, value] of fields) {
    await user.clear(box);
    if (value) await user.type(box, value);
  }
  await user.click(searchButton());
}

export function resultsTable(): HTMLElement | null {
  return screen.queryAllByRole('table').find((table) => within(table).queryByRole('columnheader', { name: 'Risk' })) ?? null;
}

export function resultRows(): string[][] {
  const table = resultsTable();
  return table ? Array.from(table.querySelectorAll('tbody tr')).map((row) => Array.from(row.children).map((cell) => text(cell))) : [];
}

export function searchCalls(): ApiCall[] {
  return api.to('GET', '/api/v1/providers');
}

export function paramsOf(call: ApiCall | undefined): Record<string, string> {
  return Object.fromEntries(call?.url.searchParams.entries() ?? []);
}

export function describedText(element: HTMLElement): string {
  return (element.getAttribute('aria-describedby') ?? '')
    .split(/\s+/)
    .filter(Boolean)
    .map((id) => text(document.getElementById(id)))
    .join(' ');
}
