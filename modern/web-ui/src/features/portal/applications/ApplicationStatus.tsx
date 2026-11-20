import { useQuery } from '@tanstack/react-query';
import { Alert, AlertHeading, AlertText, Button, Table, Tag } from '@trussworks/react-uswds';
import { Link, useLocation, useParams } from 'react-router-dom';
import { ApiError } from '../../../shared/api/client';
import { applicationPath, type Application } from '../../../shared/api/types';
import { useApi } from '../../../shared/api/useApi';
import { useAuth, useSession } from '../../../shared/auth/AuthProvider';
import { APPLICATION_STATUS_LABEL, formatDate, PROVIDER_TYPE_LABEL, withCode } from '../../../shared/format/format';
import { PageHeading } from '../../../shared/ui/PageHeading';
import { SummaryList } from '../../../shared/ui/SummaryList';
import { Timeline } from './Timeline';

/** "Individual (IN)"; a type the page does not know (a rejected E102 application) is shown exactly as it was sent. */
function typeText(providerType: string | undefined): string {
  if (providerType === undefined || providerType === '') return '';
  return providerType in PROVIDER_TYPE_LABEL ? withCode(providerType, PROVIDER_TYPE_LABEL) : providerType;
}

function detailRows(application: Application): Array<[string, string]> {
  const rows: Array<[string, string]> = [
    ['NPI', application.npi],
    ['Medicaid ID', application.medicaidId ?? ''],
    ['Provider type', typeText(application.providerType)],
    ['Taxonomy', application.taxonomy ?? ''],
  ];
  if (application.individual) {
    rows.push(
      ['Last name', application.individual.lastName ?? ''],
      ['First name', application.individual.firstName ?? ''],
      ['Licence number', application.individual.licenseNo ?? ''],
      ['Licence state', application.individual.licenseState ?? ''],
    );
  }
  if (application.organization) {
    rows.push(
      ['Legal name', application.organization.legalName ?? ''],
      ['DBA name', application.organization.dbaName ?? ''],
      ['Owner NPI', application.organization.ownerNpi ?? ''],
    );
  }
  return rows;
}

/**
 * One sentence of orientation for a visit to the page. A validated application has none: the badge, the step indicator and the
 * "Application received" box already say where it is (UX decision 2026-11-19, one confirmation only). The box and a sentence are never both shown.
 */
const LEAD: Partial<Record<Application['status'], string>> = {
  SUBMITTED: 'Your application was received and is being checked.',
  REJECTED: 'Your application was not accepted. The reasons are listed below; correct them and apply again.',
};

/** Colour adds to the status badge, which is the one element that names the status (journeys J1 and J2 and the MAM-64 specs look it up as text). */
const BADGE_COLOUR: Record<Application['status'], string> = { SUBMITTED: '', VALIDATED: 'bg-success-dark', REJECTED: 'bg-secondary-dark' };

function ApplicationView({ application, received }: { application: Application; received: boolean }) {
  const errors = application.errors ?? [];
  const lead = received ? undefined : LEAD[application.status];
  return (
    <>
      <p className="margin-top-0 text-base-darker">{`Reference: ${application.applicationId}`}</p>
      {received && (
        <Alert type="success">
          <AlertHeading level="h2">Application received</AlertHeading>
          <AlertText>What happens next: automatic checks run first, then an analyst decides on your enrolment. You can follow the progress on this page and under My enrolment.</AlertText>
        </Alert>
      )}
      {lead && <p className="usa-intro">{lead}</p>}
      <Timeline status={application.status} />
      <SummaryList
        title="Application"
        rows={[
          ['Status', <Tag className={BADGE_COLOUR[application.status]}>{APPLICATION_STATUS_LABEL[application.status]}</Tag>],
          ['Submitted', formatDate(application.submittedAt)],
          ...detailRows(application),
        ]}
      />
      {errors.length === 0 ? (
        <p>No validation errors</p>
      ) : (
        <Table bordered={false} fullWidth scrollable>
          <caption>Validation errors</caption>
          <thead>
            <tr>
              <th scope="col">Code</th>
              <th scope="col">Field</th>
              <th scope="col">Message</th>
            </tr>
          </thead>
          <tbody>
            {errors.map((error, index) => (
              <tr key={`${error.code}-${error.field}-${index}`}>
                <td>{error.code}</td>
                <td>{error.field}</td>
                <td>{error.message ?? ''}</td>
              </tr>
            ))}
          </tbody>
        </Table>
      )}
      {application.status === 'REJECTED' && (
        <p>
          <Link className="usa-button" to="/portal/apply">
            Apply again
          </Link>
        </p>
      )}
    </>
  );
}

/**
 * portal.application-status (MAM-66): one application of the signed-in provider from GET /api/v1/applications/{applicationId}
 * (MAM-54): status, submission date, the data sent and every validation error with its code (D-001, D-016). Another provider's
 * application (403) is access denied with no data, an unknown one (404) is "Application not found", and a failed load is a
 * message that matches it and never a status (D-024).
 */
export function ApplicationStatus() {
  const { applicationId } = useParams();
  const received = (useLocation().state as { received?: boolean } | null)?.received === true;
  const api = useApi();
  const user = useSession();
  const { sessionEnded } = useAuth();
  const query = useQuery({
    queryKey: ['application', user.subject, applicationId],
    enabled: applicationId !== undefined,
    // a 401 is shown here (sign in again) so that nothing of the page is lost behind a redirect
    queryFn: ({ signal }) => api.get<Application>(applicationPath(applicationId ?? ''), signal, { endSessionOn401: false }),
  });
  const failure = query.error instanceof ApiError ? query.error : undefined;
  return (
    <>
      <PageHeading>Application status</PageHeading>
      {query.status === 'pending' && <p role="status">Loading application</p>}
      {query.status === 'success' && <ApplicationView application={query.data} received={received} />}
      {query.status === 'error' && failure?.status === 403 && (
        <Alert type="error" slim role="alert">
          <AlertText>Access denied. You are not allowed to see this application.</AlertText>
        </Alert>
      )}
      {query.status === 'error' && failure?.status === 404 && (
        <>
          <p>Application not found</p>
          <p>
            <Link className="usa-link" to="/portal">
              My enrolment
            </Link>
          </p>
        </>
      )}
      {query.status === 'error' && failure?.status === 401 && (
        <>
          <Alert type="error" slim role="alert">
            <AlertText>Your session has ended. Sign in again to see this application.</AlertText>
          </Alert>
          <Button type="button" onClick={() => void sessionEnded()}>
            Sign in again
          </Button>
        </>
      )}
      {query.status === 'error' && failure?.status !== 403 && failure?.status !== 404 && failure?.status !== 401 && (
        <>
          <Alert type="error" slim role="alert">
            <AlertText>The application could not be loaded. Try again.</AlertText>
          </Alert>
          <Button type="button" onClick={() => void query.refetch()}>
            Retry
          </Button>
        </>
      )}
    </>
  );
}
