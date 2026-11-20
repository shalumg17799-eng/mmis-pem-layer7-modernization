// MAM-64 AC-2 and AC-5: the minimum of the application status page that the acceptance criteria need
// (/portal/applications/:applicationId shows 'Validated' or 'Rejected'). Corrected under Q-097 (2026-11-19): since MAM-66 AC-1
// the page reads GET /api/v1/applications/{id} (MAM-54), so the stubs answer that too (404 for an unknown id).
// The full page (errors table, history) belongs to the story ui-portal-status.
import { screen, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { problem, response } from '../support/fakeApi';
import { application, APPLICATION_ID, applicationPage } from '../support/fixtures';
import { LIST_ROUTE } from '../support/applyForm';
import { api, defaultRoutes, installApi, renderApp, setupShellWorld, signedInAs } from '../support/world';

vi.mock('oidc-client-ts', async (importOriginal) => {
  const actual = await importOriginal<typeof import('oidc-client-ts')>();
  const { FakeUserManager } = await import('../support/fakeOidc');
  return { ...actual, UserManager: FakeUserManager };
});

setupShellWorld();

const OTHER_ID = '7d2f9c3a-1b84-4e5d-8f60-5a9c1e2b3d44';
const BY_ID = `GET /api/v1/applications/${APPLICATION_ID}`;

async function openStatus(id: string, routes: Record<string, () => Response>) {
  signedInAs('provider1');
  installApi({ ...defaultRoutes(), ...routes });
  const rendered = renderApp(`/portal/applications/${id}`);
  return { ...rendered, main: within(await screen.findByRole('main')) };
}

describe('[MAM-64 AC-2][MAM-64 AC-5] the application status page', () => {
  it('[MAM-64 AC-2] a VALIDATED application shows the heading "Application status" and the status Validated', async () => {
    await openStatus(APPLICATION_ID, {
      [LIST_ROUTE]: () => response(200, applicationPage([application({ status: 'VALIDATED' })])),
      [BY_ID]: () => response(200, application({ status: 'VALIDATED' })),
    });

    expect(await screen.findByRole('heading', { level: 1, name: 'Application status' })).toBeInTheDocument();
    expect(await within(await screen.findByRole('region', { name: 'Application' })).findByText('Validated')).toBeInTheDocument();
  });

  it('[MAM-64 AC-5] a REJECTED application shows the status Rejected', async () => {
    const { main } = await openStatus(APPLICATION_ID, {
      [LIST_ROUTE]: () => response(200, applicationPage([application({ status: 'REJECTED' })])),
      [BY_ID]: () => response(200, application({ status: 'REJECTED' })),
    });

    expect(await main.findByText('Rejected')).toBeInTheDocument();
    expect(main.queryByText('Validated')).toBeNull();
  });

  it('[MAM-64 AC-2] the page shows the NPI in full and the submission date as a plain date (1 Mar 2026)', async () => {
    const { main } = await openStatus(APPLICATION_ID, {
      [LIST_ROUTE]: () => response(200, applicationPage([application()])),
      [BY_ID]: () => response(200, application()),
    });

    expect(await main.findByText('1003000111')).toBeInTheDocument();
    expect(main.getByText('1 Mar 2026')).toBeInTheDocument();
  });

  it('[MAM-64 AC-2] the page reads its application by id from GET /api/v1/applications/{id} and shows nothing of the other applications', async () => {
    const { main } = await openStatus(APPLICATION_ID, {
      [LIST_ROUTE]: () =>
        response(
          200,
          applicationPage([application({ applicationId: OTHER_ID, status: 'REJECTED', npi: '1003000555' }), application({ status: 'VALIDATED' })]),
        ),
      [BY_ID]: () => response(200, application({ status: 'VALIDATED' })),
    });

    expect(await within(await screen.findByRole('region', { name: 'Application' })).findByText('Validated')).toBeInTheDocument();
    expect(main.queryByText('Rejected')).toBeNull();
    expect(main.queryByText('1003000555')).toBeNull();
    expect(api.to('GET', '/api/v1/applications')).toHaveLength(0);
    expect(api.to('GET', `/api/v1/applications/${APPLICATION_ID}`).length).toBeGreaterThan(0);
  });

  it('[MAM-64 AC-2] an id that the service does not know (404) shows "Application not found"', async () => {
    const { main } = await openStatus(OTHER_ID, {
      [LIST_ROUTE]: () => response(200, applicationPage([application()])),
      [`GET /api/v1/applications/${OTHER_ID}`]: () => response(404, problem(404, 'Not Found')),
    });

    expect(await main.findByText(/Application not found/i)).toBeInTheDocument();
    expect(main.queryByText('Validated')).toBeNull();
  });

  it('[MAM-64 AC-2] when the application cannot be loaded the page says so and shows no status', async () => {
    const { main } = await openStatus(APPLICATION_ID, {
      [LIST_ROUTE]: () => response(500, problem(500, 'Internal Server Error')),
      [BY_ID]: () => response(500, problem(500, 'Internal Server Error')),
    });

    expect(await main.findByText(/could not be loaded/i)).toBeInTheDocument();
    expect(main.queryByText(/Validated|Rejected/)).toBeNull();
  });
});
