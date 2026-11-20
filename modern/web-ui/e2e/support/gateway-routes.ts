// A scripted gateway for the form and decision journeys (MAM-64, MAM-87): the browser's /api/v1 calls to
// http://localhost:8080 are answered here with synthetic data, so that the journeys do not depend on data in the stack.
// Keycloak is the real one. Unlike e2e/support/gateway.ts (recorded with MAM-62, GET only) this one answers POST too,
// returns problem+json for errors, records the JSON body of every call and can fail a call at the network level.
import type { Page } from '@playwright/test';

export const GATEWAY = 'http://localhost:8080';

export interface Answer {
  status: number;
  body?: unknown;
}

export interface RecordedCall {
  method: string;
  path: string;
  query: URLSearchParams;
  authorization: string | undefined;
  contentType: string | undefined;
  /** The parsed JSON body of the request, undefined when there is none. */
  body: unknown;
}

/** 'abort' fails the call at the network level (the browser sees a failed fetch). */
export type RouteHandler = (call: RecordedCall) => Answer | 'abort';

export interface MockApi {
  calls: RecordedCall[];
  /** The calls to one endpoint, e.g. api.to('POST', '/api/v1/applications'). */
  to: (method: string, path: string) => RecordedCall[];
}

export function json(status: number, body?: unknown): Answer {
  return body === undefined ? { status } : { status, body };
}

/** One answer per successive call; the last one repeats. */
export function sequence(...answers: Array<Answer | 'abort'>): RouteHandler {
  let next = 0;
  return () => {
    const answer = answers[Math.min(next, answers.length - 1)];
    next += 1;
    return answer ?? json(500, { title: 'no scripted answer', status: 500 });
  };
}

/**
 * Answer http://localhost:8080/api/v1/** from `routes`, keyed "<METHOD> <path>" (for example "GET /api/v1/applications").
 * A call without a route answers 404 so that an unexpected call shows up in `calls`.
 */
export async function mockApi(page: Page, routes: Record<string, RouteHandler>): Promise<MockApi> {
  const calls: RecordedCall[] = [];
  await page.route(`${GATEWAY}/api/v1/**`, async (route) => {
    const request = route.request();
    const cors = {
      'access-control-allow-origin': request.headers()['origin'] ?? '*',
      'access-control-allow-headers': 'authorization, content-type',
      'access-control-allow-methods': 'GET, POST, PUT, OPTIONS',
      vary: 'Origin',
    };
    if (request.method() === 'OPTIONS') return route.fulfill({ status: 204, headers: cors });

    const url = new URL(request.url());
    let body: unknown;
    const raw = request.postData();
    if (raw) {
      try {
        body = JSON.parse(raw);
      } catch {
        body = raw;
      }
    }
    const call: RecordedCall = {
      method: request.method(),
      path: url.pathname,
      query: url.searchParams,
      authorization: request.headers()['authorization'],
      contentType: request.headers()['content-type'],
      body,
    };
    calls.push(call);

    const handler = routes[`${call.method} ${call.path}`];
    const answer = handler ? handler(call) : json(404, { title: `not mocked: ${call.method} ${call.path}`, status: 404 });
    if (answer === 'abort') return route.abort('failed');
    return route.fulfill({
      status: answer.status,
      headers: { ...cors, 'content-type': answer.status >= 400 ? 'application/problem+json' : 'application/json' },
      body: answer.body === undefined ? '' : JSON.stringify(answer.body),
    });
  });
  return { calls, to: (method, path) => calls.filter((call) => call.method === method && call.path === path) };
}
