import { keepPreviousData, useQueries, useQuery } from '@tanstack/react-query';
import {
  LIST_APPLICATIONS,
  LIST_REVALIDATIONS,
  SEARCH_PROVIDERS,
  screeningsFor,
  withQuery,
  type ApplicationPage,
  type Provider,
  type ProviderPage,
  type RevalidationCase,
  type RevalidationCasePage,
  type ScreeningPage,
} from '../../../shared/api/types';
import { useApi } from '../../../shared/api/useApi';
import { useSession } from '../../../shared/auth/AuthProvider';

/** page 0 is the service default, so it is not sent; the size is the service default (20) too. */
const pageParam = (page: number) => (page > 0 ? page : undefined);

/** GET /api/v1/providers?status=P&screenFlag=P|F: Pending providers by screen flag (Pass: waiting for approval, Fail: failed). */
export function usePendingProviders(screenFlag: 'P' | 'F', page: number) {
  const api = useApi();
  const user = useSession();
  return useQuery({
    queryKey: ['queue', user.subject, 'providers', screenFlag, page],
    placeholderData: keepPreviousData,
    queryFn: ({ signal }) => api.get<ProviderPage>(withQuery(SEARCH_PROVIDERS, { status: 'P', screenFlag, page: pageParam(page) }), signal),
  });
}

/** GET /api/v1/screenings?npi=&result=FAIL for each listed provider: the failing sources of the latest failed screening. */
export function useFailedScreenings(providers: readonly Provider[]) {
  const api = useApi();
  const user = useSession();
  return useQueries({
    queries: providers.map((provider) => ({
      queryKey: ['queue', user.subject, 'failed-screening', provider.npi],
      queryFn: ({ signal }: { signal: AbortSignal }) => api.get<ScreeningPage>(screeningsFor(provider.npi, 'FAIL'), signal),
    })),
  });
}

export interface RevalidationQueue {
  status: 'pending' | 'error' | 'success';
  cases: RevalidationCase[];
  /** Entries of both open states together (the heading count). */
  total: number;
  /** The larger of the two totals: the number of pages the two lists can have (same page asked of both). */
  pagedTotal: number;
  refetch: () => void;
}

/**
 * Open revalidation cases (D-029): GET /api/v1/revalidations?status=DUE and ?status=IN_SCREENING, merged by due date, each case
 * once. Completed and cancelled cases are never asked for. When either call fails the queue is "failed", not "empty".
 */
export function useRevalidationQueue(page: number): RevalidationQueue {
  const api = useApi();
  const user = useSession();
  const results = useQueries({
    queries: (['DUE', 'IN_SCREENING'] as const).map((status) => ({
      queryKey: ['queue', user.subject, 'revalidations', status, page],
      placeholderData: keepPreviousData,
      queryFn: ({ signal }: { signal: AbortSignal }) => api.get<RevalidationCasePage>(withQuery(LIST_REVALIDATIONS, { status, page: pageParam(page) }), signal),
    })),
  });
  const failed = results.some((result) => result.status === 'error');
  const waiting = !failed && results.some((result) => result.status === 'pending');
  const pages = results.map((result) => result.data);
  const seen = new Set<string>();
  const cases = pages
    .flatMap((data) => data?.items ?? [])
    .filter((item) => !seen.has(item.caseId) && seen.add(item.caseId))
    .sort((a, b) => a.dueDate.localeCompare(b.dueDate));
  return {
    status: failed ? 'error' : waiting ? 'pending' : 'success',
    cases,
    total: pages.reduce((sum, data) => sum + (data?.total ?? 0), 0),
    pagedTotal: Math.max(0, ...pages.map((data) => data?.total ?? 0)),
    refetch: () => results.forEach((result) => void result.refetch()),
  };
}

/** GET /api/v1/applications?status=REJECTED: the rejected applications of every provider (staff read them all). */
export function useRejectedApplications(page: number) {
  const api = useApi();
  const user = useSession();
  return useQuery({
    queryKey: ['queue', user.subject, 'rejected', page],
    placeholderData: keepPreviousData,
    queryFn: ({ signal }) => api.get<ApplicationPage>(withQuery(LIST_APPLICATIONS, { status: 'REJECTED', page: pageParam(page) }), signal),
  });
}
