// MAM-70 AC-1..AC-6 (component level): /portal/notices lists the notices notification-service sent to the signed-in provider
// (D-012, D-023, D-024): date, plain name with the template code, channel and delivery state as words. Only my own notices.
import { screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { problem, response } from '../support/fakeApi';
import { answer, notice, noticeCalls, noticePage, noticeRows, noticesTable, NOTICES_ROUTE, numbered, openNotices, paramsOf } from '../support/notices';
import { api, oidc, setupShellWorld, text } from '../support/world';

vi.mock('oidc-client-ts', async (importOriginal) => {
  const actual = await importOriginal<typeof import('oidc-client-ts')>();
  const { FakeUserManager } = await import('../support/fakeOidc');
  return { ...actual, UserManager: FakeUserManager };
});

setupShellWorld();

const NOT_LOADED = 'The notices could not be loaded. Try again.';

async function rowsWhenLoaded(): Promise<string[][]> {
  await waitFor(() => expect(noticesTable()).not.toBeNull());
  return noticeRows();
}

describe('[MAM-70 AC-1] the approval notice is listed (journey J1)', () => {
  it('[MAM-70 AC-1] the page calls GET /api/v1/notices with no npi parameter', async () => {
    await openNotices({ [NOTICES_ROUTE]: answer([notice()]) });
    await rowsWhenLoaded();

    expect(noticeCalls().length).toBeGreaterThan(0);
    expect(paramsOf(noticeCalls()[0])).toEqual({});
  });

  it('[MAM-70 AC-1] the table has the columns Sent, Notice, Channel and Status and the row 5 Mar 2026, "Enrolment approved (APPROVED)", Email, Sent', async () => {
    await openNotices({ [NOTICES_ROUTE]: answer([notice()]) });
    await rowsWhenLoaded();

    expect(within(noticesTable()!).getAllByRole('columnheader').map((header) => text(header))).toEqual(['Sent', 'Notice', 'Channel', 'Status']);
    expect(noticeRows()).toEqual([['5 Mar 2026', 'Enrolment approved (APPROVED)', 'Email', 'Sent']]);
  });

  it('[MAM-70 AC-1] the page has one h1 "My notices" and the table is named', async () => {
    await openNotices({ [NOTICES_ROUTE]: answer([notice()]) });
    await rowsWhenLoaded();

    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1);
    expect(noticesTable()?.querySelector('caption')?.textContent).toBe('Notices');
  });
});

describe('[MAM-70 AC-2] the rejection notice is listed (journey J2)', () => {
  it('[MAM-70 AC-2] a REJECTED notice reads "Application rejected (REJECTED)"', async () => {
    await openNotices({ [NOTICES_ROUTE]: answer([notice({ template: 'REJECTED' })]) });

    expect((await rowsWhenLoaded())[0]?.[1]).toBe('Application rejected (REJECTED)');
  });
});

describe('[MAM-70 AC-3] every other template has a plain name and its code', () => {
  it('[MAM-70 AC-3] DENIED, TRM, SUS, RST, GEN and REVALIDATION_DUE read as named, and an unknown template is just its code', async () => {
    const templates = ['DENIED', 'TRM', 'SUS', 'RST', 'GEN', 'REVALIDATION_DUE', 'NEW_UNKNOWN_TEMPLATE'];
    const items = templates.map((template) => numbered({ template: template as never }));
    await openNotices({ [NOTICES_ROUTE]: answer(items) });

    expect((await rowsWhenLoaded()).map((row) => row[1])).toEqual([
      'Enrolment denied (DENIED)',
      'Termination notice (TRM)',
      'Suspension notice (SUS)',
      'Reinstatement notice (RST)',
      'General notice (GEN)',
      'Revalidation due (REVALIDATION_DUE)',
      'NEW_UNKNOWN_TEMPLATE',
    ]);
  });
});

describe('[MAM-70 AC-4] delivery state is written as text', () => {
  it('[MAM-70 AC-4] SENT reads "Sent" and FAILED reads "Failed", as words in the Status column', async () => {
    await openNotices({ [NOTICES_ROUTE]: answer([numbered({ status: 'SENT' }), numbered({ status: 'FAILED', template: 'DENIED' })]) });

    expect((await rowsWhenLoaded()).map((row) => row[3])).toEqual(['Sent', 'Failed']);
  });
});

describe('[MAM-70 AC-5] only my own notices are shown', () => {
  it('[MAM-70 AC-5] with notices of 1003000111 and 1003000222 in the answer only those of 1003000111 are shown', async () => {
    const mine = numbered({ template: 'APPROVED' });
    const theirs = numbered({ npi: '1003000222', template: 'TRM' });
    await openNotices({ [NOTICES_ROUTE]: answer([mine, theirs]) });

    const rows = await rowsWhenLoaded();
    expect(rows).toHaveLength(1);
    expect(rows[0]?.[1]).toBe('Enrolment approved (APPROVED)');
    expect(text(screen.getByRole('main'))).not.toContain('Termination notice');
  });

  it('[MAM-70 AC-5] the page has no input and asks for no NPI: the request has no npi parameter and the address has none', async () => {
    await openNotices({ [NOTICES_ROUTE]: answer([notice()]) });
    await rowsWhenLoaded();

    expect(within(screen.getByRole('main')).queryAllByRole('textbox')).toHaveLength(0);
    expect(within(screen.getByRole('main')).queryAllByRole('combobox')).toHaveLength(0);
    for (const call of noticeCalls()) expect(call.url.searchParams.has('npi')).toBe(false);
    expect(window.location.search).toBe('');
    expect(window.location.pathname).toBe('/portal/notices');
  });
});

describe('[MAM-70 AC-6] empty, long and failed lists are handled', () => {
  it('[MAM-70 AC-6] with no notices the page says "No notices yet"', async () => {
    await openNotices({ [NOTICES_ROUTE]: answer([]) });

    expect(await screen.findByText('No notices yet')).toBeInTheDocument();
    expect(noticesTable()).toBeNull();
  });

  it('[MAM-70 AC-6] with more than 20 notices the page shows the total and paging controls; Next asks for page 1', async () => {
    const twenty = Array.from({ length: 20 }, () => numbered());
    const { user } = await openNotices({ [NOTICES_ROUTE]: answer(twenty, 45) });
    await waitFor(() => expect(noticeRows()).toHaveLength(20));

    expect(text(screen.getByRole('main'))).toMatch(/45/);
    expect(paramsOf(noticeCalls()[0])).toEqual({});
    await user.click(screen.getByRole('button', { name: /Next/ }));
    await waitFor(() => expect(paramsOf(noticeCalls().at(-1))['page']).toBe('1'));
  });

  it('[MAM-70 AC-6] 20 notices or fewer show no paging controls', async () => {
    await openNotices({ [NOTICES_ROUTE]: answer([notice()]) });
    await rowsWhenLoaded();

    expect(screen.queryByRole('button', { name: /Next/ })).toBeNull();
  });

  it.each([
    ['401', () => response(401, problem(401, 'Not authenticated'))],
    ['500', () => response(500, problem(500, 'Internal Server Error'))],
    ['503', () => response(503, problem(503, 'Service Unavailable'))],
    [
      'network failure',
      () => {
        throw new TypeError('Failed to fetch');
      },
    ],
  ])('[MAM-70 AC-6] %s shows "The notices could not be loaded. Try again." and never "No notices yet"', async (_label, failure) => {
    await openNotices({ [NOTICES_ROUTE]: failure });

    expect(await screen.findByText(NOT_LOADED)).toBeInTheDocument();
    expect(screen.queryByText('No notices yet')).toBeNull();
    expect(noticesTable()).toBeNull();
  });

  it('[MAM-70 AC-6] after a 401 the page stays on /portal/notices with the message (it does not hide the failure behind a sign-in redirect)', async () => {
    await openNotices({ [NOTICES_ROUTE]: () => response(401, problem(401, 'Not authenticated')) });

    await screen.findByText(NOT_LOADED);
    expect(oidc.signinRedirect).not.toHaveBeenCalled();
    expect(screen.getByRole('heading', { level: 1, name: 'My notices' })).toBeInTheDocument();
  });

  it('[MAM-70 AC-6] Retry asks again and shows the notices when the service has recovered', async () => {
    let up = false;
    const { user } = await openNotices({ [NOTICES_ROUTE]: () => (up ? response(200, noticePage([notice()])) : response(503, problem(503, 'x'))) });
    await screen.findByText(NOT_LOADED);
    up = true;

    await user.click(screen.getByRole('button', { name: 'Retry' }));

    await waitFor(() => expect(noticeRows()).toHaveLength(1));
    expect(screen.queryByText(NOT_LOADED)).toBeNull();
    expect(api.to('GET', '/api/v1/notices').length).toBeGreaterThan(1);
  });
});
