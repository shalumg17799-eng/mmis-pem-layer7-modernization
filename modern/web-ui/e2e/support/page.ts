import AxeBuilder from '@axe-core/playwright';
import { expect, type Locator, type Page } from '@playwright/test';

/** Links of the main menu landmark, named "Primary navigation" (a breadcrumb nav is not the menu; Q-101): [visible text, path]; the sign-out control is not a menu entry. */
export async function menuEntries(page: Page): Promise<Array<[string, string]>> {
  const links = await page.getByRole('navigation', { name: 'Primary navigation' }).getByRole('link').all();
  const entries: Array<[string, string]> = [];
  for (const link of links) {
    const name = ((await link.textContent()) ?? '').replace(/\s+/g, ' ').trim();
    if (/sign out/i.test(name)) continue;
    entries.push([name, new URL((await link.getAttribute('href')) ?? '', page.url()).pathname]);
  }
  return entries.sort((a, b) => a[1].localeCompare(b[1]));
}

export async function expectNoAxeViolations(page: Page): Promise<void> {
  const result = await new AxeBuilder({ page }).analyze();
  expect(result.violations.map((v) => `${v.id} (${v.nodes.length})`)).toEqual([]);
}

/** One h1 and the landmarks header (banner), nav and main. */
export async function expectShellStructure(page: Page): Promise<void> {
  await expect(page.getByRole('heading', { level: 1 })).toHaveCount(1);
  await expect(page.getByRole('banner')).toHaveCount(1);
  await expect(page.getByRole('navigation')).not.toHaveCount(0);
  await expect(page.getByRole('main')).toHaveCount(1);
}

export async function isFocused(locator: Locator): Promise<boolean> {
  return locator.evaluate((element) => element === document.activeElement);
}

/** Press Tab until `target` has the focus; returns the visible names of the focus stops passed on the way. */
export async function tabTo(page: Page, target: Locator, max = 60): Promise<string[]> {
  const stops: string[] = [];
  for (let i = 0; i < max; i++) {
    await page.keyboard.press('Tab');
    stops.push(await focusedName(page));
    if (await isFocused(target)) return stops;
  }
  throw new Error(`the target never got the focus within ${max} Tab presses; stops: ${stops.join(' | ')}`);
}

export async function focusedName(page: Page): Promise<string> {
  return page.evaluate(() => {
    const element = document.activeElement;
    if (!element || element === document.body) return '(body)';
    return (element.textContent ?? '').replace(/\s+/g, ' ').trim() || element.getAttribute('aria-label') || element.tagName;
  });
}

/** True when the focused element has an outline or a box shadow (focus is always visible). */
export async function focusIsVisible(page: Page): Promise<boolean> {
  return page.evaluate(() => {
    const element = document.activeElement;
    if (!element || element === document.body) return false;
    const style = getComputedStyle(element);
    const outline = style.outlineStyle !== 'none' && Number.parseFloat(style.outlineWidth) > 0;
    return outline || style.boxShadow !== 'none';
  });
}
