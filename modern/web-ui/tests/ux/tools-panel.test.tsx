// UX overhaul 5, the Tools panel (user decision Q-101 B2, 2026-11-19): on the provider search, the start page of the console roles, a
// supervisor and an administrator get a "Tools" panel that lists the other screens their role may open, each with one line on
// what it is for. An analyst gets none. The menu is unchanged; the wording of the links differs from the menu entries on purpose, so
// that every link of a page has its own name. No new route, menu entry or start page.
import { screen, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { defaultRoutes, installApi, menuEntries, renderApp, setupShellWorld, signedInAs, tabUntil, text } from '../support/world';
import type { UserName } from '../support/fixtures';

vi.mock('oidc-client-ts', async (importOriginal) => {
  const actual = await importOriginal<typeof import('oidc-client-ts')>();
  const { FakeUserManager } = await import('../support/fakeOidc');
  return { ...actual, UserManager: FakeUserManager };
});

setupShellWorld();

async function open(who: UserName, route = '/console/providers', heading = 'Provider search') {
  signedInAs(who);
  installApi(defaultRoutes());
  const rendered = renderApp(route);
  await screen.findByRole('heading', { level: 1, name: heading });
  return rendered;
}

const panel = (): HTMLElement | null => screen.queryByRole('region', { name: 'Tools' });

/** [link text, href, the line under it] of every tool in the panel. */
function tools(): Array<[string, string, string]> {
  const region = panel();
  if (!region) return [];
  return Array.from(region.querySelectorAll('li')).map((item) => {
    const link = item.querySelector('a');
    return [text(link), link?.getAttribute('href') ?? '', text(item.querySelector('span'))] as [string, string, string];
  });
}

const QUEUES: [string, string, string] = [
  'Review providers waiting for a decision',
  '/console/queues',
  'The four lists of the day: approvals, failed screenings, revalidations and rejected applications.',
];
const JOBS: [string, string, string] = [
  'Run legacy jobs and download their files',
  '/console/admin/jobs',
  'Start a job of the old system for a business date and save the files it produced.',
];
const RISK: [string, string, string] = [
  'Change how risk levels are assigned',
  '/console/admin/risk-rules',
  'See the table that turns a specialty prefix into a risk level, and save a new version.',
];

describe('[UX-OVERHAUL 5] who sees the Tools panel (Q-101 B2: supervisor and administrator, not the analyst)', () => {
  it('[UX-OVERHAUL 5] an analyst has no Tools panel and no "Tools" heading', async () => {
    await open('analyst1');

    expect(panel()).toBeNull();
    expect(screen.queryByRole('heading', { name: 'Tools' })).toBeNull();
  });

  it('[UX-OVERHAUL 5] a supervisor sees one tool: the work queues, with its line', async () => {
    await open('supervisor1');

    expect(tools()).toEqual([QUEUES]);
  });

  it('[UX-OVERHAUL 5] an administrator sees two tools: the batch jobs and the risk-tier rules, and not the queues (the role may not open them)', async () => {
    await open('admin1');

    expect(tools()).toEqual([JOBS, RISK]);
  });

  it('[UX-OVERHAUL 5] the panel is a region named "Tools" with an h2 "Tools", built as a USWDS summary box', async () => {
    await open('admin1');

    const region = panel()!;
    const heading = within(region).getByRole('heading', { level: 2, name: 'Tools' });
    expect(heading).toHaveClass('usa-summary-box__heading');
    expect(region).toHaveClass('usa-summary-box');
    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1);
  });

  it.each(['supervisor1', 'admin1'] as UserName[])('[UX-OVERHAUL 5] %s: every tool leads to a screen that the role may open (it is in the menu of the role)', async (who) => {
    await open(who);

    const menuHrefs = menuEntries().map(([, href]) => href);
    const toolHrefs = tools().map(([, href]) => href);
    expect(toolHrefs.length).toBeGreaterThan(0);
    // the work queues are not in an administrator's menu and not in the panel; every tool that is shown is a screen of the menu
    expect(toolHrefs.filter((href) => !menuHrefs.includes(href))).toEqual([]);
  });

  it.each(['supervisor1', 'admin1'] as UserName[])('[UX-OVERHAUL 5] %s: no tool repeats the name of a menu link, so every link of the page has its own name', async (who) => {
    await open(who);

    const menuNames = menuEntries().map(([name]) => name);
    expect(tools().map(([name]) => name).filter((name) => menuNames.includes(name))).toEqual([]);
    const all = screen.getAllByRole('link').map((link) => text(link));
    expect(new Set(all).size).toBe(all.length);
  });
});

describe('[UX-OVERHAUL 5] where the panel is', () => {
  it('[UX-OVERHAUL 5] on the provider search it comes after the introduction and before the search form', async () => {
    await open('admin1');

    const region = panel()!;
    const intro = screen.getByText('Find a provider by NPI, Medicaid ID or name.');
    const form = screen.getByRole('form', { name: 'Provider search' });
    expect(intro.compareDocumentPosition(region) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(region.compareDocumentPosition(form) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(screen.getByRole('main').contains(region)).toBe(true);
  });

  it('[UX-OVERHAUL 5] a supervisor does not see it on the work queues, and the menu is the same as without the panel', async () => {
    await open('supervisor1', '/console/queues', 'Work queues');

    expect(panel()).toBeNull();
    expect(menuEntries().map(([name]) => name)).toEqual(['Provider search', 'Work queues']);
  });

  it('[UX-OVERHAUL 5] an administrator does not see it on the batch jobs page', async () => {
    await open('admin1', '/console/admin/jobs', 'Batch jobs');

    expect(panel()).toBeNull();
  });

  it('[UX-OVERHAUL 5] the menu of an administrator is unchanged by the panel: Provider search, Risk-tier rules, Batch jobs', async () => {
    await open('admin1');

    expect(menuEntries().map(([name]) => name)).toEqual(['Provider search', 'Risk-tier rules', 'Batch jobs']);
  });
});

describe('[UX-OVERHAUL 5] using the tools', () => {
  it('[UX-OVERHAUL 5] the supervisor tool opens the work queues', async () => {
    const { user, path } = await open('supervisor1');

    await user.click(screen.getByRole('link', { name: QUEUES[0] }));

    expect(await screen.findByRole('heading', { level: 1, name: 'Work queues' })).toBeInTheDocument();
    expect(path()).toBe('/console/queues');
  });

  it.each([
    [JOBS, 'Batch jobs'],
    [RISK, 'Risk-tier rules'],
  ] as Array<[[string, string, string], string]>)('[UX-OVERHAUL 5] the administrator tool "%s" opens its screen', async ([name, href], heading) => {
    const { user, path } = await open('admin1');

    await user.click(screen.getByRole('link', { name }));

    expect(await screen.findByRole('heading', { level: 1, name: heading })).toBeInTheDocument();
    expect(path()).toBe(href);
  });

  it('[UX-OVERHAUL 5] by keyboard the tools come after the menu and before the first search field, in the visual order', async () => {
    const { user } = await open('admin1');

    const stops = await tabUntil(user, (element) => element === screen.getByLabelText('NPI', { exact: true }));

    const first = stops.indexOf(JOBS[0]);
    const second = stops.indexOf(RISK[0]);
    expect(stops.indexOf('Batch jobs')).toBeLessThan(first);
    expect(first).toBeGreaterThan(stops.indexOf('Sign out'));
    expect(second).toBe(first + 1);
    expect(second).toBeLessThan(stops.length - 1);
  });
});
