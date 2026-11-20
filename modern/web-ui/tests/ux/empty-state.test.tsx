// UX overhaul 4, the shared empty state (UX overhaul point 6; src/shared/ui/EmptyState.tsx): what a list or a page says when there is
// nothing to show yet. One bold line that says so, as a paragraph of its own (so an exact lookup of the line still finds it), then
// what to do or why in plain paragraphs, then the actions. It is used on the public directory, the notices, the portal home (two
// lists), the provider record and the provider search. It is never used for a failed load: a failure is an error alert with a
// retry, never an empty state (D-024). The text of each line is also asserted by the story tests; this file asserts the shape.
import { render, screen, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { EmptyState } from '../../src/shared/ui/EmptyState';
import { directoryRoutes, GROUP, openDirectory, search as searchDirectory } from '../support/directory';
import { problem, response } from '../support/fakeApi';
import { application, PROVIDER_NPI, provider } from '../support/fixtures';
import { answer, NOTICES_ROUTE, numbered, openNotices } from '../support/notices';
import { openSearch, PROVIDERS_ROUTE, search as searchProviders, searchRoutes } from '../support/search';
import { defaultRoutes, installApi, renderApp, setupShellWorld, signedInAs, text } from '../support/world';

vi.mock('oidc-client-ts', async (importOriginal) => {
  const actual = await importOriginal<typeof import('oidc-client-ts')>();
  const { FakeUserManager } = await import('../support/fakeOidc');
  return { ...actual, UserManager: FakeUserManager };
});

setupShellWorld();

const NO_RECORD = () => response(404, problem(404, 'Not Found'));
const FAILURE = () => response(500, problem(500, 'Internal Server Error'));
const PROVIDER_ROUTE = `GET /api/v1/providers/${PROVIDER_NPI}`;

/** The panel that holds the exact line `title`, and its direct children as text (the bold line first). */
function emptyState(title: string): HTMLElement {
  const box = screen.getByText(title).closest<HTMLElement>('.pem-empty-state');
  if (!box) throw new Error(`"${title}" is not inside an empty state`);
  return box;
}
const lines = (box: HTMLElement): string[] => Array.from(box.children).map((child) => text(child));
const emptyStates = (): HTMLElement[] => Array.from(document.querySelectorAll<HTMLElement>('.pem-empty-state'));

describe('[UX-OVERHAUL 4] the EmptyState component: a bold line first, then the explanation and the actions', () => {
  it('[UX-OVERHAUL 4] the title is a paragraph of its own, in bold, and the first thing in the panel', () => {
    render(
      <EmptyState title="Nothing here yet">
        <p>Do this first.</p>
      </EmptyState>,
    );

    const title = screen.getByText('Nothing here yet');
    expect(title.tagName).toBe('P');
    expect(title).toHaveClass('text-bold');
    expect(title.parentElement).toHaveClass('pem-empty-state');
    expect(title.parentElement?.firstElementChild).toBe(title);
  });

  it('[UX-OVERHAUL 4] the children follow the title in the order given, so the actions come after the explanation', () => {
    render(
      <EmptyState title="Nothing here yet">
        <p>Do this first.</p>
        <p>
          <a href="/somewhere">Go somewhere</a>
        </p>
      </EmptyState>,
    );

    expect(lines(emptyState('Nothing here yet'))).toEqual(['Nothing here yet', 'Do this first.', 'Go somewhere']);
  });

  it('[UX-OVERHAUL 4] without children it is the title alone', () => {
    render(<EmptyState title="Nothing here yet" />);

    expect(lines(emptyState('Nothing here yet'))).toEqual(['Nothing here yet']);
  });

  it('[UX-OVERHAUL 4] it adds no heading and no alert role, so the h1 of the page stays the only heading and it is not announced as an error', () => {
    render(
      <main>
        <h1>Page</h1>
        <EmptyState title="Nothing here yet">
          <p>Explain.</p>
        </EmptyState>
      </main>,
    );

    expect(screen.getAllByRole('heading').map((heading) => text(heading))).toEqual(['Page']);
    expect(screen.queryByRole('alert')).toBeNull();
  });
});

describe('[UX-OVERHAUL 4] the five pages that use it, each with its own line and explanation', () => {
  it('[UX-OVERHAUL 4] provider search: "No provider found" with the advice, inside the live status area and with no results table', async () => {
    const { user } = await openSearch(searchRoutes([]));

    await searchProviders(user, { npi: '1003000999' });

    await screen.findByText('No provider found');
    const box = emptyState('No provider found');
    expect(lines(box)).toEqual(['No provider found', 'Check the spelling or the number and search again. Use fewer letters of the name to widen the search.']);
    expect(box.closest('[role="status"]')).not.toBeNull();
    expect(screen.queryByRole('table')).toBeNull();
  });

  it('[UX-OVERHAUL 4] public directory: "No providers found" with the advice for the name and the type', async () => {
    const { user } = await openDirectory(directoryRoutes([]));

    await searchDirectory(user, 'NOBODY');

    await screen.findByText('No providers found');
    expect(lines(emptyState('No providers found'))).toEqual(['No providers found', 'Check the spelling, try a shorter part of the name, or set Provider type to Any.']);
    expect(screen.queryByRole('table')).toBeNull();
  });

  it('[UX-OVERHAUL 4] my notices: "No notices yet" and when a notice appears', async () => {
    await openNotices({ [NOTICES_ROUTE]: answer([]) });

    await screen.findByText('No notices yet');
    expect(lines(emptyState('No notices yet'))).toEqual(['No notices yet', 'A notice appears here when the programme sends you one, for example after a decision on your enrolment.']);
    expect(screen.queryByRole('table')).toBeNull();
  });

  it('[UX-OVERHAUL 4] portal home: the lists "My applications" and "My provider record" each have their own empty state', async () => {
    signedInAs('provider1');
    installApi({ ...defaultRoutes(), [PROVIDER_ROUTE]: NO_RECORD });
    renderApp('/portal');

    const applications = await screen.findByRole('region', { name: 'My applications' });
    await within(applications).findByText('No applications yet');
    expect(lines(emptyState('No applications yet'))).toEqual(['No applications yet', 'When you apply, the application is listed here with its status.']);
    const record = screen.getByRole('region', { name: 'My provider record' });
    await within(record).findByText('No provider record exists yet.');
    expect(lines(emptyState('No provider record exists yet.'))).toEqual(['No provider record exists yet.', 'Your own record shows here once your application has been accepted.']);
    expect(applications.contains(emptyState('No applications yet'))).toBe(true);
    expect(record.contains(emptyState('No provider record exists yet.'))).toBe(true);
  });

  it('[UX-OVERHAUL 4] my provider record: the line, the explanation, then two actions as buttons: Apply for enrolment (filled) and My enrolment (outline)', async () => {
    signedInAs('provider1');
    installApi({ ...defaultRoutes(), [PROVIDER_ROUTE]: NO_RECORD });
    renderApp('/portal/provider');

    await screen.findByText('No provider record exists yet.');
    const box = emptyState('No provider record exists yet.');
    expect(lines(box).slice(0, 2)).toEqual(['No provider record exists yet.', 'A record is created when an application is accepted.']);
    expect(Array.from(box.children[2]?.querySelectorAll('a') ?? []).map((link) => text(link))).toEqual(['Apply for enrolment', 'My enrolment']);
    const apply = within(box).getByRole('link', { name: 'Apply for enrolment' });
    const home = within(box).getByRole('link', { name: 'My enrolment' });
    expect(apply).toHaveAttribute('href', '/portal/apply');
    expect(apply).toHaveClass('usa-button');
    expect(apply).not.toHaveClass('usa-button--outline');
    expect(home).toHaveAttribute('href', '/portal');
    expect(home).toHaveClass('usa-button', 'usa-button--outline');
  });

  it('[UX-OVERHAUL 4] the action "Apply for enrolment" of the empty provider record opens the form', async () => {
    signedInAs('provider1');
    installApi({ ...defaultRoutes(), [PROVIDER_ROUTE]: NO_RECORD });
    const { user, path } = renderApp('/portal/provider');
    await screen.findByText('No provider record exists yet.');

    await user.click(within(emptyState('No provider record exists yet.')).getByRole('link', { name: 'Apply for enrolment' }));

    expect(await screen.findByRole('heading', { level: 1, name: 'Apply for enrolment' })).toBeInTheDocument();
    expect(path()).toBe('/portal/apply');
  });
});

describe('[UX-OVERHAUL 4] an empty state says "nothing yet", never "something went wrong" (D-024)', () => {
  it('[UX-OVERHAUL 4] a failed provider search is an error alert and no empty state', async () => {
    const { user } = await openSearch(searchRoutes([], { [PROVIDERS_ROUTE]: FAILURE }));

    await searchProviders(user, { npi: '1003000111' });

    expect(await screen.findByRole('alert')).toHaveTextContent('The search could not be completed. Try again.');
    expect(emptyStates()).toEqual([]);
  });

  it('[UX-OVERHAUL 4] a failed directory search is an error alert and no empty state', async () => {
    const { user } = await openDirectory({ 'GET /api/v1/directory/providers': FAILURE });

    await searchDirectory(user, 'RIVERA');

    expect(await screen.findByRole('alert')).toHaveTextContent('The directory is not available right now. Try again.');
    expect(emptyStates()).toEqual([]);
  });

  it('[UX-OVERHAUL 4] failed notices are an error alert with Retry and no empty state', async () => {
    await openNotices({ [NOTICES_ROUTE]: FAILURE });

    expect(await screen.findByRole('alert')).toHaveTextContent('The notices could not be loaded. Try again.');
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument();
    expect(emptyStates()).toEqual([]);
  });

  it('[UX-OVERHAUL 4] on the portal home a failed applications call and a failed record call are alerts and show no empty state', async () => {
    signedInAs('provider1');
    installApi({ ...defaultRoutes(), 'GET /api/v1/applications': FAILURE, [PROVIDER_ROUTE]: FAILURE });
    renderApp('/portal');

    const applications = await screen.findByRole('region', { name: 'My applications' });
    expect(await within(applications).findByRole('alert')).toHaveTextContent('The applications could not be loaded. Try again later.');
    expect(await within(screen.getByRole('region', { name: 'My provider record' })).findByRole('alert')).toHaveTextContent('The provider record could not be loaded. Try again later.');
    expect(emptyStates()).toEqual([]);
  });

  it('[UX-OVERHAUL 4] a failed provider record call is an error alert and no empty state', async () => {
    signedInAs('provider1');
    installApi({ ...defaultRoutes(), [PROVIDER_ROUTE]: FAILURE });
    renderApp('/portal/provider');

    expect(await screen.findByRole('alert')).toHaveTextContent('The provider record could not be loaded. Try again later.');
    expect(emptyStates()).toEqual([]);
  });

  it('[UX-OVERHAUL 4] a portal home with an application and a record shows the table and the record and no empty state', async () => {
    signedInAs('provider1');
    installApi({ ...defaultRoutes([application()]), [PROVIDER_ROUTE]: () => response(200, provider()) });
    renderApp('/portal');

    await screen.findByRole('table');
    await within(await screen.findByRole('region', { name: 'My provider record' })).findByText('1003000111');
    expect(emptyStates()).toEqual([]);
  });

  it('[UX-OVERHAUL 4] a notice in the list gives the table and no empty state', async () => {
    await openNotices({ [NOTICES_ROUTE]: answer([numbered()]) });

    await screen.findByRole('table');
    expect(emptyStates()).toEqual([]);
  });

  it('[UX-OVERHAUL 4] a directory search that finds a provider shows the table and no empty state', async () => {
    const { user } = await openDirectory(directoryRoutes([GROUP]));

    await searchDirectory(user, 'RIO');

    await screen.findByRole('table');
    expect(emptyStates()).toEqual([]);
  });
});
