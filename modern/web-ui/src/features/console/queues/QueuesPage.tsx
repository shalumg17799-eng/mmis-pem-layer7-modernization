import { Table } from '@trussworks/react-uswds';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import type { Provider, ScreeningPage } from '../../../shared/api/types';
import {
  APPLICATION_SOURCE_LABEL,
  failedSourceText,
  formatDate,
  providerName,
  REVALIDATION_STATUS_LABEL,
  SCREEN_FLAG_LABEL,
  withCode,
} from '../../../shared/format/format';
import { PageHeading } from '../../../shared/ui/PageHeading';
import { SortHeader, useSorting } from '../../../shared/ui/sorting';
import { useFailedScreenings, usePendingProviders, useRejectedApplications, useRevalidationQueue } from './queries';
import { QueueSection } from './QueueSection';
import { QueueTiles } from './QueueTiles';

const providerPath = (npi: string) => `/console/providers/${encodeURIComponent(npi)}`;
const NO_ROWS: never[] = [];
const REVALIDATION_SORT = {
  npi: (revalidation: { npi: string }) => revalidation.npi,
  due: (revalidation: { dueDate: string }) => revalidation.dueDate,
  case: (revalidation: { status: string }) => REVALIDATION_STATUS_LABEL[revalidation.status] ?? revalidation.status,
};
const REJECTED_SORT = {
  npi: (application: { npi: string }) => application.npi,
  submitted: (application: { submittedAt: string }) => application.submittedAt,
  errors: (application: { errors?: Array<{ code: string }> | undefined }) => (application.errors ?? []).map((error) => error.code).join(', '),
  source: (application: { source: string }) => APPLICATION_SOURCE_LABEL[application.source as keyof typeof APPLICATION_SOURCE_LABEL],
};
const PENDING_SORT = {
  npi: (provider: Provider) => provider.npi,
  name: (provider: Provider) => providerName(provider),
  screened: (provider: Provider) => provider.screenFlag,
};

function PendingApproval() {
  const [page, setPage] = useState(0);
  const query = usePendingProviders('P', page);
  const sorting = useSorting(query.data?.items ?? NO_ROWS, PENDING_SORT);
  return (
    <QueueSection
      title="Pending approval"
      anchor="queue-pending-approval"
      status={query.status}
      total={query.data?.total ?? 0}
      emptyText="Nothing pending approval"
      page={page}
      onPage={setPage}
      onRetry={() => void query.refetch()}
    >
      <Table bordered={false} fullWidth scrollable>
        <thead>
          <tr>
            <SortHeader column="npi" label="NPI" sorting={sorting} />
            <SortHeader column="name" label="Name" sorting={sorting} />
            <SortHeader column="screened" label="Screening result" sorting={sorting} />
            <th scope="col">Action</th>
          </tr>
        </thead>
        <tbody>
          {sorting.rows.map((provider) => (
            <tr key={provider.npi}>
              <td>{provider.npi}</td>
              <td>{providerName(provider)}</td>
              <td>{withCode(provider.screenFlag, SCREEN_FLAG_LABEL)}</td>
              <td>
                <Link className="usa-link" to={`${providerPath(provider.npi)}/approval`}>
                  Review
                </Link>
              </td>
            </tr>
          ))}
        </tbody>
      </Table>
    </QueueSection>
  );
}

/** "OIG exclusion list (LEIE): E203 owner excluded": the failed sources of the latest failed screening, with their error codes in full (D-016). */
function failingSources(page: ScreeningPage): string {
  const latest = [...page.items].sort((a, b) => (b.startedAt ?? '').localeCompare(a.startedAt ?? ''))[0];
  const failed = (latest?.results ?? []).filter((result) => result.result === 'F');
  if (failed.length === 0) return 'Not recorded';
  return failed.map((result) => failedSourceText(result.source, result.errorCode)).join('; ');
}

function FailedScreening() {
  const [page, setPage] = useState(0);
  const query = usePendingProviders('F', page);
  const items: Provider[] = query.data?.items ?? [];
  const screenings = useFailedScreenings(items);
  const pairs = items.map((provider, index) => ({ provider, screening: screenings[index] }));
  const sorting = useSorting(pairs, {
    npi: (pair) => pair.provider.npi,
    name: (pair) => providerName(pair.provider),
    source: (pair) => (pair.screening?.status === 'success' ? failingSources(pair.screening.data) : undefined),
  });
  return (
    <QueueSection
      title="Failed screening"
      anchor="queue-failed-screening"
      status={query.status}
      total={query.data?.total ?? 0}
      emptyText="No failed screenings"
      page={page}
      onPage={setPage}
      onRetry={() => void query.refetch()}
    >
      <Table bordered={false} fullWidth scrollable>
        <thead>
          <tr>
            <SortHeader column="npi" label="NPI" sorting={sorting} />
            <SortHeader column="name" label="Name" sorting={sorting} />
            <SortHeader column="source" label="Failing source" sorting={sorting} />
          </tr>
        </thead>
        <tbody>
          {sorting.rows.map(({ provider, screening }) => {
            return (
              <tr key={provider.npi}>
                <td>
                  <Link className="usa-link" to={providerPath(provider.npi)}>
                    {provider.npi}
                  </Link>
                </td>
                <td>{providerName(provider)}</td>
                <td>
                  {screening?.status === 'success' && failingSources(screening.data)}
                  {screening?.status === 'error' && 'The failing source could not be loaded'}
                  {(screening === undefined || screening.status === 'pending') && 'Loading'}
                </td>
              </tr>
            );
          })}
        </tbody>
      </Table>
    </QueueSection>
  );
}

/**
 * Revalidation due. revalidation-service answers GET /api/v1/revalidations (D-029); when the call fails, or the service is not
 * there yet, the section says "The list could not be loaded" with Retry and never "No revalidations due".
 */
function RevalidationDue() {
  const [page, setPage] = useState(0);
  const queue = useRevalidationQueue(page);
  const sorting = useSorting(queue.cases, REVALIDATION_SORT);
  return (
    <QueueSection
      title="Revalidation due"
      anchor="queue-revalidation-due"
      status={queue.status}
      total={queue.total}
      pagedTotal={queue.pagedTotal}
      emptyText="No revalidations due"
      page={page}
      onPage={setPage}
      onRetry={queue.refetch}
    >
      <Table bordered={false} fullWidth scrollable>
        <thead>
          <tr>
            <SortHeader column="npi" label="NPI" sorting={sorting} />
            <SortHeader column="due" label="Due" sorting={sorting} />
            <SortHeader column="case" label="Case" sorting={sorting} />
          </tr>
        </thead>
        <tbody>
          {sorting.rows.map((revalidation) => (
            <tr key={revalidation.caseId}>
              <td>{revalidation.npi}</td>
              <td>{formatDate(revalidation.dueDate)}</td>
              <td>{REVALIDATION_STATUS_LABEL[revalidation.status] ?? revalidation.status}</td>
            </tr>
          ))}
        </tbody>
      </Table>
    </QueueSection>
  );
}

function RejectedApplications() {
  const [page, setPage] = useState(0);
  const query = useRejectedApplications(page);
  const sorting = useSorting(query.data?.items ?? NO_ROWS, REJECTED_SORT);
  return (
    <QueueSection
      title="Rejected applications"
      anchor="queue-rejected-applications"
      status={query.status}
      total={query.data?.total ?? 0}
      emptyText="No rejected applications"
      page={page}
      onPage={setPage}
      onRetry={() => void query.refetch()}
    >
      <Table bordered={false} fullWidth scrollable>
        <thead>
          <tr>
            <SortHeader column="npi" label="NPI" sorting={sorting} />
            <SortHeader column="submitted" label="Submitted" sorting={sorting} />
            <SortHeader column="errors" label="Errors" sorting={sorting} />
            <SortHeader column="source" label="Source" sorting={sorting} />
          </tr>
        </thead>
        <tbody>
          {sorting.rows.map((application) => (
            <tr key={application.applicationId}>
              <td>{application.npi === '' ? '(no NPI)' : application.npi}</td>
              <td>{formatDate(application.submittedAt)}</td>
              <td>{(application.errors ?? []).map((error) => error.code).join(', ')}</td>
              <td>{APPLICATION_SOURCE_LABEL[application.source]}</td>
            </tr>
          ))}
        </tbody>
      </Table>
    </QueueSection>
  );
}

/** console.work-queues (MAM-85): the four lists of the analyst. Each loads and fails on its own (D-024). */
export function QueuesPage() {
  return (
    <>
      <PageHeading>Work queues</PageHeading>
      <p className="usa-intro">Providers waiting for approval, failed screenings, revalidations due and rejected applications. Choose a count to jump to its list.</p>
      <QueueTiles />
      <PendingApproval />
      <FailedScreening />
      <RevalidationDue />
      <RejectedApplications />
    </>
  );
}
