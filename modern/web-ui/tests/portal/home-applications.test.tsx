import { screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { application, APPLICATION_ID, PROVIDER_NPI, type Application } from '../support/fixtures';
import {
  api,
  applicationsTable,
  bodyRows,
  cellTexts,
  defaultRoutes,
  findApplicationsTable,
  findApplyControl,
  installApi,
  renderApp,
  signedInAs,
  text,
  setupShellWorld,
} from '../support/world';

vi.mock('oidc-client-ts', async (importOriginal) => {
  const actual = await importOriginal<typeof import('oidc-client-ts')>();
  const { FakeUserManager } = await import('../support/fakeOidc');
  return { ...actual, UserManager: FakeUserManager };
});

setupShellWorld();

function withApplications(items: Application[]): void {
  installApi(defaultRoutes(items));
}

describe('[MAM-62 AC-4] portal home lists my applications and leads to the form', () => {
  it('[MAM-62 AC-4] the page calls GET /api/v1/applications', async () => {
    signedInAs('provider1');
    withApplications([application()]);
    renderApp('/portal');

    await waitFor(() => expect(api.to('GET', '/api/v1/applications')).toHaveLength(1));
  });

  it('[MAM-62 AC-4] the table has the columns Submitted, NPI and Status', async () => {
    signedInAs('provider1');
    withApplications([application()]);
    renderApp('/portal');

    const table = await findApplicationsTable();
    const headers = within(table)
      .getAllByRole('columnheader')
      .map((header) => text(header));
    expect(headers.slice(0, 3)).toEqual(['Submitted', 'NPI', 'Status']);
  });

  it('[MAM-62 AC-4] an application submitted on 2026-03-01 and Validated shows 1 Mar 2026, the NPI in full and Validated', async () => {
    signedInAs('provider1');
    withApplications([application({ submittedAt: '2026-03-01T10:00:00Z', npi: PROVIDER_NPI, status: 'VALIDATED' })]);
    renderApp('/portal');

    const table = await findApplicationsTable();
    const rows = bodyRows(table);
    expect(rows).toHaveLength(1);
    expect(cellTexts(rows[0]!).slice(0, 3)).toEqual(['1 Mar 2026', '1003000111', 'Validated']);
  });

  it.each([
    ['2026-03-01T10:00:00Z', '1 Mar 2026'],
    ['2026-01-09T10:00:00Z', '9 Jan 2026'],
    ['2026-12-31T10:00:00Z', '31 Dec 2026'],
  ])('[MAM-62 AC-4] the submission instant %s is shown as a plain date: %s', async (submittedAt, shown) => {
    signedInAs('provider1');
    withApplications([application({ submittedAt })]);
    renderApp('/portal');

    const table = await findApplicationsTable();
    expect(cellTexts(bodyRows(table)[0]!)[0]).toBe(shown);
  });

  it.each([
    ['SUBMITTED', 'Submitted'],
    ['VALIDATED', 'Validated'],
    ['REJECTED', 'Rejected'],
  ] as const)('[MAM-62 AC-4] status %s is written as the text "%s"', async (status, label) => {
    signedInAs('provider1');
    withApplications([application({ status })]);
    renderApp('/portal');

    const table = await findApplicationsTable();
    expect(cellTexts(bodyRows(table)[0]!)[2]).toBe(label);
  });

  it('[MAM-62 AC-4] the View link of a row leads to /portal/applications/:applicationId', async () => {
    signedInAs('provider1');
    withApplications([application()]);
    renderApp('/portal');

    const table = await findApplicationsTable();
    const view = within(bodyRows(table)[0]!).getByRole('link', { name: /view/i });
    expect(view).toHaveAttribute('href', `/portal/applications/${APPLICATION_ID}`);
  });

  it('[MAM-62 AC-4] two applications give two rows, each with its own View link', async () => {
    const second = '5d2f8a70-3c1e-4b7a-8f0d-9e6a1c4b2d33';
    signedInAs('provider1');
    withApplications([application(), application({ applicationId: second, status: 'REJECTED', submittedAt: '2026-04-02T10:00:00Z' })]);
    renderApp('/portal');

    const table = await findApplicationsTable();
    const hrefs = bodyRows(table).map((row) => within(row).getByRole('link', { name: /view/i }).getAttribute('href'));
    expect(hrefs).toEqual([`/portal/applications/${APPLICATION_ID}`, `/portal/applications/${second}`]);
  });

  it('[MAM-62 AC-4] the button Apply for enrolment leads to /portal/apply', async () => {
    signedInAs('provider1');
    withApplications([application()]);
    const app = renderApp('/portal');

    await app.user.click(await findApplyControl());

    await waitFor(() => expect(app.path()).toBe('/portal/apply'));
  });

  it('[MAM-62 AC-4] with no applications the table is replaced by the text "No applications yet"', async () => {
    signedInAs('provider1');
    withApplications([]);
    renderApp('/portal');

    expect(await screen.findByText('No applications yet')).toBeInTheDocument();
    expect(applicationsTable()).toBeNull();
  });

  it('[MAM-62 AC-4] with no applications the button Apply for enrolment is still there', async () => {
    signedInAs('provider1');
    withApplications([]);
    renderApp('/portal');

    await screen.findByText('No applications yet');
    expect(await findApplyControl()).toBeInTheDocument();
  });
});
