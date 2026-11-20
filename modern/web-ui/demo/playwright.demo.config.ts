import { defineConfig, devices } from '@playwright/test';

// Demo screenshots for modern/docs/DEMO.md (not a test of any story). Run from modern/web-ui after ./pem.sh seed:
//   NODE_OPTIONS=--dns-result-order=ipv4first npx playwright test --config demo/playwright.demo.config.ts
export default defineConfig({
  testDir: '.',
  testMatch: '*.demo.ts',
  timeout: 300_000,
  expect: { timeout: 30_000 },
  retries: 0,
  workers: 1,
  reporter: [['list']],
  use: {
    baseURL: process.env.PEM_WEB_URL ?? 'http://localhost:5173',
    trace: 'off', // a trace would record the typed Keycloak password
    viewport: { width: 1280, height: 900 },
    // "localhost" -> ::1 is served by WSL's wslrelay on this host and resets connections; see playwright.config.ts
    launchOptions: { args: ['--host-resolver-rules=MAP localhost 127.0.0.1'] },
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'], viewport: { width: 1280, height: 900 } } }],
});
