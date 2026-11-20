// MAM-80 AC-8 in a real browser: analyst1 signs in with the real Keycloak realm pem and opens /console/providers/:npi; the provider
// API is answered in the browser (e2e/support/gateway-routes.ts). Axe scans: an individual record, an organisation record, one record
// per status (A, T, P, D, S) and the not-found state. Reading the page with the keyboard: the links in visual order.
import { expect, test, type Page } from '@playwright/test';
import { json, mockApi } from './support/gateway-routes';
import { signIn } from './support/keycloak';
import { expectNoAxeViolations, expectShellStructure, tabTo } from './support/page';

test.use({ trace: 'off' });

const NPI = '1003000111';
const INDIVIDUAL = {
  npi: NPI, medicaidId: 'TX0000111', providerType: 'IN', taxonomy: '207Q00000X', riskTier: 'L', enrollStatus: 'A', screenFlag: 'P',
  effectiveDate: '2026-03-02', revalidationDueDate: '2031-03-02', version: 1,
  individual: { lastName: 'RIVERA', firstName: 'MARIA', licenseNo: 'MD12345', licenseState: 'TX' },
};
const GROUP = {
  ...INDIVIDUAL, npi: '1003000444', providerType: 'GR', taxonomy: '193200000X', individual: undefined,
  organization: { legalName: 'EXCLUDED CLINIC LLC', dbaName: 'EXCLUDED CLINIC', ownerNpi: '1990000111' },
};

async function open(page: Page, record: { npi: string } & Record<string, unknown>, status = 200) {
  const api = await mockApi(page, { [`GET /api/v1/providers/${record.npi}`]: () => json(status, status === 200 ? record : { title: 'Not Found', status }) });
  await signIn(page, 'analyst1', `/console/providers/${record.npi}`);
  await expect(page.getByRole('heading', { level: 1, name: 'Provider detail' })).toBeVisible();
  return api;
}

/** The value of a row of the list "Provider record" (UI polish, Q-097): <div><dt>label</dt><dd>value</dd></div>. The page also shows the list "Provider", so the region is named. */
const valueOf = (page: Page, label: string) => page.getByRole('region', { name: 'Provider record', exact: true }).locator('dl > div').filter({ has: page.getByText(label, { exact: true }) }).locator('dd');

test('[MAM-80 AC-1][MAM-80 AC-8] an individual record: NPI in the heading, the banner and every field, zero axe violations', async ({ page }) => {
  await open(page, INDIVIDUAL);

  await expect(page.getByRole('heading', { level: 2, name: 'Status: Active (A)' })).toBeVisible();
  await expect(page.getByRole('heading', { name: /1003000111/ })).toBeVisible();
  const record = page.getByRole('region', { name: 'Provider record', exact: true });
  // user decision 2026-11-19 (Q-097): the equivalent accessibility check for a summary list, which has no header cells: every value is a dd paired with its dt term
  await expect(record.locator('dl')).toHaveCount(1);
  await expect(record.locator('dt')).toHaveText(['NPI', 'Medicaid ID', 'Provider type', 'Taxonomy', 'Enrolment status', 'Screening result', 'Risk tier', 'Effective date', 'Revalidation due', 'Last name', 'First name', 'Licence number', 'Licence state']);
  await expect(record.locator('dt + dd')).toHaveCount(13);
  await expect(valueOf(page, 'Licence number')).toHaveText('MD12345');
  await expect(valueOf(page, 'Revalidation due')).toHaveText('2 Mar 2031');
  await expectShellStructure(page);
  await expectNoAxeViolations(page);
});

test('[MAM-80 AC-2][MAM-80 AC-8] an organisation record shows Legal name, DBA name and Owner NPI; zero axe violations', async ({ page }) => {
  await open(page, GROUP);

  const record = page.getByRole('region', { name: 'Provider record', exact: true });
  await expect(valueOf(page, 'Owner NPI')).toHaveText('1990000111');
  await expect(record.locator('dt').filter({ hasText: /^Last name$/ })).toHaveCount(0);
  await expectNoAxeViolations(page);
});

for (const [status, label] of [['A', 'Active (A)'], ['T', 'Terminated (T)'], ['P', 'Pending (P)'], ['D', 'Denied (D)'], ['S', 'Suspended (S)']] as const) {
  test(`[MAM-80 AC-3][MAM-80 AC-8] status ${status}: the banner reads "Status: ${label}", the row reads "${label}", zero axe violations`, async ({ page }) => {
    await open(page, { ...INDIVIDUAL, enrollStatus: status });

    await expect(page.getByRole('heading', { level: 2, name: `Status: ${label}` })).toBeVisible();
    await expect(page.getByRole('heading', { level: 2, name: /^Status: / })).toHaveCount(1);
    await expect(valueOf(page, 'Enrolment status')).toHaveText(label);
    await expectNoAxeViolations(page);
  });
}

test('[MAM-80 AC-6] an unrecognised status X (a test double of the API) reads "Status: Denied (X)"', async ({ page }) => {
  await open(page, { ...INDIVIDUAL, enrollStatus: 'X' });

  await expect(page.getByRole('heading', { level: 2, name: 'Status: Denied (X)' })).toBeVisible();
  await expectNoAxeViolations(page);
});

test('[MAM-80 AC-7][MAM-80 AC-8] an unknown provider: "No provider found" and the link "Back to provider search" works with the keyboard; zero axe violations', async ({ page }) => {
  await open(page, { npi: '1003000999' }, 404);

  await expect(page.getByText('No provider found')).toBeVisible();
  await expect(page.getByRole('table')).toHaveCount(0);
  await expect(page.getByRole('region', { name: 'Provider record', exact: true })).toHaveCount(0);
  await expect(page.getByRole('region', { name: 'Provider', exact: true })).toHaveCount(0);
  await expectNoAxeViolations(page);
  await tabTo(page, page.getByRole('link', { name: 'Back to provider search' }));
  await page.keyboard.press('Enter');
  await expect(page).toHaveURL(/\/console\/providers$/);
});

test('[MAM-80 AC-8] a pending provider with a passed screening: the "Approve or deny" action is reached with the keyboard and valueOf rows read in order', async ({ page }) => {
  await open(page, { ...INDIVIDUAL, enrollStatus: 'P', riskTier: null, revalidationDueDate: null });

  await expect(valueOf(page, 'Risk tier')).toHaveText('Not assigned');
  await expect(valueOf(page, 'Revalidation due')).toHaveText('Not set');
  await tabTo(page, page.getByRole('link', { name: 'Approve or deny' }));
  await page.keyboard.press('Enter');
  await expect(page).toHaveURL(new RegExp(`/console/providers/${NPI}/approval$`));
});
