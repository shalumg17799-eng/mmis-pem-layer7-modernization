// UX overhaul 10, the application status page (user decisions of 2026-11-19 19:54, Q-110, B-iv items 7 and 8): the application ID is
// shown as "Reference: <id>" under the heading, not as a second "Application" row of the list; and only ONE confirmation is shown,
// the green box "Application received" with what happens next, after a submit. A later visit has no box: a sentence tells where a
// submitted or a rejected application is, and a validated one has neither (the badge and the step indicator say it). The old
// sentence "Your application was accepted ..." is gone. A rejected application offers "Apply again".
import { screen, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { response } from '../support/fakeApi';
import { application, APPLICATION_ID, type Application } from '../support/fixtures';
import { defaultRoutes, installApi, renderApp, setupShellWorld, signedInAs, text } from '../support/world';
import { listRows, renderAppWithState } from '../support/uxWorld';

vi.mock('oidc-client-ts', async (importOriginal) => {
  const actual = await importOriginal<typeof import('oidc-client-ts')>();
  const { FakeUserManager } = await import('../support/fakeOidc');
  return { ...actual, UserManager: FakeUserManager };
});

setupShellWorld();

type Status = Application['status'];

const ERROR = { code: 'E104', field: 'taxonomy', message: 'Taxonomy code is required' } as const;
const BY_ID = `GET /api/v1/applications/${APPLICATION_ID}`;
const ROUTE = `/portal/applications/${APPLICATION_ID}`;
const NEXT_STEPS = 'What happens next: automatic checks run first, then an analyst decides on your enrolment. You can follow the progress on this page and under My enrolment.';
const SUBMITTED_LEAD = 'Your application was received and is being checked.';
const REJECTED_LEAD = 'Your application was not accepted. The reasons are listed below; correct them and apply again.';

function record(status: Status): Application {
  return application({ status, ...(status === 'REJECTED' ? { taxonomy: '', errors: [ERROR] } : {}) });
}

async function openStatus(status: Status, received = false) {
  signedInAs('provider1');
  installApi({ ...defaultRoutes(), [BY_ID]: () => response(200, record(status)) });
  const rendered = received ? renderAppWithState(ROUTE, { received: true }) : renderApp(ROUTE);
  await screen.findByRole('region', { name: 'Application' });
  return rendered;
}

const reference = (): HTMLElement => screen.getByText(/^Reference: /);
const before = (first: Element, second: Element): boolean => (first.compareDocumentPosition(second) & Node.DOCUMENT_POSITION_FOLLOWING) !== 0;

describe('[UX-OVERHAUL 10] "Reference: <id>" under the heading (Q-110)', () => {
  it.each(['SUBMITTED', 'VALIDATED', 'REJECTED'] as Status[])('[UX-OVERHAUL 10] %s: the application ID is the paragraph "Reference: <id>" right under the h1', async (status) => {
    await openStatus(status);

    const heading = screen.getByRole('heading', { level: 1, name: 'Application status' });
    expect(heading.nextElementSibling).toBe(reference());
    expect(reference().tagName).toBe('P');
    expect(text(reference())).toBe(`Reference: ${APPLICATION_ID}`);
  });

  it('[UX-OVERHAUL 10] the ID is shown once only, in full, and is not repeated as a row of the list', async () => {
    await openStatus('VALIDATED');

    expect(screen.getAllByText(new RegExp(APPLICATION_ID))).toHaveLength(1);
    expect(text(screen.getByRole('region', { name: 'Application' }))).not.toContain(APPLICATION_ID);
  });

  it('[UX-OVERHAUL 10] the list "Application" has no "Application" row: it starts with Status, and its name is only the name of the region', async () => {
    await openStatus('VALIDATED');

    const region = screen.getByRole('region', { name: 'Application' });
    const terms = listRows(region).map(([term]) => term);
    expect(terms[0]).toBe('Status');
    expect(terms).not.toContain('Application');
    expect(terms).not.toContain('Reference');
  });

  it('[UX-OVERHAUL 10] the reference comes before the confirmation, the step indicator and the list', async () => {
    await openStatus('VALIDATED', true);

    const alert = screen.getByRole('heading', { level: 2, name: 'Application received' }).closest('.usa-alert')!;
    expect(before(reference(), alert)).toBe(true);
    expect(before(reference(), screen.getByRole('group', { name: 'Progress of your application' }))).toBe(true);
    expect(before(reference(), screen.getByRole('region', { name: 'Application' }))).toBe(true);
  });
});

describe('[UX-OVERHAUL 10] one confirmation only: the green box "Application received" after a submit', () => {
  it.each(['SUBMITTED', 'VALIDATED'] as Status[])('[UX-OVERHAUL 10] after a submit (%s) the page shows the green box with its h2 and what happens next', async (status) => {
    await openStatus(status, true);

    const heading = screen.getByRole('heading', { level: 2, name: 'Application received' });
    const box = heading.closest('.usa-alert')!;
    expect(box).toHaveClass('usa-alert--success');
    expect(text(box)).toContain(NEXT_STEPS);
  });

  it.each(['SUBMITTED', 'VALIDATED'] as Status[])('[UX-OVERHAUL 10] after a submit (%s) no sentence repeats it: "Your application was received ..." and "Your application was accepted ..." are not shown', async (status) => {
    await openStatus(status, true);

    expect(screen.queryByText(SUBMITTED_LEAD)).toBeNull();
    expect(screen.queryByText(/Your application was accepted/)).toBeNull();
    expect(screen.getAllByText(/received/i)).toHaveLength(1);
    expect(screen.getAllByRole('heading', { level: 2 }).map((heading) => text(heading))).toEqual(['Application received']);
  });

  it('[UX-OVERHAUL 10] the box comes before the step indicator', async () => {
    await openStatus('SUBMITTED', true);

    const box = screen.getByRole('heading', { level: 2, name: 'Application received' }).closest('.usa-alert')!;
    expect(before(box, screen.getByRole('group', { name: 'Progress of your application' }))).toBe(true);
  });

  it('[UX-OVERHAUL 10] a later visit (the address typed or the link "View") has no box: the confirmation belongs to the arrival after the submit', async () => {
    const { user } = await openStatus('VALIDATED', true);
    expect(screen.getByRole('heading', { level: 2, name: 'Application received' })).toBeInTheDocument();
    installApi({ ...defaultRoutes([application()]), [BY_ID]: () => response(200, record('VALIDATED')) });

    await user.click(screen.getByRole('link', { name: 'Home' }));
    await screen.findByRole('heading', { level: 1, name: 'My enrolment' });
    await user.click(await screen.findByRole('link', { name: /^View/ }));

    await screen.findByRole('heading', { level: 1, name: 'Application status' });
    await screen.findByRole('region', { name: 'Application' });
    expect(screen.queryByRole('heading', { level: 2, name: 'Application received' })).toBeNull();
  });
});

describe('[UX-OVERHAUL 10] a later visit: the sentence for where the application is, never the box', () => {
  it('[UX-OVERHAUL 10] SUBMITTED: "Your application was received and is being checked." as the introduction, no box', async () => {
    await openStatus('SUBMITTED');

    expect(screen.getByText(SUBMITTED_LEAD)).toHaveClass('usa-intro');
    expect(screen.queryByRole('heading', { level: 2, name: 'Application received' })).toBeNull();
  });

  it('[UX-OVERHAUL 10] REJECTED: the sentence says the reasons are listed below, and the errors are listed in a table', async () => {
    await openStatus('REJECTED');

    expect(screen.getByText(REJECTED_LEAD)).toHaveClass('usa-intro');
    expect(screen.getByRole('table', { name: 'Validation errors' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { level: 2, name: 'Application received' })).toBeNull();
  });

  it('[UX-OVERHAUL 10] VALIDATED: neither a box nor a sentence, and the old "Your application was accepted ..." is gone', async () => {
    await openStatus('VALIDATED');

    expect(screen.queryByRole('heading', { level: 2 })).toBeNull();
    expect(screen.queryByText(/Your application was/)).toBeNull();
    expect(text(screen.getByRole('main'))).not.toMatch(/was accepted/);
  });
});

describe('[UX-OVERHAUL 10] the status badge and the way on from a rejection', () => {
  it.each([
    ['SUBMITTED', 'Submitted', undefined],
    ['VALIDATED', 'Validated', 'bg-success-dark'],
    ['REJECTED', 'Rejected', 'bg-secondary-dark'],
  ] as Array<[Status, string, string | undefined]>)('[UX-OVERHAUL 10] %s: the badge reads "%s" (the word, colour only adds)', async (status, word, colour) => {
    await openStatus(status);

    const badge = within(screen.getByRole('region', { name: 'Application' })).getAllByText(word).find((element) => element.classList.contains('usa-tag'));
    expect(badge).toBeDefined();
    expect(text(badge)).toBe(word);
    if (colour) expect(badge).toHaveClass(colour);
    else expect(badge?.className).not.toMatch(/bg-/);
  });

  it('[UX-OVERHAUL 10] a rejected application offers "Apply again" as a button that opens the form', async () => {
    const { user, path } = await openStatus('REJECTED');

    const link = screen.getByRole('link', { name: 'Apply again' });
    expect(link).toHaveClass('usa-button');
    await user.click(link);

    expect(await screen.findByRole('heading', { level: 1, name: 'Apply for enrolment' })).toBeInTheDocument();
    expect(path()).toBe('/portal/apply');
  });

  it.each(['SUBMITTED', 'VALIDATED'] as Status[])('[UX-OVERHAUL 10] %s: there is no "Apply again"', async (status) => {
    await openStatus(status);

    expect(screen.queryByRole('link', { name: 'Apply again' })).toBeNull();
  });
});
