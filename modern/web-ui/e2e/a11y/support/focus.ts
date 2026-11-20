// MAM-86 AC-4: press Tab through a whole page and read, at every stop, what the user would see: which element has the focus,
// whether it is interactive, whether it is visible, whether a focus indicator is drawn that was not there before the focus (the
// computed outline or box shadow of the element, or of the label tile of a radio button or check box whose native input is
// hidden by USWDS), and where it sits on the page. Nothing is assumed from a class name: every value is computed in the browser.
import type { Page } from '@playwright/test';

export interface Stop {
  /** 1 for the first element that got the focus. */
  index: number;
  /** Stable id of the element for this walk (the same element has the same id when the focus comes back to it). */
  id: number;
  tag: string;
  /** The kind of control, "" when the element is not interactive (see interactiveKind in the page code). */
  kind: string;
  name: string;
  /** Landmark the element is in: skip, gov-banner, header, nav, main, footer or other. */
  region: string;
  visible: boolean;
  /** 'self' when the element itself is what the user sees, 'label' when a hidden native input is shown by its label tile. */
  visibleVia: 'self' | 'label' | 'none';
  /** What is drawn on focus ("outline 4px solid rgb(36, 145, 255)" or "box-shadow ..."), "" when nothing is. */
  indicator: string;
  /** True when the indicator differs from the computed style before the element had the focus. */
  indicatorChanged: boolean;
  /** Where the indicator was read: the element, or the ::before of its label tile. */
  indicatorOn: 'self' | 'label';
  /** Box of what the user sees, in page coordinates (scroll added). */
  box: { left: number; top: number; width: number; height: number };
  /** Facts of the element for a report: tag and classes, role, aria-label, tabindex, client size and scroll size. */
  detail: string;
}

export interface Walk {
  stops: Stop[];
  /** Why the walk ended: 'wrapped' (the focus came back to the first stop), 'left-page' (body got the focus) or 'bound' (max presses). */
  ended: 'wrapped' | 'left-page' | 'bound';
  /** Visible interactive elements that no Tab press reached (one radio per group is enough). */
  unreached: string[];
}

/** Reset the focus to the top of the page and remember the computed focus-related style of every focusable element. */
async function prepare(page: Page): Promise<void> {
  await page.evaluate(() => {
    const active = document.activeElement as HTMLElement | null;
    if (active && active !== document.body) active.blur();
    window.scrollTo(0, 0);
    // Chrome starts the next Tab where the last click or focus was (a page on which a search button was pressed would start at the
    // results). Putting the focus on the body for a moment moves the starting point of the sequential navigation to the top of the page.
    const body = document.body;
    const tabindex = body.getAttribute('tabindex');
    body.setAttribute('tabindex', '-1');
    body.focus({ preventScroll: true });
    if (tabindex === null) body.removeAttribute('tabindex');
    else body.setAttribute('tabindex', tabindex);

    const snap = (node: Element, pseudo?: string) => {
      const s = getComputedStyle(node, pseudo);
      return `${s.outlineStyle}|${s.outlineWidth}|${s.outlineColor}|${s.outlineOffset}|${s.boxShadow}`;
    };
    const before = new WeakMap<Element, { self: string; label: string }>();
    for (const el of Array.from(document.querySelectorAll('a[href],button,input,select,textarea,summary,[tabindex],[role=button],[role=link]'))) {
      const label = (el as HTMLInputElement).labels?.[0];
      before.set(el, { self: snap(el), label: label ? `${snap(label, '::before')}~${snap(label)}` : '' });
    }
    (window as unknown as { __pemBefore: typeof before }).__pemBefore = before;
  });
}

/** Describe the element that has the focus now; null when it is the body (the focus left the page). Runs in the browser. */
async function describeActive(page: Page): Promise<Omit<Stop, 'index'> | null> {
  return page.evaluate(() => {
    const w = window as unknown as { __pemIds?: WeakMap<Element, number>; __pemNext?: number; __pemBefore?: WeakMap<Element, { self: string; label: string }> };
    if (!w.__pemIds) w.__pemIds = new WeakMap();
    if (!w.__pemNext) w.__pemNext = 1;
    const ids = w.__pemIds;
    const idOf = (node: Element) => {
      let id = ids.get(node);
      if (!id) {
        id = w.__pemNext as number;
        w.__pemNext = id + 1;
        ids.set(node, id);
      }
      return id;
    };
    const el = document.activeElement as HTMLElement | null;
    if (!el || el === document.body || el === document.documentElement) return null;

    const shown = (node: Element) => {
      const r = node.getBoundingClientRect();
      if (r.width < 2 || r.height < 2) return false;
      const s = getComputedStyle(node);
      if (s.visibility !== 'visible' || s.display === 'none' || Number(s.opacity) === 0) return false;
      if (s.clip === 'rect(0px, 0px, 0px, 0px)' || s.clipPath === 'inset(50%)') return false;
      return r.right > 0 && r.bottom > 0 && r.left < window.innerWidth && r.top < window.innerHeight;
    };
    const nameOf = (node: HTMLElement) => {
      const aria = node.getAttribute('aria-label');
      if (aria) return aria.trim();
      const by = node.getAttribute('aria-labelledby');
      if (by) {
        const text = by.split(/\s+/).map((id) => document.getElementById(id)?.textContent ?? '').join(' ').replace(/\s+/g, ' ').trim();
        if (text) return text;
      }
      const labels = (node as HTMLInputElement).labels;
      if (labels && labels.length) return Array.from(labels).map((l) => l.textContent ?? '').join(' ').replace(/\s+/g, ' ').trim();
      return (node.textContent ?? '').replace(/\s+/g, ' ').trim() || node.getAttribute('title') || node.getAttribute('value') || node.tagName.toLowerCase();
    };
    const interactiveKind = (node: HTMLElement) => {
      const tag = node.tagName.toLowerCase();
      const role = node.getAttribute('role') ?? '';
      if (tag === 'a' && node.hasAttribute('href')) return 'link';
      if (tag === 'button') return 'button';
      if (tag === 'input' && (node as HTMLInputElement).type !== 'hidden') return `input:${(node as HTMLInputElement).type}`;
      if (tag === 'select' || tag === 'textarea') return tag;
      if (tag === 'summary') return 'summary';
      if (['button', 'link', 'checkbox', 'radio', 'tab', 'menuitem', 'switch', 'combobox', 'textbox', 'searchbox', 'slider', 'spinbutton'].includes(role)) return `role:${role}`;
      if (node.isContentEditable) return 'editable';
      // a scrollable region with tabindex 0 is the keyboard way to scroll it (the axe rule scrollable-region-focusable asks for it)
      if (node.getAttribute('tabindex') === '0' && (node.scrollWidth > node.clientWidth || node.scrollHeight > node.clientHeight)) return 'scroll-region';
      return '';
    };
    const region = el.closest('.usa-skipnav')
      ? 'skip'
      : el.closest('main')
        ? 'main'
        : el.closest('.usa-banner')
          ? 'gov-banner'
          : el.closest('header, [role=banner]')
            ? 'header'
            : el.closest('nav, [role=navigation]')
              ? 'nav'
              : el.closest('footer, [role=contentinfo]')
                ? 'footer'
                : 'other';

    const label = (el as HTMLInputElement).labels?.[0] ?? null;
    let visibleVia: 'self' | 'label' | 'none' = 'none';
    if (shown(el)) visibleVia = 'self';
    else if (label && shown(label)) visibleVia = 'label';
    const seen = visibleVia === 'label' && label ? label : el;
    const rect = seen.getBoundingClientRect();

    const alphaOf = (color: string) => {
      if (color === 'transparent') return 0;
      const m = color.match(/rgba?\(([^)]+)\)/);
      if (!m) return 1;
      const parts = (m[1] ?? '').split(/[ ,/]+/).filter(Boolean);
      return parts.length > 3 ? Number(parts[3]) : 1;
    };
    const drawn = (node: Element, pseudo?: string) => {
      const s = getComputedStyle(node, pseudo);
      if (s.outlineStyle !== 'none' && s.outlineStyle !== 'hidden' && Number.parseFloat(s.outlineWidth) > 0 && alphaOf(s.outlineColor) > 0) {
        return `outline ${s.outlineWidth} ${s.outlineStyle} ${s.outlineColor}`;
      }
      if (s.boxShadow && s.boxShadow !== 'none') return `box-shadow ${s.boxShadow}`;
      return '';
    };
    const snap = (node: Element, pseudo?: string) => {
      const s = getComputedStyle(node, pseudo);
      return `${s.outlineStyle}|${s.outlineWidth}|${s.outlineColor}|${s.outlineOffset}|${s.boxShadow}`;
    };
    const before = w.__pemBefore?.get(el);
    // A native input that USWDS hides is not what the user sees: the indicator must then be on its label tile.
    const onLabel = visibleVia === 'label' && label !== null;
    const indicator = onLabel && label ? drawn(label, '::before') || drawn(label) : drawn(el);
    const now = onLabel && label ? `${snap(label, '::before')}~${snap(label)}` : snap(el);
    const was = onLabel ? before?.label : before?.self;
    // An element that was not on the page before the walk cannot be compared; a drawn indicator on it counts as new.
    const changed = indicator !== '' && (before === undefined || was !== now);
    return {
      id: idOf(el),
      tag: el.tagName.toLowerCase(),
      kind: interactiveKind(el),
      name: nameOf(el).slice(0, 80),
      region,
      visible: visibleVia !== 'none',
      visibleVia,
      indicator,
      indicatorChanged: changed,
      indicatorOn: onLabel ? ('label' as const) : ('self' as const),
      box: { left: rect.left + window.scrollX, top: rect.top + window.scrollY, width: rect.width, height: rect.height },
      detail:
        `${el.tagName.toLowerCase()}${typeof el.className === 'string' && el.className ? `.${el.className.trim().split(/\s+/).join('.')}` : ''} ` +
        `role=${el.getAttribute('role')} aria-label=${el.getAttribute('aria-label')} tabindex=${el.getAttribute('tabindex')} ` +
        `client=${el.clientWidth}x${el.clientHeight} scroll=${el.scrollWidth}x${el.scrollHeight}`,
    };
  });
}

/** Visible interactive elements of the page that are not among `reached` (ids from describeActive); radios count once per group. */
async function unreachedElements(page: Page, reached: number[]): Promise<string[]> {
  return page.evaluate((reachedIds) => {
    const w = window as unknown as { __pemIds?: WeakMap<Element, number> };
    const ids = w.__pemIds ?? new WeakMap<Element, number>();
    const done = new Set(reachedIds);
    const shown = (node: Element) => {
      const r = node.getBoundingClientRect();
      if (r.width < 2 || r.height < 2) return false;
      const s = getComputedStyle(node);
      return s.visibility === 'visible' && s.display !== 'none' && Number(s.opacity) !== 0;
    };
    const candidates = Array.from(
      document.querySelectorAll<HTMLElement>('a[href],button,input:not([type=hidden]),select,textarea,summary,[tabindex]:not([tabindex="-1"]),[role=button],[role=link]'),
    ).filter((el) => {
      if ((el as HTMLButtonElement).disabled) return false;
      if (el.closest('[inert],[hidden],[aria-hidden=true]')) return false;
      const label = (el as HTMLInputElement).labels?.[0];
      return shown(el) || (label ? shown(label) : false);
    });
    const groupKey = (el: HTMLElement) => `${(el as HTMLInputElement).form?.id ?? ''}|${(el as HTMLInputElement).name}`;
    const reachedGroups = new Set(
      candidates.filter((el) => (el as HTMLInputElement).type === 'radio' && done.has(ids.get(el) ?? -1)).map(groupKey),
    );
    return candidates
      .filter((el) => {
        if (done.has(ids.get(el) ?? -1)) return false;
        if ((el as HTMLInputElement).type === 'radio' && reachedGroups.has(groupKey(el))) return false;
        return true;
      })
      .map((el) => `${el.tagName.toLowerCase()} "${(el.textContent ?? el.getAttribute('aria-label') ?? '').replace(/\s+/g, ' ').trim().slice(0, 60)}"`);
  }, reached);
}

/** Press Tab from the top of the page until the focus comes back to the first stop, leaves the page, or `max` presses are used. */
export async function walkTabOrder(page: Page, max = 250): Promise<Walk> {
  await prepare(page);
  const stops: Stop[] = [];
  let ended: Walk['ended'] = 'bound';
  let outside = 0;
  for (let press = 0; press < max; press++) {
    await page.keyboard.press('Tab');
    let info = await describeActive(page);
    if (info && (!info.visible || info.indicator === '' || !info.indicatorChanged)) {
      // A skip link and the focus ring may still be in a CSS transition: read again once before judging.
      await page.waitForTimeout(350);
      info = await describeActive(page);
    }
    if (!info) {
      outside += 1;
      if (stops.length > 0 || outside > 2) {
        ended = 'left-page';
        break;
      }
      continue;
    }
    if (stops.length > 0 && info.id === stops[0]!.id) {
      ended = 'wrapped';
      break;
    }
    stops.push({ index: stops.length + 1, ...info });
  }
  return { stops, ended, unreached: await unreachedElements(page, stops.map((stop) => stop.id)) };
}

/** Reading-order tolerance, stated: two boxes are in the same visual row when their vertical extents overlap by at least half of the shorter one. */
const SAME_ROW_OVERLAP = 0.5;
/** Within a row the left edge may move back by at most this many pixels (sub-pixel layout, borders). */
const LEFT_TOLERANCE_PX = 2;
/** Pre-main regions, in the order a user meets them. */
const BEFORE_MAIN = new Set(['skip', 'gov-banner', 'header', 'nav']);

const say = (stop: Stop) => `#${stop.index} ${stop.kind || stop.tag} "${stop.name}" [${stop.region}]`;

/** Every way in which the walk breaks AC-4; an empty list means it holds. */
export function problemsOf(walk: Walk): string[] {
  const problems: string[] = [];
  const { stops } = walk;
  if (stops.length === 0) return ['no element could be reached with the Tab key'];
  if (walk.ended === 'bound') problems.push(`the focus never came back to the first element within ${stops.length} Tab presses (a keyboard trap, or too many stops)`);

  // The skip link comes first, and the header (and menu) before the page content.
  const first = stops[0]!;
  if (first.region !== 'skip') problems.push(`the first Tab stop is not the skip link: ${say(first)}`);
  const firstMain = stops.findIndex((stop) => stop.region === 'main');
  const lastBefore = stops.map((stop) => BEFORE_MAIN.has(stop.region)).lastIndexOf(true);
  if (firstMain >= 0 && lastBefore > firstMain) problems.push(`header or menu stops come after the page content: ${say(stops[lastBefore]!)} is after ${say(stops[firstMain]!)}`);
  const header = stops.filter((stop) => stop.region === 'header' || stop.region === 'nav');
  if (header.length === 0) problems.push('no header or menu element is reachable with the Tab key');

  for (const stop of stops) {
    if (stop.kind === '') problems.push(`${say(stop)} has the focus but is not an interactive element (${stop.detail})`);
    if (!stop.visible) problems.push(`${say(stop)} has the focus but cannot be seen`);
    if (stop.indicator === '') problems.push(`${say(stop)} shows no focus indicator (no outline, no box shadow)`);
    else if (!stop.indicatorChanged) problems.push(`${say(stop)} shows a focus indicator that is the same as before it had the focus: ${stop.indicator}`);
  }

  // Visual order: top to bottom, then left to right within a row.
  for (let i = 1; i < stops.length; i++) {
    const a = stops[i - 1]!;
    const b = stops[i]!;
    if (!a.visible || !b.visible) continue;
    const overlap = Math.min(a.box.top + a.box.height, b.box.top + b.box.height) - Math.max(a.box.top, b.box.top);
    const sameRow = overlap >= SAME_ROW_OVERLAP * Math.min(a.box.height, b.box.height);
    const aCenter = a.box.top + a.box.height / 2;
    const bCenter = b.box.top + b.box.height / 2;
    if (sameRow ? b.box.left < a.box.left - LEFT_TOLERANCE_PX : bCenter < aCenter) {
      problems.push(
        `focus order differs from the visual order: ${say(b)} (left ${Math.round(b.box.left)}, top ${Math.round(b.box.top)}) comes after ${say(a)} ` +
          `(left ${Math.round(a.box.left)}, top ${Math.round(a.box.top)}) but is ${sameRow ? 'to the left of it in the same row' : 'above it'}`,
      );
    }
  }
  for (const element of walk.unreached) problems.push(`the Tab key never reaches the interactive element ${element}`);
  return problems;
}
