import { waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { makeUser, MENU, USER_NAMES, type UserName } from '../support/fixtures';
import { menuEntries, oidc, renderApp, signedInAs, sorted, setupShellWorld } from '../support/world';

vi.mock('oidc-client-ts', async (importOriginal) => {
  const actual = await importOriginal<typeof import('oidc-client-ts')>();
  const { FakeUserManager } = await import('../support/fakeOidc');
  return { ...actual, UserManager: FakeUserManager };
});

setupShellWorld();

const START: Record<UserName, string> = {
  provider1: '/portal',
  analyst1: '/console/providers',
  supervisor1: '/console/providers',
  admin1: '/console/providers',
};

describe('[MAM-62 AC-2] the menu shows only what the role allows', () => {
  it.each(USER_NAMES)('[MAM-62 AC-2] %s sees exactly the menu entries of the role, with their routes', async (name) => {
    signedInAs(name);
    renderApp(START[name]);

    await waitFor(() => expect(sorted(menuEntries())).toEqual(sorted(MENU[name])));
  });

  it('[MAM-62 AC-2] provider1 sees My enrolment, Apply for enrolment, My provider record and My notices and no console entry', async () => {
    signedInAs('provider1');
    renderApp('/portal');

    await waitFor(() =>
      expect(menuEntries().map(([name]) => name)).toEqual(
        expect.arrayContaining(['My enrolment', 'Apply for enrolment', 'My provider record', 'My notices']),
      ),
    );
    expect(menuEntries().filter(([, href]) => href.startsWith('/console'))).toEqual([]);
  });

  it.each(['analyst1', 'supervisor1'] as const)(
    '[MAM-62 AC-2] %s sees Provider search and Work queues and no entry of the administrator',
    async (name) => {
      signedInAs(name);
      renderApp('/console/providers');

      await waitFor(() =>
        expect(menuEntries()).toEqual(
          expect.arrayContaining([
            ['Provider search', '/console/providers'],
            ['Work queues', '/console/queues'],
          ]),
        ),
      );
      expect(menuEntries().filter(([, href]) => href.startsWith('/console/admin') || href.startsWith('/portal'))).toEqual([]);
    },
  );

  it('[MAM-62 AC-2] admin1 sees Provider search, Risk-tier rules and Batch jobs, and no Work queues entry', async () => {
    signedInAs('admin1');
    renderApp('/console/providers');

    await waitFor(() =>
      expect(menuEntries()).toEqual(
        expect.arrayContaining([
          ['Provider search', '/console/providers'],
          ['Risk-tier rules', '/console/admin/risk-rules'],
          ['Batch jobs', '/console/admin/jobs'],
        ]),
      ),
    );
    expect(menuEntries().map(([name]) => name)).not.toContain('Work queues');
  });

  it('[MAM-62 AC-2] the menu follows the role claim of the token, whatever the user name is', async () => {
    oidc.user = makeUser('analyst1', 'jdoe');
    renderApp('/console/providers');

    await waitFor(() => expect(sorted(menuEntries())).toEqual(sorted(MENU.analyst1)));
  });
});
