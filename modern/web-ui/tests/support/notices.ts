// MAM-70 component-test helpers: GET /api/v1/notices of notification-service as the provider's portal page sees it.
import { screen, within } from '@testing-library/react';
import type { components as NotificationSchemas } from '../../src/shared/api/generated/notification-service';
import { response, type ApiCall, type Handler } from './fakeApi';
import { api, defaultRoutes, installApi, renderApp, signedInAs, text, type Rendered } from './world';

export type Notice = NotificationSchemas['schemas']['Notice'];

export function notice(overrides: Partial<Notice> = {}): Notice {
  return {
    noticeId: '9d2e1c70-4b8a-4f63-a1d5-0c7b3e9f2a11',
    npi: '1003000111',
    template: 'APPROVED',
    channel: 'EMAIL',
    status: 'SENT',
    sentAt: '2026-03-05T10:00:00Z',
    ...overrides,
  };
}

let counter = 0;
/** A notice with its own id (the table keys its rows by notice id). */
export function numbered(overrides: Partial<Notice> = {}): Notice {
  counter += 1;
  return notice({ noticeId: `00000000-0000-4000-8000-${String(counter).padStart(12, '0')}`, ...overrides });
}

export function noticePage(items: Notice[], total: number = items.length, page = 0) {
  return { items, page, size: 20, total };
}

export const NOTICES_ROUTE = 'GET /api/v1/notices';

/** Open /portal/notices as provider1; `routes` replace the default gateway answers. */
export async function openNotices(routes: Record<string, Handler>): Promise<Rendered> {
  signedInAs('provider1');
  installApi({ ...defaultRoutes(), ...routes });
  const rendered = renderApp('/portal/notices');
  await screen.findByRole('heading', { level: 1, name: 'My notices' });
  return rendered;
}

export function noticesTable(): HTMLElement | null {
  return screen.queryAllByRole('table').find((table) => within(table).queryByRole('columnheader', { name: 'Notice' })) ?? null;
}

export function noticeRows(): string[][] {
  const table = noticesTable();
  return table ? Array.from(table.querySelectorAll('tbody tr')).map((row) => Array.from(row.children).map((cell) => text(cell))) : [];
}

export function noticeCalls(): ApiCall[] {
  return api.to('GET', '/api/v1/notices');
}

export function paramsOf(call: ApiCall | undefined): Record<string, string> {
  return Object.fromEntries(call?.url.searchParams.entries() ?? []);
}

export const answer =
  (items: Notice[], total?: number): Handler =>
  () =>
    response(200, noticePage(items, total));
