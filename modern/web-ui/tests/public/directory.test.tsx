// MAM-73 AC-1..AC-8 (component level): the public directory /directory. Anyone, without signing in, searches the active
// providers by name and type (D-028, D-006, D-008). The page shows only what the API returns and sends no Authorization header.
import { screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { problem, response } from '../support/fakeApi';
import {
  directoryCalls,
  directoryPage,
  directoryRoutes,
  GROUP,
  HOSPICE,
  nameBox,
  openDirectory,
  paramsOf,
  resultRows,
  resultsTable,
  RIVERA,
  search,
  searchButton,
  typeList,
  type DirectoryEntry,
} from '../support/directory';
import { api, oidc, setupShellWorld, signedInAs, text } from '../support/world';

vi.mock('oidc-client-ts', async (importOriginal) => {
  const actual = await importOriginal<typeof import('oidc-client-ts')>();
  const { FakeUserManager } = await import('../support/fakeOidc');
  return { ...actual, UserManager: FakeUserManager };
});

setupShellWorld();

const ALL = [RIVERA, HOSPICE, GROUP];

describe('[MAM-73 AC-1] the page opens without signing in', () => {
  it('[MAM-73 AC-1] /directory shows the h1 "Find a Medicaid provider" without any redirect to Keycloak', async () => {
    await openDirectory(directoryRoutes(ALL));

    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1);
    expect(oidc.signinRedirect).not.toHaveBeenCalled();
    expect(screen.queryByText(/Checking your sign-in|Taking you to the sign-in page/)).toBeNull();
  });

  it('[MAM-73 AC-1] the form has an input "Name", a list "Provider type" (Any, Individual (IN), Group (GR), Facility (FA)) and a Search button', async () => {
    await openDirectory(directoryRoutes(ALL));

    expect(nameBox().tagName).toBe('INPUT');
    expect(Array.from(typeList().options).map((option) => option.textContent)).toEqual(['Any', 'Individual (IN)', 'Group (GR)', 'Facility (FA)']);
    expect(Array.from(typeList().options).map((option) => option.value)).toEqual(['', 'IN', 'GR', 'FA']);
    expect(searchButton().tagName).toBe('BUTTON');
  });

  it('[MAM-73 AC-1] none of the requests of the page carries an Authorization header, also when a user happens to be signed in', async () => {
    signedInAs('analyst1');
    const { user } = await openDirectory(directoryRoutes(ALL));
    await search(user, 'RIVERA');
    await waitFor(() => expect(resultRows()).toHaveLength(1));

    expect(api.calls.length).toBeGreaterThan(0);
    for (const call of api.calls) expect(call.headers.get('authorization')).toBeNull();
  });

  it('[MAM-73 AC-1] opening the page makes no request and shows no result table yet', async () => {
    await openDirectory(directoryRoutes(ALL));

    expect(directoryCalls()).toHaveLength(0);
    expect(resultsTable()).toBeNull();
  });
});

describe('[MAM-73 AC-2] search by name (journey J7)', () => {
  it('[MAM-73 AC-2] searching "HOSPICE" calls GET /api/v1/directory/providers?name=HOSPICE and lists SUNSET HOSPICE INC | 1003000333 | Facility (FA) | 251G00000X', async () => {
    const { user } = await openDirectory(directoryRoutes(ALL));

    await search(user, 'HOSPICE');

    await waitFor(() => expect(resultRows()).toHaveLength(1));
    expect(paramsOf(directoryCalls().at(-1))).toEqual({ name: 'HOSPICE' });
    expect(resultRows()).toEqual([['SUNSET HOSPICE INC', '1003000333', 'Facility (FA)', '251G00000X']]);
  });

  it('[MAM-73 AC-2] a second search "RIVERA" calls ?name=RIVERA and replaces the list by MARIA RIVERA | 1003000111 | Individual (IN) | 207Q00000X', async () => {
    const { user } = await openDirectory(directoryRoutes(ALL));
    await search(user, 'HOSPICE');
    await waitFor(() => expect(resultRows()).toHaveLength(1));

    await search(user, 'RIVERA');

    await waitFor(() => expect(resultRows()[0]?.[0]).toBe('MARIA RIVERA'));
    expect(paramsOf(directoryCalls().at(-1))).toEqual({ name: 'RIVERA' });
    expect(resultRows()).toEqual([['MARIA RIVERA', '1003000111', 'Individual (IN)', '207Q00000X']]);
  });

  it('[MAM-73 AC-2] the result table is named "Active providers"', async () => {
    const { user } = await openDirectory(directoryRoutes(ALL));
    await search(user, 'HOSPICE');

    await waitFor(() => expect(resultsTable()).not.toBeNull());
    expect(resultsTable()?.querySelector('caption')?.textContent).toBe('Active providers');
  });
});

describe('[MAM-73 AC-3] filter by provider type', () => {
  it('[MAM-73 AC-3] choosing Facility (FA) sends providerType=FA and lists only facilities; choosing Any sends no providerType and lists all types', async () => {
    const { user } = await openDirectory(directoryRoutes(ALL));

    await search(user, '', 'FA');
    await waitFor(() => expect(resultRows()).toHaveLength(1));
    expect(paramsOf(directoryCalls().at(-1))).toEqual({ providerType: 'FA' });
    expect(resultRows().map((row) => row[2])).toEqual(['Facility (FA)']);

    await search(user, '', '');
    await waitFor(() => expect(resultRows()).toHaveLength(3));
    expect(paramsOf(directoryCalls().at(-1))).toEqual({});
    expect(resultRows().map((row) => row[2])).toEqual(['Individual (IN)', 'Facility (FA)', 'Group (GR)']);
  });

  it('[MAM-73 AC-3] name and type together are both sent', async () => {
    const { user } = await openDirectory(directoryRoutes(ALL));

    await search(user, 'SUNSET', 'FA');

    await waitFor(() => expect(resultRows()).toHaveLength(1));
    expect(paramsOf(directoryCalls().at(-1))).toEqual({ name: 'SUNSET', providerType: 'FA' });
  });
});

describe('[MAM-73 AC-4] names are composed, not raw bytes', () => {
  it('[MAM-73 AC-4] the Name column shows displayName exactly as returned, never the licence number', async () => {
    const { user } = await openDirectory(directoryRoutes(ALL));

    await search(user, '');

    await waitFor(() => expect(resultRows()).toHaveLength(3));
    expect(resultRows().map((row) => row[0])).toEqual(['MARIA RIVERA', 'SUNSET HOSPICE INC', 'RIO GRANDE FAMILY MEDICINE PLLC']);
    expect(text(resultsTable())).not.toContain('MD12345');
  });

  it('[MAM-73 AC-4] a long name is written in full, not cut in the middle of a word', async () => {
    const long: DirectoryEntry = { ...GROUP, displayName: 'SOUTHWEST REGIONAL CENTER FOR ADVANCED PEDIATRIC AND FAMILY MEDICINE LLC' };
    const { user } = await openDirectory(directoryRoutes([long]));

    await search(user, '');

    await waitFor(() => expect(resultRows()).toHaveLength(1));
    expect(resultRows()[0]?.[0]).toBe(long.displayName);
  });
});

describe('[MAM-73 AC-5] only what the API returns is shown', () => {
  it('[MAM-73 AC-5] a name the API does not return (a suspended, pending, denied or terminated provider) is not listed', async () => {
    const { user } = await openDirectory(directoryRoutes([RIVERA]));

    await search(user, 'SUSPENDED CLINIC');

    expect(await screen.findByText('No providers found')).toBeInTheDocument();
    expect(resultsTable()).toBeNull();
  });

  it('[MAM-73 AC-5] the rows are exactly the items of the answer, nothing is added or filled in', async () => {
    const answer = directoryPage([RIVERA]);
    const { user } = await openDirectory({ 'GET /api/v1/directory/providers': () => response(200, answer) });

    await search(user, 'ANYTHING');

    await waitFor(() => expect(resultRows()).toHaveLength(1));
    expect(resultRows()).toEqual([['MARIA RIVERA', '1003000111', 'Individual (IN)', '207Q00000X']]);
  });
});

describe('[MAM-73 AC-6] only public columns are shown', () => {
  it('[MAM-73 AC-6] the table has exactly the columns Name, NPI, Type and Specialty (taxonomy)', async () => {
    const { user } = await openDirectory(directoryRoutes(ALL));
    await search(user, 'RIVERA');
    await waitFor(() => expect(resultRows()).toHaveLength(1));

    const headers = within(resultsTable()!).getAllByRole('columnheader').map((header) => text(header));
    expect(headers).toEqual(['Name', 'NPI', 'Type', 'Specialty (taxonomy)']);
  });

  it('[MAM-73 AC-6] the NPI is shown in full and neither the Medicaid ID nor the risk tier is displayed', async () => {
    const { user } = await openDirectory(directoryRoutes(ALL));
    await search(user, 'RIVERA');
    await waitFor(() => expect(resultRows()).toHaveLength(1));

    const main = text(screen.getByRole('main'));
    expect(resultRows()[0]?.[1]).toBe('1003000111');
    expect(main).not.toContain('TX0000111');
    expect(main).not.toMatch(/Limited|Moderate|High|risk/i);
    expect(main).not.toMatch(/address|phone|network/i);
  });
});

describe('[MAM-73 AC-7] empty, long, refused and failed searches are handled', () => {
  it('[MAM-73 AC-7] no match shows "No providers found"', async () => {
    const { user } = await openDirectory(directoryRoutes(ALL));

    await search(user, 'NOBODY');

    expect(await screen.findByText('No providers found')).toBeInTheDocument();
  });

  it('[MAM-73 AC-7] more than 20 matches show the total and paging controls; Next asks for page 1', async () => {
    const many = Array.from({ length: 20 }, (_, index) => ({ ...RIVERA, npi: `10031${String(index).padStart(5, '0')}`, displayName: `PROVIDER ${index}` }));
    const { user } = await openDirectory({ 'GET /api/v1/directory/providers': () => response(200, directoryPage(many, 45)) });

    await search(user, 'PROVIDER');

    await waitFor(() => expect(resultRows()).toHaveLength(20));
    expect(text(screen.getByRole('main'))).toMatch(/45/);
    expect(paramsOf(directoryCalls().at(-1))).toEqual({ name: 'PROVIDER' });
    await user.click(screen.getByRole('button', { name: /Next/ }));
    await waitFor(() => expect(paramsOf(directoryCalls().at(-1))['page']).toBe('1'));
    expect(paramsOf(directoryCalls().at(-1))['name']).toBe('PROVIDER');
  });

  it('[MAM-73 AC-7] 20 matches or fewer show no paging controls', async () => {
    const { user } = await openDirectory(directoryRoutes(ALL));

    await search(user, '');

    await waitFor(() => expect(resultRows()).toHaveLength(3));
    expect(screen.queryByRole('button', { name: /Next/ })).toBeNull();
  });

  it('[MAM-73 AC-7] a 400 shows the problem detail next to the search form, not "No providers found"', async () => {
    const detail = 'providerType must be IN, GR or FA (message of this test)';
    const { user } = await openDirectory({ 'GET /api/v1/directory/providers': () => response(400, { title: 'Bad Request', status: 400, detail }) });

    await search(user, 'RIVERA');

    const shown = await screen.findByText(detail);
    expect(screen.queryByText('No providers found')).toBeNull();
    const form = nameBox().closest('form');
    expect(form?.parentElement?.contains(shown)).toBe(true);
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
  ])('[MAM-73 AC-7] %s shows "The directory is not available right now. Try again." and never "No providers found"', async (_label, answer) => {
    const { user } = await openDirectory({ 'GET /api/v1/directory/providers': answer });

    await search(user, 'RIVERA');

    expect(await screen.findByText('The directory is not available right now. Try again.')).toBeInTheDocument();
    expect(screen.queryByText('No providers found')).toBeNull();
    expect(resultsTable()).toBeNull();
  });

  it('[MAM-73 AC-7] after a failure a new search shows the new answer and the failure message is gone', async () => {
    const answers = [() => response(503, problem(503, 'Service Unavailable')), () => response(200, directoryPage([RIVERA]))];
    const { user } = await openDirectory({ 'GET /api/v1/directory/providers': () => answers.shift()!() });
    await search(user, 'RIVERA');
    await screen.findByText('The directory is not available right now. Try again.');

    await search(user, 'RIVERA');

    await waitFor(() => expect(resultRows()).toHaveLength(1));
    expect(screen.queryByText('The directory is not available right now. Try again.')).toBeNull();
  });
});

describe('[MAM-73 AC-8] structure', () => {
  it('[MAM-73 AC-8] one h1, the landmarks banner and main, a "Skip to main content" link, labelled inputs and a table with header cells', async () => {
    const { user } = await openDirectory(directoryRoutes(ALL));
    await search(user, 'RIVERA');
    await waitFor(() => expect(resultRows()).toHaveLength(1));

    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1);
    // jsdom also gives the <header> inside the USWDS government banner (section.usa-banner) the role banner; the page has one site banner
    expect(screen.getAllByRole('banner').filter((banner) => !banner.closest('.usa-banner'))).toHaveLength(1);
    expect(screen.getByRole('main')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Skip to main content' })).toHaveAttribute('href', '#main-content');
    expect(nameBox().labels).toHaveLength(1);
    expect(typeList().labels).toHaveLength(1);
    expect(within(resultsTable()!).getAllByRole('columnheader').length).toBe(4);
  });

  it('[MAM-73 AC-8] Enter in the Name box starts the search (keyboard only)', async () => {
    const { user } = await openDirectory(directoryRoutes(ALL));

    await user.type(nameBox(), 'RIVERA{Enter}');

    await waitFor(() => expect(resultRows()).toHaveLength(1));
  });
});
