// UX overhaul 17, plain labels (user decisions B-iv items 2 and 3, 2026-11-19): the screening flag of a provider is called
// "Screening result" on every screen (never "Screen flag" or "Screened"), and every badge reads in sentence case ("Validated",
// "Completed", "Active (A)"), never in the capitals the services send or the USWDS tag style gives. The capitals of a stored
// code stay where the code is shown in brackets. The real browser check of the tag style is in e2e/ux-overhaul.spec.ts.
import { screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { approvalWorld, openApproval, passedScreening, pendingPass, RIVERA_NPI } from '../support/approval';
import { batchRoutes, ENDPOINTS, newRun, openJobs, runRows, runsPage } from '../support/batch';
import { openDetail, rivera } from '../support/detail';
import { response } from '../support/fakeApi';
import { application, APPLICATION_ID, provider } from '../support/fixtures';
import { answer, noticeRows, NOTICES_ROUTE, numbered, openNotices } from '../support/notices';
import { fullQueues, findQueueSection, openQueues, queueRoutes } from '../support/queues';
import { openSearch, search as searchProviders, searchRoutes, resultsTable } from '../support/search';
import { defaultRoutes, findApplicationsTable, installApi, renderApp, setupShellWorld, signedInAs, text } from '../support/world';

vi.mock('oidc-client-ts', async (importOriginal) => {
  const actual = await importOriginal<typeof import('oidc-client-ts')>();
  const { FakeUserManager } = await import('../support/fakeOidc');
  return { ...actual, UserManager: FakeUserManager };
});

setupShellWorld();

const PROVIDER_ROUTE = '/api/v1/providers/1003000111';

describe('[UX-OVERHAUL 17] "Screening result" is the name of the screening flag on every screen (B-iv item 3)', () => {
  const OLD_NAMES = /Screen flag|\bScreened\b/;

  it('[UX-OVERHAUL 17] portal home: the record in short has the term "Screening result"', async () => {
    signedInAs('provider1');
    installApi({ ...defaultRoutes(), [`GET ${PROVIDER_ROUTE}`]: () => response(200, provider()) });
    renderApp('/portal');

    await screen.findByText('Pass (P)');
    expect(text(screen.getByRole('main'))).toContain('Screening result');
    expect(text(screen.getByRole('main'))).not.toMatch(OLD_NAMES);
  });

  it('[UX-OVERHAUL 17] my provider record: the list and the badge say "Screening result"', async () => {
    signedInAs('provider1');
    installApi(defaultRoutes());
    renderApp('/portal/provider');

    await screen.findByRole('region', { name: 'Provider record' });
    expect(text(screen.getByRole('main'))).toContain('Screening result: Pass (P)');
    expect(text(screen.getByRole('main'))).not.toMatch(OLD_NAMES);
  });

  it('[UX-OVERHAUL 17] provider detail: the provider list, the record list and the summary say "Screening result"', async () => {
    await openDetail(rivera());
    await screen.findByRole('region', { name: 'Provider record' });

    const main = text(screen.getByRole('main'));
    expect(main).toContain('Screening result: Pass (P)');
    expect(main).not.toMatch(OLD_NAMES);
  });

  it('[UX-OVERHAUL 17] approval: the list "Provider" has "Screening result"', async () => {
    await openApproval('analyst1', approvalWorld(pendingPass(), passedScreening()), RIVERA_NPI);

    await screen.findByRole('region', { name: 'Provider' });
    expect(text(screen.getByRole('main'))).toContain('Screening result');
    expect(text(screen.getByRole('main'))).not.toMatch(OLD_NAMES);
  });

  it('[UX-OVERHAUL 17] work queues: the column of Pending approval is "Screening result"', async () => {
    await openQueues('analyst1', queueRoutes(fullQueues()));

    const section = await findQueueSection('Pending approval', /1003000111/);
    expect(text(section)).toContain('Screening result');
    expect(text(screen.getByRole('main'))).not.toMatch(OLD_NAMES);
  });
});

describe('[UX-OVERHAUL 17] every badge reads in sentence case (B-iv item 2)', () => {
  const badges = (): string[] => Array.from(document.querySelectorAll('.usa-tag')).map((tag) => text(tag));
  const expectSentenceCase = (shown: string[], expected: string[]) => {
    expect(shown).toEqual(expect.arrayContaining(expected));
    expect(shown.filter((badge) => /[A-Z]{4,}/.test(badge))).toEqual([]);
  };

  it('[UX-OVERHAUL 17] the status of an application: Validated, Rejected, Submitted', async () => {
    const at = (id: string, status: 'SUBMITTED' | 'VALIDATED' | 'REJECTED') => application({ applicationId: `00000000-0000-4000-8000-00000000000${id}`, status });
    signedInAs('provider1');
    installApi(defaultRoutes([at('1', 'VALIDATED'), at('2', 'REJECTED'), at('3', 'SUBMITTED')]));
    renderApp('/portal');

    await findApplicationsTable();
    expectSentenceCase(badges(), ['Validated', 'Rejected', 'Submitted']);
  });

  it('[UX-OVERHAUL 17] the badge of the application status page: Validated', async () => {
    signedInAs('provider1');
    installApi({ ...defaultRoutes(), [`GET /api/v1/applications/${APPLICATION_ID}`]: () => response(200, application()) });
    renderApp(`/portal/applications/${APPLICATION_ID}`);

    await screen.findByRole('region', { name: 'Application' });
    expectSentenceCase(badges(), ['Validated']);
  });

  it('[UX-OVERHAUL 17] the status of a provider in the search results: Active (A) and Pending (P)', async () => {
    const records = [provider({ enrollStatus: 'A' }), provider({ npi: '1003000202', enrollStatus: 'P' })];
    const { user } = await openSearch(searchRoutes(records));
    await searchProviders(user, { name: 'Provider' });

    await waitFor(() => expect(resultsTable()).not.toBeNull());
    expectSentenceCase(badges(), ['Active (A)', 'Pending (P)']);
  });

  it('[UX-OVERHAUL 17] the delivery of a notice: Sent and Failed', async () => {
    await openNotices({ [NOTICES_ROUTE]: answer([numbered({ status: 'SENT' }), numbered({ status: 'FAILED' })]) });

    await waitFor(() => expect(noticeRows()).toHaveLength(2));
    expectSentenceCase(badges(), ['Sent', 'Failed']);
  });

  it('[UX-OVERHAUL 17] the state of a batch run: Completed, Failed and Running', async () => {
    await openJobs(
      batchRoutes({ [`GET ${ENDPOINTS.tmsis}`]: () => response(200, runsPage([newRun({ status: 'COMPLETED' }), newRun({ status: 'FAILED' }), newRun({ status: 'RUNNING' })])) }),
    );

    await waitFor(() => expect(runRows()).toHaveLength(3));
    expectSentenceCase(badges(), ['Completed', 'Failed', 'Running']);
  });

  it('[UX-OVERHAUL 17] the badges of the record in short: words with the code in brackets', async () => {
    signedInAs('provider1');
    installApi(defaultRoutes());
    renderApp('/portal/provider');

    await screen.findByRole('region', { name: 'Provider record' });
    expectSentenceCase(badges(), ['Enrolment status: Active (A)', 'Screening result: Pass (P)', 'Risk tier: Limited (L)']);
  });
});
