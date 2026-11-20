// UX overhaul 12 and 13, the landing page and the two error pages (UX overhaul of every screen, 2026-11-19): the entry page "/" says
// what the application is for, offers the two ways in as buttons (Sign in, Find a Medicaid provider) and shows one card per group
// of users with what the group does and where it starts after signing in. "Page not found" (404) and "Access denied" (403) each end
// in a button to the start page OF THE ROLE, with the words of the role, and a short line on what to do. The start pages named on
// the cards are the pages the roles really land on.
import { screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { makeUser } from '../support/fixtures';
import { api, defaultRoutes, installApi, oidc, renderApp, setupShellWorld, signedInAs, tabUntil, text } from '../support/world';
import type { UserName } from '../support/fixtures';

vi.mock('oidc-client-ts', async (importOriginal) => {
  const actual = await importOriginal<typeof import('oidc-client-ts')>();
  const { FakeUserManager } = await import('../support/fakeOidc');
  return { ...actual, UserManager: FakeUserManager };
});

setupShellWorld();

const LANDING_H1 = 'Provider Enrollment and Management';
const base64 = (value: unknown): string => Buffer.from(JSON.stringify(value)).toString('base64url');

/** A signed-in session whose token carries no role of this application. */
function signedInWithoutRole(): void {
  const user = makeUser('provider1');
  const claims = { ...user.profile, realm_access: { roles: [] as string[] } };
  const token = `${base64({ alg: 'RS256', typ: 'JWT' })}.${base64(claims)}.c2ln`;
  oidc.user = { ...user, access_token: token, id_token: token, profile: claims };
}

async function openLanding() {
  installApi(defaultRoutes());
  const rendered = renderApp('/');
  await screen.findByRole('heading', { level: 1, name: LANDING_H1 });
  await within(screen.getByRole('main')).findByRole('button', { name: 'Sign in' });
  return rendered;
}

const mainArea = () => within(screen.getByRole('main'));
const before = (first: Element, second: Element): boolean => (first.compareDocumentPosition(second) & Node.DOCUMENT_POSITION_FOLLOWING) !== 0;

describe('[UX-OVERHAUL 12] the landing page: what it is for and the two ways in', () => {
  it('[UX-OVERHAUL 12] one h1 "Provider Enrollment and Management" and an introduction that tells a visitor how to start', async () => {
    await openLanding();

    expect(screen.getAllByRole('heading', { level: 1 }).map((heading) => text(heading))).toEqual([LANDING_H1]);
    expect(mainArea().getByText('Sign in with your own account to enrol, check an application or work with provider records.')).toHaveClass('usa-intro');
  });

  it('[UX-OVERHAUL 12] "Sign in" is a button in the main area, and choosing it sends the visitor to the identity service, coming back to the entry page', async () => {
    const { user } = await openLanding();

    const button = mainArea().getByRole('button', { name: 'Sign in' });
    expect(button).toHaveClass('usa-button');
    await user.click(button);

    expect(oidc.signinRedirect).toHaveBeenCalledTimes(1);
    expect(oidc.lastSigninArgs?.state).toEqual({ returnTo: '/' });
  });

  it('[UX-OVERHAUL 12] "Find a Medicaid provider" is a link styled as an outline button that leads to the public directory without any sign-in', async () => {
    const { user, path } = await openLanding();

    const link = mainArea().getByRole('link', { name: 'Find a Medicaid provider' });
    expect(link).toHaveAttribute('href', '/directory');
    expect(link).toHaveClass('usa-button', 'usa-button--outline');
    await user.click(link);

    expect(await screen.findByRole('heading', { level: 1, name: 'Find a Medicaid provider' })).toBeInTheDocument();
    expect(path()).toBe('/directory');
    expect(oidc.signinRedirect).not.toHaveBeenCalled();
  });

  it('[UX-OVERHAUL 12] a line says that no sign-in is needed to look up a provider, and it comes after the two buttons', async () => {
    await openLanding();

    const note = mainArea().getByText('You do not need to sign in to look up a provider.');
    expect(before(mainArea().getByRole('button', { name: 'Sign in' }), note)).toBe(true);
    expect(before(mainArea().getByRole('link', { name: 'Find a Medicaid provider' }), note)).toBe(true);
  });

  it('[UX-OVERHAUL 12] by keyboard the two buttons are reached one after the other: Sign in, then Find a Medicaid provider', async () => {
    const { user } = await openLanding();
    const link = mainArea().getByRole('link', { name: 'Find a Medicaid provider' });

    const stops = await tabUntil(user, (element) => element === link);

    expect(stops.slice(-2)).toEqual(['Sign in', 'Find a Medicaid provider']);
    await user.keyboard('{Enter}');
    expect(await screen.findByRole('heading', { level: 1, name: 'Find a Medicaid provider' })).toBeInTheDocument();
  });
});

describe('[UX-OVERHAUL 12] the cards: one per group of users', () => {
  const CARDS: Array<[string, string, string, string]> = [
    ['For providers', 'Apply for enrolment, follow your application, see your provider record and the notices sent to you.', 'You start on My enrolment.', 'My enrolment'],
    [
      'For analysts and supervisors',
      'Search providers, work the queues, approve or deny an enrolment and, as a supervisor, record sanctions.',
      'You start on Provider search.',
      'Provider search',
    ],
    ['For administrators', 'Run the batch jobs of the old system and keep the table of risk levels.', 'You start on Provider search.', 'Provider search'],
  ];

  it('[UX-OVERHAUL 12] there are three cards, each a region named by its h2, in the order providers, analysts and supervisors, administrators', async () => {
    await openLanding();

    const regions = mainArea().getAllByRole('region');
    expect(regions.map((region) => region.querySelector('h2')?.textContent)).toEqual(CARDS.map(([name]) => name));
    expect(mainArea().getAllByRole('heading', { level: 2 })).toHaveLength(3);
  });

  it.each(CARDS)('[UX-OVERHAUL 12] the card "%s" says what the group does and where it starts', async (name, does, starts) => {
    await openLanding();

    const card = mainArea().getByRole('region', { name });
    expect(within(card).getByText(does)).toBeInTheDocument();
    expect(within(card).getByText(starts)).toBeInTheDocument();
  });

  it('[UX-OVERHAUL 12] the cards come after the buttons', async () => {
    await openLanding();

    const first = mainArea().getByRole('region', { name: 'For providers' });
    expect(before(mainArea().getByRole('link', { name: 'Find a Medicaid provider' }), first)).toBe(true);
  });

  it.each([
    ['For providers', ['provider1']],
    ['For analysts and supervisors', ['analyst1', 'supervisor1']],
    ['For administrators', ['admin1']],
  ] as Array<[string, UserName[]]>)('[UX-OVERHAUL 12] the card "%s" names the page its users really land on after signing in', async (name, users) => {
    const { unmount } = await openLanding();
    const card = mainArea().getByRole('region', { name });
    const claimed = /You start on (.+)\./.exec(text(card))?.[1];
    expect(claimed).toBeDefined();
    unmount();

    for (const who of users) {
      signedInAs(who);
      const view = renderApp('/');
      expect(await screen.findByRole('heading', { level: 1, name: claimed })).toBeInTheDocument();
      view.unmount();
    }
  });

  it('[UX-OVERHAUL 12] a signed-in user is sent on to the page of the role and sees neither the buttons nor the cards', async () => {
    signedInAs('analyst1');
    installApi(defaultRoutes());
    const { path } = renderApp('/');

    expect(await screen.findByRole('heading', { level: 1, name: 'Provider search' })).toBeInTheDocument();
    expect(path()).toBe('/console/providers');
    expect(screen.queryByRole('region', { name: 'For providers' })).toBeNull();
    expect(screen.queryByRole('link', { name: 'Find a Medicaid provider' })).toBeNull();
  });

  it('[UX-OVERHAUL 12] a signed-in user whose account has no role is told to ask an administrator, and gets no way into a screen', async () => {
    signedInWithoutRole();
    installApi(defaultRoutes());
    renderApp('/');

    expect(await screen.findByRole('heading', { level: 1, name: LANDING_H1 })).toBeInTheDocument();
    expect(await screen.findByText('Your account has no role in this application. Ask an administrator to assign one.')).toBeInTheDocument();
    expect(mainArea().queryByRole('button', { name: 'Sign in' })).toBeNull();
    expect(mainArea().queryAllByRole('region')).toHaveLength(0);
    expect(api.calls).toEqual([]);
  });
});

describe('[UX-OVERHAUL 13] "Page not found": a button to the start page of the role', () => {
  it.each([
    ['provider1', 'Go to My enrolment', '/portal', 'My enrolment'],
    ['analyst1', 'Go to Provider search', '/console/providers', 'Provider search'],
    ['supervisor1', 'Go to Provider search', '/console/providers', 'Provider search'],
    ['admin1', 'Go to Provider search', '/console/providers', 'Provider search'],
  ] as Array<[UserName, string, string, string]>)('[UX-OVERHAUL 13] %s on an unknown address: the button "%s" leads to %s and opens "%s"', async (who, label, href, heading) => {
    signedInAs(who);
    installApi(defaultRoutes());
    const { user, path } = renderApp('/no/such/page');

    const button = await screen.findByRole('link', { name: label });
    expect(button).toHaveAttribute('href', href);
    expect(button).toHaveClass('usa-button');
    await user.click(button);
    expect(await screen.findByRole('heading', { level: 1, name: heading })).toBeInTheDocument();
    expect(path()).toBe(href);
  });

  it('[UX-OVERHAUL 13] the page says error 404 and what to do, has one h1 and sets the title of the browser tab', async () => {
    signedInAs('provider1');
    installApi(defaultRoutes());
    renderApp('/no/such/page');

    await screen.findByRole('link', { name: 'Go to My enrolment' });
    expect(screen.getAllByRole('heading', { level: 1 }).map((heading) => text(heading))).toEqual(['Page not found']);
    expect(mainArea().getByText('Error 404. The page you asked for does not exist. Use the menu to continue.')).toHaveClass('usa-intro');
    expect(mainArea().getByText('Check the address for a typing mistake, or go back to where you started.')).toBeInTheDocument();
    expect(document.title).toBe('Page not found - PEM Provider Enrollment');
  });

  it('[UX-OVERHAUL 13] a visitor who is not signed in gets "Go to the start page", which opens the entry page', async () => {
    installApi(defaultRoutes());
    const { user, path } = renderApp('/no/such/page');

    const button = await screen.findByRole('link', { name: 'Go to the start page' });
    expect(button).toHaveAttribute('href', '/');
    expect(button).toHaveClass('usa-button');
    await user.click(button);
    expect(await screen.findByRole('heading', { level: 1, name: LANDING_H1 })).toBeInTheDocument();
    expect(path()).toBe('/');
  });

  it.each([
    ['analyst1', '/console/no-such-screen'],
    ['provider1', '/portal/no-such-screen'],
  ] as Array<[UserName, string]>)('[UX-OVERHAUL 13] %s on %s, an unknown screen of the own area, gets "Page not found" and not "Access denied"', async (who, route) => {
    signedInAs(who);
    installApi(defaultRoutes());
    renderApp(route);

    expect(await screen.findByRole('heading', { level: 1, name: 'Page not found' })).toBeInTheDocument();
  });

  it.each([
    ['provider1', '/console/no-such-screen'],
    ['analyst1', '/portal/no-such-screen'],
  ] as Array<[UserName, string]>)('[UX-OVERHAUL 13] %s on %s, an unknown screen of the other area, gets "Access denied" (the area is not theirs)', async (who, route) => {
    signedInAs(who);
    installApi(defaultRoutes());
    renderApp(route);

    expect(await screen.findByRole('heading', { level: 1, name: 'Access denied' })).toBeInTheDocument();
  });

  it('[UX-OVERHAUL 13] it asks the services for nothing', async () => {
    signedInAs('analyst1');
    installApi(defaultRoutes());
    renderApp('/no/such/page');

    await screen.findByRole('link', { name: 'Go to Provider search' });
    expect(api.calls).toEqual([]);
  });
});

describe('[UX-OVERHAUL 13] "Access denied": the words of the role and a button to its start page', () => {
  it.each([
    ['provider1', '/console/queues', 'Provider', 'Go to My enrolment', '/portal', 'My enrolment'],
    ['analyst1', '/portal/apply', 'Analyst', 'Go to Provider search', '/console/providers', 'Provider search'],
    ['supervisor1', '/console/admin/jobs', 'Supervisor', 'Go to Provider search', '/console/providers', 'Provider search'],
    ['admin1', '/console/queues', 'Administrator', 'Go to Provider search', '/console/providers', 'Provider search'],
  ] as Array<[UserName, string, string, string, string, string]>)(
    '[UX-OVERHAUL 13] %s typing %s: "Error 403. Your role (%s) does not allow you to open this page.", the button "%s" leads to %s and opens "%s"',
    async (who, route, role, label, href, heading) => {
      signedInAs(who);
      installApi(defaultRoutes());
      const { user, path } = renderApp(route);

      await screen.findByRole('heading', { level: 1, name: 'Access denied' });
      expect(mainArea().getByText(`Error 403. Your role (${role}) does not allow you to open this page.`)).toHaveClass('usa-intro');
      const button = mainArea().getByRole('link', { name: label });
      expect(button).toHaveAttribute('href', href);
      expect(button).toHaveClass('usa-button');
      await user.click(button);
      expect(await screen.findByRole('heading', { level: 1, name: heading })).toBeInTheDocument();
      expect(path()).toBe(href);
    },
  );

  it('[UX-OVERHAUL 13] the page tells the user what to do if the refusal is a mistake, and sets the title of the browser tab', async () => {
    signedInAs('provider1');
    installApi(defaultRoutes());
    renderApp('/console/queues');

    await screen.findByRole('heading', { level: 1, name: 'Access denied' });
    expect(mainArea().getByText('If you think this is a mistake, ask an administrator to check the role of your account.')).toBeInTheDocument();
    expect(document.title).toBe('Access denied - PEM Provider Enrollment');
  });

  it('[UX-OVERHAUL 13] by keyboard the button is reached after the menu, and Enter opens the start page', async () => {
    signedInAs('provider1');
    installApi(defaultRoutes());
    const { user, path } = renderApp('/console/queues');
    await screen.findByRole('heading', { level: 1, name: 'Access denied' });
    const button = mainArea().getByRole('link', { name: 'Go to My enrolment' });

    const stops = await tabUntil(user, (element) => element === button);

    expect(stops.indexOf('Sign out')).toBeLessThan(stops.length - 1);
    expect(stops.at(-1)).toBe('Go to My enrolment');
    await user.keyboard('{Enter}');
    await waitFor(() => expect(path()).toBe('/portal'));
    expect(await screen.findByRole('heading', { level: 1, name: 'My enrolment' })).toBeInTheDocument();
  });

  it('[UX-OVERHAUL 13] an account with no role gets no role name in the sentence and a button to the entry page', async () => {
    signedInWithoutRole();
    installApi(defaultRoutes());
    const { user, path } = renderApp('/portal');

    await screen.findByRole('heading', { level: 1, name: 'Access denied' });
    expect(mainArea().getByText('Error 403. Your role does not allow you to open this page.')).toBeInTheDocument();
    const button = mainArea().getByRole('link', { name: 'Go to the start page' });
    expect(button).toHaveAttribute('href', '/');
    await user.click(button);
    expect(await screen.findByRole('heading', { level: 1, name: LANDING_H1 })).toBeInTheDocument();
    expect(path()).toBe('/');
  });
});
