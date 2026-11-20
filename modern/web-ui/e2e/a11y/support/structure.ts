// MAM-86 AC-5 and AC-6: what the page is made of, read in the browser: the h1, the landmarks, the tables with their header cells,
// every input with its label, and every status badge with its text.
import type { Page } from '@playwright/test';

export interface Facts {
  h1: string[];
  banners: number;
  mains: number;
  navigations: string[];
  tables: Array<{ name: string; headerCells: number; emptyHeaderCells: number; dataCells: number }>;
  inputs: Array<{ id: string; type: string; label: string; labelVisible: boolean; ariaLabel: string }>;
  /** Text of every badge (USWDS tag) that is shown. */
  badges: string[];
  /** Cells, list values and tags whose whole text is a status word with no code ("Suspended" instead of "Suspended (S)"). */
  bareStatusWords: string[];
}

export async function readFacts(page: Page): Promise<Facts> {
  const [banners, mains, navigations] = await Promise.all([
    page.getByRole('banner').count(),
    page.getByRole('main').count(),
    page.getByRole('navigation').evaluateAll((elements) => elements.map((element) => element.getAttribute('aria-label') ?? '(no name)')),
  ]);
  const inPage = await page.evaluate(() => {
    const clean = (text: string | null | undefined) => (text ?? '').replace(/\s+/g, ' ').trim();
    const shown = (node: Element) => {
      const r = node.getBoundingClientRect();
      if (r.width < 2 || r.height < 2) return false;
      const s = getComputedStyle(node);
      return s.visibility === 'visible' && s.display !== 'none' && Number(s.opacity) !== 0 && s.clip !== 'rect(0px, 0px, 0px, 0px)';
    };
    const h1 = Array.from(document.querySelectorAll('h1')).filter(shown).map((node) => clean(node.textContent));
    const tables = Array.from(document.querySelectorAll('table'))
      .filter(shown)
      .map((table) => {
        const headers = Array.from(table.querySelectorAll('th'));
        return {
          name: clean(table.querySelector('caption')?.textContent) || table.getAttribute('aria-label') || '(no name)',
          headerCells: headers.length,
          emptyHeaderCells: headers.filter((th) => clean(th.textContent) === '' && !th.getAttribute('aria-label')).length,
          dataCells: table.querySelectorAll('td').length,
        };
      });
    const skipped = ['hidden', 'submit', 'button', 'reset', 'image'];
    const inputs = Array.from(document.querySelectorAll<HTMLInputElement>('input, select, textarea'))
      .filter((node) => !skipped.includes(node.type))
      .filter((node) => shown(node) || Array.from(node.labels ?? []).some(shown))
      .map((node) => {
        const labels = Array.from(node.labels ?? []);
        return {
          id: node.id || node.name || '(no id)',
          type: node.type || node.tagName.toLowerCase(),
          label: labels.map((label) => clean(label.textContent)).filter(Boolean).join(' / '),
          labelVisible: labels.some((label) => clean(label.textContent) !== '' && shown(label)),
          ariaLabel: node.getAttribute('aria-label') ?? '',
        };
      });
    const badges = Array.from(document.querySelectorAll('.usa-tag')).filter(shown).map((node) => clean(node.textContent));
    const STATUS_WORD = /^(Pending|Active|Terminated|Denied|Suspended|Unscreened|Limited|Moderate|High|Pass|Fail)$/;
    const bareStatusWords = Array.from(document.querySelectorAll('td, dd, li, .usa-tag, strong, span'))
      .filter((node) => node.children.length === 0 && shown(node))
      .map((node) => clean(node.textContent))
      .filter((text) => STATUS_WORD.test(text));
    return { h1, tables, inputs, badges, bareStatusWords };
  });
  return { banners, mains, navigations, ...inPage };
}

/** AC-6: exactly one h1, a banner, a main and a navigation, header cells in every table, a visible label on every input. */
export function structureProblems(facts: Facts): string[] {
  const problems: string[] = [];
  if (facts.h1.length !== 1) problems.push(`expected exactly one h1, found ${facts.h1.length}: ${JSON.stringify(facts.h1)}`);
  if (facts.banners !== 1) problems.push(`expected exactly one banner landmark, found ${facts.banners}`);
  if (facts.mains !== 1) problems.push(`expected exactly one main landmark, found ${facts.mains}`);
  if (facts.navigations.length === 0) problems.push('expected a navigation landmark (the layout has the menu), found none');
  for (const table of facts.tables) {
    if (table.headerCells === 0) problems.push(`table "${table.name}" has no header cells (th)`);
    if (table.emptyHeaderCells > 0) problems.push(`table "${table.name}" has ${table.emptyHeaderCells} header cell(s) with no text`);
  }
  for (const input of facts.inputs) {
    if (!input.labelVisible) {
      const named = input.ariaLabel ? ` (it is named only by aria-label "${input.ariaLabel}")` : input.label ? ` (its label text is "${input.label}" but it is not shown)` : '';
      problems.push(`input "${input.id}" (${input.type}) has no visible label element${named}`);
    }
  }
  return problems;
}

/** The words of the legacy codes and their letters (D-006, frontend-stack section 6). */
export const CODES: Record<string, string> = {
  Pending: 'P', Active: 'A', Terminated: 'T', Denied: 'D', Suspended: 'S', Unscreened: 'U', Pass: 'P', Fail: 'F', Limited: 'L', Moderate: 'M', High: 'H',
};

/** AC-5: every badge has text; a badge that names a legacy code word carries its letter; no status is a bare word. */
export function badgeProblems(facts: Facts): string[] {
  const problems: string[] = [];
  for (const text of facts.badges) {
    if (text === '') {
      problems.push('a status badge has no text (colour alone)');
      continue;
    }
    const word = /^([A-Za-z]+)/.exec(text)?.[1] ?? '';
    const letter = CODES[word];
    if (letter !== undefined && text !== `${word} (${letter})`) problems.push(`badge "${text}" should read "${word} (${letter})"`);
  }
  for (const text of facts.bareStatusWords) problems.push(`status shown as the bare word "${text}" without its code, for example "${text} (${CODES[text] ?? '?'})"`);
  return problems;
}
