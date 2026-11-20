// UX overhaul 14 to 16, the three additions of the first overhaul round that keep the recorded flows unchanged (user decisions Q-101 B4,
// B7 and B2, 2026-11-19: "keep this version"):
//  14  the apply form has an on-page "Review your answers" summary just above "Submit application"; it is live and read-only, has
//      no control of its own, shows no error and no code, and one click on Submit application still sends the request;
//  15  the decision form has a live "You are about to approve / deny ..." line above "Record decision"; there is no confirmation
//      dialog, and nothing is sent while a choice is made;
//  16  the work queues start with four count tiles that link to their sections; a tile never shows a count it does not have.
import { screen, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { approvalWorld, approveRadio, decisions, denyRadio, failedScreening, openApproval, passedScreening, pendingFail, pendingPass, recordButton, CLINIC_NPI, RIVERA_NPI } from '../support/approval';
import { control, fieldError, fillIn, openApplyForm, rejection, rivera, submissions, submit, SUBMIT_ROUTE } from '../support/applyForm';
import { problem, response } from '../support/fakeApi';
import { callsTo, emptyQueues, fullQueues, openQueues, providerPage, queueRoutes } from '../support/queues';
import { setupShellWorld, tabUntil, text } from '../support/world';
import { listRows } from '../support/uxWorld';

vi.mock('oidc-client-ts', async (importOriginal) => {
  const actual = await importOriginal<typeof import('oidc-client-ts')>();
  const { FakeUserManager } = await import('../support/fakeOidc');
  return { ...actual, UserManager: FakeUserManager };
});

setupShellWorld();

const before = (first: Element, second: Element): boolean => (first.compareDocumentPosition(second) & Node.DOCUMENT_POSITION_FOLLOWING) !== 0;
const review = (): HTMLElement => screen.getByRole('region', { name: 'Review your answers' });

describe('[UX-OVERHAUL 14] "Review your answers" on the apply form (Q-101 B4)', () => {
  it('[UX-OVERHAUL 14] an empty form says so in words: every field "Not entered yet", the type "Not chosen yet", the individual fields first', async () => {
    await openApplyForm();

    expect(listRows(review())).toEqual([
      ['NPI:', 'Not entered yet'],
      ['Medicaid ID:', 'Not entered yet'],
      ['Provider type:', 'Not chosen yet'],
      ['Taxonomy code:', 'Not entered yet'],
      ['Last name:', 'Not entered yet'],
      ['First name:', 'Not entered yet'],
      ['Licence number:', 'Not entered yet'],
      ['Licence state:', 'Not entered yet'],
    ]);
  });

  it('[UX-OVERHAUL 14] the summary follows what is typed, live, with the type as words and code', async () => {
    const { user } = await openApplyForm();

    await fillIn(user, rivera());

    expect(listRows(review())).toEqual([
      ['NPI:', '1003000111'],
      ['Medicaid ID:', 'TX0000111'],
      ['Provider type:', 'Individual (IN)'],
      ['Taxonomy code:', '207Q00000X'],
      ['Last name:', 'RIVERA'],
      ['First name:', 'MARIA'],
      ['Licence number:', 'MD12345'],
      ['Licence state:', 'TX'],
    ]);
  });

  it('[UX-OVERHAUL 14] choosing a group changes the rows to the organisation details', async () => {
    const { user } = await openApplyForm();

    await fillIn(user, { providerType: 'GR', legalName: 'RIO GRANDE FAMILY MEDICINE PLLC', ownerNpi: '1990000111' });

    expect(listRows(review()).map(([term]) => term)).toEqual(['NPI:', 'Medicaid ID:', 'Provider type:', 'Taxonomy code:', 'Legal name:', 'DBA name:', 'Owner NPI:']);
    expect(listRows(review()).find(([term]) => term === 'Provider type:')?.[1]).toBe('Group (GR)');
    expect(listRows(review()).find(([term]) => term === 'Legal name:')?.[1]).toBe('RIO GRANDE FAMILY MEDICINE PLLC');
    expect(listRows(review()).find(([term]) => term === 'DBA name:')?.[1]).toBe('Not entered yet');
  });

  it('[UX-OVERHAUL 14] a value with blanks around it is shown without them, and a blank value is "Not entered yet"', async () => {
    const { user } = await openApplyForm();

    await fillIn(user, { npi: ' 1003 ', medicaidId: '   ' });

    expect(listRows(review()).slice(0, 2)).toEqual([
      ['NPI:', '1003'],
      ['Medicaid ID:', 'Not entered yet'],
    ]);
  });

  it('[UX-OVERHAUL 14] the box is a region named "Review your answers" with an h2, and it says that nothing is sent until Submit application', async () => {
    await openApplyForm();

    expect(within(review()).getByRole('heading', { level: 2, name: 'Review your answers' })).toBeInTheDocument();
    expect(within(review()).getByText('Nothing is sent until you choose Submit application.')).toBeInTheDocument();
  });

  it('[UX-OVERHAUL 14] it sits after the fields and just before the Submit application button', async () => {
    await openApplyForm();

    const last = control('licenseState');
    const button = screen.getByRole('button', { name: 'Submit application' });
    expect(before(last, review())).toBe(true);
    expect(before(review(), button)).toBe(true);
  });

  it('[UX-OVERHAUL 14] it has no control of its own, so the tab order of the form is unchanged: after the last field the next stop is Submit application', async () => {
    const { user } = await openApplyForm();
    expect(within(review()).queryAllByRole('button')).toHaveLength(0);
    expect(within(review()).queryAllByRole('link')).toHaveLength(0);
    expect(within(review()).queryAllByRole('textbox')).toHaveLength(0);

    const stops = await tabUntil(user, (element) => element === control('licenseState'));
    await user.tab();

    expect(screen.getByRole('button', { name: 'Submit application' })).toHaveFocus();
    expect(stops).not.toContain('Review your answers');
  });

  it('[UX-OVERHAUL 14] nothing is sent while the form is filled in, and one click on Submit application sends it once', async () => {
    const { user } = await openApplyForm({ [SUBMIT_ROUTE]: () => response(201, { applicationId: '0b9c5d1e-6a4f-4c1a-9a43-3c2d6f0a1b11', status: 'VALIDATED', npi: '1003000111', providerType: 'IN', taxonomy: '207Q00000X', source: 'PORTAL', submittedAt: '2026-03-01T10:00:00Z' }) });
    await fillIn(user, rivera());
    expect(submissions()).toHaveLength(0);

    await submit(user);

    expect(await screen.findByRole('heading', { level: 1, name: 'Application status' })).toBeInTheDocument();
    expect(submissions()).toHaveLength(1);
  });

  it('[UX-OVERHAUL 14] the summary shows no error and no code: after a rejection the codes are in the error summary and at the fields, not in the review box', async () => {
    const { user } = await openApplyForm({ [SUBMIT_ROUTE]: rejection(fieldError('E104')) });
    await fillIn(user, rivera({ taxonomy: '' }));

    await submit(user);

    expect(await screen.findByRole('heading', { level: 2, name: '1 error in the application' })).toBeInTheDocument();
    expect(text(review())).not.toMatch(/E\d{3}/);
    expect(listRows(review()).find(([term]) => term === 'Taxonomy code:')?.[1]).toBe('Not entered yet');
  });
});

describe('[UX-OVERHAUL 15] the live "You are about to ..." line of the decision form (Q-101 B7)', () => {
  const NOTHING = 'No decision chosen yet. Nothing is sent until you choose Record decision.';
  const APPROVE = 'You are about to approve the enrolment of RIVERA MARIA (NPI 1003000111). The provider becomes Active (A) and the revalidation date is set five years after the approval date.';
  const DENY = 'You are about to deny the enrolment of RIVERA MARIA (NPI 1003000111). The provider becomes Denied (D).';
  const line = (): HTMLElement => document.querySelector<HTMLElement>('p[aria-live="polite"].text-bold')!;

  async function open() {
    const rendered = await openApproval('analyst1', approvalWorld(pendingPass(), passedScreening()), RIVERA_NPI);
    await screen.findByRole('button', { name: 'Record decision' });
    return rendered;
  }

  it('[UX-OVERHAUL 15] before a choice it says that nothing is chosen and nothing is sent', async () => {
    await open();

    expect(text(line())).toBe(NOTHING);
  });

  it('[UX-OVERHAUL 15] choosing Approve says who and what follows: Active (A) and the revalidation date five years after the approval', async () => {
    const { user } = await open();

    await user.click(approveRadio());

    expect(text(line())).toBe(APPROVE);
  });

  it('[UX-OVERHAUL 15] choosing Deny says who and that the provider becomes Denied (D)', async () => {
    const { user } = await open();

    await user.click(denyRadio());

    expect(text(line())).toBe(DENY);
  });

  it('[UX-OVERHAUL 15] changing the choice changes the line at once, and the line is a polite live region', async () => {
    const { user } = await open();

    await user.click(approveRadio());
    expect(text(line())).toBe(APPROVE);
    await user.click(denyRadio());
    expect(text(line())).toBe(DENY);
    await user.click(approveRadio());
    expect(text(line())).toBe(APPROVE);
    expect(line()).toHaveAttribute('aria-live', 'polite');
  });

  it('[UX-OVERHAUL 15] the line is in bold, below the choices and above the Record decision button', async () => {
    await open();

    expect(line()).toHaveClass('text-bold');
    expect(before(denyRadio(), line())).toBe(true);
    expect(before(line(), recordButton())).toBe(true);
  });

  it('[UX-OVERHAUL 15] there is no confirmation dialog: choosing sends nothing, and Record decision sends the decision at once', async () => {
    const { user } = await open();

    await user.click(approveRadio());
    expect(screen.queryByRole('dialog')).toBeNull();
    expect(screen.queryByRole('alertdialog')).toBeNull();
    expect(decisions(RIVERA_NPI)).toHaveLength(0);
    await user.click(recordButton());

    expect(await screen.findByText(/^Decision recorded\./)).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).toBeNull();
    expect(decisions(RIVERA_NPI)).toHaveLength(1);
  });

  it('[UX-OVERHAUL 15] the line names the organisation and its NPI for a group', async () => {
    const { user } = await openApproval('analyst1', approvalWorld(pendingFail(), failedScreening()), CLINIC_NPI);
    await screen.findByRole('button', { name: 'Record decision' });

    await user.click(denyRadio());

    expect(text(line())).toBe('You are about to deny the enrolment of EXCLUDED CLINIC LLC (NPI 1003000444). The provider becomes Denied (D).');
    expect(approveRadio()).toBeDisabled();
    expect(screen.getByText('Approval needs a passed screening')).toBeInTheDocument();
  });
});

describe('[UX-OVERHAUL 16] the count tiles on the work queues (Q-101 B2)', () => {
  const tiles = (): Array<[string, string, string, string | null]> =>
    Array.from(screen.getByRole('list', { name: 'Queue summary' }).querySelectorAll('li')).map((item) => {
      const spans = Array.from(item.querySelectorAll('span')).map((span) => text(span));
      return [spans[0] ?? '', spans[1] ?? '', spans[2] ?? '', item.querySelector('a')?.getAttribute('href') ?? null];
    });

  it('[UX-OVERHAUL 16] four tiles in the order of the sections: the count, the queue and what the count means, each a link to its section', async () => {
    await openQueues('analyst1', queueRoutes(fullQueues()));
    await screen.findByRole('heading', { level: 2, name: 'Rejected applications (1)' });

    expect(tiles()).toEqual([
      ['1', 'Pending approval', 'providers waiting for your decision', '#queue-pending-approval'],
      ['1', 'Failed screening', 'providers that failed a check', '#queue-failed-screening'],
      ['1', 'Revalidation due', 'revalidation cases open', '#queue-revalidation-due'],
      ['1', 'Rejected applications', 'applications to follow up', '#queue-rejected-applications'],
    ]);
  });

  it('[UX-OVERHAUL 16] every tile leads to a section that is on the page, and the sections are the only h2 of the page (the tiles add no heading)', async () => {
    await openQueues('analyst1', queueRoutes(fullQueues()));
    await screen.findByRole('heading', { level: 2, name: 'Rejected applications (1)' });

    for (const [, , , href] of tiles()) {
      const section = document.getElementById(href!.slice(1));
      expect(section?.tagName).toBe('SECTION');
    }
    expect(screen.getAllByRole('heading', { level: 2 }).map((heading) => text(heading))).toEqual([
      'Pending approval (1)',
      'Failed screening (1)',
      'Revalidation due (1)',
      'Rejected applications (1)',
    ]);
  });

  it('[UX-OVERHAUL 16] the count is the total of the queue, not the number of rows on the page', async () => {
    await openQueues('analyst1', queueRoutes({ ...fullQueues(), pending: providerPage([pendingPass()], 25) }));
    await screen.findByRole('heading', { level: 2, name: 'Pending approval (25)' });

    expect(tiles()[0]?.slice(0, 2)).toEqual(['25', 'Pending approval']);
  });

  it('[UX-OVERHAUL 16] empty queues show the count 0 and say what it means, not a dash', async () => {
    await openQueues('analyst1', queueRoutes(emptyQueues()));
    await screen.findByText('No revalidations due');

    expect(tiles().map(([count]) => count)).toEqual(['0', '0', '0', '0']);
  });

  it('[UX-OVERHAUL 16] while a queue loads its tile shows "..." and "Loading"; it claims no count', async () => {
    await openQueues('analyst1', queueRoutes(fullQueues(), { 'GET /api/v1/applications': () => new Promise<Response>(() => undefined) }));

    expect(tiles()[3]).toEqual(['...', 'Rejected applications', 'Loading', '#queue-rejected-applications']);
  });

  it('[UX-OVERHAUL 16] a queue that fails shows "-" and says so; the other tiles keep their counts', async () => {
    await openQueues('analyst1', queueRoutes(fullQueues(), { 'GET /api/v1/revalidations': () => response(500, problem(500, 'Internal Server Error')) }));
    await screen.findByRole('heading', { level: 2, name: 'Rejected applications (1)' });

    expect(tiles()[2]).toEqual(['-', 'Revalidation due', 'The list could not be loaded', '#queue-revalidation-due']);
    expect(tiles().filter((tile) => tile[0] === '1').map((tile) => tile[1])).toEqual(['Pending approval', 'Failed screening', 'Rejected applications']);
  });

  it('[UX-OVERHAUL 16] the tiles share the answers of the sections: no extra request for the providers or the applications', async () => {
    await openQueues('analyst1', queueRoutes(fullQueues()));
    await screen.findByRole('heading', { level: 2, name: 'Rejected applications (1)' });

    expect(callsTo('/api/v1/providers')).toHaveLength(2);
    expect(callsTo('/api/v1/applications')).toHaveLength(1);
  });

  it('[UX-OVERHAUL 16] a supervisor has the tiles too', async () => {
    await openQueues('supervisor1', queueRoutes(fullQueues()));

    expect(tiles()).toHaveLength(4);
  });
});
