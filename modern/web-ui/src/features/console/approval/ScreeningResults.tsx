import { useQuery, type UseQueryResult } from '@tanstack/react-query';
import { Alert, AlertText } from '@trussworks/react-uswds';
import { screeningsPath, type ScreeningPage } from '../../../shared/api/types';
import { useApi } from '../../../shared/api/useApi';
import { useSession } from '../../../shared/auth/AuthProvider';
import { formatDate, SCREEN_RESULT_LABEL, screeningDetailText, sourceName, withCode } from '../../../shared/format/format';
import { ScrollableTable } from '../../../shared/ui/ScrollableTable';

/** GET /api/v1/screenings?npi= (screening-service, D-018): every screening of the provider with its per-source results. */
export function useScreenings(npi: string | undefined) {
  const api = useApi();
  const user = useSession();
  return useQuery({
    queryKey: ['screenings', user.subject, npi],
    enabled: npi !== undefined,
    queryFn: ({ signal }) => api.get<ScreeningPage>(screeningsPath(npi ?? ''), signal),
  });
}

/**
 * The per-source results of the latest screening of a provider: source, result as text plus code, verdict date, detail and the
 * error code of a failed source (D-016), in full.
 */
export function ScreeningResults({ query }: { query: UseQueryResult<ScreeningPage> }) {
  if (query.status === 'pending') return <p role="status">Loading screening results</p>;
  if (query.status === 'error') {
    return (
      <Alert type="error" slim role="alert">
        <AlertText>The screening results could not be loaded. Try again later.</AlertText>
      </Alert>
    );
  }
  const latest = [...query.data.items].sort((a, b) => (b.startedAt ?? '').localeCompare(a.startedAt ?? ''))[0];
  const results = latest?.results ?? [];
  if (results.length === 0) return <p>No screening results yet.</p>;
  return (
    <ScrollableTable bordered={false} fullWidth label="Screening results">
      <caption>Screening results</caption>
      <thead>
        <tr>
          <th scope="col">Source</th>
          <th scope="col">Result</th>
          <th scope="col">Verdict date</th>
          <th scope="col">Detail</th>
          <th scope="col">Error code</th>
        </tr>
      </thead>
      <tbody>
        {results.map((result) => (
          <tr key={result.source}>
            <td>{sourceName(result.source)}</td>
            <td>{withCode(result.result, SCREEN_RESULT_LABEL)}</td>
            <td>{formatDate(result.verdictAt)}</td>
            <td>{screeningDetailText(result.detail)}</td>
            <td>{result.errorCode ?? '-'}</td>
          </tr>
        ))}
      </tbody>
    </ScrollableTable>
  );
}
