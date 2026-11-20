// UX overhaul, screen 1: the portal home /portal gets a "Your enrolment" summary: where the provider stands (text plus code), the
// next step in plain words and the one "Apply for enrolment" call to action. The h1 stays "My enrolment" (journeys J1 and J2).
import { screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { problem, response } from '../support/fakeApi';
import { application, applicationPage, PROVIDER_NPI, provider } from '../support/fixtures';
import { defaultRoutes, installApi, renderApp, setupShellWorld, signedInAs, text } from '../support/world';

vi.mock('oidc-client-ts', async (importOriginal) => {
  const actual = await importOriginal<typeof import('oidc-client-ts')>();
  const { FakeUserManager } = await import('../support/fakeOidc');
  return { ...actual, UserManager: FakeUserManager };
});

setupShellWorld();

const NO_RECORD = () => response(404, problem(404, 'Not Found'));

async function openHome(items: ReturnType<typeof application>[], providerAnswer: () => Response) {
  signedInAs('provider1');
  installApi({ ...defaultRoutes(items), 'GET /api/v1/applications': () => response(200, applicationPage(items)), [`GET /api/v1/providers/${PROVIDER_NPI}`]: providerAnswer });
  renderApp('/portal');
  await screen.findByRole('heading', { level: 1, name: 'My enrolment' });
  return screen.findByRole('region', { name: 'Your enrolment' });
}

describe('[UX] portal home: the "Your enrolment" summary', () => {
  it('[UX] the page keeps its h1 "My enrolment" and adds a section "Your enrolment" with an h2', async () => {
    await openHome([], NO_RECORD);

    expect(screen.getAllByRole('heading', { level: 1 }).map((heading) => text(heading))).toEqual(['My enrolment']);
    expect(screen.getByRole('heading', { level: 2, name: 'Your enrolment' })).toBeInTheDocument();
  });

  it('[UX] with no application and no record it says so and names the next step', async () => {
    const summary = await openHome([], NO_RECORD);

    await waitFor(() => expect(text(summary)).toContain('You have not applied yet'));
    expect(text(summary)).toContain('Next step: fill in the application form to start your enrolment.');
  });

  it('[UX] a rejected application is reported in words and code, with the next step to correct it', async () => {
    const summary = await openHome([application({ status: 'REJECTED' })], NO_RECORD);

    await waitFor(() => expect(text(summary)).toContain('Your last application was not accepted (REJECTED)'));
    expect(text(summary)).toContain('Next step: correct the errors listed for your application and apply again.');
  });

  it('[UX] an accepted application without a record yet says that the automatic checks come next', async () => {
    const summary = await openHome([application({ status: 'VALIDATED' })], NO_RECORD);

    await waitFor(() => expect(text(summary)).toContain('Your application was accepted (VALIDATED)'));
    expect(text(summary)).toContain('Next step: automatic checks run first, then an analyst decides on your enrolment.');
  });

  it('[UX] the latest application counts, not the first one in the list', async () => {
    const older = application({ applicationId: '11111111-1111-4111-8111-111111111111', status: 'REJECTED', submittedAt: '2026-01-01T10:00:00Z' });
    const newer = application({ applicationId: '22222222-2222-4222-8222-222222222222', status: 'VALIDATED', submittedAt: '2026-03-01T10:00:00Z' });
    const summary = await openHome([older, newer], NO_RECORD);

    await waitFor(() => expect(text(summary)).toContain('(VALIDATED)'));
    expect(text(summary)).not.toContain('(REJECTED)');
  });

  it.each([
    ['P', 'Pending (P)', 'Next step: an analyst reviews the screening results and decides on your enrolment.'],
    ['A', 'Active (A)', 'Your next revalidation is due on 2 Mar 2031.'],
    ['T', 'Terminated (T)', 'Next step: see My notices for the reason.'],
    ['D', 'Denied (D)', 'Next step: see My notices for the reason.'],
    ['S', 'Suspended (S)', 'Next step: see My notices for the reason.'],
  ] as const)('[UX] a provider with status %s reads "Enrolment status: %s" with its next step', async (status, label, step) => {
    const summary = await openHome([application()], () => response(200, provider({ enrollStatus: status, revalidationDueDate: '2031-03-02' })));

    await waitFor(() => expect(text(summary)).toContain(`Enrolment status: ${label}`));
    expect(text(summary)).toContain(step);
  });

  it('[UX] there is exactly one "Apply for enrolment" control in the main area and it is inside the summary', async () => {
    const summary = await openHome([application()], NO_RECORD);

    const main = within(screen.getByRole('main'));
    expect(main.getAllByRole('link', { name: 'Apply for enrolment' })).toHaveLength(1);
    expect(main.queryAllByRole('button', { name: 'Apply for enrolment' })).toHaveLength(0);
    expect(within(summary).getByRole('link', { name: 'Apply for enrolment' })).toHaveAttribute('href', '/portal/apply');
  });

  it('[UX] the summary is not a table, so no table row of the page carries its status words (journey J2 counts rows)', async () => {
    const summary = await openHome([application({ status: 'REJECTED' })], NO_RECORD);
    await waitFor(() => expect(text(summary)).toContain('REJECTED'));

    expect(within(summary).queryAllByRole('row')).toHaveLength(0);
    expect(screen.getAllByRole('row').filter((row) => text(row).includes('Rejected'))).toHaveLength(1);
  });

  it('[UX] the summary comes before the table of applications', async () => {
    await openHome([application()], NO_RECORD);
    await waitFor(() => expect(screen.getAllByRole('table').length).toBeGreaterThan(0));
    const table = screen.getAllByRole('table')[0]!;

    const summary = screen.getByRole('region', { name: 'Your enrolment' });
    expect(summary.compareDocumentPosition(table) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it('[UX] while the data loads the summary says so and claims nothing', async () => {
    signedInAs('provider1');
    installApi({ ...defaultRoutes([]), 'GET /api/v1/applications': () => new Promise<Response>(() => undefined) });
    renderApp('/portal');

    const summary = await screen.findByRole('region', { name: 'Your enrolment' });
    expect(text(summary)).toContain('Loading your enrolment');
    expect(text(summary)).not.toMatch(/not applied|accepted|rejected|Enrolment status/i);
  });

  it('[UX] when the data cannot be loaded the summary says so instead of "You have not applied yet"', async () => {
    const summary = await openHome([], () => response(500, problem(500, 'Internal Server Error')));

    await waitFor(() => expect(text(summary)).toContain('Your enrolment status could not be loaded'));
    expect(text(summary)).not.toContain('You have not applied yet');
  });
});
