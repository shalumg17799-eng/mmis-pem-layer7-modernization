// UX overhaul 7, the step indicator on the application status page (user decisions B-iv item 1 and Q-104, 2026-11-19): the path of an
// application is a USWDS step indicator of five steps (Submitted, Validated, Screened, Risk assessed, Approved) with the CURRENT step
// highlighted and a heading "Step n of 5" with a sentence on where the application is. A step carries its state as text for
// assistive technology, so no state is shown by colour alone. Step 2 reads "Validated" (Q-104); of a rejected application it reads
// "Validation failed" at step 2 (the application did not pass it).
import { render, screen, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { Timeline } from '../../src/features/portal/applications/Timeline';
import { problem, response } from '../support/fakeApi';
import { application, APPLICATION_ID, type Application } from '../support/fixtures';
import { defaultRoutes, installApi, renderApp, setupShellWorld, signedInAs, text } from '../support/world';

vi.mock('oidc-client-ts', async (importOriginal) => {
  const actual = await importOriginal<typeof import('oidc-client-ts')>();
  const { FakeUserManager } = await import('../support/fakeOidc');
  return { ...actual, UserManager: FakeUserManager };
});

setupShellWorld();

type Status = Application['status'];

const GROUP = { name: 'Progress of your application' };
const segments = (): HTMLElement[] => Array.from(document.querySelectorAll<HTMLElement>('.usa-step-indicator__segment'));
const states = (): string[] =>
  segments().map((segment) => (segment.classList.contains('usa-step-indicator__segment--complete') ? 'complete' : segment.classList.contains('usa-step-indicator__segment--current') ? 'current' : 'waiting'));

const EXPECTED: Array<[Status, string[], string[], number, string]> = [
  [
    'SUBMITTED',
    ['Submitted completed', 'Validated not completed, current step', 'Screened not completed', 'Risk assessed not completed', 'Approved not completed'],
    ['complete', 'current', 'waiting', 'waiting', 'waiting'],
    2,
    'Being checked',
  ],
  [
    'VALIDATED',
    ['Submitted completed', 'Validated completed', 'Screened not completed, current step', 'Risk assessed not completed', 'Approved not completed'],
    ['complete', 'complete', 'current', 'waiting', 'waiting'],
    3,
    'Waiting for screening',
  ],
  [
    'REJECTED',
    ['Submitted completed', 'Validation failed not passed, current step', 'Screened not completed', 'Risk assessed not completed', 'Approved not completed'],
    ['complete', 'current', 'waiting', 'waiting', 'waiting'],
    2,
    'Not accepted',
  ],
];

describe('[UX-OVERHAUL 7] the step indicator of an application (B-iv 1, Q-104)', () => {
  it.each(EXPECTED)('[UX-OVERHAUL 7] %s: the five steps with their state as text for assistive technology', (status, labels) => {
    render(<Timeline status={status} />);

    expect(segments().map((segment) => text(segment))).toEqual(labels);
  });

  it.each(EXPECTED)('[UX-OVERHAUL 7] %s: the steps before the current one are complete, the current one is highlighted, the rest wait', (status, _labels, expected) => {
    render(<Timeline status={status} />);

    expect(states()).toEqual(expected);
  });

  it.each(EXPECTED)('[UX-OVERHAUL 7] %s: exactly one step is the current one, and only that step has aria-current', (status, _labels, expected) => {
    render(<Timeline status={status} />);

    const current = segments().filter((segment) => segment.classList.contains('usa-step-indicator__segment--current'));
    expect(current).toHaveLength(1);
    expect(segments().filter((segment) => segment.hasAttribute('aria-current'))).toEqual(current);
    expect(current[0]).toHaveAttribute('aria-current', 'true');
    expect(segments().indexOf(current[0]!)).toBe(expected.indexOf('current'));
  });

  it.each(EXPECTED)('[UX-OVERHAUL 7] %s: the heading gives the number of the current step out of 5 and says where the application is in words', (status, _labels, _states, step, where) => {
    render(<Timeline status={status} />);

    expect(document.querySelector('.usa-step-indicator__current-step')).toHaveTextContent(String(step));
    expect(text(document.querySelector('.usa-step-indicator__total-steps'))).toBe('of 5');
    expect(text(document.querySelector('.usa-step-indicator__heading-text'))).toBe(where);
    expect(text(document.querySelector('.usa-step-indicator__heading-counter'))).toContain('Step');
  });

  it('[UX-OVERHAUL 7] the indicator is a labelled group holding an ordered list of five steps', () => {
    render(<Timeline status="VALIDATED" />);

    const group = screen.getByRole('group', GROUP);
    expect(group).toHaveClass('usa-step-indicator');
    expect(within(group).getByRole('list').tagName).toBe('OL');
    expect(within(group).getAllByRole('listitem')).toHaveLength(5);
  });

  it('[UX-OVERHAUL 7] the five names are Submitted, Validated, Screened, Risk assessed and Approved, in that order', () => {
    render(<Timeline status="SUBMITTED" />);

    const names = segments().map((segment) => segment.querySelector('.usa-step-indicator__segment-label')?.firstChild?.textContent?.trim());
    expect(names).toEqual(['Submitted', 'Validated', 'Screened', 'Risk assessed', 'Approved']);
  });

  it('[UX-OVERHAUL 7] a rejected application never shows "Validated" at step 2, and the other statuses never show "Validation failed" (Q-104)', () => {
    const { unmount } = render(<Timeline status="REJECTED" />);
    expect(text(segments()[1])).toContain('Validation failed');
    expect(text(document.body)).not.toContain('Validated');
    unmount();

    for (const status of ['SUBMITTED', 'VALIDATED'] as const) {
      const view = render(<Timeline status={status} />);
      expect(text(segments()[1])).toContain('Validated');
      expect(text(document.body)).not.toContain('Validation failed');
      view.unmount();
    }
  });

  it('[UX-OVERHAUL 7] the state is also text, not colour alone: every step carries its state as screen-reader text', () => {
    render(<Timeline status="REJECTED" />);

    for (const segment of segments()) expect(segment.querySelector('.usa-sr-only')?.textContent?.trim()).toBeTruthy();
    expect(Array.from(document.querySelectorAll('.usa-step-indicator__segment .usa-sr-only')).map((hidden) => text(hidden))).toEqual([
      'completed',
      'not passed, current step',
      'not completed',
      'not completed',
      'not completed',
    ]);
  });
});

describe('[UX-OVERHAUL 7] the indicator on the status page', () => {
  async function openStatus(status: Status, extra: Partial<Application> = {}) {
    signedInAs('provider1');
    installApi({ ...defaultRoutes(), [`GET /api/v1/applications/${APPLICATION_ID}`]: () => response(200, application({ status, ...extra })) });
    renderApp(`/portal/applications/${APPLICATION_ID}`);
    await screen.findByRole('group', GROUP);
  }

  it.each(EXPECTED)('[UX-OVERHAUL 7] an application that is %s shows the indicator with the current step highlighted', async (status, labels, expected) => {
    await openStatus(status, status === 'REJECTED' ? { errors: [{ code: 'E104', field: 'taxonomy', message: 'Taxonomy code is required' }] } : {});

    expect(segments().map((segment) => text(segment))).toEqual(labels);
    expect(states()).toEqual(expected);
  });

  it('[UX-OVERHAUL 7] the indicator comes after the heading and before the list "Application"', async () => {
    await openStatus('VALIDATED');

    const group = screen.getByRole('group', GROUP);
    const heading = screen.getByRole('heading', { level: 1, name: 'Application status' });
    const region = screen.getByRole('region', { name: 'Application' });
    expect(heading.compareDocumentPosition(group) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(group.compareDocumentPosition(region) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it('[UX-OVERHAUL 7] the badge in the list "Application" is the one element whose whole text is the status, next to the step "Validation failed" of a rejected application', async () => {
    await openStatus('REJECTED', { errors: [{ code: 'E104', field: 'taxonomy', message: 'Taxonomy code is required' }] });

    const region = within(screen.getByRole('region', { name: 'Application' }));
    expect(region.getByText('Rejected')).toBeInTheDocument();
    expect(region.queryByText('Validated')).toBeNull();
    expect(text(segments()[1])).toBe('Validation failed not passed, current step');
  });

  it.each([
    [404, 'Application not found'],
    [403, 'Access denied. You are not allowed to see this application.'],
  ] as const)('[UX-OVERHAUL 7] a %s answer shows %s and no indicator (nothing is claimed about an application that is not shown)', async (status, message) => {
    signedInAs('provider1');
    installApi({ ...defaultRoutes(), [`GET /api/v1/applications/${APPLICATION_ID}`]: () => response(status, problem(status, 'x')) });
    renderApp(`/portal/applications/${APPLICATION_ID}`);

    expect(await screen.findByText(message)).toBeInTheDocument();
    expect(screen.queryByRole('group', GROUP)).toBeNull();
  });
});
