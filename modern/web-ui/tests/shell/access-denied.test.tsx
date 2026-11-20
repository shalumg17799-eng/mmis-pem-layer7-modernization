import { screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { MENU, roleOf, SCREENS, USER_NAMES, type UserName } from '../support/fixtures';
import { api, menuEntries, renderApp, signedInAs, setupShellWorld } from '../support/world';

vi.mock('oidc-client-ts', async (importOriginal) => {
  const actual = await importOriginal<typeof import('oidc-client-ts')>();
  const { FakeUserManager } = await import('../support/fakeOidc');
  return { ...actual, UserManager: FakeUserManager };
});

setupShellWorld();

async function accessDeniedHeading() {
  return screen.findByRole('heading', { level: 1, name: 'Access denied' });
}

const REFUSED: Array<[UserName, string]> = [
  ['provider1', '/console/providers'],
  ['analyst1', '/portal/apply'],
  ['admin1', '/console/queues'],
];

describe('[MAM-62 AC-3] a role cannot open the areas of other roles', () => {
  it.each(REFUSED)('[MAM-62 AC-3] %s typing %s sees the heading "Access denied" and the text 403', async (name, path) => {
    signedInAs(name);
    renderApp(path);

    await accessDeniedHeading();
    expect(within(screen.getByRole('main')).getAllByText(/\b403\b/).length).toBeGreaterThan(0);
  });

  it.each(REFUSED)('[MAM-62 AC-3] %s typing %s makes no API call for the data of the refused page', async (name, path) => {
    signedInAs(name);
    renderApp(path);

    await accessDeniedHeading();
    expect(api.calls).toEqual([]);
  });

  it.each(REFUSED)('[MAM-62 AC-3] %s typing %s gets a link to their own start page', async (name, path) => {
    signedInAs(name);
    renderApp(path);

    await accessDeniedHeading();
    const hrefs = within(screen.getByRole('main'))
      .getAllByRole('link')
      .map((link) => link.getAttribute('href'));
    const own = MENU[name].map(([, href]) => href);
    expect(hrefs.some((href) => href !== null && own.includes(href))).toBe(true);
    if (name === 'provider1') expect(hrefs).toContain('/portal');
  });

  it.each(REFUSED)('[MAM-62 AC-3] on the access-denied page of %s (%s) there is one h1 and the menu still shows only the entries of the role', async (name, path) => {
    signedInAs(name);
    renderApp(path);

    await accessDeniedHeading();
    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1);
    expect(menuEntries().map(([, href]) => href).sort()).toEqual(MENU[name].map(([, href]) => href).sort());
  });

  // Every route of the screen inventory against every role (modern/docs/ux/screen-inventory.md, roles column).
  const matrix = USER_NAMES.flatMap((name) =>
    SCREENS.map((screenRoute) => ({
      name,
      path: screenRoute.path,
      allowed: screenRoute.roles.includes(roleOf(name)),
    })),
  );

  it.each(matrix.filter((m) => !m.allowed))(
    '[MAM-62 AC-3] $name is refused on $path (outside the role in the screen inventory)',
    async ({ name, path }) => {
      signedInAs(name);
      renderApp(path);

      await accessDeniedHeading();
      expect(api.calls).toEqual([]);
    },
  );

  it.each(matrix.filter((m) => m.allowed))(
    '[MAM-62 AC-3] $name is not refused on $path (inside the role in the screen inventory)',
    async ({ name, path }) => {
      signedInAs(name);
      renderApp(path);

      // the shell with the menu of the role is shown ...
      await waitFor(() => expect(menuEntries().length).toBe(MENU[name].length));
      // ... and not the refusal
      expect(screen.queryByRole('heading', { name: 'Access denied' })).toBeNull();
      expect(screen.queryByText(/\b403\b/)).toBeNull();
    },
  );
});
