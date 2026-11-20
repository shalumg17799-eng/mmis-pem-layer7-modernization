// MAM-87 AC-1, AC-4, AC-6 and the structure of AC-7: the approval page /console/providers/:npi/approval shows the provider and
// its per-source screening results (GET /api/v1/providers/{npi}, GET /api/v1/screenings?npi=), offers the decisions the screen
// flag allows (D-019, QE-6) and only to ANALYST and SUPERVISOR (D-023).
import { screen, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { response, problem } from '../support/fakeApi';
import {
  approvalPath,
  approvalWorld,
  approveRadio,
  CLINIC_NPI,
  decisions,
  denyRadio,
  failedScreening,
  HOSPICE_NPI,
  NO_SCREENINGS,
  openApproval,
  passedScreening,
  pendingFail,
  pendingPass,
  pendingUnscreened,
  recordButton,
  reasonBox,
  RIVERA_NPI,
  screening,
  screeningPage,
  source,
} from '../support/approval';
import { api, bodyRows, cellTexts, fieldValue, renderApp, installApi, setupShellWorld, signedInAs, text } from '../support/world';

vi.mock('oidc-client-ts', async (importOriginal) => {
  const actual = await importOriginal<typeof import('oidc-client-ts')>();
  const { FakeUserManager } = await import('../support/fakeOidc');
  return { ...actual, UserManager: FakeUserManager };
});

setupShellWorld();

/** The table of screening results: the table that has a "Source" column header. */
function screeningTable(): HTMLElement {
  const table = screen.queryAllByRole('table').find((candidate) => within(candidate).queryByRole('columnheader', { name: 'Source' }));
  if (!table) throw new Error('the page shows no screening results table');
  return table;
}

/** Cell texts of the row of one source, found by the name the page shows for it (UI polish: names, not codes), keyed by column header. */
function resultRow(sourceName: string): Record<string, string> {
  const table = screeningTable();
  const headers = within(table).getAllByRole('columnheader').map((header) => text(header));
  const row = bodyRows(table).find((candidate) => text(candidate.children[0]) === sourceName);
  if (!row) throw new Error(`no screening row for ${sourceName}`);
  const cells = cellTexts(row);
  return Object.fromEntries(headers.map((header, index) => [header, cells[index] ?? '']));
}

describe('[MAM-87 AC-1] the approval page shows the provider and its screening results', () => {
  it('[MAM-87 AC-1] analyst1 opens /console/providers/1003000111/approval: one h1 "Approve or deny", NPI, Name, Status "Pending (P)", Screen flag "Pass (P)"', async () => {
    await openApproval('analyst1', approvalWorld(pendingPass(), passedScreening()), RIVERA_NPI);

    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1);
    expect(await screen.findByText('RIVERA MARIA')).toBeInTheDocument();
    expect(fieldValue('NPI')).toBe('1003000111');
    expect(fieldValue('Name')).toBe('RIVERA MARIA');
    expect(fieldValue('Status')).toBe('Pending (P)');
    expect(fieldValue('Screening result')).toBe('Pass (P)');
  });

  it('[MAM-87 AC-1] the page reads GET /api/v1/providers/1003000111 and GET /api/v1/screenings?npi=1003000111 and sends nothing', async () => {
    await openApproval('analyst1', approvalWorld(pendingPass(), passedScreening()), RIVERA_NPI);
    await screen.findByText('RIVERA MARIA');

    expect(api.to('GET', `/api/v1/providers/${RIVERA_NPI}`).length).toBeGreaterThan(0);
    const screenings = api.to('GET', '/api/v1/screenings');
    expect(screenings.length).toBeGreaterThan(0);
    expect(screenings[0]?.url.searchParams.get('npi')).toBe(RIVERA_NPI);
    expect(decisions(RIVERA_NPI)).toHaveLength(0);
  });

  it('[MAM-87 AC-1] the per-source results list every source with its result as text and code, its verdict date and its detail', async () => {
    await openApproval('analyst1', approvalWorld(pendingPass(), passedScreening()), RIVERA_NPI);
    await screen.findByText('RIVERA MARIA');

    const table = screeningTable();
    expect(bodyRows(table).map((row) => text(row.children[0]))).toEqual(['Licence board', 'OIG exclusion list (LEIE)', 'SAM.gov debarment', 'SSA death records']);
    const licence = resultRow('Licence board');
    expect(licence['Result']).toBe('Pass (P)');
    expect(licence['Detail']).toBe('MD12345 active to 09/2028');
    expect(Object.entries(licence).find(([header]) => /^Verdict/.test(header))?.[1]).toContain('2 Mar 2026');
    for (const name of ['OIG exclusion list (LEIE)', 'SAM.gov debarment', 'SSA death records']) expect(resultRow(name)['Result']).toBe('Pass (P)');
  });

  it('[MAM-87 AC-1] a failed source shows Fail (F), its detail and its error code E203 in full', async () => {
    await openApproval('analyst1', approvalWorld(pendingFail(), failedScreening()), CLINIC_NPI);
    await screen.findByText('EXCLUDED CLINIC LLC');

    const row = resultRow('OIG exclusion list (LEIE)');
    expect(row['Result']).toBe('Fail (F)');
    expect(row['Detail']).toBe('Owner 1990000111 is on the OIG exclusion list');
    expect(Object.entries(row).find(([header]) => /^Error/.test(header))?.[1]).toBe('E203');
  });

  it('[MAM-87 AC-1] a conditional result is written as Conditional (C)', async () => {
    const world = approvalWorld(pendingPass(), screeningPage(screening(RIVERA_NPI, 'PASS', [source('LICBOARD', 'C', 'Licence expires within 30 days')])));
    await openApproval('analyst1', world, RIVERA_NPI);
    await screen.findByText('RIVERA MARIA');

    expect(resultRow('Licence board')['Result']).toBe('Conditional (C)');
  });

  it('[MAM-87 AC-1] a group or facility shows its legal name as Name', async () => {
    await openApproval('analyst1', approvalWorld(pendingFail(), failedScreening()), CLINIC_NPI);

    expect(await screen.findByText('EXCLUDED CLINIC LLC')).toBeInTheDocument();
    expect(fieldValue('Name')).toBe('EXCLUDED CLINIC LLC');
    expect(fieldValue('Screening result')).toBe('Fail (F)');
  });

  it('[MAM-87 AC-1] when there are several screenings the results of the latest one are shown', async () => {
    const older = screening(RIVERA_NPI, 'FAIL', [source('LICBOARD', 'F', 'Older result (test)', 'E202')], { screeningId: '1d0c5b7e-8a21-4f63-9c15-7e4b2a9d6f30', startedAt: '2026-01-02T09:13:00Z' });
    const newer = screening(RIVERA_NPI, 'PASS', [source('LICBOARD', 'P', 'Newer result (test)')]);
    await openApproval('analyst1', approvalWorld(pendingPass(), screeningPage(older, newer)), RIVERA_NPI);
    await screen.findByText('RIVERA MARIA');

    expect(resultRow('Licence board')['Detail']).toBe('Newer result (test)');
    expect(screen.queryByText('Older result (test)')).toBeNull();
  });

  it('[MAM-87 AC-1] a provider without screenings says "No screening results yet"', async () => {
    await openApproval('analyst1', approvalWorld(pendingUnscreened(), NO_SCREENINGS), HOSPICE_NPI);

    expect(await screen.findByText(/No screening results yet/i)).toBeInTheDocument();
  });

  it('[MAM-87 AC-1] when the screening results cannot be loaded the page says so and still shows the provider', async () => {
    const world = approvalWorld(pendingPass(), passedScreening());
    world.routes['GET /api/v1/screenings'] = () => response(500, problem(500, 'Internal Server Error'));
    await openApproval('analyst1', world, RIVERA_NPI);

    expect(await screen.findByText(/screening results could not be loaded/i)).toBeInTheDocument();
    expect(fieldValue('NPI')).toBe('1003000111');
  });

  it('[MAM-87 AC-1] an unknown provider (404) shows "No provider found" and no decision form', async () => {
    const world = approvalWorld(pendingPass(), passedScreening());
    world.routes[`GET /api/v1/providers/${RIVERA_NPI}`] = () => response(404, problem(404, 'Not Found'));
    await openApproval('analyst1', world, RIVERA_NPI);

    expect(await screen.findByText('No provider found')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Record decision' })).toBeNull();
  });

  it('[MAM-87 AC-1] a provider that cannot be loaded (500) shows an alert, not an empty page and not a decision form', async () => {
    const world = approvalWorld(pendingPass(), passedScreening());
    world.routes[`GET /api/v1/providers/${RIVERA_NPI}`] = () => response(500, problem(500, 'Internal Server Error'));
    await openApproval('analyst1', world, RIVERA_NPI);

    expect(await screen.findByText(/provider could not be loaded/i)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Record decision' })).toBeNull();
  });

  it('[MAM-87 AC-1] an NPI that is not 10 digits shows "No provider found" without calling the API', async () => {
    signedInAs('analyst1');
    installApi(approvalWorld(pendingPass(), passedScreening()).routes);
    renderApp(approvalPath('abc'));

    expect(await screen.findByText('No provider found')).toBeInTheDocument();
    expect(api.calls.filter((call) => call.url.pathname.startsWith('/api/v1/providers/') || call.url.pathname === '/api/v1/screenings')).toHaveLength(0);
  });
});

describe('[MAM-87 AC-4] approval needs a passed screening', () => {
  it('[MAM-87 AC-4] a Pending provider with screen flag Pass offers Approve and Deny', async () => {
    await openApproval('analyst1', approvalWorld(pendingPass(), passedScreening()), RIVERA_NPI);
    await screen.findByText('RIVERA MARIA');

    expect(approveRadio()).toBeEnabled();
    expect(denyRadio()).toBeEnabled();
    expect(recordButton()).toBeEnabled();
  });

  it('[MAM-87 AC-4] screen flag Fail (F): the Approve option is disabled with the text "Approval needs a passed screening"', async () => {
    await openApproval('analyst1', approvalWorld(pendingFail(), failedScreening()), CLINIC_NPI);
    await screen.findByText('EXCLUDED CLINIC LLC');

    expect(approveRadio()).toBeDisabled();
    expect(screen.getByText('Approval needs a passed screening')).toBeVisible();
    expect(approveRadio()).toHaveAccessibleDescription(/Approval needs a passed screening/);
  });

  it('[MAM-87 AC-4] screen flag Fail (F): Deny can be chosen and the disabled Approve cannot', async () => {
    const { user } = await openApproval('analyst1', approvalWorld(pendingFail(), failedScreening()), CLINIC_NPI);
    await screen.findByText('EXCLUDED CLINIC LLC');

    await user.click(approveRadio());
    expect(approveRadio()).not.toBeChecked();
    await user.click(denyRadio());

    expect(denyRadio()).toBeChecked();
    expect(denyRadio()).toBeEnabled();
  });

  it('[MAM-87 AC-4] screen flag Unscreened (U): neither decision is offered and the page says that screening has not finished', async () => {
    await openApproval('analyst1', approvalWorld(pendingUnscreened(), NO_SCREENINGS), HOSPICE_NPI);
    await screen.findByText('SUNSET HOSPICE INC');

    expect(screen.queryByRole('radio')).toBeNull();
    expect(screen.queryByRole('button', { name: 'Record decision' })).toBeNull();
    expect(screen.getByText(/screening has not finished/i)).toBeInTheDocument();
    expect(fieldValue('Screening result')).toBe('Unscreened (U)');
  });

  it.each([
    ['Pass', pendingPass(), passedScreening(), RIVERA_NPI, 'RIVERA MARIA'],
    ['Fail', pendingFail(), failedScreening(), CLINIC_NPI, 'EXCLUDED CLINIC LLC'],
    ['Unscreened', pendingUnscreened(), NO_SCREENINGS, HOSPICE_NPI, 'SUNSET HOSPICE INC'],
  ])('[MAM-87 AC-4] opening the page of a %s provider sends nothing to the approval endpoint', async (_flag, record, screenings, npi, name) => {
    await openApproval('analyst1', approvalWorld(record, screenings), npi);
    await screen.findByText(name);

    expect(decisions(npi)).toHaveLength(0);
  });

  it('[MAM-87 AC-4] Record decision without a choice sends nothing and asks to choose Approve or Deny', async () => {
    const { user } = await openApproval('analyst1', approvalWorld(pendingPass(), passedScreening()), RIVERA_NPI);
    await screen.findByText('RIVERA MARIA');

    await user.click(recordButton());

    expect(await screen.findByText(/Choose Approve or Deny/i)).toBeInTheDocument();
    expect(decisions(RIVERA_NPI)).toHaveLength(0);
  });

  it.each([
    ['A', 'Active (A)'],
    ['T', 'Terminated (T)'],
    ['D', 'Denied (D)'],
    ['S', 'Suspended (S)'],
  ] as const)('[MAM-87 AC-4] a provider with status %s is not Pending: no decision is offered, the status is shown as %s', async (status, label) => {
    await openApproval('analyst1', approvalWorld(pendingPass({ enrollStatus: status }), passedScreening()), RIVERA_NPI);
    await screen.findByText('RIVERA MARIA');

    expect(fieldValue('Status')).toBe(label);
    expect(screen.queryByRole('radio')).toBeNull();
    expect(screen.queryByRole('button', { name: 'Record decision' })).toBeNull();
  });
});

describe('[MAM-87 AC-6] only analysts and supervisors decide', () => {
  it.each(['analyst1', 'supervisor1'] as const)('[MAM-87 AC-6] %s sees the decision form', async (who) => {
    await openApproval(who, approvalWorld(pendingPass(), passedScreening()), RIVERA_NPI);

    expect(await screen.findByRole('group', { name: 'Decision' })).toBeInTheDocument();
    expect(approveRadio()).toBeEnabled();
    expect(recordButton()).toBeEnabled();
  });

  it.each(['admin1', 'provider1'] as const)('[MAM-87 AC-6] %s sees the access-denied page and nothing of the provider is fetched', async (who) => {
    signedInAs(who);
    installApi(approvalWorld(pendingPass(), passedScreening()).routes);
    renderApp(approvalPath(RIVERA_NPI));

    expect(await screen.findByRole('heading', { level: 1, name: 'Access denied' })).toBeInTheDocument();
    expect(screen.queryByRole('group', { name: 'Decision' })).toBeNull();
    expect(screen.queryByText('RIVERA MARIA')).toBeNull();
    expect(api.calls.filter((call) => call.url.pathname.startsWith('/api/v1/providers/') || call.url.pathname === '/api/v1/screenings')).toHaveLength(0);
  });
});

describe('[MAM-87 AC-7] the decision form is labelled and status is never colour alone', () => {
  it('[MAM-87 AC-7] the decision is a labelled group of two radio buttons with visible labels', async () => {
    await openApproval('analyst1', approvalWorld(pendingPass(), passedScreening()), RIVERA_NPI);

    const group = await screen.findByRole('group', { name: 'Decision' });
    const radios = within(group).getAllByRole<HTMLInputElement>('radio');
    expect(radios.map((radio) => text(radio.labels?.[0]))).toEqual(['Approve (becomes Active)', 'Deny (becomes Denied)']);
    for (const radio of radios) expect(radio.labels?.[0]).not.toHaveClass('usa-sr-only');
  });

  it('[MAM-87 AC-7] the Reason has a visible label once Deny is chosen', async () => {
    const { user } = await openApproval('analyst1', approvalWorld(pendingPass(), passedScreening()), RIVERA_NPI);
    await user.click(await screen.findByRole('radio', { name: /^Deny/ }));

    const box = reasonBox();
    expect(box.labels).toHaveLength(1);
    expect(text(box.labels?.[0])).toMatch(/^Reason/);
    expect(box.labels?.[0]).not.toHaveClass('usa-sr-only');
  });

  it('[MAM-87 AC-7] the status and the screen flag are written as text plus code, in table cells of their own', async () => {
    await openApproval('analyst1', approvalWorld(pendingPass(), passedScreening()), RIVERA_NPI);
    await screen.findByText('RIVERA MARIA');

    const providerRegion = within(screen.getByRole('region', { name: 'Provider' }));
    expect(providerRegion.getByText('Pending (P)', { selector: 'dd' })).toBeInTheDocument();
    expect(providerRegion.getByText('Pass (P)', { selector: 'dd' })).toBeInTheDocument();
  });

  it('[MAM-87 AC-7] the page has one h1 inside main, tables with column headers and a caption', async () => {
    await openApproval('analyst1', approvalWorld(pendingPass(), passedScreening()), RIVERA_NPI);
    await screen.findByText('RIVERA MARIA');

    expect(within(screen.getByRole('main')).getAllByRole('heading', { level: 1 })).toHaveLength(1);
    for (const table of screen.getAllByRole('table')) {
      expect(within(table).getAllByRole('columnheader').length).toBeGreaterThan(0);
      expect(table.querySelector('caption')).not.toBeNull();
    }
  });
});
