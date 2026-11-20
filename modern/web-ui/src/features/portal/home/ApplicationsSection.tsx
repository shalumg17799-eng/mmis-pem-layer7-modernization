import { Alert, AlertText, Table, Tag } from '@trussworks/react-uswds';
import { Link } from 'react-router-dom';
import { APPLICATION_STATUS_LABEL, formatDate } from '../../../shared/format/format';
import { EmptyState } from '../../../shared/ui/EmptyState';
import { SortHeader, useSorting } from '../../../shared/ui/sorting';
import type { Application } from '../../../shared/api/types';
import { useApplications } from './queries';

const NO_APPLICATIONS: Application[] = [];
const APPLICATION_SORT = {
  submitted: (application: Application) => application.submittedAt,
  npi: (application: Application) => application.npi,
  status: (application: Application) => APPLICATION_STATUS_LABEL[application.status],
};

/** The provider's applications: Submitted, NPI, Status (as text) and a View link (AC-4). */
export function ApplicationsSection() {
  const query = useApplications();
  const sorting = useSorting(query.data?.items ?? NO_APPLICATIONS, APPLICATION_SORT);
  return (
    <section className="margin-top-4" aria-labelledby="applications-heading">
      <h2 id="applications-heading">My applications</h2>
      {query.status === 'pending' && <p role="status">Loading applications</p>}
      {query.status === 'error' && (
        <Alert type="error" slim role="alert">
          <AlertText>The applications could not be loaded. Try again later.</AlertText>
        </Alert>
      )}
      {query.status === 'success' && query.data.items.length === 0 && (
        <EmptyState title="No applications yet">
          <p>When you apply, the application is listed here with its status.</p>
        </EmptyState>
      )}
      {query.status === 'success' && query.data.items.length > 0 && (
        <>
          <Table bordered={false} fullWidth scrollable>
            <thead>
              <tr>
                <SortHeader column="submitted" label="Submitted" sorting={sorting} />
                <SortHeader column="npi" label="NPI" sorting={sorting} />
                <SortHeader column="status" label="Status" sorting={sorting} />
                <th scope="col">Action</th>
              </tr>
            </thead>
            <tbody>
              {sorting.rows.map((application) => (
                <tr key={application.applicationId}>
                  <td>{formatDate(application.submittedAt)}</td>
                  <td>{application.npi}</td>
                  <td>
                    <Tag>{APPLICATION_STATUS_LABEL[application.status]}</Tag>
                  </td>
                  <td>
                    <Link className="usa-link" to={`/portal/applications/${encodeURIComponent(application.applicationId)}`}>
                      View
                      <span className="usa-sr-only"> application submitted on {formatDate(application.submittedAt)}</span>
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </Table>
          {query.data.total > query.data.items.length && (
            <p>
              Showing {query.data.items.length} of {query.data.total} applications.
            </p>
          )}
        </>
      )}
    </section>
  );
}
