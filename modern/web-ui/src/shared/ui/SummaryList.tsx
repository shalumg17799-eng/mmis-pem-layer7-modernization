import { useId, type ReactNode } from 'react';
import './SummaryList.css';

type Row = readonly [label: string, value: ReactNode];

/** The rows of a key-value list: a term and its value each (no Field / Value headers). */
export function DefinitionList({ rows }: { rows: readonly Row[] }) {
  return (
    <dl className="pem-summary-list">
      {rows.map(([label, value]) => (
        <div key={label} className="pem-summary-list__row">
          <dt>{label}</dt>
          <dd>{value}</dd>
        </div>
      ))}
    </dl>
  );
}

/**
 * A key-value list with its title, as a labelled section ("Provider", "Application", "Provider record"): the name of the section is
 * the name of the list. Real data tables (screening results, queues, notices, errors, search results) stay tables.
 */
export function SummaryList({ title, rows }: { title: string; rows: readonly Row[] }) {
  const id = useId();
  return (
    <section aria-labelledby={id} className="margin-y-3">
      <p id={id} className="text-bold margin-bottom-1 margin-top-0">
        {title}
      </p>
      <DefinitionList rows={rows} />
    </section>
  );
}
