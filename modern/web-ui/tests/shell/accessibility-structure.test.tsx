// Component-level part of AC-7: page structure and keyboard use. The axe scans run in the browser (e2e/ui-shell.spec.ts).
import { screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { application, APPLICATION_ID } from '../support/fixtures';
import {
  defaultRoutes,
  findApplicationsTable,
  findApplyControl,
  installApi,
  menuEntries,
  oidc,
  renderApp,
  signedInAs,
  signOutControl,
  tabUntil,
  setupShellWorld,
} from '../support/world';

vi.mock('oidc-client-ts', async (importOriginal) => {
  const actual = await importOriginal<typeof import('oidc-client-ts')>();
  const { FakeUserManager } = await import('../support/fakeOidc');
  return { ...actual, UserManager: FakeUserManager };
});

setupShellWorld();

async function openPortalHome() {
  signedInAs('provider1');
  installApi(defaultRoutes([application()]));
  const app = renderApp('/portal');
  await findApplicationsTable();
  return app;
}

function expectShellLandmarks(): void {
  expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1);
  // ARIA: a header is the banner landmark only when it is not inside sectioning content (the USWDS government banner
  // has its own header inside a section, which testing-library's role mapping also reports as "banner").
  const pageHeaders = Array.from(document.querySelectorAll('header')).filter((h) => !h.closest('section, article, aside, main, nav'));
  expect(pageHeaders).toHaveLength(1);
  expect(screen.getAllByRole('navigation').length).toBeGreaterThanOrEqual(1);
  expect(screen.getAllByRole('main')).toHaveLength(1);
}

describe('[MAM-62 AC-7] the shell and portal home: structure and keyboard', () => {
  it('[MAM-62 AC-7] the portal home has one h1 and the landmarks header, nav and main', async () => {
    await openPortalHome();

    expectShellLandmarks();
  });

  it('[MAM-62 AC-7] the portal home without applications has one h1 and the landmarks header, nav and main', async () => {
    signedInAs('provider1');
    installApi(defaultRoutes([]));
    renderApp('/portal');
    await screen.findByText('No applications yet');

    expectShellLandmarks();
  });

  it('[MAM-62 AC-7] the access-denied page has one h1 and the landmarks header, nav and main', async () => {
    signedInAs('provider1');
    renderApp('/console/providers');
    await screen.findByRole('heading', { level: 1, name: 'Access denied' });

    expectShellLandmarks();
  });

  it('[MAM-62 AC-7] the signed-out page has one h1 and the landmarks header, nav and main', async () => {
    signedInAs('provider1');
    const first = renderApp('/portal');
    await waitFor(() => expect(menuEntries().length).toBeGreaterThan(0));
    await first.user.click(signOutControl());
    await waitFor(() => expect(oidc.signoutRedirect).toHaveBeenCalled());
    first.unmount();

    renderApp(oidc.returnFromLogout());
    await screen.findByRole('heading', { level: 1, name: /signed out/i });

    expectShellLandmarks();
  });

  it('[MAM-62 AC-7] "Skip to main content" is the first focus stop and jumps to the main landmark', async () => {
    const app = await openPortalHome();
    await waitFor(() => expect(menuEntries().length).toBeGreaterThan(0));

    await app.user.tab();

    const skip = screen.getByRole('link', { name: 'Skip to main content' });
    expect(skip).toHaveFocus();
    const target = document.querySelector(skip.getAttribute('href') ?? '#none');
    expect(target).toBe(screen.getByRole('main'));
  });

  it.each([
    ['My enrolment', '/portal'],
    ['Apply for enrolment', '/portal/apply'],
    ['My provider record', '/portal/provider'],
    ['My notices', '/portal/notices'],
  ])('[MAM-62 AC-7] the menu entry "%s" is reached with Tab and opened with Enter (%s)', async (name, href) => {
    const app = await openPortalHome();

    const nav = screen.getAllByRole('navigation')[0]!;
    const stops = await tabUntil(app.user, (element) => within(nav).queryByRole('link', { name }) === element);
    expect(stops.at(-1)).toBe(name);
    await app.user.keyboard('{Enter}');

    await waitFor(() => expect(app.path()).toBe(href));
  });

  it('[MAM-62 AC-7] a View link is reached with Tab and opened with Enter', async () => {
    const app = await openPortalHome();
    const table = await findApplicationsTable();
    const view = within(table).getByRole('link', { name: /view/i });

    await tabUntil(app.user, (element) => element === view);
    await app.user.keyboard('{Enter}');

    await waitFor(() => expect(app.path()).toBe(`/portal/applications/${APPLICATION_ID}`));
  });

  it('[MAM-62 AC-7] the button Apply for enrolment is reached with Tab and used with Enter', async () => {
    const app = await openPortalHome();
    const apply = await findApplyControl();

    await tabUntil(app.user, (element) => element === apply);
    await app.user.keyboard('{Enter}');

    await waitFor(() => expect(app.path()).toBe('/portal/apply'));
  });
});
