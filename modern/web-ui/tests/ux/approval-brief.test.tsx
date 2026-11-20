// UX overhaul 8, the approval brief (user decisions B-iv items 2 and 4, 2026-11-19): the approval page opens with "In short" (who the
// provider is, the screening in one or two plain sentences, the risk tier with its reason), then the per-source screening results,
// then the decision, and the key-value "Provider" list after the form. Sources are named in plain words ("OIG exclusion list
// (LEIE)", never OIGLEIE), a failed check carries its error code in full ("E203 owner excluded"), and the detail of a source is shown
// in sentence case ("Not excluded", "Licence active until Sep 2028"). Nothing in the brief is new data: it restates the provider and
// its screening results.
import type { UseQueryResult } from '@tanstack/react-query';
import { render, screen, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { ApprovalBrief, riskSentence, screeningSentences, whoSentence } from '../../src/features/console/approval/ApprovalBrief';
import type { ScreeningPage } from '../../src/shared/api/types';
import {
  approvalWorld,
  approveRadio,
  CLINIC_NPI,
  failedScreening,
  HOSPICE_NPI,
  NO_SCREENINGS,
  openApproval,
  passedScreening,
  pendingFail,
  pendingPass,
  pendingUnscreened,
  RIVERA_NPI,
  screening,
  screeningPage,
  source,
  recordButton,
} from '../support/approval';
import { provider } from '../support/fixtures';
import { setupShellWorld, text } from '../support/world';
import { bodyTexts } from '../support/uxWorld';

vi.mock('oidc-client-ts', async (importOriginal) => {
  const actual = await importOriginal<typeof import('oidc-client-ts')>();
  const { FakeUserManager } = await import('../support/fakeOidc');
  return { ...actual, UserManager: FakeUserManager };
});

setupShellWorld();

const PASSED_LINE = 'All 4 checks passed: Licence board, OIG exclusion list (LEIE), SAM.gov debarment, SSA death records.';
const FAILED_LINES = ['1 of 4 checks failed: OIG exclusion list (LEIE): E203 owner excluded.', '3 passed: Licence board, SAM.gov debarment, SSA death records.'];

describe('[UX-OVERHAUL 8] the screening in plain sentences (screeningSentences)', () => {
  it('[UX-OVERHAUL 8] when every check passed it is one sentence that counts them and names the sources', () => {
    expect(screeningSentences(passedScreening())).toEqual([PASSED_LINE]);
  });

  it('[UX-OVERHAUL 8] one failed check: the failure first with its error code in full, then the checks that passed', () => {
    expect(screeningSentences(failedScreening())).toEqual(FAILED_LINES);
  });

  it('[UX-OVERHAUL 8] several failed checks are listed in one sentence separated by semicolons, with their meanings', () => {
    const page = screeningPage(
      screening(CLINIC_NPI, 'FAIL', [
        source('LICBOARD', 'F', 'LICENCE MD12345 TX EXPIRED 2025-01-31', 'E202'),
        source('OIGLEIE', 'F', 'EXCLUDED', 'E201'),
        source('SAMGOV', 'P', 'NOT DEBARRED'),
      ]),
    );

    expect(screeningSentences(page)).toEqual(['2 of 3 checks failed: Licence board: E202 licence expired; OIG exclusion list (LEIE): E201 excluded.', '1 passed: SAM.gov debarment.']);
  });

  it('[UX-OVERHAUL 8] a failed check without an error code is named by its source alone', () => {
    const page = screeningPage(screening(CLINIC_NPI, 'FAIL', [source('LICBOARD', 'F', 'LICENCE NOT FOUND'), source('SAMGOV', 'P', 'NOT DEBARRED')]));

    expect(screeningSentences(page)).toEqual(['1 of 2 checks failed: Licence board.', '1 passed: SAM.gov debarment.']);
  });

  it('[UX-OVERHAUL 8] when every check failed there is no sentence about passed checks', () => {
    const page = screeningPage(screening(CLINIC_NPI, 'FAIL', [source('SAMGOV', 'F', 'DEBARRED', 'E205'), source('SSADMF', 'F', 'DECEASED 2025-03-04', 'E204')]));

    expect(screeningSentences(page)).toEqual(['2 of 2 checks failed: SAM.gov debarment: E205 debarred; SSA death records: E204 deceased.']);
  });

  it.each([
    [1, '1 check is conditional: Licence board.', '3 passed: OIG exclusion list (LEIE), SAM.gov debarment, SSA death records.'],
    [2, '2 checks are conditional: Licence board, OIG exclusion list (LEIE).', '2 passed: SAM.gov debarment, SSA death records.'],
  ] as const)('[UX-OVERHAUL 8] %s conditional check(s) are named apart from the passed ones (singular "is", plural "are")', (count, conditional, passed) => {
    const results = [
      source('LICBOARD', 'C', 'COULD NOT BE REACHED'),
      source('OIGLEIE', count === 2 ? 'C' : 'P', count === 2 ? 'COULD NOT BE REACHED' : 'NOT EXCLUDED'),
      source('SAMGOV', 'P', 'NOT DEBARRED'),
      source('SSADMF', 'P', 'NOT DECEASED'),
    ];

    expect(screeningSentences(screeningPage(screening(RIVERA_NPI, 'PASS', results)))).toEqual([conditional, passed]);
  });

  it('[UX-OVERHAUL 8] a failed and a conditional check give three sentences: failed, conditional, passed', () => {
    const page = screeningPage(
      screening(CLINIC_NPI, 'FAIL', [source('OIGLEIE', 'F', 'OWNER 1990000111 EXCLUDED', 'E203'), source('LICBOARD', 'C', 'COULD NOT BE REACHED'), source('SAMGOV', 'P', 'NOT DEBARRED')]),
    );

    expect(screeningSentences(page)).toEqual(['1 of 3 checks failed: OIG exclusion list (LEIE): E203 owner excluded.', '1 check is conditional: Licence board.', '1 passed: SAM.gov debarment.']);
  });

  it('[UX-OVERHAUL 8] only the latest screening counts, whatever the order of the list', () => {
    const older = screening(CLINIC_NPI, 'FAIL', [source('SAMGOV', 'F', 'DEBARRED', 'E205'), source('SSADMF', 'P', 'NOT DECEASED')], { screeningId: '11111111-1111-4111-8111-111111111111', startedAt: '2026-03-01T09:00:00Z' });
    const newer = screening(CLINIC_NPI, 'PASS', [source('SAMGOV', 'P', 'NOT DEBARRED'), source('SSADMF', 'P', 'NOT DECEASED')], { screeningId: '22222222-2222-4222-8222-222222222222', startedAt: '2026-03-02T09:00:00Z' });

    expect(screeningSentences(screeningPage(older, newer))).toEqual(['All 2 checks passed: SAM.gov debarment, SSA death records.']);
    expect(screeningSentences(screeningPage(newer, older))).toEqual(['All 2 checks passed: SAM.gov debarment, SSA death records.']);
  });

  it('[UX-OVERHAUL 8] no screening yet, or a screening with no results, gives no sentence (nothing is claimed)', () => {
    expect(screeningSentences(NO_SCREENINGS)).toEqual([]);
    expect(screeningSentences(screeningPage(screening(RIVERA_NPI, 'PASS', [])))).toEqual([]);
  });
});

describe('[UX-OVERHAUL 8] the risk tier and who the provider is', () => {
  it.each([
    ['207Q00000X', 'L', 'Risk tier Limited (L): set from the first three characters of the taxonomy code (207Q00000X -> 207).'],
    ['341600000X', 'M', 'Risk tier Moderate (M): set from the first three characters of the taxonomy code (341600000X -> 341).'],
    ['251G00000X', 'H', 'Risk tier High (H): set from the first three characters of the taxonomy code (251G00000X -> 251).'],
  ] as const)('[UX-OVERHAUL 8] taxonomy %s with tier %s reads "%s"', (taxonomy, riskTier, sentence) => {
    expect(riskSentence(provider({ taxonomy, riskTier }))).toBe(sentence);
  });

  it('[UX-OVERHAUL 8] a provider without a tier says so and still explains where the tier will come from', () => {
    expect(riskSentence(provider({ taxonomy: '207Q00000X', riskTier: null }))).toBe(
      'No risk tier is assigned yet. It is set from the first three characters of the taxonomy code (207Q00000X -> 207).',
    );
  });

  it.each([
    [pendingPass(), 'RIVERA MARIA, NPI 1003000111, Pending (P)'],
    [pendingFail(), 'EXCLUDED CLINIC LLC, NPI 1003000444, Pending (P)'],
    [pendingPass({ enrollStatus: 'A' }), 'RIVERA MARIA, NPI 1003000111, Active (A)'],
    [pendingPass({ enrollStatus: 'S' }), 'RIVERA MARIA, NPI 1003000111, Suspended (S)'],
  ] as const)('[UX-OVERHAUL 8] the line about the provider is the name, the NPI in full and the status with its code: %#', (record, line) => {
    expect(whoSentence(record)).toBe(line);
  });

  it('[UX-OVERHAUL 8] a provider with no name is identified by NPI and status alone', () => {
    expect(whoSentence(provider({ individual: undefined, organization: undefined, enrollStatus: 'P' }))).toBe('NPI 1003000111, Pending (P)');
  });
});

describe('[UX-OVERHAUL 8] the ApprovalBrief box: "In short" first, then the provider line, the screening and the risk tier', () => {
  const fake = (status: 'success' | 'pending' | 'error', data?: ScreeningPage) => ({ status, data }) as unknown as UseQueryResult<ScreeningPage>;
  const paragraphs = (): string[] => Array.from(document.querySelectorAll('.usa-summary-box p')).map((line) => text(line));

  it('[UX-OVERHAUL 8] the paragraphs are "In short", the provider line, the screening sentence(s) and the risk sentence, in that order', () => {
    render(<ApprovalBrief provider={pendingPass()} screenings={fake('success', failedScreening())} />);

    expect(paragraphs()).toEqual([
      'In short',
      'RIVERA MARIA, NPI 1003000111, Pending (P)',
      ...FAILED_LINES,
      'No risk tier is assigned yet. It is set from the first three characters of the taxonomy code (207Q00000X -> 207).',
    ]);
  });

  it('[UX-OVERHAUL 8] "In short" and the provider line are in bold', () => {
    render(<ApprovalBrief provider={pendingPass()} screenings={fake('success', passedScreening())} />);

    const [label, who, screeningLine] = Array.from(document.querySelectorAll('.usa-summary-box p'));
    expect(label).toHaveClass('text-bold');
    expect(who).toHaveClass('text-bold');
    expect(screeningLine).not.toHaveClass('text-bold');
  });

  it.each(['pending', 'error'] as const)('[UX-OVERHAUL 8] while the screening is %s the box claims nothing about it: only the provider line and the risk sentence', (status) => {
    render(<ApprovalBrief provider={pendingPass()} screenings={fake(status)} />);

    expect(paragraphs()).toHaveLength(3);
    expect(paragraphs().slice(0, 2)).toEqual(['In short', 'RIVERA MARIA, NPI 1003000111, Pending (P)']);
    expect(paragraphs().join(' ')).not.toMatch(/checks/);
  });

  it('[UX-OVERHAUL 8] it is a summary box with no heading of its own', () => {
    const { container } = render(<ApprovalBrief provider={pendingPass()} screenings={fake('success', passedScreening())} />);

    expect(container.querySelector('.usa-summary-box')).not.toBeNull();
    expect(screen.queryByRole('heading')).toBeNull();
  });
});

describe('[UX-OVERHAUL 8] the approval page: In short, then the screening results, then the decision, then the provider list', () => {
  const before = (first: Element, second: Element): boolean => (first.compareDocumentPosition(second) & Node.DOCUMENT_POSITION_FOLLOWING) !== 0;

  it('[UX-OVERHAUL 8] with a passed screening the order in the page is: In short, the screening results, the decision, the "Provider" list, the link to the detail', async () => {
    await openApproval('analyst1', approvalWorld(pendingPass(), passedScreening()), RIVERA_NPI);
    const inShort = await screen.findByText('In short');
    const results = await screen.findByRole('table', { name: 'Screening results' });
    const decision = approveRadio();
    const button = recordButton();
    const provider = screen.getByRole('region', { name: 'Provider' });
    const detail = within(screen.getByRole('main')).getByRole('link', { name: 'Provider detail' });

    const order = [inShort, results, decision, button, provider, detail];
    for (let index = 1; index < order.length; index++) expect(before(order[index - 1]!, order[index]!)).toBe(true);
  });

  it('[UX-OVERHAUL 8] the brief sits after the introduction of the page and before everything else', async () => {
    await openApproval('analyst1', approvalWorld(pendingPass(), passedScreening()), RIVERA_NPI);
    const inShort = await screen.findByText('In short');

    const intro = screen.getByText(/^Read the summary and the screening results/);
    expect(before(intro, inShort)).toBe(true);
    expect(before(screen.getByRole('heading', { level: 1, name: 'Approve or deny' }), intro)).toBe(true);
  });

  it('[UX-OVERHAUL 8] the brief names the sources in plain words and the failing check with its meaning, on a failed screening', async () => {
    await openApproval('analyst1', approvalWorld(pendingFail(), failedScreening()), CLINIC_NPI);
    await screen.findByText('In short');

    const box = screen.getByText('In short').closest('.usa-summary-box')!;
    expect(text(box)).toContain('EXCLUDED CLINIC LLC, NPI 1003000444, Pending (P)');
    for (const line of FAILED_LINES) expect(text(box)).toContain(line);
  });

  it('[UX-OVERHAUL 8] no raw source code (LICBOARD, OIGLEIE, SAMGOV, SSADMF) is shown anywhere on the page', async () => {
    await openApproval('analyst1', approvalWorld(pendingFail(), failedScreening()), CLINIC_NPI);
    await screen.findByRole('table', { name: 'Screening results' });

    expect(text(screen.getByRole('main'))).not.toMatch(/LICBOARD|OIGLEIE|SAMGOV|SSADMF/);
  });

  it('[UX-OVERHAUL 8] the details of the sources are in sentence case with plain dates ("Licence active until Sep 2028", "Not excluded")', async () => {
    const world = approvalWorld(
      pendingPass(),
      screeningPage(
        screening(RIVERA_NPI, 'PASS', [
          source('LICBOARD', 'P', 'LICENCE MD12345 TX ACTIVE UNTIL 2028-09'),
          source('OIGLEIE', 'P', 'NOT EXCLUDED'),
          source('SAMGOV', 'P', 'NOT DEBARRED'),
          source('SSADMF', 'P', 'NOT DECEASED'),
        ]),
      ),
    );
    await openApproval('analyst1', world, RIVERA_NPI);

    const table = await screen.findByRole('table', { name: 'Screening results' });
    expect(bodyTexts(table)).toEqual([
      ['Licence board', 'Pass (P)', '2 Mar 2026', 'Licence active until Sep 2028', '-'],
      ['OIG exclusion list (LEIE)', 'Pass (P)', '2 Mar 2026', 'Not excluded', '-'],
      ['SAM.gov debarment', 'Pass (P)', '2 Mar 2026', 'Not debarred', '-'],
      ['SSA death records', 'Pass (P)', '2 Mar 2026', 'Not deceased', '-'],
    ]);
  });

  it('[UX-OVERHAUL 8] a failed source shows its detail in sentence case and its error code in the last column', async () => {
    const world = approvalWorld(
      pendingFail(),
      screeningPage(screening(CLINIC_NPI, 'FAIL', [source('OIGLEIE', 'F', 'OWNER 1990000111 EXCLUDED 1128A1 SINCE 2020-05-01', 'E203')])),
    );
    await openApproval('analyst1', world, CLINIC_NPI);

    const table = await screen.findByRole('table', { name: 'Screening results' });
    expect(bodyTexts(table)).toEqual([['OIG exclusion list (LEIE)', 'Fail (F)', '2 Mar 2026', 'Owner 1990000111 excluded 1128A1 since 1 May 2020', 'E203']]);
  });

  it('[UX-OVERHAUL 8] a provider whose screening has not finished: In short first, then the message instead of a decision form', async () => {
    await openApproval('analyst1', approvalWorld(pendingUnscreened(), NO_SCREENINGS), HOSPICE_NPI);

    const inShort = await screen.findByText('In short');
    const message = screen.getByText('Screening has not finished, so no decision can be recorded yet.');
    expect(before(inShort, message)).toBe(true);
    expect(screen.queryByRole('radio')).toBeNull();
    expect(text(inShort.closest('.usa-summary-box'))).toContain('SUNSET HOSPICE INC, NPI 1003000333, Pending (P)');
  });

  it('[UX-OVERHAUL 8] a provider that is already decided: In short first, then "No further decision can be recorded"', async () => {
    await openApproval('analyst1', approvalWorld(pendingPass({ enrollStatus: 'A', riskTier: 'L' }), passedScreening()), RIVERA_NPI);

    const inShort = await screen.findByText('In short');
    const message = screen.getByText(/No further decision can be recorded/);
    expect(before(inShort, message)).toBe(true);
    expect(within(inShort.closest('.usa-summary-box') as HTMLElement).getByText(/Risk tier Limited \(L\)/)).toBeInTheDocument();
  });
});
