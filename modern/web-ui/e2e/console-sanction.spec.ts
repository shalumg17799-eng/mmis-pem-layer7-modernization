// MAM-90 AC-8 in a real browser: supervisor1 signs in with the real Keycloak realm pem and records a sanction on
// /console/providers/1003000111/sanctions/new; the sanctions API is answered in the browser (e2e/support/gateway-routes.ts).
// Axe scans: the empty form, the form with field errors, the form after a successful action and the failure state.
// A sanction is recorded with the keyboard only (Tab, the list, typing, Enter).
import { expect, test, type Page } from '@playwright/test';
import { json, mockApi, type RouteHandler } from './support/gateway-routes';
import { signIn } from './support/keycloak';
import { expectNoAxeViolations, tabTo } from './support/page';

test.use({ trace: 'off' });

const NPI = '1003000111';
const RESULTING: Record<string, string> = { T: 'T', S: 'S', R: 'A' };

function world(over: Record<string, RouteHandler> = {}) {
  const history: unknown[] = [];
  const routes: Record<string, RouteHandler> = {
    'GET /api/v1/sanctions': () => json(200, { items: history, page: 0, size: 20, total: history.length }),
    'POST /api/v1/sanctions': (call) => {
      const body = call.body as { npi: string; action: string; reason?: string; effectiveDate: string };
      const recorded = {
        sanctionId: `00000000-0000-4000-8000-${String(history.length + 1).padStart(12, '0')}`,
        ...body, recordedBy: 'supervisor1', recordedAt: '2026-03-31T10:00:00Z', resultingStatus: RESULTING[body.action],
      };
      history.unshift(recorded);
      return json(201, recorded);
    },
    [`GET /api/v1/providers/${NPI}`]: () => json(200, { npi: NPI, medicaidId: 'TX0000111', providerType: 'IN', taxonomy: '207Q00000X', riskTier: 'L', enrollStatus: 'A', screenFlag: 'P', effectiveDate: '2026-03-02', revalidationDueDate: '2031-03-02', version: 1, individual: { lastName: 'RIVERA', firstName: 'MARIA', licenseNo: 'MD12345', licenseState: 'TX' } }),
    ...over,
  };
  return { routes, history };
}

async function open(page: Page, routes: Record<string, RouteHandler>, path = `/console/providers/${NPI}/sanctions/new`) {
  const api = await mockApi(page, routes);
  await signIn(page, 'supervisor1', path);
  await expect(page.getByRole('heading', { level: 1, name: 'Record a sanction' })).toBeVisible();
  return api;
}

const field = (page: Page, label: string | RegExp) => page.getByLabel(label, { exact: typeof label === 'string' });

test('[MAM-90 AC-1] supervisor1 opens the form from the detail page with "Record sanction"', async ({ page }) => {
  await mockApi(page, world().routes);
  await signIn(page, 'supervisor1', `/console/providers/${NPI}`);

  await page.getByRole('link', { name: 'Record sanction' }).click();

  await expect(page).toHaveURL(new RegExp(`/console/providers/${NPI}/sanctions/new$`));
  await expect(page.getByRole('heading', { level: 1, name: 'Record a sanction' })).toBeVisible();
});

test('[MAM-90 AC-2][MAM-90 AC-8] the empty form shows every field with a visible label and has zero axe violations', async ({ page }) => {
  await open(page, world().routes);

  await expect(field(page, 'NPI')).toHaveValue(NPI);
  await expect(field(page, 'NPI')).toHaveAttribute('readonly', '');
  const options = (await field(page, 'Action').locator('option').allTextContents()).map((option) => option.trim());
  expect(options).toEqual(['Choose...', 'Terminate (T)', 'Suspend (S)', 'Reinstate (R)']);
  await expect(field(page, /^Reason/)).toHaveAttribute('maxlength', '60');
  await expect(field(page, /^Effective date/)).toBeVisible();
  await expectNoAxeViolations(page);
});

test('[MAM-90 AC-2][MAM-90 AC-8] with no action and no date nothing is sent; the field errors and the linked summary have zero axe violations', async ({ page }) => {
  const api = await open(page, world().routes);

  await page.getByRole('button', { name: 'Record sanction' }).click();

  await expect(page.getByRole('heading', { level: 2, name: '2 errors in the sanction' })).toBeVisible();
  await expect(field(page, 'Action')).toHaveAccessibleDescription(/Choose an action/);
  await expect(field(page, /^Effective date/)).toHaveAccessibleDescription(/Enter the effective date as MM\/DD\/YYYY/);
  expect(api.to('POST', '/api/v1/sanctions')).toHaveLength(0);
  await expectNoAxeViolations(page);
  await page.getByRole('link', { name: 'Choose an action' }).click();
  await expect(field(page, 'Action')).toBeFocused();
});

test('[MAM-90 AC-3][MAM-90 AC-4][MAM-90 AC-8] Suspend then Reinstate: the page confirms Suspended (S) and Active (A), the history lists both rows, zero axe violations', async ({ page }) => {
  const api = await open(page, world().routes);

  await field(page, 'Action').selectOption('S');
  await field(page, /^Reason/).fill('Licence under review');
  await field(page, /^Effective date/).fill('03/10/2026');
  await page.getByRole('button', { name: 'Record sanction' }).click();
  await expect(page.getByText(/Sanction recorded/)).toContainText('Suspended (S)');
  await expectNoAxeViolations(page);

  await field(page, 'Action').selectOption('R');
  await field(page, /^Reason/).fill('Licence confirmed');
  await field(page, /^Effective date/).fill('03/20/2026');
  await page.getByRole('button', { name: 'Record sanction' }).click();
  await expect(page.getByText(/Sanction recorded/)).toContainText('Active (A)');

  const history = page.getByRole('table', { name: 'Sanction history' });
  await expect(history.getByRole('columnheader')).toHaveText(['Effective date', 'Action', 'Reason', 'By']);
  await expect(history.locator('tbody tr')).toHaveCount(2);
  await expect(history.locator('tbody tr').nth(0).getByRole('cell')).toHaveText(['20 Mar 2026', 'Reinstate (R)', 'Licence confirmed', 'supervisor1']);
  await expect(history.locator('tbody tr').nth(1).getByRole('cell')).toHaveText(['10 Mar 2026', 'Suspend (S)', 'Licence under review', 'supervisor1']);
  expect(api.to('POST', '/api/v1/sanctions').map((call) => call.body)).toEqual([
    { npi: NPI, action: 'S', reason: 'Licence under review', effectiveDate: '2026-03-10' },
    { npi: NPI, action: 'R', reason: 'Licence confirmed', effectiveDate: '2026-03-20' },
  ]);
  await expectNoAxeViolations(page);
});

test('[MAM-90 AC-5] an unknown action code (422 of a test double) is shown at the Action field and in the summary', async ({ page }) => {
  const detail = 'Field action must be one of T, S or R (T terminate, S suspend, R reinstate).';
  await open(page, world({ 'POST /api/v1/sanctions': () => json(422, { title: 'Unprocessable Entity', status: 422, detail }) }).routes);

  await field(page, 'Action').selectOption('S');
  await field(page, /^Effective date/).fill('03/31/2026');
  await page.getByRole('button', { name: 'Record sanction' }).click();

  await expect(page.getByRole('heading', { level: 2, name: '1 error in the sanction' })).toBeVisible();
  await expect(field(page, 'Action')).toHaveAccessibleDescription(new RegExp(detail.replaceAll('.', '\\.').replaceAll('(', '\\(').replaceAll(')', '\\)')));
  await expect(page.getByText(/Sanction recorded/)).toHaveCount(0);
  await expectNoAxeViolations(page);
});

for (const [name, status, message] of [
  ['a 404', 404, 'No provider found'],
  ['a 403', 403, 'You are not allowed to record sanctions'],
  ['a 503', 503, 'The sanction could not be applied. Nothing was recorded.'],
] as const) {
  test(`[MAM-90 AC-7][MAM-90 AC-8] ${name} shows "${message}", claims nothing and has zero axe violations`, async ({ page }) => {
    await open(page, world({ 'POST /api/v1/sanctions': () => json(status, { title: 'x', status }) }).routes);

    await field(page, 'Action').selectOption('S');
    await field(page, /^Effective date/).fill('03/31/2026');
    await page.getByRole('button', { name: 'Record sanction' }).click();

    await expect(page.getByText(message, { exact: true })).toBeVisible();
    await expect(page.getByText(/Sanction recorded/)).toHaveCount(0);
    await expectNoAxeViolations(page);
  });
}

test('[MAM-90 AC-8] a sanction is recorded with the keyboard only: Tab to the list, a letter, Tab to the reason and the date, Enter', async ({ page }) => {
  const api = await open(page, world().routes);

  await tabTo(page, field(page, 'Action'));
  await page.keyboard.press('ArrowDown'); // Terminate (T)
  await page.keyboard.press('ArrowDown'); // Suspend (S)
  await expect(field(page, 'Action')).toHaveValue('S');
  await page.keyboard.press('Tab');
  await expect(field(page, /^Reason/)).toBeFocused();
  await page.keyboard.type('Licence under review');
  await page.keyboard.press('Tab');
  await expect(field(page, /^Effective date/)).toBeFocused();
  await page.keyboard.type('03/31/2026');
  await page.keyboard.press('Enter');

  await expect(page.getByText(/Sanction recorded/)).toContainText('Suspended (S)');
  expect(api.to('POST', '/api/v1/sanctions')[0]?.body).toEqual({ npi: NPI, action: 'S', reason: 'Licence under review', effectiveDate: '2026-03-31' });
});
