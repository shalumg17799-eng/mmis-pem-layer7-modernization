// MAM-73 component-test helpers: the public directory page /directory answered by a directory-service stub
// (GET /api/v1/directory/providers, no sign-in). Synthetic data only (aws-transform/test-data/providers.txt).
import { screen, within } from '@testing-library/react';
import type { UserEvent } from '@testing-library/user-event';
import type { components as DirectorySchemas } from '../../src/shared/api/generated/directory-service';
import { response, type ApiCall, type Handler } from './fakeApi';
import { api, installApi, renderApp, text, type Rendered } from './world';

export type DirectoryEntry = DirectorySchemas['schemas']['DirectoryEntry'];

export const RIVERA: DirectoryEntry = { npi: '1003000111', medicaidId: 'TX0000111', providerType: 'IN', riskTier: 'L', displayName: 'MARIA RIVERA', taxonomy: '207Q00000X' };
export const HOSPICE: DirectoryEntry = { npi: '1003000333', medicaidId: 'TX0000333', providerType: 'FA', riskTier: 'H', displayName: 'SUNSET HOSPICE INC', taxonomy: '251G00000X' };
export const GROUP: DirectoryEntry = { npi: '1003000222', medicaidId: 'TX0000222', providerType: 'GR', riskTier: 'M', displayName: 'RIO GRANDE FAMILY MEDICINE PLLC', taxonomy: '193200000X' };

export function directoryPage(items: DirectoryEntry[], total: number = items.length, page = 0) {
  return { items, page, size: 20, total };
}

/** The directory answers from `entries`: name is "contains" (case-insensitive), providerType is exact (D-008). */
export function directoryRoutes(entries: DirectoryEntry[]): Record<string, Handler> {
  return {
    'GET /api/v1/directory/providers': (call) => {
      const name = call.url.searchParams.get('name')?.toUpperCase();
      const type = call.url.searchParams.get('providerType');
      const found = entries.filter((entry) => (!name || entry.displayName.includes(name)) && (!type || entry.providerType === type));
      return response(200, directoryPage(found));
    },
  };
}

/** Open /directory as a visitor who is not signed in. */
export async function openDirectory(routes: Record<string, Handler>): Promise<Rendered> {
  installApi(routes);
  const rendered = renderApp('/directory');
  await screen.findByRole('heading', { level: 1, name: 'Find a Medicaid provider' });
  return rendered;
}

export const nameBox = (): HTMLInputElement => screen.getByLabelText('Name', { exact: true });
export const typeList = (): HTMLSelectElement => screen.getByLabelText('Provider type', { exact: true });
export const searchButton = (): HTMLElement => screen.getByRole('button', { name: 'Search' });

export async function search(user: UserEvent, name: string, providerType?: string): Promise<void> {
  await user.clear(nameBox());
  if (name !== '') await user.type(nameBox(), name);
  if (providerType !== undefined) await user.selectOptions(typeList(), providerType);
  await user.click(searchButton());
}

/** The table "Active providers". */
export function resultsTable(): HTMLElement | null {
  return screen.queryAllByRole('table').find((table) => within(table).queryByRole('columnheader', { name: 'Name' })) ?? null;
}

export function resultRows(): string[][] {
  const table = resultsTable();
  return table ? Array.from(table.querySelectorAll('tbody tr')).map((row) => Array.from(row.children).map((cell) => text(cell))) : [];
}

export function directoryCalls(): ApiCall[] {
  return api.to('GET', '/api/v1/directory/providers');
}

export function paramsOf(call: ApiCall | undefined): Record<string, string> {
  return Object.fromEntries(call?.url.searchParams.entries() ?? []);
}
