// MAM-90 component-test helpers: the sanction form /console/providers/:npi/sanctions/new answered by a sanctions-service stub
// (POST /api/v1/sanctions, GET /api/v1/sanctions?npi=). Synthetic data only.
import { screen, within } from '@testing-library/react';
import type { UserEvent } from '@testing-library/user-event';
import { response, type ApiCall, type Handler } from './fakeApi';
import { sanction, page, type Sanction } from './panels';
import type { UserName } from './fixtures';
import { api, defaultRoutes, installApi, renderApp, signedInAs, text, type Rendered } from './world';

export const SANCTION_ROUTE = 'POST /api/v1/sanctions';
export const HISTORY_ROUTE = 'GET /api/v1/sanctions';
export const formPath = (npi: string): string => `/console/providers/${npi}/sanctions/new`;

const RESULTING: Record<string, 'T' | 'S' | 'A'> = { T: 'T', S: 'S', R: 'A' };

export interface SanctionWorld {
  routes: Record<string, Handler>;
  history: Sanction[];
}

/** A sanctions service that keeps a history: each POST is applied (T to T, S to S, R to A) and listed newest first. */
export function sanctionWorld(over: Record<string, Handler> = {}): SanctionWorld {
  const history: Sanction[] = [];
  let counter = 0;
  const routes: Record<string, Handler> = {
    ...defaultRoutes(),
    [HISTORY_ROUTE]: () => response(200, page(history)),
    [SANCTION_ROUTE]: (call) => {
      const body = JSON.parse(call.body ?? '{}') as { npi: string; action: string; reason?: string; effectiveDate: string };
      counter += 1;
      const recorded = sanction({
        sanctionId: `00000000-0000-4000-8000-${String(counter).padStart(12, '0')}`,
        npi: body.npi,
        action: body.action as Sanction['action'],
        reason: body.reason,
        effectiveDate: body.effectiveDate,
        resultingStatus: RESULTING[body.action],
      });
      history.unshift(recorded);
      return response(201, recorded);
    },
    ...over,
  };
  return { routes, history };
}

export async function openForm(npi: string, routes: Record<string, Handler>, who: UserName = 'supervisor1'): Promise<Rendered> {
  signedInAs(who);
  installApi(routes);
  const rendered = renderApp(formPath(npi));
  await screen.findByRole('heading', { level: 1, name: 'Record a sanction' });
  return rendered;
}

export const npiBox = (): HTMLInputElement => screen.getByLabelText('NPI', { exact: true });
export const actionList = (): HTMLSelectElement => screen.getByLabelText('Action', { exact: true });
export const reasonBox = (): HTMLInputElement => screen.getByLabelText(/^Reason/);
export const dateBox = (): HTMLInputElement => screen.getByLabelText(/^Effective date/);
export const recordButton = (): HTMLElement => screen.getByRole('button', { name: 'Record sanction' });

export interface Entry {
  action?: string;
  reason?: string;
  date?: string;
}

export async function fillForm(user: UserEvent, entry: Entry): Promise<void> {
  if (entry.action !== undefined) await user.selectOptions(actionList(), entry.action);
  if (entry.reason !== undefined) {
    await user.clear(reasonBox());
    if (entry.reason !== '') await user.type(reasonBox(), entry.reason);
  }
  if (entry.date !== undefined) {
    await user.clear(dateBox());
    if (entry.date !== '') await user.type(dateBox(), entry.date);
  }
}

export async function record(user: UserEvent, entry: Entry): Promise<void> {
  await fillForm(user, entry);
  await user.click(recordButton());
}

export function posts(): ApiCall[] {
  return api.to('POST', '/api/v1/sanctions');
}

export function sentBody(call: ApiCall | undefined): Record<string, unknown> {
  if (!call?.body) throw new Error('no POST /api/v1/sanctions with a body was sent');
  return JSON.parse(call.body) as Record<string, unknown>;
}

/** The table "Sanction history". */
export function historyRows(): string[][] {
  const table = screen.queryAllByRole('table').find((candidate) => candidate.querySelector('caption')?.textContent === 'Sanction history');
  return table ? Array.from(table.querySelectorAll('tbody tr')).map((row) => Array.from(row.children).map((cell) => text(cell))) : [];
}

export function describedText(element: HTMLElement): string {
  return (element.getAttribute('aria-describedby') ?? '')
    .split(/\s+/)
    .filter(Boolean)
    .map((id) => text(document.getElementById(id)))
    .join(' ');
}

export const main = () => within(screen.getByRole('main'));
