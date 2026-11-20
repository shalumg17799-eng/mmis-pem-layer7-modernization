import { Table, type TableProps } from '@trussworks/react-uswds';
import { useCallback, useRef, useSyncExternalStore } from 'react';

export interface ScrollableTableProps extends Omit<TableProps, 'scrollable'> {
  /** What the table is ("Screening results"). It names the scrolling region, so a screen reader says what the keyboard stop is. */
  label: string;
}

/**
 * True when the content is wider than the box, that is, when the box really scrolls sideways. The vertical axis is left out: the
 * USWDS container hides it (overflow-y) and grows with the table, so a difference of one rounded pixel there scrolls nothing.
 * This is the test the browser and the accessibility checks use (scrollWidth > clientWidth).
 */
const scrollsSideways = (box: HTMLElement): boolean => box.scrollWidth > box.clientWidth;

/**
 * The one table of the application that may be wider than the screen (every list and record table goes through it). The USWDS
 * scrollable container is kept, because a narrow screen needs it, but it is a keyboard stop only while its table really overflows:
 *   - overflowing: tabindex 0, so that a keyboard user can scroll the table, plus role "region" and a name ("Screening results
 *     (scrollable table)"), so that the stop is announced as what it is (WCAG 2.1.1, 4.1.2; axe scrollable-region-focusable);
 *   - fitting: no tabindex and no role, so that Tab does not stop on a frame that does nothing.
 * The width is measured in the browser after layout and again whenever the container or the table changes size (the window or the
 * zoom changes, rows arrive, a web font loads). The component re-renders only when the answer changes.
 */
export function ScrollableTable({ label, ...table }: ScrollableTableProps) {
  const box = useRef<HTMLDivElement>(null);
  // subscribe runs after the first render, when the container and its table exist; a ResizeObserver reports the first size at once
  const subscribe = useCallback((changed: () => void) => {
    const element = box.current;
    // jsdom (the component tests) has no layout and no ResizeObserver: nothing is measured there, and nothing overflows
    if (element === null || typeof ResizeObserver === 'undefined') return () => undefined;
    const observer = new ResizeObserver(() => changed());
    observer.observe(element);
    const inner = element.querySelector('table');
    if (inner) observer.observe(inner);
    return () => observer.disconnect();
  }, []);
  const scrolls = useSyncExternalStore(
    subscribe,
    () => box.current !== null && scrollsSideways(box.current),
    () => false,
  );
  return (
    <div
      ref={box}
      data-testid="scrollable-table-container"
      className="usa-table-container--scrollable"
      {...(scrolls ? { tabIndex: 0, 'aria-label': `${label} (scrollable table)` } : {})}
    >
      <Table {...table} />
    </div>
  );
}
