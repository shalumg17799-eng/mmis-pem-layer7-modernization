import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';

test.describe('[MAM-19] accessibility scan in Playwright', () => {
  test('[MAM-19 AC-6] the home page has no axe violations', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    const result = await new AxeBuilder({ page }).analyze();
    expect(result.violations.map((v) => v.id)).toEqual([]);
  });

  test('[MAM-19 AC-6] an input without a label is reported, and the labelled one is not', async ({ page }) => {
    await page.setContent('<!doctype html><html lang="en"><head><title>t</title></head><body><main><h1>Form</h1><input id="a" type="text"></main></body></html>');
    const bad = await new AxeBuilder({ page }).analyze();
    expect(bad.violations.map((v) => v.id)).toContain('label');
    await page.setContent('<!doctype html><html lang="en"><head><title>t</title></head><body><main><h1>Form</h1><label for="a">NPI</label><input id="a" type="text"></main></body></html>');
    const good = await new AxeBuilder({ page }).analyze();
    expect(good.violations.map((v) => v.id)).not.toContain('label');
  });
});
