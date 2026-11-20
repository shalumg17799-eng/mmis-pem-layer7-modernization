import { Alert, AlertHeading } from '@trussworks/react-uswds';
import { Link, useParams } from 'react-router-dom';
import type { Provider } from '../../../shared/api/types';
import { useSession } from '../../../shared/auth/AuthProvider';
import { providerName, statusWithCatchAll } from '../../../shared/format/format';
import { PageHeading } from '../../../shared/ui/PageHeading';
import { ProviderRecordTable } from '../../../shared/ui/ProviderRecordTable';
import { RecordSummary } from '../../../shared/ui/RecordSummary';
import { canOpenApproval } from '../approval/decision';
import { ProviderLoader } from './ProviderLoader';
import { DetailPanels } from './panels/DetailPanels';
import { ProviderTable } from './ProviderTable';

/** Colour may add to the status, never replace it: the banner always says it in words and code (frontend-stack section 5). */
function bannerType(status: string): 'success' | 'info' | 'warning' | 'error' {
  if (status === 'A') return 'success';
  if (status === 'P') return 'info';
  if (status === 'S' || status === 'T') return 'warning';
  return 'error';
}

/** "Status: Active (A)": the status message the CICS screen never sent (R-PRVCICS1-ADD-01), as text at the top of the page. */
function StatusBanner({ provider }: { provider: Provider }) {
  return (
    <Alert type={bannerType(provider.enrollStatus)} slim>
      <AlertHeading level="h2">{`Status: ${statusWithCatchAll(provider.enrollStatus)}`}</AlertHeading>
    </Alert>
  );
}

/**
 * console.provider-detail (MAM-80): the status banner, the provider in short and the table "Provider record" with every field of
 * PRVRECRD and its meaning, the actions for ANALYST and SUPERVISOR ("Approve or deny" when it is possible), and below them the
 * panels of MAM-82: screening, sanctions, revalidation, notices and audit, each for the roles that may read it.
 */
export function ProviderDetail() {
  const { npi } = useParams();
  const user = useSession();
  const mayDecide = user.roles.some((role) => role === 'ANALYST' || role === 'SUPERVISOR');
  return (
    <>
      <PageHeading>Provider detail</PageHeading>
      <p className="usa-intro">One provider at a glance: where the enrolment stands, then the full record, the actions you may take and the history below.</p>
      <ProviderLoader npi={npi}>
        {(provider) => (
          <>
            <StatusBanner provider={provider} />
            <RecordSummary provider={provider} />
            <h2>{`${provider.npi} ${providerName(provider)}`.trim()}</h2>
            <ProviderTable provider={provider} />
            <ProviderRecordTable provider={provider} statusText={statusWithCatchAll} />
            {mayDecide && canOpenApproval(provider) && (
              <p>
                <Link className="usa-button" to={`/console/providers/${encodeURIComponent(provider.npi)}/approval`}>
                  Approve or deny
                </Link>
              </p>
            )}
            {user.roles.includes('SUPERVISOR') && (
              <p>
                <Link className="usa-button usa-button--outline" to={`/console/providers/${encodeURIComponent(provider.npi)}/sanctions/new`}>
                  Record sanction
                </Link>
              </p>
            )}
            <DetailPanels provider={provider} />
          </>
        )}
      </ProviderLoader>
    </>
  );
}
