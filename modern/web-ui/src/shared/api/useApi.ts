import { useMemo } from 'react';
import { config } from '../../app/config';
import { useAuth } from '../auth/AuthProvider';
import { createApiClient, createPublicApiClient, type ApiClient } from './client';

/** The gateway client of the signed-in user; a 401 ends the session and sends the user to sign in again (AC-6). */
export function useApi(): ApiClient {
  const { accessToken, sessionEnded } = useAuth();
  return useMemo(
    () => createApiClient({ baseUrl: config().gatewayUrl, accessToken, onUnauthorized: sessionEnded }),
    [accessToken, sessionEnded],
  );
}

/** The client of the public pages: no token, no session handling (the directory needs no sign-in). */
export function usePublicApi() {
  return useMemo(() => createPublicApiClient(config().gatewayUrl), []);
}
