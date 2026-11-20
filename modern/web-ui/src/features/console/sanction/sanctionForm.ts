import { ApiError } from '../../../shared/api/client';
import { toIsoDate } from '../../../shared/format/dates';

export type SanctionField = 'action' | 'reason' | 'effectiveDate';

export interface FormError {
  /** The input the message belongs to; undefined when the form has no such input (the summary still lists it). */
  field: SanctionField | undefined;
  message: string;
}

export const ACTION_REQUIRED = 'Choose an action';
export const DATE_REQUIRED = 'Enter the effective date as MM/DD/YYYY';
export const DATE_INVALID = 'Enter a real date as MM/DD/YYYY';

/** What the page checks before it sends anything: the action and the date (the legacy rules about the status are not applied, D-027). */
export function validate(action: string, date: string): { errors: FormError[]; isoDate: string | null } {
  const errors: FormError[] = [];
  if (action === '') errors.push({ field: 'action', message: ACTION_REQUIRED });
  const isoDate = date.trim() === '' ? null : toIsoDate(date);
  if (date.trim() === '') errors.push({ field: 'effectiveDate', message: DATE_REQUIRED });
  else if (isoDate === null) errors.push({ field: 'effectiveDate', message: DATE_INVALID });
  return { errors, isoDate };
}

const FIELDS: readonly string[] = ['action', 'reason', 'effectiveDate'];

/** A refusal of the service as the page shows it (MAM-90 AC-5, AC-7); nothing is claimed that did not happen (D-024). */
export type Refusal = { kind: 'fields'; errors: FormError[] } | { kind: 'message'; heading: string };

/**
 * 422: the detail names the field ("Field action must be one of T, S or R ..."): it is shown at that field and in the summary.
 * 404, 403, and everything else (5xx, a network failure) get their own sentence; the 503 of the service has no special wording.
 */
export function describeRefusal(error: unknown): Refusal {
  if (error instanceof ApiError) {
    if (error.status === 422) {
      const detail = error.problem?.detail ?? 'The sanction was not accepted.';
      const named = /^Field (\w+) /.exec(detail)?.[1];
      return { kind: 'fields', errors: [{ field: named !== undefined && FIELDS.includes(named) ? (named as SanctionField) : undefined, message: detail }] };
    }
    if (error.status === 404) return { kind: 'message', heading: 'No provider found' };
    if (error.status === 403) return { kind: 'message', heading: 'You are not allowed to record sanctions' };
  }
  return { kind: 'message', heading: 'The sanction could not be applied. Nothing was recorded.' };
}
