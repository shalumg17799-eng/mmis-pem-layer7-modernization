import { useQuery } from '@tanstack/react-query';
import { useSession } from '../auth/AuthProvider';
import { ApiError } from './client';
import { providerPath, type Provider } from './types';
import { useApi } from './useApi';

/** Cache key of one provider record for the signed-in user (a decision refreshes it, MAM-87 AC-5). */
export function providerKey(subject: string, npi: string | undefined) {
  return ['provider', subject, npi] as const;
}

/** GET /api/v1/providers/{npi}; null when the provider does not exist (404). Nothing is fetched while `npi` is undefined. */
export function useProvider(npi: string | undefined) {
  const api = useApi();
  const user = useSession();
  return useQuery({
    queryKey: providerKey(user.subject, npi),
    enabled: npi !== undefined,
    queryFn: async ({ signal }) => {
      try {
        return await api.get<Provider>(providerPath(npi ?? ''), signal);
      } catch (error) {
        if (error instanceof ApiError && error.status === 404) return null;
        throw error;
      }
    },
  });
}
