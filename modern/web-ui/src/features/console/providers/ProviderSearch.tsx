import { useQuery } from '@tanstack/react-query';
import { Alert, AlertText, Button, Form, FormGroup, Label, Table, Tag, TextInput } from '@trussworks/react-uswds';
import { useRef, useState, type FormEvent, type MouseEvent } from 'react';
import { Link } from 'react-router-dom';
import { ApiError } from '../../../shared/api/client';
import { SEARCH_PROVIDERS, withQuery, type ProviderPage } from '../../../shared/api/types';
import { useApi } from '../../../shared/api/useApi';
import { useSession } from '../../../shared/auth/AuthProvider';
import { ENROLL_STATUS_LABEL, PROVIDER_TYPE_LABEL, providerName, RISK_TIER_LABEL, withCode } from '../../../shared/format/format';
import { EmptyState } from '../../../shared/ui/EmptyState';
import { ErrorSummary } from '../../../shared/ui/ErrorSummary';
import { PageHeading } from '../../../shared/ui/PageHeading';
import { SortHeader, useSorting } from '../../../shared/ui/sorting';
import { ToolsPanel } from './ToolsPanel';

/** The last NPI searched, for the next inquiry of this browser tab (sessionStorage is per tab: a new tab starts empty, MAM-77 AC-6). */
const NO_PROVIDERS: ProviderPage['items'] = [];
const RESULT_SORT = {
  npi: (provider: ProviderPage['items'][number]) => provider.npi,
  name: (provider: ProviderPage['items'][number]) => providerName(provider),
  type: (provider: ProviderPage['items'][number]) => provider.providerType,
  status: (provider: ProviderPage['items'][number]) => provider.enrollStatus,
  risk: (provider: ProviderPage['items'][number]) => provider.riskTier,
};
/** Colour adds to the status badge; the word and the code name the status. */
const STATUS_COLOUR: Record<string, string> = { A: 'bg-success-dark', T: 'bg-secondary-dark', D: 'bg-secondary-dark', P: '', S: '' };
const LAST_NPI_KEY = 'pem.console.lastNpi';
const EMPTY_SEARCH = 'Enter an NPI, a Medicaid ID or a name';
const BAD_NPI = 'NPI must be 10 digits';

function readLastNpi(): string {
  try {
    return window.sessionStorage.getItem(LAST_NPI_KEY) ?? '';
  } catch {
    return '';
  }
}

function keepLastNpi(npi: string): void {
  try {
    window.sessionStorage.setItem(LAST_NPI_KEY, npi);
  } catch {
    // no session storage: the NPI is simply not kept
  }
}

interface Criteria {
  npi: string;
  medicaidId: string;
  name: string;
  /** One per accepted Search, so that searching again asks again (also after a failure). */
  attempt: number;
}

/** What the page says for a failed search (D-024): never "No provider found", never a blank table. */
function failureText(error: unknown): string {
  if (error instanceof ApiError && error.status === 403) return 'You are not allowed to search providers';
  if (error instanceof ApiError && error.status === 400) return error.problem?.detail ?? 'The search was refused. Check the entries and try again.';
  return 'The search could not be completed. Try again.';
}

/**
 * console.provider-search (MAM-77, replaces the CICS PINQ transaction, D-022): look a provider up by the typed NPI, by Medicaid ID
 * or by name. Only the filled inputs are sent to GET /api/v1/providers; a blank or malformed entry is sent back for correction and
 * nothing is searched; an empty answer is "No provider found"; the last NPI is kept for the next inquiry of this tab.
 */
export function ProviderSearch() {
  const api = useApi();
  const user = useSession();
  const [npi, setNpi] = useState(readLastNpi);
  const [medicaidId, setMedicaidId] = useState('');
  const [name, setName] = useState('');
  const [criteria, setCriteria] = useState<Criteria | null>(null);
  const [problem, setProblem] = useState<{ message: string; attempt: number } | null>(null);
  const attempts = useRef(0);
  const query = useQuery({
    queryKey: ['provider-search', user.subject, criteria],
    enabled: criteria !== null,
    queryFn: ({ signal }) => api.get<ProviderPage>(withQuery(SEARCH_PROVIDERS, { npi: criteria?.npi, medicaidId: criteria?.medicaidId, name: criteria?.name }), signal),
  });

  function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    attempts.current += 1;
    const typed = { npi: npi.trim(), medicaidId: medicaidId.trim(), name: name.trim() };
    if (typed.npi === '' && typed.medicaidId === '' && typed.name === '') {
      setProblem({ message: EMPTY_SEARCH, attempt: attempts.current });
      return;
    }
    if (typed.npi !== '' && !/^\d{10}$/.test(typed.npi)) {
      setProblem({ message: BAD_NPI, attempt: attempts.current });
      return;
    }
    setProblem(null);
    if (typed.npi !== '') keepLastNpi(typed.npi);
    setCriteria({ ...typed, attempt: attempts.current });
  }

  function focusNpi(event: MouseEvent<HTMLAnchorElement>) {
    event.preventDefault();
    document.getElementById('npi')?.focus();
  }

  const data = query.status === 'success' && criteria ? query.data : undefined;
  const sorting = useSorting(data?.items ?? NO_PROVIDERS, RESULT_SORT);
  const searching = criteria !== null && query.isFetching;
  return (
    <>
      <PageHeading>Provider search</PageHeading>
      <p className="usa-intro">Find a provider by NPI, Medicaid ID or name.</p>
      <p className="usa-hint">Fill in at least one field, then choose Search.</p>
      <ToolsPanel />
      {problem && (
        <ErrorSummary key={problem.attempt} heading="1 error in the search">
          <a className="usa-link" href="#npi" onClick={focusNpi}>
            {problem.message}
          </a>
        </ErrorSummary>
      )}
      <Form large noValidate aria-label="Provider search" onSubmit={onSubmit}>
        <FormGroup error={problem !== null}>
          <Label htmlFor="npi" error={problem !== null}>
            NPI
          </Label>
          <span className="usa-hint" id="npi-hint">
            10 digits, for example 1234567890.
          </span>
          {problem && (
            <span id="npi-error" className="usa-error-message">
              {problem.message}
            </span>
          )}
          <TextInput
            id="npi"
            name="npi"
            type="text"
            inputMode="numeric"
            value={npi}
            validationStatus={problem ? 'error' : undefined}
            aria-describedby={problem ? 'npi-hint npi-error' : 'npi-hint'}
            aria-invalid={problem ? true : undefined}
            onChange={(event) => setNpi(event.target.value)}
          />
        </FormGroup>
        <FormGroup>
          <Label htmlFor="medicaidId">Medicaid ID</Label>
          <span className="usa-hint" id="medicaidId-hint">
            The provider's Medicaid ID, for example TX0000123.
          </span>
          <TextInput id="medicaidId" name="medicaidId" type="text" aria-describedby="medicaidId-hint" value={medicaidId} onChange={(event) => setMedicaidId(event.target.value)} />
        </FormGroup>
        <FormGroup>
          <Label htmlFor="name">Name contains</Label>
          <span className="usa-hint" id="name-hint">
            Part of a name is enough, for example SMITH.
          </span>
          <TextInput id="name" name="name" type="text" aria-describedby="name-hint" value={name} onChange={(event) => setName(event.target.value)} />
        </FormGroup>
        <Button type="submit">Search</Button>
      </Form>
      <div role="status" aria-live="polite">
        {searching && <p>Searching</p>}
        {data && data.items.length === 0 && (
          <EmptyState title="No provider found">
            <p>Check the spelling or the number and search again. Use fewer letters of the name to widen the search.</p>
          </EmptyState>
        )}
      </div>
      {query.status === 'error' && !searching && (
        <Alert type="error" slim role="alert">
          <AlertText>{failureText(query.error)}</AlertText>
        </Alert>
      )}
      {data && data.items.length > 0 && (
        <>
          <p>{`${data.total} ${data.total === 1 ? 'provider' : 'providers'} found`}</p>
          <Table bordered fullWidth>
            <caption>Search results</caption>
            <thead>
              <tr>
                <SortHeader column="npi" label="NPI" sorting={sorting} />
                <SortHeader column="name" label="Name" sorting={sorting} />
                <SortHeader column="type" label="Type" sorting={sorting} />
                <SortHeader column="status" label="Status" sorting={sorting} />
                <SortHeader column="risk" label="Risk" sorting={sorting} />
              </tr>
            </thead>
            <tbody>
              {sorting.rows.map((provider) => (
                <tr key={provider.npi}>
                  <td>
                    <Link className="usa-link" to={`/console/providers/${encodeURIComponent(provider.npi)}`}>
                      {provider.npi}
                    </Link>
                  </td>
                  <td>{providerName(provider)}</td>
                  <td>{withCode(provider.providerType, PROVIDER_TYPE_LABEL)}</td>
                  <td>
                    <Tag className={STATUS_COLOUR[provider.enrollStatus] ?? ''}>{withCode(provider.enrollStatus, ENROLL_STATUS_LABEL)}</Tag>
                  </td>
                  <td>{provider.riskTier ? withCode(provider.riskTier, RISK_TIER_LABEL) : 'Not assigned'}</td>
                </tr>
              ))}
            </tbody>
          </Table>
          {data.total > data.items.length && <p>{`Showing ${data.items.length} of ${data.total} providers. Narrow the search to see the others.`}</p>}
        </>
      )}
    </>
  );
}
