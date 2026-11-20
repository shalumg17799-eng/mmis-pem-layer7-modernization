import { ApiError } from '../../../shared/api/client';
import type { ApprovalRequest, Provider } from '../../../shared/api/types';

export type Decision = 'APPROVE' | 'DENY';

/** Maximum length of the reason (contract ApprovalRequest.reason). */
export const REASON_LIMIT = 200;

/**
 * What the approval page offers for a provider (D-019, QE-6, MAM-87 AC-4): the form only for a Pending provider whose screening has
 * finished; Approve only after a passed screening (flag Pass), Deny after Pass or Fail. Anything else is a message.
 */
export type Offer = { kind: 'form'; approveAllowed: boolean } | { kind: 'unscreened' } | { kind: 'decided' };

export function offerFor(provider: Provider): Offer {
  if (provider.enrollStatus !== 'P') return { kind: 'decided' };
  if (provider.screenFlag === 'U') return { kind: 'unscreened' };
  return { kind: 'form', approveAllowed: provider.screenFlag === 'P' };
}

/** The "Approve or deny" action of the detail page is offered exactly when the approval page has a form to show. */
export function canOpenApproval(provider: Provider): boolean {
  return offerFor(provider).kind === 'form';
}

/** The body of POST /api/v1/providers/{npi}/approval: the reason belongs to a denial and is left out when it is blank. */
export function toApprovalRequest(decision: Decision, reason: string): ApprovalRequest {
  const trimmed = reason.trim();
  return decision === 'DENY' && trimmed !== '' ? { decision, reason: trimmed } : { decision };
}

export interface Refusal {
  heading: string;
  detail?: string;
}

/** What the page says when the service did not record the decision (AC-5, AC-6, D-024): never a claim that it was recorded. */
export function describeRefusal(error: unknown): Refusal {
  if (error instanceof ApiError) {
    if (error.status === 403) return { heading: 'You are not allowed to record this decision' };
    if (error.status === 404) return { heading: 'No provider found' };
    if (error.status === 409) {
      return { heading: 'The decision could not be recorded', detail: error.problem?.detail ?? 'The provider has already been decided.' };
    }
    if (error.status === 422) {
      return { heading: 'The decision could not be recorded', detail: error.problem?.detail ?? 'The decision is not allowed for this provider.' };
    }
  }
  return { heading: 'The decision could not be recorded. Try again.' };
}
