// MAM-92 component-test helpers: the risk-service table GET / PUT /api/v1/risk-rules as the admin page /console/admin/risk-rules
// calls it. The initial rule set is the legacy table of PRVRSK01 (version 1, default tier Limited). Synthetic data only.
import { screen, within } from '@testing-library/react';
import type { UserEvent } from '@testing-library/user-event';
import type { components as RiskSchemas } from '../../src/shared/api/generated/risk-service';
import { response, type ApiCall, type Handler } from './fakeApi';
import type { UserName } from './fixtures';
import { api, defaultRoutes, installApi, renderApp, signedInAs, text, type Rendered } from './world';

export type RiskRuleSet = RiskSchemas['schemas']['RiskRuleSet'];
export type RiskRule = RiskSchemas['schemas']['RiskRule'];

export const RULES_ROUTE = 'GET /api/v1/risk-rules';
export const SAVE_ROUTE = 'PUT /api/v1/risk-rules';

export const LEGACY_RULES: RiskRule[] = [
  { taxonomyPrefix: '332', tier: 'H' },
  { taxonomyPrefix: '251', tier: 'H' },
  { taxonomyPrefix: '341', tier: 'M' },
  { taxonomyPrefix: '291', tier: 'M' },
  { taxonomyPrefix: '225', tier: 'M' },
];

export function ruleSet(overrides: Partial<RiskRuleSet> = {}): RiskRuleSet {
  return { version: 1, defaultTier: 'L', rules: LEGACY_RULES, ...overrides };
}

export interface RiskWorld {
  routes: Record<string, Handler>;
  /** The table the service holds now: GET answers with it, a successful PUT replaces it with version + 1. */
  current: () => RiskRuleSet;
}

/** A service that keeps the table: PUT with the loaded version stores a new version, as the contract says. */
export function riskWorld(over: Record<string, Handler> = {}, initial: RiskRuleSet = ruleSet()): RiskWorld {
  let current = initial;
  return {
    current: () => current,
    routes: {
      ...defaultRoutes(),
      [RULES_ROUTE]: () => response(200, current),
      [SAVE_ROUTE]: (call) => {
        const body = JSON.parse(call.body ?? '{}') as RiskRuleSet;
        current = { version: current.version + 1, defaultTier: body.defaultTier, rules: body.rules };
        return response(200, current);
      },
      ...over,
    },
  };
}

export async function openRules(routes: Record<string, Handler>, who: UserName = 'admin1'): Promise<Rendered> {
  signedInAs(who);
  installApi(routes);
  const rendered = renderApp('/console/admin/risk-rules');
  await screen.findByRole('heading', { level: 1, name: 'Risk-tier rules' });
  return rendered;
}

/** The heading of the loaded table: "Version 1 (default tier: Limited (L))". */
export async function findVersionHeading(): Promise<HTMLElement> {
  return screen.findByRole('heading', { level: 2, name: /^Version \d+ \(default tier: / });
}

export const prefixBox = (row: number): HTMLInputElement => screen.getByLabelText(`Taxonomy prefix, row ${row}`, { exact: true });
export const tierList = (row: number): HTMLSelectElement => screen.getByLabelText(`Tier, row ${row}`, { exact: true });
export const defaultTierList = (): HTMLSelectElement => screen.getByLabelText('Default tier', { exact: true });
export const saveButton = (): HTMLElement => screen.getByRole('button', { name: 'Save as new version' });
export const addButton = (): HTMLElement => screen.getByRole('button', { name: 'Add row' });

/** The rows on screen as [prefix, selected tier text]. */
export function tableRows(): Array<[string, string]> {
  const table = screen.queryAllByRole('table').find((candidate) => within(candidate).queryByRole('columnheader', { name: 'Taxonomy prefix' }));
  if (!table) return [];
  return Array.from(table.querySelectorAll('tbody tr')).map((row) => {
    const prefix = (row.querySelector('input') as HTMLInputElement).value;
    const select = row.querySelector('select') as HTMLSelectElement;
    return [prefix, text(select.selectedOptions[0])] as [string, string];
  });
}

export function saves(): ApiCall[] {
  return api.to('PUT', '/api/v1/risk-rules');
}

export function savedBody(call: ApiCall | undefined): RiskRuleSet {
  if (!call?.body) throw new Error('no PUT /api/v1/risk-rules with a body was sent');
  return JSON.parse(call.body) as RiskRuleSet;
}

export async function setPrefix(user: UserEvent, row: number, value: string): Promise<void> {
  await user.clear(prefixBox(row));
  if (value !== '') await user.type(prefixBox(row), value);
}

export function describedText(element: HTMLElement): string {
  return (element.getAttribute('aria-describedby') ?? '')
    .split(/\s+/)
    .filter(Boolean)
    .map((id) => text(document.getElementById(id)))
    .join(' ');
}
