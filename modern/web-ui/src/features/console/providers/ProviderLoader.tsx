import { Alert, AlertText } from '@trussworks/react-uswds';
import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { ApiError } from '../../../shared/api/client';
import type { Provider } from '../../../shared/api/types';
import { useProvider } from '../../../shared/api/useProvider';

const NPI_PATTERN = /^\d{10}$/;

export function isNpi(value: string | undefined): value is string {
  return value !== undefined && NPI_PATTERN.test(value);
}

/** "No provider found" (the message PINQ never sent) with the way back to the search. */
function NotFound() {
  return (
    <>
      <p>No provider found</p>
      <p>
        <Link className="usa-link" to="/console/providers">
          Back to provider search
        </Link>
      </p>
    </>
  );
}

/**
 * Loads the provider of a console page from GET /api/v1/providers/{npi} and shows what the user needs to know when there is
 * none to show (D-024): loading, a 404 as "No provider found" with a link back to the search, a 403 as "not allowed", any
 * other failure as an alert; none of them is a status. An NPI that is not 10 digits is "No provider found" without a call.
 */
export function ProviderLoader({ npi, children }: { npi: string | undefined; children: (provider: Provider) => ReactNode }) {
  const valid = isNpi(npi);
  const query = useProvider(valid ? npi : undefined);
  if (!valid) return <NotFound />;
  if (query.status === 'pending') return <p role="status">Loading provider</p>;
  if (query.status === 'error') {
    const denied = query.error instanceof ApiError && query.error.status === 403;
    return (
      <Alert type="error" slim role="alert">
        <AlertText>{denied ? 'You are not allowed to see this provider' : 'The provider could not be loaded. Try again.'}</AlertText>
      </Alert>
    );
  }
  if (query.data === null) return <NotFound />;
  return <>{children(query.data)}</>;
}
