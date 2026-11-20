// MAM-93 AC-4, AC-5, AC-6 and AC-8 (component level), added 2026-11-20 on the user's order (19:32, item 5): the Batch jobs page
// /console/admin/jobs must move a T-MSIS run off "Running" without a reload.
// The T-MSIS job has no run-status call in its contract (a list and a file only), so the page follows the run it started through
// the list GET /api/v1/batch/tmsis-extracts: the status the service lists for that runId replaces the stale local copy (the POST
// answer is always RUNNING) until the run is Completed or Failed, with a bounded number of list requests and no request storm.
// Which AC: AC-4 says the page follows a started run until it is Completed or Failed and shows the counts the service returns
// (for the file intakes); AC-5 says a failure is shown with its text and never dropped or shown as Completed; AC-6 says a Completed
// run has a Download button; AC-8 is the accessibility structure. MAM-83 AC-1 (journey J7) needs the admin to download the file of
// the run just started, which is the AC-6 test below. The journey itself is e2e/journeys/j7-extracts.spec.ts.
// The tests use real timers like tests/admin/batch-jobs.test.tsx; the polling bound is POLL_INTERVAL_MS (1 s) of the page.
import { waitFor, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { POLL_INTERVAL_MS } from '../../src/features/console/admin/jobs/JobsPage';
import { problem, response } from '../support/fakeApi';
import { api, oidc, setupShellWorld, text } from '../support/world';
import {
  batchRoutes,
  batchRun,
  bytesOf,
  captureSaves,
  dateBox,
  downloadIn,
  ENDPOINTS,
  fileAnswer,
  LEGACY_BYTES,
  newRun,
  openJobs,
  recentRuns,
  runJob,
  runRows,
  runsPage,
  type BatchRun,
} from '../support/batch';

vi.mock('oidc-client-ts', async (importOriginal) => {
  const actual = await importOriginal<typeof import('oidc-client-ts')>();
  const { FakeUserManager } = await import('../support/fakeOidc');
  return { ...actual, UserManager: FakeUserManager };
});

setupShellWorld();
afterEach(() => vi.restoreAllMocks());

/** The page may take a few poll intervals to show what the service lists now. */
const FOLLOW_WITHIN = POLL_INTERVAL_MS * 5;
/** A test that waits for one or more polls of real time. */
const TEST_TIMEOUT = 20_000;
const NOTE = 'The status could not be refreshed';
const OLDER_BYTES = [0x4f, 0x4c, 0x44, 0x45, 0x52]; // the file of the older run: it must never be saved for the new run
const NEW_DATE = '31 Mar 2026';
const OLD_DATE = '28 Feb 2026';

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

/** The T-MSIS service as the page sees it: the list, the start and the two files. The test changes `state.listed` while the page runs. */
function tmsisWorld(options: { publishOnStart?: boolean } = {}) {
  const earlier = newRun({ businessDate: '2026-02-28', counts: { rows: 40, archiveGeneration: 6 } });
  const started = newRun({ businessDate: '2026-03-31', status: 'RUNNING', counts: undefined });
  const state = { listed: [earlier] as BatchRun[], unreadable: false };
  const routes = batchRoutes({
    // A little latency, as on a network: the page can show that a read is under way, and it has to keep the screen reader quiet then.
    [`GET ${ENDPOINTS.tmsis}`]: async () => {
      await sleep(40);
      return state.unreadable ? response(500, problem(500, 'Internal Server Error')) : response(200, runsPage(state.listed));
    },
    [`POST ${ENDPOINTS.tmsis}`]: () => {
      if (options.publishOnStart !== false) state.listed = [started, earlier];
      return response(202, started);
    },
    [`GET ${ENDPOINTS.tmsis}/${started.runId}/file`]: () => fileAnswer(),
    [`GET ${ENDPOINTS.tmsis}/${earlier.runId}/file`]: () => fileAnswer(OLDER_BYTES),
    [`POST ${ENDPOINTS.screening}`]: () => response(202, newRun()),
  });
  const completed = (counts: Record<string, number> = { rows: 12, archiveGeneration: 7 }): BatchRun => ({ ...started, status: 'COMPLETED', counts, endedAt: '2026-03-31T10:00:09Z' });
  const failed = (error: string): BatchRun => ({ ...started, status: 'FAILED', counts: undefined, error });
  return { earlier, started, state, routes, completed, failed };
}

type World = ReturnType<typeof tmsisWorld>;

const tmsisRows = () => runRows().filter((row) => row[0] === 'T-MSIS extract');
const rowOfDate = (date: string) => tmsisRows().find((row) => row[1] === date);
const rowElement = (date: string): HTMLElement => {
  const row = Array.from(recentRuns().querySelectorAll('tbody tr')).find((candidate) => text(candidate.children[0]) === 'T-MSIS extract' && text(candidate.children[1]) === date);
  if (!row) throw new Error(`no T-MSIS row for ${date}`);
  return row as HTMLElement;
};
const listReads = () => api.to('GET', ENDPOINTS.tmsis).length;

/** The page is open, the older run is listed, "T-MSIS extract" for 03/31/2026 is started and its row reads Running (what the page shows today). */
async function startRun(world: World) {
  const { user } = await openJobs(world.routes);
  await waitFor(() => expect(tmsisRows().map((row) => row[1])).toEqual([OLD_DATE]));
  await runJob(user, 'T-MSIS extract', '03/31/2026');
  await waitFor(() => expect(rowOfDate(NEW_DATE)?.[2]).toBe('Running'));
  return user;
}

/** The service now lists the started run as Completed (or Failed); the page must show it by itself, without a reload. */
async function untilStatus(date: string, status: string) {
  await waitFor(() => expect(rowOfDate(date)?.[2]).toBe(status), { timeout: FOLLOW_WITHIN });
}

describe('[MAM-93 AC-4] the T-MSIS run just started is followed until it ends, without a reload', () => {
  it('[MAM-93 AC-4] the row turns from Running to Completed when the service list reports the run Completed', async () => {
    const world = tmsisWorld();
    await startRun(world);

    world.state.listed = [world.completed(), world.earlier];

    await untilStatus(NEW_DATE, 'Completed');
  }, TEST_TIMEOUT);

  it('[MAM-93 AC-4] the finished row shows the counts the service lists for the run (rows and archive generation)', async () => {
    const world = tmsisWorld();
    await startRun(world);

    world.state.listed = [world.completed({ rows: 12, archiveGeneration: 7 }), world.earlier];

    await waitFor(() => expect(rowOfDate(NEW_DATE)?.[3]).toBe('rows 12, archiveGeneration 7'), { timeout: FOLLOW_WITHIN });
  }, TEST_TIMEOUT);

  it('[MAM-93 AC-4] the finished run keeps ONE row (the listed copy does not add a second one) and the older run keeps its own', async () => {
    const world = tmsisWorld();
    await startRun(world);

    world.state.listed = [world.completed(), world.earlier];

    await untilStatus(NEW_DATE, 'Completed');
    expect(tmsisRows().map((row) => row.slice(1, 3)).sort((a, b) => (a[0] ?? '').localeCompare(b[0] ?? ''))).toEqual([
      [OLD_DATE, 'Completed'],
      [NEW_DATE, 'Completed'],
    ]);
  }, TEST_TIMEOUT);

  it('[MAM-93 AC-4] the page is not reloaded: the form still holds the business date that was typed', async () => {
    const world = tmsisWorld();
    await startRun(world);

    world.state.listed = [world.completed(), world.earlier];

    await untilStatus(NEW_DATE, 'Completed');
    expect(dateBox()).toHaveValue('03/31/2026');
  }, TEST_TIMEOUT);

  it('[MAM-93 AC-4] a run that the list does not show yet keeps its Running row (not dropped, not doubled) until the list reports it', async () => {
    const world = tmsisWorld({ publishOnStart: false });
    await startRun(world);
    const reads = listReads();

    await waitFor(() => expect(listReads()).toBeGreaterThanOrEqual(reads + 2), { timeout: FOLLOW_WITHIN });
    expect(tmsisRows().filter((row) => row[1] === NEW_DATE).map((row) => row[2])).toEqual(['Running']);
    world.state.listed = [world.completed(), world.earlier];

    await untilStatus(NEW_DATE, 'Completed');
    expect(tmsisRows().filter((row) => row[1] === NEW_DATE)).toHaveLength(1);
  }, TEST_TIMEOUT);
});

describe('[MAM-93 AC-4] the page asks for the T-MSIS list at a bounded rate', () => {
  it('[MAM-93 AC-4] while the run is Running the list is read about once per poll interval: steadily, and no request storm', async () => {
    const world = tmsisWorld();
    await startRun(world);
    await sleep(300); // the read that follows the start settles
    const span = 3500;
    const before = listReads();

    await sleep(span);

    const reads = listReads() - before;
    expect(reads).toBeGreaterThanOrEqual(Math.floor(span / POLL_INTERVAL_MS) - 1);
    expect(reads).toBeLessThanOrEqual(Math.ceil(span / POLL_INTERVAL_MS) + 1);
  }, TEST_TIMEOUT);

  it('[MAM-93 AC-4] once the run is Completed the page stops asking: no further list request', async () => {
    const world = tmsisWorld();
    await startRun(world);
    world.state.listed = [world.completed(), world.earlier];
    await untilStatus(NEW_DATE, 'Completed');
    await sleep(300);
    const reads = listReads();

    await sleep(2500);

    expect(listReads()).toBe(reads);
  }, TEST_TIMEOUT);

  it('[MAM-93 AC-4] once the run is Failed the page stops asking: no further list request', async () => {
    const world = tmsisWorld();
    await startRun(world);
    world.state.listed = [world.failed('The T-MSIS extract could not read the provider master'), world.earlier];
    await untilStatus(NEW_DATE, 'Failed');
    await sleep(300);
    const reads = listReads();

    await sleep(2500);

    expect(listReads()).toBe(reads);
  }, TEST_TIMEOUT);
});

describe('[MAM-93 AC-5] a failed T-MSIS run and an unreadable list are shown, never dropped or shown as Completed', () => {
  it('[MAM-93 AC-5] a run the list reports FAILED shows the status "Failed" and the error text returned for it', async () => {
    const world = tmsisWorld();
    await startRun(world);

    world.state.listed = [world.failed('The T-MSIS extract could not read the provider master'), world.earlier];

    await untilStatus(NEW_DATE, 'Failed');
    expect(rowOfDate(NEW_DATE)?.slice(2, 5)).toEqual(['Failed', '', 'The T-MSIS extract could not read the provider master']);
  }, TEST_TIMEOUT);

  it('[MAM-93 AC-5] a Failed run has no Download button', async () => {
    const world = tmsisWorld();
    await startRun(world);

    world.state.listed = [world.failed('The T-MSIS extract could not read the provider master'), world.earlier];

    await untilStatus(NEW_DATE, 'Failed');
    expect(within(rowElement(NEW_DATE)).queryByRole('button', { name: /^Download/ })).toBeNull();
  }, TEST_TIMEOUT);

  it('[MAM-93 AC-5] when the list cannot be read the row says "The status could not be refreshed"', async () => {
    const world = tmsisWorld();
    await startRun(world);

    world.state.unreadable = true;

    await waitFor(() => expect(rowOfDate(NEW_DATE)?.[4]).toBe(NOTE), { timeout: FOLLOW_WITHIN });
  }, TEST_TIMEOUT);

  it('[MAM-93 AC-5] when the list cannot be read the status is not guessed: the row still reads Running', async () => {
    const world = tmsisWorld();
    await startRun(world);

    world.state.unreadable = true;

    await waitFor(() => expect(rowOfDate(NEW_DATE)?.[4]).toBe(NOTE), { timeout: FOLLOW_WITHIN });
    expect(rowOfDate(NEW_DATE)?.[2]).toBe('Running');
  }, TEST_TIMEOUT);

  it('[MAM-93 AC-5] while the row says the status could not be refreshed, the page keeps working: another job still runs and adds its row', async () => {
    const world = tmsisWorld();
    const user = await startRun(world);
    world.state.unreadable = true;
    await waitFor(() => expect(rowOfDate(NEW_DATE)?.[4]).toBe(NOTE), { timeout: FOLLOW_WITHIN });

    await runJob(user, 'Screening run', '03/31/2026');

    await waitFor(() => expect(runRows().some((row) => row[0] === 'Screening run')).toBe(true));
  }, TEST_TIMEOUT);

  it('[MAM-93 AC-5] when the list can be read again the row shows the finished run and the failure note is gone', async () => {
    const world = tmsisWorld();
    await startRun(world);
    world.state.unreadable = true;
    await waitFor(() => expect(rowOfDate(NEW_DATE)?.[4]).toBe(NOTE), { timeout: FOLLOW_WITHIN });

    world.state.unreadable = false;
    world.state.listed = [world.completed(), world.earlier];

    await waitFor(() => expect(rowOfDate(NEW_DATE)?.slice(2, 5)).toEqual(['Completed', 'rows 12, archiveGeneration 7', '']), { timeout: FOLLOW_WITHIN });
  }, TEST_TIMEOUT);
});

describe('[MAM-93 AC-6][MAM-83 AC-1] the Download of the followed run is the file of that run', () => {
  it('[MAM-93 AC-6][MAM-83 AC-1] the finished run offers Download, which requests the file of THAT run (not the older run) and saves it as TMSISOUT.D20260331 byte for byte', async () => {
    const saved = captureSaves();
    const world = tmsisWorld();
    const user = await startRun(world);
    world.state.listed = [world.completed(), world.earlier];
    await untilStatus(NEW_DATE, 'Completed');

    await user.click(downloadIn(rowElement(NEW_DATE)));

    await waitFor(() => expect(saved).toHaveLength(1));
    const request = api.to('GET', `${ENDPOINTS.tmsis}/${world.started.runId}/file`)[0];
    expect(request?.headers.get('authorization')).toBe(`Bearer ${oidc.user?.access_token}`);
    expect(await bytesOf(saved[0]!.blob)).toEqual(LEGACY_BYTES);
    expect(saved[0]!.name).toBe('TMSISOUT.D20260331');
    expect(api.to('GET', `${ENDPOINTS.tmsis}/${world.earlier.runId}/file`)).toHaveLength(0);
  }, TEST_TIMEOUT);
});

describe('[MAM-93 AC-4] the follow behaviour of the other jobs is unchanged', () => {
  it.each([
    ['Screening run', ENDPOINTS.screening, 'Screening run'],
    ['Revalidation sweep', ENDPOINTS.sweep, 'Revalidation sweep'],
    ['Directory extract (DIROUT)', ENDPOINTS.directory, 'Directory extract'],
  ] as const)('[MAM-93 AC-4] %s: the run is refreshed from GET %s/{runId} until it is Completed with the counts of the service, and the T-MSIS list is not read again for it', async (job, path, label) => {
    const run = newRun({ status: 'RUNNING', counts: undefined });
    const answers = [batchRun({ ...run, status: 'RUNNING', counts: { read: 5 } }), batchRun({ ...run, status: 'COMPLETED', counts: { read: 12, written: 10 } })];
    const world = tmsisWorld();
    const routes = {
      ...world.routes,
      [`POST ${path}`]: () => response(202, run),
      [`GET ${path}/${run.runId}`]: () => response(200, answers.length > 1 ? answers.shift() : answers[0]),
    };
    const { user } = await openJobs(routes);
    await waitFor(() => expect(tmsisRows()).toHaveLength(1));
    const reads = listReads();

    await runJob(user, job, '03/31/2026');

    await waitFor(() => expect(runRows().find((row) => row[0] === label)?.[2]).toBe('Completed'), { timeout: 8000 });
    expect(runRows().find((row) => row[0] === label)?.[3]).toBe('read 12, written 10');
    expect(api.to('GET', `${path}/${run.runId}`).length).toBeGreaterThanOrEqual(2);
    expect(listReads()).toBe(reads);
  }, 14_000);
});

// ---- screen readers: what is announced while the page follows a run ---------------------------------------------------------
// The page has no live region of its own for run status. Its live regions are the loading message "Loading recent runs"
// (role status), the alerts (role alert) and the file note. The rule that exists, and that the follow must keep, is that no text
// is announced twice: a poll must not make a message appear again.

const LIVE = '[role="status"], [role="alert"], [aria-live]';

/** Records every live-region text that APPEARS in the page (a region added, or its text changed to something it did not say). */
function watchLiveRegions() {
  const snapshot = () =>
    Array.from(document.querySelectorAll(LIVE))
      .filter((region) => region.getAttribute('aria-live') !== 'off')
      .map((region) => text(region))
      .filter((value) => value !== '');
  let previous = snapshot();
  const appeared: string[] = [];
  const observer = new MutationObserver(() => {
    const remaining = [...previous];
    const now = snapshot();
    for (const value of now) {
      const index = remaining.indexOf(value);
      if (index >= 0) remaining.splice(index, 1);
      else appeared.push(value);
    }
    previous = now;
  });
  observer.observe(document.body, { subtree: true, childList: true, characterData: true, attributes: true, attributeFilter: ['role', 'aria-live'] });
  return {
    appeared,
    async stop() {
      await sleep(0); // let the observer deliver the last records
      observer.disconnect();
    },
  };
}

const repeated = (values: string[]) => values.filter((value, index) => values.indexOf(value) !== index);

describe('[MAM-93 AC-8] nothing is announced twice to a screen reader while a T-MSIS run is followed', () => {
  it('[MAM-93 AC-8] following the run from Running to Completed makes no live-region text appear twice', async () => {
    const world = tmsisWorld();
    await startRun(world);
    const watcher = watchLiveRegions();
    await sleep(POLL_INTERVAL_MS * 2.2); // two polls that still say Running
    world.state.listed = [world.completed(), world.earlier];

    await untilStatus(NEW_DATE, 'Completed');

    await watcher.stop();
    expect(repeated(watcher.appeared)).toEqual([]);
  }, TEST_TIMEOUT);

  it('[MAM-93 AC-8] while the list keeps failing, the failure is announced once and not again on every poll', async () => {
    const world = tmsisWorld();
    await startRun(world);
    const watcher = watchLiveRegions();
    const reads = listReads();
    world.state.unreadable = true;

    await waitFor(() => expect(listReads()).toBeGreaterThanOrEqual(reads + 3), { timeout: POLL_INTERVAL_MS * 6 });

    await watcher.stop();
    expect(repeated(watcher.appeared)).toEqual([]);
  }, TEST_TIMEOUT);
});
