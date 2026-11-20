// MAM-64 AC-5: a rejected application can be found afterwards and nothing is registered. After the errors of AC-3 the page
// offers "My applications"; the portal home lists the application as Rejected with a View link; the status page shows it;
// My provider record (GET /api/v1/providers/1003000111 answers 404) says that no record exists yet.
// The full status page and provider record page belong to their own stories (ui-portal-status, ui-portal-record).
import { screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { problem, response } from '../support/fakeApi';
import { application, APPLICATION_ID, applicationPage, PROVIDER_NPI } from '../support/fixtures';
import { fieldError, fillIn, LIST_ROUTE, openApplyForm, rejection, rivera, submit, SUBMIT_ROUTE } from '../support/applyForm';
import { api, bodyRows, cellTexts, findApplicationsTable, renderApp, setupShellWorld, signedInAs, text, installApi, defaultRoutes } from '../support/world';

vi.mock('oidc-client-ts', async (importOriginal) => {
  const actual = await importOriginal<typeof import('oidc-client-ts')>();
  const { FakeUserManager } = await import('../support/fakeOidc');
  return { ...actual, UserManager: FakeUserManager };
});

setupShellWorld();

const REJECTED = application({ status: 'REJECTED', taxonomy: '', errors: [fieldError('E104')] });

/** The state after AC-3: the service stored a REJECTED application; there is no provider record. */
const AFTER_REJECTION = {
  [SUBMIT_ROUTE]: rejection(fieldError('E104')),
  [LIST_ROUTE]: () => response(200, applicationPage([REJECTED])),
  [`GET /api/v1/applications/${APPLICATION_ID}`]: () => response(200, REJECTED),
  [`GET /api/v1/providers/${PROVIDER_NPI}`]: () => response(404, problem(404, 'Not Found')),
};

async function rejectedSubmission() {
  const rendered = await openApplyForm(AFTER_REJECTION);
  await fillIn(rendered.user, rivera({ taxonomy: '' }));
  await submit(rendered.user);
  await screen.findByRole('heading', { level: 2, name: '1 error in the application' });
  return rendered;
}

describe('[MAM-64 AC-5] a rejected application can be found afterwards', () => {
  it('[MAM-64 AC-5] after the errors the page offers a link "My applications" to /portal', async () => {
    await rejectedSubmission();

    const link = within(screen.getByRole('main')).getByRole('link', { name: 'My applications' });
    expect(link).toHaveAttribute('href', '/portal');
  });

  it('[MAM-64 AC-5] choosing "My applications" opens the portal home, which lists the application with status Rejected and a View link', async () => {
    const { user, path } = await rejectedSubmission();

    await user.click(within(screen.getByRole('main')).getByRole('link', { name: 'My applications' }));

    expect(await screen.findByRole('heading', { level: 1, name: 'My enrolment' })).toBeInTheDocument();
    expect(path()).toBe('/portal');
    const table = await findApplicationsTable();
    const rows = bodyRows(table);
    expect(rows).toHaveLength(1);
    const cells = cellTexts(rows[0]!);
    expect(cells[1]).toBe('1003000111');
    expect(cells[2]).toBe('Rejected');
    expect(within(rows[0]!).getByRole('link', { name: /^View/ })).toHaveAttribute('href', `/portal/applications/${APPLICATION_ID}`);
  });

  it('[MAM-64 AC-5] the View link opens the application status page, which shows the rejected application', async () => {
    const { user, path } = await rejectedSubmission();
    await user.click(within(screen.getByRole('main')).getByRole('link', { name: 'My applications' }));
    const table = await findApplicationsTable();

    await user.click(within(bodyRows(table)[0]!).getByRole('link', { name: /^View/ }));

    expect(await screen.findByRole('heading', { level: 1, name: 'Application status' })).toBeInTheDocument();
    expect(path()).toBe(`/portal/applications/${APPLICATION_ID}`);
    const main = within(screen.getByRole('main'));
    expect(await main.findByText('Rejected')).toBeInTheDocument();
    expect(main.getByText('1003000111')).toBeInTheDocument();
    expect(main.queryByText('Validated')).toBeNull();
  });

  it('[MAM-64 AC-5] opening /portal and then My provider record says that no record exists yet', async () => {
    signedInAs('provider1');
    installApi({ ...defaultRoutes(), ...AFTER_REJECTION });
    const { user, path } = renderApp('/portal');
    await findApplicationsTable();

    await user.click(within(screen.getByRole('navigation', { name: 'Primary navigation' })).getByRole('link', { name: 'My provider record' }));

    expect(await screen.findByRole('heading', { level: 1, name: 'My provider record' })).toBeInTheDocument();
    expect(path()).toBe('/portal/provider');
    expect(await within(screen.getByRole('main')).findByText(/No provider record exists yet/i)).toBeInTheDocument();
    expect(api.to('GET', `/api/v1/providers/${PROVIDER_NPI}`).length).toBeGreaterThan(0);
  });

  it('[MAM-64 AC-5] /portal/provider opened directly asks for the provider of the token (1003000111) and says that no record exists yet', async () => {
    signedInAs('provider1');
    installApi({ ...defaultRoutes(), ...AFTER_REJECTION });
    renderApp('/portal/provider');

    expect(await within(await screen.findByRole('main')).findByText(/No provider record exists yet/i)).toBeInTheDocument();
    expect(api.calls.filter((call) => call.url.pathname.startsWith('/api/v1/providers/')).map((call) => call.url.pathname)).toEqual([`/api/v1/providers/${PROVIDER_NPI}`]);
    expect(screen.queryByText(/not available yet/i)).toBeNull();
  });

  it('[MAM-64 AC-5] the provider record page shows no status of an application', async () => {
    signedInAs('provider1');
    installApi({ ...defaultRoutes(), ...AFTER_REJECTION });
    renderApp('/portal/provider');

    await within(await screen.findByRole('main')).findByText(/No provider record exists yet/i);
    expect(text(screen.getByRole('main'))).not.toMatch(/Rejected|Validated/);
  });

  it('[MAM-64 AC-5] when the provider record cannot be loaded the page says so instead of claiming that none exists', async () => {
    signedInAs('provider1');
    installApi({ ...defaultRoutes(), [`GET /api/v1/providers/${PROVIDER_NPI}`]: () => response(500, problem(500, 'Internal Server Error')) });
    renderApp('/portal/provider');

    expect(await screen.findByText(/could not be loaded/i)).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByText(/No provider record exists yet/i)).toBeNull());
  });
});
