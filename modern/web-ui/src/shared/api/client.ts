import type { FieldError, Problem } from './types';

/**
 * An answer of the gateway that is not a success. `status` is the HTTP status (401 = the session ended); `problem` is the
 * problem+json document of the answer when it had one: its `detail`, `code` and the `errors` of a 422 (D-001, D-024).
 */
export class ApiError extends Error {
  readonly status: number;
  readonly problem: Problem | undefined;

  constructor(status: number, message: string, problem?: Problem) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.problem = problem;
  }
}

export interface PostOptions {
  signal?: AbortSignal;
  /**
   * What a 401 does. true (default): the session ends and the user is sent to sign in again, as for a GET. false: the 401 is
   * only thrown as an ApiError, so that a form keeps what was typed and tells the user to sign in again (MAM-64 AC-6).
   */
  endSessionOn401?: boolean;
}

export interface GetOptions {
  /** As PostOptions.endSessionOn401: false throws the 401 to the page, which shows its own message (MAM-66, MAM-70). */
  endSessionOn401?: boolean;
}

export interface ApiClient {
  /** PUT a JSON body (replace a resource); errors as for POST. */
  put: <T>(path: string, body: unknown, options?: PostOptions) => Promise<T>;
  /** POST multipart/form-data (a legacy file and its parameters); the browser sets the boundary, so no Content-Type is sent. */
  postForm: <T>(path: string, form: FormData, options?: PostOptions) => Promise<T>;
  /** GET a file and keep its bytes exactly as received (a Blob, never a decoded string): the legacy files are fixed-width records. */
  download: (path: string, signal?: AbortSignal) => Promise<Blob>;
  get: <T>(path: string, signal?: AbortSignal, options?: GetOptions) => Promise<T>;
  /** POST a JSON body; a success answer is returned as JSON, anything else throws an ApiError. */
  post: <T>(path: string, body: unknown, options?: PostOptions) => Promise<T>;
}

export interface ApiClientOptions {
  /** The gateway address (configuration, never a literal in a screen). */
  baseUrl: string;
  /** The access token of the session, or null when there is none. */
  accessToken: () => Promise<string | null>;
  /** Called when the token is missing or the gateway answers 401. */
  onUnauthorized: () => Promise<void>;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function toFieldErrors(value: unknown): FieldError[] | undefined {
  if (!Array.isArray(value)) return undefined;
  return value.flatMap((item): FieldError[] => {
    if (!isRecord(item) || typeof item['code'] !== 'string' || typeof item['field'] !== 'string') return [];
    const message = item['message'];
    return [{ code: item['code'] as FieldError['code'], field: item['field'], ...(typeof message === 'string' ? { message } : {}) }];
  });
}

/** The problem+json document of an answer; undefined when the body is not one. */
async function readProblem(response: Response): Promise<Problem | undefined> {
  try {
    const body: unknown = await response.json();
    if (!isRecord(body)) return undefined;
    const errors = toFieldErrors(body['errors']);
    return {
      title: typeof body['title'] === 'string' ? body['title'] : `HTTP ${response.status}`,
      status: response.status,
      ...(typeof body['detail'] === 'string' ? { detail: body['detail'] } : {}),
      ...(typeof body['code'] === 'string' ? { code: body['code'] as NonNullable<Problem['code']> } : {}),
      ...(errors ? { errors } : {}),
    };
  } catch {
    return undefined; // no problem document in the body
  }
}

/** A client for the public endpoints (no sign-in): the same errors as the signed-in client, and never an Authorization header (ADR-006). */
export function createPublicApiClient(baseUrl: string): { get: <T>(path: string, signal?: AbortSignal) => Promise<T> } {
  return {
    async get<T>(path: string, signal?: AbortSignal): Promise<T> {
      const response = await fetch(new URL(path, baseUrl).toString(), { method: 'GET', headers: { Accept: 'application/json' }, ...(signal ? { signal } : {}) });
      if (!response.ok) {
        const problem = await readProblem(response);
        throw new ApiError(response.status, problem?.title ?? `HTTP ${response.status}`, problem);
      }
      return (await response.json()) as T;
    },
  };
}

interface Send {
  method: 'GET' | 'POST' | 'PUT';
  path: string;
  body?: unknown;
  form?: FormData;
  as?: 'json' | 'blob';
  signal?: AbortSignal;
  endSessionOn401: boolean;
}

/** The only place that talks to the gateway: the access token goes in the Authorization header and nowhere else. */
export function createApiClient(options: ApiClientOptions): ApiClient {
  async function send<T>({ method, path, body, form, as = 'json', signal, endSessionOn401 }: Send): Promise<T> {
    const notAuthenticated = async (): Promise<never> => {
      if (endSessionOn401) await options.onUnauthorized();
      throw new ApiError(401, 'Not authenticated');
    };
    const token = await options.accessToken();
    if (!token) return notAuthenticated();
    const response = await fetch(new URL(path, options.baseUrl).toString(), {
      method,
      headers: {
        Accept: 'application/json',
        Authorization: `Bearer ${token}`,
        ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
      },
      ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
      ...(form !== undefined ? { body: form } : {}),
      ...(signal ? { signal } : {}),
    });
    if (response.status === 401) return notAuthenticated();
    if (!response.ok) {
      const problem = await readProblem(response);
      throw new ApiError(response.status, problem?.title ?? `HTTP ${response.status}`, problem);
    }
    return (as === 'blob' ? await response.blob() : await response.json()) as T;
  }

  return {
    put: <T>(path: string, body: unknown, putOptions: PostOptions = {}) =>
      send<T>({ method: 'PUT', path, body, ...(putOptions.signal ? { signal: putOptions.signal } : {}), endSessionOn401: putOptions.endSessionOn401 ?? true }),
    postForm: <T>(path: string, form: FormData, postOptions: PostOptions = {}) =>
      send<T>({ method: 'POST', path, form, ...(postOptions.signal ? { signal: postOptions.signal } : {}), endSessionOn401: postOptions.endSessionOn401 ?? true }),
    download: (path: string, signal?: AbortSignal) => send<Blob>({ method: 'GET', path, as: 'blob', ...(signal ? { signal } : {}), endSessionOn401: true }),
    get: <T>(path: string, signal?: AbortSignal, getOptions: GetOptions = {}) =>
      send<T>({ method: 'GET', path, ...(signal ? { signal } : {}), endSessionOn401: getOptions.endSessionOn401 ?? true }),
    post: <T>(path: string, body: unknown, postOptions: PostOptions = {}) =>
      send<T>({
        method: 'POST',
        path,
        body,
        ...(postOptions.signal ? { signal: postOptions.signal } : {}),
        endSessionOn401: postOptions.endSessionOn401 ?? true,
      }),
  };
}
