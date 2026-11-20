// MAM-85 AC-1..AC-8 (component level): the work queues /console/queues for ANALYST and SUPERVISOR. Pending approval and Failed
// screening come from provider-service and screening-service, Revalidation due from revalidation-service (D-029), Rejected
// applications from enrollment-service. One failing queue never hides the others (D-024).
import { screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { problem, response } from '../support/fakeApi';
import { applicationPage } from '../support/fixtures';
import { CLINIC_NPI, pendingPass, RIVERA_NPI } from '../support/approval';
import {
  callsTo,
  dueCase,
  DUE_NPI,
  emptyQueues,
  findQueueSection,
  fullQueues,
  openQueues,
  paramsOf,
  providerPage,
  queueRoutes,
  queueSection,
  rejectedApplication,
  revalidationPage,
  rowsOf,
} from '../support/queues';
import { api, installApi, renderApp, setupShellWorld, signedInAs, text } from '../support/world';

vi.mock('oidc-client-ts', async (importOriginal) => {
  const actual = await importOriginal<typeof import('oidc-client-ts')>();
  const { FakeUserManager } = await import('../support/fakeOidc');
  return { ...actual, UserManager: FakeUserManager };
});

setupShellWorld();

const QUEUE_CALLS = ['/api/v1/providers', '/api/v1/screenings', '/api/v1/revalidations', '/api/v1/applications'];

describe('[MAM-85 AC-1] Pending approval lists providers with a passed screening (journey J1)', () => {
  it('[MAM-85 AC-1] the page calls GET /api/v1/providers?status=P&screenFlag=P', async () => {
    await openQueues('analyst1', queueRoutes(fullQueues()));
    await findQueueSection('Pending approval', /1003000111/);

    const calls = callsTo('/api/v1/providers').map((call) => paramsOf(call));
    expect(calls).toContainEqual({ status: 'P', screenFlag: 'P' });
  });

  it('[MAM-85 AC-1] the section "Pending approval" lists 1003000111 with NPI, Name, Screened "Pass (P)" and a Review link to its approval page', async () => {
    await openQueues('analyst1', queueRoutes(fullQueues()));
    const section = await findQueueSection('Pending approval', /1003000111/);

    const headers = within(section).getAllByRole('columnheader').map((header) => text(header));
    expect(headers.slice(0, 3)).toEqual(['NPI', 'Name', 'Screening result']);
    expect(rowsOf(section)[0]?.slice(0, 3)).toEqual(['1003000111', 'RIVERA MARIA', 'Pass (P)']);
    expect(within(section).getByRole('link', { name: 'Review' })).toHaveAttribute('href', `/console/providers/${RIVERA_NPI}/approval`);
  });

  it('[MAM-85 AC-1] the provider whose screening failed (1003000444) is not listed under Pending approval', async () => {
    await openQueues('analyst1', queueRoutes(fullQueues()));
    const section = await findQueueSection('Pending approval', /1003000111/);

    expect(text(section)).not.toContain(CLINIC_NPI);
  });

  it('[MAM-85 AC-1] each queue is a <section> with a heading and a table with header cells', async () => {
    await openQueues('analyst1', queueRoutes(fullQueues()));
    for (const name of ['Pending approval', 'Failed screening', 'Revalidation due', 'Rejected applications']) {
      const section = await findQueueSection(name, /\d{3}/);
      expect(section.tagName).toBe('SECTION');
      expect(within(section).getAllByRole('columnheader').length).toBeGreaterThan(0);
    }
  });
});

describe('[MAM-85 AC-2] Failed screening shows the failing source (journey J3)', () => {
  it('[MAM-85 AC-2] the page calls GET /api/v1/providers?status=P&screenFlag=F and, for the listed provider, GET /api/v1/screenings?npi=1003000444&result=FAIL', async () => {
    await openQueues('analyst1', queueRoutes(fullQueues()));
    await findQueueSection('Failed screening', /OIG exclusion list/);

    expect(callsTo('/api/v1/providers').map((call) => paramsOf(call))).toContainEqual({ status: 'P', screenFlag: 'F' });
    expect(callsTo('/api/v1/screenings').map((call) => paramsOf(call))).toContainEqual({ npi: CLINIC_NPI, result: 'FAIL' });
  });

  it('[MAM-85 AC-2] the section lists 1003000444 EXCLUDED CLINIC LLC with the failing source "OIGLEIE (E203 owner excluded)"; the NPI links to the provider detail', async () => {
    await openQueues('analyst1', queueRoutes(fullQueues()));
    const section = await findQueueSection('Failed screening', /OIG exclusion list/);

    expect(rowsOf(section)[0]).toEqual([CLINIC_NPI, 'EXCLUDED CLINIC LLC', 'OIG exclusion list (LEIE): E203 owner excluded']);
    expect(within(section).getByRole('link', { name: CLINIC_NPI })).toHaveAttribute('href', `/console/providers/${CLINIC_NPI}`);
  });

  it('[MAM-85 AC-2] a provider whose failing source cannot be loaded is still listed, with a message instead of the source', async () => {
    const data = fullQueues();
    await openQueues('analyst1', queueRoutes(data, { 'GET /api/v1/screenings': () => response(500, problem(500, 'Internal Server Error')) }));
    const section = await findQueueSection('Failed screening', /1003000444/);

    await waitFor(() => expect(text(section)).toMatch(/could not be loaded/i));
    expect(rowsOf(section)[0]?.[0]).toBe(CLINIC_NPI);
  });
});

describe('[MAM-85 AC-3] Revalidation due lists open cases once each (journey J6)', () => {
  it('[MAM-85 AC-3] the page calls GET /api/v1/revalidations?status=DUE and ?status=IN_SCREENING, never for completed or cancelled cases', async () => {
    await openQueues('analyst1', queueRoutes(fullQueues()));
    await findQueueSection('Revalidation due', /1003000222/);

    const statuses = callsTo('/api/v1/revalidations').map((call) => paramsOf(call)['status']);
    expect(statuses).toContain('DUE');
    expect(statuses).toContain('IN_SCREENING');
    expect(statuses).not.toContain('COMPLETED');
    expect(statuses).not.toContain('CANCELLED');
  });

  it('[MAM-85 AC-3] the case of 1003000222 is listed once with NPI, Due 28 Feb 2026 and Case "Due"', async () => {
    await openQueues('analyst1', queueRoutes(fullQueues()));
    const section = await findQueueSection('Revalidation due', /1003000222/);

    expect(within(section).getAllByRole('columnheader').map((header) => text(header))).toEqual(['NPI', 'Due', 'Case']);
    expect(rowsOf(section)).toEqual([[DUE_NPI, '28 Feb 2026', 'Due']]);
  });

  it('[MAM-85 AC-3] a case that is already IN_SCREENING reads "In screening" and is listed once', async () => {
    const data = fullQueues();
    data.due = revalidationPage([]);
    data.inScreening = revalidationPage([dueCase({ status: 'IN_SCREENING' })]);
    await openQueues('analyst1', queueRoutes(data));
    const section = await findQueueSection('Revalidation due', /1003000222/);

    expect(rowsOf(section)).toEqual([[DUE_NPI, '28 Feb 2026', 'In screening']]);
  });
});

describe('[MAM-85 AC-4] Rejected applications are listed for follow-up', () => {
  it('[MAM-85 AC-4] the page calls GET /api/v1/applications?status=REJECTED', async () => {
    await openQueues('analyst1', queueRoutes(fullQueues()));
    await findQueueSection('Rejected applications', /1003000111/);

    expect(callsTo('/api/v1/applications').map((call) => paramsOf(call))).toContainEqual({ status: 'REJECTED' });
  });

  it('[MAM-85 AC-4] each rejection shows NPI (or "(no NPI)"), Submitted as a plain date (1 Mar 2026), the error codes and the source', async () => {
    const data = fullQueues();
    data.rejected = applicationPage([
      rejectedApplication(),
      rejectedApplication({
        applicationId: '6a0d3f21-9c47-4b8e-8f12-4d5e7a9b1c22',
        npi: '',
        errors: [{ code: 'E101', field: 'npi', message: 'Enter the 10-digit NPI.' }],
        source: 'LEGACY_FILE',
        submittedAt: '2026-03-02T08:30:00Z',
      }),
    ]);
    await openQueues('analyst1', queueRoutes(data));
    const section = await findQueueSection('Rejected applications', /E101/);

    expect(within(section).getAllByRole('columnheader').map((header) => text(header))).toEqual(['NPI', 'Submitted', 'Errors', 'Source']);
    expect(rowsOf(section)).toEqual([
      ['1003000111', '1 Mar 2026', 'E104', 'Portal'],
      ['(no NPI)', '2 Mar 2026', 'E101', 'Legacy file'],
    ]);
  });
});

describe('[MAM-85 AC-5] counts, empty lists and paging', () => {
  it('[MAM-85 AC-5] each heading carries its count', async () => {
    await openQueues('analyst1', queueRoutes(fullQueues()));

    expect(text(await findQueueSection('Pending approval', /\(1\)/))).toContain('Pending approval (1)');
    expect(text(await findQueueSection('Failed screening', /\(1\)/))).toContain('Failed screening (1)');
    expect(text(await findQueueSection('Revalidation due', /\(1\)/))).toContain('Revalidation due (1)');
    expect(text(await findQueueSection('Rejected applications', /\(1\)/))).toContain('Rejected applications (1)');
  });

  it.each([
    ['Pending approval', 'Nothing pending approval'],
    ['Failed screening', 'No failed screenings'],
    ['Revalidation due', 'No revalidations due'],
    ['Rejected applications', 'No rejected applications'],
  ])('[MAM-85 AC-5] an empty %s section says "%s"', async (name, message) => {
    await openQueues('analyst1', queueRoutes(emptyQueues()));

    const section = await findQueueSection(name, /\(0\)/);
    expect(text(section)).toContain(message);
    expect(section.querySelector('table')).toBeNull();
  });

  it('[MAM-85 AC-5] a section with more than 20 entries shows paging controls, and Next asks for page 1 with the default size', async () => {
    const data = fullQueues();
    data.pending = providerPage(Array.from({ length: 20 }, (_, index) => pendingPass({ npi: `10030${String(index).padStart(5, '0')}` })), 45);
    const { user } = await openQueues('analyst1', queueRoutes(data));
    const section = await findQueueSection('Pending approval', /\(45\)/);
    expect(callsTo('/api/v1/providers').map((call) => paramsOf(call)).filter((params) => params['screenFlag'] === 'P')).toEqual([{ status: 'P', screenFlag: 'P' }]);
    expect(text(section)).toMatch(/Page 1 of 3/);

    await user.click(within(section).getByRole('button', { name: /Next/ }));

    await waitFor(() => expect(callsTo('/api/v1/providers').map((call) => paramsOf(call)['page'])).toContain('1'));
  });

  it('[MAM-85 AC-5] a section with 20 entries or fewer shows no paging controls', async () => {
    await openQueues('analyst1', queueRoutes(fullQueues()));
    const section = await findQueueSection('Pending approval', /1003000111/);

    expect(within(section).queryByRole('button', { name: /Next/ })).toBeNull();
  });
});

describe('[MAM-85 AC-6] one failing queue does not hide the others', () => {
  it('[MAM-85 AC-6] when the revalidations answer 5xx the other three sections are shown normally', async () => {
    await openQueues('analyst1', queueRoutes(fullQueues(), { 'GET /api/v1/revalidations': () => response(503, problem(503, 'Service Unavailable')) }));

    expect(text(await findQueueSection('Pending approval', /1003000111/))).toContain('RIVERA MARIA');
    expect(text(await findQueueSection('Failed screening', /OIG exclusion list/))).toContain('EXCLUDED CLINIC LLC');
    expect(text(await findQueueSection('Rejected applications', /E104/))).toContain('1003000111');
  });

  it('[MAM-85 AC-6] "Revalidation due" shows "The list could not be loaded" with a Retry button, never "No revalidations due"', async () => {
    await openQueues('analyst1', queueRoutes(fullQueues(), { 'GET /api/v1/revalidations': () => response(503, problem(503, 'Service Unavailable')) }));

    const section = await findQueueSection('Revalidation due', /could not be loaded/);
    expect(text(section)).toContain('The list could not be loaded');
    expect(within(section).getByRole('button', { name: 'Retry' })).toBeInTheDocument();
    expect(text(section)).not.toContain('No revalidations due');
  });

  it('[MAM-85 AC-6] an endpoint that does not exist yet (404) is shown the same way, not as an empty list', async () => {
    await openQueues('analyst1', queueRoutes(fullQueues(), { 'GET /api/v1/revalidations': () => response(404, problem(404, 'Not Found')) }));

    const section = await findQueueSection('Revalidation due', /could not be loaded/);
    expect(text(section)).not.toContain('No revalidations due');
    expect(section.querySelector('table')).toBeNull();
  });

  it('[MAM-85 AC-6] a network failure is shown the same way', async () => {
    await openQueues(
      'analyst1',
      queueRoutes(fullQueues(), {
        'GET /api/v1/revalidations': () => {
          throw new TypeError('Failed to fetch');
        },
      }),
    );

    expect(text(await findQueueSection('Revalidation due', /could not be loaded/))).toContain('The list could not be loaded');
  });

  it('[MAM-85 AC-6] Retry asks again and shows the list when the service has recovered', async () => {
    let up = false;
    const routes = queueRoutes(fullQueues(), {
      'GET /api/v1/revalidations': () => (up ? response(200, revalidationPage([dueCase()])) : response(503, problem(503, 'Service Unavailable'))),
    });
    const { user } = await openQueues('analyst1', routes);
    const failed = await findQueueSection('Revalidation due', /could not be loaded/);
    up = true;

    await user.click(within(failed).getByRole('button', { name: 'Retry' }));

    const section = await findQueueSection('Revalidation due', /1003000222/);
    expect(rowsOf(section)).toEqual([[DUE_NPI, '28 Feb 2026', 'Due']]);
  });

  it('[MAM-85 AC-6] a failing Pending approval list is shown the same way and the others stay', async () => {
    await openQueues('analyst1', queueRoutes(fullQueues(), { 'GET /api/v1/providers': (call) => (call.url.searchParams.get('screenFlag') === 'P' ? response(500, problem(500, 'x')) : response(200, fullQueues().failed)) }));

    expect(text(await findQueueSection('Pending approval', /could not be loaded/))).not.toContain('Nothing pending approval');
    expect(text(await findQueueSection('Failed screening', /OIG exclusion list/))).toContain('EXCLUDED CLINIC LLC');
  });
});

describe('[MAM-85 AC-7] only analysts and supervisors open the queues', () => {
  it.each(['analyst1', 'supervisor1'] as const)('[MAM-85 AC-7] %s sees the four sections', async (who) => {
    await openQueues(who, queueRoutes(fullQueues()));

    for (const name of ['Pending approval', 'Failed screening', 'Revalidation due', 'Rejected applications']) {
      expect(text(await findQueueSection(name, /\(\d+\)/))).toContain(name);
    }
  });

  it.each(['admin1', 'provider1'] as const)('[MAM-85 AC-7] %s sees the access-denied page and no queue request is made', async (who) => {
    signedInAs(who);
    installApi(queueRoutes(fullQueues()));
    renderApp('/console/queues');

    expect(await screen.findByRole('heading', { level: 1, name: 'Access denied' })).toBeInTheDocument();
    expect(api.calls.filter((call) => QUEUE_CALLS.includes(call.url.pathname))).toEqual([]);
    expect(() => queueSection('Pending approval')).toThrow();
  });
});

describe('[MAM-85 AC-8] structure and keyboard', () => {
  it('[MAM-85 AC-8] the page has one h1, one h2 per section and the Review and NPI links are links in the tab order', async () => {
    const { user } = await openQueues('analyst1', queueRoutes(fullQueues()));
    await findQueueSection('Failed screening', /OIG exclusion list/);
    await findQueueSection('Pending approval', /1003000111/);

    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1);
    expect(screen.getAllByRole('heading', { level: 2 }).map((heading) => text(heading).replace(/ \(\d+\)$/, ''))).toEqual([
      'Pending approval',
      'Failed screening',
      'Revalidation due',
      'Rejected applications',
    ]);
    const review = within(queueSection('Pending approval')).getByRole('link', { name: 'Review' });
    review.focus();
    await user.keyboard('{Enter}');
    expect(await screen.findByRole('heading', { level: 1, name: 'Approve or deny' })).toBeInTheDocument();
  });
});
