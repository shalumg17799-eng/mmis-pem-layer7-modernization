import { UserManager } from 'oidc-client-ts';
import { config } from '../../app/config';

/**
 * The one OIDC client of the web UI (ADR-007, ADR-008): Keycloak realm pem, public client pem-web, authorization code flow
 * with PKCE (oidc-client-ts adds the S256 code challenge for response_type "code"). The browser never handles a password
 * and holds no client secret. Addresses come from the run-time configuration (public/config.js).
 */
const { keycloakUrl, realm, clientId } = config();

export const SIGN_IN_CALLBACK_PATH = '/auth/callback';
export const SIGNED_OUT_PATH = '/signed-out';

export const userManager = new UserManager({
  authority: `${keycloakUrl}/realms/${realm}`,
  client_id: clientId,
  redirect_uri: `${window.location.origin}${SIGN_IN_CALLBACK_PATH}`,
  post_logout_redirect_uri: `${window.location.origin}${SIGNED_OUT_PATH}`,
  response_type: 'code',
  // The realm pem offers only the scopes openid and pem-claims (a default scope of pem-web that adds the roles, the npi
  // claim and the user name to the tokens). Asking for profile or email makes Keycloak answer invalid_scope.
  scope: 'openid',
  loadUserInfo: false,
  automaticSilentRenew: false,
  monitorSession: false,
});
