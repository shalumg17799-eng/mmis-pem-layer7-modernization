import { useMemo, useState } from 'react';
import './SortHeader.css';

type Direction = 'ascending' | 'descending';
type Accessor<T> = (row: T) => string | number | null | undefined;

interface SortState {
  column: string;
  direction: Direction;
}

export interface Sorting<T> {
  /** The rows in the order chosen; the order the service returned until a column header is pressed. */
  rows: T[];
  /** The `aria-sort` value of a column: ascending, descending, or none for a column that is not the sort column. */
  ariaSort: (column: string) => Direction | 'none';
  /** none -> ascending -> descending -> none (the order of the service again). */
  toggle: (column: string) => void;
}

const isEmpty = (value: string | number | null | undefined): value is null | undefined | '' => value === null || value === undefined || value === '';

function compare(a: string | number, b: string | number): number {
  if (typeof a === 'number' && typeof b === 'number') return a - b;
  return String(a).localeCompare(String(b), 'en', { numeric: true, sensitivity: 'base' });
}

/**
 * Sorting of the rows of one list table by the column headers (UX overhaul). The sort is applied to the rows that are on the page:
 * paging stays with the service, so a sort never changes which rows a page holds. Nothing is sorted until a header is pressed.
 */
export function useSorting<T>(items: readonly T[], accessors: Record<string, Accessor<T>>): Sorting<T> {
  const [state, setState] = useState<SortState | null>(null);
  const rows = useMemo(() => {
    const accessor = state ? accessors[state.column] : undefined;
    if (!state || !accessor) return [...items];
    const factor = state.direction === 'ascending' ? 1 : -1;
    return items
      .map((row, index) => ({ row, index }))
      .sort((left, right) => {
        const a = accessor(left.row);
        const b = accessor(right.row);
        // an empty value stays last whichever way the column is sorted; ties keep the order of the service
        if (isEmpty(a) || isEmpty(b)) return isEmpty(a) === isEmpty(b) ? left.index - right.index : isEmpty(a) ? 1 : -1;
        return factor * compare(a, b) || left.index - right.index;
      })
      .map((entry) => entry.row);
    // the accessors are fixed per table, so the items and the sort state are the inputs
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [items, state]);
  return {
    rows,
    ariaSort: (column) => (state?.column === column ? state.direction : 'none'),
    toggle: (column) =>
      setState((current) => {
        if (current?.column !== column) return { column, direction: 'ascending' };
        return current.direction === 'ascending' ? { column, direction: 'descending' } : null;
      }),
  };
}

/**
 * A column header that sorts its table: a real button inside the header cell, so the keyboard works (Tab, Enter, Space) and the
 * header keeps its name (the visible text only; the arrow is an image that is hidden from assistive technology). `aria-sort` on the
 * header cell of the sorted column tells assistive technology which column is sorted and how (an unsorted column has none, which
 * means the same as "none" and keeps USWDS from painting every header).
 */
export function SortHeader<T>({ column, label, sorting }: { column: string; label: string; sorting: Sorting<T> }) {
  const direction = sorting.ariaSort(column);
  return (
    <th scope="col" aria-sort={direction === 'none' ? undefined : direction}>
      <button type="button" className="usa-button usa-button--unstyled pem-sort-button" onClick={() => sorting.toggle(column)}>
        {label}
        <svg aria-hidden="true" focusable="false" width="12" height="12" viewBox="0 0 12 12" className="margin-left-05" style={{ verticalAlign: 'middle' }}>
          {direction !== 'descending' && <path d="M6 1 10 5H2z" fill="currentColor" opacity={direction === 'ascending' ? 1 : 0.35} />}
          {direction !== 'ascending' && <path d="M6 11 2 7h8z" fill="currentColor" opacity={direction === 'descending' ? 1 : 0.35} />}
        </svg>
      </button>
    </th>
  );
}
