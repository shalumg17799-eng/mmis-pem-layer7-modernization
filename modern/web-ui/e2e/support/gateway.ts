// The gateway (http://localhost:8080) is answered in the browser with synthetic data, so that the portal home can be scanned
// with and without applications whether or not enrollment-service and provider-service are up. Keycloak is the real one.
import type { Page } from '@playwright/test';

export const GATEWAY = 'http://localhost:8080';
export const PROVIDER_NPI = '1003000111';
export const APPLICATION_ID = '0b9c5d1e-6a4f-4c1a-9a43-3c2d6f0a1b11';

export const APPLICATION = {
  applicationId: APPLICATION_ID,
  status: 'VALIDATED',
  npi: PROVIDER_NPI,
  medicaidId: 'TX0000111',
  providerType: 'IN',
  taxonomy: '207Q00000X',
  source: 'PORTAL',
  submittedAt: '2026-03-01T10:00:00Z',
};

export const PROVIDER = {
  npi: PROVIDER_NPI,
  medicaidId: 'TX0000111',
  providerType: 'IN',
  taxonomy: '207Q00000X',
  riskTier: 'L',
  enrollStatus: 'A',
  screenFlag: 'P',
  effectiveDate: '2026-03-02',
  revalidationDueDate: '2031-03-02',
  version: 1,
};

export interface GatewayCall {
  method: string;
  path: string;
  authorization: string | undefined;
}

export interface GatewayOptions {
  applications?: unknown[];
  /** Status of each successive GET /api/v1/applications; the last one repeats (e.g. [401, 200]). */
  applicationStatuses?: number[];
}

export interface Gateway {
  calls: GatewayCall[];
}

export async function mockGateway(page: Page, options: GatewayOptions = {}): Promise<Gateway> {
  const calls: GatewayCall[] = [];
  const statuses = [...(options.applicationStatuses ?? [200])];
  await page.route(`${GATEWAY}/api/v1/**`, async (route) => {
    const request = route.request();
    const cors = {
      'access-control-allow-origin': request.headers()['origin'] ?? '*',
      'access-control-allow-headers': 'authorization, content-type',
      'access-control-allow-methods': 'GET, POST, PUT, OPTIONS',
      vary: 'Origin',
    };
    if (request.method() === 'OPTIONS') return route.fulfill({ status: 204, headers: cors });

    const path = new URL(request.url()).pathname;
    calls.push({ method: request.method(), path, authorization: request.headers()['authorization'] });
    const json = (status: number, body: unknown) =>
      route.fulfill({ status, headers: { ...cors, 'content-type': status >= 400 ? 'application/problem+json' : 'application/json' }, body: JSON.stringify(body) });

    if (request.method() === 'GET' && path === '/api/v1/applications') {
      const status = statuses.length > 1 ? statuses.shift()! : statuses[0]!;
      if (status === 401) return json(401, { title: 'Not authenticated', status: 401 });
      const items = options.applications ?? [APPLICATION];
      return json(200, { items, page: 0, size: 20, total: items.length });
    }
    if (request.method() === 'GET' && path === `/api/v1/providers/${PROVIDER_NPI}`) return json(200, PROVIDER);
    return json(404, { title: `not mocked: ${request.method()} ${path}`, status: 404 });
  });
  return { calls };
}
