import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { SIGN_IN_CALLBACK_PATH, SIGNED_OUT_PATH, userManager } from './userManager';
import { toSessionUser, type SessionUser } from './claims';

/**
 * Where the browser is in the sign-in life cycle.
 *  loading           the stored session is being read
 *  anonymous         nobody is signed in
 *  authenticated     a signed-in user
 *  reauthenticating  the API refused the token (401): the session ended and the user is sent to sign in again
 *  signing-out       the Keycloak session is being ended
 */
export type AuthState =
  | { status: 'loading' }
  | { status: 'anonymous' }
  | { status: 'authenticated'; user: SessionUser }
  | { status: 'reauthenticating' }
  | { status: 'signing-out' };

interface AuthContextValue {
  state: AuthState;
  /** Send the browser to the Keycloak sign-in page; after the sign-in it comes back to `returnTo`. */
  signIn: (returnTo: string) => Promise<void>;
  /** End the Keycloak session; Keycloak returns the browser to the signed-out page. */
  signOut: () => Promise<void>;
  /** Handle the redirect back from Keycloak (authorization code + state); resolves with the route that was asked for. */
  completeSignIn: () => Promise<string>;
  /** The API answered 401: drop the stored session and show the sign-in again (D-024). */
  sessionEnded: () => Promise<void>;
  /** The access token for the gateway, or null when there is no valid session. */
  accessToken: () => Promise<string | null>;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

/** Only a route inside this app may be the target after the sign-in (no open redirect), and never the auth pages themselves. */
export function safeReturnTo(value: unknown): string {
  if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//') || value.startsWith('/\\')) return '/';
  if (value.startsWith(SIGN_IN_CALLBACK_PATH) || value.startsWith(SIGNED_OUT_PATH)) return '/';
  return value;
}

function returnToOf(state: unknown): string {
  if (typeof state === 'object' && state !== null && 'returnTo' in state) return safeReturnTo(state.returnTo);
  return '/';
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({ status: 'loading' });
  const sessionEndHandled = useRef(false);

  useEffect(() => {
    let active = true;
    const settle = (next: AuthState) => {
      // a sign-in that completed in the meantime (the redirect route) wins over the stored session read here
      if (active) setState((current) => (current.status === 'loading' ? next : current));
    };
    userManager.getUser().then(
      (user) => settle(user && !user.expired ? { status: 'authenticated', user: toSessionUser(user) } : { status: 'anonymous' }),
      () => settle({ status: 'anonymous' }),
    );
    return () => {
      active = false;
    };
  }, []);

  const signIn = useCallback((returnTo: string) => userManager.signinRedirect({ state: { returnTo: safeReturnTo(returnTo) } }), []);

  const signOut = useCallback(async () => {
    setState({ status: 'signing-out' });
    try {
      await userManager.signoutRedirect();
    } catch {
      await userManager.removeUser();
      setState({ status: 'anonymous' });
    }
  }, []);

  const completeSignIn = useCallback(async () => {
    const user = await userManager.signinRedirectCallback();
    sessionEndHandled.current = false;
    setState({ status: 'authenticated', user: toSessionUser(user) });
    return returnToOf(user.state);
  }, []);

  const sessionEnded = useCallback(async () => {
    if (sessionEndHandled.current) return; // several calls may be refused at the same time
    sessionEndHandled.current = true;
    try {
      await userManager.removeUser(); // the rejected token must not be reused
    } finally {
      setState({ status: 'reauthenticating' });
    }
  }, []);

  const accessToken = useCallback(async () => {
    const user = await userManager.getUser();
    return user && !user.expired ? user.access_token : null;
  }, []);

  const value = useMemo(
    () => ({ state, signIn, signOut, completeSignIn, sessionEnded, accessToken }),
    [state, signIn, signOut, completeSignIn, sessionEnded, accessToken],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used inside AuthProvider');
  return context;
}

/** The signed-in user. Screens are only rendered behind RequireRole, so this is never called without a session. */
export function useSession(): SessionUser {
  const { state } = useAuth();
  if (state.status !== 'authenticated') throw new Error('this screen needs a signed-in user');
  return state.user;
}
