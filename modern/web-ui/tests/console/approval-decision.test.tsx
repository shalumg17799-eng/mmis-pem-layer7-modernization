// MAM-87 AC-2, AC-3, AC-5 and the 403 of AC-6: recording a decision on the approval page (D-019, D-003, D-004, D-024).
// provider-service is replaced by the recording fetch stub; the page must show what the service returns and never claim a
// decision that the service did not record.
import { screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { response, type Handler } from '../support/fakeApi';
import {
  approvalWorld,
  approveRadio,
  CLINIC_NPI,
  decided,
  decisions,
  denyRadio,
  failedScreening,
  openApproval,
  passedScreening,
  pendingFail,
  pendingPass,
  reasonBox,
  recordButton,
  RIVERA_NPI,
  sentDecision,
} from '../support/approval';
import { api, fieldValue, setupShellWorld, text } from '../support/world';

vi.mock('oidc-client-ts', async (importOriginal) => {
  const actual = await importOriginal<typeof import('oidc-client-ts')>();
  const { FakeUserManager } = await import('../support/fakeOidc');
  return { ...actual, UserManager: FakeUserManager };
});

setupShellWorld();

const REASON = 'Owner is on the OIG exclusion list';

/** Open the page of 1003000111 (Pending, Pass) with the service answering the decision as `decision` (default: as the contract says). */
async function openRivera(decision?: Handler, who: 'analyst1' | 'supervisor1' = 'analyst1') {
  const world = approvalWorld(pendingPass(), passedScreening(), decision);
  const rendered = await openApproval(who, world, RIVERA_NPI);
  await screen.findByText('RIVERA MARIA');
  return { ...rendered, world };
}

/** Open the page of 1003000444 (Pending, Fail on OIGLEIE with E203). */
async function openClinic(decision?: Handler) {
  const world = approvalWorld(pendingFail(), failedScreening(), decision);
  const rendered = await openApproval('analyst1', world, CLINIC_NPI);
  await screen.findByText('EXCLUDED CLINIC LLC');
  return { ...rendered, world };
}

const main = () => within(screen.getByRole('main'));

describe('[MAM-87 AC-2] Approve makes the provider Active (journey J1)', () => {
  it('[MAM-87 AC-2] "Approve (becomes Active)" and "Record decision" send POST /api/v1/providers/1003000111/approval with decision APPROVE', async () => {
    const { user } = await openRivera();

    await user.click(screen.getByRole('radio', { name: 'Approve (becomes Active)' }));
    await user.click(recordButton());

    await waitFor(() => expect(decisions(RIVERA_NPI)).toHaveLength(1));
    expect(sentDecision(decisions(RIVERA_NPI)[0])).toEqual({ decision: 'APPROVE' });
    const call = decisions(RIVERA_NPI)[0];
    expect(call?.url.origin).toBe('http://localhost:8080');
    expect(call?.headers.get('content-type')).toMatch(/application\/json/);
    expect(call?.headers.get('authorization')).toMatch(/^Bearer \S+/);
  });

  it('[MAM-87 AC-2] the page confirms Active (A) and the revalidation due date five years later: approval on 5 Mar 2026 shows 5 Mar 2031', async () => {
    const { user } = await openRivera();

    await user.click(approveRadio());
    await user.click(recordButton());

    const confirmation = await screen.findByText(/Decision recorded/);
    expect(text(confirmation)).toContain('Active (A)');
    expect(text(confirmation)).toContain('5 Mar 2031');
    expect(fieldValue('Status')).toBe('Active (A)');
    expect(fieldValue('Revalidation due')).toBe('5 Mar 2031');
  });

  it('[MAM-87 AC-2] the due date shown is the one the service returned, not a date the page computed', async () => {
    const answer: Handler = () => response(200, { ...decided(pendingPass(), 'APPROVE'), revalidationDueDate: '2031-04-09' });
    const { user } = await openRivera(answer);

    await user.click(approveRadio());
    await user.click(recordButton());

    await screen.findByText(/Decision recorded/);
    expect(fieldValue('Revalidation due')).toBe('9 Apr 2031');
  });

  it('[MAM-87 AC-2] before the decision the due date reads "Not set" and after it no further decision is offered', async () => {
    const { user } = await openRivera();
    expect(fieldValue('Revalidation due')).toBe('Not set');

    await user.click(approveRadio());
    await user.click(recordButton());

    await screen.findByText(/Decision recorded/);
    expect(screen.queryByRole('radio')).toBeNull();
    expect(screen.queryByRole('button', { name: 'Record decision' })).toBeNull();
  });

  it('[MAM-87 AC-2] the Reason box is not offered for Approve', async () => {
    const { user } = await openRivera();

    await user.click(approveRadio());

    expect(screen.queryByLabelText(/^Reason/)).toBeNull();
  });

  it('[MAM-87 AC-2] supervisor1 can record the decision too', async () => {
    const { user } = await openRivera(undefined, 'supervisor1');

    await user.click(approveRadio());
    await user.click(recordButton());

    expect(await screen.findByText(/Decision recorded/)).toBeInTheDocument();
    expect(sentDecision(decisions(RIVERA_NPI)[0])).toEqual({ decision: 'APPROVE' });
  });

  it('[MAM-87 AC-2] the page links to the provider detail, which then shows Active (A) and no "Approve or deny" action', async () => {
    const { user } = await openRivera();
    await user.click(approveRadio());
    await user.click(recordButton());
    await screen.findByText(/Decision recorded/);

    await user.click(main().getByRole('link', { name: 'Provider detail' }));

    expect(await screen.findByRole('heading', { level: 1, name: 'Provider detail' })).toBeInTheDocument();
    await waitFor(() => expect(fieldValue('Status')).toBe('Active (A)'));
    expect(screen.queryByRole('link', { name: 'Approve or deny' })).toBeNull();
  });
});

describe('[MAM-87 AC-3] Deny with a reason (journey J3)', () => {
  it('[MAM-87 AC-3] "Deny (becomes Denied)" with the reason and "Record decision" send POST .../1003000444/approval with decision DENY and the reason', async () => {
    const { user } = await openClinic();

    await user.click(screen.getByRole('radio', { name: 'Deny (becomes Denied)' }));
    await user.type(reasonBox(), REASON);
    await user.click(recordButton());

    await waitFor(() => expect(decisions(CLINIC_NPI)).toHaveLength(1));
    expect(sentDecision(decisions(CLINIC_NPI)[0])).toEqual({ decision: 'DENY', reason: REASON });
  });

  it('[MAM-87 AC-3] the page confirms Denied (D)', async () => {
    const { user } = await openClinic();

    await user.click(denyRadio());
    await user.type(reasonBox(), REASON);
    await user.click(recordButton());

    const confirmation = await screen.findByText(/Decision recorded/);
    expect(text(confirmation)).toContain('Denied (D)');
    expect(fieldValue('Status')).toBe('Denied (D)');
    expect(screen.queryByRole('radio')).toBeNull();
  });

  it('[MAM-87 AC-3] the Reason is limited to 200 characters and has a character counter', async () => {
    const { user } = await openClinic();
    await user.click(denyRadio());

    expect(reasonBox()).toHaveAttribute('maxlength', '200');
    const counter = () => `${text(reasonBox().labels?.[0])} ${(reasonBox().getAttribute('aria-describedby') ?? '').split(/\s+/).map((id) => text(document.getElementById(id))).join(' ')}`;
    expect(counter()).toMatch(/\b200\b/);
    await user.type(reasonBox(), REASON);
    expect(counter()).toMatch(/\b34\b.*\b200\b|\b166\b/);
  });

  it('[MAM-87 AC-3] a longer reason is not sent: only the first 200 characters can be entered and nothing longer reaches the API', async () => {
    const { user } = await openClinic();
    await user.click(denyRadio());

    await user.type(reasonBox(), 'x'.repeat(205));
    await user.click(recordButton());

    await waitFor(() => expect(decisions(CLINIC_NPI)).toHaveLength(1));
    const sent = sentDecision(decisions(CLINIC_NPI)[0]);
    expect(String(sent['reason']).length).toBeLessThanOrEqual(200);
    expect(sent['reason']).toBe('x'.repeat(200));
  });

  it('[MAM-87 AC-3] a denial without a reason is allowed and sends no reason', async () => {
    const { user } = await openClinic();

    await user.click(denyRadio());
    await user.click(recordButton());

    await waitFor(() => expect(decisions(CLINIC_NPI)).toHaveLength(1));
    expect(sentDecision(decisions(CLINIC_NPI)[0])).toEqual({ decision: 'DENY' });
  });

  it('[MAM-87 AC-3] a reason of blanks only is not sent', async () => {
    const { user } = await openClinic();

    await user.click(denyRadio());
    await user.type(reasonBox(), '   ');
    await user.click(recordButton());

    await waitFor(() => expect(decisions(CLINIC_NPI)).toHaveLength(1));
    expect(sentDecision(decisions(CLINIC_NPI)[0])).toEqual({ decision: 'DENY' });
  });

  it('[MAM-87 AC-3] a Pass provider can be denied too, and switching back to Approve drops the reason', async () => {
    const { user } = await openRivera();

    await user.click(denyRadio());
    await user.type(reasonBox(), 'typed under Deny');
    await user.click(approveRadio());
    expect(screen.queryByLabelText(/^Reason/)).toBeNull();
    await user.click(recordButton());

    await waitFor(() => expect(decisions(RIVERA_NPI)).toHaveLength(1));
    expect(sentDecision(decisions(RIVERA_NPI)[0])).toEqual({ decision: 'APPROVE' });
  });
});

describe('[MAM-87 AC-5] refusals from the service are shown and the page is refreshed', () => {
  const ALREADY = 'Provider 1003000111 has already been decided: Active (message of this test).';

  /** Another analyst approved the provider while this page was open: the next GET returns Active. */
  async function openAfterAnotherAnalyst() {
    let decidedByOther = false;
    const world = approvalWorld(pendingPass(), passedScreening(), () => {
      decidedByOther = true;
      return response(409, { title: 'Conflict', status: 409, detail: ALREADY });
    });
    world.routes['GET /api/v1/providers/1003000111'] = () => response(200, decidedByOther ? decided(pendingPass(), 'APPROVE') : pendingPass());
    const rendered = await openApproval('analyst1', world, RIVERA_NPI);
    await screen.findByText('RIVERA MARIA');
    return rendered;
  }

  it('[MAM-87 AC-5] 409: the page shows the problem detail', async () => {
    const { user } = await openAfterAnotherAnalyst();

    await user.click(approveRadio());
    await user.click(recordButton());

    expect(await main().findByText(ALREADY)).toBeInTheDocument();
  });

  it('[MAM-87 AC-5] 409: the provider is reloaded, reads "Active (A)" and no further decision is offered', async () => {
    const { user } = await openAfterAnotherAnalyst();
    const before = api.to('GET', '/api/v1/providers/1003000111').length;

    await user.click(approveRadio());
    await user.click(recordButton());

    await waitFor(() => expect(fieldValue('Status')).toBe('Active (A)'));
    expect(api.to('GET', '/api/v1/providers/1003000111').length).toBeGreaterThan(before);
    expect(screen.queryByRole('radio')).toBeNull();
    expect(screen.queryByRole('button', { name: 'Record decision' })).toBeNull();
  });

  it('[MAM-87 AC-5] 409: the refusal is not shown as a recorded decision', async () => {
    const { user } = await openAfterAnotherAnalyst();

    await user.click(approveRadio());
    await user.click(recordButton());

    await main().findByText(ALREADY);
    expect(screen.queryByText(/Decision recorded/)).toBeNull();
  });

  it('[MAM-87 AC-5] 422: the detail is shown at the decision form and what was typed is kept', async () => {
    const detail = 'The screening of this provider has not passed (message of this test).';
    const { user } = await openClinic(() => response(422, { title: 'Unprocessable Entity', status: 422, detail }));

    await user.click(denyRadio());
    await user.type(reasonBox(), REASON);
    await user.click(recordButton());

    const shown = await main().findByText(detail);
    const group = screen.getByRole('group', { name: 'Decision' });
    expect(shown.compareDocumentPosition(group) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(denyRadio()).toBeChecked();
    expect(reasonBox()).toHaveValue(REASON);
    expect(screen.queryByText(/Decision recorded/)).toBeNull();
    expect(fieldValue('Status')).toBe('Pending (P)');
  });

  it('[MAM-87 AC-5] 404: the page shows "No provider found"', async () => {
    const { user } = await openRivera(() => response(404, { title: 'Not Found', status: 404, detail: 'No provider 1003000111.' }));

    await user.click(approveRadio());
    await user.click(recordButton());

    expect(await screen.findByText('No provider found')).toBeInTheDocument();
    expect(screen.queryByText(/Decision recorded/)).toBeNull();
  });

  it.each([
    ['500', () => response(500, { title: 'Internal Server Error', status: 500 })],
    ['503', () => response(503, { title: 'Service Unavailable', status: 503 })],
    [
      'network failure',
      () => {
        throw new TypeError('Failed to fetch');
      },
    ],
  ] as Array<[string, Handler]>)('[MAM-87 AC-5] %s: the page shows "The decision could not be recorded. Try again." and makes no claim that a decision was recorded', async (_label, answer) => {
    const { user } = await openRivera(answer);

    await user.click(approveRadio());
    await user.click(recordButton());

    expect(await screen.findByText('The decision could not be recorded. Try again.')).toBeInTheDocument();
    expect(screen.queryByText(/Decision recorded/)).toBeNull();
    expect(fieldValue('Status')).toBe('Pending (P)');
    expect(fieldValue('Revalidation due')).toBe('Not set');
  });

  it('[MAM-87 AC-5] after a failure the same decision can be recorded again', async () => {
    const answers: Handler[] = [() => response(500, { title: 'Internal Server Error', status: 500 }), (call) => response(200, decided(pendingPass(), (JSON.parse(call.body ?? '{}') as { decision?: unknown }).decision))];
    const { user } = await openRivera((call) => answers.shift()!(call));
    await user.click(approveRadio());
    await user.click(recordButton());
    await screen.findByText('The decision could not be recorded. Try again.');

    await user.click(recordButton());

    expect(await screen.findByText(/Decision recorded/)).toBeInTheDocument();
    expect(decisions(RIVERA_NPI)).toHaveLength(2);
    expect(screen.queryByRole('radio')).toBeNull(); // the form is gone after the decision
  });

  it('[MAM-87 AC-5] a refusal moves the focus to its message (the alert) so that it is announced and reachable', async () => {
    const { user } = await openRivera(() => response(500, { title: 'Internal Server Error', status: 500 }));

    await user.click(approveRadio());
    await user.click(recordButton());

    await screen.findByText('The decision could not be recorded. Try again.');
    await waitFor(() => expect(screen.getByRole('alert')).toHaveFocus());
  });
});

describe('[MAM-87 AC-6] a 403 from the service', () => {
  it('[MAM-87 AC-6] if the API answers 403 to a decision the page shows "You are not allowed to record this decision"', async () => {
    const { user } = await openRivera(() => response(403, { title: 'Forbidden', status: 403 }));

    await user.click(approveRadio());
    await user.click(recordButton());

    expect(await screen.findByText('You are not allowed to record this decision')).toBeInTheDocument();
    expect(screen.queryByText(/Decision recorded/)).toBeNull();
    expect(fieldValue('Status')).toBe('Pending (P)');
  });
});
