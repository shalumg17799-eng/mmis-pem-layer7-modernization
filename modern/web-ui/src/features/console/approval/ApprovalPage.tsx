import { useMutation, useQueryClient, type UseQueryResult } from '@tanstack/react-query';
import { Alert, AlertText } from '@trussworks/react-uswds';
import { useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ApiError } from '../../../shared/api/client';
import { decisionPath, type ApprovalRequest, type Provider, type ScreeningPage } from '../../../shared/api/types';
import { useApi } from '../../../shared/api/useApi';
import { providerKey } from '../../../shared/api/useProvider';
import { useSession } from '../../../shared/auth/AuthProvider';
import { ENROLL_STATUS_LABEL, formatDate, providerName, withCode } from '../../../shared/format/format';
import { ErrorSummary } from '../../../shared/ui/ErrorSummary';
import { PageHeading } from '../../../shared/ui/PageHeading';
import { isNpi, ProviderLoader } from '../providers/ProviderLoader';
import { ProviderTable } from '../providers/ProviderTable';
import { ApprovalBrief } from './ApprovalBrief';
import { DecisionForm } from './DecisionForm';
import { describeRefusal, offerFor, type Refusal } from './decision';
import { ScreeningResults, useScreenings } from './ScreeningResults';

/** "Decision recorded. Status: Active (A). Revalidation due: 5 Mar 2031." with the values the service returned (D-003, D-004). */
function confirmation(decided: Provider): string {
  const due = decided.revalidationDueDate ? ` Revalidation due: ${formatDate(decided.revalidationDueDate)}.` : '';
  return `Decision recorded. Status: ${withCode(decided.enrollStatus, ENROLL_STATUS_LABEL)}.${due}`;
}

function Approval({ provider, screenings }: { provider: Provider; screenings: UseQueryResult<ScreeningPage> }) {
  const api = useApi();
  const user = useSession();
  const queryClient = useQueryClient();
  const [decided, setDecided] = useState<Provider | null>(null);
  const [refusal, setRefusal] = useState<(Refusal & { attempt: number }) | null>(null);
  const attempts = useRef(0);
  const key = providerKey(user.subject, provider.npi);

  const decide = useMutation({
    mutationFn: (request: ApprovalRequest) => api.post<Provider>(decisionPath(provider.npi), request),
    onSuccess: (result) => {
      queryClient.setQueryData(key, result); // the page shows what the service returned, not what it expected
      setDecided(result);
      setRefusal(null);
    },
    onError: (error) => {
      attempts.current += 1;
      setRefusal({ ...describeRefusal(error), attempt: attempts.current });
      // 409: someone else decided first. Reload the provider so that the page reads the current status (AC-5).
      if (error instanceof ApiError && error.status === 409) void queryClient.invalidateQueries({ queryKey: key });
    },
  });

  const offer = offerFor(provider);
  return (
    <>
      {decided && (
        <Alert type="success" slim role="status">
          <AlertText>{confirmation(decided)}</AlertText>
        </Alert>
      )}
      <ApprovalBrief provider={provider} screenings={screenings} />
      <ScreeningResults query={screenings} />
      {refusal && (
        <ErrorSummary key={refusal.attempt} heading={refusal.heading}>
          {refusal.detail && <p>{refusal.detail}</p>}
        </ErrorSummary>
      )}
      {offer.kind === 'form' && (
        <DecisionForm
          approveAllowed={offer.approveAllowed}
          subject={`${providerName(provider)} (NPI ${provider.npi})`}
          pending={decide.isPending}
          onDecide={(request) => {
            setRefusal(null);
            decide.mutate(request);
          }}
        />
      )}
      {offer.kind === 'unscreened' && <p>Screening has not finished, so no decision can be recorded yet.</p>}
      {offer.kind === 'decided' && <p>No further decision can be recorded: the status is {withCode(provider.enrollStatus, ENROLL_STATUS_LABEL)}.</p>}
      <ProviderTable provider={provider} />
      <p>
        <Link className="usa-link" to={`/console/providers/${encodeURIComponent(provider.npi)}`}>
          Provider detail
        </Link>
      </p>
    </>
  );
}

/**
 * console.approval (MAM-87): the analyst approval step that makes a provider Active after a passed screening, or Denied (D-019).
 * Only ANALYST and SUPERVISOR open it (the route is guarded, D-023). The page shows the provider and its per-source screening
 * results and sends the decision; the status and the revalidation due date (+5 years) are the service's (D-003).
 */
export function ApprovalPage() {
  const { npi } = useParams();
  // Both are fetched at once; the page is shown when the provider and the screening answer are both in, so that the analyst
  // reviews the provider together with its results (a failed screening call is shown as an alert, not as a missing page).
  const screenings = useScreenings(isNpi(npi) ? npi : undefined);
  return (
    <>
      <PageHeading>Approve or deny</PageHeading>
      <p className="usa-intro">Read the summary and the screening results, then approve the enrolment or deny it with a reason. Approval is offered only after a passed screening.</p>
      <ProviderLoader npi={npi}>
        {(provider) => (screenings.status === 'pending' ? <p role="status">Loading screening results</p> : <Approval provider={provider} screenings={screenings} />)}
      </ProviderLoader>
    </>
  );
}
