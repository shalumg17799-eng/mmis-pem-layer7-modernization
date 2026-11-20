// MAM-82 AC-1..AC-7 (component level): below the provider record the console detail page shows what happened to the provider:
// screening per source, sanctions, revalidation cases, notices and the audit trail. Each role sees only the panels it may read
// (a panel that is not shown is not requested, so no 403), and one failing panel never hides the others (D-024).
import { screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { problem, response } from '../support/fakeApi';
import { api, setupShellWorld, text } from '../support/world';
import { dueCase } from '../support/queues';
import { clinic, callsTo, findPanel, hasPanel, openDetail, panelRows, paramsOf, recordTable, rivera, RIVERA_NPI } from '../support/detail';
import { emptyPanels, fullPanels, page, panelRoutes, PANEL_CALLS, sanction } from '../support/panels';
import { screening, screeningPage, source } from '../support/approval';

vi.mock('oidc-client-ts', async (importOriginal) => {
  const actual = await importOriginal<typeof import('oidc-client-ts')>();
  const { FakeUserManager } = await import('../support/fakeOidc');
  return { ...actual, UserManager: FakeUserManager };
});

setupShellWorld();

const CLINIC_NPI = '1003000444';
const DUE_NPI = '1003000222';

describe('[MAM-82 AC-1] screening results per source (journey J3)', () => {
  it('[MAM-82 AC-1] the page calls GET /api/v1/screenings?npi=<npi> and the panel "Screening" shows the screening with its reason, date and overall result', async () => {
    await openDetail(clinic(), panelRoutes(CLINIC_NPI, fullPanels()));
    const panel = await findPanel('Screening');
    await waitFor(() => expect(text(panel)).toMatch(/Initial/));

    expect(callsTo('/api/v1/screenings').map((call) => paramsOf(call))).toContainEqual({ npi: CLINIC_NPI });
    expect(text(panel)).toMatch(/Initial screening on 2 Mar 2026/);
    expect(text(panel)).toMatch(/Fail/);
  });

  it('[MAM-82 AC-1] 1003000444: the table Source | Result | Verdict | Detail | Error code has the OIGLEIE row "Fail (F)" with its detail and "E203 owner excluded"', async () => {
    await openDetail(clinic(), panelRoutes(CLINIC_NPI, fullPanels()));
    const panel = await findPanel('Screening');
    await waitFor(() => expect(panelRows(panel).length).toBeGreaterThan(0));

    expect(within(panel).getAllByRole('columnheader').map((header) => text(header))).toEqual(['Source', 'Result', 'Verdict', 'Detail', 'Error code']);
    expect(panelRows(panel)).toContainEqual(['OIG exclusion list (LEIE)', 'Fail (F)', '2 Mar 2026', 'Owner 1990000111 is on the OIG exclusion list', 'E203 owner excluded']);
    expect(panelRows(panel)).toHaveLength(4);
  });

  it('[MAM-82 AC-1] 1003000111: the four sources read "Pass (P)"', async () => {
    await openDetail(rivera(), panelRoutes(RIVERA_NPI, fullPanels()));
    const panel = await findPanel('Screening');
    await waitFor(() => expect(panelRows(panel)).toHaveLength(4));

    expect(panelRows(panel).map((row) => [row[0], row[1]])).toEqual([
      ['Licence board', 'Pass (P)'],
      ['OIG exclusion list (LEIE)', 'Pass (P)'],
      ['SAM.gov debarment', 'Pass (P)'],
      ['SSA death records', 'Pass (P)'],
    ]);
  });

  it('[MAM-82 AC-1] a result C reads "Conditional (C)"', async () => {
    const data = fullPanels();
    data.screenings = () => screeningPage(screening(RIVERA_NPI, 'PASS', [source('LICBOARD', 'C', 'Licence expires within 30 days')]));
    await openDetail(rivera(), panelRoutes(RIVERA_NPI, data));
    const panel = await findPanel('Screening');

    await waitFor(() => expect(panelRows(panel)[0]?.[1]).toBe('Conditional (C)'));
  });

  it('[MAM-82 AC-1] a provider with no screening shows "Not screened yet"', async () => {
    await openDetail(rivera(), panelRoutes(RIVERA_NPI, emptyPanels()));
    const panel = await findPanel('Screening');

    await waitFor(() => expect(text(panel)).toContain('Not screened yet'));
    expect(panel.querySelector('table')).toBeNull();
  });
});

describe('[MAM-82 AC-2] the sanctions history (journey J5)', () => {
  it('[MAM-82 AC-2] the page calls GET /api/v1/sanctions?npi=1003000111 and the panel "Sanctions" lists Effective date | Action | Reason | By', async () => {
    await openDetail(rivera(), panelRoutes(RIVERA_NPI, fullPanels()));
    const panel = await findPanel('Sanctions');
    await waitFor(() => expect(panelRows(panel)).toHaveLength(2));

    expect(callsTo('/api/v1/sanctions').map((call) => paramsOf(call))).toContainEqual({ npi: RIVERA_NPI });
    expect(within(panel).getAllByRole('columnheader').map((header) => text(header))).toEqual(['Effective date', 'Action', 'Reason', 'By']);
    expect(panelRows(panel)).toEqual([
      ['20 Mar 2026', 'Reinstate (R)', 'Licence confirmed', 'supervisor1'],
      ['10 Mar 2026', 'Suspend (S)', 'Licence under review', 'supervisor1'],
    ]);
  });

  it('[MAM-82 AC-2] a termination reads "Terminate (T)"', async () => {
    const data = fullPanels();
    data.sanctions = page([sanction({ action: 'T', resultingStatus: 'T', reason: 'Exclusion confirmed' })]);
    await openDetail(rivera(), panelRoutes(RIVERA_NPI, data));
    const panel = await findPanel('Sanctions');

    await waitFor(() => expect(panelRows(panel)[0]?.[1]).toBe('Terminate (T)'));
  });

  it('[MAM-82 AC-2] a provider with no sanction shows "None recorded"', async () => {
    await openDetail(rivera(), panelRoutes(RIVERA_NPI, emptyPanels()));
    const panel = await findPanel('Sanctions');

    await waitFor(() => expect(text(panel)).toContain('None recorded'));
    expect(panel.querySelector('table')).toBeNull();
  });

  it('[MAM-82 AC-2] when there are more sanctions than the page holds the panel says how many are shown', async () => {
    const data = fullPanels();
    data.sanctions = page([sanction()], 45);
    await openDetail(rivera(), panelRoutes(RIVERA_NPI, data));
    const panel = await findPanel('Sanctions');

    await waitFor(() => expect(text(panel)).toMatch(/Showing 1 of 45/));
  });
});

describe('[MAM-82 AC-3] revalidation cases, one per provider and period', () => {
  it('[MAM-82 AC-3] the page calls GET /api/v1/revalidations?npi=1003000222 and the panel shows the due date of the provider and the table Due date | Case | Opened | Closed', async () => {
    const data = fullPanels();
    data.revalidations = page([
      dueCase({ npi: DUE_NPI, dueDate: '2026-02-28', status: 'DUE', openedAt: '2026-03-01T00:00:00Z' }),
      dueCase({ caseId: '6e1d2a90-7b3c-4c58-8d41-1f0a9c2b7e55', npi: DUE_NPI, dueDate: '2021-02-28', status: 'COMPLETED', openedAt: '2021-03-01T00:00:00Z', closedAt: '2021-03-15T00:00:00Z' }),
    ]);
    await openDetail(rivera({ npi: DUE_NPI, revalidationDueDate: '2026-02-28' }), panelRoutes(DUE_NPI, data));
    const panel = await findPanel('Revalidation');
    await waitFor(() => expect(panelRows(panel)).toHaveLength(2));

    expect(callsTo('/api/v1/revalidations').map((call) => paramsOf(call))).toContainEqual({ npi: DUE_NPI });
    expect(text(panel)).toContain('Revalidation due: 28 Feb 2026');
    expect(within(panel).getAllByRole('columnheader').map((header) => text(header))).toEqual(['Due date', 'Case', 'Opened', 'Closed']);
    expect(panelRows(panel)).toEqual([
      ['28 Feb 2026', 'Due', '1 Mar 2026', '-'],
      ['28 Feb 2021', 'Completed', '1 Mar 2021', '15 Mar 2021'],
    ]);
  });

  it.each([
    ['DUE', 'Due'],
    ['IN_SCREENING', 'In screening'],
    ['COMPLETED', 'Completed'],
    ['CANCELLED', 'Cancelled'],
  ] as const)('[MAM-82 AC-3] a case in state %s reads "%s"', async (status, label) => {
    const data = fullPanels();
    data.revalidations = page([dueCase({ npi: DUE_NPI, status })]);
    await openDetail(rivera({ npi: DUE_NPI }), panelRoutes(DUE_NPI, data));
    const panel = await findPanel('Revalidation');

    await waitFor(() => expect(panelRows(panel)[0]?.[1]).toBe(label));
  });

  it('[MAM-82 AC-3] a provider with no case shows "No revalidation case"', async () => {
    await openDetail(rivera(), panelRoutes(RIVERA_NPI, emptyPanels()));
    const panel = await findPanel('Revalidation');

    await waitFor(() => expect(text(panel)).toContain('No revalidation case'));
  });
});

describe('[MAM-82 AC-4] notices sent to the provider', () => {
  it('[MAM-82 AC-4] the page calls GET /api/v1/notices?npi=1003000111 and the panel "Notices" lists Sent | Notice | Status in words', async () => {
    await openDetail(rivera(), panelRoutes(RIVERA_NPI, fullPanels()));
    const panel = await findPanel('Notices');
    await waitFor(() => expect(panelRows(panel)).toHaveLength(2));

    expect(callsTo('/api/v1/notices').map((call) => paramsOf(call))).toContainEqual({ npi: RIVERA_NPI });
    expect(within(panel).getAllByRole('columnheader').map((header) => text(header))).toEqual(['Sent', 'Notice', 'Status']);
    expect(panelRows(panel)).toEqual([
      ['10 Mar 2026', 'Suspension notice (SUS)', 'Sent'],
      ['20 Mar 2026', 'Reinstatement notice (RST)', 'Failed'],
    ]);
  });

  it('[MAM-82 AC-4] a provider with no notice shows "No notices sent"', async () => {
    await openDetail(rivera(), panelRoutes(RIVERA_NPI, emptyPanels()));
    const panel = await findPanel('Notices');

    await waitFor(() => expect(text(panel)).toContain('No notices sent'));
  });
});

describe('[MAM-82 AC-5] the audit trail (supervisor and administrator)', () => {
  it.each(['supervisor1', 'admin1'] as const)('[MAM-82 AC-5] %s: the page calls GET /api/v1/providers/1003000111/audit and the panel "Audit" lists When | Who | Action', async (who) => {
    await openDetail(rivera(), panelRoutes(RIVERA_NPI, fullPanels()), who);
    const panel = await findPanel('Audit');
    await waitFor(() => expect(panelRows(panel)).toHaveLength(1));

    expect(api.to('GET', `/api/v1/providers/${RIVERA_NPI}/audit`).length).toBeGreaterThan(0);
    expect(within(panel).getAllByRole('columnheader').map((header) => text(header))).toEqual(['When', 'Who', 'Action']);
    expect(panelRows(panel)).toEqual([['2 Mar 2026', 'analyst1', 'Approve (APPROVE)']]);
  });

  it('[MAM-82 AC-5] an audit trail with no entry shows "No audit entries"', async () => {
    await openDetail(rivera(), panelRoutes(RIVERA_NPI, emptyPanels()), 'supervisor1');
    const panel = await findPanel('Audit');

    await waitFor(() => expect(text(panel)).toContain('No audit entries'));
  });
});

describe('[MAM-82 AC-6] each role sees only the panels it may read', () => {
  async function panelsFor(who: 'analyst1' | 'supervisor1' | 'admin1') {
    await openDetail(rivera(), panelRoutes(RIVERA_NPI, fullPanels()), who);
    await recordTable();
    const names = ['Screening', 'Sanctions', 'Revalidation', 'Notices', 'Audit'];
    await waitFor(() => expect(names.filter(hasPanel).length).toBeGreaterThan(0));
    // let every panel settle before reading the call log
    await waitFor(() => expect(screen.queryAllByText(/^Loading/)).toHaveLength(0));
    return names.filter(hasPanel);
  }

  it('[MAM-82 AC-6] analyst1 sees Screening, Sanctions, Revalidation and Notices, no Audit panel and no audit request', async () => {
    expect(await panelsFor('analyst1')).toEqual(['Screening', 'Sanctions', 'Revalidation', 'Notices']);
    expect(api.calls.filter((call) => call.url.pathname.endsWith('/audit'))).toHaveLength(0);
  });

  it('[MAM-82 AC-6] supervisor1 sees all five panels', async () => {
    expect(await panelsFor('supervisor1')).toEqual(['Screening', 'Sanctions', 'Revalidation', 'Notices', 'Audit']);
    for (const path of PANEL_CALLS) expect(callsTo(path).length).toBeGreaterThan(0);
    expect(api.calls.filter((call) => call.url.pathname.endsWith('/audit')).length).toBeGreaterThan(0);
  });

  it('[MAM-82 AC-6] admin1 sees Screening, Sanctions, Notices and Audit, no Revalidation panel and no revalidation request', async () => {
    expect(await panelsFor('admin1')).toEqual(['Screening', 'Sanctions', 'Notices', 'Audit']);
    expect(callsTo('/api/v1/revalidations')).toHaveLength(0);
  });

  it('[MAM-82 AC-6] no request of a panel is answered 403: the panels that are not shown are not asked for', async () => {
    const data = fullPanels();
    const denied = () => response(403, problem(403, 'Forbidden'));
    await openDetail(rivera(), panelRoutes(RIVERA_NPI, data, { [`GET /api/v1/providers/${RIVERA_NPI}/audit`]: denied }), 'analyst1');
    await findPanel('Screening');
    await waitFor(() => expect(screen.queryAllByText(/^Loading/)).toHaveLength(0));

    expect(text(screen.getByRole('main'))).not.toMatch(/could not be loaded/);
  });
});

describe('[MAM-82 AC-7] one failing panel does not hide the others', () => {
  it('[MAM-82 AC-7] when the sanctions call answers 5xx the record, banner, screening, revalidation and notices panels are shown normally', async () => {
    const routes = panelRoutes(RIVERA_NPI, fullPanels(), { 'GET /api/v1/sanctions': () => response(503, problem(503, 'Service Unavailable')) });
    await openDetail(rivera(), routes);

    await recordTable();
    expect(screen.getByRole('heading', { level: 2, name: 'Status: Active (A)' })).toBeInTheDocument();
    const screening = await findPanel('Screening');
    await waitFor(() => expect(panelRows(screening)).toHaveLength(4));
    const notices = await findPanel('Notices');
    await waitFor(() => expect(panelRows(notices)).toHaveLength(2));
    const revalidation = await findPanel('Revalidation');
    await waitFor(() => expect(panelRows(revalidation)).toHaveLength(1));
  });

  it('[MAM-82 AC-7] the Sanctions panel shows "The sanctions could not be loaded" with a Retry button, never "None recorded"', async () => {
    await openDetail(rivera(), panelRoutes(RIVERA_NPI, fullPanels(), { 'GET /api/v1/sanctions': () => response(503, problem(503, 'Service Unavailable')) }));
    const panel = await findPanel('Sanctions');

    await waitFor(() => expect(text(panel)).toContain('The sanctions could not be loaded'));
    expect(within(panel).getByRole('button', { name: 'Retry' })).toBeInTheDocument();
    expect(text(panel)).not.toContain('None recorded');
  });

  it('[MAM-82 AC-7] Retry asks again and shows the sanctions when the service has recovered', async () => {
    let up = false;
    const { user } = await openDetail(
      rivera(),
      panelRoutes(RIVERA_NPI, fullPanels(), { 'GET /api/v1/sanctions': () => (up ? response(200, fullPanels().sanctions) : response(503, problem(503, 'x'))) }),
    );
    const failed = await findPanel('Sanctions');
    await waitFor(() => expect(text(failed)).toContain('could not be loaded'));
    up = true;

    await user.click(within(failed).getByRole('button', { name: 'Retry' }));

    await waitFor(() => expect(panelRows(failed)).toHaveLength(2));
  });

  it.each([
    ['Revalidation', 'GET /api/v1/revalidations', 'The revalidation cases could not be loaded'],
    ['Notices', 'GET /api/v1/notices', 'The notices could not be loaded'],
    ['Screening', 'GET /api/v1/screenings', 'The screening results could not be loaded'],
  ])('[MAM-82 AC-7] a failing %s panel (also an endpoint that does not exist yet, 404) shows its own message and Retry, and no invented data', async (name, route, message) => {
    await openDetail(rivera(), panelRoutes(RIVERA_NPI, fullPanels(), { [route]: () => response(404, problem(404, 'Not Found')) }));
    const panel = await findPanel(name);

    await waitFor(() => expect(text(panel)).toContain(message));
    expect(within(panel).getByRole('button', { name: 'Retry' })).toBeInTheDocument();
    expect(panel.querySelector('table')).toBeNull();
  });

  it('[MAM-82 AC-7] the Audit panel of supervisor1 shows its own failure when the audit endpoint fails', async () => {
    await openDetail(rivera(), panelRoutes(RIVERA_NPI, fullPanels(), { [`GET /api/v1/providers/${RIVERA_NPI}/audit`]: () => response(500, problem(500, 'x')) }), 'supervisor1');
    const panel = await findPanel('Audit');

    await waitFor(() => expect(text(panel)).toContain('The audit history could not be loaded'));
    expect(text(panel)).not.toContain('No audit entries');
  });
});

describe('[MAM-82 AC-8] structure', () => {
  it('[MAM-82 AC-8] each panel is a section with a heading, and its tables have header cells; results and statuses are text plus code', async () => {
    await openDetail(rivera(), panelRoutes(RIVERA_NPI, fullPanels()), 'supervisor1');
    for (const name of ['Screening', 'Sanctions', 'Revalidation', 'Notices', 'Audit']) {
      const panel = await findPanel(name);
      await waitFor(() => expect(panel.querySelector('table')).not.toBeNull());
      expect(panel.tagName).toBe('SECTION');
      expect(within(panel).getAllByRole('columnheader').length).toBeGreaterThan(0);
    }
    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1);
    expect(text(await findPanel('Screening'))).toMatch(/Pass \(P\)/);
  });
});
