// The HTTP boundary of the web UI is fetch. installApi() replaces it with a recording stub: requests are matched on
// "<METHOD> <path>" (the gateway origin is checked by the tests that care), every call is recorded, anything that is not
// declared answers 404 so that an unexpected call is visible in the call log.
import { vi } from 'vitest';

export interface ApiCall {
  method: string;
  url: URL;
  headers: Headers;
  body: string | null;
}

export type Handler = (call: ApiCall) => Response | Promise<Response>;

export function response(status: number, body?: unknown): Response {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { 'content-type': status >= 400 ? 'application/problem+json' : 'application/json' },
  });
}

export function reply(status: number, body?: unknown): Handler {
  return () => response(status, body);
}

export function problem(status: number, title: string): { title: string; status: number } {
  return { title, status };
}

export function deferred<T>(): { promise: Promise<T>; resolve: (value: T) => void } {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((r) => {
    resolve = r;
  });
  return { promise, resolve };
}

export const api = {
  calls: [] as ApiCall[],
  /** Calls to one endpoint, e.g. api.to('GET', '/api/v1/applications'). */
  to(method: string, pathname: string): ApiCall[] {
    return api.calls.filter((c) => c.method === method && c.url.pathname === pathname);
  },
};

async function toCall(input: RequestInfo | URL, init?: RequestInit): Promise<ApiCall> {
  if (input instanceof Request) {
    const body = input.method === 'GET' || input.method === 'HEAD' ? null : await input.clone().text();
    return { method: input.method.toUpperCase(), url: new URL(input.url), headers: new Headers(input.headers), body };
  }
  return {
    method: (init?.method ?? 'GET').toUpperCase(),
    url: new URL(input.toString(), window.location.origin),
    headers: new Headers(init?.headers),
    body: typeof init?.body === 'string' ? init.body : null,
  };
}

export function installApi(routes: Record<string, Handler>): void {
  api.calls = [];
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const call = await toCall(input, init);
      api.calls.push(call);
      const handler = routes[`${call.method} ${call.url.pathname}`];
      return handler ? handler(call) : response(404, problem(404, `not mocked: ${call.method} ${call.url.pathname}`));
    }),
  );
}
