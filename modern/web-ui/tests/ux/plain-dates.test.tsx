// UX overhaul 11, plain dates (user decision Q-110, 2026-11-19 19:54, B-iv item 9): every displayed date reads "19 Nov 2026" on every
// screen (the month in letters, the day without a leading zero); the API keeps ISO dates and the legacy files CCYYMMDD. A date field
// a user types into keeps MM/DD/YYYY and is read by toIsoDate. First the two functions, then every screen that shows a date: no
// date in ISO form and none in MM/DD/YYYY form is shown, and the expected plain date is.
import { screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { formatDate } from '../../src/shared/format/format';
import { toIsoDate } from '../../src/shared/format/dates';
import { approvalWorld, openApproval, passedScreening, pendingPass, RIVERA_NPI } from '../support/approval';
import { batchRoutes, dateBox as businessDateBox, ENDPOINTS, newRun, openJobs, runJob, runRows, runsPage } from '../support/batch';
import { findPanel, openDetail, rivera } from '../support/detail';
import { response } from '../support/fakeApi';
import { application, APPLICATION_ID, PROVIDER_NPI, provider } from '../support/fixtures';
import { answer, noticeRows, NOTICES_ROUTE, numbered, openNotices } from '../support/notices';
import { fullPanels, page, panelRoutes, sanction } from '../support/panels';
import { emptyQueues, findQueueSection, fullQueues, openQueues, queueRoutes } from '../support/queues';
import { dateBox as effectiveDateBox, HISTORY_ROUTE, openForm, record, sanctionWorld, historyRows, posts } from '../support/sanction';
import { api, defaultRoutes, findApplicationsTable, installApi, renderApp, setupShellWorld, signedInAs, text } from '../support/world';

vi.mock('oidc-client-ts', async (importOriginal) => {
  const actual = await importOriginal<typeof import('oidc-client-ts')>();
  const { FakeUserManager } = await import('../support/fakeOidc');
  return { ...actual, UserManager: FakeUserManager };
});

setupShellWorld();

describe('[UX-OVERHAUL 11] formatDate: "19 Nov 2026"', () => {
  it('[UX-OVERHAUL 11] the example of the decision: 2026-11-19 is "19 Nov 2026"', () => {
    expect(formatDate('2026-11-19')).toBe('19 Nov 2026');
  });

  it.each([
    ['2026-01-15', '15 Jan 2026'],
    ['2026-02-15', '15 Feb 2026'],
    ['2026-03-15', '15 Mar 2026'],
    ['2026-04-15', '15 Apr 2026'],
    ['2026-05-15', '15 May 2026'],
    ['2026-06-15', '15 Jun 2026'],
    ['2026-07-15', '15 Jul 2026'],
    ['2026-08-15', '15 Aug 2026'],
    ['2026-09-15', '15 Sep 2026'],
    ['2026-10-15', '15 Oct 2026'],
    ['2026-11-15', '15 Nov 2026'],
    ['2026-12-15', '15 Dec 2026'],
  ])('[UX-OVERHAUL 11] %s is "%s": the month in letters', (iso, plain) => {
    expect(formatDate(iso)).toBe(plain);
  });

  it.each([
    ['2026-03-05', '5 Mar 2026'],
    ['2026-11-09', '9 Nov 2026'],
    ['2026-01-01', '1 Jan 2026'],
    ['2026-12-31', '31 Dec 2026'],
    ['2028-02-29', '29 Feb 2028'],
  ])('[UX-OVERHAUL 11] %s is "%s": the day has no leading zero', (iso, plain) => {
    expect(formatDate(iso)).toBe(plain);
  });

  it.each([
    ['2026-03-01T23:59:59Z', '1 Mar 2026'],
    ['2026-12-31T00:00:00Z', '31 Dec 2026'],
    ['2026-03-01T10:00:00.123Z', '1 Mar 2026'],
    ['2026-03-01T23:30:00-05:00', '1 Mar 2026'],
  ])('[UX-OVERHAUL 11] the instant %s is shown with its date part as sent: "%s"', (instant, plain) => {
    expect(formatDate(instant)).toBe(plain);
  });

  it.each(['', 'garbage', '03/05/2026', '5 Mar 2026', '20260305'])('[UX-OVERHAUL 11] a value that is not an ISO date ("%s") is shown as it came, not as a made-up date', (value) => {
    expect(formatDate(value)).toBe(value);
  });
});

describe('[UX-OVERHAUL 11] toIsoDate: typed dates stay MM/DD/YYYY', () => {
  it.each([
    ['03/05/2026', '2026-03-05'],
    ['3/5/2026', '2026-03-05'],
    ['12/31/2026', '2026-12-31'],
    ['  11/19/2026 ', '2026-11-19'],
    ['02/29/2028', '2028-02-29'],
  ])('[UX-OVERHAUL 11] %s is read as %s', (typed, iso) => {
    expect(toIsoDate(typed)).toBe(iso);
  });

  it.each(['', '02/30/2026', '02/29/2027', '13/01/2026', '00/10/2026', '03/32/2026', '03/05/26', '2026-03-05', '5 Mar 2026', '31/03/2026'])(
    '[UX-OVERHAUL 11] "%s" is not a date a user can type (no ISO, no plain form, no day first, no impossible day)',
    (typed) => {
      expect(toIsoDate(typed)).toBeNull();
    },
  );

  it('[UX-OVERHAUL 11] what is typed as MM/DD/YYYY is displayed as a plain date', () => {
    expect(formatDate(toIsoDate('11/19/2026')!)).toBe('19 Nov 2026');
  });
});

// ---- every screen -------------------------------------------------------------------------------------------------------------------

const ISO_DATE = /\d{4}-\d{2}-\d{2}/;
const SLASH_DATE = /(^|\s)\d{1,2}\/\d{1,2}\/\d{2,4}(\s|$|[.,;:])/;
const PLAIN_DATE = /(^|\s)\d{1,2} (Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec) \d{4}(\s|$|[.,;:])/;

/** The text of the main area with a space between the text of every element (table cells would otherwise run together: "10030001119 Mar 2026"). */
function spacedText(scope: HTMLElement): string {
  const walker = document.createTreeWalker(scope, NodeFilter.SHOW_TEXT);
  const parts: string[] = [];
  for (let node = walker.nextNode(); node; node = walker.nextNode()) {
    const value = (node.textContent ?? '').replace(/\s+/g, ' ').trim();
    if (value) parts.push(value);
  }
  return parts.join(' ');
}

/** The main area shows its dates in the plain form: the listed ones are there, and no date in ISO or MM/DD/YYYY form is. */
function expectPlainDates(...expected: string[]): void {
  const shown = spacedText(screen.getByRole('main'));
  for (const date of expected) expect(shown).toContain(date);
  expect(shown).toMatch(PLAIN_DATE);
  expect(shown).not.toMatch(ISO_DATE);
  expect(shown).not.toMatch(SLASH_DATE);
}

describe('[UX-OVERHAUL 11] the screens of the portal', () => {
  it('[UX-OVERHAUL 11] portal home: the submission date of an application and the next revalidation', async () => {
    signedInAs('provider1');
    installApi({ ...defaultRoutes([application()]), [`GET /api/v1/providers/${PROVIDER_NPI}`]: () => response(200, provider()) });
    renderApp('/portal');

    await findApplicationsTable();
    await waitFor(() => expect(text(screen.getByRole('main'))).toContain('Your next revalidation is due on 2 Mar 2031.'));
    expectPlainDates('1 Mar 2026', '2 Mar 2031');
  });

  it('[UX-OVERHAUL 11] application status: Submitted', async () => {
    signedInAs('provider1');
    installApi({ ...defaultRoutes(), [`GET /api/v1/applications/${APPLICATION_ID}`]: () => response(200, application()) });
    renderApp(`/portal/applications/${APPLICATION_ID}`);

    await screen.findByRole('region', { name: 'Application' });
    expectPlainDates('1 Mar 2026');
  });

  it('[UX-OVERHAUL 11] my provider record: the effective date, the due date and the next revalidation sentence', async () => {
    signedInAs('provider1');
    installApi(defaultRoutes());
    renderApp('/portal/provider');

    await screen.findByRole('region', { name: 'Provider record' });
    expectPlainDates('2 Mar 2026', '2 Mar 2031', 'Next revalidation: 2 Mar 2031');
  });

  it('[UX-OVERHAUL 11] my notices: the date a notice was sent', async () => {
    await openNotices({ [NOTICES_ROUTE]: answer([numbered({ sentAt: '2026-03-05T10:00:00Z' })]) });

    await waitFor(() => expect(noticeRows()).toHaveLength(1));
    expectPlainDates('5 Mar 2026');
  });
});

describe('[UX-OVERHAUL 11] the screens of the console', () => {
  it('[UX-OVERHAUL 11] provider detail with every panel: record, screening, sanctions, revalidation, notices and audit', async () => {
    await openDetail(rivera(), panelRoutes(RIVERA_NPI, fullPanels()), 'supervisor1');
    for (const name of ['Screening', 'Sanctions', 'Revalidation', 'Notices', 'Audit']) {
      const panel = await findPanel(name);
      await waitFor(() => expect(panel.querySelectorAll('tbody tr').length).toBeGreaterThan(0));
    }

    expectPlainDates('2 Mar 2026', '2 Mar 2031', '10 Mar 2026', '20 Mar 2026', '28 Feb 2026', '1 Mar 2026');
  });

  it('[UX-OVERHAUL 11] approval: the verdict date of every source', async () => {
    await openApproval('analyst1', approvalWorld(pendingPass(), passedScreening()), RIVERA_NPI);

    await screen.findByRole('table', { name: 'Screening results' });
    expectPlainDates('2 Mar 2026');
  });

  it('[UX-OVERHAUL 11] work queues: the due date of a revalidation and the submission date of a rejected application', async () => {
    await openQueues('analyst1', queueRoutes(fullQueues()));
    await findQueueSection('Revalidation due', /1003000222/);
    await findQueueSection('Rejected applications', /1003000111/);

    expectPlainDates('28 Feb 2026', '1 Mar 2026');
  });

  it('[UX-OVERHAUL 11] batch jobs: the business date of a run', async () => {
    await openJobs(batchRoutes({ [`GET ${ENDPOINTS.tmsis}`]: () => response(200, runsPage([newRun({ businessDate: '2026-02-28' })])) }));

    await waitFor(() => expect(runRows()).toHaveLength(1));
    expectPlainDates('28 Feb 2026');
  });

  it('[UX-OVERHAUL 11] record a sanction: the effective date in the history', async () => {
    const world = sanctionWorld({ [HISTORY_ROUTE]: () => response(200, page([sanction({ effectiveDate: '2026-03-10' })])) });
    await openForm(PROVIDER_NPI, world.routes);

    await waitFor(() => expect(historyRows()).toHaveLength(1));
    expectPlainDates('10 Mar 2026');
  });

  it('[UX-OVERHAUL 11] an empty queue set shows no date at all, and no made-up one', async () => {
    await openQueues('analyst1', queueRoutes(emptyQueues()));
    await findQueueSection('Revalidation due', /No revalidations due/);

    const shown = spacedText(screen.getByRole('main'));
    expect(shown).not.toMatch(ISO_DATE);
    expect(shown).not.toMatch(PLAIN_DATE);
  });
});

describe('[UX-OVERHAUL 11] the date fields a user types into keep MM/DD/YYYY', () => {
  it('[UX-OVERHAUL 11] the batch job form asks for the business date as MM/DD/YYYY, and a plain date typed there is refused and sent nowhere', async () => {
    const { user } = await openJobs(batchRoutes());
    expect(businessDateBox()).toBeInTheDocument();

    await runJob(user, 'Revalidation sweep', '31 Mar 2026');

    expect(await screen.findByRole('link', { name: 'Enter a real date as MM/DD/YYYY' })).toBeInTheDocument();
    expect(api.calls.filter((call) => call.method === 'POST')).toHaveLength(0);
  });

  it('[UX-OVERHAUL 11] the batch job form reads 03/31/2026 and sends the ISO date 2026-03-31', async () => {
    const { user } = await openJobs(batchRoutes({ [`POST ${ENDPOINTS.sweep}`]: () => response(202, newRun({ businessDate: '2026-03-31' })) }));

    await runJob(user, 'Revalidation sweep', '03/31/2026');

    await waitFor(() => expect(api.to('POST', ENDPOINTS.sweep)).toHaveLength(1));
    expect(JSON.parse(api.to('POST', ENDPOINTS.sweep)[0]!.body ?? '{}')).toEqual({ businessDate: '2026-03-31' });
    await waitFor(() => expect(runRows().some((row) => row[1] === '31 Mar 2026')).toBe(true));
  });

  it('[UX-OVERHAUL 11] the sanction form asks for the effective date as MM/DD/YYYY, and a plain date typed there is refused and sent nowhere', async () => {
    const { user } = await openForm(PROVIDER_NPI, sanctionWorld().routes);
    expect(effectiveDateBox().labels?.[0]?.textContent).toContain('MM/DD/YYYY');

    await record(user, { action: 'S', date: '31 Mar 2026' });

    expect(await screen.findByRole('heading', { level: 2, name: '1 error in the sanction' })).toBeInTheDocument();
    expect(posts()).toHaveLength(0);
    expect(effectiveDateBox()).toHaveValue('31 Mar 2026');
  });
});
