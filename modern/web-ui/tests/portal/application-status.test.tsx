// MAM-66 AC-1..AC-5 (component level): /portal/applications/:applicationId shows one application of the signed-in provider:
// status, submission date, the data that was sent and, for a rejected one, every validation error with its code (D-001, D-016).
// The page reads GET /api/v1/applications/{applicationId} (MAM-54): 403 for another provider's application, 404 for an unknown one.
import { screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { problem, response, type Handler } from '../support/fakeApi';
import { application, APPLICATION_ID } from '../support/fixtures';
import { api, defaultRoutes, fieldValue, installApi, oidc, renderApp, setupShellWorld, signedInAs, text } from '../support/world';

vi.mock('oidc-client-ts', async (importOriginal) => {
  const actual = await importOriginal<typeof import('oidc-client-ts')>();
  const { FakeUserManager } = await import('../support/fakeOidc');
  return { ...actual, UserManager: FakeUserManager };
});

setupShellWorld();

const BY_ID = `GET /api/v1/applications/${APPLICATION_ID}`;
const UNKNOWN_ID = '00000000-0000-0000-0000-000000000000';
const FOREIGN_ID = '7d2f9c3a-1b84-4e5d-8f60-5a9c1e2b3d44';
const NOT_LOADED = 'The application could not be loaded. Try again.';

const VALIDATED = application({
  status: 'VALIDATED',
  individual: { lastName: 'RIVERA', firstName: 'MARIA', licenseNo: 'MD12345', licenseState: 'TX' },
});
const E104 = { code: 'E104', field: 'taxonomy', message: 'Taxonomy code is required' } as const;
const E102 = { code: 'E102', field: 'providerType', message: 'The provider type must be IN, GR or FA.' } as const;

async function openStatus(id: string, routes: Record<string, Handler>) {
  signedInAs('provider1');
  installApi({ ...defaultRoutes(), ...routes });
  const rendered = renderApp(`/portal/applications/${id}`);
  await screen.findByRole('heading', { level: 1, name: 'Application status' });
  return { ...rendered, main: within(screen.getByRole('main')) };
}

/** The "Application" summary list (region): the status badge is looked up inside it, because the step indicator names step 2 "Validated" too (Q-104, UI polish). */
async function inApplication() {
  return within(await screen.findByRole('region', { name: 'Application' }));
}

function errorRows(): string[][] {
  const table = screen.queryAllByRole('table').find((candidate) => candidate.querySelector('caption')?.textContent === 'Validation errors');
  return table ? Array.from(table.querySelectorAll('tbody tr')).map((row) => Array.from(row.children).map((cell) => text(cell))) : [];
}

describe('[MAM-66 AC-1] a validated application shows its status and details (journey J1)', () => {
  it('[MAM-66 AC-1] the page calls GET /api/v1/applications/{applicationId}', async () => {
    await openStatus(APPLICATION_ID, { [BY_ID]: () => response(200, VALIDATED) });
    await (await inApplication()).findByText('Validated');

    expect(api.to('GET', `/api/v1/applications/${APPLICATION_ID}`).length).toBeGreaterThan(0);
  });

  it('[MAM-66 AC-1] it shows the application id, Status "Validated", the submitted date, the NPI in full, Medicaid ID, type, taxonomy and the individual details', async () => {
    await openStatus(APPLICATION_ID, { [BY_ID]: () => response(200, VALIDATED) });
    await (await inApplication()).findByText('Validated');

    expect(text(screen.getByText(/^Reference: /))).toBe(`Reference: ${APPLICATION_ID}`);
    expect(fieldValue('Status')).toBe('Validated');
    expect(fieldValue('Submitted')).toBe('1 Mar 2026');
    expect(fieldValue('NPI')).toBe('1003000111');
    expect(fieldValue('Medicaid ID')).toBe('TX0000111');
    expect(fieldValue('Provider type')).toBe('Individual (IN)');
    expect(fieldValue('Taxonomy')).toBe('207Q00000X');
    expect(fieldValue('Last name')).toBe('RIVERA');
    expect(fieldValue('First name')).toBe('MARIA');
    expect(fieldValue('Licence number')).toBe('MD12345');
    expect(fieldValue('Licence state')).toBe('TX');
  });

  it('[MAM-66 AC-1] it says "No validation errors" and shows no error table', async () => {
    const { main } = await openStatus(APPLICATION_ID, { [BY_ID]: () => response(200, VALIDATED) });

    expect(await main.findByText('No validation errors')).toBeInTheDocument();
    expect(errorRows()).toEqual([]);
  });

  it('[MAM-66 AC-1] the word "Validated" appears once in the main landmark (journey J1 selector)', async () => {
    await openStatus(APPLICATION_ID, { [BY_ID]: () => response(200, VALIDATED) });
    await (await inApplication()).findByText('Validated');

    expect((await inApplication()).getAllByText('Validated')).toHaveLength(1);
    expect(within(screen.getByRole('main')).getAllByText('Validated', { ignore: 'script, style, .usa-step-indicator__segment-label' })).toHaveLength(1);
    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1);
  });

  it('[MAM-66 AC-1] a group shows its organisation details instead of the personal ones', async () => {
    const group = application({
      providerType: 'GR',
      individual: undefined,
      organization: { legalName: 'RIO GRANDE FAMILY MEDICINE PLLC', dbaName: 'RIO GRANDE FAMILY CARE', ownerNpi: '1990000111' },
    });
    await openStatus(APPLICATION_ID, { [BY_ID]: () => response(200, group) });
    await (await inApplication()).findByText('Validated');

    expect(fieldValue('Provider type')).toBe('Group (GR)');
    expect(fieldValue('Legal name')).toBe('RIO GRANDE FAMILY MEDICINE PLLC');
    expect(fieldValue('Owner NPI')).toBe('1990000111');
    expect(fieldValue('Last name')).toBeUndefined();
  });
});

describe('[MAM-66 AC-2] a rejected application shows exactly one error row (journey J2)', () => {
  it('[MAM-66 AC-2] Status reads "Rejected" and the table "Validation errors" has the columns Code, Field and Message and one row: E104, taxonomy and the message of the API', async () => {
    const rejected = application({ status: 'REJECTED', taxonomy: '', errors: [E104] });
    const { main } = await openStatus(APPLICATION_ID, { [BY_ID]: () => response(200, rejected) });

    expect(await main.findByText('Rejected')).toBeInTheDocument();
    const table = screen.getAllByRole('table').find((candidate) => candidate.querySelector('caption')?.textContent === 'Validation errors')!;
    expect(within(table).getAllByRole('columnheader').map((header) => text(header))).toEqual(['Code', 'Field', 'Message']);
    expect(errorRows()).toEqual([['E104', 'taxonomy', 'Taxonomy code is required']]);
    expect(main.queryByText('No validation errors')).toBeNull();
    expect(main.queryByText('Validated')).toBeNull();
  });
});

describe('[MAM-66 AC-3] several failed checks give several rows, each once', () => {
  it('[MAM-66 AC-3] two errors, E102 on providerType and E104 on taxonomy, are listed once each in the order returned by the API', async () => {
    const rejected = application({ status: 'REJECTED', providerType: 'XX', taxonomy: '', errors: [E102, E104] });
    const { main } = await openStatus(APPLICATION_ID, { [BY_ID]: () => response(200, rejected) });
    await main.findByText('Rejected');

    expect(errorRows()).toEqual([
      ['E102', 'providerType', 'The provider type must be IN, GR or FA.'],
      ['E104', 'taxonomy', 'Taxonomy code is required'],
    ]);
  });

  it('[MAM-66 AC-3] a provider type that is not IN, GR or FA is shown as sent, not as a legacy meaning', async () => {
    const rejected = application({ status: 'REJECTED', providerType: 'XX', errors: [E102] });
    const { main } = await openStatus(APPLICATION_ID, { [BY_ID]: () => response(200, rejected) });
    await main.findByText('Rejected');

    expect(fieldValue('Provider type')).toBe('XX');
  });
});

describe('[MAM-66 AC-4] someone else\'s or an unknown application is never shown', () => {
  it('[MAM-66 AC-4] a 403 shows an access-denied message with none of the application\'s data', async () => {
    const { main } = await openStatus(FOREIGN_ID, { [`GET /api/v1/applications/${FOREIGN_ID}`]: () => response(403, problem(403, 'Forbidden')) });

    expect(await main.findByText(/Access denied/i)).toBeInTheDocument();
    expect(screen.queryByRole('table')).toBeNull();
    expect(text(screen.getByRole('main'))).not.toMatch(/1003000|E\d{3}|Validated|Rejected/);
  });

  it('[MAM-66 AC-4] a 404 shows "Application not found" with a link to My enrolment', async () => {
    const { main } = await openStatus(UNKNOWN_ID, { [`GET /api/v1/applications/${UNKNOWN_ID}`]: () => response(404, problem(404, 'Not Found')) });

    expect(await main.findByText('Application not found')).toBeInTheDocument();
    expect(main.getByRole('link', { name: 'My enrolment' })).toHaveAttribute('href', '/portal');
    expect(screen.queryByRole('table')).toBeNull();
    expect(screen.queryByRole('region', { name: 'Application' })).toBeNull();
  });
});

describe('[MAM-66 AC-5] loading failures are shown, not an empty page', () => {
  it('[MAM-66 AC-5] a 401 says to sign in again, offers a "Sign in again" button and shows no status', async () => {
    const { main, user } = await openStatus(APPLICATION_ID, { [BY_ID]: () => response(401, problem(401, 'Not authenticated')) });

    expect(await main.findByText(/sign in again/i, { selector: 'p, .usa-alert__text' })).toBeInTheDocument();
    expect(oidc.signinRedirect).not.toHaveBeenCalled();
    expect(main.queryByText(/Validated|Rejected/)).toBeNull();
    await user.click(main.getByRole('button', { name: 'Sign in again' }));
    await waitFor(() => expect(oidc.signinRedirect).toHaveBeenCalled());
  });

  it.each([
    ['500', () => response(500, problem(500, 'Internal Server Error'))],
    ['503', () => response(503, problem(503, 'Service Unavailable'))],
    [
      'network failure',
      () => {
        throw new TypeError('Failed to fetch');
      },
    ],
  ] as Array<[string, Handler]>)('[MAM-66 AC-5] %s shows "The application could not be loaded. Try again." and no status', async (_label, failure) => {
    const { main } = await openStatus(APPLICATION_ID, { [BY_ID]: failure });

    expect(await main.findByText(NOT_LOADED)).toBeInTheDocument();
    expect(main.queryByText(/Validated|Rejected|Submitted/)).toBeNull();
    expect(screen.queryByRole('table')).toBeNull();
  });

  it('[MAM-66 AC-5] Retry asks again and shows the status when the service has recovered', async () => {
    let up = false;
    const { main, user } = await openStatus(APPLICATION_ID, { [BY_ID]: () => (up ? response(200, VALIDATED) : response(503, problem(503, 'x'))) });
    await main.findByText(NOT_LOADED);
    up = true;

    await user.click(main.getByRole('button', { name: 'Retry' }));

    expect(await (await inApplication()).findByText('Validated')).toBeInTheDocument();
    expect(main.queryByText(NOT_LOADED)).toBeNull();
  });
});
