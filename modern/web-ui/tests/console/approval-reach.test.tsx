// MAM-87 AC-1 (how to reach the approval page): the provider detail page /console/providers/:npi offers 'Approve or deny' only
// to ANALYST and SUPERVISOR and only when the status is Pending (P) and the screen flag is Pass (P) or Fail (F); the NPI in a
// Provider search result is a link to that page (MAM-77 AC-2). The 'Pending approval' work queue is another story.
import { screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { problem, response } from '../support/fakeApi';
import { provider, type Provider, type UserName } from '../support/fixtures';
import {
  approvalPath,
  approvalWorld,
  detailPath,
  HOSPICE_NPI,
  passedScreening,
  pendingFail,
  pendingPass,
  pendingUnscreened,
  RIVERA_NPI,
} from '../support/approval';
import { api, bodyRows, fieldValue, installApi, renderApp, setupShellWorld, signedInAs, text } from '../support/world';

vi.mock('oidc-client-ts', async (importOriginal) => {
  const actual = await importOriginal<typeof import('oidc-client-ts')>();
  const { FakeUserManager } = await import('../support/fakeOidc');
  return { ...actual, UserManager: FakeUserManager };
});

setupShellWorld();

async function openDetail(who: UserName, record: Provider) {
  signedInAs(who);
  installApi(approvalWorld(record, passedScreening(record.npi)).routes);
  const rendered = renderApp(detailPath(record.npi));
  await screen.findByRole('heading', { level: 1, name: 'Provider detail' });
  await waitFor(() => expect(fieldValue('NPI')).toBe(record.npi));
  return rendered;
}

const actionLink = () => screen.queryByRole('link', { name: 'Approve or deny' });

describe('[MAM-87 AC-1] the provider detail page and its "Approve or deny" action', () => {
  it('[MAM-87 AC-1] /console/providers/1003000111 shows NPI, Name, Status "Pending (P)" and Screen flag "Pass (P)"', async () => {
    await openDetail('analyst1', pendingPass());

    expect(fieldValue('NPI')).toBe('1003000111');
    expect(fieldValue('Name')).toBe('RIVERA MARIA');
    expect(fieldValue('Status')).toBe('Pending (P)');
    expect(fieldValue('Screening result')).toBe('Pass (P)');
    expect(api.to('GET', `/api/v1/providers/${RIVERA_NPI}`).length).toBeGreaterThan(0);
  });

  it.each(['analyst1', 'supervisor1'] as const)('[MAM-87 AC-1] %s sees "Approve or deny" for a Pending provider whose screen flag is Pass (P)', async (who) => {
    await openDetail(who, pendingPass());

    expect(actionLink()).toHaveAttribute('href', approvalPath(RIVERA_NPI));
  });

  it('[MAM-87 AC-1] "Approve or deny" is offered for a Pending provider whose screen flag is Fail (F)', async () => {
    await openDetail('analyst1', pendingFail());

    expect(actionLink()).toHaveAttribute('href', approvalPath('1003000444'));
  });

  it('[MAM-87 AC-1] choosing "Approve or deny" opens the approval page of the provider', async () => {
    const { user, path } = await openDetail('analyst1', pendingPass());

    await user.click(actionLink()!);

    expect(await screen.findByRole('heading', { level: 1, name: 'Approve or deny' })).toBeInTheDocument();
    expect(path()).toBe(approvalPath(RIVERA_NPI));
    expect(await screen.findByRole('group', { name: 'Decision' })).toBeInTheDocument();
  });

  it('[MAM-87 AC-1] "Approve or deny" is not offered when the screen flag is Unscreened (U)', async () => {
    await openDetail('analyst1', pendingUnscreened());

    expect(fieldValue('Screening result')).toBe('Unscreened (U)');
    expect(actionLink()).toBeNull();
  });

  it.each([
    ['A', 'Active (A)'],
    ['T', 'Terminated (T)'],
    ['D', 'Denied (D)'],
    ['S', 'Suspended (S)'],
  ] as const)('[MAM-87 AC-1] "Approve or deny" is not offered when the status is %s (%s), not Pending', async (status, label) => {
    await openDetail('analyst1', pendingPass({ enrollStatus: status }));

    expect(fieldValue('Status')).toBe(label);
    expect(actionLink()).toBeNull();
  });

  it('[MAM-87 AC-1] admin1 sees the provider detail but not the "Approve or deny" action', async () => {
    await openDetail('admin1', pendingPass());

    expect(fieldValue('Status')).toBe('Pending (P)');
    expect(actionLink()).toBeNull();
  });

  it('[MAM-87 AC-1] provider1 cannot open the console detail page (access denied, nothing fetched)', async () => {
    signedInAs('provider1');
    installApi(approvalWorld(pendingPass(), passedScreening()).routes);
    renderApp(detailPath(RIVERA_NPI));

    expect(await screen.findByRole('heading', { level: 1, name: 'Access denied' })).toBeInTheDocument();
    expect(api.calls.filter((call) => call.url.pathname.startsWith('/api/v1/providers/'))).toHaveLength(0);
  });

  it('[MAM-87 AC-1] an unknown provider on the detail page shows "No provider found" and no action', async () => {
    signedInAs('analyst1');
    const world = approvalWorld(pendingPass(), passedScreening());
    world.routes[`GET /api/v1/providers/${RIVERA_NPI}`] = () => response(404, problem(404, 'Not Found'));
    installApi(world.routes);
    renderApp(detailPath(RIVERA_NPI));

    expect(await screen.findByText('No provider found')).toBeInTheDocument();
    expect(actionLink()).toBeNull();
  });

  it('[MAM-87 AC-1] a detail page that cannot be loaded (500) shows an alert, not an empty page', async () => {
    signedInAs('analyst1');
    const world = approvalWorld(pendingPass(), passedScreening());
    world.routes[`GET /api/v1/providers/${RIVERA_NPI}`] = () => response(500, problem(500, 'Internal Server Error'));
    installApi(world.routes);
    renderApp(detailPath(RIVERA_NPI));

    expect(await screen.findByText(/provider could not be loaded/i)).toBeInTheDocument();
    expect(actionLink()).toBeNull();
  });

  it('[MAM-87 AC-1] the detail page of a hospice shows its legal name as Name and its codes with their meanings', async () => {
    await openDetail('analyst1', pendingUnscreened());

    expect(fieldValue('NPI')).toBe(HOSPICE_NPI);
    expect(fieldValue('Name')).toBe('SUNSET HOSPICE INC');
    expect(fieldValue('Status')).toBe('Pending (P)');
  });
});

describe('[MAM-87 AC-1] the NPI of a Provider search result links to the provider detail (MAM-77 AC-2)', () => {
  const ALPHA = provider({
    npi: '1005940865',
    medicaidId: '989455927',
    enrollStatus: 'A',
    individual: { lastName: 'ALPHA', firstName: 'TESTPROV', licenseNo: 'SD10001', licenseState: 'TX' },
  });

  async function searchFor(npi: string) {
    signedInAs('analyst1');
    installApi({
      ...approvalWorld(ALPHA, passedScreening(npi)).routes,
      'GET /api/v1/providers': () => response(200, { items: [ALPHA], page: 0, size: 20, total: 1 }),
      [`GET /api/v1/providers/${npi}`]: () => response(200, ALPHA),
    });
    const rendered = renderApp('/console/providers');
    await screen.findByRole('heading', { level: 1, name: 'Provider search' });
    await rendered.user.type(screen.getByLabelText('NPI', { exact: true }), npi);
    await rendered.user.click(screen.getByRole('button', { name: 'Search' }));
    const table = await waitFor(() => {
      const found = screen.queryAllByRole('table').find((candidate) => within(candidate).queryByRole('columnheader', { name: 'Risk' }));
      if (!found || bodyRows(found).length === 0) throw new Error('no result row yet');
      return found;
    });
    return { ...rendered, row: bodyRows(table)[0]! };
  }

  it('[MAM-87 AC-1] the NPI in the result row is a link to /console/providers/:npi and is still shown in full', async () => {
    const { row } = await searchFor(ALPHA.npi);

    const link = within(row).getByRole('link', { name: ALPHA.npi });
    expect(link).toHaveAttribute('href', detailPath(ALPHA.npi));
    expect(text(row.children[0])).toBe('1005940865');
  });

  it('[MAM-87 AC-1] following the link opens the provider detail page of that provider', async () => {
    const { user, row, path } = await searchFor(ALPHA.npi);

    await user.click(within(row).getByRole('link', { name: ALPHA.npi }));

    expect(await screen.findByRole('heading', { level: 1, name: 'Provider detail' })).toBeInTheDocument();
    expect(path()).toBe(detailPath(ALPHA.npi));
    await waitFor(() => expect(fieldValue('NPI')).toBe('1005940865'));
    expect(fieldValue('Name')).toBe('ALPHA TESTPROV');
  });
});
