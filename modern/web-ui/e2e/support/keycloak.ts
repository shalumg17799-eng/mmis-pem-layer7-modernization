// Real Keycloak sign-in for the e2e journeys (realm pem, public client pem-web). The passwords of the test users are read at
// run time from the environment (KC_<ROLE>_PASSWORD) or from modern/infra/.env.local; they are never written to a file,
// a log or an assertion message.
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { expect, type Page } from '@playwright/test';

export type PemUser = 'provider1' | 'analyst1' | 'supervisor1' | 'admin1';

const PASSWORD_VARIABLE: Record<PemUser, string> = {
  provider1: 'KC_PROVIDER_PASSWORD',
  analyst1: 'KC_ANALYST_PASSWORD',
  supervisor1: 'KC_SUPERVISOR_PASSWORD',
  admin1: 'KC_ADMIN_PASSWORD',
};

export const KEYCLOAK_AUTHORIZE = /\/realms\/pem\/protocol\/openid-connect\/auth/;

function passwordOf(user: PemUser): string {
  const variable = PASSWORD_VARIABLE[user];
  const fromEnvironment = process.env[variable];
  if (fromEnvironment) return fromEnvironment;
  const file = fileURLToPath(new URL('../../../infra/.env.local', import.meta.url));
  const line = readFileSync(file, 'utf8')
    .split(/\r?\n/)
    .find((candidate) => candidate.startsWith(`${variable}=`));
  const value = line?.slice(variable.length + 1).trim();
  if (!value) throw new Error(`${variable} is not set (environment or modern/infra/.env.local)`);
  return value;
}

/** Open `path` as a visitor who is not signed in, complete the Keycloak form as `user`, and wait until the app is shown again. */
export async function signIn(page: Page, user: PemUser, path: string): Promise<void> {
  await page.goto(path, { waitUntil: 'commit' });
  await page.waitForURL(KEYCLOAK_AUTHORIZE);
  await page.locator('#username').fill(user);
  await page.locator('#password').fill(passwordOf(user));
  await page.locator('#kc-login').click();
  await page.waitForURL((url) => !url.pathname.startsWith('/realms/pem/'));
}

export async function signOut(page: Page): Promise<void> {
  await page.getByRole('button', { name: /sign out/i }).or(page.getByRole('link', { name: /sign out/i })).first().click();
  const signedOut = page.getByRole('heading', { level: 1, name: /signed out/i });
  const confirm = page.locator('#kc-logout'); // Keycloak asks first when the logout request has no id_token_hint
  await expect(signedOut.or(confirm)).toBeVisible();
  if (await confirm.isVisible()) await confirm.click();
  await expect(signedOut).toBeVisible();
}

/** The claims of a bearer token (header "Authorization: Bearer <jwt>"); never printed. */
export function claimsOf(authorization: string | undefined): Record<string, unknown> {
  const token = authorization?.replace(/^Bearer /, '') ?? '';
  const payload = token.split('.')[1];
  if (!payload) throw new Error('the Authorization header does not carry a bearer JWT');
  return JSON.parse(Buffer.from(payload, 'base64url').toString('utf8')) as Record<string, unknown>;
}
