// UX overhaul 6, summary lists in place of key-value tables (user decisions B-iv item 5 and Q-109, 2026-11-19): a record is shown as a
// USWDS-style description list inside a named section (the name of the section is the name of the list), one row per field: the term
// (dt) and its value (dd), no "Field | Value" header row. Real data tables (screening results, queues, notices, validation errors)
// stay tables. RecordSummary is the "at a glance" box above a record: the screening result, the risk tier (and the enrolment status
// where the page has no banner of its own) as words with codes, and the next revalidation as a sentence.
import { render, screen, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { RecordSummary } from '../../src/shared/ui/RecordSummary';
import { DefinitionList, SummaryList } from '../../src/shared/ui/SummaryList';
import { approvalWorld, openApproval, passedScreening, pendingPass, RIVERA_NPI } from '../support/approval';
import { openDetail, rivera } from '../support/detail';
import { response } from '../support/fakeApi';
import { application, APPLICATION_ID, provider, PROVIDER_NPI } from '../support/fixtures';
import { defaultRoutes, installApi, renderApp, setupShellWorld, signedInAs, text } from '../support/world';
import { listRows } from '../support/uxWorld';

vi.mock('oidc-client-ts', async (importOriginal) => {
  const actual = await importOriginal<typeof import('oidc-client-ts')>();
  const { FakeUserManager } = await import('../support/fakeOidc');
  return { ...actual, UserManager: FakeUserManager };
});

setupShellWorld();

describe('[UX-OVERHAUL 6] SummaryList and DefinitionList: a term and its value per row, no table', () => {
  it('[UX-OVERHAUL 6] the section is a region named by its title, and the title is not a heading (the headings of the page keep their meaning)', () => {
    render(<SummaryList title="Provider" rows={[['NPI', '1003000111']]} />);

    const region = screen.getByRole('region', { name: 'Provider' });
    expect(region.tagName).toBe('SECTION');
    expect(screen.queryByRole('heading')).toBeNull();
  });

  it('[UX-OVERHAUL 6] every row is a dt (the term) with its dd (the value), in the order given', () => {
    render(
      <SummaryList
        title="Provider"
        rows={[
          ['NPI', '1003000111'],
          ['Name', 'RIVERA MARIA'],
          ['Status', 'Active (A)'],
        ]}
      />,
    );

    expect(listRows(screen.getByRole('region', { name: 'Provider' }))).toEqual([
      ['NPI', '1003000111'],
      ['Name', 'RIVERA MARIA'],
      ['Status', 'Active (A)'],
    ]);
  });

  it('[UX-OVERHAUL 6] it is not a table: no table, no row, no header cell, no "Field" and no "Value" header', () => {
    render(<SummaryList title="Provider" rows={[['NPI', '1003000111']]} />);
    const region = within(screen.getByRole('region', { name: 'Provider' }));

    expect(region.queryByRole('table')).toBeNull();
    expect(region.queryAllByRole('row')).toHaveLength(0);
    expect(region.queryAllByRole('columnheader')).toHaveLength(0);
    expect(region.queryByText('Field')).toBeNull();
    expect(region.queryByText('Value')).toBeNull();
  });

  it('[UX-OVERHAUL 6] a value may be an element, not only text (a badge)', () => {
    render(<SummaryList title="Application" rows={[['Status', <span key="s" className="usa-tag">Validated</span>]]} />);

    const value = screen.getByRole('region', { name: 'Application' }).querySelector('dd');
    expect(value?.querySelector('.usa-tag')).toHaveTextContent('Validated');
  });

  it('[UX-OVERHAUL 6] two lists on one page are two regions with their own names and their own terms', () => {
    render(
      <>
        <SummaryList title="Provider" rows={[['NPI', '1003000111']]} />
        <SummaryList title="Provider record" rows={[['NPI', '1003000111'], ['Taxonomy', '207Q00000X']]} />
      </>,
    );

    const names = screen.getAllByRole('region').map((region) => region.getAttribute('aria-labelledby'));
    expect(new Set(names).size).toBe(2);
    expect(listRows(screen.getByRole('region', { name: 'Provider' }))).toHaveLength(1);
    expect(listRows(screen.getByRole('region', { name: 'Provider record' }))).toHaveLength(2);
  });

  it('[UX-OVERHAUL 6] DefinitionList is the list alone: a dl with the same rows and no region of its own', () => {
    const { container } = render(<DefinitionList rows={[['NPI', '1003000111'], ['Taxonomy', '207Q00000X']]} />);

    expect(screen.queryByRole('region')).toBeNull();
    expect(container.querySelectorAll('dl')).toHaveLength(1);
    expect(listRows(container)).toEqual([['NPI', '1003000111'], ['Taxonomy', '207Q00000X']]);
  });
});

describe('[UX-OVERHAUL 6] RecordSummary: the record at a glance, in words and codes', () => {
  const badges = (): string[] => Array.from(document.querySelectorAll('.usa-tag')).map((tag) => text(tag));

  it('[UX-OVERHAUL 6] with the status it shows three badges and the next revalidation as a sentence', () => {
    render(<RecordSummary provider={provider()} showStatus />);

    expect(badges()).toEqual(['Enrolment status: Active (A)', 'Screening result: Pass (P)', 'Risk tier: Limited (L)']);
    expect(screen.getByText('Next revalidation: 2 Mar 2031')).toBeInTheDocument();
  });

  it('[UX-OVERHAUL 6] without showStatus the enrolment status is left to the page banner: two badges', () => {
    render(<RecordSummary provider={provider()} />);

    expect(badges()).toEqual(['Screening result: Pass (P)', 'Risk tier: Limited (L)']);
  });

  it('[UX-OVERHAUL 6] a provider with no tier and no due date says so in words', () => {
    render(<RecordSummary provider={provider({ riskTier: null, revalidationDueDate: null })} showStatus />);

    expect(badges()).toContain('Risk tier: not assigned yet');
    expect(screen.getByText('Next revalidation: not set yet')).toBeInTheDocument();
  });

  it.each([
    ['U', 'Screening result: Unscreened (U)'],
    ['P', 'Screening result: Pass (P)'],
    ['F', 'Screening result: Fail (F)'],
  ] as const)('[UX-OVERHAUL 6] the screening result %s reads "%s" (never colour alone)', (screenFlag, badge) => {
    render(<RecordSummary provider={provider({ screenFlag })} />);

    expect(badges()).toContain(badge);
  });

  it.each([
    ['L', 'Risk tier: Limited (L)'],
    ['M', 'Risk tier: Moderate (M)'],
    ['H', 'Risk tier: High (H)'],
  ] as const)('[UX-OVERHAUL 6] the risk tier %s reads "%s"', (riskTier, badge) => {
    render(<RecordSummary provider={provider({ riskTier })} />);

    expect(badges()).toContain(badge);
  });

  it('[UX-OVERHAUL 6] it adds no heading and no table, so the headings and the record list of the page keep their meaning', () => {
    render(<RecordSummary provider={provider()} showStatus />);

    expect(screen.queryByRole('heading')).toBeNull();
    expect(screen.queryByRole('table')).toBeNull();
    expect(document.querySelector('.usa-summary-box')).not.toBeNull();
  });
});

describe('[UX-OVERHAUL 6] the lists on the pages', () => {
  it('[UX-OVERHAUL 6] my provider record: the summary box with the status comes before the list "Provider record"', async () => {
    signedInAs('provider1');
    installApi(defaultRoutes());
    renderApp('/portal/provider');

    const record = await screen.findByRole('region', { name: 'Provider record' });
    const box = document.querySelector('.usa-summary-box')!;
    expect(box.textContent).toContain('Enrolment status: Active (A)');
    expect(box.compareDocumentPosition(record) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it('[UX-OVERHAUL 6] the console detail: the summary box has no status badge because the banner "Status: Active (A)" says it', async () => {
    await openDetail(rivera());
    await screen.findByRole('heading', { level: 2, name: 'Status: Active (A)' });

    const boxes = Array.from(document.querySelectorAll('.usa-summary-box')).map((box) => text(box));
    expect(boxes.some((box) => box.includes('Screening result: Pass (P)') && box.includes('Risk tier: Limited (L)'))).toBe(true);
    expect(boxes.some((box) => box.includes('Enrolment status:'))).toBe(false);
  });

  it('[UX-OVERHAUL 6] the approval page: the list "Provider" has the five terms NPI, Name, Status, Screening result and Revalidation due', async () => {
    await openApproval('analyst1', approvalWorld(pendingPass(), passedScreening()), RIVERA_NPI);

    const region = await screen.findByRole('region', { name: 'Provider' });
    expect(region.querySelector('dl')).not.toBeNull();
    expect(listRows(region)).toEqual([
      ['NPI', '1003000111'],
      ['Name', 'RIVERA MARIA'],
      ['Status', 'Pending (P)'],
      ['Screening result', 'Pass (P)'],
      ['Revalidation due', 'Not set'],
    ]);
  });

  it('[UX-OVERHAUL 6] the approval page has one table only, the screening results; the record is a list, not a table', async () => {
    await openApproval('analyst1', approvalWorld(pendingPass(), passedScreening()), RIVERA_NPI);
    await screen.findByRole('region', { name: 'Provider' });

    expect(screen.getAllByRole('table').map((table) => table.querySelector('caption')?.textContent)).toEqual(['Screening results']);
  });

  it('[UX-OVERHAUL 6] the application status: the list "Application" starts with Status and Submitted, has no "Application" row and no header row', async () => {
    signedInAs('provider1');
    installApi({
      ...defaultRoutes(),
      [`GET /api/v1/applications/${APPLICATION_ID}`]: () =>
        response(200, application({ individual: { lastName: 'RIVERA', firstName: 'MARIA', licenseNo: 'MD12345', licenseState: 'TX' } })),
    });
    renderApp(`/portal/applications/${APPLICATION_ID}`);

    const region = await screen.findByRole('region', { name: 'Application' });
    const terms = listRows(region).map(([term]) => term);
    expect(terms).toEqual(['Status', 'Submitted', 'NPI', 'Medicaid ID', 'Provider type', 'Taxonomy', 'Last name', 'First name', 'Licence number', 'Licence state']);
    expect(within(region).queryAllByRole('columnheader')).toHaveLength(0);
    expect(screen.queryAllByRole('table')).toHaveLength(0);
  });

  it('[UX-OVERHAUL 6] a rejected application keeps its errors as a real table beside the list', async () => {
    signedInAs('provider1');
    installApi({
      ...defaultRoutes(),
      [`GET /api/v1/applications/${APPLICATION_ID}`]: () =>
        response(200, application({ status: 'REJECTED', taxonomy: '', errors: [{ code: 'E104', field: 'taxonomy', message: 'Taxonomy code is required' }] })),
    });
    renderApp(`/portal/applications/${APPLICATION_ID}`);

    await screen.findByRole('region', { name: 'Application' });
    expect(screen.getAllByRole('table').map((table) => table.querySelector('caption')?.textContent)).toEqual(['Validation errors']);
  });

  it('[UX-OVERHAUL 6] the portal home: the record in short is a list of six terms, followed by the link to the full record', async () => {
    signedInAs('provider1');
    installApi({ ...defaultRoutes(), [`GET /api/v1/providers/${PROVIDER_NPI}`]: () => response(200, provider()) });
    renderApp('/portal');

    const region = await screen.findByRole('region', { name: 'My provider record' });
    await within(region).findByText('1003000111');
    expect(listRows(region)).toEqual([
      ['NPI', '1003000111'],
      ['Medicaid ID', 'TX0000111'],
      ['Provider type', 'Individual (IN)'],
      ['Taxonomy', '207Q00000X'],
      ['Enrolment status', 'Active (A)'],
      ['Screening result', 'Pass (P)'],
    ]);
    expect(within(region).getByRole('link', { name: 'My provider record' })).toHaveAttribute('href', '/portal/provider');
    expect(within(region).queryByRole('table')).toBeNull();
  });
});
