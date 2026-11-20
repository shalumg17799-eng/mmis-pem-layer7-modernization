import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Alert, AlertText, Button, Form, Label, Select, Table, TextInput } from '@trussworks/react-uswds';
import { useState, type FormEvent } from 'react';
import { ApiError } from '../../../shared/api/client';
import { SEARCH_DIRECTORY, withQuery, type DirectoryPage } from '../../../shared/api/types';
import { usePublicApi } from '../../../shared/api/useApi';
import { PROVIDER_TYPE_LABEL, withCode } from '../../../shared/format/format';
import { EmptyState } from '../../../shared/ui/EmptyState';
import { PageHeading } from '../../../shared/ui/PageHeading';
import { Pager } from '../../../shared/ui/Pager';
import { SortHeader, useSorting } from '../../../shared/ui/sorting';

const PAGE_SIZE = 20;
const NO_ENTRIES: DirectoryPage['items'] = [];
const DIRECTORY_SORT = {
  name: (entry: DirectoryPage['items'][number]) => entry.displayName,
  npi: (entry: DirectoryPage['items'][number]) => entry.npi,
  type: (entry: DirectoryPage['items'][number]) => entry.providerType,
  taxonomy: (entry: DirectoryPage['items'][number]) => entry.taxonomy,
};

interface Criteria {
  name: string;
  providerType: string;
  page: number;
  /** One per Search click, so that searching again asks again (also after a failure). */
  attempt: number;
}

/**
 * public.directory (MAM-73): anyone, without signing in, searches the active providers by name and type. The page shows what
 * the API returns for active providers and nothing else: no Medicaid ID, no risk tier, no address (ADR-006, Q-008), and never an
 * Authorization header. The name is the displayName composed by the service (D-028), written in full.
 */
export function PublicDirectory() {
  const api = usePublicApi();
  const [name, setName] = useState('');
  const [providerType, setProviderType] = useState('');
  const [criteria, setCriteria] = useState<Criteria | null>(null);
  const query = useQuery({
    queryKey: ['directory', criteria],
    enabled: criteria !== null,
    placeholderData: keepPreviousData,
    queryFn: ({ signal }) =>
      api.get<DirectoryPage>(
        withQuery(SEARCH_DIRECTORY, { name: criteria?.name.trim(), providerType: criteria?.providerType, page: criteria && criteria.page > 0 ? criteria.page : undefined }),
        signal,
      ),
  });

  function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setCriteria((current) => ({ name, providerType, page: 0, attempt: (current?.attempt ?? 0) + 1 }));
  }

  const refused = query.error instanceof ApiError && query.error.status === 400 ? query.error : null;
  const unavailable = query.status === 'error' && !refused;
  const data = query.status === 'success' ? query.data : undefined;
  const sorting = useSorting(data?.items ?? NO_ENTRIES, DIRECTORY_SORT);
  return (
    <>
      <PageHeading>Find a Medicaid provider</PageHeading>
      <p className="usa-intro">Search the Medicaid providers who are currently enrolled and active.</p>
      <Form large role="search" onSubmit={onSubmit}>
        <Label htmlFor="name">Name</Label>
        <span className="usa-hint" id="name-hint">
          Part of a name is enough. Leave it empty to list every active provider.
        </span>
        <TextInput id="name" name="name" type="text" aria-describedby="name-hint" value={name} onChange={(event) => setName(event.target.value)} />
        <Label htmlFor="providerType">Provider type</Label>
        <Select id="providerType" name="providerType" value={providerType} onChange={(event) => setProviderType(event.target.value)}>
          <option value="">Any</option>
          {(Object.keys(PROVIDER_TYPE_LABEL) as Array<keyof typeof PROVIDER_TYPE_LABEL>).map((code) => (
            <option key={code} value={code}>
              {withCode(code, PROVIDER_TYPE_LABEL)}
            </option>
          ))}
        </Select>
        <Button type="submit">Search</Button>
      </Form>
      {query.isFetching && <p role="status">Searching</p>}
      {refused && (
        <Alert type="error" slim role="alert">
          <AlertText>{refused.problem?.detail ?? 'The search was refused. Check the name and the type and try again.'}</AlertText>
        </Alert>
      )}
      {unavailable && (
        <Alert type="error" slim role="alert">
          <AlertText>The directory is not available right now. Try again.</AlertText>
        </Alert>
      )}
      {data && data.items.length === 0 && (
        <EmptyState title="No providers found">
          <p>Check the spelling, try a shorter part of the name, or set Provider type to Any.</p>
        </EmptyState>
      )}
      {data && data.items.length > 0 && criteria && (
        <>
          <p>{`${data.total} ${data.total === 1 ? 'provider' : 'providers'} found`}</p>
          <Table bordered={false} fullWidth scrollable>
            <caption>Active providers</caption>
            <thead>
              <tr>
                <SortHeader column="name" label="Name" sorting={sorting} />
                <SortHeader column="npi" label="NPI" sorting={sorting} />
                <SortHeader column="type" label="Type" sorting={sorting} />
                <SortHeader column="taxonomy" label="Specialty (taxonomy)" sorting={sorting} />
              </tr>
            </thead>
            <tbody>
              {sorting.rows.map((entry) => (
                <tr key={entry.npi}>
                  <td>{entry.displayName}</td>
                  <td>{entry.npi}</td>
                  <td>{withCode(entry.providerType, PROVIDER_TYPE_LABEL)}</td>
                  <td>{entry.taxonomy ?? ''}</td>
                </tr>
              ))}
            </tbody>
          </Table>
          <Pager page={criteria.page} size={PAGE_SIZE} total={data.total} label="Active providers" onPage={(page) => setCriteria({ ...criteria, page })} />
        </>
      )}
    </>
  );
}
