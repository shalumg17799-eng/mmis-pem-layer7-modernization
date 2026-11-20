// The auth boundary of the web UI is the oidc-client-ts library. Every MAM-62 component test replaces its UserManager with
// FakeUserManager (see the vi.mock call at the top of each test file) and drives it through the `oidc` world below:
//   - oidc.user            the session the browser holds (what getUser() returns)
//   - oidc.issueOnSignIn   the session the identity server issues when the visitor signs in
//   - the vi.fn spies      signinRedirect, signoutRedirect and removeUser, so a test can assert that the UI used them
// The fake keeps the library's contract: signinRedirect stores the caller's state, the callback hands it back,
// signoutRedirect ends the session, a callback with an unknown state is an error.
import { vi } from 'vitest';
import type { UserManagerSettings } from 'oidc-client-ts';

export interface FakeUser {
  access_token: string;
  id_token: string;
  token_type: string;
  scope: string;
  session_state: string;
  expires_at: number;
  expires_in: number;
  expired: boolean;
  scopes: string[];
  profile: Record<string, unknown>;
  state?: unknown;
  url_state?: string;
}

export interface RedirectArgs {
  state?: unknown;
  url_state?: string;
  redirect_uri?: string;
  post_logout_redirect_uri?: string;
  [key: string]: unknown;
}

interface PendingRequest {
  state?: unknown;
  url_state?: string;
}

const listeners = new Map<string, Set<(...args: unknown[]) => void>>();

function emit(name: string, ...args: unknown[]): void {
  listeners.get(name)?.forEach((callback) => callback(...args));
}

// events.addUserLoaded / removeUserLoaded / addAccessTokenExpired ... : any add<Name> / remove<Name> of the real library.
const events = new Proxy({} as Record<string, unknown>, {
  get(_target, property) {
    const match = /^(add|remove)(\w+)$/.exec(String(property));
    if (!match) return undefined;
    const operation = match[1];
    const name = match[2] ?? '';
    return (callback: (...args: unknown[]) => void) => {
      const set = listeners.get(name) ?? new Set();
      listeners.set(name, set);
      if (operation === 'add') set.add(callback);
      else set.delete(callback);
    };
  },
});

let counter = 0;

export const oidc = {
  /** The session the browser holds; null = nobody is signed in. */
  user: null as FakeUser | null,
  /** The session the identity server issues when the visitor completes the sign-in. */
  issueOnSignIn: null as FakeUser | null,
  /** Settings of the most recently created UserManager (kept across tests: a module-level manager is created once). */
  settings: undefined as UserManagerSettings | undefined,
  lastSigninArgs: undefined as RedirectArgs | undefined,
  lastSignoutArgs: undefined as RedirectArgs | undefined,
  pendingSignin: new Map<string, PendingRequest>(),
  pendingSignout: new Map<string, PendingRequest>(),

  signinRedirect: vi.fn(async (args?: RedirectArgs): Promise<void> => {
    oidc.lastSigninArgs = args;
    oidc.pendingSignin.set(`signin-state-${++counter}`, { state: args?.state, url_state: args?.url_state });
  }),
  signoutRedirect: vi.fn(async (args?: RedirectArgs): Promise<void> => {
    oidc.lastSignoutArgs = args;
    if (args?.state !== undefined || args?.url_state !== undefined) {
      oidc.pendingSignout.set(`signout-state-${++counter}`, { state: args.state, url_state: args.url_state });
    }
    oidc.user = null; // the real library removes the stored user when it ends the session
    emit('UserUnloaded');
  }),
  removeUser: vi.fn(async (): Promise<void> => {
    oidc.user = null;
    emit('UserUnloaded');
  }),

  /** The redirect URI the app registered (settings or the sign-in call), as path + query to load in the browser. */
  returnFromKeycloak(user: FakeUser): string {
    const entry = [...oidc.pendingSignin.entries()].at(-1);
    if (!entry) throw new Error('the web UI never asked the identity server for a sign-in (signinRedirect was not called)');
    oidc.issueOnSignIn = user;
    const redirectUri = oidc.lastSigninArgs?.redirect_uri ?? oidc.settings?.redirect_uri;
    if (!redirectUri) throw new Error('no redirect_uri configured for the sign-in');
    return `${new URL(redirectUri, window.location.origin).pathname}?code=test-code&state=${entry[0]}`;
  },
  /** The post-logout redirect URI of the last sign-out, as an absolute URL ('' when none was configured). */
  postLogoutRedirectUri(): string {
    return oidc.lastSignoutArgs?.post_logout_redirect_uri ?? oidc.settings?.post_logout_redirect_uri ?? '';
  },
  /** Where Keycloak sends the browser after it ended the session, as path + query to load in the browser. */
  returnFromLogout(): string {
    const uri = oidc.postLogoutRedirectUri();
    if (!uri) throw new Error('no post_logout_redirect_uri configured: Keycloak would stay on its own page');
    const url = new URL(uri, window.location.origin);
    const entry = [...oidc.pendingSignout.entries()].at(-1);
    return `${url.pathname}${entry ? `?state=${entry[0]}` : ''}`;
  },
};

export function resetOidc(): void {
  oidc.user = null;
  oidc.issueOnSignIn = null;
  oidc.lastSigninArgs = undefined;
  oidc.lastSignoutArgs = undefined;
  oidc.pendingSignin.clear();
  oidc.pendingSignout.clear();
  oidc.signinRedirect.mockClear();
  oidc.signoutRedirect.mockClear();
  oidc.removeUser.mockClear();
}

function callbackParams(url?: string): URLSearchParams {
  return new URL(url ?? window.location.href, window.location.origin).searchParams;
}

export class FakeUserManager {
  readonly settings: UserManagerSettings;
  readonly events = events;

  constructor(settings: UserManagerSettings) {
    this.settings = settings;
    oidc.settings = settings;
  }

  getUser = (): Promise<FakeUser | null> => Promise.resolve(oidc.user);
  removeUser = (): Promise<void> => oidc.removeUser();
  storeUser = (user: FakeUser | null): Promise<void> => {
    oidc.user = user;
    return Promise.resolve();
  };
  signinRedirect = (args?: RedirectArgs): Promise<void> => oidc.signinRedirect(args);
  signinSilent = (): Promise<FakeUser> => Promise.reject(new Error('login_required'));
  signoutRedirect = (args?: RedirectArgs): Promise<void> => oidc.signoutRedirect(args);
  startSilentRenew = (): void => undefined;
  stopSilentRenew = (): void => undefined;
  clearStaleState = (): Promise<void> => Promise.resolve();

  signinRedirectCallback = async (url?: string): Promise<FakeUser> => {
    const params = callbackParams(url);
    const state = params.get('state');
    if (!params.get('code') || !state) throw new Error('No code or state in the sign-in response');
    const request = oidc.pendingSignin.get(state);
    if (!request) throw new Error('No matching state found in storage');
    oidc.pendingSignin.delete(state);
    if (!oidc.issueOnSignIn) throw new Error('the identity server issued no session');
    const user: FakeUser = { ...oidc.issueOnSignIn, state: request.state, url_state: request.url_state };
    oidc.user = user;
    emit('UserLoaded', user);
    return user;
  };
  signinCallback = (url?: string): Promise<FakeUser> => this.signinRedirectCallback(url);

  signoutRedirectCallback = async (url?: string): Promise<{ state?: unknown; url_state?: string }> => {
    const state = callbackParams(url).get('state');
    if (!state) return {};
    const request = oidc.pendingSignout.get(state);
    if (!request) throw new Error('No matching state found in storage');
    oidc.pendingSignout.delete(state);
    return { state: request.state, url_state: request.url_state };
  };
  signoutCallback = (url?: string): Promise<{ state?: unknown; url_state?: string }> => this.signoutRedirectCallback(url);
}
