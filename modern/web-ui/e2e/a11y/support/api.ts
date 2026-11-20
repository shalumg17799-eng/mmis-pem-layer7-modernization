// MAM-86: the gateway and Keycloak calls that the keyboard journeys use for set-up and for the outcome checks (the status reached,
// the notice, the file). They are plain HTTP calls, not browser input. Passwords come from the environment or modern/infra/.env.local
// and are never written to a file, a log or a message. seedStack() restores the seed state before a journey, like `make seed`.
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { expect, type APIRequestContext } from '@playwright/test';
import { passwordOf, type Role } from './sessions';

export const GATEWAY = process.env.PEM_GATEWAY_URL ?? 'http://localhost:8080';
export const KEYCLOAK_TOKEN = `${process.env.PEM_KEYCLOAK_URL ?? 'http://localhost:8180'}/realms/pem/protocol/openid-connect/token`;
export const MAILPIT = process.env.PEM_MAILPIT_URL ?? 'http://localhost:8025';
export const POLL = { timeout: 120_000, intervals: [2_000] };

// eslint-disable-next-line @typescript-eslint/no-explicit-any -- untyped JSON answers of several services
export type Json = any;

/** Restore the seed state of every service database (modern/infra/scripts/seed.sh) and empty the Mailpit inbox. */
export function seedStack(): void {
  const modern = fileURLToPath(new URL('../../../../', import.meta.url));
  execFileSync('bash', ['infra/scripts/seed.sh'], { cwd: modern, timeout: 180_000, stdio: 'pipe' });
}

/** A real access token for the API checks (Keycloak client pem-test, password grant); never printed. */
export async function tokenOf(request: APIRequestContext, user: Role): Promise<string> {
  const response = await request.post(KEYCLOAK_TOKEN, {
    form: { grant_type: 'password', client_id: 'pem-test', username: user, password: passwordOf(user) },
  });
  expect(response.status(), `Keycloak token for ${user}`).toBe(200);
  return ((await response.json()) as { access_token: string }).access_token;
}

export async function getJson(request: APIRequestContext, path: string, token?: string): Promise<{ status: number; body: Json }> {
  const response = await request.get(`${GATEWAY}${path}`, { headers: token ? { Authorization: `Bearer ${token}` } : {} });
  const text = await response.text();
  return { status: response.status(), body: text ? JSON.parse(text) : null };
}

/** Subjects of the mails in Mailpit that were sent to `address`. */
export async function mailSubjects(request: APIRequestContext, address: string): Promise<string[]> {
  const body = (await (await request.get(`${MAILPIT}/api/v1/messages`)).json()) as { messages: Array<{ To: Array<{ Address: string }>; Subject: string }> };
  return body.messages.filter((message) => message.To.some((to) => to.Address === address)).map((message) => message.Subject);
}

/** "19 Nov 2026" for an ISO date (yyyy-mm-dd): the plain form the UI displays (Q-097). */
const MONTH = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
export const shownDate = (iso: string): string => `${Number(iso.slice(8, 10))} ${MONTH[Number(iso.slice(5, 7)) - 1]} ${iso.slice(0, 4)}`;

/** The UTC date of an HTTP Date header plus five years, as yyyy-mm-dd (29 February becomes 28 February in a common year). */
export function fiveYearsAfter(httpDate: string): string {
  const day = new Date(httpDate);
  const year = day.getUTCFullYear() + 5;
  const month = day.getUTCMonth() + 1;
  const leap = (year % 4 === 0 && year % 100 !== 0) || year % 400 === 0;
  const date = month === 2 && day.getUTCDate() === 29 && !leap ? 28 : day.getUTCDate();
  return `${year}-${String(month).padStart(2, '0')}-${String(date).padStart(2, '0')}`;
}
