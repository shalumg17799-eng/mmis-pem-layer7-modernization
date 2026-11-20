// MAM-86 AC-3: what a person who uses only the keyboard does, as small helpers for keyboard-journeys.spec.ts. Everything goes in
// through page.keyboard (Tab, Shift+Tab, Enter, Space, the arrow keys, typed text). There is no mouse call, no fill, no check, no
// selectOption, no dispatchEvent and no programmatic focus in this file or in the journeys; policy.a11y.spec.ts checks that.
// A target is reached by pressing Tab until it has the focus (bounded); when it cannot be reached the error names the target and
// lists the elements the focus passed, so a keyboard trap or a missing control is a failing test, not a hang.
import { expect, type Locator, type Page, type Response } from '@playwright/test';
import { KEYCLOAK_AUTHORIZE, passwordOf, type Role } from './sessions';

const REACH_TIMEOUT = 30_000;

/** Short description of the element that has the focus now, for messages. */
export async function describeFocus(page: Page): Promise<string> {
  return page.evaluate(() => {
    const element = document.activeElement as HTMLElement | null;
    if (!element || element === document.body) return '(page)';
    const label = (element as HTMLInputElement).labels?.[0]?.textContent;
    const text = element.getAttribute('aria-label') ?? label ?? element.textContent ?? '';
    return `${element.tagName.toLowerCase()} "${text.replace(/\s+/g, ' ').trim().slice(0, 50)}"`;
  });
}

async function hasFocus(target: Locator): Promise<boolean> {
  return target.evaluate((element) => element === document.activeElement, undefined, { timeout: REACH_TIMEOUT });
}

/**
 * Press Tab (Shift+Tab when the target is above the element that has the focus) until `target` has the focus. Returns the elements passed on the way. Fails, naming `what`, when the
 * target is not on the page or is not reached within `max` presses.
 */
export async function tabTo(page: Page, target: Locator, what: string, options: { max?: number; backwards?: boolean } = {}): Promise<string[]> {
  const max = options.max ?? 120;
  await expect(target, `keyboard: "${what}" is on the page`).toHaveCount(1, { timeout: REACH_TIMEOUT });
  const passed: string[] = [];
  if (await hasFocus(target)) return passed;
  // Tab order is source order: a target above the element that has the focus is reached faster with Shift+Tab, as a person would.
  const above = await target.evaluate(
    (element) => {
      const active = document.activeElement;
      return active !== null && active !== document.body && (element.compareDocumentPosition(active) & Node.DOCUMENT_POSITION_FOLLOWING) !== 0;
    },
    undefined,
    { timeout: REACH_TIMEOUT },
  );
  const key = options.backwards ?? above ? 'Shift+Tab' : 'Tab';
  for (let press = 0; press < max; press++) {
    await page.keyboard.press(key);
    passed.push(await describeFocus(page));
    if (await hasFocus(target)) return passed;
  }
  throw new Error(`keyboard: "${what}" could not be reached with ${max} ${key} presses; the focus passed: ${passed.join(' | ')}`);
}

/** Tab to `target` and press Enter (a link or a button). */
export async function tabAndEnter(page: Page, target: Locator, what: string): Promise<void> {
  await tabTo(page, target, what);
  await page.keyboard.press('Enter');
}

/** Tab to `target` and press the space bar (a button, a check box or a radio button). */
export async function tabAndSpace(page: Page, target: Locator, what: string): Promise<void> {
  await tabTo(page, target, what);
  await page.keyboard.press('Space');
}

/** Tab to a text field and type `text` in place of what it holds (select all, then type); checks the value that results. */
export async function typeInto(page: Page, field: Locator, what: string, text: string): Promise<void> {
  await tabTo(page, field, what);
  await page.keyboard.press('Control+A');
  if (text === '') await page.keyboard.press('Backspace');
  else await page.keyboard.type(text);
  await expect(field, `keyboard: "${what}" holds what was typed`).toHaveValue(text);
}

/** Tab to a drop-down list and choose the option with the text `optionText` with the arrow keys (Down or Up); checks what is selected. */
export async function chooseOption(page: Page, select: Locator, what: string, optionText: string): Promise<void> {
  await tabTo(page, select, what);
  const texts = await select.evaluate((element) => Array.from((element as HTMLSelectElement).options).map((option) => (option.textContent ?? '').trim()));
  const wanted = texts.indexOf(optionText);
  expect(wanted, `keyboard: "${what}" offers ${optionText} (it offers ${texts.join(', ')})`).toBeGreaterThanOrEqual(0);
  const selectedIndex = () => select.evaluate((element) => (element as HTMLSelectElement).selectedIndex);
  for (let press = 0; press < texts.length; press++) {
    const index = await selectedIndex();
    if (index === wanted) break;
    await page.keyboard.press(index < wanted ? 'ArrowDown' : 'ArrowUp');
  }
  expect(await selectedIndex(), `keyboard: "${what}" shows ${optionText}`).toBe(wanted);
}

/** Tab into a group of radio buttons and move to `target` with the arrow keys (or the space bar); checks that it is the chosen one. */
export async function chooseRadio(page: Page, group: Locator, target: Locator, what: string): Promise<void> {
  await expect(target, `keyboard: "${what}" is on the page`).toHaveCount(1, { timeout: REACH_TIMEOUT });
  const inGroup = () => group.evaluateAll((elements) => elements.some((element) => element === document.activeElement));
  for (let press = 0; press < 120 && !(await inGroup()); press++) await page.keyboard.press('Tab');
  expect(await inGroup(), `keyboard: the group of "${what}" could not be reached with Tab`).toBe(true);
  for (let press = 0; press < 6 && !(await hasFocus(target)); press++) await page.keyboard.press('ArrowDown');
  expect(await hasFocus(target), `keyboard: "${what}" could not be reached with the arrow keys`).toBe(true);
  if (!(await target.isChecked())) await page.keyboard.press('Space');
  await expect(target, `keyboard: "${what}" is chosen`).toBeChecked();
}

/** Press `key` (Enter by default) and wait for the response of the request that it sends. */
export async function pressAndWait(page: Page, match: (response: Response) => boolean, key = 'Enter'): Promise<Response> {
  const [response] = await Promise.all([page.waitForResponse(match, { timeout: 60_000 }), page.keyboard.press(key)]);
  return response;
}

/** Press Enter on the control that is focused and take the file that the browser saves. */
export async function pressAndDownload(page: Page, key = 'Enter') {
  const [download] = await Promise.all([page.waitForEvent('download', { timeout: 60_000 }), page.keyboard.press(key)]);
  return download;
}

/** An entry of the menu (the navigation landmark "Primary navigation"): Tab to it, Enter, and wait for the page's h1. */
export async function openMenuEntry(page: Page, label: string, h1: string): Promise<void> {
  const entry = page.getByRole('navigation', { name: 'Primary navigation' }).getByRole('link', { name: label, exact: true });
  await tabAndEnter(page, entry, `menu entry ${label}`);
  await expect(page.getByRole('heading', { level: 1, name: h1 }), `keyboard: the menu entry ${label} opens ${h1}`).toBeVisible({ timeout: 30_000 });
}

/**
 * Open `path` (typing an address is the one thing done before the page has the focus), complete the Keycloak form with the keyboard
 * and wait until the app is shown. The password is typed, never printed.
 */
export async function signInWithKeyboard(page: Page, user: Role, path: string): Promise<void> {
  await page.goto(path, { waitUntil: 'commit' });
  await page.waitForURL(KEYCLOAK_AUTHORIZE, { timeout: 30_000 });
  await expect(page.locator('#username')).toBeVisible({ timeout: 30_000 });
  await tabTo(page, page.locator('#username'), 'the user name of the sign-in form');
  await page.keyboard.type(user);
  await tabTo(page, page.locator('#password'), 'the password of the sign-in form');
  try {
    await page.keyboard.type(passwordOf(user));
  } catch {
    throw new Error(`keyboard: typing the password of ${user} failed`);
  }
  await page.keyboard.press('Enter');
  await page.waitForURL((url) => !url.pathname.startsWith('/realms/pem/'), { timeout: 60_000 });
}
