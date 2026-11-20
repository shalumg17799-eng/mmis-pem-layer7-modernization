import type { ReactElement } from 'react';
import { ApprovalPage } from '../features/console/approval/ApprovalPage';
import { SanctionPage } from '../features/console/sanction/SanctionPage';
import { JobsPage } from '../features/console/admin/jobs/JobsPage';
import { RiskRulesPage } from '../features/console/admin/risk/RiskRulesPage';
import { QueuesPage } from '../features/console/queues/QueuesPage';
import { ProviderDetail } from '../features/console/providers/ProviderDetail';
import { ProviderSearch } from '../features/console/providers/ProviderSearch';
import { ApplyPage } from '../features/portal/apply/ApplyPage';
import { ApplicationStatus } from '../features/portal/applications/ApplicationStatus';
import { PortalHome } from '../features/portal/home/PortalHome';
import { NoticesPage } from '../features/portal/notices/NoticesPage';
import { ProviderRecord } from '../features/portal/provider/ProviderRecord';
import type { Role } from '../shared/auth/claims';

/** Roles of the two areas (frontend-stack section 1; D-023). */
export const PORTAL_ROLES: readonly Role[] = ['PROVIDER'];
export const CONSOLE_ROLES: readonly Role[] = ['ANALYST', 'SUPERVISOR', 'ADMIN'];

export interface Screen {
  path: string;
  /** The h1 of the page. */
  title: string;
  /** Roles allowed to open the screen: modern/docs/ux/screen-inventory.md, roles column. */
  roles: readonly Role[];
  /** Label of the menu entry; screens without one are reached from other screens. */
  menu?: string;
  element: ReactElement;
}

/**
 * Every routed screen of the inventory. The order of the entries with a `menu` label is the order of the menu.
 * Screens other than the portal home are built in their own stories and show a placeholder until then.
 */
export const SCREENS: readonly Screen[] = [
  { path: '/portal', title: 'My enrolment', roles: PORTAL_ROLES, menu: 'My enrolment', element: <PortalHome /> },
  { path: '/portal/apply', title: 'Apply for enrolment', roles: PORTAL_ROLES, menu: 'Apply for enrolment', element: <ApplyPage /> },
  { path: '/portal/applications/:applicationId', title: 'Application status', roles: PORTAL_ROLES, element: <ApplicationStatus /> },
  { path: '/portal/provider', title: 'My provider record', roles: PORTAL_ROLES, menu: 'My provider record', element: <ProviderRecord /> },
  { path: '/portal/notices', title: 'My notices', roles: PORTAL_ROLES, menu: 'My notices', element: <NoticesPage /> },
  { path: '/console/providers', title: 'Provider search', roles: CONSOLE_ROLES, menu: 'Provider search', element: <ProviderSearch /> },
  { path: '/console/providers/:npi', title: 'Provider detail', roles: CONSOLE_ROLES, element: <ProviderDetail /> },
  { path: '/console/queues', title: 'Work queues', roles: ['ANALYST', 'SUPERVISOR'], menu: 'Work queues', element: <QueuesPage /> },
  { path: '/console/providers/:npi/approval', title: 'Approve or deny', roles: ['ANALYST', 'SUPERVISOR'], element: <ApprovalPage /> },
  { path: '/console/providers/:npi/sanctions/new', title: 'Record a sanction', roles: ['SUPERVISOR'], element: <SanctionPage /> },
  { path: '/console/admin/risk-rules', title: 'Risk-tier rules', roles: ['ADMIN'], menu: 'Risk-tier rules', element: <RiskRulesPage /> },
  { path: '/console/admin/jobs', title: 'Batch jobs', roles: ['ADMIN'], menu: 'Batch jobs', element: <JobsPage /> },
];

export interface MenuEntry {
  label: string;
  path: string;
}

/** The menu of a user: only the entries whose screen the roles allow (AC-2). */
export function menuFor(roles: readonly Role[]): MenuEntry[] {
  return SCREENS.flatMap((screen) =>
    screen.menu !== undefined && screen.roles.some((role) => roles.includes(role)) ? [{ label: screen.menu, path: screen.path }] : [],
  );
}

/** The start page of a user: the portal home for a provider, the provider search for the console roles. */
export function startPath(roles: readonly Role[]): { path: string; label: string } {
  if (roles.some((role) => PORTAL_ROLES.includes(role))) return { path: '/portal', label: 'Go to My enrolment' };
  if (roles.some((role) => CONSOLE_ROLES.includes(role))) return { path: '/console/providers', label: 'Go to Provider search' };
  return { path: '/', label: 'Go to the start page' };
}
