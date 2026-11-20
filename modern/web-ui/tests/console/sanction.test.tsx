// MAM-90 AC-1..AC-7 (component level): a supervisor terminates, suspends or reinstates a provider on
// /console/providers/:npi/sanctions/new (D-006, D-013, D-018, D-026, D-027). The page sends POST /api/v1/sanctions, shows what the
// service answers, keeps the history, never blocks an action by the current status and never claims what did not happen (D-024).
import { screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { problem, response } from '../support/fakeApi';
import { api, installApi, renderApp, setupShellWorld, signedInAs, text } from '../support/world';
import { openDetail, rivera, RIVERA_NPI } from '../support/detail';
import {
  actionList,
  dateBox,
  describedText,
  formPath,
  historyRows,
  main,
  npiBox,
  openForm,
  posts,
  reasonBox,
  record,
  recordButton,
  SANCTION_ROUTE,
  sanctionWorld,
  sentBody,
  HISTORY_ROUTE,
} from '../support/sanction';

vi.mock('oidc-client-ts', async (importOriginal) => {
  const actual = await importOriginal<typeof import('oidc-client-ts')>();
  const { FakeUserManager } = await import('../support/fakeOidc');
  return { ...actual, UserManager: FakeUserManager };
});

setupShellWorld();

const HOSPICE = '1003000333';
const METRO = '1003000777';
const NOT_FOUND_NPI = '1003000999';

describe('[MAM-90 AC-1] only supervisors reach the sanction form', () => {
  it('[MAM-90 AC-1] supervisor1 sees "Record sanction" on the detail page and it opens the form of that provider', async () => {
    const { user, path } = await openDetail(rivera(), {}, 'supervisor1');
    const link = await screen.findByRole('link', { name: 'Record sanction' });

    expect(link).toHaveAttribute('href', `/console/providers/${RIVERA_NPI}/sanctions/new`);
    await user.click(link);
    expect(await screen.findByRole('heading', { level: 1, name: 'Record a sanction' })).toBeInTheDocument();
    expect(path()).toBe(formPath(RIVERA_NPI));
  });

  it.each(['analyst1', 'admin1'] as const)('[MAM-90 AC-1] %s does not see "Record sanction" on the detail page', async (who) => {
    await openDetail(rivera(), {}, who);
    await screen.findByRole('heading', { level: 2, name: 'Status: Active (A)' });

    expect(screen.queryByRole('link', { name: 'Record sanction' })).toBeNull();
  });

  it.each(['analyst1', 'admin1', 'provider1'] as const)('[MAM-90 AC-1] typing the address of the form as %s shows the access-denied page and no sanction request', async (who) => {
    signedInAs(who);
    installApi(sanctionWorld().routes);
    renderApp(formPath(RIVERA_NPI));

    expect(await screen.findByRole('heading', { level: 1, name: 'Access denied' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Record sanction' })).toBeNull();
    expect(api.calls.filter((call) => call.url.pathname === '/api/v1/sanctions')).toHaveLength(0);
  });
});

describe('[MAM-90 AC-2] the form and its required values', () => {
  it('[MAM-90 AC-2] the form shows the NPI (read-only), the Action list, the Reason (60) and the Effective date (MM/DD/YYYY)', async () => {
    await openForm(RIVERA_NPI, sanctionWorld().routes);

    expect(npiBox()).toHaveValue(RIVERA_NPI);
    expect(npiBox()).toHaveAttribute('readonly');
    expect(Array.from(actionList().options).map((option) => option.textContent)).toEqual(['Choose...', 'Terminate (T)', 'Suspend (S)', 'Reinstate (R)']);
    expect(Array.from(actionList().options).map((option) => option.value)).toEqual(['', 'T', 'S', 'R']);
    expect(reasonBox()).toHaveAttribute('maxlength', '60');
    expect(dateBox().labels?.[0]?.textContent).toContain('MM/DD/YYYY');
    expect(recordButton()).toBeEnabled();
  });

  it('[MAM-90 AC-2] "Record sanction" with no action and no date sends nothing and shows field errors and an error summary', async () => {
    const { user } = await openForm(RIVERA_NPI, sanctionWorld().routes);

    await user.click(recordButton());

    expect(await screen.findByRole('heading', { level: 2, name: '2 errors in the sanction' })).toBeInTheDocument();
    expect(posts()).toHaveLength(0);
    expect(describedText(actionList())).toContain('Choose an action');
    expect(describedText(dateBox())).toContain('Enter the effective date as MM/DD/YYYY');
    expect(actionList()).toHaveAttribute('aria-invalid', 'true');
    expect(dateBox()).toHaveAttribute('aria-invalid', 'true');
  });

  it('[MAM-90 AC-2] the summary links move the focus to the field, and the focus is first in the summary', async () => {
    const { user } = await openForm(RIVERA_NPI, sanctionWorld().routes);
    await user.click(recordButton());
    const heading = await screen.findByRole('heading', { level: 2, name: '2 errors in the sanction' });

    await waitFor(() => expect(document.activeElement?.contains(heading)).toBe(true));
    await user.click(main().getByRole('link', { name: 'Choose an action' }));
    expect(actionList()).toHaveFocus();
    await user.click(main().getByRole('link', { name: 'Enter the effective date as MM/DD/YYYY' }));
    expect(dateBox()).toHaveFocus();
  });

  it.each(['02/30/2026', '13/01/2026', '31/03/2026', 'tomorrow', '2026-03-31'])('[MAM-90 AC-2] the impossible date "%s" is not sent and is reported at the date field', async (date) => {
    const { user } = await openForm(RIVERA_NPI, sanctionWorld().routes);

    await record(user, { action: 'S', date });

    expect(await screen.findByRole('heading', { level: 2, name: '1 error in the sanction' })).toBeInTheDocument();
    expect(posts()).toHaveLength(0);
    expect(describedText(dateBox())).toContain('Enter a real date as MM/DD/YYYY');
    expect(dateBox()).toHaveValue(date);
  });

  it('[MAM-90 AC-2] what was typed is kept after the errors', async () => {
    const { user } = await openForm(RIVERA_NPI, sanctionWorld().routes);

    await record(user, { reason: 'Licence under review' });

    await screen.findByRole('heading', { level: 2, name: '2 errors in the sanction' });
    expect(reasonBox()).toHaveValue('Licence under review');
  });

  it('[MAM-90 AC-2] a valid date 03/31/2026 is sent in the ISO form 2026-03-31, with no reason when none is typed', async () => {
    const { user } = await openForm(RIVERA_NPI, sanctionWorld().routes);

    await record(user, { action: 'S', date: '03/31/2026' });

    await waitFor(() => expect(posts()).toHaveLength(1));
    expect(sentBody(posts()[0])).toEqual({ npi: RIVERA_NPI, action: 'S', effectiveDate: '2026-03-31' });
  });
});

describe('[MAM-90 AC-3] each action sets its own status', () => {
  it.each([
    ['S', HOSPICE, 'Suspended (S)'],
    ['T', METRO, 'Terminated (T)'],
    ['R', HOSPICE, 'Active (A)'],
  ] as const)('[MAM-90 AC-3] action %s for %s sends POST /api/v1/sanctions with npi, action, reason and 2026-03-31 and confirms "%s"', async (action, npi, status) => {
    const { user } = await openForm(npi, sanctionWorld().routes);

    await record(user, { action, reason: 'Reason of this test', date: '03/31/2026' });

    const confirmation = await screen.findByText(/Sanction recorded/);
    expect(text(confirmation)).toContain(status);
    expect(sentBody(posts()[0])).toEqual({ npi, action, reason: 'Reason of this test', effectiveDate: '2026-03-31' });
    expect(posts()[0]?.headers.get('authorization')).toMatch(/^Bearer \S+/);
    expect(posts()[0]?.url.origin).toBe('http://localhost:8080');
  });

  it('[MAM-90 AC-3] a suspension is never shown as Denied', async () => {
    const { user } = await openForm(HOSPICE, sanctionWorld().routes);

    await record(user, { action: 'S', reason: 'x', date: '03/31/2026' });

    const confirmation = await screen.findByText(/Sanction recorded/);
    expect(text(confirmation)).not.toContain('Denied');
  });

  it('[MAM-90 AC-3] the form can record the next action on the same page (Suspend, then Reinstate)', async () => {
    const { user } = await openForm(HOSPICE, sanctionWorld().routes);
    await record(user, { action: 'S', reason: 'First', date: '03/31/2026' });
    expect(text(await screen.findByText(/Sanction recorded/))).toContain('Suspended (S)');

    await record(user, { action: 'R', reason: 'Second', date: '04/15/2026' });

    await waitFor(() => expect(text(screen.getByText(/Sanction recorded/))).toContain('Active (A)'));
    expect(posts().map((call) => sentBody(call)['action'])).toEqual(['S', 'R']);
  });
});

describe('[MAM-90 AC-4] suspend and reinstate keep a history (journey J5)', () => {
  it('[MAM-90 AC-4] the page reads GET /api/v1/sanctions?npi=1003000111 and shows "None recorded" before any action', async () => {
    await openForm(RIVERA_NPI, sanctionWorld().routes);

    await waitFor(() => expect(api.to('GET', '/api/v1/sanctions').length).toBeGreaterThan(0));
    expect(Object.fromEntries(api.to('GET', '/api/v1/sanctions')[0]!.url.searchParams.entries())).toEqual({ npi: RIVERA_NPI });
    expect(await screen.findByText('None recorded')).toBeInTheDocument();
  });

  it('[MAM-90 AC-4] after Suspend the page confirms "Suspended (S)", after Reinstate "Active (A)", and the history lists both rows', async () => {
    const { user } = await openForm(RIVERA_NPI, sanctionWorld().routes);
    await record(user, { action: 'S', reason: 'Licence under review', date: '03/10/2026' });
    expect(text(await screen.findByText(/Sanction recorded/))).toContain('Suspended (S)');
    await waitFor(() => expect(historyRows()).toHaveLength(1));

    await record(user, { action: 'R', reason: 'Licence confirmed', date: '03/20/2026' });

    await waitFor(() => expect(historyRows()).toHaveLength(2));
    expect(text(screen.getByText(/Sanction recorded/))).toContain('Active (A)');
    expect(historyRows()).toEqual([
      ['20 Mar 2026', 'Reinstate (R)', 'Licence confirmed', 'supervisor1'],
      ['10 Mar 2026', 'Suspend (S)', 'Licence under review', 'supervisor1'],
    ]);
    const table = screen.getAllByRole('table').find((candidate) => candidate.querySelector('caption')?.textContent === 'Sanction history')!;
    expect(within(table).getAllByRole('columnheader').map((header) => text(header))).toEqual(['Effective date', 'Action', 'Reason', 'By']);
  });

  it('[MAM-90 AC-4] a history that cannot be loaded says so and is not shown as "None recorded"', async () => {
    await openForm(RIVERA_NPI, sanctionWorld({ [HISTORY_ROUTE]: () => response(500, problem(500, 'x')) }).routes);

    expect(await screen.findByText('The sanction history could not be loaded')).toBeInTheDocument();
    expect(screen.queryByText('None recorded')).toBeNull();
  });
});

describe('[MAM-90 AC-5] an unknown action code is rejected at the field (journey J5)', () => {
  const DETAIL = 'Field action must be one of T, S or R (T terminate, S suspend, R reinstate).';

  async function rejected() {
    const world = sanctionWorld({ [SANCTION_ROUTE]: () => response(422, { title: 'Unprocessable Entity', status: 422, detail: DETAIL }) });
    const rendered = await openForm(RIVERA_NPI, world.routes);
    await record(rendered.user, { action: 'S', reason: 'x', date: '03/31/2026' });
    await screen.findByRole('heading', { level: 2, name: '1 error in the sanction' });
    return rendered;
  }

  it('[MAM-90 AC-5] the message of the 422 is shown next to the Action field and in the error summary', async () => {
    await rejected();

    expect(describedText(actionList())).toContain(DETAIL);
    expect(actionList()).toHaveAttribute('aria-invalid', 'true');
    expect(main().getAllByText(DETAIL)).toHaveLength(2);
  });

  it('[MAM-90 AC-5] the status and the history shown on the page are unchanged', async () => {
    await rejected();

    expect(screen.queryByText(/Sanction recorded/)).toBeNull();
    expect(historyRows()).toEqual([]);
    expect(await screen.findByText('None recorded')).toBeInTheDocument();
  });

  it('[MAM-90 AC-5] a 422 for another field is shown at that field (effectiveDate)', async () => {
    const world = sanctionWorld({ [SANCTION_ROUTE]: () => response(422, { title: 'Unprocessable Entity', status: 422, detail: 'Field effectiveDate is required.' }) });
    const { user } = await openForm(RIVERA_NPI, world.routes);

    await record(user, { action: 'S', date: '03/31/2026' });

    await screen.findByRole('heading', { level: 2, name: '1 error in the sanction' });
    expect(describedText(dateBox())).toContain('Field effectiveDate is required.');
  });
});

describe('[MAM-90 AC-6] no status is blocked by the page', () => {
  it('[MAM-90 AC-6] all three actions are offered whatever the status of the provider (the page does not look at it)', async () => {
    await openForm(RIVERA_NPI, sanctionWorld().routes);

    expect(Array.from(actionList().options).filter((option) => option.value !== '').map((option) => option.value)).toEqual(['T', 'S', 'R']);
    for (const option of Array.from(actionList().options)) expect(option).toBeEnabled();
    expect(api.calls.filter((call) => call.url.pathname.startsWith('/api/v1/providers/'))).toHaveLength(0);
  });

  it('[MAM-90 AC-6] Reinstate (R) for a denied provider is sent, and the page shows the status the service returns', async () => {
    const world = sanctionWorld({
      [SANCTION_ROUTE]: () => response(201, { sanctionId: '4c8a1f20-6d3b-4b7e-9a15-2e7f0b3c9d90', npi: '1003000444', action: 'R', effectiveDate: '2026-03-31', recordedAt: '2026-03-31T10:00:00Z', resultingStatus: 'A' }),
    });
    const { user } = await openForm('1003000444', world.routes);

    await record(user, { action: 'R', reason: 'Owner removed from the list', date: '03/31/2026' });

    expect(text(await screen.findByText(/Sanction recorded/))).toContain('Active (A)');
    expect(sentBody(posts()[0])['action']).toBe('R');
  });
});

describe('[MAM-90 AC-7] unknown providers and failures are shown, nothing is claimed that did not happen', () => {
  async function recordWith(answer: Response | (() => Response), npi = NOT_FOUND_NPI) {
    const world = sanctionWorld({ [SANCTION_ROUTE]: typeof answer === 'function' ? answer : () => answer });
    const rendered = await openForm(npi, world.routes);
    await record(rendered.user, { action: 'S', reason: 'x', date: '03/31/2026' });
    return rendered;
  }

  it('[MAM-90 AC-7] a 404 shows "No provider found" and records nothing', async () => {
    await recordWith(response(404, problem(404, 'Not Found')));

    expect(await screen.findByText('No provider found')).toBeInTheDocument();
    expect(screen.queryByText(/Sanction recorded/)).toBeNull();
    expect(historyRows()).toEqual([]);
  });

  it('[MAM-90 AC-7] a 403 shows "You are not allowed to record sanctions"', async () => {
    await recordWith(response(403, problem(403, 'Forbidden')), RIVERA_NPI);

    expect(await screen.findByText('You are not allowed to record sanctions')).toBeInTheDocument();
    expect(screen.queryByText(/Sanction recorded/)).toBeNull();
  });

  it.each([
    ['500', () => response(500, problem(500, 'Internal Server Error'))],
    ['503 (provider-service did not change the status)', () => response(503, { title: 'Service Unavailable', status: 503, detail: 'The sanction for NPI 1003000111 could not be applied. Nothing was recorded; try again later.' })],
    [
      'network failure',
      () => {
        throw new TypeError('Failed to fetch');
      },
    ],
  ])('[MAM-90 AC-7] %s shows "The sanction could not be applied. Nothing was recorded." and the history and status stay as they were', async (_label, answer) => {
    await recordWith(answer, RIVERA_NPI);

    expect(await screen.findByText('The sanction could not be applied. Nothing was recorded.')).toBeInTheDocument();
    expect(screen.queryByText(/Sanction recorded/)).toBeNull();
    expect(historyRows()).toEqual([]);
  });

  it('[MAM-90 AC-7] after a failure the focus is in the message, what was typed is kept and the same sanction can be recorded again', async () => {
    const answers = [() => response(503, problem(503, 'x')), undefined];
    const world = sanctionWorld({
      [SANCTION_ROUTE]: (call) => {
        const next = answers.shift();
        return next ? next() : sanctionWorld().routes[SANCTION_ROUTE]!(call);
      },
    });
    const { user } = await openForm(RIVERA_NPI, world.routes);
    await record(user, { action: 'S', reason: 'Kept reason', date: '03/31/2026' });
    const heading = await screen.findByText('The sanction could not be applied. Nothing was recorded.');
    await waitFor(() => expect(document.activeElement?.contains(heading)).toBe(true));
    expect(reasonBox()).toHaveValue('Kept reason');

    await user.click(recordButton());

    expect(text(await screen.findByText(/Sanction recorded/))).toContain('Suspended (S)');
    expect(posts()).toHaveLength(2);
  });
});
