// MAM-86: the one accessibility checker of the page scans. Every scan of this story, and the gate test of AC-7, goes through
// expectNoViolations(); it throws when axe reports a violation, and the message names the page (the screen id of the inventory,
// the URL) and the rule id, so a failing run says where and what.
//
// Policy (checked by policy.a11y.spec.ts for every file under modern/web-ui/e2e): no axe rule is switched off, no part of a page is
// left out of a scan, and no scan is narrowed to a subset of rules without a written justification next to the call (a comment
// that starts with the marker JUSTIFICATION_MARKER, in policy.a11y.spec.ts). The only option set here is the list of tags of AC-1.
import AxeBuilder from '@axe-core/playwright';
import type { Page } from '@playwright/test';

/** WCAG 2.0 and 2.1, levels A and AA (AC-1). */
export const AXE_TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'];

type AxeResults = Awaited<ReturnType<AxeBuilder['analyze']>>;

export interface Scan {
  screen: string;
  url: string;
  violations: AxeResults['violations'];
  /** Ids of the rules that found at least one element to check (passed, failed or needs review). */
  ran: string[];
  /** Elements of the colour-contrast rule that passed: proof that the rule looked at text on this page. */
  contrastPassed: number;
  /** Elements the colour-contrast rule could not decide (for example text over an image); reported, not hidden. */
  contrastUndecided: number;
  /** Those elements with the reason axe gives and the ratio measured here from the computed colours: "selector (reason): 12.6:1 (needs 4.5:1)". */
  contrastUndecidedTargets: string[];
  /** The measurement behind contrastUndecidedTargets; `ok` is false when the ratio is below the WCAG limit for the text size. */
  contrastUndecidedMeasured: MeasuredText[];
}

export interface MeasuredText {
  target: string;
  reason: string;
  ratio: number;
  required: number;
  ok: boolean;
}

/** "image-alt (critical, 1 element): Images must have alternative text: img.pem-x" for each violation, one per line. */
export function describeViolations(violations: AxeResults['violations']): string {
  return violations
    .map((violation) => {
      const targets = violation.nodes
        .slice(0, 4)
        .map((node) => node.target.join(' '))
        .join(' | ');
      const more = violation.nodes.length > 4 ? ` (+${violation.nodes.length - 4} more)` : '';
      return `  - ${violation.id} (${violation.impact ?? 'n/a'}, ${violation.nodes.length} element${violation.nodes.length === 1 ? '' : 's'}): ${violation.help}: ${targets}${more}`;
    })
    .join('\n');
}

/**
 * Axe cannot decide the contrast of text over a gradient or a pseudo-element (rule outcome "needs review"). Those elements are not
 * left unchecked: the ratio is computed here from the computed text colour and the first opaque background behind the element
 * (WCAG 2.1 relative luminance), against 4.5:1, or 3:1 for large text (24 px, or 18.66 px and bold). A background image is ignored,
 * which is right for a drop-down list whose image is only its arrow and for the marker bar of the current menu entry.
 */
async function measureUndecided(page: Page, nodes: Array<{ target: string; reason: string }>): Promise<MeasuredText[]> {
  return page.evaluate((list) => {
    const parse = (color: string): number[] => {
      const match = color.match(/rgba?\(([^)]+)\)/);
      if (!match) return [NaN, NaN, NaN, 1];
      const parts = (match[1] ?? '').split(/[ ,/]+/).filter(Boolean).map(Number);
      return [parts[0] ?? NaN, parts[1] ?? NaN, parts[2] ?? NaN, parts.length > 3 ? (parts[3] ?? 1) : 1];
    };
    const over = (top: number[], bottom: number[]): number[] => {
      const alpha = top[3] ?? 1;
      return [0, 1, 2].map((index) => (top[index] ?? 0) * alpha + (bottom[index] ?? 0) * (1 - alpha)).concat(1);
    };
    const luminance = (rgb: number[]): number => {
      const channel = (value: number) => {
        const unit = value / 255;
        return unit <= 0.03928 ? unit / 12.92 : ((unit + 0.055) / 1.055) ** 2.4;
      };
      return 0.2126 * channel(rgb[0] ?? 0) + 0.7152 * channel(rgb[1] ?? 0) + 0.0722 * channel(rgb[2] ?? 0);
    };
    const backgroundOf = (element: Element): number[] => {
      const layers: number[][] = [];
      for (let node: Element | null = element; node; node = node.parentElement) {
        const layer = parse(getComputedStyle(node).backgroundColor);
        if ((layer[3] ?? 0) > 0) layers.push(layer);
        if ((layer[3] ?? 0) >= 1) break;
      }
      return layers.reverse().reduce((base, layer) => over(layer, base), [255, 255, 255, 1]);
    };
    return list.map(({ target, reason }) => {
      const element = document.querySelector(target);
      if (!element) return { target, reason, ratio: NaN, required: 4.5, ok: false };
      const style = getComputedStyle(element);
      const size = Number.parseFloat(style.fontSize);
      const large = size >= 24 || (size >= 18.66 && Number(style.fontWeight) >= 700);
      const background = backgroundOf(element);
      const text = over(parse(style.color), background);
      const [lighter, darker] = [luminance(text), luminance(background)].sort((a, b) => b - a);
      const ratio = ((lighter ?? 0) + 0.05) / ((darker ?? 0) + 0.05);
      const required = large ? 3 : 4.5;
      return { target, reason, ratio: Math.round(ratio * 100) / 100, required, ok: ratio >= required };
    });
  }, nodes);
}

/** Run axe on the open page for the AC-1 tags and return the facts; never throws for a violation (see expectNoViolations). */
export async function scanPage(page: Page, screen: string): Promise<Scan> {
  // axe-justification: AC-1 of MAM-86 names exactly the tags wcag2a, wcag2aa, wcag21a and wcag21aa; every rule with one of these tags runs, none is disabled and nothing is excluded from the page.
  const results = await new AxeBuilder({ page }).withTags(AXE_TAGS).analyze();
  const contrast = (list: AxeResults['passes']) => list.filter((rule) => rule.id === 'color-contrast').reduce((sum, rule) => sum + rule.nodes.length, 0);
  const undecided = results.incomplete
    .filter((rule) => rule.id === 'color-contrast')
    .flatMap((rule) => rule.nodes.map((node) => ({ target: node.target.join(' '), reason: String((node.any[0]?.data as { messageKey?: string } | undefined)?.messageKey ?? 'not decided') })));
  const measured = await measureUndecided(page, undecided);
  return {
    screen,
    url: page.url(),
    violations: results.violations,
    ran: [...new Set([...results.passes, ...results.violations, ...results.incomplete].map((rule) => rule.id))].sort(),
    contrastPassed: contrast(results.passes),
    contrastUndecided: contrast(results.incomplete),
    contrastUndecidedTargets: measured.map((item) => `${item.target} (${item.reason}): ${item.ratio}:1 (needs ${item.required}:1)`),
    contrastUndecidedMeasured: measured,
  };
}

/**
 * The gate: zero violations, and the colour-contrast rule really ran. Throws an Error that names the page and each rule id.
 * A scan in which color-contrast never looked at anything is also a failure, so the rule cannot be silently off.
 */
export async function expectNoViolations(page: Page, screen: string): Promise<Scan> {
  const scan = await scanPage(page, screen);
  if (scan.violations.length > 0) {
    throw new Error(
      `accessibility: page "${scan.screen}" (${scan.url}) has ${scan.violations.length} axe violation${scan.violations.length === 1 ? '' : 's'} ` +
        `(tags ${AXE_TAGS.join(', ')}):\n${describeViolations(scan.violations)}`,
    );
  }
  if (!scan.ran.includes('color-contrast')) {
    throw new Error(`accessibility: page "${scan.screen}" (${scan.url}): the colour-contrast rule did not run, so the contrast of the text was not checked`);
  }
  return scan;
}
