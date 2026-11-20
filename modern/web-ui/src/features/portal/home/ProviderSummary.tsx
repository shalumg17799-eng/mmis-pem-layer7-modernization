import { Alert, AlertText } from '@trussworks/react-uswds';
import { Link } from 'react-router-dom';
import { ENROLL_STATUS_LABEL, PROVIDER_TYPE_LABEL, SCREEN_FLAG_LABEL, withCode } from '../../../shared/format/format';
import { EmptyState } from '../../../shared/ui/EmptyState';
import { DefinitionList } from '../../../shared/ui/SummaryList';
import { useOwnProvider } from './queries';

/** The provider's own record in short (AC-5). The NPI is the claim of the token, never something typed in the address bar. */
export function ProviderSummary({ npi }: { npi: string | undefined }) {
  const query = useOwnProvider(npi);
  const provider = query.data;
  return (
    <section className="margin-top-4" aria-labelledby="provider-heading">
      <h2 id="provider-heading">My provider record</h2>
      {npi === undefined && <p>Your account carries no NPI, so no provider record can be shown.</p>}
      {npi !== undefined && query.status === 'pending' && <p role="status">Loading provider record</p>}
      {query.status === 'error' && (
        <Alert type="error" slim role="alert">
          <AlertText>The provider record could not be loaded. Try again later.</AlertText>
        </Alert>
      )}
      {query.status === 'success' && provider === null && (
        <EmptyState title="No provider record exists yet.">
          <p>Your own record shows here once your application has been accepted.</p>
        </EmptyState>
      )}
      {query.status === 'success' && provider && (
        <>
          <DefinitionList
            rows={[
              ['NPI', provider.npi],
              ['Medicaid ID', provider.medicaidId],
              ['Provider type', withCode(provider.providerType, PROVIDER_TYPE_LABEL)],
              ['Taxonomy', provider.taxonomy],
              ['Enrolment status', withCode(provider.enrollStatus, ENROLL_STATUS_LABEL)],
              ['Screening result', withCode(provider.screenFlag, SCREEN_FLAG_LABEL)],
            ]}
          />
          <p>
            <Link className="usa-link" to="/portal/provider">
              My provider record
            </Link>
          </p>
        </>
      )}
    </section>
  );
}
