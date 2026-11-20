// MAM-86 AC-4 (focus is visible and the tab order is logical): the one table container of the application, src/shared/ui/ScrollableTable.tsx,
// is a keyboard stop ONLY while its table really scrolls sideways. Then it has tabindex 0 (so that a keyboard user can scroll it), the role
// "region" and the name "<label> (scrollable table)" (so that the stop is announced as what it is). When the table fits it has no tabindex
// and no role, so that Tab does not stop on a frame that does nothing (defect D1 of modern/docs/reports/accessibility-report.md, found by the
// Playwright Tab walks of e2e/a11y/pages.a11y.spec.ts on the build of 2026-11-20 22:02).
//
// Order of work, said plainly: these component tests were written AFTER the component. The tests that were red first are the six Playwright Tab
// walks of AC-4 (they failed for D1 against the old build and pass against the build deployed on 2026-11-21 00:19). jsdom has no layout, so
// the sizes the browser would measure (scrollWidth and clientWidth of the container) and the ResizeObserver are stubbed here; the real
// measuring is proved only by the Playwright runs.
import { act, cleanup, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ScrollableTable } from './zz-mutant-component';

const CONTAINER = 'scrollable-table-container';

/** What the browser would measure for the container. jsdom reports 0 for both, so a test sets them. */
const sizes = { scrollWidth: 0, clientWidth: 0 };
const FITS = { scrollWidth: 960, clientWidth: 960 };
const OVERFLOWS = { scrollWidth: 1400, clientWidth: 960 };
const setSizes = (next: { scrollWidth: number; clientWidth: number }): void => {
  sizes.scrollWidth = next.scrollWidth;
  sizes.clientWidth = next.clientWidth;
};

/** A ResizeObserver that measures nothing by itself: the test calls fire() after it has changed the sizes, as the browser would. */
class FakeResizeObserver {
  static created: FakeResizeObserver[] = [];
  readonly observed: Element[] = [];
  disconnected = false;
  private readonly callback: ResizeObserverCallback;
  constructor(callback: ResizeObserverCallback) {
    this.callback = callback;
    FakeResizeObserver.created.push(this);
  }
  observe(target: Element): void {
    this.observed.push(target);
  }
  unobserve(): void {
    // the component never calls it; nothing to do
  }
  disconnect(): void {
    this.disconnected = true;
  }
  fire(): void {
    this.callback([], this as unknown as ResizeObserver);
  }
}

/** The browser reports a change of size: every observer that is still connected runs its callback inside act, so React re-renders. */
function resizeObserverFires(): void {
  act(() => {
    for (const observer of FakeResizeObserver.created.filter((each) => !each.disconnected)) observer.fire();
  });
}

const MEASURED = ['scrollWidth', 'clientWidth'] as const;
const originalSizeProperties = MEASURED.map((name) => [name, Object.getOwnPropertyDescriptor(Element.prototype, name)] as const);

beforeEach(() => {
  setSizes({ scrollWidth: 0, clientWidth: 0 });
  FakeResizeObserver.created = [];
  for (const name of MEASURED) {
    Object.defineProperty(Element.prototype, name, {
      configurable: true,
      get(this: Element): number {
        return this.getAttribute('data-testid') === CONTAINER ? sizes[name] : 0;
      },
    });
  }
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  for (const [name, original] of originalSizeProperties) {
    if (original) Object.defineProperty(Element.prototype, name, original);
    else delete (Element.prototype as unknown as Record<string, unknown>)[name];
  }
});

function withObserver(): void {
  vi.stubGlobal('ResizeObserver', FakeResizeObserver);
}

function renderTable(label = 'Screening results') {
  return render(
    <ScrollableTable label={label}>
      <thead>
        <tr>
          <th scope="col">NPI</th>
          <th scope="col">Result</th>
        </tr>
      </thead>
      <tbody>
        <tr>
          <td>
            <a href="#detail">1003000111</a>
          </td>
          <td>Pass (P)</td>
        </tr>
      </tbody>
    </ScrollableTable>,
  );
}

const container = (): HTMLElement => screen.getByTestId(CONTAINER);

describe('ScrollableTable: a Tab stop only while its table overflows', () => {
  it('[MAM-86 AC-4] a table wider than its container makes the container a Tab stop: tabindex is 0', () => {
    setSizes(OVERFLOWS);
    withObserver();
    renderTable();
    expect(container()).toHaveAttribute('tabindex', '0');
  });

  it('[MAM-86 AC-4] a table wider than its container gives the container the role region', () => {
    setSizes(OVERFLOWS);
    withObserver();
    renderTable();
    expect(container()).toHaveAttribute('role', 'region');
  });

  it('[MAM-86 AC-4] the name of the scrolling region contains the label of the table: "Screening results (scrollable table)"', () => {
    setSizes(OVERFLOWS);
    withObserver();
    renderTable('Screening results');
    expect(screen.getByRole('region', { name: 'Screening results (scrollable table)' })).toBe(container());
  });

  it('[MAM-86 AC-4] the name follows the label that the page gives: another table is named after its own label', () => {
    setSizes(OVERFLOWS);
    withObserver();
    renderTable('My notices');
    expect(container()).toHaveAccessibleName('My notices (scrollable table)');
  });

  it('[MAM-86 AC-4] a table that fits has no tabindex on its container', () => {
    setSizes(FITS);
    withObserver();
    renderTable();
    expect(container()).not.toHaveAttribute('tabindex');
  });

  it('[MAM-86 AC-4] a table that fits has no role and no name on its container, so there is no region to announce', () => {
    setSizes(FITS);
    withObserver();
    renderTable();
    expect(container()).not.toHaveAttribute('role');
    expect(container()).not.toHaveAttribute('aria-label');
    expect(screen.queryByRole('region')).toBeNull();
  });

  it('[MAM-86 AC-4] a table exactly as wide as its container fits: equal widths are not an overflow', () => {
    setSizes({ scrollWidth: 960, clientWidth: 960 });
    withObserver();
    renderTable();
    expect(container()).not.toHaveAttribute('tabindex');
  });

  it('[MAM-86 AC-4] a table one pixel wider than its container overflows', () => {
    setSizes({ scrollWidth: 961, clientWidth: 960 });
    withObserver();
    renderTable();
    expect(container()).toHaveAttribute('tabindex', '0');
  });

  it('[MAM-86 AC-4] when the sizes change and the observer fires, a table that fitted becomes a Tab stop with role and name', () => {
    setSizes(FITS);
    withObserver();
    renderTable('Screening results');
    expect(container()).not.toHaveAttribute('tabindex');

    setSizes(OVERFLOWS);
    resizeObserverFires();

    expect(container()).toHaveAttribute('tabindex', '0');
    expect(container()).toHaveAttribute('role', 'region');
    expect(container()).toHaveAccessibleName('Screening results (scrollable table)');
  });

  it('[MAM-86 AC-4] when the sizes change and the observer fires, a table that overflowed stops being a Tab stop and loses role and name', () => {
    setSizes(OVERFLOWS);
    withObserver();
    renderTable();
    expect(container()).toHaveAttribute('tabindex', '0');

    setSizes(FITS);
    resizeObserverFires();

    expect(container()).not.toHaveAttribute('tabindex');
    expect(container()).not.toHaveAttribute('role');
    expect(container()).not.toHaveAttribute('aria-label');
  });

  it('[MAM-86 AC-4] the attributes follow the sizes in both directions on the same element, so that the focus is not lost by a new element', () => {
    setSizes(FITS);
    withObserver();
    renderTable();
    const before = container();

    setSizes(OVERFLOWS);
    resizeObserverFires();
    const whileOverflowing = container();
    setSizes(FITS);
    resizeObserverFires();

    expect(whileOverflowing).toBe(before);
    expect(container()).toBe(before);
  });

  it('[MAM-86 AC-4] an observer that fires with the sizes unchanged changes nothing', () => {
    setSizes(OVERFLOWS);
    withObserver();
    renderTable();

    resizeObserverFires();

    expect(container()).toHaveAttribute('tabindex', '0');
    expect(container()).toHaveAttribute('role', 'region');
  });

  it('[MAM-86 AC-4] the observer watches the container and the table in it (rows that arrive or a window that changes both change the answer)', () => {
    withObserver();
    renderTable();
    const live = FakeResizeObserver.created.filter((each) => !each.disconnected);
    expect(live).toHaveLength(1);
    const watched = live[0]?.observed ?? [];
    expect(watched).toHaveLength(2);
    expect(watched[0]).toBe(container());
    expect(watched[1]).toBe(within(container()).getByRole('table'));
  });

  it('[MAM-86 AC-4] the observer is disconnected when the table leaves the page', () => {
    withObserver();
    const view = renderTable();
    view.unmount();
    expect(FakeResizeObserver.created.every((each) => each.disconnected)).toBe(true);
    expect(FakeResizeObserver.created.length).toBeGreaterThan(0);
  });

  it('[MAM-86 AC-4] where the browser has no ResizeObserver and nothing is measured (jsdom: no layout), the container is not a Tab stop', () => {
    renderTable();
    expect(typeof ResizeObserver).toBe('undefined');
    expect(container()).not.toHaveAttribute('tabindex');
    expect(container()).not.toHaveAttribute('role');
  });
});

describe('ScrollableTable: what the keyboard user meets', () => {
  it('[MAM-86 AC-4] a table that overflows is reached by Tab: the container takes the focus before the link in the first row', async () => {
    setSizes(OVERFLOWS);
    withObserver();
    renderTable();
    const user = userEvent.setup();

    await user.tab();
    expect(container()).toHaveFocus();
    await user.tab();
    expect(screen.getByRole('link', { name: '1003000111' })).toHaveFocus();
  });

  it('[MAM-86 AC-4] a table that fits is not a stop: the first Tab goes straight to the link in the first row', async () => {
    setSizes(FITS);
    withObserver();
    renderTable();
    const user = userEvent.setup();

    await user.tab();
    expect(screen.getByRole('link', { name: '1003000111' })).toHaveFocus();
    expect(container()).not.toHaveFocus();
  });
});

describe('ScrollableTable: the table itself', () => {
  it('[MAM-86 AC-4] the table is inside the USWDS scrolling container, with its header cells and its data', () => {
    withObserver();
    renderTable();
    expect(container()).toHaveClass('usa-table-container--scrollable');
    const table = within(container()).getByRole('table');
    expect(within(table).getAllByRole('columnheader').map((cell) => cell.textContent)).toEqual(['NPI', 'Result']);
    expect(within(table).getByRole('cell', { name: 'Pass (P)' })).toBeInTheDocument();
  });

  it('[MAM-86 AC-4] there is one scrolling container, not two (the USWDS scrollable option of the table is not used as well)', () => {
    withObserver();
    const { container: page } = renderTable();
    expect(page.querySelectorAll('.usa-table-container--scrollable')).toHaveLength(1);
  });

  it('[MAM-86 AC-4] the properties of the table (a caption, a class) reach the table, not the container', () => {
    withObserver();
    render(
      <ScrollableTable label="Notices" caption="Notices sent" className="pem-test-table">
        <tbody>
          <tr>
            <td>x</td>
          </tr>
        </tbody>
      </ScrollableTable>,
    );
    const table = within(container()).getByRole('table', { name: 'Notices sent' });
    expect(table).toHaveClass('pem-test-table');
    expect(container()).not.toHaveClass('pem-test-table');
  });
});
