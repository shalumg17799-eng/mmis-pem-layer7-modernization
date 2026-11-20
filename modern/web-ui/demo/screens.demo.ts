// Walks journey J1 on the live, freshly seeded stack and saves one screenshot per demo step into modern/docs/reports/ui-screenshots/.
// It is demo tooling for modern/docs/DEMO.md, not a story test (the J1 test is e2e/journeys/j1-enrolment.spec.ts).
// Passwords are read at run time from modern/infra/.env.local and never written anywhere; Keycloak's password field is
// masked, so no screenshot shows one.
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { expect, test, type Page } from '@playwright/test';
import { signIn, type PemUser } from '../e2e/support/keycloak';

const OUT = fileURLToPath(new URL('../../docs/reports/ui-screenshots/', import.meta.url));
const NPI = '1003000111';
const GATEWAY = 'http://localhost:8080';

const PASSWORD_VARIABLE: Record<PemUser, string> = {
  provider1: 'KC_PROVIDER_PASSWORD',
  analyst1: 'KC_ANALYST_PASSWORD',
  supervisor1: 'KC_SUPERVISOR_PASSWORD',
  admin1: 'KC_ADMIN_PASSWORD',
};

function passwordOf(user: PemUser): string {
  const variable = PASSWORD_VARIABLE[user];
  const file = fileURLToPath(new URL('../../infra/.env.local', import.meta.url));
  const line = readFileSync(file, 'utf8')
    .split(/\r?\n/)
    .find((candidate) => candidate.startsWith(`${variable}=`));
  return line?.slice(variable.length + 1).trim() ?? '';
}

// Wait until no part of the page still says "Loading ..." so the picture shows real content.
const shot = async (page: Page, name: string) => {
  await expect(page.getByRole('main').getByText(/Loading/)).toHaveCount(0, { timeout: 60_000 });
  await page.screenshot({ path: `${OUT}${name}.png`, fullPage: true });
};

test('demo screenshots of journey J1', async ({ browser, request }) => {
  const token = async (user: PemUser) =>
    ((await (
      await request.post('http://localhost:8180/realms/pem/protocol/openid-connect/token', {
        form: { grant_type: 'password', client_id: 'pem-test', username: user, password: passwordOf(user) },
      })
    ).json()) as { access_token: string }).access_token;
  const analyst = await token('analyst1');
  const provider = async () =>
    (await (await request.get(`${GATEWAY}/api/v1/providers/${NPI}`, { headers: { Authorization: `Bearer ${analyst}` } })).json()) as {
      riskTier?: string;
    };

  // Provider: home, apply form (filled), status page.
  const portal = await (await browser.newContext()).newPage();
  await signIn(portal, 'provider1', '/portal');
  await expect(portal.getByRole('heading', { level: 1, name: 'My enrolment' })).toBeVisible();
  await expect(portal.getByText('Your enrolment').first()).toBeVisible();
  await shot(portal, '01-portal-home');
  await portal.goto('/portal/apply');
  await portal.getByLabel(/^Provider type/).selectOption('IN');
  await portal.getByLabel(/^NPI/).fill(NPI);
  await portal.getByLabel(/^Medicaid ID/).fill('TX0000111');
  await portal.getByLabel(/^Taxonomy code/).fill('207Q00000X');
  await portal.getByLabel(/^Last name/).fill('RIVERA');
  await portal.getByLabel(/^First name/).fill('MARIA');
  await portal.getByLabel(/^Licence number/).fill('MD12345');
  await portal.getByLabel(/^Licence state/).fill('TX');
  await shot(portal, '02-apply-form');
  await portal.getByRole('button', { name: 'Submit application' }).click();
  await expect(portal.getByRole('heading', { level: 1, name: 'Application status' })).toBeVisible();
  await expect(portal.getByRole('main').getByText('Validated', { exact: true })).toBeVisible();
  await shot(portal, '03-application-status');

  // Wait for screening and the risk tier, then the analyst side.
  await expect.poll(async () => (await provider()).riskTier, { timeout: 120_000, intervals: [2_000] }).toBe('L');
  const console_ = await (await browser.newContext()).newPage();
  await signIn(console_, 'analyst1', '/console/queues');
  await expect(console_.getByRole('link', { name: 'Review' }).first()).toBeVisible();
  await shot(console_, '04-work-queues');
  await console_.goto(`/console/providers/${NPI}`);
  await expect(console_.getByRole('heading', { level: 1, name: 'Provider detail' })).toBeVisible();
  await expect(console_.getByRole('main')).toContainText('Pending (P)');
  await expect(console_.locator('section').filter({ has: console_.getByRole('heading', { name: /^Screening/ }) }).getByRole('row').filter({ hasText: 'OIG exclusion list' })).toHaveCount(1);
  await shot(console_, '05-provider-detail-pending');
  await console_.goto(`/console/providers/${NPI}/approval`);
  await expect(console_.getByRole('heading', { level: 1, name: 'Approve or deny' })).toBeVisible();
  await expect(console_.getByRole('radio', { name: 'Approve (becomes Active)' })).toBeVisible();
  await shot(console_, '06-approve-or-deny');
  await console_.getByRole('radio', { name: 'Approve (becomes Active)' }).check();
  await console_.getByRole('button', { name: 'Record decision' }).click();
  await expect(console_.getByText(/Decision recorded/)).toBeVisible();
  await shot(console_, '07-decision-recorded');

  // Public directory, then the provider's notices and record, then Mailpit.
  const visitor = await (await browser.newContext()).newPage();
  await visitor.goto('/directory');
  await visitor.getByLabel('Name', { exact: true }).fill('RIVERA');
  await visitor.getByRole('button', { name: 'Search' }).click();
  await expect(visitor.getByRole('row').filter({ hasText: NPI })).toHaveCount(1, { timeout: 120_000 });
  await shot(visitor, '08-public-directory');
  await portal.goto('/portal/notices');
  await expect(portal.getByRole('row').filter({ hasText: 'Enrolment approved' })).toHaveCount(1, { timeout: 120_000 });
  await shot(portal, '09-my-notices');
  await portal.goto('/portal/provider');
  await expect(portal.getByRole('main')).toContainText('Active (A)');
  await shot(portal, '10-my-provider-record');
  await visitor.goto('http://localhost:8025/');
  await expect(visitor.getByText('Enrolment approved').first()).toBeVisible();
  await shot(visitor, '11-mailpit');
});
