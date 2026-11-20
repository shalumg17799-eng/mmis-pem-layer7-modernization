// MAM-85 component-test helpers: the four APIs behind /console/queues (provider-service, screening-service,
// revalidation-service, enrollment-service) answered the way their contracts say. Synthetic data only.
import { screen, waitFor, within } from '@testing-library/react';
import type { components as RevalidationSchemas } from '../../src/shared/api/generated/revalidation-service';
import { response, type ApiCall, type Handler } from './fakeApi';
import { application, applicationPage, type Application, type Provider, type UserName } from './fixtures';
import { CLINIC_NPI, failedScreening, pendingFail, pendingPass, RIVERA_NPI, type ScreeningPage } from './approval';
import { api, defaultRoutes, installApi, renderApp, signedInAs, text, type Rendered } from './world';

export type RevalidationCase = RevalidationSchemas['schemas']['RevalidationCase'];

export const DUE_NPI = '1003000222';

export function providerPage(items: Provider[], total: number = items.length) {
  return { items, page: 0, size: 20, total };
}

export function revalidationPage(items: RevalidationCase[], total: number = items.length) {
  return { items, page: 0, size: 20, total };
}

export function dueCase(overrides: Partial<RevalidationCase> = {}): RevalidationCase {
  return { caseId: '3b1f6c52-8d0e-4a77-b0a4-6f2d9c1e5a10', npi: DUE_NPI, dueDate: '2026-02-28', status: 'DUE', openedAt: '2026-03-01T00:00:00Z', ...overrides };
}

export function rejectedApplication(overrides: Partial<Application> = {}): Application {
  return application({
    applicationId: '8f1c2e0a-5b3d-4e7a-9c11-2a6d4b8e0f33',
    status: 'REJECTED',
    npi: RIVERA_NPI,
    taxonomy: '',
    errors: [{ code: 'E104', field: 'taxonomy', message: 'Enter the taxonomy code.' }],
    source: 'PORTAL',
    ...overrides,
  });
}

export interface QueueData {
  pending: ReturnType<typeof providerPage>;
  failed: ReturnType<typeof providerPage>;
  screenings: (npi: string) => ScreeningPage;
  due: ReturnType<typeof revalidationPage>;
  inScreening: ReturnType<typeof revalidationPage>;
  rejected: ReturnType<typeof applicationPage>;
}

/** The state of journey J1 and J3: 1003000111 waits for approval, 1003000444 failed on OIGLEIE, 1003000222 is due, one rejection. */
export function fullQueues(): QueueData {
  return {
    pending: providerPage([pendingPass()]),
    failed: providerPage([pendingFail()]),
    screenings: (npi) => (npi === CLINIC_NPI ? failedScreening(npi) : { items: [], page: 0, size: 20, total: 0 }),
    due: revalidationPage([dueCase()]),
    inScreening: revalidationPage([]),
    rejected: applicationPage([rejectedApplication()]),
  };
}

export function emptyQueues(): QueueData {
  return {
    pending: providerPage([]),
    failed: providerPage([]),
    screenings: () => ({ items: [], page: 0, size: 20, total: 0 }),
    due: revalidationPage([]),
    inScreening: revalidationPage([]),
    rejected: applicationPage([]),
  };
}

/** The gateway for /console/queues; `over` replaces single answers (for example a 5xx for the revalidations). */
export function queueRoutes(data: QueueData, over: Record<string, Handler> = {}): Record<string, Handler> {
  return {
    ...defaultRoutes(),
    'GET /api/v1/providers': (call) => response(200, call.url.searchParams.get('screenFlag') === 'F' ? data.failed : data.pending),
    'GET /api/v1/screenings': (call) => response(200, data.screenings(call.url.searchParams.get('npi') ?? '')),
    'GET /api/v1/revalidations': (call) => response(200, call.url.searchParams.get('status') === 'IN_SCREENING' ? data.inScreening : data.due),
    'GET /api/v1/applications': () => response(200, data.rejected),
    ...over,
  };
}

export async function openQueues(who: UserName, routes: Record<string, Handler>): Promise<Rendered> {
  signedInAs(who);
  installApi(routes);
  const rendered = renderApp('/console/queues');
  await screen.findByRole('heading', { level: 1, name: 'Work queues' });
  return rendered;
}

/** The section of a queue: the <section> whose heading starts with `name` (the selector of journey J1). */
export function queueSection(name: string): HTMLElement {
  const heading = screen.getAllByRole('heading', { level: 2 }).find((candidate) => text(candidate).startsWith(name));
  const section = heading?.closest('section');
  if (!section) throw new Error(`no section with a heading that starts with "${name}"`);
  return section;
}

export async function findQueueSection(name: string, until: RegExp = /./): Promise<HTMLElement> {
  return waitFor(() => {
    const section = queueSection(name);
    if (!until.test(text(section))) throw new Error(`section ${name} is not ready: ${text(section)}`);
    return section;
  });
}

/** Cell texts of the body rows of the table of a section. */
export function rowsOf(section: HTMLElement): string[][] {
  return Array.from(section.querySelectorAll('tbody tr')).map((row) => Array.from(row.children).map((cell) => text(cell)));
}

export function callsTo(path: string): ApiCall[] {
  return api.to('GET', path);
}

export function paramsOf(call: ApiCall | undefined): Record<string, string> {
  return Object.fromEntries(call?.url.searchParams.entries() ?? []);
}

export { within };
