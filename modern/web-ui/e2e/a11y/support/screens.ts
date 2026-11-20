// MAM-86: the 13 screens of modern/docs/ux/screen-inventory.md (5 portal, 7 console, 1 public), each with the role that opens it,
// the route, the h1 it shows and how to bring it to its main state.
//
// Data. The seven console screens and the public directory read the live stack at its seed state (16 providers, nothing written).
// The provider1 screens, and the approval page, are answered with synthetic data in the browser (e2e/support/gateway-routes.ts),
// because the seed holds no application, notice or pending-and-screened provider for provider1 (those exist only after journey
// J1) and a scan must not change the stack. Keycloak is always the real one.
import { expect, type Browser, type Locator, type Page } from '@playwright/test';
import { json, mockApi, type RouteHandler } from '../../support/gateway-routes';
import { openAs, tabOf, type Role } from './sessions';

export const NPI = '1003000111';
const APPLICATION_ID = '0b9c5d1e-6a4f-4c1a-9a43-3c2d6f0a1b11';
const REJECTED_ID = '7d1e2c3b-4a5f-4b6c-8d7e-9f0a1b2c3d4e';

const INDIVIDUAL = { lastName: 'RIVERA', firstName: 'MARIA', licenseNo: 'MD12345', licenseState: 'TX' };
export const VALIDATED = {
  applicationId: APPLICATION_ID, status: 'VALIDATED', npi: NPI, medicaidId: 'TX0000111', providerType: 'IN', taxonomy: '207Q00000X',
  individual: INDIVIDUAL, source: 'PORTAL', submittedAt: '2026-03-01T10:00:00Z',
};
export const REJECTED = {
  ...VALIDATED, applicationId: REJECTED_ID, status: 'REJECTED', providerType: 'XX', taxonomy: '', individual: undefined, submittedAt: '2026-02-20T09:00:00Z',
  errors: [
    { code: 'E102', field: 'providerType', message: 'The provider type must be IN, GR or FA.' },
    { code: 'E104', field: 'taxonomy', message: 'Taxonomy code is required' },
  ],
};
const ACTIVE_PROVIDER = {
  npi: NPI, medicaidId: 'TX0000111', providerType: 'IN', taxonomy: '207Q00000X', riskTier: 'L', enrollStatus: 'A', screenFlag: 'P',
  effectiveDate: '2026-03-02', revalidationDueDate: '2031-03-02', version: 1, individual: INDIVIDUAL,
};
const PENDING_PROVIDER = { ...ACTIVE_PROVIDER, riskTier: null, enrollStatus: 'P', revalidationDueDate: null };
const notice = (index: number, over: Record<string, unknown> = {}) => ({
  noticeId: `00000000-0000-4000-8000-${String(index).padStart(12, '0')}`, npi: NPI, template: 'APPROVED', channel: 'EMAIL', status: 'SENT',
  sentAt: '2026-03-05T10:00:00Z', ...over,
});
const result = (source: string, detail: string) => ({ source, result: 'P', verdictAt: '2026-03-02T09:14:00Z', detail, errorCode: null });
const PASSED_SCREENING = {
  items: [{
    screeningId: '5c1f0a9e-3b7d-4a52-9e64-0d8f2b6a7c10', npi: NPI, reason: 'INITIAL', status: 'COMPLETED', result: 'PASS',
    results: [result('LICBOARD', 'MD12345 active to 09/2028'), result('OIGLEIE', 'No exclusion'), result('SAMGOV', 'Not debarred'), result('SSADMF', 'No death record')],
    startedAt: '2026-03-02T09:13:00Z', completedAt: '2026-03-02T09:14:00Z',
  }],
  page: 0, size: 20, total: 1,
};
const page_of = (items: unknown[]) => json(200, { items, page: 0, size: 20, total: items.length });

export interface Screen {
  /** Screen id of the inventory, "<area>.<screen>": the name used in the test titles and in the report. */
  id: string;
  user: Role | 'public';
  /** The route of the inventory. */
  route: string;
  /** The concrete path opened. */
  path: string;
  /** The h1 the screen shows. */
  title: string;
  /** Data of the screen: 'live' = the running stack at the seed state; 'scripted' = answered in the browser (see the head of this file). */
  data: 'live' | 'scripted';
  /** The gateway answers of a scripted screen. */
  routes?: Record<string, RouteHandler>;
  /** What is visible once the main state is there. */
  ready: (page: Page) => Locator;
  /** Steps that bring the opened page to its main state (for example a search). */
  prepare?: (page: Page) => Promise<void>;
}

const main = (page: Page) => page.getByRole('main');

export const SCREENS: Screen[] = [
  {
    id: 'portal.home', user: 'provider1', route: '/portal', path: '/portal', title: 'My enrolment', data: 'scripted',
    routes: {
      'GET /api/v1/applications': () => page_of([VALIDATED, REJECTED]),
      [`GET /api/v1/providers/${NPI}`]: () => json(200, ACTIVE_PROVIDER),
    },
    ready: (page) => page.getByRole('row').filter({ hasText: NPI }).first(),
  },
  {
    id: 'portal.apply', user: 'provider1', route: '/portal/apply', path: '/portal/apply', title: 'Apply for enrolment', data: 'scripted',
    routes: {},
    ready: (page) => page.getByRole('button', { name: 'Submit application' }),
  },
  {
    id: 'portal.application-status', user: 'provider1', route: '/portal/applications/:applicationId', path: `/portal/applications/${APPLICATION_ID}`,
    title: 'Application status', data: 'scripted',
    routes: { [`GET /api/v1/applications/${APPLICATION_ID}`]: () => json(200, VALIDATED) },
    ready: (page) => main(page).getByText('Validated', { exact: true }),
  },
  {
    id: 'portal.provider-record', user: 'provider1', route: '/portal/provider', path: '/portal/provider', title: 'My provider record', data: 'scripted',
    routes: { [`GET /api/v1/providers/${NPI}`]: () => json(200, ACTIVE_PROVIDER) },
    ready: (page) => main(page).getByText('RIVERA').first(),
  },
  {
    id: 'portal.notices', user: 'provider1', route: '/portal/notices', path: '/portal/notices', title: 'My notices', data: 'scripted',
    routes: { 'GET /api/v1/notices': () => page_of([notice(1), notice(2, { template: 'DENIED', status: 'FAILED', sentAt: '2026-03-06T09:00:00Z' })]) },
    ready: (page) => page.getByRole('row').filter({ hasText: 'Enrolment approved (APPROVED)' }),
  },
  {
    id: 'console.provider-search', user: 'analyst1', route: '/console/providers', path: '/console/providers', title: 'Provider search', data: 'live',
    ready: (page) => page.getByRole('table', { name: 'Search results' }),
    // main state: the results of a name search that returns providers of every status of the seed (A, P, D, S, T)
    prepare: async (page) => {
      // The page pre-fills the NPI of the last search made in this tab (MAM-77 AC-6, sessionStorage). The main state is a search by name
      // only, so the NPI is emptied first: an NPI typed by an earlier test of the file (the "No provider found" test of AC-2 types
      // 1003000999) would be combined with the name and the search would find nothing.
      await page.getByLabel(/^NPI/).fill('');
      await page.getByLabel(/^Name/).fill('TESTPROV');
      await page.getByRole('button', { name: 'Search' }).click();
      await expect(page.getByRole('table', { name: 'Search results' })).toBeVisible({ timeout: 30_000 });
    },
  },
  {
    id: 'console.provider-detail', user: 'analyst1', route: '/console/providers/:npi', path: '/console/providers/1005940865', title: 'Provider detail', data: 'live',
    ready: (page) => page.getByRole('heading', { name: /^Screening/ }),
  },
  {
    id: 'console.work-queues', user: 'analyst1', route: '/console/queues', path: '/console/queues', title: 'Work queues', data: 'live',
    ready: (page) => page.getByRole('heading', { name: /^Pending approval/ }),
  },
  {
    id: 'console.approval', user: 'analyst1', route: '/console/providers/:npi/approval', path: `/console/providers/${NPI}/approval`, title: 'Approve or deny', data: 'scripted',
    routes: {
      [`GET /api/v1/providers/${NPI}`]: () => json(200, PENDING_PROVIDER),
      'GET /api/v1/screenings': () => json(200, PASSED_SCREENING),
    },
    ready: (page) => page.getByRole('radio', { name: 'Approve (becomes Active)' }),
  },
  {
    id: 'console.record-sanction', user: 'supervisor1', route: '/console/providers/:npi/sanctions/new', path: '/console/providers/1005940865/sanctions/new',
    title: 'Record a sanction', data: 'live',
    ready: (page) => page.getByRole('button', { name: 'Record sanction' }),
  },
  {
    id: 'console.risk-rules', user: 'admin1', route: '/console/admin/risk-rules', path: '/console/admin/risk-rules', title: 'Risk-tier rules', data: 'live',
    ready: (page) => page.getByRole('heading', { level: 2, name: /^Version/ }),
  },
  {
    id: 'console.batch-jobs', user: 'admin1', route: '/console/admin/jobs', path: '/console/admin/jobs', title: 'Batch jobs', data: 'live',
    ready: (page) => page.getByRole('button', { name: 'Run job' }),
  },
  {
    id: 'public.directory', user: 'public', route: '/directory', path: '/directory', title: 'Find a Medicaid provider', data: 'live',
    ready: (page) => page.getByRole('table', { name: 'Active providers' }),
    // main state: the list of every active provider (an empty name lists them, the page says so)
    prepare: async (page) => {
      await page.getByRole('button', { name: 'Search' }).click();
      await expect(page.getByRole('table', { name: 'Active providers' })).toBeVisible({ timeout: 30_000 });
    },
  },
];

/**
 * Open a screen in the tab of its role and bring it to its main state. Returns the tab. Checks that the h1 of the inventory is
 * shown (the right page, the right role). Every wait has its own limit, so a missing page fails the test instead of hanging it.
 */
export async function openScreen(browser: Browser, screen: Screen): Promise<Page> {
  const page = await tabOf(browser, screen.user);
  await page.unrouteAll({ behavior: 'ignoreErrors' });
  if (screen.routes) await mockApi(page, screen.routes);
  // The two screens that need a search first are "ready" only after it: open them on their form, then prepare.
  const formReady = screen.prepare ? () => page.getByRole('heading', { level: 1, name: screen.title }) : () => screen.ready(page);
  await openAs(page, screen.user, screen.path, formReady);
  await expect(page.getByRole('heading', { level: 1, name: screen.title }), `${screen.id} shows its h1`).toBeVisible();
  if (screen.prepare) await screen.prepare(page);
  await expect(screen.ready(page).first(), `${screen.id} is in its main state`).toBeVisible({ timeout: 30_000 });
  await expect(page.getByText(/^Loading/), `${screen.id} has finished loading`).toHaveCount(0, { timeout: 30_000 });
  return page;
}

export const SCREEN_BY_ID = new Map(SCREENS.map((screen) => [screen.id, screen]));

export function screenOf(id: string): Screen {
  const screen = SCREEN_BY_ID.get(id);
  if (!screen) throw new Error(`unknown screen ${id}`);
  return screen;
}

/** The states of AC-2, besides the main states. */
export const APPLY_ERRORS = [
  { code: 'E101', field: 'npi', message: 'NPI is required' },
  { code: 'E102', field: 'providerType', message: 'The provider type must be IN, GR or FA.' },
  { code: 'E104', field: 'taxonomy', message: 'Taxonomy code is required' },
];
export const REJECTED_STATUS_ROUTES: Record<string, RouteHandler> = { [`GET /api/v1/applications/${REJECTED_ID}`]: () => json(200, REJECTED) };
export const REJECTED_STATUS_PATH = `/portal/applications/${REJECTED_ID}`;

/** Work queues with an entry in each of the four lists (scripted answers; the seed state has all four empty). */
const FAILED_PROVIDER = {
  npi: '1003000444', medicaidId: 'TX0000444', providerType: 'GR', taxonomy: '193200000X', riskTier: null, enrollStatus: 'P', screenFlag: 'F',
  effectiveDate: '2026-03-02', revalidationDueDate: null, version: 1, organization: { legalName: 'EXCLUDED CLINIC LLC', dbaName: 'EXCLUDED CLINIC', ownerNpi: '1990000111' },
};
const FAILED_SCREENING = {
  items: [{
    screeningId: '8e2a4c10-7b3d-4f6a-9c15-1a2b3c4d5e6f', npi: '1003000444', reason: 'INITIAL', status: 'COMPLETED', result: 'FAIL',
    results: [{ source: 'OIGLEIE', result: 'F', verdictAt: '2026-03-02T09:14:00Z', detail: 'Owner 1990000111 is on the exclusion list', errorCode: 'E203' }],
    startedAt: '2026-03-02T09:13:00Z', completedAt: '2026-03-02T09:14:00Z',
  }],
  page: 0, size: 20, total: 1,
};
export const QUEUES_POPULATED: Record<string, RouteHandler> = {
  'GET /api/v1/providers': (call) => {
    const flag = call.query.get('screenFlag');
    return page_of(flag === 'P' ? [PENDING_PROVIDER, { ...PENDING_PROVIDER, npi: '1003000222', medicaidId: 'TX0000222' }] : flag === 'F' ? [FAILED_PROVIDER] : []);
  },
  'GET /api/v1/screenings': () => json(200, FAILED_SCREENING),
  'GET /api/v1/revalidations': (call) =>
    json(
      200,
      call.query.get('status') === 'DUE'
        ? { items: [{ caseId: '3f9d1c2e-5a6b-4c7d-8e9f-0a1b2c3d4e5f', npi: '1005951276', dueDate: '2026-01-15', status: 'DUE' }], page: 0, size: 20, total: 1 }
        : { items: [], page: 0, size: 20, total: 0 },
    ),
  'GET /api/v1/applications': () => page_of([REJECTED]),
};
