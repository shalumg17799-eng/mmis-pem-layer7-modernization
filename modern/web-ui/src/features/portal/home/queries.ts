import { useQuery } from '@tanstack/react-query';
import { LIST_APPLICATIONS, type ApplicationPage } from '../../../shared/api/types';
import { useApi } from '../../../shared/api/useApi';
import { useProvider } from '../../../shared/api/useProvider';
import { useSession } from '../../../shared/auth/AuthProvider';

/** GET /api/v1/applications: a provider gets only their own applications from the service. */
export function useApplications() {
  const api = useApi();
  const user = useSession();
  return useQuery({
    queryKey: ['applications', user.subject],
    queryFn: ({ signal }) => api.get<ApplicationPage>(LIST_APPLICATIONS, signal),
  });
}

/** GET /api/v1/providers/{npi} for the NPI of the token claim; null when the provider does not exist yet (404). */
export function useOwnProvider(npi: string | undefined) {
  return useProvider(npi);
}
