// MAM-34 AC-3 against the running stack (make up && make seed): real Keycloak sign-in as analyst1, the real gateway and
// provider-service, the seeded provider TESTPROV ALPHA of modern/testdata/README.md. Nothing is mocked.
import { expect, test } from '@playwright/test';
import { signIn } from './support/keycloak';
import { expectNoAxeViolations } from './support/page';

const SEEDED = { npi: '1005940865', name: 'ALPHA TESTPROV', status: 'Active (A)', risk: 'Limited (L)' };

test('[MAM-34 AC-3] analyst1 signs in, looks up seeded NPI 1005940865 on Provider search and sees its name, status and risk tier', async ({ page }) => {
  await signIn(page, 'analyst1', '/console/providers');
  await expect(page.getByRole('heading', { level: 1, name: 'Provider search' })).toBeVisible();

  await page.getByLabel('NPI', { exact: true }).fill(SEEDED.npi);
  await page.getByRole('button', { name: 'Search' }).click();

  const table = page.getByRole('table').filter({ has: page.getByRole('columnheader', { name: 'Risk' }) });
  const row = table.getByRole('row').filter({ hasText: SEEDED.npi });
  await expect(row).toHaveCount(1);
  const headers = (await table.getByRole('columnheader').allTextContents()).map((h) => h.trim());
  const cells = (await row.getByRole('cell').allTextContents()).map((c) => c.replace(/\s+/g, ' ').trim());
  const value = (header: string) => cells[headers.indexOf(header)];
  expect(value('Name')).toBe(SEEDED.name);
  expect(value('Status')).toBe(SEEDED.status);
  expect(value('Risk')).toBe(SEEDED.risk);

  await expectNoAxeViolations(page);
});
