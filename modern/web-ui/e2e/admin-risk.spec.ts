// MAM-92 AC-7 in a real browser: admin1 signs in with the real Keycloak realm pem and edits the risk-tier rules on
// /console/admin/risk-rules; GET / PUT /api/v1/risk-rules are answered in the browser (e2e/support/gateway-routes.ts). Axe scans: the
// table, the table with field errors and the page after a refusal. The table is edited and saved with the keyboard only.
import { expect, test, type Page } from '@playwright/test';
import { json, mockApi, type RouteHandler } from './support/gateway-routes';
import { signIn } from './support/keycloak';
import { expectNoAxeViolations, tabTo } from './support/page';

test.use({ trace: 'off' });

const LEGACY = [
  { taxonomyPrefix: '332', tier: 'H' }, { taxonomyPrefix: '251', tier: 'H' }, { taxonomyPrefix: '341', tier: 'M' },
  { taxonomyPrefix: '291', tier: 'M' }, { taxonomyPrefix: '225', tier: 'M' },
];

function world(over: Record<string, RouteHandler> = {}) {
  let current: { version: number; defaultTier: string; rules: unknown[] } = { version: 1, defaultTier: 'L', rules: LEGACY };
  const routes: Record<string, RouteHandler> = {
    'GET /api/v1/risk-rules': () => json(200, current),
    'PUT /api/v1/risk-rules': (call) => {
      const body = call.body as { defaultTier: string; rules: unknown[] };
      current = { version: current.version + 1, defaultTier: body.defaultTier, rules: body.rules };
      return json(200, current);
    },
    ...over,
  };
  return { routes, set: (next: typeof current) => { current = next; } };
}

async function open(page: Page, routes: Record<string, RouteHandler>) {
  const api = await mockApi(page, routes);
  await signIn(page, 'admin1', '/console/admin/risk-rules');
  await expect(page.getByRole('heading', { level: 1, name: 'Risk-tier rules' })).toBeVisible();
  await expect(page.getByRole('heading', { level: 2, name: /^Version \d+/ })).toBeVisible();
  return api;
}

const prefix = (page: Page, row: number) => page.getByLabel(`Taxonomy prefix, row ${row}`, { exact: true });
const tier = (page: Page, row: number) => page.getByLabel(`Tier, row ${row}`, { exact: true });

test('[MAM-92 AC-1][MAM-92 AC-7] the table shows the legacy values with labelled cells and has zero axe violations', async ({ page }) => {
  await open(page, world().routes);

  await expect(page.getByRole('heading', { level: 2, name: 'Version 1 (default tier: Limited (L))' })).toBeVisible();
  for (const [index, rule] of LEGACY.entries()) {
    await expect(prefix(page, index + 1)).toHaveValue(rule.taxonomyPrefix);
    await expect(tier(page, index + 1).locator('option:checked')).toHaveText(rule.tier === 'H' ? 'High (H)' : 'Moderate (M)');
  }
  await expect(page.getByRole('button', { name: 'Remove row 4 (291)' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Add row' })).toBeVisible();
  await expectNoAxeViolations(page);
});

test('[MAM-92 AC-4][MAM-92 AC-7] invalid prefixes send nothing; the summary links to the cells and the page has zero axe violations', async ({ page }) => {
  const api = await open(page, world().routes);

  await prefix(page, 1).fill('20');
  await prefix(page, 3).fill('2o7');
  await page.getByRole('button', { name: 'Save as new version' }).click();

  await expect(page.getByRole('heading', { level: 2, name: '2 errors in the risk rules' })).toBeVisible();
  await expect(prefix(page, 1)).toHaveAccessibleDescription(/A prefix is exactly 3 upper-case letters or digits/);
  await expect(prefix(page, 3)).toHaveAccessibleDescription(/A prefix is exactly 3 upper-case letters or digits/);
  expect(api.to('PUT', '/api/v1/risk-rules')).toHaveLength(0);
  await expectNoAxeViolations(page);
  await page.getByRole('link', { name: 'Row 3: A prefix is exactly 3 upper-case letters or digits' }).click();
  await expect(prefix(page, 3)).toBeFocused();
});

test('[MAM-92 AC-2][MAM-92 AC-3] a changed table is saved as version 2 with exactly the edited rows and default tier', async ({ page }) => {
  const api = await open(page, world().routes);

  await tier(page, 5).selectOption('H');
  await page.getByRole('button', { name: 'Remove row 4 (291)' }).click();
  await page.getByLabel('Default tier', { exact: true }).selectOption('M');
  await page.getByRole('button', { name: 'Add row' }).click();
  await prefix(page, 5).fill('207');
  await tier(page, 5).selectOption('M');
  await page.getByRole('button', { name: 'Save as new version' }).click();

  await expect(page.getByRole('heading', { level: 2, name: 'Version 2 (default tier: Moderate (M))' })).toBeVisible();
  expect(api.to('PUT', '/api/v1/risk-rules')[0]?.body).toEqual({
    version: 1,
    defaultTier: 'M',
    rules: [
      { taxonomyPrefix: '332', tier: 'H' }, { taxonomyPrefix: '251', tier: 'H' }, { taxonomyPrefix: '341', tier: 'M' },
      { taxonomyPrefix: '225', tier: 'H' }, { taxonomyPrefix: '207', tier: 'M' },
    ],
  });
});

test('[MAM-92 AC-5][MAM-92 AC-7] a refusal (422) shows its detail, reloads the table the service holds and has zero axe violations', async ({ page }) => {
  const w = world();
  w.routes['PUT /api/v1/risk-rules'] = () => {
    w.set({ version: 2, defaultTier: 'M', rules: [{ taxonomyPrefix: '111', tier: 'H' }] });
    return json(422, { title: 'Unprocessable Entity', status: 422, detail: 'The table was changed by someone else (test)' });
  };
  await open(page, w.routes);

  await page.getByRole('button', { name: 'Add row' }).click();
  await prefix(page, 6).fill('207');
  await page.getByRole('button', { name: 'Save as new version' }).click();

  await expect(page.getByText('The table was changed by someone else (test)')).toBeVisible();
  await expect(page.getByRole('heading', { level: 2, name: 'Version 2 (default tier: Moderate (M))' })).toBeVisible();
  await expect(prefix(page, 1)).toHaveValue('111');
  await expectNoAxeViolations(page);
});

test('[MAM-92 AC-6] a 403 to a save shows "You are not allowed to change the risk rules"', async ({ page }) => {
  await open(page, world({ 'PUT /api/v1/risk-rules': () => json(403, { title: 'Forbidden', status: 403 }) }).routes);

  await page.getByRole('button', { name: 'Save as new version' }).click();

  await expect(page.getByText('You are not allowed to change the risk rules')).toBeVisible();
});

test('[MAM-92 AC-7] the table is edited and saved with the keyboard only: Add row, the prefix, the tier, Save as new version', async ({ page }) => {
  const api = await open(page, world().routes);

  await tabTo(page, page.getByRole('button', { name: 'Add row' }));
  await page.keyboard.press('Enter');
  await expect(prefix(page, 6)).toBeFocused();
  await page.keyboard.type('207');
  await page.keyboard.press('Tab');
  await expect(tier(page, 6)).toBeFocused();
  await page.keyboard.press('ArrowUp'); // Limited (L) -> Moderate (M)
  await expect(tier(page, 6)).toHaveValue('M');
  await tabTo(page, page.getByRole('button', { name: 'Save as new version' }));
  await page.keyboard.press('Enter');

  await expect(page.getByRole('heading', { level: 2, name: 'Version 2 (default tier: Limited (L))' })).toBeVisible();
  expect((api.to('PUT', '/api/v1/risk-rules')[0]?.body as { rules: unknown[] }).rules.at(-1)).toEqual({ taxonomyPrefix: '207', tier: 'M' });
  await expectNoAxeViolations(page);
});
