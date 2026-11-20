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
    // --- What hides an element, or a text, from a sighted user (the pieces of `seen` and `visibleText` below) ---------------------------
    // The alpha of a computed colour: rgba(r, g, b, a), or a colour function with "/ a" (rgb, color, lab, oklch). 1 when there is none.
    const alphaOf = (color: string): number => {
      const found = /\/\s*([\d.]+)(%?)\s*\)\s*$/.exec(color) ?? /^rgba\(\s*[^,]+,[^,]+,[^,]+,\s*([\d.]+)(%?)\s*\)$/.exec(color);
      if (found === null) return 1;
      const alpha = Number(found[1]) / (found[2] ? 100 : 1);
      return Number.isNaN(alpha) ? 1 : alpha;
    };
    // True when a computed `filter` holds opacity(0), opacity(0%) or opacity(.0); opacity(0.5) does not count.
    const filterHides = (filter: string): boolean => (filter.match(/opacity\(\s*[\d.]+%?\s*\)/g) ?? []).some((each) => Number.parseFloat(each.slice('opacity('.length)) === 0);
    // The legacy `clip` property (it works on an absolutely or fixed positioned box only): true when its rectangle is under 2 by 2 pixels.
    const clipRectEmpty = (s: CSSStyleDeclaration): boolean => {
      if (s.position !== 'absolute' && s.position !== 'fixed') return false;
      const m = /^rect\(\s*([^,\s]+)[,\s]+([^,\s]+)[,\s]+([^,\s]+)[,\s]+([^,\s)]+)\s*\)$/.exec(s.clip);
      if (m === null) return false;
      const [top, right, bottom, left] = [1, 2, 3, 4].map((index) => Number.parseFloat(m[index] ?? 'auto')); // 'auto' is NaN: no comparison is true
      return Number(right) - Number(left) < 2 || Number(bottom) - Number(top) < 2;
    };
    // What takes an element and all its descendants out of sight, whatever their own style: opacity 0, filter opacity(0), any clip-path
    // (even a decorative one: it errs on the side of reporting), a clip rectangle under 2 by 2 pixels.
    const hidesSubtree = (s: CSSStyleDeclaration): boolean => Number(s.opacity) === 0 || (s.clipPath !== 'none' && s.clipPath !== '') || filterHides(s.filter) || clipRectEmpty(s);
    // A box that is the containing block of an absolutely positioned descendant (a positioned box, or one with a transform, filter,
    // perspective or layout/paint containment), and one that is the containing block of a fixed one (the same, without `position`).
    const anchorsFixed = (s: CSSStyleDeclaration): boolean =>
      ['transform', 'translate', 'rotate', 'scale', 'perspective', 'filter'].some((name) => !['', 'none'].includes(s.getPropertyValue(name))) || /\b(layout|paint|strict|content)\b/.test(s.getPropertyValue('contain'));
    // Boxes to which `overflow` does not apply (an inline box, a table, a row or a column): they clip nothing.
    const NO_OVERFLOW = /^(inline|contents|table|inline-table|table-(row|row-group|header-group|footer-group|column|column-group))$/;
    // Is the part of `node` that the ancestors leave visible smaller than 2 by 2 pixels? Walks the containing blocks of the box (an
    // absolutely positioned box is not clipped by a wrapper that is not its containing block). An ancestor with overflow hidden or clip
    // cuts the box on that axis. An ancestor with overflow auto or scroll is a scroll container: its content can be scrolled to, as
    // with a box far right of the page, so only its own size matters (under 2 pixels shows nothing). The html and body elements are
    // left out: their overflow belongs to the page.
    const clippedAway = (node: Element): boolean => {
      const own = node.getBoundingClientRect();
      let left = own.left;
      let right = own.right;
      let top = own.top;
      let bottom = own.bottom;
      let placed = getComputedStyle(node).position; // how the box that is followed up the tree is placed
      for (let up = node.parentElement; up !== null; up = up.parentElement) {
        const s = getComputedStyle(up);
        const isContainingBlock = placed === 'absolute' ? s.position !== 'static' || anchorsFixed(s) : placed === 'fixed' ? anchorsFixed(s) : true;
        if (!isContainingBlock) continue;
        placed = s.position;
        if (NO_OVERFLOW.test(s.display)) continue;
        const box = up.getBoundingClientRect(); // overflow clips at the padding box: the border box less the borders
        const x0 = box.left + (Number.parseFloat(s.borderLeftWidth) || 0);
        const x1 = box.right - (Number.parseFloat(s.borderRightWidth) || 0);
        const y0 = box.top + (Number.parseFloat(s.borderTopWidth) || 0);
        const y1 = box.bottom - (Number.parseFloat(s.borderBottomWidth) || 0);
        if (s.overflowX === 'hidden' || s.overflowX === 'clip') {
          left = Math.max(left, x0);
          right = Math.min(right, x1);
        } else if (s.overflowX !== 'visible' && x1 - x0 < 2) return true;
        if (s.overflowY === 'hidden' || s.overflowY === 'clip') {
          top = Math.max(top, y0);
          bottom = Math.min(bottom, y1);
        } else if (s.overflowY !== 'visible' && y1 - y0 < 2) return true;
      }
      return right - left < 2 || bottom - top < 2;
    };
    // Can a sighted user see this element? Stricter than `shown`, which reads only the element's own style and size. On top of it the
    // element is NOT seen when:
    //  - it or an ancestor has opacity 0, a filter with opacity(0), a clip-path other than none, or a clip rectangle under 2 by 2 pixels
    //    (opacity is not inherited, so the child of a transparent wrapper has opacity 1 and a box, and is still invisible);
    //    `checkVisibility` is asked as well (display none or content-visibility on an ancestor, the visibility of the element itself);
    //  - the part of its box that the ancestors that clip it leave is under 2 by 2 pixels (see `clippedAway`);
    //  - its box is wholly off the page: its right edge at or left of the page origin, or its bottom edge at or above it (the USWDS
    //    class usa-sr-only moves a box 999em to the left; a box there cannot be scrolled to). A box that merely starts left of the edge
    //    but reaches into the page is seen.
    // What the check does not detect is listed in the accessibility report (section "Page structure (AC-6)"), word for word.
    const seen = (node: Element): boolean => {
      if (!shown(node)) return false;
      for (let each: Element | null = node; each !== null; each = each.parentElement) {
        if (hidesSubtree(getComputedStyle(each))) return false;
      }
      const options: Record<string, boolean> = { opacityProperty: true, visibilityProperty: true, checkOpacity: true, checkVisibilityCSS: true };
      const check = (node as HTMLElement).checkVisibility;
      if (typeof check === 'function' && !check.call(node, options)) return false;
      const r = node.getBoundingClientRect();
      if (r.right + window.scrollX <= 0 || r.bottom + window.scrollY <= 0) return false;
      return !clippedAway(node);
    };
    // Controls inside a label or a header cell: what they hold (the text of a textarea, the options of a select, a button's caption) is
    // their content, not the text of the label, so it is not read.
    const CONTROLS = ['TEXTAREA', 'SELECT', 'OPTION', 'INPUT', 'BUTTON'];
    // A text can be read only when its own element has a font size above 0 and a colour with an alpha above 0.
    const readable = (text: Node): boolean => {
      const owner = text.parentElement;
      if (owner === null) return false;
      const s = getComputedStyle(owner);
      return Number.parseFloat(s.fontSize) > 0 && alphaOf(s.color) > 0;
    };
    // The text of an element that a sighted user can read: text nodes that are readable, whose own element and every element up to
    // `root` are seen, and that are not inside a control.
    const visibleText = (root: Element): string => {
      const parts: string[] = [];
      const walk = (node: Node): void => {
        if (node.nodeType === Node.TEXT_NODE) {
          if (readable(node)) parts.push(node.textContent ?? '');
        } else if (node.nodeType === Node.ELEMENT_NODE && !CONTROLS.includes((node as Element).tagName) && (node === root || seen(node as Element))) node.childNodes.forEach(walk);
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
      const isSeen = seen(header);
      return { text: isSeen ? visibleText(header) : clean(header.textContent), shown: isSeen };
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
          labelVisible: labels.some((label) => seen(label) && visibleText(label) !== ''),
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
