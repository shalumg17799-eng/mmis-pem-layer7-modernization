// MAM-86 AC-5 and AC-6: what the page is made of, read in the browser: the h1, the landmarks, the tables with their header cells,
// every input with its label (and, for an input in a table cell, the header cell of its column), and every status badge with its text.
import type { Page } from '@playwright/test';

/** One input, select or textarea of the page, with what the AC-6 label rule needs to decide (see structureProblems). */
export interface InputFact {
  id: string;
  type: string;
  /** Text of its label elements, shown or not. */
  label: string;
  /** True when at least one label element has text and is shown. */
  labelVisible: boolean;
  ariaLabel: string;
  /** Accessible name: the text of the aria-labelledby elements, else aria-label, else the text of the label elements ('' when none). */
  name: string;
  /** True when the input sits in a table cell (td or th). */
  inCell: boolean;
  /**
   * The header cell of the input's column, found in the thead of its table (else in the first row made only of th), by the column
   * grid of the table (colspan and rowspan counted). `text` is the text a sighted user can read in it. null: not in a table cell,
   * or the table has no header cell over that column.
   */
  header: { text: string; shown: boolean } | null;
}

export interface Facts {
  h1: string[];
  banners: number;
  mains: number;
  navigations: string[];
  tables: Array<{ name: string; headerCells: number; emptyHeaderCells: number; dataCells: number }>;
  inputs: InputFact[];
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
    // The text of an element that a sighted user can read: text nodes whose own element and every element up to `root` are shown.
    const visibleText = (root: Element): string => {
      const parts: string[] = [];
      const walk = (node: Node): void => {
        if (node.nodeType === Node.TEXT_NODE) parts.push(node.textContent ?? '');
        else if (node.nodeType === Node.ELEMENT_NODE && (node === root || shown(node as Element))) node.childNodes.forEach(walk);
      };
      walk(root);
      return clean(parts.join(''));
    };
    // The accessible name of an input, by the order of the accessible name computation: aria-labelledby, aria-label, label elements.
    const nameOf = (node: HTMLInputElement): string => {
      const labelledBy = (node.getAttribute('aria-labelledby') ?? '')
        .split(/\s+/)
        .filter(Boolean)
        .map((id) => clean(document.getElementById(id)?.textContent))
        .filter(Boolean)
        .join(' ');
      if (labelledBy) return labelledBy;
      const aria = clean(node.getAttribute('aria-label'));
      if (aria) return aria;
      return Array.from(node.labels ?? [])
        .map((label) => clean(label.textContent))
        .filter(Boolean)
        .join(' ');
    };
    // The first column of every cell of a table, counted over the whole grid: colspan widens a cell, and rowspan keeps its columns
    // taken in the rows below (so a cell is not placed by its position in its row alone).
    const gridOf = (table: HTMLTableElement): Map<HTMLTableCellElement, number> => {
      const starts = new Map<HTMLTableCellElement, number>();
      const taken: number[][] = [];
      Array.from(table.rows).forEach((row, rowIndex) => {
        let column = 0;
        for (const cell of Array.from(row.cells)) {
          while (taken[rowIndex]?.includes(column)) column += 1;
          starts.set(cell, column);
          for (let down = 0; down < Math.max(1, cell.rowSpan); down += 1) {
            for (let across = 0; across < Math.max(1, cell.colSpan); across += 1) (taken[rowIndex + down] ??= []).push(column + across);
          }
          column += Math.max(1, cell.colSpan);
        }
      });
      return starts;
    };
    const grids = new Map<HTMLTableElement, Map<HTMLTableCellElement, number>>();
    // The header cell over the column of `cell`: the lowest header cell of the thead (or of the first row made only of th) that covers it.
    const headerOf = (cell: HTMLTableCellElement): { text: string; shown: boolean } | null => {
      const table = cell.closest('table');
      if (!table) return null;
      const starts = grids.get(table) ?? gridOf(table);
      grids.set(table, starts);
      const column = starts.get(cell);
      if (column === undefined) return null;
      const startOf = (candidate: HTMLTableCellElement) => starts.get(candidate) ?? Number.POSITIVE_INFINITY;
      const headingRow = Array.from(table.rows).find((row) => row.cells.length > 0 && Array.from(row.cells).every((each) => each.tagName === 'TH'));
      const headerRows = table.tHead !== null && table.tHead.rows.length > 0 ? Array.from(table.tHead.rows) : headingRow ? [headingRow] : [];
      const covering = headerRows
        .flatMap((row) => Array.from(row.cells))
        .filter((candidate) => candidate !== cell && startOf(candidate) <= column && column < startOf(candidate) + Math.max(1, candidate.colSpan));
      const header = covering[covering.length - 1];
      if (!header) return null;
      const isShown = shown(header);
      return { text: isShown ? visibleText(header) : clean(header.textContent), shown: isShown };
    };
    const skipped = ['hidden', 'submit', 'button', 'reset', 'image'];
    const inputs = Array.from(document.querySelectorAll<HTMLInputElement>('input, select, textarea'))
      .filter((node) => !skipped.includes(node.type))
      .filter((node) => shown(node) || Array.from(node.labels ?? []).some(shown))
      .map((node): InputFact => {
        const labels = Array.from(node.labels ?? []);
        const cell = node.closest<HTMLTableCellElement>('td, th');
        return {
          id: node.id || node.name || '(no id)',
          type: node.type || node.tagName.toLowerCase(),
          label: labels.map((label) => clean(label.textContent)).filter(Boolean).join(' / '),
          labelVisible: labels.some((label) => clean(label.textContent) !== '' && shown(label)),
          ariaLabel: node.getAttribute('aria-label') ?? '',
          name: nameOf(node),
          inCell: cell !== null,
          header: cell === null ? null : headerOf(cell),
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

/**
 * Case (b) of the AC-6 label rule: why the input is NOT labelled by the visible header of its column ('' when it is). The input must
 * sit in a table cell, the header cell of its column must be shown and have visible text, and that text (trimmed, case kept) must be
 * part of the input's accessible name.
 */
function columnHeaderGap(input: InputFact): string {
  if (!input.inCell) return 'it is not in a table cell, so no column header can label it';
  if (input.header === null) return 'its table has no header cell over its column';
  if (!input.header.shown) return `the header cell of its column ("${input.header.text}") is not shown`;
  if (input.header.text === '') return 'the header cell of its column has no visible text';
  if (input.name === '') return `it has no accessible name, so it does not contain the visible header text "${input.header.text}" of its column`;
  if (!input.name.includes(input.header.text)) return `its accessible name "${input.name}" does not contain the visible header text "${input.header.text}" of its column`;
  return '';
}

/** Case (a) of the AC-6 label rule is `labelVisible`; this says why it is not met. */
function labelElementGap(input: InputFact): string {
  if (input.label && input.ariaLabel) return `its label element "${input.label}" is not shown, and it has aria-label "${input.ariaLabel}"`;
  if (input.label) return `its label element "${input.label}" is not shown`;
  if (input.ariaLabel) return `it is named only by aria-label "${input.ariaLabel}"`;
  if (input.name) return `it is named only by aria-labelledby "${input.name}"`;
  return 'it has no label element and no name';
}

/**
 * AC-6: exactly one h1, a banner, a main and a navigation, header cells in every table, and a visible label on every input.
 * An input counts as labelled when (a) it has a visible label element, OR (b) (decision Q-137 A, WCAG 2.1 3.3.2 Labels or
 * Instructions, 2.5.3 Label in Name, 1.3.1 Info and Relationships) it sits in a table cell, the header cell of its column is shown and
 * has text, and that text is contained in the input's accessible name. An input that meets neither is a problem, and the message says
 * what each of the two misses.
 */
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
    if (input.labelVisible) continue; // case (a)
    const gap = columnHeaderGap(input);
    if (gap === '') continue; // case (b)
    problems.push(
      `input "${input.id}" (${input.type}) is not labelled for a sighted user: it meets neither (a) a visible label element (${labelElementGap(input)}) ` +
        `nor (b) a visible column header whose text is in its accessible name (${gap})`,
    );
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
