import { screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { deferred, problem, response } from '../support/fakeApi';
import { application, PROVIDER_NPI, provider } from '../support/fixtures';
import {
  api,
  applicationsTable,
  defaultRoutes,
  fieldValue,
  installApi,
  menuEntries,
  oidc,
  renderApp,
  signedInAs,
  signOutControl,
  setupShellWorld,
} from '../support/world';

vi.mock('oidc-client-ts', async (importOriginal) => {
  const actual = await importOriginal<typeof import('oidc-client-ts')>();
  const { FakeUserManager } = await import('../support/fakeOidc');
  return { ...actual, UserManager: FakeUserManager };
});

setupShellWorld();

async function openHomeAndSignOut() {
  signedInAs('provider1');
  const app = renderApp('/portal');
  await waitFor(() => expect(menuEntries().length).toBeGreaterThan(0));
  await app.user.click(signOutControl());
  await waitFor(() => expect(oidc.signoutRedirect).toHaveBeenCalled());
  return app;
}

describe('[MAM-62 AC-6] sign-out and ended sessions', () => {
  describe('Sign out', () => {
    it('[MAM-62 AC-6] a signed-in provider finds a Sign out control', async () => {
      signedInAs('provider1');
      renderApp('/portal');

      await waitFor(() => expect(signOutControl()).toBeInTheDocument());
    });

    it('[MAM-62 AC-6] choosing Sign out ends the Keycloak session (one RP-initiated logout)', async () => {
      await openHomeAndSignOut();

      expect(oidc.signoutRedirect).toHaveBeenCalledTimes(1);
    });

    it('[MAM-62 AC-6] the logout asks Keycloak to return the browser to this web UI (post-logout redirect URI on the app origin)', async () => {
      await openHomeAndSignOut();

      expect(oidc.postLogoutRedirectUri().startsWith(`${window.location.origin}/`)).toBe(true);
    });

    it('[MAM-62 AC-6] after Keycloak returns the browser the signed-out page is shown (one h1 "signed out"), not a sign-in redirect', async () => {
      const first = await openHomeAndSignOut();
      first.unmount();
      oidc.signinRedirect.mockClear();

      renderApp(oidc.returnFromLogout());

      expect(await screen.findByRole('heading', { level: 1, name: /signed out/i })).toBeInTheDocument();
      expect(oidc.signinRedirect).not.toHaveBeenCalled();
    });

    it('[MAM-62 AC-6] the signed-out page shows no menu entry of the portal or the console and makes no API call', async () => {
      const first = await openHomeAndSignOut();
      first.unmount();
      installApi(defaultRoutes([application()]));

      renderApp(oidc.returnFromLogout());

      await screen.findByRole('heading', { level: 1, name: /signed out/i });
      expect(menuEntries().filter(([, href]) => href.startsWith('/portal') || href.startsWith('/console'))).toEqual([]);
      expect(api.calls).toEqual([]);
    });

    it('[MAM-62 AC-6] after signing out, opening /portal asks for sign-in again', async () => {
      const first = await openHomeAndSignOut();
      first.unmount();
      oidc.signinRedirect.mockClear();

      renderApp('/portal');

      await waitFor(() => expect(oidc.signinRedirect).toHaveBeenCalled());
      expect(screen.queryByText(/TX0000111/)).toBeNull();
    });
  });

  describe('401 from the API', () => {
    it.each([
      ['GET /api/v1/applications'],
      [`GET /api/v1/providers/${PROVIDER_NPI}`],
    ])('[MAM-62 AC-6] when %s answers 401 the user is sent to sign in again', async (endpoint) => {
      installApi({
        ...defaultRoutes([application()]),
        [endpoint]: () => response(401, problem(401, 'Not authenticated')),
      });
      signedInAs('provider1');
      renderApp('/portal');

      await waitFor(() => expect(oidc.signinRedirect).toHaveBeenCalled());
    });

    it('[MAM-62 AC-6] the token the API rejected is dropped from the session so that signing in again cannot reuse it', async () => {
      installApi({
        ...defaultRoutes([application()]),
        'GET /api/v1/applications': () => response(401, problem(401, 'Not authenticated')),
      });
      signedInAs('provider1');
      renderApp('/portal');

      await waitFor(() => expect(oidc.signinRedirect).toHaveBeenCalled());
      expect(oidc.user).toBeNull();
    });

    it('[MAM-62 AC-6] after a 401 no data of the earlier page stays on the screen (no half-filled page)', async () => {
      const expired = deferred<Response>();
      installApi({
        ...defaultRoutes([application()]),
        'GET /api/v1/applications': () => expired.promise,
        [`GET /api/v1/providers/${PROVIDER_NPI}`]: () => response(200, provider()),
      });
      signedInAs('provider1');
      renderApp('/portal');
      // the provider summary arrives first and is on the screen ...
      await waitFor(() => expect(fieldValue('Medicaid ID')).toBe('TX0000111'));

      // ... then the token turns out to have expired
      expired.resolve(response(401, problem(401, 'Not authenticated')));

      await waitFor(() => expect(oidc.signinRedirect).toHaveBeenCalled());
      expect(fieldValue('Medicaid ID')).toBeUndefined();
      expect(applicationsTable()).toBeNull();
    });

    it('[MAM-62 AC-6] a 401 is not shown as an empty page or an empty list: "No applications yet" does not appear', async () => {
      installApi({
        ...defaultRoutes([application()]),
        'GET /api/v1/applications': () => response(401, problem(401, 'Not authenticated')),
      });
      signedInAs('provider1');
      renderApp('/portal');

      await waitFor(() => expect(oidc.signinRedirect).toHaveBeenCalled());
      expect(screen.queryByText('No applications yet')).toBeNull();
    });
  });
});
