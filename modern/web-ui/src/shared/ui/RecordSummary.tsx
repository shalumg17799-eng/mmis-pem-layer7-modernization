import { Tag } from '@trussworks/react-uswds';
import type { Provider } from '../api/types';
import { ENROLL_STATUS_LABEL, formatDate, RISK_TIER_LABEL, SCREEN_FLAG_LABEL, withCode } from '../format/format';

/**
 * A provider at a glance (UX overhaul): the screening result, the risk tier (and, where the page has no status banner of its own,
 * the enrolment status) as badges, and the next revalidation as a sentence, all in words and code, never colour alone. It has no
 * heading and no table, so the headings and the record table of the page keep their meaning. The badge texts carry a prefix, so
 * no badge repeats the text of a table cell.
 */
export function RecordSummary({ provider, showStatus = false }: { provider: Provider; showStatus?: boolean }) {
  const tier = provider.riskTier ? `Risk tier: ${withCode(provider.riskTier, RISK_TIER_LABEL)}` : 'Risk tier: not assigned yet';
  return (
    <div className="usa-summary-box margin-y-2">
      <div className="usa-summary-box__body">
        <p className="margin-0">
          {showStatus && <Tag className="margin-right-1">{`Enrolment status: ${withCode(provider.enrollStatus, ENROLL_STATUS_LABEL)}`}</Tag>}
          <Tag className="margin-right-1">{`Screening result: ${withCode(provider.screenFlag, SCREEN_FLAG_LABEL)}`}</Tag>
          <Tag className="margin-right-1">{tier}</Tag>
        </p>
        <p className="margin-bottom-0">{provider.revalidationDueDate ? `Next revalidation: ${formatDate(provider.revalidationDueDate)}` : 'Next revalidation: not set yet'}</p>
      </div>
    </div>
  );
}
