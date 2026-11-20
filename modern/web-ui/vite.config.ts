/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Port 5173 (java-microservices 1). Addresses of the gateway and Keycloak come from public/config.js at run time.
export default defineConfig({
  plugins: [react()],
  server: { port: 5173, strictPort: true },
  preview: { port: 5173, strictPort: true },
  test: {
    environment: 'jsdom',
    setupFiles: ['tests/setup.ts'],
    include: ['tests/**/*.test.{ts,tsx}'],
    css: false,
    // 30 s per test (default 5 s): the tests that type 200 characters (MAM-64 apply, MAM-87 approval decision) take longer
    // than 5 s on a loaded machine or a small CI runner, and a test cut off while typing leaks keystrokes into the next
    // one. No assertion depends on this value (web UI code review of 2026-11-20, SHOULD-FIX 4).
    testTimeout: 30_000,
    // Measurement only (npm run coverage): no thresholds. The JaCoCo 85 % gate belongs to the Java services.
    coverage: {
      provider: 'v8',
      reporter: ['text-summary', 'json-summary', 'html'],
      reportsDirectory: 'coverage',
      include: ['src/**/*.ts', 'src/**/*.tsx'],
      // Generated API types (npm run api:gen) are type declarations; main.tsx mounts into #root and cannot be loaded in a test.
      exclude: ['src/shared/api/generated/**', 'src/main.tsx'],
    },
  },
});
