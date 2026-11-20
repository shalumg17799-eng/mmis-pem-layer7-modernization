import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, AlertText, Button, Form, FormGroup, Label, Select, TextInput } from '@trussworks/react-uswds';
import { useRef, useState, type FormEvent, type MouseEvent } from 'react';
import { Link, useParams } from 'react-router-dom';
import { SANCTIONS, withQuery, type Sanction, type SanctionPage as SanctionPageData, type SanctionRequest } from '../../../shared/api/types';
import { useApi } from '../../../shared/api/useApi';
import { useSession } from '../../../shared/auth/AuthProvider';
import { statusWithCatchAll } from '../../../shared/format/format';
import { ErrorSummary } from '../../../shared/ui/ErrorSummary';
import { PageHeading } from '../../../shared/ui/PageHeading';
import { SanctionTable } from '../providers/panels/SanctionTable';
import { describeRefusal, validate, type FormError, type Refusal, type SanctionField } from './sanctionForm';

type Outcome = ({ attempt: number } & Refusal) | { kind: 'invalid'; errors: FormError[]; attempt: number };

const ACTIONS = [
  { value: 'T', label: 'Terminate (T)' },
  { value: 'S', label: 'Suspend (S)' },
  { value: 'R', label: 'Reinstate (R)' },
];

function errorsAt(outcome: Outcome | null, field: SanctionField): FormError[] {
  return outcome && 'errors' in outcome ? outcome.errors.filter((error) => error.field === field) : [];
}

function Messages({ field, errors }: { field: SanctionField; errors: FormError[] }) {
  return errors.map((error, index) => (
    <span key={`${field}-${index}`} id={`${field}-error-${index}`} className="usa-error-message">
      {error.message}
    </span>
  ));
}

const describedBy = (field: SanctionField, errors: FormError[], hint?: string) => {
  const ids = [...(hint ? [hint] : []), ...errors.map((_, index) => `${field}-error-${index}`)];
  return ids.length ? ids.join(' ') : undefined;
};

/** The history of the provider (GET /api/v1/sanctions?npi=, D-018): its own failure and Retry, never "None recorded" for a failure. */
function History({ npi }: { npi: string }) {
  const api = useApi();
  const user = useSession();
  const query = useQuery({ queryKey: ['sanction-history', user.subject, npi], queryFn: ({ signal }) => api.get<SanctionPageData>(withQuery(SANCTIONS, { npi }), signal) });
  const items = query.data?.items ?? [];
  return (
    <section aria-labelledby="sanction-history-heading" className="margin-top-4">
      <h2 id="sanction-history-heading">Sanction history</h2>
      {query.status === 'pending' && <p role="status">Loading sanction history</p>}
      {query.status === 'error' && (
        <>
          <Alert type="error" slim role="alert">
            <AlertText>The sanction history could not be loaded</AlertText>
          </Alert>
          <Button type="button" onClick={() => void query.refetch()}>
            Retry
          </Button>
        </>
      )}
      {query.status === 'success' && (items.length === 0 ? (
          <>
            <p>None recorded</p>
            <p>An action appears here after a supervisor records it.</p>
          </>
        ) : (
          <SanctionTable items={items} />
        ))}
    </section>
  );
}

/**
 * console.record-sanction (MAM-90): a supervisor terminates (T), suspends (S) or reinstates (R) a provider with a reason and an
 * effective date. The page sends POST /api/v1/sanctions and shows the status the service returns ("Suspended (S)", never Denied,
 * D-006). It does not look at the current status (D-027) and does not claim anything the service did not do (D-024).
 */
export function SanctionPage() {
  const { npi = '' } = useParams();
  const api = useApi();
  const user = useSession();
  const queryClient = useQueryClient();
  const [action, setAction] = useState('');
  const [reason, setReason] = useState('');
  const [date, setDate] = useState('');
  const [outcome, setOutcome] = useState<Outcome | null>(null);
  const [recorded, setRecorded] = useState<Sanction | null>(null);
  const attempts = useRef(0);

  const record = useMutation({
    mutationFn: (request: SanctionRequest) => api.post<Sanction>(SANCTIONS, request),
    onSuccess: (sanction) => {
      setRecorded(sanction);
      setOutcome(null);
      setAction('');
      setReason('');
      setDate('');
      void queryClient.invalidateQueries({ queryKey: ['sanction-history', user.subject, npi] });
      void queryClient.invalidateQueries({ queryKey: ['panel', user.subject] });
      void queryClient.invalidateQueries({ queryKey: ['provider', user.subject, npi] });
    },
    onError: (error) => {
      attempts.current += 1;
      setRecorded(null);
      setOutcome({ ...describeRefusal(error), attempt: attempts.current });
    },
  });

  function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (record.isPending) return;
    const { errors, isoDate } = validate(action, date);
    if (errors.length > 0 || isoDate === null) {
      attempts.current += 1;
      setRecorded(null);
      setOutcome({ kind: 'invalid', errors, attempt: attempts.current });
      return;
    }
    setOutcome(null);
    const trimmed = reason.trim();
    record.mutate({ npi, action, effectiveDate: isoDate, ...(trimmed !== '' ? { reason: trimmed } : {}) });
  }

  function focusField(event: MouseEvent<HTMLAnchorElement>, field: SanctionField) {
    event.preventDefault();
    document.getElementById(field)?.focus();
  }

  const summaryErrors = outcome && 'errors' in outcome ? outcome.errors : [];
  const actionErrors = errorsAt(outcome, 'action');
  const reasonErrors = errorsAt(outcome, 'reason');
  const dateErrors = errorsAt(outcome, 'effectiveDate');
  return (
    <>
      <PageHeading>Record a sanction</PageHeading>
      <p className="usa-intro">Terminate, suspend or reinstate the provider with a reason and an effective date. The history keeps every action.</p>
      {recorded && (
        <Alert type="success" slim role="status">
          <AlertText>
            {recorded.resultingStatus ? `Sanction recorded. Status: ${statusWithCatchAll(recorded.resultingStatus)}.` : 'Sanction recorded.'}
          </AlertText>
        </Alert>
      )}
      {outcome?.kind === 'message' && <ErrorSummary key={outcome.attempt} heading={outcome.heading}>{null}</ErrorSummary>}
      {summaryErrors.length > 0 && outcome && (
        <ErrorSummary key={outcome.attempt} heading={`${summaryErrors.length} ${summaryErrors.length === 1 ? 'error' : 'errors'} in the sanction`}>
          <ul className="usa-list usa-list--unstyled">
            {summaryErrors.map((error, index) => (
              <li key={`${error.field}-${index}`}>
                {error.field ? (
                  <a className="usa-link" href={`#${error.field}`} onClick={(event) => focusField(event, error.field as SanctionField)}>
                    {error.message}
                  </a>
                ) : (
                  error.message
                )}
              </li>
            ))}
          </ul>
        </ErrorSummary>
      )}
      <History npi={npi} />
      <Form large noValidate onSubmit={onSubmit}>
        <FormGroup>
          <Label htmlFor="npi">NPI</Label>
          <div className="usa-hint" id="npi-hint">
            The provider this action applies to. It cannot be changed here.
          </div>
          <TextInput id="npi" name="npi" type="text" value={npi} readOnly aria-describedby="npi-hint" />
        </FormGroup>
        <FormGroup error={actionErrors.length > 0}>
          <Label htmlFor="action" error={actionErrors.length > 0}>
            Action
          </Label>
          <div className="usa-hint" id="action-hint">
            Terminate ends the enrolment, Suspend pauses it, Reinstate makes it active again.
          </div>
          <Messages field="action" errors={actionErrors} />
          <Select
            id="action"
            name="action"
            value={action}
            validationStatus={actionErrors.length ? 'error' : undefined}
            aria-describedby={describedBy('action', actionErrors, 'action-hint')}
            aria-invalid={actionErrors.length ? true : undefined}
            onChange={(event) => setAction(event.target.value)}
          >
            <option value="">Choose...</option>
            {ACTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </Select>
        </FormGroup>
        <FormGroup error={reasonErrors.length > 0}>
          <Label htmlFor="reason" error={reasonErrors.length > 0}>
            Reason
          </Label>
          <div className="usa-hint" id="reason-hint">
            Optional. At most 60 characters, for example: Licence revoked by the state board.
          </div>
          <Messages field="reason" errors={reasonErrors} />
          <TextInput
            id="reason"
            name="reason"
            type="text"
            value={reason}
            maxLength={60}
            aria-describedby={['reason-hint', describedBy('reason', reasonErrors)].filter(Boolean).join(' ')}
            aria-invalid={reasonErrors.length ? true : undefined}
            onChange={(event) => setReason(event.target.value)}
          />
        </FormGroup>
        <FormGroup error={dateErrors.length > 0}>
          <Label htmlFor="effectiveDate" error={dateErrors.length > 0}>
            Effective date (MM/DD/YYYY)
          </Label>
          <div className="usa-hint" id="effectiveDate-hint">
            The day the action takes effect, for example 12/31/2026.
          </div>
          <Messages field="effectiveDate" errors={dateErrors} />
          <TextInput
            id="effectiveDate"
            name="effectiveDate"
            type="text"
            value={date}
            validationStatus={dateErrors.length ? 'error' : undefined}
            aria-describedby={describedBy('effectiveDate', dateErrors, 'effectiveDate-hint')}
            aria-invalid={dateErrors.length ? true : undefined}
            onChange={(event) => setDate(event.target.value)}
          />
        </FormGroup>
        <Button type="submit" disabled={record.isPending}>
          Record sanction
        </Button>
      </Form>
      <p>
        <Link className="usa-link" to={`/console/providers/${encodeURIComponent(npi)}`}>
          Provider detail
        </Link>
      </p>
    </>
  );
}
