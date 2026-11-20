import { defineConfig, devices } from '@playwright/test';

// End-to-end tests against the running stack (make up && make seed): the web UI on 5173, the gateway on 8080.
export default defineConfig({
  testDir: 'e2e',
  // The journeys (e2e/journeys) run against the live, freshly seeded stack and change its data: they run only through
  // `./pem.sh j1` (or with PEM_JOURNEYS=1), never in the default `npm run e2e` with the mocked specs.
  testIgnore: process.env.PEM_JOURNEYS ? [] : ['journeys/**'],
  timeout: 90_000,
  expect: { timeout: 15_000 },
  retries: 0,
  workers: 1,
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]],
  use: {
    baseURL: process.env.PEM_WEB_URL ?? 'http://localhost:5173',
    trace: 'off', // a trace would record the typed Keycloak password (MODERNIZATION.md section 5)
    screenshot: 'only-on-failure',
    // On this Windows host "localhost" first resolves to ::1, where WSL's wslrelay (not Docker's proxy) listens on the
    // published ports and intermittently resets connections (net::ERR_CONNECTION_RESET). Resolve it to 127.0.0.1 instead;
    // URLs and origins stay "localhost", so the Keycloak redirects and CORS are unchanged.
    launchOptions: { args: ['--host-resolver-rules=MAP localhost 127.0.0.1'] },
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
