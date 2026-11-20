// MAM-93 component-test helpers: the batch endpoints of six services (/api/v1/batch/...) as the console page /console/admin/jobs
// calls them, and the capture of what the page saves as a file. Synthetic data only.
import { screen, within } from '@testing-library/react';
import type { UserEvent } from '@testing-library/user-event';
import { vi } from 'vitest';
import type { components as EnrollmentSchemas } from '../../src/shared/api/generated/enrollment-service';
import { response, type ApiCall, type Handler } from './fakeApi';
import type { UserName } from './fixtures';
import { api, defaultRoutes, installApi, renderApp, signedInAs, text, type Rendered } from './world';

export type BatchRun = EnrollmentSchemas['schemas']['BatchRun'];

export const RUN_ID = '7a3c1e90-5b2d-4f68-9c04-1d8e6f2a3b51';

export function batchRun(overrides: Partial<BatchRun> = {}): BatchRun {
  return { runId: RUN_ID, job: 'JOB', businessDate: '2026-03-31', status: 'COMPLETED', counts: { read: 12, written: 10 }, startedAt: '2026-03-31T10:00:00Z', endedAt: '2026-03-31T10:00:05Z', ...overrides };
}

let counter = 0;
/** A run with its own id. */
export function newRun(overrides: Partial<BatchRun> = {}): BatchRun {
  counter += 1;
  return batchRun({ runId: `00000000-0000-4000-8000-${String(counter).padStart(12, '0')}`, ...overrides });
}

export const ENDPOINTS = {
  intake: '/api/v1/batch/intake-runs',
  sanctionIntake: '/api/v1/batch/sanction-intake-runs',
  screening: '/api/v1/batch/screening-runs',
  sweep: '/api/v1/batch/revalidation-sweeps',
  directory: '/api/v1/batch/directory-extracts',
  tmsis: '/api/v1/batch/tmsis-extracts',
} as const;

export function runsPage(items: BatchRun[]) {
  return { items, page: 0, size: 20, total: items.length };
}

/** Default answers: no T-MSIS run listed yet. */
export function batchRoutes(over: Record<string, Handler> = {}): Record<string, Handler> {
  return { ...defaultRoutes(), [`GET ${ENDPOINTS.tmsis}`]: () => response(200, runsPage([])), ...over };
}

export async function openJobs(routes: Record<string, Handler>, who: UserName = 'admin1'): Promise<Rendered> {
  signedInAs(who);
  installApi(routes);
  const rendered = renderApp('/console/admin/jobs');
  await screen.findByRole('heading', { level: 1, name: 'Batch jobs' });
  return rendered;
}

export const jobList = (): HTMLSelectElement => screen.getByLabelText('Job', { exact: true });
export const dateBox = (): HTMLInputElement => screen.getByLabelText('Business date (MM/DD/YYYY)', { exact: true });
export const fileBox = (): HTMLInputElement => screen.getByLabelText(/^File/);
export const batchIdBox = (): HTMLInputElement => screen.getByLabelText(/^Batch id/);
export const runButton = (): HTMLElement => screen.getByRole('button', { name: 'Run job' });

export async function runJob(user: UserEvent, job: string, date: string, extra: { file?: File; batchId?: string } = {}): Promise<void> {
  await user.selectOptions(jobList(), job);
  await user.clear(dateBox());
  if (date !== '') await user.type(dateBox(), date);
  if (extra.file) await user.upload(fileBox(), extra.file);
  if (extra.batchId) await user.type(batchIdBox(), extra.batchId);
  await user.click(runButton());
}

/** The table "Recent runs": rows as cell texts. */
export function runRows(): string[][] {
  const table = screen.queryAllByRole('table').find((candidate) => candidate.querySelector('caption')?.textContent === 'Recent runs');
  return table ? Array.from(table.querySelectorAll('tbody tr')).map((row) => Array.from(row.children).map((cell) => text(cell))) : [];
}

export function recentRuns(): HTMLElement {
  const table = screen.queryAllByRole('table').find((candidate) => candidate.querySelector('caption')?.textContent === 'Recent runs');
  if (!table) throw new Error('no "Recent runs" table');
  return table;
}

export function rowOf(job: string): HTMLElement {
  const row = Array.from(recentRuns().querySelectorAll('tbody tr')).find((candidate) => text(candidate.children[0]) === job);
  if (!row) throw new Error(`no run row for ${job}`);
  return row as HTMLElement;
}

export const downloadIn = (row: HTMLElement): HTMLElement => within(row).getByRole('button', { name: /^Download/ });

export function posts(path: string): ApiCall[] {
  return api.to('POST', path);
}

export function jsonBody(call: ApiCall | undefined): Record<string, unknown> {
  if (!call?.body) throw new Error('no JSON body was sent');
  return JSON.parse(call.body) as Record<string, unknown>;
}

// ---- multipart bodies: the recording stub keeps only string bodies, so FormData is captured here ------------------------

export interface SentForm {
  url: URL;
  headers: Headers;
  form: FormData;
}

/** Wrap the fetch stub that installApi put in place: every FormData body is recorded, then the request goes on. */
export function recordForms(): SentForm[] {
  const sent: SentForm[] = [];
  const inner = globalThis.fetch;
  vi.stubGlobal('fetch', (input: RequestInfo | URL, init?: RequestInit) => {
    if (init?.body instanceof FormData) sent.push({ url: new URL(input.toString(), window.location.origin), headers: new Headers(init.headers), form: init.body });
    return inner(input, init);
  });
  return sent;
}

// ---- files saved by the page ---------------------------------------------------------------------------------------------

export interface SavedFile {
  blob: Blob;
  name: string;
}

/** Capture what the page saves: the blob given to URL.createObjectURL and the download name of the clicked anchor. */
export function captureSaves(): SavedFile[] {
  const saved: SavedFile[] = [];
  let lastBlob: Blob | undefined;
  URL.createObjectURL = vi.fn((blob: Blob | MediaSource) => {
    lastBlob = blob as Blob;
    return 'blob:pem-test';
  });
  URL.revokeObjectURL = vi.fn();
  vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
    if (lastBlob) saved.push({ blob: lastBlob, name: this.getAttribute('download') ?? '' });
  });
  return saved;
}

/** The bytes of a Blob (jsdom or Node): arrayBuffer() when there is one, a FileReader otherwise. */
export async function bytesOf(blob: Blob): Promise<number[]> {
  if (typeof blob.arrayBuffer === 'function') return Array.from(new Uint8Array(await blob.arrayBuffer()));
  const buffer = await new Promise<ArrayBuffer>((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result as ArrayBuffer);
    reader.onerror = () => reject(reader.error);
    reader.readAsArrayBuffer(blob);
  });
  return Array.from(new Uint8Array(buffer));
}

/** A file answer with bytes that a text conversion would damage (an e-acute in Latin-1, a NUL, CR LF, trailing blanks). */
export const LEGACY_BYTES = [0x31, 0x30, 0x30, 0x33, 0x30, 0x30, 0x30, 0x31, 0x31, 0x31, 0xe9, 0x00, 0x20, 0x20, 0x0d, 0x0a, 0xff];

export function fileAnswer(bytes: number[] = LEGACY_BYTES): Response {
  return new Response(new Uint8Array(bytes), { status: 200, headers: { 'content-type': 'text/plain' } });
}

export const legacyFile = (name = 'APPINPUT.txt'): File => new File(['1003000111IN...'], name, { type: 'text/plain' });
