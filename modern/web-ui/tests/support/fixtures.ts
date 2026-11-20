// Synthetic data only (the NPIs and users of the acceptance criteria, modern/infra/keycloak/realm-pem.json).
import type { components as EnrollmentSchemas } from '../../src/shared/api/generated/enrollment-service';
import type { components as ProviderSchemas } from '../../src/shared/api/generated/provider-service';
import type { FakeUser } from './fakeOidc';

export type Application = EnrollmentSchemas['schemas']['Application'];
export type ApplicationPage = EnrollmentSchemas['schemas']['ApplicationPage'];
export type Provider = ProviderSchemas['schemas']['Provider'];

export type UserName = 'provider1' | 'analyst1' | 'supervisor1' | 'admin1';
export type Role = 'PROVIDER' | 'ANALYST' | 'SUPERVISOR' | 'ADMIN';

export const PROVIDER_NPI = '1003000111';
export const OTHER_NPI = '1990000111';
export const APPLICATION_ID = '0b9c5d1e-6a4f-4c1a-9a43-3c2d6f0a1b11';
export const GATEWAY = 'http://localhost:8080';

const ACCOUNTS: Record<UserName, { role: Role; givenName: string; familyName: string; npi?: string }> = {
  provider1: { role: 'PROVIDER', givenName: 'Pat', familyName: 'Provider', npi: PROVIDER_NPI },
  analyst1: { role: 'ANALYST', givenName: 'Alex', familyName: 'Analyst' },
  supervisor1: { role: 'SUPERVISOR', givenName: 'Sam', familyName: 'Supervisor' },
  admin1: { role: 'ADMIN', givenName: 'Ada', familyName: 'Admin' },
};

export const USER_NAMES = Object.keys(ACCOUNTS) as UserName[];

function jwt(claims: Record<string, unknown>): string {
  const part = (value: unknown) => Buffer.from(JSON.stringify(value)).toString('base64url');
  return `${part({ alg: 'RS256', typ: 'JWT' })}.${part(claims)}.dGVzdC1zaWduYXR1cmU`;
}

/** A session as oidc-client-ts holds it after a Keycloak sign-in: roles in realm_access.roles, the npi claim for a provider. */
export function makeUser(name: UserName, username: string = name): FakeUser {
  const account = ACCOUNTS[name];
  const now = Math.floor(Date.now() / 1000);
  const claims: Record<string, unknown> = {
    iss: 'http://localhost:8180/realms/pem',
    sub: `sub-${name}`,
    azp: 'pem-web',
    preferred_username: username,
    given_name: account.givenName,
    family_name: account.familyName,
    email: `${name}@pem.local`,
    realm_access: { roles: [account.role] },
    ...(account.npi ? { npi: account.npi } : {}),
    iat: now,
    exp: now + 900,
  };
  return {
    access_token: jwt({ ...claims, typ: 'Bearer', jti: `access-${name}` }),
    id_token: jwt({ ...claims, typ: 'ID', jti: `id-${name}` }),
    token_type: 'Bearer',
    scope: 'openid profile email',
    session_state: `session-${name}`,
    expires_at: now + 900,
    expires_in: 900,
    expired: false,
    scopes: ['openid', 'profile', 'email'],
    profile: claims,
  };
}

export function application(overrides: Partial<Application> = {}): Application {
  return {
    applicationId: APPLICATION_ID,
    status: 'VALIDATED',
    npi: PROVIDER_NPI,
    medicaidId: 'TX0000111',
    providerType: 'IN',
    taxonomy: '207Q00000X',
    source: 'PORTAL',
    submittedAt: '2026-03-01T10:00:00Z',
    ...overrides,
  };
}

export function applicationPage(items: Application[]): ApplicationPage {
  return { items, page: 0, size: 20, total: items.length };
}

export function provider(overrides: Partial<Provider> = {}): Provider {
  return {
    npi: PROVIDER_NPI,
    medicaidId: 'TX0000111',
    providerType: 'IN',
    taxonomy: '207Q00000X',
    riskTier: 'L',
    enrollStatus: 'A',
    screenFlag: 'P',
    effectiveDate: '2026-03-02',
    revalidationDueDate: '2031-03-02',
    version: 1,
    individual: { lastName: 'Provider', firstName: 'Pat', licenseNo: 'TX123456', licenseState: 'TX' },
    ...overrides,
  };
}

// Menu per role (AC-2) and the screen inventory (modern/docs/ux/screen-inventory.md, roles column) for AC-3.
export const MENU: Record<UserName, Array<[string, string]>> = {
  provider1: [
    ['My enrolment', '/portal'],
    ['Apply for enrolment', '/portal/apply'],
    ['My provider record', '/portal/provider'],
    ['My notices', '/portal/notices'],
  ],
  analyst1: [
    ['Provider search', '/console/providers'],
    ['Work queues', '/console/queues'],
  ],
  supervisor1: [
    ['Provider search', '/console/providers'],
    ['Work queues', '/console/queues'],
  ],
  admin1: [
    ['Provider search', '/console/providers'],
    ['Risk-tier rules', '/console/admin/risk-rules'],
    ['Batch jobs', '/console/admin/jobs'],
  ],
};

export const SCREENS: Array<{ path: string; roles: Role[] }> = [
  { path: '/portal', roles: ['PROVIDER'] },
  { path: '/portal/apply', roles: ['PROVIDER'] },
  { path: `/portal/applications/${APPLICATION_ID}`, roles: ['PROVIDER'] },
  { path: '/portal/provider', roles: ['PROVIDER'] },
  { path: '/portal/notices', roles: ['PROVIDER'] },
  { path: '/console/providers', roles: ['ANALYST', 'SUPERVISOR', 'ADMIN'] },
  { path: `/console/providers/${PROVIDER_NPI}`, roles: ['ANALYST', 'SUPERVISOR', 'ADMIN'] },
  { path: '/console/queues', roles: ['ANALYST', 'SUPERVISOR'] },
  { path: `/console/providers/${PROVIDER_NPI}/approval`, roles: ['ANALYST', 'SUPERVISOR'] },
  { path: `/console/providers/${PROVIDER_NPI}/sanctions/new`, roles: ['SUPERVISOR'] },
  { path: '/console/admin/risk-rules', roles: ['ADMIN'] },
  { path: '/console/admin/jobs', roles: ['ADMIN'] },
];

export function roleOf(name: UserName): Role {
  return ACCOUNTS[name].role;
}
