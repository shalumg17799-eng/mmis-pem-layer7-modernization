// MAM-80 AC-1..AC-7 (component level): the console provider detail /console/providers/:npi shows every record field with its
// meaning and states the enrolment status in words and as the stored code (R-PRVCICS1-ADD-01, D-022, D-006). An unrecognised
// status keeps the legacy catch-all label "Denied" and the stored value. Unknown providers and failures are told apart (D-024).
import { screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { problem, response } from '../support/fakeApi';
import { api, oidc, setupShellWorld, text } from '../support/world';
import { banner, clinic, detailPath, openDetail, providerRoute, recordRows, recordTable, rivera, RIVERA_NPI } from '../support/detail';

vi.mock('oidc-client-ts', async (importOriginal) => {
  const actual = await importOriginal<typeof import('oidc-client-ts')>();
  const { FakeUserManager } = await import('../support/fakeOidc');
  return { ...actual, UserManager: FakeUserManager };
});

setupShellWorld();

describe('[MAM-80 AC-1] an individual record is shown in full, with meanings (journey J4)', () => {
  it('[MAM-80 AC-1] the page calls GET /api/v1/providers/1003000111 and shows the NPI in full in a heading', async () => {
    await openDetail(rivera());
    await recordTable();

    expect(api.to('GET', `/api/v1/providers/${RIVERA_NPI}`).length).toBeGreaterThan(0);
    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1);
    expect(screen.getByRole('heading', { name: /1003000111/ })).toBeInTheDocument();
  });

  it('[MAM-80 AC-1] the region "Provider record" is a list in which every value is a dd paired with its dt term, and shows every field with its value and meaning', async () => {
    await openDetail(rivera());
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
    expect(recordRows(table)).toEqual([
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

  it('[MAM-80 AC-1] a missing risk tier reads "Not assigned" and a missing revalidation date "Not set"', async () => {
    await openDetail(rivera({ riskTier: null, revalidationDueDate: null }));
    const rows = recordRows(await recordTable());

    expect(rows).toContainEqual(['Risk tier', 'Not assigned']);
    expect(rows).toContainEqual(['Revalidation due', 'Not set']);
  });
});

describe('[MAM-80 AC-2] a group or facility shows its organisation fields', () => {
  it('[MAM-80 AC-2] 1003000444 reads "Group (GR)" and shows Legal name, DBA name and Owner NPI in full; the personal fields are not shown', async () => {
    await openDetail(clinic());
    const rows = recordRows(await recordTable());

    expect(rows).toContainEqual(['Provider type', 'Group (GR)']);
    expect(rows.slice(-3)).toEqual([
      ['Legal name', 'EXCLUDED CLINIC LLC'],
      ['DBA name', 'EXCLUDED CLINIC'],
      ['Owner NPI', '1990000111'],
    ]);
    const labels = rows.map((row) => row[0]);
    for (const personal of ['Last name', 'First name', 'Licence number', 'Licence state']) expect(labels).not.toContain(personal);
  });
});

describe('[MAM-80 AC-3] the status banner states the status in words and code', () => {
  it.each([
    ['A', 'Active (A)'],
    ['T', 'Terminated (T)'],
    ['P', 'Pending (P)'],
    ['D', 'Denied (D)'],
    ['S', 'Suspended (S)'],
  ] as const)('[MAM-80 AC-3] status %s: the banner reads exactly "Status: %s" and the Enrolment status row has the same text', async (status, label) => {
    await openDetail(rivera({ enrollStatus: status }));
    const rows = recordRows(await recordTable());

    expect(banner().map((heading) => text(heading))).toEqual([`Status: ${label}`]);
    expect(rows).toContainEqual(['Enrolment status', label]);
  });

  it('[MAM-80 AC-3] the banner is above the record table and is text (a heading), not only colour or an icon', async () => {
    await openDetail(rivera());
    const table = await recordTable();

    const heading = banner()[0]!;
    expect(heading.compareDocumentPosition(table) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(text(heading)).toBe('Status: Active (A)');
  });
});

describe('[MAM-80 AC-4][MAM-80 AC-5] Denied and Suspended have their own wording', () => {
  it('[MAM-80 AC-4] a denied provider (1003000444): the banner reads "Status: Denied (D)" and the row "Denied (D)"', async () => {
    await openDetail(clinic({ enrollStatus: 'D' }));
    const rows = recordRows(await recordTable());

    expect(banner().map((heading) => text(heading))).toEqual(['Status: Denied (D)']);
    expect(rows).toContainEqual(['Enrolment status', 'Denied (D)']);
  });

  it('[MAM-80 AC-5] a suspended provider reads "Suspended (S)" in the banner and the row, never "Denied (D)"', async () => {
    await openDetail(rivera({ enrollStatus: 'S' }));
    const table = await recordTable();

    expect(banner().map((heading) => text(heading))).toEqual(['Status: Suspended (S)']);
    expect(recordRows(table)).toContainEqual(['Enrolment status', 'Suspended (S)']);
    expect(text(banner()[0])).not.toContain('Denied');
    expect(text(table)).not.toContain('Denied');
  });
});

describe('[MAM-80 AC-6] an unrecognised status keeps the legacy catch-all label and the stored code', () => {
  it('[MAM-80 AC-6] status X reads "Status: Denied (X)"', async () => {
    await openDetail(rivera({ enrollStatus: 'X' as never }));
    const rows = recordRows(await recordTable());

    expect(banner().map((heading) => text(heading))).toEqual(['Status: Denied (X)']);
    expect(rows).toContainEqual(['Enrolment status', 'Denied (X)']);
  });

  it('[MAM-80 AC-6] an empty status reads "Status: Denied (blank)"', async () => {
    await openDetail(rivera({ enrollStatus: '' as never }));
    const rows = recordRows(await recordTable());

    expect(banner().map((heading) => text(heading))).toEqual(['Status: Denied (blank)']);
    expect(rows).toContainEqual(['Enrolment status', 'Denied (blank)']);
  });
});

describe('[MAM-80 AC-7] unknown providers and failures are told apart', () => {
  async function open(answer: Response) {
    return openDetail(rivera({ npi: '1003000999' }), { [providerRoute('1003000999')]: () => answer });
  }

  it('[MAM-80 AC-7] a 404 shows "No provider found" with a link "Back to provider search" and no table', async () => {
    await open(response(404, problem(404, 'Not Found')));

    expect(await screen.findByText('No provider found')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Back to provider search' })).toHaveAttribute('href', '/console/providers');
    expect(screen.queryByRole('table')).toBeNull();
    expect(screen.queryByRole('region', { name: 'Provider record' })).toBeNull();
    expect(screen.queryByRole('region', { name: 'Provider' })).toBeNull();
    expect(banner()).toHaveLength(0);
  });

  it('[MAM-80 AC-7] a 401 sends the analyst to sign in and shows no provider data', async () => {
    await open(response(401, problem(401, 'Not authenticated')));

    await vi.waitFor(() => expect(oidc.signinRedirect).toHaveBeenCalled());
    expect(screen.queryByRole('table')).toBeNull();
    expect(screen.queryByRole('region', { name: 'Provider record' })).toBeNull();
    expect(screen.queryByRole('region', { name: 'Provider' })).toBeNull();
    expect(banner()).toHaveLength(0);
  });

  it('[MAM-80 AC-7] a 403 shows "You are not allowed to see this provider" and no status', async () => {
    await open(response(403, problem(403, 'Forbidden')));

    expect(await screen.findByText('You are not allowed to see this provider')).toBeInTheDocument();
    expect(screen.queryByRole('table')).toBeNull();
    expect(screen.queryByRole('region', { name: 'Provider record' })).toBeNull();
    expect(screen.queryByRole('region', { name: 'Provider' })).toBeNull();
    expect(banner()).toHaveLength(0);
  });

  it.each([
    ['500', () => response(500, problem(500, 'Internal Server Error'))],
    ['503', () => response(503, problem(503, 'Service Unavailable'))],
  ])('[MAM-80 AC-7] a %s shows "The provider could not be loaded. Try again." and no status', async (_label, answer) => {
    await open(answer());

    expect(await screen.findByText('The provider could not be loaded. Try again.')).toBeInTheDocument();
    expect(banner()).toHaveLength(0);
    expect(screen.queryByText('No provider found')).toBeNull();
  });

  it('[MAM-80 AC-7] a network failure shows "The provider could not be loaded. Try again." and no status', async () => {
    await openDetail(rivera({ npi: '1003000999' }), {
      [providerRoute('1003000999')]: () => {
        throw new TypeError('Failed to fetch');
      },
    });

    expect(await screen.findByText('The provider could not be loaded. Try again.')).toBeInTheDocument();
    expect(banner()).toHaveLength(0);
  });

  it('[MAM-80 AC-7] the address of the page is /console/providers/:npi', async () => {
    await openDetail(rivera());
    await recordTable();

    expect(window.location.pathname).toBe(detailPath(RIVERA_NPI));
  });
});
