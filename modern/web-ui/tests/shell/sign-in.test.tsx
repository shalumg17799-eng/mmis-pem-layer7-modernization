import { screen, waitFor } from '@testing-library/react';
import { OidcClient } from 'oidc-client-ts';
import { describe, expect, it, vi } from 'vitest';
import { GATEWAY, makeUser, PROVIDER_NPI, SCREENS } from '../support/fixtures';
import { api, menuEntries, oidc, renderApp, signedInAs, setupShellWorld } from '../support/world';

vi.mock('oidc-client-ts', async (importOriginal) => {
  const actual = await importOriginal<typeof import('oidc-client-ts')>();
  const { FakeUserManager } = await import('../support/fakeOidc');
  return { ...actual, UserManager: FakeUserManager };
});

setupShellWorld();

describe('[MAM-62 AC-1] sign-in is required and uses the identity service', () => {
  it.each(SCREENS.map((s) => s.path))(
    '[MAM-62 AC-1] a visitor who is not signed in and opens %s is sent to sign in, with no page content and no API call',
    async (path) => {
      renderApp(path);

      await waitFor(() => expect(oidc.signinRedirect).toHaveBeenCalled());
      expect(api.calls).toEqual([]);
      expect(screen.queryByRole('heading', { name: 'Access denied' })).toBeNull();
      expect(menuEntries()).toEqual([]);
    },
  );

  it('[MAM-62 AC-1] the web UI never shows a password field: it hands the visitor to the identity service instead', async () => {
    renderApp('/portal');

    await waitFor(() => expect(oidc.signinRedirect).toHaveBeenCalled());
    expect(document.querySelector('input[type="password"]')).toBeNull();
  });

  it('[MAM-62 AC-1] the sign-in client is the Keycloak realm pem, client pem-web, authorization code flow', async () => {
    renderApp('/portal');
    await waitFor(() => expect(oidc.signinRedirect).toHaveBeenCalled());

    const settings = oidc.settings;
    expect(settings?.authority).toBe('http://localhost:8180/realms/pem');
    expect(settings?.client_id).toBe('pem-web');
    expect(settings?.response_type).toBe('code');
    expect(settings?.scope?.split(' ')).toContain('openid');
    expect(settings?.redirect_uri?.startsWith(`${window.location.origin}/`)).toBe(true);
  });

  it('[MAM-62 AC-1] the client is public: no client secret is configured in the browser', async () => {
    renderApp('/portal');
    await waitFor(() => expect(oidc.signinRedirect).toHaveBeenCalled());

    expect(oidc.settings).toBeDefined();
    expect(oidc.settings).not.toHaveProperty('client_secret');
  });

  it('[MAM-62 AC-1] the authorize request carries an S256 code challenge (PKCE) and asks for an authorization code', async () => {
    renderApp('/portal');
    await waitFor(() => expect(oidc.signinRedirect).toHaveBeenCalled());
    const settings = oidc.settings;
    expect(settings).toBeDefined();

    // The real library builds the authorize request from the settings the web UI configured.
    const authority = 'http://localhost:8180/realms/pem';
    const client = new OidcClient({
      ...settings!,
      metadata: {
        issuer: authority,
        authorization_endpoint: `${authority}/protocol/openid-connect/auth`,
        token_endpoint: `${authority}/protocol/openid-connect/token`,
        end_session_endpoint: `${authority}/protocol/openid-connect/logout`,
      },
    });
    const request = await client.createSigninRequest({ state: {} });
    const authorize = new URL(request.url);

    expect(authorize.origin + authorize.pathname).toBe(`${authority}/protocol/openid-connect/auth`);
    expect(authorize.searchParams.get('client_id')).toBe('pem-web');
    expect(authorize.searchParams.get('response_type')).toBe('code');
    expect(authorize.searchParams.get('code_challenge_method')).toBe('S256');
    expect(authorize.searchParams.get('code_challenge')).toMatch(/^[A-Za-z0-9_-]{43}$/);
  });

  it('[MAM-62 AC-1] after a successful sign-in the visitor lands on the route that was asked for', async () => {
    const first = renderApp('/console/providers');
    await waitFor(() => expect(oidc.signinRedirect).toHaveBeenCalled());
    first.unmount(); // the browser leaves for Keycloak

    // Keycloak sends the browser back to the redirect URI with the authorization code and the state
    const back = renderApp(oidc.returnFromKeycloak(makeUser('analyst1')));

    await waitFor(() => expect(back.path()).toBe('/console/providers'));
    expect(await screen.findByRole('link', { name: 'Provider search' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Access denied' })).toBeNull();
  });

  it('[MAM-62 AC-1] the route asked for survives a deep link with an id in it', async () => {
    const wanted = `/console/providers/${PROVIDER_NPI}`;
    const first = renderApp(wanted);
    await waitFor(() => expect(oidc.signinRedirect).toHaveBeenCalled());
    first.unmount();

    const back = renderApp(oidc.returnFromKeycloak(makeUser('analyst1')));

    await waitFor(() => expect(back.path()).toBe(wanted));
    expect(await screen.findByRole('link', { name: 'Work queues' })).toBeInTheDocument();
  });

  it('[MAM-62 AC-1] every API call carries the access token as a bearer token to the gateway at http://localhost:8080', async () => {
    signedInAs('provider1');
    const token = oidc.user!.access_token;
    renderApp('/portal');

    await waitFor(() => expect(api.calls.length).toBeGreaterThanOrEqual(2));
    for (const call of api.calls) {
      expect(call.url.origin).toBe(GATEWAY);
      expect(call.headers.get('authorization')).toBe(`Bearer ${token}`);
    }
  });

  it('[MAM-62 AC-1] a signed-in visitor opening a route is not sent to sign in again and sees the shell', async () => {
    signedInAs('analyst1');
    renderApp('/console/providers');

    await waitFor(() => expect(menuEntries().map(([name]) => name)).toContain('Provider search'));
    expect(oidc.signinRedirect).not.toHaveBeenCalled();
  });
});
