// MAM-77 AC-1..AC-7 (component level): the provider search /console/providers replaces the CICS PINQ screen (D-022). The typed NPI
// is the key searched (R-PRVCICS1-ADD-02), Medicaid ID and name are new, a blank or malformed entry is sent back for correction,
// an unknown provider is "No provider found", the last NPI is kept for the next inquiry (sessionStorage = this tab only), and
// failures are shown (D-024). The page calls GET /api/v1/providers?npi= | medicaidId= | name=, never GET /api/v1/providers/{npi}.
import { screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { problem, response } from '../support/fakeApi';
import { provider } from '../support/fixtures';
import { api, oidc, setupShellWorld, text } from '../support/world';
import { rivera } from '../support/detail';
import {
  describedText,
  medicaidBox,
  nameBox,
  npiBox,
  openSearch,
  paramsOf,
  PROVIDERS_ROUTE,
  resultRows,
  resultsTable,
  search,
  searchButton,
  searchCalls,
  searchRoutes,
} from '../support/search';

vi.mock('oidc-client-ts', async (importOriginal) => {
  const actual = await importOriginal<typeof import('oidc-client-ts')>();
  const { FakeUserManager } = await import('../support/fakeOidc');
  return { ...actual, UserManager: FakeUserManager };
});

setupShellWorld();

const RIVERA = rivera();
const SECOND = provider({ npi: '1003000222', medicaidId: 'TX0000222', individual: { lastName: 'OAKS', firstName: 'DEE', licenseNo: 'MD22222', licenseState: 'TX' } });
const EMPTY = 'Enter an NPI, a Medicaid ID or a name';
const BAD_NPI = 'NPI must be 10 digits';

describe('[MAM-77 AC-1] the inquiry screen opens ready for input', () => {
  it('[MAM-77 AC-1] the page shows the h1 "Provider search", labelled inputs NPI, Medicaid ID and Name contains and a Search button', async () => {
    await openSearch(searchRoutes([RIVERA]));

    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1);
    for (const box of [npiBox(), medicaidBox(), nameBox()]) {
      expect(box.tagName).toBe('INPUT');
      expect(box.labels).toHaveLength(1);
      expect(box.labels?.[0]).not.toHaveClass('usa-sr-only');
    }
    expect(searchButton().tagName).toBe('BUTTON');
  });

  it('[MAM-77 AC-1] the NPI input is empty, no results table and no message are shown, and no search call has been made', async () => {
    await openSearch(searchRoutes([RIVERA]));

    expect(npiBox()).toHaveValue('');
    expect(resultsTable()).toBeNull();
    expect(screen.queryByText('No provider found')).toBeNull();
    expect(screen.queryByRole('alert')).toBeNull();
    expect(api.calls.filter((call) => call.url.pathname.startsWith('/api/v1/providers'))).toHaveLength(0);
  });
});

describe('[MAM-77 AC-2] the typed NPI is the search key', () => {
  it('[MAM-77 AC-2] typing 1003000111 calls GET /api/v1/providers?npi=1003000111 with exactly the typed value and not the single-provider endpoint', async () => {
    const { user } = await openSearch(searchRoutes([RIVERA]));

    await search(user, { npi: '1003000111' });

    await waitFor(() => expect(resultRows()).toHaveLength(1));
    expect(paramsOf(searchCalls().at(-1))).toEqual({ npi: '1003000111' });
    expect(api.to('GET', '/api/v1/providers/1003000111')).toHaveLength(0);
  });

  it('[MAM-77 AC-2] the table NPI | Name | Type | Status | Risk has one row 1003000111 | RIVERA MARIA | Individual (IN) | Active (A) | Limited (L)', async () => {
    const { user } = await openSearch(searchRoutes([RIVERA]));

    await search(user, { npi: '1003000111' });

    await waitFor(() => expect(resultRows()).toHaveLength(1));
    expect(Array.from(resultsTable()!.querySelectorAll('thead th')).map((header) => text(header))).toEqual(['NPI', 'Name', 'Type', 'Status', 'Risk']);
    expect(resultRows()).toEqual([['1003000111', 'RIVERA MARIA', 'Individual (IN)', 'Active (A)', 'Limited (L)']]);
  });

  it('[MAM-77 AC-2] the NPI in the row links to /console/providers/1003000111', async () => {
    const { user } = await openSearch(searchRoutes([RIVERA]));

    await search(user, { npi: '1003000111' });

    await waitFor(() => expect(resultRows()).toHaveLength(1));
    expect(screen.getByRole('link', { name: '1003000111' })).toHaveAttribute('href', '/console/providers/1003000111');
  });
});

describe('[MAM-77 AC-3] search by Medicaid ID or by name', () => {
  it('[MAM-77 AC-3] Medicaid ID TX0000111 calls ?medicaidId=TX0000111 and then the name RIVERA calls ?name=RIVERA, each sending only the filled inputs', async () => {
    const { user } = await openSearch(searchRoutes([RIVERA, SECOND]));

    await search(user, { medicaidId: 'TX0000111' });
    await waitFor(() => expect(resultRows()).toHaveLength(1));
    expect(paramsOf(searchCalls().at(-1))).toEqual({ medicaidId: 'TX0000111' });
    expect(resultRows()[0]?.[0]).toBe('1003000111');

    await search(user, { name: 'RIVERA' });
    await waitFor(() => expect(paramsOf(searchCalls().at(-1))).toEqual({ name: 'RIVERA' }));
    await waitFor(() => expect(resultRows()[0]?.[0]).toBe('1003000111'));
    expect(searchCalls()).toHaveLength(2);
  });

  it('[MAM-77 AC-3] filled inputs are all sent together', async () => {
    const { user } = await openSearch(searchRoutes([RIVERA]));

    await search(user, { npi: '1003000111', medicaidId: 'TX0000111', name: 'RIVERA' });

    await waitFor(() => expect(resultRows()).toHaveLength(1));
    expect(paramsOf(searchCalls().at(-1))).toEqual({ npi: '1003000111', medicaidId: 'TX0000111', name: 'RIVERA' });
  });

  it('[MAM-77 AC-3] several matches are listed, one row each', async () => {
    const { user } = await openSearch(searchRoutes([RIVERA, SECOND]));

    await search(user, { name: 'A' });

    await waitFor(() => expect(resultRows()).toHaveLength(2));
    expect(resultRows().map((row) => row[0])).toEqual(['1003000111', '1003000222']);
  });
});

describe('[MAM-77 AC-4] a blank or malformed entry is not searched', () => {
  it('[MAM-77 AC-4] Search with all three inputs empty makes no call and shows an error summary that links to the NPI field, with the same text next to it', async () => {
    const { user } = await openSearch(searchRoutes([RIVERA]));

    await user.click(searchButton());

    const heading = await screen.findByRole('heading', { level: 2, name: '1 error in the search' });
    expect(api.calls.filter((call) => call.url.pathname.startsWith('/api/v1/providers'))).toHaveLength(0);
    const link = screen.getByRole('link', { name: EMPTY });
    expect(link).toBeInTheDocument();
    expect(describedText(npiBox())).toContain(EMPTY);
    expect(npiBox()).toHaveAttribute('aria-invalid', 'true');
    await waitFor(() => expect(document.activeElement?.contains(heading)).toBe(true));
    await user.click(link);
    expect(npiBox()).toHaveFocus();
  });

  it('[MAM-77 AC-4] NPI 12345 makes no call and reads "NPI must be 10 digits" in the summary and next to the field; what was typed stays', async () => {
    const { user } = await openSearch(searchRoutes([RIVERA]));

    await search(user, { npi: '12345' });

    await screen.findByRole('heading', { level: 2, name: '1 error in the search' });
    expect(api.calls.filter((call) => call.url.pathname.startsWith('/api/v1/providers'))).toHaveLength(0);
    expect(screen.getByRole('link', { name: BAD_NPI })).toBeInTheDocument();
    expect(describedText(npiBox())).toContain(BAD_NPI);
    expect(npiBox()).toHaveValue('12345');
  });

  it.each(['1003000111x', '10030001110', ' abc '])('[MAM-77 AC-4] the NPI "%s" is not 10 digits and is not searched', async (value) => {
    const { user } = await openSearch(searchRoutes([RIVERA]));

    await search(user, { npi: value });

    await screen.findByRole('heading', { level: 2, name: '1 error in the search' });
    expect(searchCalls()).toHaveLength(0);
  });

  it('[MAM-77 AC-4] after the correction the search is made and the summary is gone', async () => {
    const { user } = await openSearch(searchRoutes([RIVERA]));
    await search(user, { npi: '12345' });
    await screen.findByRole('heading', { level: 2, name: '1 error in the search' });

    await search(user, { npi: '1003000111' });

    await waitFor(() => expect(resultRows()).toHaveLength(1));
    expect(screen.queryByRole('heading', { level: 2, name: '1 error in the search' })).toBeNull();
    expect(npiBox()).not.toHaveAttribute('aria-invalid', 'true');
  });
});

describe('[MAM-77 AC-5] an unknown provider shows "No provider found" (journey J4)', () => {
  it('[MAM-77 AC-5] the API answers 200 with no items and the page shows "No provider found" in a status area with no results table', async () => {
    const { user } = await openSearch(searchRoutes([RIVERA]));

    await search(user, { npi: '1003000999' });

    await screen.findByText('No provider found');
    expect(screen.getAllByRole('status').some((area) => text(area).includes('No provider found'))).toBe(true);
    expect(resultsTable()).toBeNull();
    expect(paramsOf(searchCalls().at(-1))).toEqual({ npi: '1003000999' });
  });

  it('[MAM-77 AC-5] a new search replaces the message by the results', async () => {
    const { user } = await openSearch(searchRoutes([RIVERA]));
    await search(user, { npi: '1003000999' });
    await screen.findByText('No provider found');

    await search(user, { npi: '1003000111' });

    await waitFor(() => expect(resultRows()).toHaveLength(1));
    expect(screen.queryByText('No provider found')).toBeNull();
  });
});

describe('[MAM-77 AC-6] the last NPI is kept for the next inquiry', () => {
  it('[MAM-77 AC-6] after a search for 1003000111 and its detail page the NPI input is pre-filled on the return visit and nothing is searched until Search', async () => {
    const { user } = await openSearch(searchRoutes([RIVERA, SECOND]));
    await search(user, { npi: '1003000111' });
    await waitFor(() => expect(resultRows()).toHaveLength(1));
    await user.click(screen.getByRole('link', { name: '1003000111' }));
    await screen.findByRole('heading', { level: 1, name: 'Provider detail' });
    const before = searchCalls().length;

    await user.click(screen.getByRole('link', { name: 'Provider search' }));

    await screen.findByRole('heading', { level: 1, name: 'Provider search' });
    expect(npiBox()).toHaveValue('1003000111');
    expect(resultsTable()).toBeNull();
    expect(searchCalls()).toHaveLength(before);
  });

  it('[MAM-77 AC-6] after the search for 1003000222 that NPI is the one kept', async () => {
    const first = await openSearch(searchRoutes([RIVERA, SECOND]));
    await search(first.user, { npi: '1003000111' });
    await waitFor(() => expect(resultRows()).toHaveLength(1));
    await search(first.user, { npi: '1003000222' });
    await waitFor(() => expect(resultRows()[0]?.[0]).toBe('1003000222'));
    first.unmount();

    await openSearch(searchRoutes([RIVERA, SECOND]));

    expect(npiBox()).toHaveValue('1003000222');
  });

  it('[MAM-77 AC-6] a new tab (a new session storage) shows an empty NPI input, as on a first visit', async () => {
    const first = await openSearch(searchRoutes([RIVERA]));
    await search(first.user, { npi: '1003000111' });
    await waitFor(() => expect(resultRows()).toHaveLength(1));
    first.unmount();
    window.sessionStorage.clear();

    await openSearch(searchRoutes([RIVERA]));

    expect(npiBox()).toHaveValue('');
  });

  it('[MAM-77 AC-6] an NPI that was rejected as malformed is not kept', async () => {
    const first = await openSearch(searchRoutes([RIVERA]));
    await search(first.user, { npi: '12345' });
    await screen.findByRole('heading', { level: 2, name: '1 error in the search' });
    first.unmount();

    await openSearch(searchRoutes([RIVERA]));

    expect(npiBox()).toHaveValue('');
  });
});

describe('[MAM-77 AC-7] failures are shown, never swallowed', () => {
  async function failing(answer: () => Response) {
    const rendered = await openSearch(searchRoutes([RIVERA], { [PROVIDERS_ROUTE]: answer }));
    await search(rendered.user, { npi: '1003000111' });
    return rendered;
  }

  it('[MAM-77 AC-7] a 401 sends the analyst to sign in again', async () => {
    await failing(() => response(401, problem(401, 'Not authenticated')));

    await waitFor(() => expect(oidc.signinRedirect).toHaveBeenCalled());
  });

  it('[MAM-77 AC-7] a 403 reads "You are not allowed to search providers"', async () => {
    await failing(() => response(403, problem(403, 'Forbidden')));

    expect(await screen.findByText('You are not allowed to search providers')).toBeInTheDocument();
    expect(screen.queryByText('No provider found')).toBeNull();
    expect(resultsTable()).toBeNull();
  });

  it('[MAM-77 AC-7] a 400 shows the detail the API gives', async () => {
    const detail = 'npi must be 10 digits (message of this test)';
    await failing(() => response(400, { title: 'Bad Request', status: 400, detail }));

    expect(await screen.findByText(detail)).toBeInTheDocument();
    expect(screen.queryByText('No provider found')).toBeNull();
    expect(resultsTable()).toBeNull();
  });

  it.each([
    ['500', () => response(500, problem(500, 'Internal Server Error'))],
    ['503', () => response(503, problem(503, 'Service Unavailable'))],
    [
      'network failure',
      () => {
        throw new TypeError('Failed to fetch');
      },
    ],
  ])('[MAM-77 AC-7] a %s reads "The search could not be completed. Try again." and is not "No provider found"', async (_label, answer) => {
    await failing(answer);

    expect(await screen.findByText('The search could not be completed. Try again.')).toBeInTheDocument();
    expect(screen.queryByText('No provider found')).toBeNull();
    expect(resultsTable()).toBeNull();
  });

  it('[MAM-77 AC-7] after a failure the same search can be made again and shows the results', async () => {
    let up = false;
    const { user } = await openSearch(searchRoutes([RIVERA], { [PROVIDERS_ROUTE]: () => (up ? response(200, { items: [RIVERA], page: 0, size: 20, total: 1 }) : response(503, problem(503, 'x'))) }));
    await search(user, { npi: '1003000111' });
    await screen.findByText('The search could not be completed. Try again.');
    up = true;

    await user.click(searchButton());

    await waitFor(() => expect(resultRows()).toHaveLength(1));
    expect(screen.queryByText('The search could not be completed. Try again.')).toBeNull();
  });
});

describe('[MAM-77 AC-8] keyboard and structure', () => {
  it('[MAM-77 AC-8] Enter in any of the three inputs starts the search', async () => {
    const { user } = await openSearch(searchRoutes([RIVERA]));

    await user.type(nameBox(), 'RIVERA{Enter}');
    await waitFor(() => expect(resultRows()).toHaveLength(1));
    await user.type(medicaidBox(), 'TX0000111{Enter}');
    await waitFor(() => expect(searchCalls()).toHaveLength(2));
    await user.type(npiBox(), '1003000111{Enter}');

    await waitFor(() => expect(searchCalls()).toHaveLength(3));
  });

  it('[MAM-77 AC-8] the results table has header cells and status and risk are text with their code', async () => {
    const { user } = await openSearch(searchRoutes([RIVERA]));
    await search(user, { npi: '1003000111' });
    await waitFor(() => expect(resultRows()).toHaveLength(1));

    expect(resultsTable()!.querySelectorAll('thead th').length).toBe(5);
    expect(resultRows()[0]?.[3]).toBe('Active (A)');
    expect(resultRows()[0]?.[4]).toBe('Limited (L)');
  });
});
