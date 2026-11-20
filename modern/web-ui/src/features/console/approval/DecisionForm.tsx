import { Button, Fieldset, Form, FormGroup, Label, Radio, Textarea } from '@trussworks/react-uswds';
import { useState, type FormEvent, type MouseEvent } from 'react';
import type { ApprovalRequest } from '../../../shared/api/types';
import { ErrorSummary } from '../../../shared/ui/ErrorSummary';
import { REASON_LIMIT, toApprovalRequest, type Decision } from './decision';
import './OnScreenRadio.css';

interface DecisionFormProps {
  /** Approve is offered only after a passed screening (D-019): false disables it, with the reason written next to it. */
  approveAllowed: boolean;
  /** Who the decision is about, for the live summary: "RIVERA MARIA (NPI 1003000111)". */
  subject: string;
  /** A decision is being sent: it cannot be sent twice. */
  pending: boolean;
  onDecide: (request: ApprovalRequest) => void;
}

/**
 * The decision form: a labelled set of two radio buttons (Approve / Deny), the optional Reason with its counter when Deny is
 * chosen, and Record decision. Choosing nothing sends nothing and says so in an error summary (MAM-87 AC-4, AC-7).
 */
export function DecisionForm({ approveAllowed, subject, pending, onDecide }: DecisionFormProps) {
  const [decision, setDecision] = useState<Decision | ''>('');
  const [reason, setReason] = useState('');
  const [missing, setMissing] = useState(0);

  function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending) return;
    if (decision === '') {
      setMissing((count) => count + 1);
      return;
    }
    onDecide(toApprovalRequest(decision, reason));
  }

  function focusFirstChoice(event: MouseEvent<HTMLAnchorElement>) {
    event.preventDefault();
    document.getElementById(approveAllowed ? 'decision-approve' : 'decision-deny')?.focus();
  }

  const firstChoice = approveAllowed ? 'decision-approve' : 'decision-deny';
  return (
    <>
      {missing > 0 && decision === '' && (
        <ErrorSummary key={missing} heading="1 error in the decision">
          <a className="usa-link" href={`#${firstChoice}`} onClick={focusFirstChoice}>
            Choose Approve or Deny
          </a>
        </ErrorSummary>
      )}
      <Form large noValidate onSubmit={onSubmit}>
        <Fieldset legend="Decision" legendStyle="default" className="pem-onscreen-radio">
          <Radio
            id="decision-approve"
            name="decision"
            value="APPROVE"
            label="Approve (becomes Active)"
            checked={decision === 'APPROVE'}
            disabled={!approveAllowed}
            aria-describedby={approveAllowed ? undefined : 'decision-approve-hint'}
            onChange={() => setDecision('APPROVE')}
          />
          {!approveAllowed && (
            <div className="usa-hint margin-left-4" id="decision-approve-hint">
              Approval needs a passed screening
            </div>
          )}
          <Radio
            id="decision-deny"
            name="decision"
            value="DENY"
            label="Deny (becomes Denied)"
            checked={decision === 'DENY'}
            onChange={() => setDecision('DENY')}
          />
        </Fieldset>
        {decision === 'DENY' && (
          <FormGroup>
            <Label htmlFor="reason">Reason</Label>
            <div className="usa-hint" id="reason-hint">
              Optional. At most {REASON_LIMIT} characters.
            </div>
            <Textarea
              id="reason"
              name="reason"
              value={reason}
              maxLength={REASON_LIMIT}
              aria-describedby="reason-hint reason-count"
              onChange={(event) => setReason(event.target.value)}
            />
            <div className="usa-hint" id="reason-count">
              {reason.length} of {REASON_LIMIT} characters used
            </div>
          </FormGroup>
        )}
        <p aria-live="polite" className="text-bold">
          {decision === 'APPROVE' && `You are about to approve the enrolment of ${subject}. The provider becomes Active (A) and the revalidation date is set five years after the approval date.`}
          {decision === 'DENY' && `You are about to deny the enrolment of ${subject}. The provider becomes Denied (D).`}
          {decision === '' && 'No decision chosen yet. Nothing is sent until you choose Record decision.'}
        </p>
        <Button type="submit" disabled={pending}>
          Record decision
        </Button>
      </Form>
    </>
  );
}
