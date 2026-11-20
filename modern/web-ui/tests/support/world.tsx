// Test world of the MAM-62 component tests: the App is rendered the way main.tsx does (QueryClientProvider > router > App),
// only the router is an in-memory one. Boundaries replaced: oidc-client-ts (fakeOidc) and fetch (fakeApi).
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent, { type UserEvent } from '@testing-library/user-event';
import { useEffect } from 'react';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, vi } from 'vitest';
import { App } from '../../src/app/App';
import { api, installApi, reply, type Handler } from './fakeApi';
import { oidc, resetOidc } from './fakeOidc';
import { applicationPage, makeUser, PROVIDER_NPI, provider, type Application, type UserName } from './fixtures';

export { api, installApi, oidc };

/** Register the per-test setup and clean-up. Call once at the top of a test file, after vi.mock('oidc-client-ts', ...). */
export function setupShellWorld(): void {
  beforeEach(() => {
    resetOidc();
    sessionStorage.clear();
    localStorage.clear();
    window.PEM_CONFIG = undefined;
    installApi(defaultRoutes());
  });
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });
}

/** Gateway answers used unless a test declares its own: the given applications, the provider of the AC exists. */
export function defaultRoutes(applications: Application[] = []): Record<string, Handler> {
  return {
    'GET /api/v1/applications': reply(200, applicationPage(applications)),
    [`GET /api/v1/providers/${PROVIDER_NPI}`]: reply(200, provider()),
  };
}

export function signedInAs(name: UserName): void {
  oidc.user = makeUser(name);
}

function LocationProbe({ onChange }: { onChange: (path: string) => void }) {
  const location = useLocation();
  useEffect(() => {
    onChange(location.pathname);
  }, [location.pathname, onChange]);
  return null;
}

export interface Rendered {
  user: UserEvent;
  /** Current router path (pathname only). */
  path: () => string;
  unmount: () => void;
}

/** Open `route` in the browser (address bar and router agree), like a full page load. */
export function renderApp(route: string): Rendered {
  window.history.replaceState(null, '', route);
  let current = route;
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const utils = render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[route]}>
        <App />
        <LocationProbe onChange={(path) => (current = path)} />
      </MemoryRouter>
    </QueryClientProvider>,
  );
  return { user: userEvent.setup(), path: () => current, unmount: utils.unmount };
}

// ---- queries shared by the tests -------------------------------------------------------------------------------------

export function text(element: Element | null | undefined): string {
  return (element?.textContent ?? '').replace(/\s+/g, ' ').trim();
}

/** Links of the main menu landmark, named "Primary navigation" (a breadcrumb nav is not the menu; Q-101), without the sign-out control. */
export function menuEntries(): Array<[string, string]> {
  return screen
    .queryAllByRole('navigation', { name: 'Primary navigation' })
    .flatMap((nav) => within(nav).queryAllByRole('link'))
    .filter((link) => !/sign out/i.test(text(link)))
    .map((link) => [text(link), link.getAttribute('href') ?? ''] as [string, string]);
}

export function sorted(entries: Array<[string, string]>): Array<[string, string]> {
  return [...entries].sort((a, b) => a[1].localeCompare(b[1]));
}

/** The "Apply for enrolment" control inside the main landmark: a button, or a link styled as one. */
export async function findApplyControl(): Promise<HTMLElement> {
  const main = await screen.findByRole('main');
  return waitFor(() => {
    const control =
      within(main).queryByRole('button', { name: 'Apply for enrolment' }) ?? within(main).queryByRole('link', { name: 'Apply for enrolment' });
    if (!control) throw new Error('the main area has no Apply for enrolment button');
    return control;
  });
}

export function signOutControl(): HTMLElement {
  return screen.queryByRole('button', { name: /sign out/i }) ?? screen.getByRole('link', { name: /sign out/i });
}

/** The applications table: the table that has a "Submitted" column header. */
export function applicationsTable(): HTMLElement | null {
  return (
    screen.queryAllByRole('table').find((table) => within(table).queryByRole('columnheader', { name: 'Submitted' })) ?? null
  );
}

export async function findApplicationsTable(): Promise<HTMLElement> {
  return waitFor(() => {
    const table = applicationsTable();
    if (!table) throw new Error('the page shows no applications table');
    return table;
  });
}

export function bodyRows(table: HTMLElement): HTMLElement[] {
  return Array.from(table.querySelectorAll<HTMLElement>('tbody tr'));
}

export function cellTexts(row: HTMLElement): string[] {
  return Array.from(row.children).map((cell) => text(cell));
}

/** Value shown next to a label, in a two-column table row or a description list. */
export function fieldValue(label: string): string | undefined {
  for (const row of Array.from(document.querySelectorAll('tr'))) {
    const cells = Array.from(row.children);
    if (cells.length >= 2 && text(cells[0]) === label) return text(cells[1]);
  }
  for (const term of Array.from(document.querySelectorAll('dt'))) {
    if (text(term) === label && term.nextElementSibling) return text(term.nextElementSibling);
  }
  return undefined;
}

/** Press Tab until `matches` accepts the focused element; returns the names of every focus stop on the way. */
export async function tabUntil(user: UserEvent, matches: (element: Element) => boolean, max = 60): Promise<string[]> {
  const stops: string[] = [];
  for (let i = 0; i < max; i++) {
    await user.tab();
    const focused = document.activeElement;
    if (!focused || focused === document.body) continue;
    stops.push(text(focused) || focused.getAttribute('aria-label') || focused.tagName);
    if (matches(focused)) return stops;
  }
  throw new Error(`no matching focus stop within ${max} Tab presses; stops were: ${stops.join(' | ')}`);
}
