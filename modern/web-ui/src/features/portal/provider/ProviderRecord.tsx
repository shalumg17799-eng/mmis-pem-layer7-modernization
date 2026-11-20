import { Alert, AlertText } from '@trussworks/react-uswds';
import { Link } from 'react-router-dom';
import { ApiError } from '../../../shared/api/client';
import { useProvider } from '../../../shared/api/useProvider';
import { useSession } from '../../../shared/auth/AuthProvider';
import { EmptyState } from '../../../shared/ui/EmptyState';
import { PageHeading } from '../../../shared/ui/PageHeading';
import { ProviderRecordTable } from '../../../shared/ui/ProviderRecordTable';
import { RecordSummary } from '../../../shared/ui/RecordSummary';

/**
 * portal.provider-record (MAM-68): the provider's own record in full. The NPI is the claim of the token, never typed or put in the
 * address (D-023): the page asks for that one record only. A 404 is explained (nothing is registered until an application is
 * accepted, D-001), a 403 is access denied with no data, any other failure says that the record could not be loaded (D-024).
 */
export function ProviderRecord() {
  const user = useSession();
  const query = useProvider(user.npi);
  const provider = query.data;
  const denied = query.error instanceof ApiError && query.error.status === 403;
  return (
    <>
      <PageHeading>My provider record</PageHeading>
      <p className="usa-intro">Your provider record: identifiers, status, screening, risk level and the dates of your enrolment.</p>
      {user.npi === undefined && <p>Your account carries no NPI, so no provider record can be shown.</p>}
      {user.npi !== undefined && query.status === 'pending' && <p role="status">Loading provider record</p>}
      {denied && (
        <Alert type="error" slim role="alert">
          <AlertText>Access denied. You are not allowed to see this provider record.</AlertText>
        </Alert>
      )}
      {query.status === 'error' && !denied && (
        <Alert type="error" slim role="alert">
          <AlertText>The provider record could not be loaded. Try again later.</AlertText>
        </Alert>
      )}
      {query.status === 'success' && provider === null && (
        <EmptyState title="No provider record exists yet.">
          <p>A record is created when an application is accepted.</p>
          <p className="display-flex flex-wrap flex-align-center">
            <Link className="usa-button margin-right-2" to="/portal/apply">
              Apply for enrolment
            </Link>
            <Link className="usa-button usa-button--outline" to="/portal">
              My enrolment
            </Link>
          </p>
        </EmptyState>
      )}
      {query.status === 'success' && provider && (
        <>
          <RecordSummary provider={provider} showStatus />
          <ProviderRecordTable provider={provider} />
        </>
      )}
    </>
  );
}
