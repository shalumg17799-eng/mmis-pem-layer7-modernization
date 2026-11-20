// UX-OVERHAUL support (a new file: the MAM-62 support files are recorded and stay untouched). Helpers shared by tests/ux/*.test.tsx:
//  - renderAppWithState: the App opened at a route WITH router state, the way ApplyPage navigates to the status page after a
//    submit (navigate(path, { state: { received: true } })); renderApp of world.tsx cannot carry a state.
//  - the structure helpers of the polished pages (trail of the breadcrumb, sort state of a table header, list terms).
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useEffect } from 'react';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { App } from '../../src/app/App';
import { text, type Rendered } from './world';

function LocationProbe({ onChange }: { onChange: (path: string) => void }) {
  const location = useLocation();
  useEffect(() => {
    onChange(location.pathname);
  }, [location.pathname, onChange]);
  return null;
}

/** Like renderApp(route) of world.tsx, but the first history entry carries `state` (what navigate(..., { state }) sets). */
export function renderAppWithState(route: string, state: unknown): Rendered {
  window.history.replaceState(null, '', route);
  let current = route;
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const utils = render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[{ pathname: route, state }]}>
        <App />
        <LocationProbe onChange={(path) => (current = path)} />
      </MemoryRouter>
    </QueryClientProvider>,
  );
  return { user: userEvent.setup(), path: () => current, unmount: utils.unmount };
}

export interface CrumbItem {
  label: string;
  /** The href of the link, null for the item that is plain text. */
  href: string | null;
  current: boolean;
}

/** The navigation landmark "Breadcrumb" (null when the page has none). */
export function breadcrumbNav(): HTMLElement | null {
  return screen.queryByRole('navigation', { name: 'Breadcrumb' });
}

/** The items of the breadcrumb trail in order; an empty list when the page has no breadcrumb. */
export function trail(): CrumbItem[] {
  const nav = breadcrumbNav();
  if (!nav) return [];
  return Array.from(nav.querySelectorAll('li')).map((item) => ({
    label: text(item),
    href: item.querySelector('a')?.getAttribute('href') ?? null,
    current: item.getAttribute('aria-current') === 'page',
  }));
}

/** The columns of `table` whose header holds a sort button, in column order. */
export function sortableColumns(table: HTMLElement): string[] {
  return within(table)
    .getAllByRole('columnheader')
    .filter((header) => header.querySelector('button') !== null)
    .map((header) => text(header));
}

/** The header cell of the column named `name` in `table`. */
export function headerOf(table: HTMLElement, name: string): HTMLElement {
  const header = within(table)
    .getAllByRole('columnheader')
    .find((candidate) => text(candidate) === name);
  if (!header) throw new Error(`no column header "${name}"`);
  return header;
}

/** The sort button inside the header cell of the column named `name`. */
export function sortButton(table: HTMLElement, name: string): HTMLElement {
  return within(headerOf(table, name)).getByRole('button', { name });
}

/** Body rows of a table as cell texts. */
export function bodyTexts(table: HTMLElement): string[][] {
  return Array.from(table.querySelectorAll('tbody tr')).map((row) => Array.from(row.children).map((cell) => text(cell)));
}

/** The terms (dt) and values (dd) of every row of the description lists inside `scope`. */
export function listRows(scope: HTMLElement): Array<[string, string]> {
  return Array.from(scope.querySelectorAll('dl > div')).map((row) => [text(row.querySelector('dt')), text(row.querySelector('dd'))] as [string, string]);
}
