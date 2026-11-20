import { screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { response, problem } from '../support/fakeApi';
import { application, OTHER_NPI, PROVIDER_NPI, provider, type Provider } from '../support/fixtures';
import {
  api,
  applicationsTable,
  bodyRows,
  cellTexts,
  defaultRoutes,
  fieldValue,
  findApplicationsTable,
  installApi,
  oidc,
  renderApp,
  signedInAs,
  setupShellWorld,
} from '../support/world';

vi.mock('oidc-client-ts', async (importOriginal) => {
  const actual = await importOriginal<typeof import('oidc-client-ts')>();
  const { FakeUserManager } = await import('../support/fakeOidc');
  return { ...actual, UserManager: FakeUserManager };
});

setupShellWorld();

function withProvider(record: Provider): void {
  installApi({ ...defaultRoutes([application()]), [`GET /api/v1/providers/${record.npi}`]: () => response(200, record) });
}

async function openPortalHome(): Promise<void> {
  signedInAs('provider1');
  renderApp('/portal');
  await waitFor(() => expect(fieldValue('NPI')).toBeDefined());
}

describe('[MAM-62 AC-5] portal home summarises my own provider record', () => {
  it('[MAM-62 AC-5] the page calls GET /api/v1/providers/1003000111, the NPI of the token claim', async () => {
    signedInAs('provider1');
    renderApp('/portal');

    await waitFor(() => expect(api.to('GET', `/api/v1/providers/${PROVIDER_NPI}`)).toHaveLength(1));
  });

  it('[MAM-62 AC-5] an NPI typed into the address bar is ignored: the call still uses the NPI of the token', async () => {
    signedInAs('provider1');
    renderApp(`/portal?npi=${OTHER_NPI}`);

    await waitFor(() => expect(api.to('GET', `/api/v1/providers/${PROVIDER_NPI}`)).toHaveLength(1));
    expect(api.calls.filter((call) => call.url.pathname.includes(OTHER_NPI))).toEqual([]);
  });

  it('[MAM-62 AC-5] the summary shows the NPI and the Medicaid ID', async () => {
    withProvider(provider());
    await openPortalHome();

    expect([fieldValue('NPI'), fieldValue('Medicaid ID')]).toEqual(['1003000111', 'TX0000111']);
  });

  it('[MAM-62 AC-5] the summary shows the provider type as Individual (IN) and the taxonomy', async () => {
    withProvider(provider({ providerType: 'IN', taxonomy: '207Q00000X' }));
    await openPortalHome();

    expect([fieldValue('Provider type'), fieldValue('Taxonomy')]).toEqual(['Individual (IN)', '207Q00000X']);
  });

  it('[MAM-62 AC-5] an Active provider with screen flag Pass shows "Active (A)" and "Pass (P)"', async () => {
    withProvider(provider({ enrollStatus: 'A', screenFlag: 'P' }));
    await openPortalHome();

    expect([fieldValue('Enrolment status'), fieldValue('Screening result')]).toEqual(['Active (A)', 'Pass (P)']);
  });

  it.each([
    ['P', 'Pending (P)'],
    ['A', 'Active (A)'],
    ['T', 'Terminated (T)'],
    ['D', 'Denied (D)'],
    ['S', 'Suspended (S)'],
  ] as const)('[MAM-62 AC-5] enrolment status code %s is shown as "%s"', async (code, label) => {
    withProvider(provider({ enrollStatus: code }));
    await openPortalHome();

    expect(fieldValue('Enrolment status')).toBe(label);
  });

  it.each([
    ['U', 'Unscreened (U)'],
    ['P', 'Pass (P)'],
    ['F', 'Fail (F)'],
  ] as const)('[MAM-62 AC-5] screen flag code %s is shown as "%s"', async (code, label) => {
    withProvider(provider({ screenFlag: code }));
    await openPortalHome();

    expect(fieldValue('Screening result')).toBe(label);
  });

  it.each([
    ['IN', 'Individual (IN)'],
    ['GR', 'Group (GR)'],
    ['FA', 'Facility (FA)'],
  ] as const)('[MAM-62 AC-5] provider type code %s is shown as "%s"', async (code, label) => {
    withProvider(provider({ providerType: code }));
    await openPortalHome();

    expect(fieldValue('Provider type')).toBe(label);
  });

  it('[MAM-62 AC-5] the summary has a link to My provider record (/portal/provider)', async () => {
    withProvider(provider());
    await openPortalHome();

    const link = within(screen.getByRole('main')).getByRole('link', { name: /my provider record/i });
    expect(link).toHaveAttribute('href', '/portal/provider');
  });

  it('[MAM-62 AC-5] when the provider does not exist yet (404) the summary says that no provider record exists yet', async () => {
    installApi({
      ...defaultRoutes([application()]),
      [`GET /api/v1/providers/${PROVIDER_NPI}`]: () => response(404, problem(404, 'Provider not found')),
    });
    signedInAs('provider1');
    renderApp('/portal');

    expect(await screen.findByText(/no provider record exists yet/i)).toBeInTheDocument();
    expect(fieldValue('Medicaid ID')).toBeUndefined();
  });

  it('[MAM-62 AC-5] when the provider does not exist yet (404) the applications table is still shown', async () => {
    installApi({
      ...defaultRoutes([application()]),
      [`GET /api/v1/providers/${PROVIDER_NPI}`]: () => response(404, problem(404, 'Provider not found')),
    });
    signedInAs('provider1');
    renderApp('/portal');

    const table = await findApplicationsTable();
    expect(cellTexts(bodyRows(table)[0]!).slice(0, 3)).toEqual(['1 Mar 2026', '1003000111', 'Validated']);
  });

  it('[MAM-62 AC-5] a 404 is not an ended session: the visitor is not sent to sign in again', async () => {
    installApi({
      ...defaultRoutes([application()]),
      [`GET /api/v1/providers/${PROVIDER_NPI}`]: () => response(404, problem(404, 'Provider not found')),
    });
    signedInAs('provider1');
    renderApp('/portal');

    await screen.findByText(/no provider record exists yet/i);
    expect(oidc.signinRedirect).not.toHaveBeenCalled();
    expect(applicationsTable()).not.toBeNull();
  });
});
