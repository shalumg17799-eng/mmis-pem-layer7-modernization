import { Tag } from '@trussworks/react-uswds';
import type { Sanction } from '../../../../shared/api/types';
import { formatDate, SANCTION_ACTION_LABEL, withCode } from '../../../../shared/format/format';
import { ScrollableTable } from '../../../../shared/ui/ScrollableTable';
import { SortHeader, useSorting } from '../../../../shared/ui/sorting';

/** Colour adds to the action badge; the word and the code name the action. Reinstate is the one that restores the enrolment. */
const ACTION_COLOUR: Record<string, string> = { T: 'bg-secondary-dark', S: '', R: 'bg-success-dark' };

const SANCTION_SORT = {
  date: (sanction: Sanction) => sanction.effectiveDate,
  action: (sanction: Sanction) => SANCTION_ACTION_LABEL[sanction.action] ?? sanction.action,
  reason: (sanction: Sanction) => sanction.reason,
  by: (sanction: Sanction) => sanction.recordedBy,
};

/** The sanction history: Effective date | Action | Reason | By, newest first as the service returns it (D-018) until a header is pressed. */
export function SanctionTable({ items }: { items: readonly Sanction[] }) {
  const sorting = useSorting(items, SANCTION_SORT);
  return (
    <ScrollableTable bordered={false} fullWidth label="Sanction history">
      <caption className="usa-sr-only">Sanction history</caption>
      <thead>
        <tr>
          <SortHeader column="date" label="Effective date" sorting={sorting} />
          <SortHeader column="action" label="Action" sorting={sorting} />
          <SortHeader column="reason" label="Reason" sorting={sorting} />
          <SortHeader column="by" label="By" sorting={sorting} />
        </tr>
      </thead>
      <tbody>
        {sorting.rows.map((sanction) => (
          <tr key={sanction.sanctionId}>
            <td>{formatDate(sanction.effectiveDate)}</td>
            <td>
              <Tag className={ACTION_COLOUR[sanction.action] ?? ''}>{withCode(sanction.action, SANCTION_ACTION_LABEL)}</Tag>
            </td>
            <td>{sanction.reason ?? ''}</td>
            <td>{sanction.recordedBy ?? ''}</td>
          </tr>
        ))}
      </tbody>
    </ScrollableTable>
  );
}
