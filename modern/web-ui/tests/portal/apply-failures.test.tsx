// MAM-64 AC-6 and the focus / keyboard parts of AC-7: failures other than a business rejection are shown, never swallowed
// (D-024), and after a failed submit the focus moves to the error summary (frontend-stack section 5). The axe scans of
// AC-7 are in e2e/portal-apply.spec.ts.
import { screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { response, type Handler } from '../support/fakeApi';
import { application, APPLICATION_ID, applicationPage } from '../support/fixtures';
import {
  control,
  fieldError,
  fillIn,
  LIST_ROUTE,
  openApplyForm,
  rejection,
  rivera,
  submissions,
  submit,
  SUBMIT_ROUTE,
  summaryLinks,
  type FieldName,
} from '../support/applyForm';
import { oidc, setupShellWorld, text } from '../support/world';

vi.mock('oidc-client-ts', async (importOriginal) => {
  const actual = await importOriginal<typeof import('oidc-client-ts')>();
  const { FakeUserManager } = await import('../support/fakeOidc');
  return { ...actual, UserManager: FakeUserManager };
});

setupShellWorld();

const SUMMARY = 'The application could not be submitted';
const TYPED: Partial<Record<FieldName, string>> = rivera();

interface Failure {
  label: string;
  answer: Handler;
  /** What the reason has to say. */
  reason: RegExp;
}

const FAILURES: Failure[] = [
  {
    label: '400',
    answer: () => response(400, { title: 'Bad Request', status: 400, detail: 'licenseState must be 2 letters (detail sent by this test)' }),
    reason: /try again/i,
  },
  { label: '401', answer: () => response(401, { title: 'Not authenticated', status: 401 }), reason: /sign in again/i },
  { label: '403', answer: () => response(403, { title: 'Forbidden', status: 403 }), reason: /not allowed to apply/i },
  { label: '500', answer: () => response(500, { title: 'Internal Server Error', status: 500 }), reason: /try again/i },
  { label: '503', answer: () => response(503, { title: 'Service Unavailable', status: 503 }), reason: /try again/i },
  {
    label: 'network failure',
    answer: () => {
      throw new TypeError('Failed to fetch');
    },
    reason: /try again/i,
  },
  {
    label: '422 without any error',
    answer: () => response(422, { title: 'Unprocessable Entity', status: 422, detail: 'The application failed validation.' }),
    reason: /try again/i,
  },
];

async function submitCompleteForm(answer: Handler) {
  const rendered = await openApplyForm({
    [SUBMIT_ROUTE]: answer,
    [LIST_ROUTE]: () => response(200, applicationPage([application()])),
    [`GET /api/v1/applications/${APPLICATION_ID}`]: () => response(200, application()),
  });
  await fillIn(rendered.user, TYPED);
  await submit(rendered.user);
  await screen.findByRole('heading', { level: 2, name: SUMMARY });
  return rendered;
}

describe('[MAM-64 AC-6] other failures are shown, never swallowed', () => {
  it.each(FAILURES)('[MAM-64 AC-6] $label: the page shows the error summary "The application could not be submitted" with a matching reason', async ({ answer, reason }) => {
    await submitCompleteForm(answer);

    expect(within(screen.getByRole('main')).getAllByText(reason).length).toBeGreaterThan(0);
  });

  it('[MAM-64 AC-6] 400: the detail of the answer is shown', async () => {
    await submitCompleteForm(FAILURES[0]!.answer);

    expect(screen.getByText(/licenseState must be 2 letters \(detail sent by this test\)/)).toBeInTheDocument();
  });

  it('[MAM-64 AC-6] 403: the reason says that the user is not allowed to apply', async () => {
    await submitCompleteForm(FAILURES[2]!.answer);

    expect(screen.getByText(/You are not allowed to apply/i)).toBeInTheDocument();
  });

  it('[MAM-64 AC-6] 401: the reason says to sign in again and the page offers a "Sign in again" button that starts the sign-in for /portal/apply', async () => {
    const { user } = await submitCompleteForm(FAILURES[1]!.answer);

    await user.click(screen.getByRole('button', { name: 'Sign in again' }));

    await waitFor(() => expect(oidc.signinRedirect).toHaveBeenCalled());
    expect(oidc.lastSigninArgs?.state).toEqual({ returnTo: '/portal/apply' });
    expect(oidc.removeUser).toHaveBeenCalled();
  });

  it.each(FAILURES)('[MAM-64 AC-6] $label: everything that was typed is kept', async ({ answer }) => {
    await submitCompleteForm(answer);

    for (const [name, value] of Object.entries(TYPED)) expect(control(name as FieldName)).toHaveValue(value);
  });

  it.each(FAILURES)('[MAM-64 AC-6] $label: no success page, no status and no field error is shown', async ({ answer }) => {
    const { path } = await submitCompleteForm(answer);

    expect(path()).toBe('/portal/apply');
    expect(screen.getByRole('heading', { level: 1, name: 'Apply for enrolment' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { level: 1, name: 'Application status' })).toBeNull();
    expect(screen.queryByText(/Validated|Rejected/)).toBeNull();
    expect(summaryLinks()).toHaveLength(0);
    expect(screen.queryByText(/\bE\d{3}:/)).toBeNull();
  });

  it.each(FAILURES.filter((failure) => failure.label !== '401'))('[MAM-64 AC-6] $label: the user can submit again and the second answer is handled', async ({ answer }) => {
    const answers: Handler[] = [answer, () => response(201, application())];
    const { user, path } = await submitCompleteForm((call) => answers.shift()!(call));
    expect(screen.getByRole('button', { name: 'Submit application' })).toBeEnabled();

    await submit(user);

    expect(await within(await screen.findByRole('region', { name: 'Application' })).findByText('Validated')).toBeInTheDocument();
    expect(path()).toBe(`/portal/applications/${APPLICATION_ID}`);
    expect(submissions()).toHaveLength(2);
  });

  it('[MAM-64 AC-6] after a 401 the button still lets the user submit again (the form is not locked)', async () => {
    const { user } = await submitCompleteForm(FAILURES[1]!.answer);

    expect(screen.getByRole('button', { name: 'Submit application' })).toBeEnabled();
    await submit(user);

    await waitFor(() => expect(submissions()).toHaveLength(2));
  });

  it('[MAM-64 AC-6] a failure after a rejection replaces the field errors of the rejection', async () => {
    const answers: Handler[] = [rejection(fieldError('E104')), FAILURES[3]!.answer];
    const { user } = await openApplyForm({ [SUBMIT_ROUTE]: (call) => answers.shift()!(call), [LIST_ROUTE]: () => response(200, applicationPage([])) });
    await fillIn(user, rivera({ taxonomy: '' }));
    await submit(user);
    await screen.findByRole('heading', { level: 2, name: '1 error in the application' });

    await submit(user);

    await screen.findByRole('heading', { level: 2, name: SUMMARY });
    expect(screen.queryByRole('heading', { level: 2, name: '1 error in the application' })).toBeNull();
    expect(control('taxonomy')).not.toHaveAttribute('aria-invalid', 'true');
  });
});

describe('[MAM-64 AC-7] focus and keyboard in the form', () => {
  it('[MAM-64 AC-7] after a rejected submit the focus is in the error summary', async () => {
    const { user } = await openApplyForm({ [SUBMIT_ROUTE]: rejection(fieldError('E104')) });
    await fillIn(user, rivera({ taxonomy: '' }));
    await submit(user);

    const heading = await screen.findByRole('heading', { level: 2, name: '1 error in the application' });
    await waitFor(() => expect(document.activeElement?.contains(heading)).toBe(true));
    expect(document.activeElement).not.toBe(document.body);
  });

  it('[MAM-64 AC-7] after any other failed submit the focus is in the error summary too', async () => {
    await submitCompleteForm(FAILURES[3]!.answer);

    const heading = screen.getByRole('heading', { level: 2, name: SUMMARY });
    await waitFor(() => expect(document.activeElement?.contains(heading)).toBe(true));
  });

  it('[MAM-64 AC-7] the first Tab after the summary reaches its first link and Enter on it moves the focus to the field', async () => {
    const { user } = await openApplyForm({ [SUBMIT_ROUTE]: rejection(fieldError('E104')) });
    await fillIn(user, rivera({ taxonomy: '' }));
    await submit(user);
    await screen.findByRole('heading', { level: 2, name: '1 error in the application' });

    await user.tab();
    expect(document.activeElement).toBe(summaryLinks()[0]);
    await user.keyboard('{Enter}');

    expect(control('taxonomy')).toHaveFocus();
  });

  it('[MAM-64 AC-7] the tab order follows the visual order and ends at Submit application', async () => {
    const { user } = await openApplyForm();
    control('npi').focus();

    const order: Array<HTMLElement | string> = [control('medicaidId'), control('providerType'), control('taxonomy'), control('lastName'), control('firstName'), control('licenseNo'), control('licenseState'), 'Submit application'];
    for (const expected of order) {
      await user.tab();
      if (typeof expected === 'string') expect(text(document.activeElement)).toBe(expected);
      else expect(document.activeElement).toBe(expected);
    }
  });

  it('[MAM-64 AC-7] pressing Enter in an input sends the form (no mouse needed)', async () => {
    const { user } = await openApplyForm({ [SUBMIT_ROUTE]: rejection(fieldError('E104')) });
    await fillIn(user, rivera({ taxonomy: '' }));

    await user.type(control('licenseState'), '{Enter}');

    await waitFor(() => expect(submissions()).toHaveLength(1));
  });
});
