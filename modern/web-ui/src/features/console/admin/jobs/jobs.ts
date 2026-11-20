import type { paths as DirectoryPaths } from '../../../../shared/api/generated/directory-service';
import type { components as EnrollmentComponents, paths as EnrollmentPaths } from '../../../../shared/api/generated/enrollment-service';
import type { paths as RevalidationPaths } from '../../../../shared/api/generated/revalidation-service';
import type { paths as SanctionPaths } from '../../../../shared/api/generated/sanctions-service';
import type { paths as ScreeningPaths } from '../../../../shared/api/generated/screening-service';
import type { paths as TmsisPaths } from '../../../../shared/api/generated/tmsis-service';

/** One run of a batch job as the services answer it (every service uses the same BatchRun shape). */
export type BatchRun = EnrollmentComponents['schemas']['BatchRun'];

export type JobKey = 'intake' | 'screening' | 'sweep' | 'sanctions' | 'directory' | 'tmsis';

export interface JobDef {
  key: JobKey;
  /** The text of the Job list. */
  option: string;
  /** The Job column of "Recent runs". */
  label: string;
  startPath: string;
  /** A legacy input file is uploaded (the two intakes, D-017). */
  fileJob: boolean;
  /** The file input is named after the legacy dataset: "File (APPINPUT)". */
  inputName?: string;
  /** Runs that are followed with GET {runId} until they end: the two intakes (MAM-93 AC-4) and, since the contracts 1.1.0, the screening run, the revalidation sweep and the directory extract. */
  statusPath?: (runId: string) => string;
  /** The legacy file the run produces (ADR-005) and where it is read. */
  output?: { dd: string; path: (runId: string) => string };
}

const id = (template: string, runId: string) => template.replace('{runId}', encodeURIComponent(runId));

// The paths come from the contracts: a typo or a removed endpoint fails the type check.
const INTAKE = '/api/v1/batch/intake-runs' satisfies keyof EnrollmentPaths;
const INTAKE_RUN = '/api/v1/batch/intake-runs/{runId}' satisfies keyof EnrollmentPaths;
const INTAKE_ERRORS = '/api/v1/batch/intake-runs/{runId}/error-file' satisfies keyof EnrollmentPaths;
const SANCTION_INTAKE = '/api/v1/batch/sanction-intake-runs' satisfies keyof SanctionPaths;
const SANCTION_INTAKE_RUN = '/api/v1/batch/sanction-intake-runs/{runId}' satisfies keyof SanctionPaths;
const SCREENING = '/api/v1/batch/screening-runs' satisfies keyof ScreeningPaths;
const SCREENING_RUN = '/api/v1/batch/screening-runs/{runId}' satisfies keyof ScreeningPaths;
const SCREENING_FILE = '/api/v1/batch/screening-runs/{runId}/results-file' satisfies keyof ScreeningPaths;
const SWEEP = '/api/v1/batch/revalidation-sweeps' satisfies keyof RevalidationPaths;
const SWEEP_RUN = '/api/v1/batch/revalidation-sweeps/{runId}' satisfies keyof RevalidationPaths;
const SWEEP_FILE = '/api/v1/batch/revalidation-sweeps/{runId}/queue-file' satisfies keyof RevalidationPaths;
const DIRECTORY = '/api/v1/batch/directory-extracts' satisfies keyof DirectoryPaths;
const DIRECTORY_RUN = '/api/v1/batch/directory-extracts/{runId}' satisfies keyof DirectoryPaths;
const DIRECTORY_FILE = '/api/v1/batch/directory-extracts/{runId}/file' satisfies keyof DirectoryPaths;
export const TMSIS = '/api/v1/batch/tmsis-extracts' satisfies keyof TmsisPaths;
const TMSIS_FILE = '/api/v1/batch/tmsis-extracts/{runId}/file' satisfies keyof TmsisPaths;

/** The six jobs of the old estate in the order of the Job list (D-020: each is run for an explicit business date). */
export const JOBS: readonly JobDef[] = [
  {
    key: 'intake',
    option: 'Enrolment intake (APPINPUT)',
    label: 'Enrolment intake',
    startPath: INTAKE,
    fileJob: true,
    inputName: 'APPINPUT',
    statusPath: (runId) => id(INTAKE_RUN, runId),
    output: { dd: 'APPERROR', path: (runId) => id(INTAKE_ERRORS, runId) },
  },
  { key: 'screening', option: 'Screening run', label: 'Screening run', startPath: SCREENING, fileJob: false, statusPath: (runId) => id(SCREENING_RUN, runId), output: { dd: 'SCRNOUT', path: (runId) => id(SCREENING_FILE, runId) } },
  { key: 'sweep', option: 'Revalidation sweep', label: 'Revalidation sweep', startPath: SWEEP, fileJob: false, statusPath: (runId) => id(SWEEP_RUN, runId), output: { dd: 'REVALQ', path: (runId) => id(SWEEP_FILE, runId) } },
  { key: 'sanctions', option: 'Sanction intake (SANCIN)', label: 'Sanction intake', startPath: SANCTION_INTAKE, fileJob: true, inputName: 'SANCIN', statusPath: (runId) => id(SANCTION_INTAKE_RUN, runId) },
  { key: 'directory', option: 'Directory extract (DIROUT)', label: 'Directory extract', startPath: DIRECTORY, fileJob: false, statusPath: (runId) => id(DIRECTORY_RUN, runId), output: { dd: 'DIROUT', path: (runId) => id(DIRECTORY_FILE, runId) } },
  { key: 'tmsis', option: 'T-MSIS extract', label: 'T-MSIS extract', startPath: TMSIS, fileJob: false, output: { dd: 'TMSISOUT', path: (runId) => id(TMSIS_FILE, runId) } },
];

/** One plain sentence per job, shown under the Job list: what the job does and the legacy program it replaces. */
export const JOB_HELP: Record<JobKey, string> = {
  intake: 'Loads a file of enrolment applications, checks each one and creates the provider records (legacy program PRVENR01).',
  screening: 'Checks the providers that are not screened yet against the exclusion lists, the licence board and the other sources (legacy program PRVSCR01).',
  sweep: 'Finds the active providers whose revalidation date has come and queues them for revalidation (legacy program PRVREV01).',
  sanctions: 'Applies a file of terminations, suspensions and reinstatements to the providers (legacy program PRVSAN01).',
  directory: 'Writes the list of active providers that the public directory shows (legacy program PRVDIR01).',
  tmsis: 'Writes the report of active providers that goes to CMS (legacy program PRVTMS01).',
};

export const JOB_BY_KEY = Object.fromEntries(JOBS.map((job) => [job.key, job])) as Record<JobKey, JobDef>;

export const RUN_STATUS_LABEL: Record<BatchRun['status'], string> = { RUNNING: 'Running', COMPLETED: 'Completed', FAILED: 'Failed' };

/** "read 12, written 10": the counts the service returns, as they are named. */
export function countsText(counts: BatchRun['counts']): string {
  return Object.entries(counts ?? {})
    .map(([name, value]) => `${name} ${value}`)
    .join(', ');
}

/** The legacy dataset name of a saved file: SCRNOUT.D20260331 (the business date as in the old DSN suffix). */
export function savedFileName(dd: string, businessDate: string): string {
  return `${dd}.D${businessDate.replaceAll('-', '')}`;
}

export interface FormError {
  field: 'job' | 'businessDate' | 'file';
  message: string;
}

export const JOB_REQUIRED = 'Choose a job';
export const DATE_REQUIRED = 'Enter the business date as MM/DD/YYYY';
export const DATE_INVALID = 'Enter a real date as MM/DD/YYYY';
export const FILE_REQUIRED = 'Choose the legacy file';
