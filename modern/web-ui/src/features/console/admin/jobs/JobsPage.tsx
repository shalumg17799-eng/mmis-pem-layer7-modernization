import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, AlertText, Button, Fieldset, Form, FormGroup, Label, Select, Table, Tag, TextInput } from '@trussworks/react-uswds';
import { useEffect, useRef, useState, type FormEvent, type MouseEvent } from 'react';
import { ApiError } from '../../../../shared/api/client';
import { useApi } from '../../../../shared/api/useApi';
import { useSession } from '../../../../shared/auth/AuthProvider';
import { toIsoDate } from '../../../../shared/format/dates';
import { formatDate } from '../../../../shared/format/format';
import { ErrorSummary } from '../../../../shared/ui/ErrorSummary';
import { PageHeading } from '../../../../shared/ui/PageHeading';
import { SortHeader, useSorting } from '../../../../shared/ui/sorting';
import {
  countsText,
  DATE_INVALID,
  DATE_REQUIRED,
  FILE_REQUIRED,
  JOB_BY_KEY,
  JOB_HELP,
  JOB_REQUIRED,
  JOBS,
  RUN_STATUS_LABEL,
  savedFileName,
  TMSIS,
  type BatchRun,
  type FormError,
  type JobKey,
} from './jobs';

/** Colour adds to the run badge; the word names the state. */
const RUN_COLOUR: Record<BatchRun['status'], string> = { RUNNING: '', COMPLETED: 'bg-success-dark', FAILED: 'bg-secondary-dark' };

/** How often a running job (intake, screening run, revalidation sweep, directory extract) is asked for its status. */
export const POLL_INTERVAL_MS = 1000;

interface Run {
  job: JobKey;
  run: BatchRun;
  /** The last refresh of a running job failed: the row says so, the status is not guessed. */
  refreshFailed?: boolean;
}

const RUN_SORT = {
  job: (entry: Run) => JOB_BY_KEY[entry.job].label,
  date: (entry: Run) => entry.run.businessDate,
  status: (entry: Run) => RUN_STATUS_LABEL[entry.run.status],
};

type Outcome = { kind: 'invalid'; errors: FormError[]; attempt: number } | { kind: 'refused'; heading: string; detail?: string; attempt: number };

function describeRefusal(error: unknown): { heading: string; detail?: string } {
  if (error instanceof ApiError) {
    if (error.status === 403) return { heading: 'You are not allowed to run jobs' };
    if (error.status === 400 || error.status === 422) return { heading: 'The job could not be started', detail: error.problem?.detail ?? 'The job was not accepted.' };
  }
  return { heading: 'The job could not be started. Try again.' };
}

/** Save the bytes of a Blob under a name, exactly as received (the legacy files are fixed-width, byte for byte). */
function saveFile(blob: Blob, name: string): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = name;
  document.body.append(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

/**
 * console.batch-jobs (MAM-93): an administrator runs the jobs of the old estate on demand for an explicit business date (D-020),
 * follows the file intakes until they end (D-017), reads failed runs with their error (D-024) and downloads the legacy-format files.
 * The runs listed are the ones started here plus the T-MSIS runs the service lists; the services have no other run list.
 */
export function JobsPage() {
  const api = useApi();
  const user = useSession();
  const queryClient = useQueryClient();
  const [job, setJob] = useState<JobKey | ''>('');
  const [businessDate, setBusinessDate] = useState('');
  const [file, setFile] = useState<File | null>(null);
  const [fileKey, setFileKey] = useState(0);
  const [batchId, setBatchId] = useState('');
  const [outcome, setOutcome] = useState<Outcome | null>(null);
  const [runs, setRuns] = useState<Run[]>([]);
  const [fileNotes, setFileNotes] = useState<Record<string, string>>({});
  const attempts = useRef(0);
  const def = job === '' ? undefined : JOB_BY_KEY[job];

  const tmsis = useQuery({
    queryKey: ['batch', user.subject, 'tmsis-runs'],
    queryFn: ({ signal }) => api.get<{ items: BatchRun[] }>(TMSIS, signal),
  });

  const start = useMutation({
    mutationFn: ({ key, isoDate }: { key: JobKey; isoDate: string }) => {
      const chosen = JOB_BY_KEY[key];
      if (!chosen.fileJob) return api.post<BatchRun>(chosen.startPath, { businessDate: isoDate });
      const form = new FormData();
      form.append('file', file as File);
      form.append('businessDate', isoDate);
      if (batchId.trim() !== '') form.append('batchId', batchId.trim());
      return api.postForm<BatchRun>(chosen.startPath, form);
    },
    onSuccess: (run, { key }) => {
      setRuns((current) => [{ job: key, run }, ...current]);
      setOutcome(null);
      setFile(null);
      setFileKey((value) => value + 1);
      if (key === 'tmsis') void queryClient.invalidateQueries({ queryKey: ['batch', user.subject, 'tmsis-runs'] });
    },
    onError: (error) => {
      attempts.current += 1;
      setOutcome({ kind: 'refused', ...describeRefusal(error), attempt: attempts.current });
    },
  });

  // Follow the running jobs that have a status call (AC-4; the three contracts 1.1.0 added screening, sweep and directory): ask each one until it is Completed or Failed.
  useEffect(() => {
    const running = runs.filter((entry) => JOB_BY_KEY[entry.job].statusPath && entry.run.status === 'RUNNING');
    if (running.length === 0) return;
    const timer = setInterval(() => {
      for (const entry of running) {
        const path = JOB_BY_KEY[entry.job].statusPath?.(entry.run.runId);
        if (!path) continue;
        api.get<BatchRun>(path).then(
          (fresh) => setRuns((current) => current.map((candidate) => (candidate.run.runId === fresh.runId ? { job: candidate.job, run: fresh } : candidate))),
          () => setRuns((current) => current.map((candidate) => (candidate.run.runId === entry.run.runId ? { ...candidate, refreshFailed: true } : candidate))),
        );
      }
    }, POLL_INTERVAL_MS);
    return () => clearInterval(timer);
  }, [runs, api]);

  function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (start.isPending) return;
    const errors: FormError[] = [];
    if (job === '') errors.push({ field: 'job', message: JOB_REQUIRED });
    const isoDate = businessDate.trim() === '' ? null : toIsoDate(businessDate);
    if (businessDate.trim() === '') errors.push({ field: 'businessDate', message: DATE_REQUIRED });
    else if (isoDate === null) errors.push({ field: 'businessDate', message: DATE_INVALID });
    if (def?.fileJob && file === null) errors.push({ field: 'file', message: FILE_REQUIRED });
    if (errors.length > 0 || job === '' || isoDate === null) {
      attempts.current += 1;
      setOutcome({ kind: 'invalid', errors, attempt: attempts.current });
      return;
    }
    setOutcome(null);
    start.mutate({ key: job, isoDate });
  }

  async function download(entry: Run) {
    const output = JOB_BY_KEY[entry.job].output;
    if (!output) return;
    setFileNotes((current) => ({ ...current, [entry.run.runId]: '' }));
    try {
      const blob = await api.download(output.path(entry.run.runId));
      saveFile(blob, savedFileName(output.dd, entry.run.businessDate));
    } catch (error) {
      const note = error instanceof ApiError && error.status === 404 ? 'The file is not available (yet)' : 'The file could not be downloaded. Try again.';
      setFileNotes((current) => ({ ...current, [entry.run.runId]: note }));
    }
  }

  function focusField(event: MouseEvent<HTMLAnchorElement>, field: string) {
    event.preventDefault();
    document.getElementById(field)?.focus();
  }

  // The rows: the runs started here, then the T-MSIS runs of the service that are not among them.
  const known = new Set(runs.map((entry) => entry.run.runId));
  const listed: Run[] = (tmsis.data?.items ?? []).filter((item) => !known.has(item.runId)).map((item) => ({ job: 'tmsis' as const, run: item }));
  const rows = [...runs, ...listed];
  const sorting = useSorting(rows, RUN_SORT);

  const errors = outcome?.kind === 'invalid' ? outcome.errors : [];
  const errorOf = (field: FormError['field']) => errors.filter((error) => error.field === field);
  const described = (field: FormError['field'], hint?: string) => {
    const ids = [...(hint ? [hint] : []), ...(errorOf(field).length ? [`${field}-error`] : [])];
    return ids.length ? ids.join(' ') : undefined;
  };
  const message = (field: FormError['field']) =>
    errorOf(field).map((error) => (
      <span key={field} id={`${field}-error`} className="usa-error-message">
        {error.message}
      </span>
    ));

  return (
    <>
      <PageHeading>Batch jobs</PageHeading>
      <p className="usa-intro">Run a job of the old system for a business date you choose, then download the legacy files it produced.</p>
      {outcome?.kind === 'refused' && (
        <ErrorSummary key={outcome.attempt} heading={outcome.heading}>
          {outcome.detail && <p>{outcome.detail}</p>}
        </ErrorSummary>
      )}
      {outcome?.kind === 'invalid' && (
        <ErrorSummary key={outcome.attempt} heading={`${errors.length} ${errors.length === 1 ? 'error' : 'errors'} in the job`}>
          <ul className="usa-list usa-list--unstyled">
            {errors.map((error) => (
              <li key={error.field}>
                <a className="usa-link" href={`#${error.field}`} onClick={(event) => focusField(event, error.field)}>
                  {error.message}
                </a>
              </li>
            ))}
          </ul>
        </ErrorSummary>
      )}
      <Form large noValidate onSubmit={onSubmit}>
        <Fieldset legend="What to run" legendStyle="default">
        <FormGroup error={errorOf('job').length > 0}>
          <Label htmlFor="job" error={errorOf('job').length > 0}>
            Job
          </Label>
          <div className="usa-hint" id="job-hint">
            {def ? JOB_HELP[def.key] : 'Choose a job to see what it does.'}
          </div>
          {message('job')}
          <Select
            id="job"
            name="job"
            value={job}
            validationStatus={errorOf('job').length ? 'error' : undefined}
            aria-describedby={described('job', 'job-hint')}
            aria-invalid={errorOf('job').length ? true : undefined}
            onChange={(event) => {
              setJob(event.target.value as JobKey | '');
              setFile(null);
              setFileKey((value) => value + 1);
            }}
          >
            <option value="">Choose...</option>
            {JOBS.map((option) => (
              <option key={option.key} value={option.key}>
                {option.option}
              </option>
            ))}
          </Select>
        </FormGroup>
        <FormGroup error={errorOf('businessDate').length > 0}>
          <Label htmlFor="businessDate" error={errorOf('businessDate').length > 0}>
            Business date (MM/DD/YYYY)
          </Label>
          <div className="usa-hint" id="businessDate-hint">
            The day the run is for, for example 12/31/2026. It also names the files you download.
          </div>
          {message('businessDate')}
          <TextInput
            id="businessDate"
            name="businessDate"
            type="text"
            value={businessDate}
            validationStatus={errorOf('businessDate').length ? 'error' : undefined}
            aria-describedby={described('businessDate', 'businessDate-hint')}
            aria-invalid={errorOf('businessDate').length ? true : undefined}
            onChange={(event) => setBusinessDate(event.target.value)}
          />
        </FormGroup>
        </Fieldset>
        {def?.fileJob && (
          <Fieldset legend="Input file" legendStyle="default">
            <FormGroup error={errorOf('file').length > 0}>
              <Label htmlFor="file" error={errorOf('file').length > 0}>
                {`File (${def.inputName})`}
              </Label>
              <div className="usa-hint" id="file-hint">
                The legacy file as it was written by the old system, fixed-width, not edited.
              </div>
              {message('file')}
              <input
                key={fileKey}
                id="file"
                name="file"
                type="file"
                className="usa-input"
                required
                aria-describedby={described('file', 'file-hint')}
                aria-invalid={errorOf('file').length ? true : undefined}
                onChange={(event) => setFile(event.target.files?.[0] ?? null)}
              />
            </FormGroup>
            <FormGroup>
              <Label htmlFor="batchId">Batch id (optional, at most 8 characters)</Label>
              <div className="usa-hint" id="batchId-hint">
                A short name for this load in the reports, for example B1231.
              </div>
              <TextInput id="batchId" name="batchId" type="text" maxLength={8} aria-describedby="batchId-hint" value={batchId} onChange={(event) => setBatchId(event.target.value)} />
            </FormGroup>
          </Fieldset>
        )}
        <Button type="submit" disabled={start.isPending}>
          Run job
        </Button>
      </Form>
      {tmsis.status === 'pending' && <p role="status">Loading recent runs</p>}
      {tmsis.status === 'error' && (
        <>
          <Alert type="error" slim role="alert">
            <AlertText>The T-MSIS runs could not be listed</AlertText>
          </Alert>
          <Button type="button" onClick={() => void tmsis.refetch()}>
            Retry
          </Button>
        </>
      )}
      <Table bordered={false} fullWidth scrollable>
        <caption>Recent runs</caption>
        <thead>
          <tr>
            <SortHeader column="job" label="Job" sorting={sorting} />
            <SortHeader column="date" label="Business date" sorting={sorting} />
            <SortHeader column="status" label="Status" sorting={sorting} />
            <th scope="col">Counts</th>
            <th scope="col">Error</th>
            <th scope="col">File</th>
          </tr>
        </thead>
        <tbody>
          {rows.length === 0 && (
            <tr>
              <td colSpan={6}>No runs yet</td>
            </tr>
          )}
          {sorting.rows.map((entry) => {
            const definition = JOB_BY_KEY[entry.job];
            const date = formatDate(entry.run.businessDate);
            return (
              <tr key={entry.run.runId}>
                <td>{definition.label}</td>
                <td>{date}</td>
                <td>
                  <Tag className={RUN_COLOUR[entry.run.status]}>{RUN_STATUS_LABEL[entry.run.status]}</Tag>
                </td>
                <td>{countsText(entry.run.counts)}</td>
                <td>{entry.run.error ?? (entry.refreshFailed ? 'The status could not be refreshed' : '')}</td>
                <td>
                  {definition.output && entry.run.status === 'COMPLETED' && (
                    <Button type="button" outline aria-label={`Download ${definition.label} ${date}`} onClick={() => void download(entry)}>
                      Download
                    </Button>
                  )}
                  {fileNotes[entry.run.runId] && <span role="alert"> {fileNotes[entry.run.runId]}</span>}
                </td>
              </tr>
            );
          })}
        </tbody>
      </Table>
      {rows.length === 0 && <p>Choose a job and a business date above, then start it. Each run appears in this list; a finished run that writes a file has a Download button.</p>}
    </>
  );
}
