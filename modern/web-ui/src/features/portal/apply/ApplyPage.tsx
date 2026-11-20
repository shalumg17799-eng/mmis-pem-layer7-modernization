import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Button, Fieldset, Form } from '@trussworks/react-uswds';
import { useRef, useState, type FormEvent, type MouseEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ApiError } from '../../../shared/api/client';
import { SUBMIT_APPLICATION, type Application, type ApplicationRequest, type FieldError } from '../../../shared/api/types';
import { useApi } from '../../../shared/api/useApi';
import { useAuth } from '../../../shared/auth/AuthProvider';
import { ErrorSummary } from '../../../shared/ui/ErrorSummary';
import { PageHeading } from '../../../shared/ui/PageHeading';
import {
  describeFailure,
  EMPTY_VALUES,
  isOrganisation,
  errorText,
  MEDICAID_ID_FIELD,
  NPI_FIELD,
  shownFields,
  TAXONOMY_FIELD,
  toRequest,
  variantFields,
  type FailureView,
  type FieldName,
  type FormValues,
} from './applicationForm';
import { ProviderTypeField, TextField } from './fields';
import { ReviewSummary } from './ReviewSummary';

/** What the last submit left on the page; `attempt` is the key that moves the focus to a new summary every time. */
type Outcome = { kind: 'rejected'; errors: FieldError[]; attempt: number } | ({ kind: 'failed'; attempt: number } & FailureView);

function focusField(event: MouseEvent<HTMLAnchorElement>, name: FieldName) {
  event.preventDefault();
  document.getElementById(name)?.focus();
}

/** The business rejection (422): one entry per failed check, each linked to its field when the form has it (D-001). */
function RejectionSummary({ errors, shown }: { errors: FieldError[]; shown: readonly FieldName[] }) {
  const heading = `${errors.length} ${errors.length === 1 ? 'error' : 'errors'} in the application`;
  return (
    <ErrorSummary heading={heading}>
      <ul className="usa-list usa-list--unstyled">
        {errors.map((error, index) => {
          const target = shown.find((name) => name === error.field);
          return (
            <li key={`${error.code}-${error.field}-${index}`}>
              {target ? (
                <a className="usa-link" href={`#${target}`} onClick={(event) => focusField(event, target)}>
                  {errorText(error)}
                </a>
              ) : (
                errorText(error)
              )}
            </li>
          );
        })}
      </ul>
      <p>
        The application was not registered.{' '}
        <Link className="usa-link" to="/portal">
          My applications
        </Link>{' '}
        lists it as rejected.
      </p>
    </ErrorSummary>
  );
}

/** Any other failure: the reason that matches it, never a claim that the application was accepted (D-024). */
function FailureSummary({ failure, onSignInAgain }: { failure: FailureView; onSignInAgain: () => void }) {
  return (
    <ErrorSummary heading="The application could not be submitted">
      <p>{failure.reason}</p>
      {failure.signInAgain && (
        <Button type="button" onClick={onSignInAgain}>
          Sign in again
        </Button>
      )}
    </ErrorSummary>
  );
}

/**
 * portal.apply (MAM-64): the enrolment application form. The browser does not check the fields (noValidate): an empty NPI, type
 * or taxonomy is sent, and the service stores the rejection and answers E101 / E102 / E104 (journey J2). The page shows exactly
 * the codes and messages it gets.
 */
export function ApplyPage() {
  const api = useApi();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { sessionEnded } = useAuth();
  const [values, setValues] = useState<FormValues>(EMPTY_VALUES);
  const [outcome, setOutcome] = useState<Outcome | null>(null);
  const attempts = useRef(0);

  const submit = useMutation({
    mutationFn: (request: ApplicationRequest) => api.post<Application>(SUBMIT_APPLICATION, request, { endSessionOn401: false }),
    onSuccess: (application) => {
      void queryClient.invalidateQueries({ queryKey: ['applications'] });
      navigate(`/portal/applications/${encodeURIComponent(application.applicationId)}`, { state: { received: true } });
    },
    onError: (error) => {
      attempts.current += 1;
      const attempt = attempts.current;
      if (error instanceof ApiError && error.status === 422 && error.problem?.errors?.length) {
        void queryClient.invalidateQueries({ queryKey: ['applications'] }); // the service stored the rejected application
        setOutcome({ kind: 'rejected', errors: error.problem.errors, attempt });
      } else {
        setOutcome({ kind: 'failed', attempt, ...describeFailure(error) });
      }
    },
  });

  function onChange(name: FieldName, value: string) {
    setValues((current) => ({ ...current, [name]: value }));
  }

  function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submit.isPending) return;
    setOutcome(null);
    submit.mutate(toRequest(values));
  }

  const shown = shownFields(values.providerType);
  const errorsOf = (name: FieldName): FieldError[] => (outcome?.kind === 'rejected' ? outcome.errors.filter((error) => error.field === name) : []);

  return (
    <>
      <PageHeading>Apply for enrolment</PageHeading>
      <p className="usa-intro">Tell us who you are and what you do. Every field has an example under it; you can review your answers before you submit.</p>
      {outcome?.kind === 'rejected' && <RejectionSummary key={outcome.attempt} errors={outcome.errors} shown={shown} />}
      {outcome?.kind === 'failed' && <FailureSummary key={outcome.attempt} failure={outcome} onSignInAgain={() => void sessionEnded()} />}
      <Form large noValidate onSubmit={onSubmit}>
        <Fieldset legend="Provider" legendStyle="large">
          <TextField spec={NPI_FIELD} value={values.npi} errors={errorsOf('npi')} onChange={onChange} />
          <TextField spec={MEDICAID_ID_FIELD} value={values.medicaidId} errors={errorsOf('medicaidId')} onChange={onChange} />
          <ProviderTypeField value={values.providerType} errors={errorsOf('providerType')} onChange={onChange} />
          <TextField spec={TAXONOMY_FIELD} value={values.taxonomy} errors={errorsOf('taxonomy')} onChange={onChange} />
        </Fieldset>
        <Fieldset legend={isOrganisation(values.providerType) ? 'Organisation details' : 'Individual details'} legendStyle="large">
          {variantFields(values.providerType).map((spec) => (
            <TextField key={spec.name} spec={spec} value={values[spec.name]} errors={errorsOf(spec.name)} onChange={onChange} />
          ))}
        </Fieldset>
        <ReviewSummary values={values} />
        <Button type="submit" disabled={submit.isPending}>
          Submit application
        </Button>
      </Form>
    </>
  );
}
