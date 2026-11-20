import type { ReactNode } from 'react';
import { useLocation } from 'react-router-dom';
import { hasAnyRole, type Role } from '../shared/auth/claims';
import { useAuth } from '../shared/auth/AuthProvider';
import { AccessDenied } from './pages/AccessDenied';
import { CheckingSession, SigningIn, SigningOut } from './pages/SignInPages';

/**
 * The auth guard of every /portal and /console route (D-023).
 * Not signed in: the visitor is sent to Keycloak and nothing of the page is rendered or fetched.
 * Signed in without a role of the screen: "Access denied" (403); the screen itself is never rendered, so it fetches nothing.
 */
export function RequireRole({ roles, children }: { roles: readonly Role[]; children: ReactNode }) {
  const { state } = useAuth();
  const location = useLocation();
  switch (state.status) {
    case 'loading':
      return <CheckingSession />;
    case 'anonymous':
    case 'reauthenticating':
      return <SigningIn returnTo={`${location.pathname}${location.search}${location.hash}`} />;
    case 'signing-out':
      return <SigningOut />;
    case 'authenticated':
      return hasAnyRole(state.user, roles) ? <>{children}</> : <AccessDenied user={state.user} />;
  }
}
