// MAM-93 AC-1..AC-7 (component level): the admin page /console/admin/jobs runs the batch jobs of the old system on demand for an
// explicit business date (D-020), follows the file intakes (D-017), shows failed runs with their error (D-024) and downloads the
// legacy-format files byte for byte (ADR-005, MODERNIZATION.md rule 3). Only administrators open it (D-023).
import { screen, waitFor, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { problem, response, type Handler } from '../support/fakeApi';
import { api, oidc, installApi, renderApp, setupShellWorld, signedInAs, text } from '../support/world';
import {
  batchIdBox,
  batchRoutes,
  batchRun,
  bytesOf,
  captureSaves,
  dateBox,
  downloadIn,
  ENDPOINTS,
  fileAnswer,
  fileBox,
  jobList,
  jsonBody,
  LEGACY_BYTES,
  legacyFile,
  newRun,
  openJobs,
  posts,
  recentRuns,
  recordForms,
  rowOf,
  runButton,
  runJob,
  runRows,
  runsPage,
} from '../support/batch';

vi.mock('oidc-client-ts', async (importOriginal) => {
  const actual = await importOriginal<typeof import('oidc-client-ts')>();
  const { FakeUserManager } = await import('../support/fakeOidc');
  return { ...actual, UserManager: FakeUserManager };
});

setupShellWorld();
afterEach(() => vi.restoreAllMocks());

const accept = (run = batchRun()): Handler => () => response(202, run);

describe('[MAM-93 AC-1] the form asks for an explicit business date and, where needed, a file', () => {
  it('[MAM-93 AC-1] the page shows an empty "Business date (MM/DD/YYYY)", the Job list, "Run job" and the table "Recent runs"', async () => {
    await openJobs(batchRoutes());

    expect(dateBox()).toHaveValue('');
    expect(Array.from(jobList().options).map((option) => option.textContent)).toEqual([
      'Choose...',
      'Enrolment intake (APPINPUT)',
      'Screening run',
      'Revalidation sweep',
      'Sanction intake (SANCIN)',
      'Directory extract (DIROUT)',
      'T-MSIS extract',
    ]);
    expect(runButton()).toBeEnabled();
    expect(within(recentRuns()).getAllByRole('columnheader').map((header) => text(header))).toEqual(['Job', 'Business date', 'Status', 'Counts', 'Error', 'File']);
  });

  it('[MAM-93 AC-1] the page does not fill in the date, also after a job is picked', async () => {
    const { user } = await openJobs(batchRoutes());

    await user.selectOptions(jobList(), 'Screening run');

    expect(dateBox()).toHaveValue('');
  });

  it.each(['Enrolment intake (APPINPUT)', 'Sanction intake (SANCIN)'])('[MAM-93 AC-1] picking %s adds a required file input and an optional Batch id of at most 8 characters', async (job) => {
    const { user } = await openJobs(batchRoutes());
    expect(screen.queryByLabelText(/^File/)).toBeNull();
    expect(screen.queryByLabelText(/^Batch id/)).toBeNull();

    await user.selectOptions(jobList(), job);

    expect(fileBox()).toBeRequired();
    expect(fileBox()).toHaveAttribute('type', 'file');
    expect(batchIdBox()).toHaveAttribute('maxlength', '8');
    expect(batchIdBox()).not.toBeRequired();
  });

  it.each(['Screening run', 'Revalidation sweep', 'Directory extract (DIROUT)', 'T-MSIS extract'])('[MAM-93 AC-1] picking %s needs no file and no Batch id', async (job) => {
    const { user } = await openJobs(batchRoutes());

    await user.selectOptions(jobList(), job);

    expect(screen.queryByLabelText(/^File/)).toBeNull();
    expect(screen.queryByLabelText(/^Batch id/)).toBeNull();
  });

  it('[MAM-93 AC-1] "Run job" with nothing chosen sends nothing and shows field errors and a summary', async () => {
    const { user } = await openJobs(batchRoutes());

    await user.click(runButton());

    expect(await screen.findByRole('heading', { level: 2, name: '2 errors in the job' })).toBeInTheDocument();
    expect(api.calls.filter((call) => call.method === 'POST')).toHaveLength(0);
    expect(jobList()).toHaveAttribute('aria-invalid', 'true');
    expect(dateBox()).toHaveAttribute('aria-invalid', 'true');
    expect(within(screen.getByRole('main')).getByRole('link', { name: 'Choose a job' })).toBeInTheDocument();
    expect(within(screen.getByRole('main')).getByRole('link', { name: 'Enter the business date as MM/DD/YYYY' })).toBeInTheDocument();
  });

  it.each(['02/30/2026', '13/01/2026', '2026-03-31'])('[MAM-93 AC-1] the impossible date "%s" is not sent', async (date) => {
    const { user } = await openJobs(batchRoutes());

    await runJob(user, 'Revalidation sweep', date);

    expect(await screen.findByRole('heading', { level: 2, name: '1 error in the job' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Enter a real date as MM/DD/YYYY' })).toBeInTheDocument();
    expect(api.calls.filter((call) => call.method === 'POST')).toHaveLength(0);
  });

  it('[MAM-93 AC-1] an intake job without a file sends nothing and asks for the file', async () => {
    const { user } = await openJobs(batchRoutes());

    await runJob(user, 'Enrolment intake (APPINPUT)', '03/31/2026');

    expect(await screen.findByRole('heading', { level: 2, name: '1 error in the job' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Choose the legacy file' })).toBeInTheDocument();
    expect(api.calls.filter((call) => call.method === 'POST')).toHaveLength(0);
  });
});

describe('[MAM-93 AC-2] run the revalidation sweep (journey J6)', () => {
  it('[MAM-93 AC-2] "Revalidation sweep", 03/31/2026 and "Run job" send POST /api/v1/batch/revalidation-sweeps with businessDate 2026-03-31', async () => {
    const { user } = await openJobs(batchRoutes({ [`POST ${ENDPOINTS.sweep}`]: accept(batchRun({ status: 'RUNNING', counts: undefined })) }));

    await runJob(user, 'Revalidation sweep', '03/31/2026');

    await waitFor(() => expect(posts(ENDPOINTS.sweep)).toHaveLength(1));
    expect(jsonBody(posts(ENDPOINTS.sweep)[0])).toEqual({ businessDate: '2026-03-31' });
    expect(posts(ENDPOINTS.sweep)[0]?.headers.get('content-type')).toMatch(/application\/json/);
  });

  it.each([
    ['RUNNING', 'Running'],
    ['COMPLETED', 'Completed'],
    ['FAILED', 'Failed'],
  ] as const)('[MAM-93 AC-2] the new row reads Job "Revalidation sweep", Business date 31 Mar 2026 and the status %s as "%s" with the counts', async (status, label) => {
    const { user } = await openJobs(batchRoutes({ [`POST ${ENDPOINTS.sweep}`]: accept(batchRun({ status, counts: { opened: 3, skipped: 1 }, error: status === 'FAILED' ? 'The sweep stopped' : null })) }));

    await runJob(user, 'Revalidation sweep', '03/31/2026');

    await waitFor(() => expect(runRows().filter((row) => row[0] === 'Revalidation sweep')).toHaveLength(1));
    const row = runRows().find((candidate) => candidate[0] === 'Revalidation sweep')!;
    expect(row.slice(0, 4)).toEqual(['Revalidation sweep', '31 Mar 2026', label, 'opened 3, skipped 1']);
  });

  it('[MAM-93 AC-2] a second run adds a second row', async () => {
    const { user } = await openJobs(batchRoutes({ [`POST ${ENDPOINTS.sweep}`]: () => response(202, newRun()) }));

    await runJob(user, 'Revalidation sweep', '03/31/2026');
    await waitFor(() => expect(runRows().filter((row) => row[0] === 'Revalidation sweep')).toHaveLength(1));
    await runJob(user, 'Revalidation sweep', '04/30/2026');

    await waitFor(() => expect(runRows().filter((row) => row[0] === 'Revalidation sweep')).toHaveLength(2));
  });
});

describe('[MAM-93 AC-3] run the screening, directory and T-MSIS jobs (journey J7)', () => {
  it.each([
    ['Screening run', ENDPOINTS.screening],
    ['Directory extract (DIROUT)', ENDPOINTS.directory],
    ['T-MSIS extract', ENDPOINTS.tmsis],
  ] as const)('[MAM-93 AC-3] %s sends POST %s with businessDate 2026-03-31 and adds a row', async (job, path) => {
    const { user } = await openJobs(batchRoutes({ [`POST ${path}`]: () => response(202, newRun()) }));

    await runJob(user, job, '03/31/2026');

    await waitFor(() => expect(posts(path)).toHaveLength(1));
    expect(jsonBody(posts(path)[0])).toEqual({ businessDate: '2026-03-31' });
    const label = job === 'Directory extract (DIROUT)' ? 'Directory extract' : job;
    await waitFor(() => expect(runRows().some((row) => row[0] === label && row[1] === '31 Mar 2026')).toBe(true));
  });

  it('[MAM-93 AC-3] the T-MSIS runs are listed from GET /api/v1/batch/tmsis-extracts and each new T-MSIS run adds a new row there', async () => {
    const earlier = newRun({ businessDate: '2026-02-28' });
    const created = newRun({ businessDate: '2026-03-31' });
    let started = false;
    const { user } = await openJobs(
      batchRoutes({
        [`GET ${ENDPOINTS.tmsis}`]: () => response(200, runsPage(started ? [created, earlier] : [earlier])),
        [`POST ${ENDPOINTS.tmsis}`]: () => {
          started = true;
          return response(202, created);
        },
      }),
    );
    await waitFor(() => expect(runRows().filter((row) => row[0] === 'T-MSIS extract')).toHaveLength(1));
    expect(runRows().find((row) => row[0] === 'T-MSIS extract')?.[1]).toBe('28 Feb 2026');

    await runJob(user, 'T-MSIS extract', '03/31/2026');

    await waitFor(() => expect(runRows().filter((row) => row[0] === 'T-MSIS extract')).toHaveLength(2));
    expect(runRows().filter((row) => row[0] === 'T-MSIS extract').map((row) => row[1]).sort()).toEqual(['28 Feb 2026', '31 Mar 2026']);
    expect(api.to('GET', ENDPOINTS.tmsis).length).toBeGreaterThan(1);
  });

  it('[MAM-93 AC-3] when the T-MSIS list cannot be loaded the page says so with Retry and the other jobs still run', async () => {
    const { user } = await openJobs(
      batchRoutes({ [`GET ${ENDPOINTS.tmsis}`]: () => response(404, problem(404, 'Not Found')), [`POST ${ENDPOINTS.screening}`]: () => response(202, newRun()) }),
    );

    expect(await screen.findByText('The T-MSIS runs could not be listed')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument();
    await runJob(user, 'Screening run', '03/31/2026');
    await waitFor(() => expect(runRows().some((row) => row[0] === 'Screening run')).toBe(true));
  });
});

describe('[MAM-93 AC-4] file intake runs upload the file and follow the run', () => {
  it.each([
    ['Enrolment intake (APPINPUT)', ENDPOINTS.intake, 'Enrolment intake'],
    ['Sanction intake (SANCIN)', ENDPOINTS.sanctionIntake, 'Sanction intake'],
  ] as const)('[MAM-93 AC-4] %s sends a multipart POST %s with the file, businessDate 2026-03-31 and batchId B0331', async (job, path, label) => {
    const run = newRun({ status: 'COMPLETED' });
    const { user } = await openJobs(batchRoutes({ [`POST ${path}`]: () => response(202, run) }));
    const forms = recordForms();
    const file = legacyFile();

    await runJob(user, job, '03/31/2026', { file, batchId: 'B0331' });

    await waitFor(() => expect(forms).toHaveLength(1));
    expect(forms[0]?.url.pathname).toBe(path);
    expect((forms[0]?.form.get('file') as File).name).toBe('APPINPUT.txt');
    expect(forms[0]?.form.get('businessDate')).toBe('2026-03-31');
    expect(forms[0]?.form.get('batchId')).toBe('B0331');
    expect(forms[0]?.headers.get('authorization')).toMatch(/^Bearer \S+/);
    expect(forms[0]?.headers.get('content-type')).toBeNull(); // the browser sets the multipart boundary
    await waitFor(() => expect(runRows().some((row) => row[0] === label)).toBe(true));
  });

  it('[MAM-93 AC-4] without a batch id none is sent', async () => {
    const { user } = await openJobs(batchRoutes({ [`POST ${ENDPOINTS.intake}`]: () => response(202, newRun()) }));
    const forms = recordForms();

    await runJob(user, 'Enrolment intake (APPINPUT)', '03/31/2026', { file: legacyFile() });

    await waitFor(() => expect(forms).toHaveLength(1));
    expect(forms[0]?.form.has('batchId')).toBe(false);
  });

  it.each([
    ['Enrolment intake (APPINPUT)', ENDPOINTS.intake, 'Enrolment intake'],
    ['Sanction intake (SANCIN)', ENDPOINTS.sanctionIntake, 'Sanction intake'],
  ] as const)('[MAM-93 AC-4] %s: the run is refreshed from GET %s/{runId} until it is Completed, and the counts of the service are shown', async (job, path, label) => {
    const run = newRun({ status: 'RUNNING', counts: undefined });
    const answers = [batchRun({ ...run, status: 'RUNNING', counts: { read: 5 } }), batchRun({ ...run, status: 'COMPLETED', counts: { read: 12, written: 10, errors: 2 } })];
    const { user } = await openJobs(
      batchRoutes({
        [`POST ${path}`]: () => response(202, run),
        [`GET ${path}/${run.runId}`]: () => response(200, answers.length > 1 ? answers.shift() : answers[0]),
      }),
    );

    await runJob(user, job, '03/31/2026', { file: legacyFile(), batchId: 'B0331' });

    await waitFor(() => expect(runRows().find((row) => row[0] === label)?.[2]).toBe('Completed'), { timeout: 8000 });
    expect(runRows().find((row) => row[0] === label)?.[3]).toBe('read 12, written 10, errors 2');
    expect(api.to('GET', `${path}/${run.runId}`).length).toBeGreaterThanOrEqual(2);
  }, 12000);

  it('[MAM-93 AC-4] a run that is Failed after the refresh shows its error text and is not polled again', async () => {
    const run = newRun({ status: 'RUNNING', counts: undefined });
    const { user } = await openJobs(
      batchRoutes({
        [`POST ${ENDPOINTS.intake}`]: () => response(202, run),
        [`GET ${ENDPOINTS.intake}/${run.runId}`]: () => response(200, batchRun({ ...run, status: 'FAILED', error: 'Record 3 has 120 bytes, not 121' })),
      }),
    );

    await runJob(user, 'Enrolment intake (APPINPUT)', '03/31/2026', { file: legacyFile() });

    await waitFor(() => expect(runRows().find((row) => row[0] === 'Enrolment intake')?.[2]).toBe('Failed'), { timeout: 8000 });
    expect(runRows().find((row) => row[0] === 'Enrolment intake')?.[4]).toBe('Record 3 has 120 bytes, not 121');
    const calls = api.to('GET', `${ENDPOINTS.intake}/${run.runId}`).length;
    await new Promise((resolve) => setTimeout(resolve, 2500));
    expect(api.to('GET', `${ENDPOINTS.intake}/${run.runId}`)).toHaveLength(calls);
  }, 14000);
});

describe('[MAM-93 AC-5] failed runs and refusals are shown', () => {
  it('[MAM-93 AC-5] a run that ends FAILED shows the status "Failed" and the error text returned for it, never "Completed"', async () => {
    const failed = batchRun({ status: 'FAILED', counts: undefined, error: 'The T-MSIS extract could not read the provider master' });
    const { user } = await openJobs(batchRoutes({ [`POST ${ENDPOINTS.tmsis}`]: accept(failed) }));

    await runJob(user, 'T-MSIS extract', '03/31/2026');

    await waitFor(() => expect(runRows().some((row) => row[0] === 'T-MSIS extract')).toBe(true));
    const row = runRows().find((candidate) => candidate[0] === 'T-MSIS extract')!;
    expect(row[2]).toBe('Failed');
    expect(row[4]).toBe('The T-MSIS extract could not read the provider master');
    expect(row.join(' ')).not.toContain('Completed');
  });

  it.each([
    ['400', () => response(400, { title: 'Bad Request', status: 400, detail: 'businessDate is required (message of this test)' })],
    ['422', () => response(422, { title: 'Unprocessable Entity', status: 422, detail: 'The file is not a 200-byte APPINPUT file (message of this test)' })],
  ])('[MAM-93 AC-5] a %s shows the problem detail in an error summary at the form and adds no row', async (_label, answer) => {
    const { user } = await openJobs(batchRoutes({ [`POST ${ENDPOINTS.screening}`]: answer }));

    await runJob(user, 'Screening run', '03/31/2026');

    const heading = await screen.findByRole('heading', { level: 2, name: 'The job could not be started' });
    await waitFor(() => expect(document.activeElement?.contains(heading)).toBe(true));
    expect(within(document.activeElement as HTMLElement).getByText(/message of this test/)).toBeInTheDocument();
    expect(runRows().some((row) => row[0] === 'Screening run')).toBe(false);
  });

  it('[MAM-93 AC-5] a 403 shows "You are not allowed to run jobs"', async () => {
    const { user } = await openJobs(batchRoutes({ [`POST ${ENDPOINTS.sweep}`]: () => response(403, problem(403, 'Forbidden')) }));

    await runJob(user, 'Revalidation sweep', '03/31/2026');

    expect(await screen.findByText('You are not allowed to run jobs')).toBeInTheDocument();
    expect(runRows().some((row) => row[0] === 'Revalidation sweep')).toBe(false);
  });

  it.each([
    ['500', () => response(500, problem(500, 'Internal Server Error'))],
    [
      'network failure',
      () => {
        throw new TypeError('Failed to fetch');
      },
    ],
  ])('[MAM-93 AC-5] a %s shows "The job could not be started. Try again." and adds no row', async (_label, answer) => {
    const { user } = await openJobs(batchRoutes({ [`POST ${ENDPOINTS.directory}`]: answer }));

    await runJob(user, 'Directory extract (DIROUT)', '03/31/2026');

    expect(await screen.findByText('The job could not be started. Try again.')).toBeInTheDocument();
    expect(runRows().some((row) => row[0] === 'Directory extract')).toBe(false);
  });
});

describe('[MAM-93 AC-6] the legacy files can be downloaded (journeys J6 and J7)', () => {
  const FILES = [
    ['T-MSIS extract', ENDPOINTS.tmsis, (id: string) => `${ENDPOINTS.tmsis}/${id}/file`, 'TMSISOUT'],
    ['Directory extract (DIROUT)', ENDPOINTS.directory, (id: string) => `${ENDPOINTS.directory}/${id}/file`, 'DIROUT'],
    ['Revalidation sweep', ENDPOINTS.sweep, (id: string) => `${ENDPOINTS.sweep}/${id}/queue-file`, 'REVALQ'],
    ['Screening run', ENDPOINTS.screening, (id: string) => `${ENDPOINTS.screening}/${id}/results-file`, 'SCRNOUT'],
  ] as const;

  it.each(FILES)('[MAM-93 AC-6] %s: "Download" requests the legacy file with the administrator token and saves it byte for byte as %s', async (job, startPath, filePath, dd) => {
    const run = newRun({ status: 'COMPLETED' });
    const saved = captureSaves();
    const { user } = await openJobs(batchRoutes({ [`POST ${startPath}`]: () => response(202, run), [`GET ${filePath(run.runId)}`]: () => fileAnswer() }));
    await runJob(user, job, '03/31/2026');
    const label = job === 'Directory extract (DIROUT)' ? 'Directory extract' : job;
    await waitFor(() => expect(runRows().some((row) => row[0] === label)).toBe(true));

    await user.click(downloadIn(rowOf(label)));

    await waitFor(() => expect(saved).toHaveLength(1));
    const request = api.to('GET', filePath(run.runId))[0];
    expect(request?.headers.get('authorization')).toBe(`Bearer ${oidc.user?.access_token}`);
    expect(await bytesOf(saved[0]!.blob)).toEqual(LEGACY_BYTES);
    expect(saved[0]!.name.startsWith(dd)).toBe(true);
  });

  it('[MAM-93 AC-6] the enrolment intake run downloads its error file (APPERROR) byte for byte', async () => {
    const run = newRun({ status: 'COMPLETED' });
    const saved = captureSaves();
    const { user } = await openJobs(batchRoutes({ [`POST ${ENDPOINTS.intake}`]: () => response(202, run), [`GET ${ENDPOINTS.intake}/${run.runId}/error-file`]: () => fileAnswer() }));
    await runJob(user, 'Enrolment intake (APPINPUT)', '03/31/2026', { file: legacyFile() });
    await waitFor(() => expect(runRows().some((row) => row[0] === 'Enrolment intake')).toBe(true));

    await user.click(downloadIn(rowOf('Enrolment intake')));

    await waitFor(() => expect(saved).toHaveLength(1));
    expect(await bytesOf(saved[0]!.blob)).toEqual(LEGACY_BYTES);
    expect(saved[0]!.name.startsWith('APPERROR')).toBe(true);
  });

  it('[MAM-93 AC-6] the sanction intake run has no file to download', async () => {
    const { user } = await openJobs(batchRoutes({ [`POST ${ENDPOINTS.sanctionIntake}`]: () => response(202, newRun({ status: 'COMPLETED' })) }));

    await runJob(user, 'Sanction intake (SANCIN)', '03/31/2026', { file: legacyFile('SANCIN.txt') });

    await waitFor(() => expect(runRows().some((row) => row[0] === 'Sanction intake')).toBe(true));
    expect(within(rowOf('Sanction intake')).queryByRole('button', { name: /^Download/ })).toBeNull();
  });

  it('[MAM-93 AC-6] a 404 shows "The file is not available (yet)" and saves nothing', async () => {
    const run = newRun({ status: 'COMPLETED' });
    const saved = captureSaves();
    const { user } = await openJobs(batchRoutes({ [`POST ${ENDPOINTS.screening}`]: () => response(202, run), [`GET ${ENDPOINTS.screening}/${run.runId}/results-file`]: () => response(404, problem(404, 'Not Found')) }));
    await runJob(user, 'Screening run', '03/31/2026');
    await waitFor(() => expect(runRows().some((row) => row[0] === 'Screening run')).toBe(true));

    await user.click(downloadIn(rowOf('Screening run')));

    expect(await screen.findByText('The file is not available (yet)')).toBeInTheDocument();
    expect(saved).toHaveLength(0);
  });

  it('[MAM-93 AC-6] a run that is still Running or Failed offers no download', async () => {
    const { user } = await openJobs(
      batchRoutes({
        [`POST ${ENDPOINTS.screening}`]: () => response(202, newRun({ status: 'RUNNING', counts: undefined })),
        [`POST ${ENDPOINTS.sweep}`]: () => response(202, newRun({ status: 'FAILED', error: 'x' })),
      }),
    );

    await runJob(user, 'Screening run', '03/31/2026');
    await runJob(user, 'Revalidation sweep', '03/31/2026');

    await waitFor(() => expect(runRows().filter((row) => row[0] === 'Screening run' || row[0] === 'Revalidation sweep')).toHaveLength(2));
    expect(within(recentRuns()).queryAllByRole('button', { name: /^Download/ })).toHaveLength(0);
  });

  it('[MAM-93 AC-6] a file that cannot be fetched (5xx) shows "The file could not be downloaded. Try again."', async () => {
    const run = newRun({ status: 'COMPLETED' });
    const { user } = await openJobs(batchRoutes({ [`POST ${ENDPOINTS.screening}`]: () => response(202, run), [`GET ${ENDPOINTS.screening}/${run.runId}/results-file`]: () => response(500, problem(500, 'x')) }));
    await runJob(user, 'Screening run', '03/31/2026');
    await waitFor(() => expect(runRows().some((row) => row[0] === 'Screening run')).toBe(true));

    await user.click(downloadIn(rowOf('Screening run')));

    expect(await screen.findByText('The file could not be downloaded. Try again.')).toBeInTheDocument();
  });
});

describe('[MAM-93 AC-7] only administrators open the page', () => {
  it('[MAM-93 AC-7] admin1 sees the page and its menu entry "Batch jobs"', async () => {
    await openJobs(batchRoutes());

    expect(within(screen.getAllByRole('navigation')[0]!).getByRole('link', { name: 'Batch jobs' })).toHaveAttribute('href', '/console/admin/jobs');
    expect(runButton()).toBeInTheDocument();
  });

  it.each(['analyst1', 'supervisor1', 'provider1'] as const)('[MAM-93 AC-7] %s has no menu entry, sees the access-denied page when typing the address, and no batch request is made', async (who) => {
    signedInAs(who);
    installApi(batchRoutes());
    renderApp('/console/admin/jobs');

    expect(await screen.findByRole('heading', { level: 1, name: 'Access denied' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Batch jobs' })).toBeNull();
    expect(api.calls.filter((call) => call.url.pathname.startsWith('/api/v1/batch'))).toHaveLength(0);
  });

  it('[MAM-93 AC-7] every batch request is made with the administrator\'s token', async () => {
    const { user } = await openJobs(batchRoutes({ [`POST ${ENDPOINTS.screening}`]: () => response(202, newRun()) }));
    await runJob(user, 'Screening run', '03/31/2026');
    await waitFor(() => expect(posts(ENDPOINTS.screening)).toHaveLength(1));

    const batch = api.calls.filter((call) => call.url.pathname.startsWith('/api/v1/batch'));
    expect(batch.length).toBeGreaterThanOrEqual(2);
    for (const call of batch) expect(call.headers.get('authorization')).toBe(`Bearer ${oidc.user?.access_token}`);
  });
});

describe('[MAM-93 AC-8] structure', () => {
  it('[MAM-93 AC-8] every input has a visible label, the runs table has header cells, the status is text and each Download names its job and date', async () => {
    const { user } = await openJobs(batchRoutes({ [`POST ${ENDPOINTS.screening}`]: () => response(202, newRun({ status: 'COMPLETED' })) }));
    await user.selectOptions(jobList(), 'Enrolment intake (APPINPUT)');
    for (const input of [jobList(), dateBox(), fileBox(), batchIdBox()]) {
      expect(input.labels).toHaveLength(1);
      expect(input.labels?.[0]).not.toHaveClass('usa-sr-only');
    }

    await runJob(user, 'Screening run', '03/31/2026');

    await waitFor(() => expect(runRows().some((row) => row[0] === 'Screening run')).toBe(true));
    expect(within(rowOf('Screening run')).getByRole('button', { name: 'Download Screening run 31 Mar 2026' })).toBeInTheDocument();
    expect(runRows().find((row) => row[0] === 'Screening run')?.[2]).toBe('Completed');
    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1);
  });
});
