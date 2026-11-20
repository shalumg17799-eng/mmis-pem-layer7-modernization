import type { User } from 'oidc-client-ts';

/** Realm roles of the Keycloak realm pem that the web UI knows (D-023). SYSTEM is for services and is never a UI role. */
export const ROLES = ['PROVIDER', 'ANALYST', 'SUPERVISOR', 'ADMIN'] as const;
export type Role = (typeof ROLES)[number];

export const ROLE_LABEL: Record<Role, string> = {
  PROVIDER: 'Provider',
  ANALYST: 'Analyst',
  SUPERVISOR: 'Supervisor',
  ADMIN: 'Administrator',
};

/** What the screens need to know about the signed-in person, taken from the token claims. */
export interface SessionUser {
  subject: string;
  displayName: string;
  roles: Role[];
  /** The provider's NPI claim (provider accounts only). It is the only source of the NPI the portal uses. */
  npi?: string;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

/** The payload of a JWT, read for display and navigation only: the gateway and the services verify the token. */
function jwtPayload(token: string): Record<string, unknown> {
  const part = token.split('.')[1];
  if (!part) return {};
  try {
    const base64 = part.replace(/-/g, '+').replace(/_/g, '/');
    const binary = atob(base64.padEnd(Math.ceil(base64.length / 4) * 4, '='));
    const json = new TextDecoder().decode(Uint8Array.from(binary, (char) => char.charCodeAt(0)));
    const parsed: unknown = JSON.parse(json);
    return isRecord(parsed) ? parsed : {};
  } catch {
    return {};
  }
}

function isRole(value: unknown): value is Role {
  return typeof value === 'string' && (ROLES as readonly string[]).includes(value);
}

/** Session of a signed-in user: the roles (realm_access.roles) and the npi claim come from the access token the gateway sees. */
export function toSessionUser(user: User): SessionUser {
  const claims: Record<string, unknown> = { ...user.profile, ...jwtPayload(user.access_token) };
  const realmAccess = claims['realm_access'];
  const rawRoles = isRecord(realmAccess) && Array.isArray(realmAccess['roles']) ? (realmAccess['roles'] as unknown[]) : [];
  const roles = ROLES.filter((role) => rawRoles.some((raw) => isRole(raw) && raw === role));
  const given = typeof claims['given_name'] === 'string' ? claims['given_name'] : '';
  const family = typeof claims['family_name'] === 'string' ? claims['family_name'] : '';
  const username = typeof claims['preferred_username'] === 'string' ? claims['preferred_username'] : '';
  const npi = typeof claims['npi'] === 'string' && claims['npi'] !== '' ? claims['npi'] : undefined;
  return {
    subject: typeof claims['sub'] === 'string' ? claims['sub'] : username,
    displayName: `${given} ${family}`.trim() || username || 'Signed-in user',
    roles,
    ...(npi ? { npi } : {}),
  };
}

export function hasAnyRole(user: SessionUser, allowed: readonly Role[]): boolean {
  return user.roles.some((role) => allowed.includes(role));
}
