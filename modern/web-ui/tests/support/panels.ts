// MAM-82 component-test helpers: the five services behind the panels of the console provider detail
// (screening, sanctions, revalidation, notices, audit) answered the way their contracts say. Synthetic data only.
import type { components as ProviderSchemas } from '../../src/shared/api/generated/provider-service';
import type { components as SanctionSchemas } from '../../src/shared/api/generated/sanctions-service';
import { response, type Handler } from './fakeApi';
import { failedScreening, passedScreening, type ScreeningPage } from './approval';
import { numbered, type Notice } from './notices';
import { dueCase, type RevalidationCase } from './queues';

export type Sanction = SanctionSchemas['schemas']['Sanction'];
export type AuditEntry = ProviderSchemas['schemas']['AuditEntry'];

export function sanction(overrides: Partial<Sanction> = {}): Sanction {
  return {
    sanctionId: '4c8a1f20-6d3b-4b7e-9a15-2e7f0b3c9d41',
    npi: '1003000111',
    action: 'S',
    reason: 'Licence under review',
    effectiveDate: '2026-03-10',
    recordedBy: 'supervisor1',
    recordedAt: '2026-03-10T14:00:00Z',
    resultingStatus: 'S',
    ...overrides,
  };
}

export function page<T>(items: T[], total: number = items.length) {
  return { items, page: 0, size: 20, total };
}

export function auditEntry(overrides: Partial<AuditEntry> = {}): AuditEntry {
  return { at: '2026-03-02T09:30:00Z', actor: 'analyst1', action: 'APPROVE', ...overrides };
}

export function noticeFor(template: Notice['template'], overrides: Partial<Notice> = {}): Notice {
  return numbered({ template, sentAt: '2026-03-10T14:05:00Z', ...overrides });
}

export interface PanelData {
  screenings: (npi: string) => ScreeningPage;
  sanctions: ReturnType<typeof page<Sanction>>;
  revalidations: ReturnType<typeof page<RevalidationCase>>;
  notices: ReturnType<typeof page<Notice>>;
  audit: ReturnType<typeof page<AuditEntry>>;
}

/** Everything populated: 1003000111 passed, was suspended and reinstated, got the SUS and RST notices, was approved. */
export function fullPanels(): PanelData {
  return {
    screenings: (npi) => (npi === '1003000444' ? failedScreening(npi) : passedScreening(npi)),
    sanctions: page([
      sanction({ sanctionId: '4c8a1f20-6d3b-4b7e-9a15-2e7f0b3c9d42', action: 'R', reason: 'Licence confirmed', effectiveDate: '2026-03-20', resultingStatus: 'A' }),
      sanction(),
    ]),
    revalidations: page([dueCase({ npi: '1003000111' })]),
    notices: page([noticeFor('SUS'), noticeFor('RST', { status: 'FAILED', sentAt: '2026-03-20T10:00:00Z' })]),
    audit: page([auditEntry()]),
  };
}

export function emptyPanels(): PanelData {
  return {
    screenings: () => page([]),
    sanctions: page([]),
    revalidations: page([]),
    notices: page([]),
    audit: page([]),
  };
}

/** The gateway answers of the five panels; `over` replaces single answers (a 5xx, a 404 for an endpoint that does not exist yet). */
export function panelRoutes(npi: string, data: PanelData, over: Record<string, Handler> = {}): Record<string, Handler> {
  return {
    'GET /api/v1/screenings': (call) => response(200, data.screenings(call.url.searchParams.get('npi') ?? npi)),
    'GET /api/v1/sanctions': () => response(200, data.sanctions),
    'GET /api/v1/revalidations': () => response(200, data.revalidations),
    'GET /api/v1/notices': () => response(200, data.notices),
    [`GET /api/v1/providers/${npi}/audit`]: () => response(200, data.audit),
    ...over,
  };
}

export const PANEL_CALLS = ['/api/v1/screenings', '/api/v1/sanctions', '/api/v1/revalidations', '/api/v1/notices'];
