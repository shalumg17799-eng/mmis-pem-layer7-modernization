// MAM-68 AC-7 in a real browser: provider1 signs in with the real Keycloak realm pem and opens /portal/provider; the provider
// API is answered in the browser (e2e/support/gateway-routes.ts). Axe scans: an individual record, an organisation record and
// the no-record page. The page is read with the keyboard only (the links of the no-record page).
import { expect, test, type Page } from '@playwright/test';
import { json, mockApi, type RouteHandler } from './support/gateway-routes';
import { signIn } from './support/keycloak';
import { expectNoAxeViolations, expectShellStructure, tabTo } from './support/page';

test.use({ trace: 'off' });

const NPI = '1003000111';
const BASE = { npi: NPI, medicaidId: 'TX0000111', providerType: 'IN', taxonomy: '207Q00000X', riskTier: 'L', enrollStatus: 'A', screenFlag: 'P', effectiveDate: '2026-03-02', revalidationDueDate: '2031-03-02', version: 1 };
const INDIVIDUAL = { ...BASE, individual: { lastName: 'RIVERA', firstName: 'MARIA', licenseNo: 'MD12345', licenseState: 'TX' } };
const ORGANISATION = { ...BASE, providerType: 'GR', taxonomy: '193200000X', organization: { legalName: 'RIO GRANDE FAMILY MEDICINE PLLC', dbaName: 'RIO GRANDE FAMILY CARE', ownerNpi: '1990000111' } };

async function open(page: Page, handler: RouteHandler) {
  const api = await mockApi(page, { [`GET /api/v1/providers/${NPI}`]: handler });
  await signIn(page, 'provider1', '/portal/provider');
  await expect(page.getByRole('heading', { level: 1, name: 'My provider record' })).toBeVisible();
  return api;
}

/** The value of a row of a key-value list (UI polish, Q-097): <div><dt>label</dt><dd>value</dd></div>. */
const valueOf = (page: Page, label: string) => page.locator('dl > div').filter({ has: page.getByText(label, { exact: true }) }).locator('dd');

test('[MAM-68 AC-1][MAM-68 AC-7] the individual record shows every field and has zero axe violations; only the own record is requested', async ({ page }) => {
  const api = await open(page, () => json(200, INDIVIDUAL));

  await expect(valueOf(page, 'Provider type')).toHaveText('Individual (IN)');
  await expect(valueOf(page, 'Enrolment status')).toHaveText('Active (A)');
  await expect(valueOf(page, 'Screening result')).toHaveText('Pass (P)');
  await expect(valueOf(page, 'Risk tier')).toHaveText('Limited (L)');
  await expect(valueOf(page, 'Effective date')).toHaveText('2 Mar 2026');
  await expect(valueOf(page, 'Revalidation due')).toHaveText('2 Mar 2031');
  await expect(valueOf(page, 'Licence number')).toHaveText('MD12345');
  await expect(page.getByRole('main')).toContainText('Active (A)');
  // user decision 2026-11-19 (Q-097): the equivalent accessibility check for a summary list, which has no header cells: every value is a dd paired with its dt term
  const record = page.getByRole('region', { name: 'Provider record', exact: true });
  await expect(record.locator('dl')).toHaveCount(1);
  await expect(record.locator('dt')).toHaveText(['NPI', 'Medicaid ID', 'Provider type', 'Taxonomy', 'Enrolment status', 'Screening result', 'Risk tier', 'Effective date', 'Revalidation due', 'Last name', 'First name', 'Licence number', 'Licence state']);
  await expect(record.locator('dt + dd')).toHaveCount(13);
  await expectShellStructure(page);
  await expectNoAxeViolations(page);
  expect(new Set(api.calls.map((call) => `${call.method} ${call.path}`))).toEqual(new Set([`GET /api/v1/providers/${NPI}`]));
});

test('[MAM-68 AC-2][MAM-68 AC-7] the organisation record shows Legal name, DBA name and Owner NPI and has zero axe violations', async ({ page }) => {
  await open(page, () => json(200, ORGANISATION));

  await expect(valueOf(page, 'Provider type')).toHaveText('Group (GR)');
  await expect(valueOf(page, 'Legal name')).toHaveText('RIO GRANDE FAMILY MEDICINE PLLC');
  await expect(valueOf(page, 'Owner NPI')).toHaveText('1990000111');
  await expect(page.locator('dt').filter({ hasText: /^Last name$/ })).toHaveCount(0);
  await expectNoAxeViolations(page);
});

test('[MAM-68 AC-5][MAM-68 AC-7] the no-record page explains why, has no table and zero axe violations; its links work with the keyboard only', async ({ page }) => {
  await open(page, () => json(404, { title: 'Not Found', status: 404 }));

  const main = page.getByRole('main');
  await expect(main.getByText(/No provider record exists yet/i)).toBeVisible();
  await expect(main.getByText(/A record is created when an application is accepted/i)).toBeVisible();
  await expect(page.getByRole('table')).toHaveCount(0);
  await expect(page.getByRole('region', { name: 'Provider record', exact: true })).toHaveCount(0);
  await expectNoAxeViolations(page);

  const apply = main.getByRole('link', { name: 'Apply for enrolment' });
  await tabTo(page, apply);
  await page.keyboard.press('Enter');
  await expect(page).toHaveURL(/\/portal\/apply$/);
});

test('[MAM-68 AC-4] after a 403 the page shows an access-denied message and no provider data', async ({ page }) => {
  await open(page, () => json(403, { title: 'Forbidden', status: 403 }));

  await expect(page.getByText(/access denied/i)).toBeVisible();
  await expect(page.getByRole('table')).toHaveCount(0);
  await expect(page.getByRole('region', { name: 'Provider record', exact: true })).toHaveCount(0);
  await expectNoAxeViolations(page);
});
