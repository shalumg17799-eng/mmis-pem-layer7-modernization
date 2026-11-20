import type { Application } from '../../../shared/api/types';

type StepState = 'done' | 'current' | 'failed' | 'waiting';

/** The five steps of an application (Q-104: step 2 reads "Validated"). */
const STEPS = ['Submitted', 'Validated', 'Screened', 'Risk assessed', 'Approved'] as const;

/** What each state adds for a screen reader: the segment is not told by colour alone. */
const SR_TEXT: Record<StepState, string> = { done: 'completed', current: 'not completed, current step', failed: 'not passed, current step', waiting: 'not completed' };

/** What this application's own status says about each step: only the first two steps are known from the application. */
function states(status: Application['status']): StepState[] {
  if (status === 'REJECTED') return ['done', 'failed', 'waiting', 'waiting', 'waiting'];
  if (status === 'VALIDATED') return ['done', 'done', 'current', 'waiting', 'waiting'];
  return ['done', 'current', 'waiting', 'waiting', 'waiting'];
}

/** Step 2 of a rejected application reads "Validation failed" (it did not pass), never "Validated"; for every other status it is "Validated" (Q-104). */
const stepName = (step: string, index: number, status: Application['status']): string => (index === 1 && status === 'REJECTED' ? 'Validation failed' : step);

/** The heading of the indicator says where the application is in words that are not the status badge (which is the one element that names the status). */
const WHERE: Record<Application['status'], string> = {
  SUBMITTED: 'Being checked',
  VALIDATED: 'Waiting for screening',
  REJECTED: 'Not accepted',
};

/**
 * The path of an application as a USWDS step indicator: five segments with their names, the current step highlighted and a heading
 * "Step 2 of 5" with a sentence on where the application is. A segment carries its state as text for assistive technology
 * ("completed", "not completed"), so no state is shown by colour alone. The badge of the status page is the one element whose whole text is
 * the status ("Validated" or "Rejected"): a rejected application shows "Validation failed" on step 2, and the heading says where the
 * application is in other words. A segment of a validated application reads "Validated" like the badge, so a lookup of that word by
 * text has to be scoped to the "Application" summary list (journeys J1 and J2 use an exact text lookup inside main on the badge).
 */
export function Timeline({ status }: { status: Application['status'] }) {
  const current = states(status);
  const highlighted = current.findIndex((state) => state === 'current' || state === 'failed');
  return (
    <div className="usa-step-indicator margin-y-3" role="group" aria-label="Progress of your application">
      <ol className="usa-step-indicator__segments">
        {STEPS.map((step, index) => {
          const state = current[index] ?? 'waiting';
          const modifier = state === 'done' ? ' usa-step-indicator__segment--complete' : state === 'waiting' ? '' : ' usa-step-indicator__segment--current';
          return (
            <li key={step} className={`usa-step-indicator__segment${modifier}`} aria-current={state === 'current' || state === 'failed' ? 'true' : undefined}>
              <span className="usa-step-indicator__segment-label">
                {stepName(step, index, status)} <span className="usa-sr-only">{SR_TEXT[state]}</span>
              </span>
            </li>
          );
        })}
      </ol>
      <div className="usa-step-indicator__header">
        <p className="usa-step-indicator__heading">
          <span className="usa-step-indicator__heading-counter">
            <span className="usa-sr-only">Step</span>
            <span className="usa-step-indicator__current-step">{highlighted + 1}</span>
            <span className="usa-step-indicator__total-steps">{` of ${STEPS.length}`}</span>
          </span>
          <span className="usa-step-indicator__heading-text">{WHERE[status]}</span>
        </p>
      </div>
    </div>
  );
}
