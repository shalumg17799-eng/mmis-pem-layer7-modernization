// UX overhaul 2, the breadcrumb (user decision Q-101 B3, 2026-11-19): the real USWDS <nav aria-label="Breadcrumb"> that follows the
// "Primary navigation" and sits outside main. "Home" is the start page of the role; the page itself is plain text with
// aria-current="page"; the screens of one provider go through "Provider detail". Nothing is shown on the start pages, on the public
// pages, when the role may not open the page, or on the 404 page. The trail uses names of its own, so no link repeats a menu name.
import { screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { type Handler } from '../support/fakeApi';
import { approvalWorld, openApproval, passedScreening, pendingPass } from '../support/approval';
import { APPLICATION_ID, PROVIDER_NPI, type UserName } from '../support/fixtures';
import { defaultRoutes, installApi, menuEntries, renderApp, setupShellWorld, signedInAs, tabUntil, text } from '../support/world';
import { breadcrumbNav, trail } from '../support/uxWorld';

vi.mock('oidc-client-ts', async (importOriginal) => {
  const actual = await importOriginal<typeof import('oidc-client-ts')>();
  const { FakeUserManager } = await import('../support/fakeOidc');
  return { ...actual, UserManager: FakeUserManager };
});

setupShellWorld();

const DETAIL = `/console/providers/${PROVIDER_NPI}`;

/** Sign in as `who`, open `route` and wait for the h1 of the page. Calls that are not declared answer 404; the trail does not depend on them. */
async function open(who: UserName, route: string, heading: string, routes: Record<string, Handler> = {}) {
  signedInAs(who);
  installApi({ ...defaultRoutes(), ...routes });
  const rendered = renderApp(route);
  await screen.findByRole('heading', { level: 1, name: heading });
  return rendered;
}

type Trail = Array<[label: string, href: string | null]>;

const TRAILS: Array<[UserName, string, string, Trail]> = [
  ['provider1', '/portal/apply', 'Apply for enrolment', [['Home', '/portal'], ['Apply for enrolment', null]]],
  ['provider1', '/portal/provider', 'My provider record', [['Home', '/portal'], ['My provider record', null]]],
  ['provider1', '/portal/notices', 'My notices', [['Home', '/portal'], ['My notices', null]]],
  ['provider1', `/portal/applications/${APPLICATION_ID}`, 'Application status', [['Home', '/portal'], ['Application status', null]]],
  ['analyst1', '/console/queues', 'Work queues', [['Home', '/console/providers'], ['Work queues', null]]],
  ['supervisor1', '/console/queues', 'Work queues', [['Home', '/console/providers'], ['Work queues', null]]],
  ['analyst1', DETAIL, 'Provider detail', [['Home', '/console/providers'], ['Provider detail', null]]],
  ['admin1', DETAIL, 'Provider detail', [['Home', '/console/providers'], ['Provider detail', null]]],
  ['analyst1', `${DETAIL}/approval`, 'Approve or deny', [['Home', '/console/providers'], ['Provider detail', DETAIL], ['Approve or deny', null]]],
  ['supervisor1', `${DETAIL}/sanctions/new`, 'Record a sanction', [['Home', '/console/providers'], ['Provider detail', DETAIL], ['Record a sanction', null]]],
  ['admin1', '/console/admin/risk-rules', 'Risk-tier rules', [['Home', '/console/providers'], ['Risk-tier rules', null]]],
  ['admin1', '/console/admin/jobs', 'Batch jobs', [['Home', '/console/providers'], ['Batch jobs', null]]],
];

describe('[UX-OVERHAUL 2] the trail of every screen (Q-101 B3: Home, the provider detail for the screens of one provider, then the page itself)', () => {
  it.each(TRAILS)('[UX-OVERHAUL 2] %s on %s: the trail is the Home link, the parents and the page', async (who, route, heading, expected) => {
    await open(who, route, heading);

    expect(trail().map((item) => [item.label, item.href])).toEqual(expected);
  });

  it.each(TRAILS)('[UX-OVERHAUL 2] %s on %s: only the last item is the current page, it is plain text with the words of the h1', async (who, route, heading) => {
    await open(who, route, heading);

    const items = trail();
    expect(items.filter((item) => item.current)).toHaveLength(1);
    expect(items.at(-1)).toEqual({ label: heading, href: null, current: true });
  });

  it.each(TRAILS)('[UX-OVERHAUL 2] %s on %s: no link of the trail repeats the name of a menu link, and "Home" is one link only', async (who, route, heading) => {
    await open(who, route, heading);

    const menuNames = menuEntries().map(([name]) => name);
    const linkNames = trail().filter((item) => item.href !== null).map((item) => item.label);
    expect(linkNames.filter((name) => menuNames.includes(name))).toEqual([]);
    expect(screen.getAllByRole('link', { name: 'Home' })).toHaveLength(1);
  });

  it('[UX-OVERHAUL 2] the markup is the USWDS breadcrumb: nav.usa-breadcrumb with an ordered list, links with usa-breadcrumb__link, the current item with usa-current', async () => {
    await open('analyst1', `${DETAIL}/approval`, 'Approve or deny');

    const nav = breadcrumbNav()!;
    expect(nav).toHaveClass('usa-breadcrumb');
    const list = nav.querySelector('ol');
    expect(list).toHaveClass('usa-breadcrumb__list');
    const items = Array.from(list!.children);
    expect(items.map((item) => item.tagName)).toEqual(['LI', 'LI', 'LI']);
    expect(items.every((item) => item.classList.contains('usa-breadcrumb__list-item'))).toBe(true);
    expect(items.slice(0, 2).map((item) => item.querySelector('a')?.classList.contains('usa-breadcrumb__link'))).toEqual([true, true]);
    expect(items[2]).toHaveClass('usa-current');
    expect(items[2]!.querySelector('a')).toBeNull();
  });
});

describe('[UX-OVERHAUL 2] where the breadcrumb sits in the page', () => {
  it('[UX-OVERHAUL 2] it comes after the Primary navigation and before main, outside main, so the first navigation landmark is still the menu', async () => {
    await open('provider1', '/portal/apply', 'Apply for enrolment');

    const names = screen.getAllByRole('navigation').map((nav) => nav.getAttribute('aria-label'));
    expect(names[0]).toBe('Primary navigation');
    expect(names.indexOf('Breadcrumb')).toBeGreaterThan(names.indexOf('Primary navigation'));
    const menu = screen.getByRole('navigation', { name: 'Primary navigation' });
    const nav = breadcrumbNav()!;
    const main = screen.getByRole('main');
    expect(menu.compareDocumentPosition(nav) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(nav.compareDocumentPosition(main) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(main.contains(nav)).toBe(false);
  });

  it('[UX-OVERHAUL 2] the menu helpers still see only the menu links: the trail adds no entry to the Primary navigation', async () => {
    await open('provider1', '/portal/apply', 'Apply for enrolment');

    expect(menuEntries().map(([name]) => name)).toEqual(['My enrolment', 'Apply for enrolment', 'My provider record', 'My notices']);
  });
});

describe('[UX-OVERHAUL 2] where there is no breadcrumb', () => {
  it.each([
    ['provider1', '/portal', 'My enrolment'],
    ['analyst1', '/console/providers', 'Provider search'],
    ['supervisor1', '/console/providers', 'Provider search'],
    ['admin1', '/console/providers', 'Provider search'],
  ] as Array<[UserName, string, string]>)('[UX-OVERHAUL 2] %s on the start page %s (%s) has no breadcrumb: Home would point to the page itself', async (who, route, heading) => {
    await open(who, route, heading);

    expect(breadcrumbNav()).toBeNull();
  });

  it('[UX-OVERHAUL 2] a visitor who is not signed in has none on the entry page', async () => {
    installApi(defaultRoutes());
    renderApp('/');
    await screen.findByRole('heading', { level: 1, name: 'Provider Enrollment and Management' });

    expect(breadcrumbNav()).toBeNull();
  });

  it('[UX-OVERHAUL 2] a visitor who is not signed in has none on the public directory', async () => {
    installApi(defaultRoutes());
    renderApp('/directory');
    await screen.findByRole('heading', { level: 1, name: 'Find a Medicaid provider' });

    expect(breadcrumbNav()).toBeNull();
  });

  it('[UX-OVERHAUL 2] a signed-in provider has none on the public directory either (it is not a screen of the role)', async () => {
    await open('provider1', '/directory', 'Find a Medicaid provider');

    expect(breadcrumbNav()).toBeNull();
  });

  it.each([
    ['provider1', '/console/queues'],
    ['analyst1', '/portal/apply'],
    ['supervisor1', '/console/admin/jobs'],
    ['admin1', '/console/queues'],
  ] as Array<[UserName, string]>)('[UX-OVERHAUL 2] %s typing %s gets "Access denied" and no breadcrumb that would offer the refused page', async (who, route) => {
    await open(who, route, 'Access denied');

    expect(breadcrumbNav()).toBeNull();
  });

  it.each([
    ['provider1', '/portal/no-such-page'],
    ['analyst1', '/nowhere'],
  ] as Array<[UserName, string]>)('[UX-OVERHAUL 2] %s on the unknown address %s gets "Page not found" and no breadcrumb', async (who, route) => {
    await open(who, route, 'Page not found');

    expect(breadcrumbNav()).toBeNull();
  });
});

describe('[UX-OVERHAUL 2] using the breadcrumb', () => {
  it('[UX-OVERHAUL 2] the Home link of a provider leads to My enrolment and the trail is gone there', async () => {
    const { user, path } = await open('provider1', '/portal/apply', 'Apply for enrolment');

    await user.click(screen.getByRole('link', { name: 'Home' }));

    expect(await screen.findByRole('heading', { level: 1, name: 'My enrolment' })).toBeInTheDocument();
    expect(path()).toBe('/portal');
    expect(breadcrumbNav()).toBeNull();
  });

  it('[UX-OVERHAUL 2] the Home link of an administrator leads to the provider search', async () => {
    const { user, path } = await open('admin1', '/console/admin/jobs', 'Batch jobs');

    await user.click(screen.getByRole('link', { name: 'Home' }));

    expect(await screen.findByRole('heading', { level: 1, name: 'Provider search' })).toBeInTheDocument();
    expect(path()).toBe('/console/providers');
  });

  it('[UX-OVERHAUL 2] on the approval page the "Provider detail" crumb leads back to the detail page of that provider', async () => {
    const { user, path } = await openApproval('analyst1', approvalWorld(pendingPass(), passedScreening()), PROVIDER_NPI);

    await user.click(screen.getByRole('navigation', { name: 'Breadcrumb' }).querySelector(`a[href="${DETAIL}"]`)!);

    expect(await screen.findByRole('heading', { level: 1, name: 'Provider detail' })).toBeInTheDocument();
    expect(path()).toBe(DETAIL);
    expect(trail().map((item) => item.label)).toEqual(['Home', 'Provider detail']);
  });

  it('[UX-OVERHAUL 2] by keyboard the link comes after the menu and the Sign out button, in the visual order, and Enter follows it', async () => {
    const { user, path } = await open('provider1', '/portal/apply', 'Apply for enrolment');
    const home = screen.getByRole('link', { name: 'Home' });

    const stops = await tabUntil(user, (element) => element === home);

    const menu = ['My enrolment', 'Apply for enrolment', 'My provider record', 'My notices'];
    expect(stops.filter((stop) => menu.includes(stop))).toEqual(menu);
    expect(stops.indexOf('Sign out')).toBeGreaterThan(stops.indexOf('My notices'));
    expect(stops.at(-1)).toBe('Home');
    await user.keyboard('{Enter}');
    expect(await screen.findByRole('heading', { level: 1, name: 'My enrolment' })).toBeInTheDocument();
    expect(path()).toBe('/portal');
  });

  it('[UX-OVERHAUL 2] the current page is not a tab stop: after the Home link the focus leaves the breadcrumb', async () => {
    const { user } = await open('provider1', '/portal/apply', 'Apply for enrolment');
    const home = screen.getByRole('link', { name: 'Home' });
    await tabUntil(user, (element) => element === home);

    await user.tab();

    expect(breadcrumbNav()!.contains(document.activeElement)).toBe(false);
    expect(text(document.activeElement)).not.toBe('Apply for enrolment');
  });
});
