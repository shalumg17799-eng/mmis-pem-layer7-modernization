/** Run-time addresses from public/config.js (no secrets); local defaults otherwise. */
export interface PemConfig {
  gatewayUrl: string;
  keycloakUrl: string;
  realm: string;
  clientId: string;
}

declare global {
  interface Window {
    PEM_CONFIG?: Partial<PemConfig>;
  }
}

export function config(): PemConfig {
  const c = (typeof window !== 'undefined' && window.PEM_CONFIG) || {};
  return {
    gatewayUrl: c.gatewayUrl ?? 'http://localhost:8080',
    keycloakUrl: c.keycloakUrl ?? 'http://localhost:8180',
    realm: c.realm ?? 'pem',
    clientId: c.clientId ?? 'pem-web',
  };
}
