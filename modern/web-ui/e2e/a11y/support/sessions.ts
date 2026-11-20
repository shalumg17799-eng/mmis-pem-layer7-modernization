// MAM-86: one signed-in browser tab per role, reused by the tests of a file (a Keycloak sign-in costs seconds). The tab keeps the
// token in its session storage, so later page.goto() calls stay signed in; when Keycloak asks again (the token expired) the form
// is filled again. Passwords are read at run time from the environment (KC_<ROLE>_PASSWORD) or modern/infra/.env.local and are
// never written to a file, a log or a message.
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { expect, type Browser, type BrowserContext, type Page } from '@playwright/test';

export type Role = 'provider1' | 'analyst1' | 'supervisor1' | 'admin1';

const PASSWORD_VARIABLE: Record<Role, string> = {
  provider1: 'KC_PROVIDER_PASSWORD',
  analyst1: 'KC_ANALYST_PASSWORD',
  supervisor1: 'KC_SUPERVISOR_PASSWORD',
  admin1: 'KC_ADMIN_PASSWORD',
};

export const KEYCLOAK_AUTHORIZE = /\/realms\/pem\/protocol\/openid-connect\/auth/;

/** The password of a test user; never printed. */
export function passwordOf(user: Role): string {
  const variable = PASSWORD_VARIABLE[user];
  const fromEnvironment = process.env[variable];
  if (fromEnvironment) return fromEnvironment;
  const file = fileURLToPath(new URL('../../../../infra/.env.local', import.meta.url));
  const line = readFileSync(file, 'utf8')
    .split(/\r?\n/)
    .find((candidate) => candidate.startsWith(`${variable}=`));
  const value = line?.slice(variable.length + 1).trim();
  if (!value) throw new Error(`${variable} is not set (environment or modern/infra/.env.local)`);
  return value;
}

const opened = new Map<string, { context: BrowserContext; page: Page }>();

/** The signed-in tab of a role ('public' is a visitor who never signs in). Created on first use, closed by closeSessions(). */
export async function tabOf(browser: Browser, who: Role | 'public'): Promise<Page> {
  const known = opened.get(who);
  if (known && !known.page.isClosed()) return known.page;
  const context = await browser.newContext();
  const page = await context.newPage();
  opened.set(who, { context, page });
  return page;
}

export async function closeSessions(): Promise<void> {
  for (const { context } of opened.values()) await context.close().catch(() => undefined);
  opened.clear();
}

/**
 * Open `path` in the tab of `user` and wait until the page shows `ready` (a locator that is only visible once the screen is
 * there). When the tab is not signed in (first use, or the token ended) the Keycloak form is completed first. Mouse and fill
 * are fine here: only the keyboard-journeys file is keyboard only.
 */
export async function openAs(page: Page, user: Role | 'public', path: string, ready: () => ReturnType<Page['locator']>): Promise<void> {
  await page.goto(path, { waitUntil: 'commit' });
  if (user !== 'public') {
    const form = page.locator('#username');
    // Either the screen appears at once (signed in), or the browser is sent to Keycloak.
    await expect(async () => {
      const onKeycloak = KEYCLOAK_AUTHORIZE.test(page.url());
      const there = await ready().first().isVisible().catch(() => false);
      expect(onKeycloak || there, `neither the screen nor the Keycloak form is shown at ${page.url()}`).toBe(true);
    }).toPass({ timeout: 30_000, intervals: [250, 500, 1000] });
    if (KEYCLOAK_AUTHORIZE.test(page.url())) {
      await expect(form).toBeVisible({ timeout: 15_000 });
      await form.fill(user);
      await page.locator('#password').fill(passwordOf(user));
      await page.locator('#kc-login').click();
      await page.waitForURL((url) => !url.pathname.startsWith('/realms/pem/'), { timeout: 30_000 });
    }
  }
  await expect(ready().first()).toBeVisible({ timeout: 30_000 });
}
