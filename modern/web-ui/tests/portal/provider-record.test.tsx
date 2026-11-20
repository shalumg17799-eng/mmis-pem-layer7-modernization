// MAM-68 AC-1..AC-6 (component level): /portal/provider shows the signed-in provider's own record in full: identifiers, type,
// taxonomy, status, screen flag, risk tier, dates and the individual or organisation details; every code with its meaning,
// four-digit years (D-004), never another provider's record (D-023). The record is asked for by the NPI claim of the token only.
import { screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { problem, response } from '../support/fakeApi';
import { PROVIDER_NPI, provider, type Provider } from '../support/fixtures';
import { api, defaultRoutes, fieldValue, installApi, renderApp, setupShellWorld, signedInAs, text } from '../support/world';

vi.mock('oidc-client-ts', async (importOriginal) => {
  const actual = await importOriginal<typeof import('oidc-client-ts')>();
  const { FakeUserManager } = await import('../support/fakeOidc');
  return { ...actual, UserManager: FakeUserManager };
});

setupShellWorld();

const RIVERA = provider({
  riskTier: 'L',
  enrollStatus: 'A',
  screenFlag: 'P',
  effectiveDate: '2026-03-02',
  revalidationDueDate: '2031-03-02',
  individual: { lastName: 'RIVERA', firstName: 'MARIA', licenseNo: 'MD12345', licenseState: 'TX' },
});

const ORGANISATION = provider({
  providerType: 'GR',
  taxonomy: '193200000X',
  individual: undefined,
  organization: { legalName: 'RIO GRANDE FAMILY MEDICINE PLLC', dbaName: 'RIO GRANDE FAMILY CARE', ownerNpi: '1990000111' },
});

async function openRecord(answer: Provider | Response) {
  signedInAs('provider1');
  installApi({ ...defaultRoutes(), [`GET /api/v1/providers/${PROVIDER_NPI}`]: () => (answer instanceof Response ? answer : response(200, answer)) });
  const rendered = renderApp('/portal/provider');
  await screen.findByRole('heading', { level: 1, name: 'My provider record' });
  return rendered;
}

/** The record list: the region named "Provider record" (term in the dt, value in the dd; a key-value list since the UI polish, Q-097). */
async function recordTable(): Promise<HTMLElement> {
  return waitFor(() => within(screen.getByRole('main')).getByRole('region', { name: 'Provider record' }));
}

function rows(table: HTMLElement): Array<[string, string]> {
  return Array.from(table.querySelectorAll('dl > div')).map((row) => [text(row.querySelector('dt')), text(row.querySelector('dd'))] as [string, string]);
}

describe('[MAM-68 AC-1] the individual record is shown in full (journey J1)', () => {
  it('[MAM-68 AC-1] the page calls GET /api/v1/providers/1003000111', async () => {
    await openRecord(RIVERA);
    await recordTable();

    expect(api.to('GET', `/api/v1/providers/${PROVIDER_NPI}`).length).toBeGreaterThan(0);
  });

  it('[MAM-68 AC-1] the list in which every value is a dd paired with its dt term shows every field with its value and meaning', async () => {
    await openRecord(RIVERA);
    const table = await recordTable();

    // user decision 2026-11-19 (Q-097): the equivalent accessibility check for a summary list, which has no header cells
    expect(table.querySelector('dl')).not.toBeNull();
    const terms = Array.from(table.querySelectorAll('dt'));
    expect(terms.map((term) => text(term))).toEqual([
      'NPI',
      'Medicaid ID',
      'Provider type',
      'Taxonomy',
      'Enrolment status',
      'Screening result',
      'Risk tier',
      'Effective date',
      'Revalidation due',
      'Last name',
      'First name',
      'Licence number',
      'Licence state',
    ]);
    for (const term of terms) expect(term.nextElementSibling?.tagName).toBe('DD');
    expect(rows(table)).toEqual([
      ['NPI', '1003000111'],
      ['Medicaid ID', 'TX0000111'],
      ['Provider type', 'Individual (IN)'],
      ['Taxonomy', '207Q00000X'],
      ['Enrolment status', 'Active (A)'],
      ['Screening result', 'Pass (P)'],
      ['Risk tier', 'Limited (L)'],
      ['Effective date', '2 Mar 2026'],
      ['Revalidation due', '2 Mar 2031'],
      ['Last name', 'RIVERA'],
      ['First name', 'MARIA'],
      ['Licence number', 'MD12345'],
      ['Licence state', 'TX'],
    ]);
  });

  it('[MAM-68 AC-1] the main landmark reads "Active (A)" (journey J1 step 7)', async () => {
    await openRecord(RIVERA);
    await recordTable();

    expect(within(screen.getByRole('main')).getByText('Active (A)')).toBeInTheDocument();
    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1);
  });
});

describe('[MAM-68 AC-2] a group or facility shows its organisation details', () => {
  it.each([
    ['GR', 'Group (GR)'],
    ['FA', 'Facility (FA)'],
  ] as const)('[MAM-68 AC-2] type %s reads "%s" and shows Legal name, DBA name and Owner NPI instead of the personal details', async (type, label) => {
    await openRecord({ ...ORGANISATION, providerType: type });
    const table = await recordTable();

    const shown = rows(table);
    expect(shown).toContainEqual(['Provider type', label]);
    expect(shown.slice(-3)).toEqual([
      ['Legal name', 'RIO GRANDE FAMILY MEDICINE PLLC'],
      ['DBA name', 'RIO GRANDE FAMILY CARE'],
      ['Owner NPI', '1990000111'],
    ]);
    const labels = shown.map((row) => row[0]);
    for (const personal of ['Last name', 'First name', 'Licence number', 'Licence state']) expect(labels).not.toContain(personal);
  });
});

describe('[MAM-68 AC-3] every status, screen flag and risk tier has its own wording', () => {
  it.each([
    ['P', 'Pending (P)'],
    ['A', 'Active (A)'],
    ['T', 'Terminated (T)'],
    ['D', 'Denied (D)'],
    ['S', 'Suspended (S)'],
  ] as const)('[MAM-68 AC-3] enrolment status %s reads "%s"', async (status, label) => {
    await openRecord({ ...RIVERA, enrollStatus: status });
    await recordTable();

    expect(fieldValue('Enrolment status')).toBe(label);
  });

  it.each([
    ['U', 'Unscreened (U)'],
    ['P', 'Pass (P)'],
    ['F', 'Fail (F)'],
  ] as const)('[MAM-68 AC-3] screen flag %s reads "%s"', async (flag, label) => {
    await openRecord({ ...RIVERA, screenFlag: flag });
    await recordTable();

    expect(fieldValue('Screening result')).toBe(label);
  });

  it.each([
    ['L', 'Limited (L)'],
    ['M', 'Moderate (M)'],
    ['H', 'High (H)'],
    [null, 'Not assigned'],
  ] as const)('[MAM-68 AC-3] risk tier %s reads "%s"', async (tier, label) => {
    await openRecord({ ...RIVERA, riskTier: tier });
    await recordTable();

    expect(fieldValue('Risk tier')).toBe(label);
  });

  it('[MAM-68 AC-3] a missing revalidation date reads "Not set"', async () => {
    await openRecord({ ...RIVERA, enrollStatus: 'P', revalidationDueDate: null });
    await recordTable();

    expect(fieldValue('Revalidation due')).toBe('Not set');
  });

  it('[MAM-68 AC-3] Suspended is never shown as Denied', async () => {
    await openRecord({ ...RIVERA, enrollStatus: 'S' });
    const table = await recordTable();

    expect(text(table)).not.toContain('Denied');
  });
});

describe('[MAM-68 AC-4] only my own record is ever requested (journey J4)', () => {
  it('[MAM-68 AC-4] the only provider request is GET /api/v1/providers/1003000111, and the page has no NPI input and no NPI in its address', async () => {
    await openRecord(RIVERA);
    await recordTable();

    const providerCalls = api.calls.filter((call) => call.url.pathname.startsWith('/api/v1/providers'));
    expect(new Set(providerCalls.map((call) => `${call.method} ${call.url.pathname}${call.url.search}`))).toEqual(new Set([`GET /api/v1/providers/${PROVIDER_NPI}`]));
    expect(within(screen.getByRole('main')).queryAllByRole('textbox')).toHaveLength(0);
    expect(window.location.pathname).toBe('/portal/provider');
    expect(window.location.search).toBe('');
  });

  it('[MAM-68 AC-4] after a 403 the page shows an access-denied message with no provider data', async () => {
    await openRecord(response(403, problem(403, 'Forbidden')));

    expect(await screen.findByText(/access denied/i)).toBeInTheDocument();
    expect(screen.queryByRole('table')).toBeNull();
    expect(text(screen.getByRole('main'))).not.toContain('1003000111');
    expect(screen.queryByText(/No provider record/i)).toBeNull();
  });
});

describe('[MAM-68 AC-5] no record yet is explained', () => {
  async function openNoRecord() {
    return openRecord(response(404, problem(404, 'Not Found')));
  }

  it('[MAM-68 AC-5] a 404 says that there is no provider record yet and that a record is created when an application is accepted', async () => {
    await openNoRecord();

    const main = within(screen.getByRole('main'));
    expect(await main.findByText(/No provider record exists yet/i)).toBeInTheDocument();
    expect(main.getByText(/A record is created when an application is accepted/i)).toBeInTheDocument();
  });

  it('[MAM-68 AC-5] it links to My enrolment and to Apply for enrolment', async () => {
    await openNoRecord();

    const main = within(screen.getByRole('main'));
    expect(await main.findByRole('link', { name: 'My enrolment' })).toHaveAttribute('href', '/portal');
    expect(main.getByRole('link', { name: 'Apply for enrolment' })).toHaveAttribute('href', '/portal/apply');
  });

  it('[MAM-68 AC-5] it shows no table and no error code', async () => {
    await openNoRecord();
    await within(screen.getByRole('main')).findByText(/No provider record exists yet/i);

    expect(screen.queryByRole('table')).toBeNull();
    expect(screen.queryByRole('region', { name: 'Provider record' })).toBeNull();
    expect(text(screen.getByRole('main'))).not.toMatch(/\bE\d{3}\b|404/);
  });

  it('[MAM-68 AC-5] a failure other than 404 is not explained as "no record yet"', async () => {
    await openRecord(response(500, problem(500, 'Internal Server Error')));

    expect(await screen.findByText(/could not be loaded/i)).toBeInTheDocument();
    expect(screen.queryByText(/No provider record exists yet/i)).toBeNull();
  });
});

describe('[MAM-68 AC-6] dates keep four-digit years', () => {
  it('[MAM-68 AC-6] a revalidation due date of 2070-03-02 reads 2 Mar 2070 and an effective date of 2026-03-02 reads 2 Mar 2026', async () => {
    await openRecord({ ...RIVERA, effectiveDate: '2026-03-02', revalidationDueDate: '2070-03-02' });
    await recordTable();

    expect(fieldValue('Revalidation due')).toBe('2 Mar 2070');
    expect(fieldValue('Effective date')).toBe('2 Mar 2026');
    expect(text(screen.getByRole('main'))).not.toMatch(/\b\d{2}\/\d{2}\/\d{2}\b(?!\d)/);
  });
});
