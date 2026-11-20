// UX overhaul 3, sortable table headers (UX overhaul, B-i 2026-11-19): every list table sorts on the rows of its page when a column header
// is pressed: none -> ascending -> descending -> none (the order of the service again). The header holds a real button (keyboard:
// Tab, Enter, Space), keeps its visible name, tells assistive technology the state with aria-sort on the header cell, and sorts
// by the raw value (ISO dates, codes), not by the words shown. An empty value stays last whichever way the column is sorted, and a
// tie keeps the order of the service. Sorting never asks the service again (paging stays with the service).
// First the hook and the header on a small table, then every table of the application: which columns sort and one real sort per kind.
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { SortHeader, useSorting } from '../../src/shared/ui/sorting';
import { approvalWorld, openApproval, passedScreening, pendingPass, RIVERA_NPI } from '../support/approval';
import { openDirectory, directoryRoutes, GROUP, HOSPICE, RIVERA as RIVERA_ENTRY, resultRows, resultsTable as directoryTable, search as searchDirectory } from '../support/directory';
import { response } from '../support/fakeApi';
import { application, provider, type Application } from '../support/fixtures';
import { batchRoutes, ENDPOINTS, newRun, openJobs, recentRuns, runRows, runsPage } from '../support/batch';
import { answer, noticeRows, noticesTable, NOTICES_ROUTE, numbered, openNotices } from '../support/notices';
import { fullPanels, panelRoutes } from '../support/panels';
import { emptyQueues, findQueueSection, fullQueues, openQueues, providerPage, queueRoutes } from '../support/queues';
import { openSearch, resultsTable as searchTable, search as searchProviders, searchRoutes } from '../support/search';
import { api, defaultRoutes, findApplicationsTable, installApi, renderApp, setupShellWorld, signedInAs } from '../support/world';
import { findPanel, openDetail, rivera } from '../support/detail';
import { bodyTexts, headerOf, sortableColumns, sortButton } from '../support/uxWorld';

vi.mock('oidc-client-ts', async (importOriginal) => {
  const actual = await importOriginal<typeof import('oidc-client-ts')>();
  const { FakeUserManager } = await import('../support/fakeOidc');
  return { ...actual, UserManager: FakeUserManager };
});

setupShellWorld();

// ---- the hook and the header on a small table -------------------------------------------------------------------------------------

interface Row {
  id: string;
  name: string | null | undefined;
  count: number | null;
}

const ACCESSORS = { name: (row: Row) => row.name, count: (row: Row) => row.count };

function Harness({ rows }: { rows: readonly Row[] }) {
  const sorting = useSorting(rows, ACCESSORS);
  return (
    <table>
      <caption>Rows</caption>
      <thead>
        <tr>
          <SortHeader column="name" label="Name" sorting={sorting} />
          <SortHeader column="count" label="Count" sorting={sorting} />
          <th scope="col">Plain</th>
        </tr>
      </thead>
      <tbody>
        {sorting.rows.map((row) => (
          <tr key={row.id}>
            <td>{row.name ?? ''}</td>
            <td>{row.count ?? ''}</td>
            <td>{row.id}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

const SERVICE_ORDER: Row[] = [
  { id: 'r1', name: 'Mango', count: 10 },
  { id: 'r2', name: 'apple', count: 2 },
  { id: 'r3', name: 'Zebra', count: 33 },
  { id: 'r4', name: 'Banana', count: 1 },
];

const ids = (): string[] => within(screen.getByRole('table')).getAllByRole('row').slice(1).map((row) => row.lastElementChild?.textContent ?? '');
const nameHeader = (): HTMLElement => screen.getByRole('columnheader', { name: 'Name' });
const countHeader = (): HTMLElement => screen.getByRole('columnheader', { name: 'Count' });
const press = (name: string) => userEvent.click(screen.getByRole('button', { name }));

describe('[UX-OVERHAUL 3] the sort header and the sort order (UX overhaul: sortable table headers)', () => {
  it('[UX-OVERHAUL 3] nothing is sorted before a header is pressed: no header has aria-sort and the rows keep the order of the service', () => {
    render(<Harness rows={SERVICE_ORDER} />);

    expect(ids()).toEqual(['r1', 'r2', 'r3', 'r4']);
    expect(nameHeader()).not.toHaveAttribute('aria-sort');
    expect(countHeader()).not.toHaveAttribute('aria-sort');
  });

  it('[UX-OVERHAUL 3] the first press sorts ascending: aria-sort="ascending" on that header and the rows run from A to Z without regard to case', async () => {
    render(<Harness rows={SERVICE_ORDER} />);

    await press('Name');

    expect(nameHeader()).toHaveAttribute('aria-sort', 'ascending');
    expect(ids()).toEqual(['r2', 'r4', 'r1', 'r3']);
  });

  it('[UX-OVERHAUL 3] the second press sorts descending', async () => {
    render(<Harness rows={SERVICE_ORDER} />);

    await press('Name');
    await press('Name');

    expect(nameHeader()).toHaveAttribute('aria-sort', 'descending');
    expect(ids()).toEqual(['r3', 'r1', 'r4', 'r2']);
  });

  it('[UX-OVERHAUL 3] the third press goes back to the order of the service and takes aria-sort away', async () => {
    render(<Harness rows={SERVICE_ORDER} />);

    await press('Name');
    await press('Name');
    await press('Name');

    expect(ids()).toEqual(['r1', 'r2', 'r3', 'r4']);
    expect(nameHeader()).not.toHaveAttribute('aria-sort');
  });

  it('[UX-OVERHAUL 3] pressing another column moves the sort: the first column loses aria-sort and the new one starts ascending', async () => {
    render(<Harness rows={SERVICE_ORDER} />);
    await press('Name');
    await press('Name');

    await press('Count');

    expect(nameHeader()).not.toHaveAttribute('aria-sort');
    expect(countHeader()).toHaveAttribute('aria-sort', 'ascending');
    expect(ids()).toEqual(['r4', 'r2', 'r1', 'r3']);
  });

  it('[UX-OVERHAUL 3] numbers are compared as numbers (2 before 10 before 33), not as text', async () => {
    render(<Harness rows={SERVICE_ORDER} />);

    await press('Count');
    expect(ids()).toEqual(['r4', 'r2', 'r1', 'r3']);
    await press('Count');
    expect(ids()).toEqual(['r3', 'r1', 'r2', 'r4']);
  });

  it('[UX-OVERHAUL 3] text with numbers inside is compared naturally: "b2" before "B10"', async () => {
    const rows: Row[] = [
      { id: 'x1', name: 'B10', count: 1 },
      { id: 'x2', name: 'b2', count: 2 },
      { id: 'x3', name: 'a1', count: 3 },
    ];
    render(<Harness rows={rows} />);

    await press('Name');

    expect(ids()).toEqual(['x3', 'x2', 'x1']);
  });

  it('[UX-OVERHAUL 3] an empty value (null, undefined or "") stays last whichever way the column is sorted', async () => {
    const rows: Row[] = [
      { id: 'e1', name: null, count: 5 },
      { id: 'e2', name: 'Kiwi', count: null },
      { id: 'e3', name: undefined, count: 1 },
      { id: 'e4', name: '', count: 9 },
      { id: 'e5', name: 'Fig', count: 7 },
    ];
    render(<Harness rows={rows} />);

    await press('Name');
    expect(ids()).toEqual(['e5', 'e2', 'e1', 'e3', 'e4']);
    await press('Name');
    expect(ids()).toEqual(['e2', 'e5', 'e1', 'e3', 'e4']);
    await press('Count');
    expect(ids().at(-1)).toBe('e2');
    await press('Count');
    expect(ids().at(-1)).toBe('e2');
  });

  it('[UX-OVERHAUL 3] rows with equal values keep the order of the service, ascending and descending', async () => {
    const rows: Row[] = [
      { id: 't1', name: 'Same', count: 1 },
      { id: 't2', name: 'Other', count: 1 },
      { id: 't3', name: 'Same', count: 1 },
      { id: 't4', name: 'Same', count: 1 },
    ];
    render(<Harness rows={rows} />);

    await press('Count');
    expect(ids()).toEqual(['t1', 't2', 't3', 't4']);
    await press('Count');
    expect(ids()).toEqual(['t1', 't2', 't3', 't4']);
    await press('Name');
    expect(ids()).toEqual(['t2', 't1', 't3', 't4']);
    await press('Name');
    expect(ids()).toEqual(['t1', 't3', 't4', 't2']);
  });

  it('[UX-OVERHAUL 3] the list that is passed in is not reordered', async () => {
    const given = [...SERVICE_ORDER];
    render(<Harness rows={given} />);

    await press('Name');

    expect(given.map((row) => row.id)).toEqual(['r1', 'r2', 'r3', 'r4']);
  });

  it('[UX-OVERHAUL 3] when the table gets new rows (a new page) the chosen sort still applies to them', async () => {
    const view = render(<Harness rows={SERVICE_ORDER} />);
    await press('Name');
    const next: Row[] = [
      { id: 'n1', name: 'Plum', count: 1 },
      { id: 'n2', name: 'Cherry', count: 2 },
    ];

    view.rerender(<Harness rows={next} />);

    expect(nameHeader()).toHaveAttribute('aria-sort', 'ascending');
    expect(ids()).toEqual(['n2', 'n1']);
  });

  it('[UX-OVERHAUL 3] the header holds a real button that keeps the visible label as its name (the arrow is hidden from assistive technology)', () => {
    render(<Harness rows={SERVICE_ORDER} />);

    const button = within(nameHeader()).getByRole('button', { name: 'Name' });
    expect(button).toHaveAttribute('type', 'button');
    expect(button.querySelector('svg')).toHaveAttribute('aria-hidden', 'true');
    expect(within(screen.getByRole('table')).getAllByRole('button').map((element) => element.textContent)).toEqual(['Name', 'Count']);
  });

  it('[UX-OVERHAUL 3] a plain header has no button and is not sortable', () => {
    render(<Harness rows={SERVICE_ORDER} />);

    expect(within(screen.getByRole('columnheader', { name: 'Plain' })).queryByRole('button')).toBeNull();
  });

  it('[UX-OVERHAUL 3] the arrow shows the direction: both arrows while unsorted, the up arrow when ascending, the down arrow when descending', async () => {
    render(<Harness rows={SERVICE_ORDER} />);
    const paths = () => nameHeader().querySelectorAll('svg path').length;

    expect(paths()).toBe(2);
    await press('Name');
    expect(paths()).toBe(1);
    expect(nameHeader().querySelector('svg path')?.getAttribute('d')).toBe('M6 1 10 5H2z');
    await press('Name');
    expect(paths()).toBe(1);
    expect(nameHeader().querySelector('svg path')?.getAttribute('d')).toBe('M6 11 2 7h8z');
  });

  it('[UX-OVERHAUL 3] the keyboard works: Tab reaches the button, Enter sorts ascending and Space sorts descending', async () => {
    const user = userEvent.setup();
    render(<Harness rows={SERVICE_ORDER} />);

    await user.tab();
    expect(screen.getByRole('button', { name: 'Name' })).toHaveFocus();
    await user.keyboard('{Enter}');
    expect(nameHeader()).toHaveAttribute('aria-sort', 'ascending');
    expect(ids()).toEqual(['r2', 'r4', 'r1', 'r3']);
    await user.keyboard(' ');
    expect(nameHeader()).toHaveAttribute('aria-sort', 'descending');
    expect(ids()).toEqual(['r3', 'r1', 'r4', 'r2']);
    await user.tab();
    expect(screen.getByRole('button', { name: 'Count' })).toHaveFocus();
  });
});

// ---- every table of the application ---------------------------------------------------------------------------------------------

const ADAMS = provider({ npi: '1003000202', medicaidId: 'TX0000202', enrollStatus: 'P', riskTier: null, individual: { lastName: 'ADAMS', firstName: 'ALAN', licenseNo: 'TX1', licenseState: 'TX' } });
const MORENO = provider({ npi: '1003000201', medicaidId: 'TX0000201', enrollStatus: 'A', riskTier: 'M', individual: { lastName: 'MORENO', firstName: 'ANA', licenseNo: 'TX2', licenseState: 'TX' } });
const ZIMMER = provider({ npi: '1003000203', medicaidId: 'TX0000203', enrollStatus: 'A', riskTier: 'H', individual: { lastName: 'ZIMMER', firstName: 'AMY', licenseNo: 'TX3', licenseState: 'TX' } });
const firstColumn = (rows: string[][]): string[] => rows.map((row) => row[0] ?? '');

describe('[UX-OVERHAUL 3] provider search (console): sort on the rows of the page, no new request', () => {
  async function openResults() {
    const rendered = await openSearch(searchRoutes([MORENO, ADAMS, ZIMMER]));
    await searchProviders(rendered.user, { name: 'A' });
    await waitFor(() => expect(searchTable()).not.toBeNull());
    return { ...rendered, table: searchTable()! };
  }

  it('[UX-OVERHAUL 3] the columns NPI, Name, Type, Status and Risk sort', async () => {
    const { table } = await openResults();

    expect(sortableColumns(table)).toEqual(['NPI', 'Name', 'Type', 'Status', 'Risk']);
  });

  it('[UX-OVERHAUL 3] Name: ascending ADAMS, MORENO, ZIMMER; descending the reverse; the third press restores the order of the service; one search request only', async () => {
    const { table } = await openResults();
    const npis = () => firstColumn(bodyTexts(table));

    expect(npis()).toEqual(['1003000201', '1003000202', '1003000203']);
    await userEvent.click(sortButton(table, 'Name'));
    expect(headerOf(table, 'Name')).toHaveAttribute('aria-sort', 'ascending');
    expect(npis()).toEqual(['1003000202', '1003000201', '1003000203']);
    await userEvent.click(sortButton(table, 'Name'));
    expect(npis()).toEqual(['1003000203', '1003000201', '1003000202']);
    await userEvent.click(sortButton(table, 'Name'));
    expect(npis()).toEqual(['1003000201', '1003000202', '1003000203']);
    expect(api.to('GET', '/api/v1/providers')).toHaveLength(1);
  });

  it('[UX-OVERHAUL 3] Risk sorts by the code and a provider with no tier stays last both ways', async () => {
    const { table } = await openResults();
    const npis = () => firstColumn(bodyTexts(table));

    await userEvent.click(sortButton(table, 'Risk'));
    expect(npis()).toEqual(['1003000203', '1003000201', '1003000202']);
    await userEvent.click(sortButton(table, 'Risk'));
    expect(npis()).toEqual(['1003000201', '1003000203', '1003000202']);
  });
});

describe('[UX-OVERHAUL 3] public directory', () => {
  async function openResults() {
    const rendered = await openDirectory(directoryRoutes([RIVERA_ENTRY, HOSPICE, GROUP]));
    await searchDirectory(rendered.user, '');
    await waitFor(() => expect(directoryTable()).not.toBeNull());
    return { ...rendered, table: directoryTable()! };
  }

  it('[UX-OVERHAUL 3] Name, NPI, Type and Specialty (taxonomy) sort', async () => {
    const { table } = await openResults();

    expect(sortableColumns(table)).toEqual(['Name', 'NPI', 'Type', 'Specialty (taxonomy)']);
  });

  it('[UX-OVERHAUL 3] Name ascending is MARIA RIVERA, RIO GRANDE ..., SUNSET HOSPICE INC; descending the reverse', async () => {
    const { table } = await openResults();

    await userEvent.click(sortButton(table, 'Name'));
    expect(firstColumn(resultRows())).toEqual(['MARIA RIVERA', 'RIO GRANDE FAMILY MEDICINE PLLC', 'SUNSET HOSPICE INC']);
    await userEvent.click(sortButton(table, 'Name'));
    expect(firstColumn(resultRows())).toEqual(['SUNSET HOSPICE INC', 'RIO GRANDE FAMILY MEDICINE PLLC', 'MARIA RIVERA']);
  });

  it('[UX-OVERHAUL 3] NPI ascending is 1003000111, 1003000222, 1003000333', async () => {
    const { table } = await openResults();

    await userEvent.click(sortButton(table, 'NPI'));

    expect(resultRows().map((row) => row[1])).toEqual(['1003000111', '1003000222', '1003000333']);
  });
});

describe('[UX-OVERHAUL 3] portal home: My applications', () => {
  const at = (id: string, submittedAt: string, status: Application['status']) => application({ applicationId: `00000000-0000-4000-8000-00000000000${id}`, submittedAt, status });
  const APPLICATIONS = [at('1', '2026-03-05T10:00:00Z', 'VALIDATED'), at('2', '2026-02-28T09:00:00Z', 'REJECTED'), at('3', '2026-03-20T08:00:00Z', 'SUBMITTED')];

  async function openTable() {
    signedInAs('provider1');
    installApi(defaultRoutes(APPLICATIONS));
    renderApp('/portal');
    return findApplicationsTable();
  }

  it('[UX-OVERHAUL 3] Submitted, NPI and Status sort; the Action column does not', async () => {
    const table = await openTable();

    expect(sortableColumns(table)).toEqual(['Submitted', 'NPI', 'Status']);
    expect(within(headerOf(table, 'Action')).queryByRole('button')).toBeNull();
  });

  it('[UX-OVERHAUL 3] Submitted sorts by the real date, not by the words: 28 Feb 2026 before 5 Mar 2026 before 20 Mar 2026', async () => {
    const table = await openTable();

    await userEvent.click(sortButton(table, 'Submitted'));
    expect(firstColumn(bodyTexts(table))).toEqual(['28 Feb 2026', '5 Mar 2026', '20 Mar 2026']);
    await userEvent.click(sortButton(table, 'Submitted'));
    expect(firstColumn(bodyTexts(table))).toEqual(['20 Mar 2026', '5 Mar 2026', '28 Feb 2026']);
  });

  it('[UX-OVERHAUL 3] Status sorts by the word: Rejected, Submitted, Validated', async () => {
    const table = await openTable();

    await userEvent.click(sortButton(table, 'Status'));

    expect(bodyTexts(table).map((row) => row[2])).toEqual(['Rejected', 'Submitted', 'Validated']);
  });
});

describe('[UX-OVERHAUL 3] portal notices', () => {
  const NOTICES = [
    numbered({ template: 'TRM', sentAt: '2026-03-05T10:00:00Z' }),
    numbered({ template: 'APPROVED', sentAt: '2026-03-20T10:00:00Z' }),
    numbered({ template: 'DENIED', sentAt: '2026-02-28T10:00:00Z', status: 'FAILED' }),
  ];

  async function openTable() {
    await openNotices({ [NOTICES_ROUTE]: answer(NOTICES) });
    await waitFor(() => expect(noticesTable()).not.toBeNull());
    return noticesTable()!;
  }

  it('[UX-OVERHAUL 3] Sent, Notice, Channel and Status sort', async () => {
    expect(sortableColumns(await openTable())).toEqual(['Sent', 'Notice', 'Channel', 'Status']);
  });

  it('[UX-OVERHAUL 3] Sent ascending is 28 Feb 2026, 5 Mar 2026, 20 Mar 2026 (real dates)', async () => {
    const table = await openTable();

    await userEvent.click(sortButton(table, 'Sent'));

    expect(noticeRows().map((row) => row[0])).toEqual(['28 Feb 2026', '5 Mar 2026', '20 Mar 2026']);
  });

  it('[UX-OVERHAUL 3] Notice sorts by the plain name: Enrolment approved, Enrolment denied, Termination notice', async () => {
    const table = await openTable();

    await userEvent.click(sortButton(table, 'Notice'));

    expect(noticeRows().map((row) => row[1])).toEqual(['Enrolment approved (APPROVED)', 'Enrolment denied (DENIED)', 'Termination notice (TRM)']);
  });
});

describe('[UX-OVERHAUL 3] work queues: four tables, each with its own sortable columns', () => {
  const SECOND = pendingPass({ npi: '1003000202', individual: { lastName: 'ADAMS', firstName: 'ALAN', licenseNo: 'TX1', licenseState: 'TX' } });

  it.each([
    ['Pending approval', ['NPI', 'Name', 'Screening result'], 'Action'],
    ['Failed screening', ['NPI', 'Name', 'Failing source'], undefined],
    ['Revalidation due', ['NPI', 'Due', 'Case'], undefined],
    ['Rejected applications', ['NPI', 'Submitted', 'Errors', 'Source'], undefined],
  ] as Array<[string, string[], string | undefined]>)('[UX-OVERHAUL 3] %s: the columns %j sort', async (title, columns, plain) => {
    await openQueues('analyst1', queueRoutes(fullQueues()));
    const section = await findQueueSection(title, /1003000/);

    const table = within(section).getByRole('table');
    expect(sortableColumns(table)).toEqual(columns);
    if (plain) expect(within(headerOf(table, plain)).queryByRole('button')).toBeNull();
  });

  it('[UX-OVERHAUL 3] Pending approval: Name ascending lists ADAMS before RIVERA, NPI ascending lists 1003000111 first; the provider queue is not asked again', async () => {
    await openQueues('analyst1', queueRoutes({ ...emptyQueues(), pending: providerPage([pendingPass(), SECOND]) }));
    const section = await findQueueSection('Pending approval', /1003000202/);
    const table = within(section).getByRole('table');
    const calls = api.to('GET', '/api/v1/providers').length;

    await userEvent.click(sortButton(table, 'Name'));
    expect(firstColumn(bodyTexts(table))).toEqual(['1003000202', '1003000111']);
    await userEvent.click(sortButton(table, 'NPI'));
    expect(firstColumn(bodyTexts(table))).toEqual(['1003000111', '1003000202']);
    expect(headerOf(table, 'Name')).not.toHaveAttribute('aria-sort');
    expect(api.to('GET', '/api/v1/providers')).toHaveLength(calls);
  });

  it('[UX-OVERHAUL 3] Revalidation due: Due sorts by the real date, so 28 Feb 2026 comes before 5 Mar 2026', async () => {
    const later = { caseId: '3b1f6c52-8d0e-4a77-b0a4-6f2d9c1e5a11', npi: '1003000223', dueDate: '2026-03-05', status: 'DUE' as const, openedAt: '2026-03-01T00:00:00Z' };
    const earlier = { caseId: '3b1f6c52-8d0e-4a77-b0a4-6f2d9c1e5a10', npi: '1003000222', dueDate: '2026-02-28', status: 'DUE' as const, openedAt: '2026-03-01T00:00:00Z' };
    await openQueues('analyst1', queueRoutes({ ...emptyQueues(), due: { items: [later, earlier], page: 0, size: 20, total: 2 } }));
    const section = await findQueueSection('Revalidation due', /1003000223/);
    const table = within(section).getByRole('table');

    await userEvent.click(sortButton(table, 'Due'));

    expect(bodyTexts(table).map((row) => row[1])).toEqual(['28 Feb 2026', '5 Mar 2026']);
  });
});

describe('[UX-OVERHAUL 3] batch jobs: Recent runs', () => {
  const MARCH = newRun({ businessDate: '2026-03-05', status: 'FAILED' });
  const FEBRUARY = newRun({ businessDate: '2026-02-28', status: 'COMPLETED' });
  const OPEN = newRun({ businessDate: '2026-03-20', status: 'RUNNING' });

  async function openTable() {
    await openJobs(batchRoutes({ [`GET ${ENDPOINTS.tmsis}`]: () => response(200, runsPage([MARCH, FEBRUARY, OPEN])) }), 'admin1');
    await waitFor(() => expect(runRows()).toHaveLength(3));
    return recentRuns();
  }

  it('[UX-OVERHAUL 3] Job, Business date and Status sort; Counts, Error and File do not', async () => {
    const table = await openTable();

    expect(sortableColumns(table)).toEqual(['Job', 'Business date', 'Status']);
    for (const plain of ['Counts', 'Error', 'File']) expect(within(headerOf(table, plain)).queryByRole('button')).toBeNull();
  });

  it('[UX-OVERHAUL 3] Business date sorts by the real date: 28 Feb 2026, 5 Mar 2026, 20 Mar 2026', async () => {
    const table = await openTable();

    await userEvent.click(sortButton(table, 'Business date'));

    expect(runRows().map((row) => row[1])).toEqual(['28 Feb 2026', '5 Mar 2026', '20 Mar 2026']);
  });

  it('[UX-OVERHAUL 3] Status sorts by the word: Completed, Failed, Running', async () => {
    const table = await openTable();

    await userEvent.click(sortButton(table, 'Status'));

    expect(runRows().map((row) => row[2])).toEqual(['Completed', 'Failed', 'Running']);
  });
});

describe('[UX-OVERHAUL 3] provider detail panels and the sanction history', () => {
  it.each([
    ['Sanctions', ['Effective date', 'Action', 'Reason', 'By']],
    ['Revalidation', ['Due date', 'Case', 'Opened', 'Closed']],
    ['Notices', ['Sent', 'Notice', 'Status']],
    ['Audit', ['When', 'Who', 'Action']],
  ] as Array<[string, string[]]>)('[UX-OVERHAUL 3] the panel %s: the columns %j sort', async (name, columns) => {
    await openDetail(rivera(), panelRoutes('1003000111', fullPanels()), 'supervisor1');
    const panel = await findPanel(name);
    await waitFor(() => expect(panel.querySelectorAll('tbody tr').length).toBeGreaterThan(0));

    expect(sortableColumns(within(panel).getByRole('table'))).toEqual(columns);
  });

  it('[UX-OVERHAUL 3] the panel Sanctions: Effective date ascending lists 10 Mar 2026 before 20 Mar 2026', async () => {
    await openDetail(rivera(), panelRoutes('1003000111', fullPanels()), 'supervisor1');
    const panel = await findPanel('Sanctions');
    await waitFor(() => expect(panel.querySelectorAll('tbody tr')).toHaveLength(2));
    const table = within(panel).getByRole('table');

    expect(firstColumn(bodyTexts(table))).toEqual(['20 Mar 2026', '10 Mar 2026']);
    await userEvent.click(sortButton(table, 'Effective date'));
    expect(firstColumn(bodyTexts(table))).toEqual(['10 Mar 2026', '20 Mar 2026']);
  });
});

describe('[UX-OVERHAUL 3] tables that are not sortable: results of one record, not lists of many', () => {
  it('[UX-OVERHAUL 3] the screening results of the approval page have plain headers', async () => {
    await openApproval('analyst1', approvalWorld(pendingPass(), passedScreening()), RIVERA_NPI);
    const table = await screen.findByRole('table', { name: 'Screening results' });

    expect(within(table).getAllByRole('columnheader').length).toBeGreaterThan(0);
    expect(sortableColumns(table)).toEqual([]);
  });

  it('[UX-OVERHAUL 3] the screening panel of the provider detail has plain headers', async () => {
    await openDetail(rivera(), panelRoutes('1003000111', fullPanels()));
    const panel = await findPanel('Screening');
    await waitFor(() => expect(panel.querySelectorAll('tbody tr').length).toBeGreaterThan(0));

    expect(sortableColumns(within(panel).getByRole('table'))).toEqual([]);
  });
});
