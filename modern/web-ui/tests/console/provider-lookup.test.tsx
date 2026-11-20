// MAM-34 AC-3 (walking skeleton): the console page Provider search looks up a seeded NPI and shows the provider's name,
// status and risk tier. Display formats as in MAM-77 and the wireframe console-provider-search.html: name LAST FIRST for
// an individual, the legal name for a group or facility, status and risk as text with their code. The lookup may use
// GET /api/v1/providers/{npi} or GET /api/v1/providers?npi= (MAM-77); both are answered here.
import { screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { response, type Handler } from '../support/fakeApi';
import { provider, type Provider } from '../support/fixtures';
import { api, bodyRows, defaultRoutes, installApi, oidc, renderApp, setupShellWorld, signedInAs, text } from '../support/world';

vi.mock('oidc-client-ts', async (importOriginal) => {
  const actual = await importOriginal<typeof import('oidc-client-ts')>();
  const { FakeUserManager } = await import('../support/fakeOidc');
  return { ...actual, UserManager: FakeUserManager };
});

setupShellWorld();

// Seeded providers of modern/testdata/README.md (make seed).
const ALPHA = provider({
  npi: '1005940865',
  medicaidId: '989455927',
  providerType: 'IN',
  riskTier: 'L',
  enrollStatus: 'A',
  individual: { lastName: 'ALPHA', firstName: 'TESTPROV', licenseNo: 'SD10001', licenseState: 'TX' },
});
const HOSPICE = provider({
  npi: '1005932946',
  medicaidId: '924589677',
  providerType: 'FA',
  taxonomy: '251G00000X',
  riskTier: 'H',
  enrollStatus: 'T',
  individual: undefined,
  organization: { legalName: 'SAMPLE HOSPICE 02', dbaName: 'SAMPLE HOSPICE CARE 02', ownerNpi: '1005918507' },
});

function lookupRoutes(record: Provider): Record<string, Handler> {
  return {
    ...defaultRoutes(),
    [`GET /api/v1/providers/${record.npi}`]: () => response(200, record),
    'GET /api/v1/providers': (call) =>
      call.url.searchParams.get('npi') === record.npi
        ? response(200, { items: [record], page: 0, size: 20, total: 1 })
        : response(200, { items: [], page: 0, size: 20, total: 0 }),
  };
}

async function lookUp(npi: string) {
  signedInAs('analyst1');
  const { user } = renderApp('/console/providers');
  expect(await screen.findByRole('heading', { level: 1, name: 'Provider search' })).toBeInTheDocument();
  await user.type(screen.getByLabelText('NPI', { exact: true }), npi);
  await user.click(screen.getByRole('button', { name: 'Search' }));
  return waitFor(() => {
    const table = screen
      .queryAllByRole('table')
      .find((t) => within(t).queryByRole('columnheader', { name: 'Risk' }) && within(t).queryByRole('columnheader', { name: 'Name' }));
    if (!table || bodyRows(table).length === 0) throw new Error('no results table with a row yet');
    return table;
  });
}

/** Cell texts of the first body row, keyed by column header. */
function firstRow(table: HTMLElement): Record<string, string> {
  const headers = within(table).getAllByRole('columnheader').map((h) => text(h));
  const first = bodyRows(table)[0];
  if (!first) throw new Error('the results table has no row');
  const cells = Array.from(first.children).map((c) => text(c));
  return Object.fromEntries(headers.map((h, i) => [h, cells[i] ?? '']));
}

describe('[MAM-34 AC-3] the console page Provider lookup shows the provider', () => {
  it('[MAM-34 AC-3] analyst1 looks up seeded NPI 1005940865 and sees name ALPHA TESTPROV, status Active (A) and risk tier Limited (L)', async () => {
    installApi(lookupRoutes(ALPHA));
    const table = await lookUp(ALPHA.npi);
    expect(bodyRows(table)).toHaveLength(1);
    const row = firstRow(table);
    expect(row.NPI).toBe('1005940865');
    expect(row.Name).toBe('ALPHA TESTPROV');
    expect(row.Status).toBe('Active (A)');
    expect(row.Risk).toBe('Limited (L)');
  });

  it('[MAM-34 AC-3] a facility shows its legal name, status Terminated (T) and risk tier High (H)', async () => {
    installApi(lookupRoutes(HOSPICE));
    const row = firstRow(await lookUp(HOSPICE.npi));
    expect(row.Name).toBe('SAMPLE HOSPICE 02');
    expect(row.Status).toBe('Terminated (T)');
    expect(row.Risk).toBe('High (H)');
  });

  it('[MAM-34 AC-3] the lookup goes to the gateway with the analyst token and the typed NPI', async () => {
    installApi(lookupRoutes(ALPHA));
    await lookUp(ALPHA.npi);
    const lookups = api.calls.filter((c) => c.url.pathname.startsWith('/api/v1/providers'));
    expect(lookups.length).toBeGreaterThan(0);
    for (const call of lookups) {
      expect(call.url.origin).toBe('http://localhost:8080');
      expect(call.headers.get('authorization')).toBe(`Bearer ${oidc.user?.access_token}`);
      expect(call.url.pathname.endsWith(ALPHA.npi) || call.url.searchParams.get('npi') === ALPHA.npi).toBe(true);
    }
  });
});
