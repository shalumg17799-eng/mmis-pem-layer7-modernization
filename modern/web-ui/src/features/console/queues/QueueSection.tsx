import { Alert, AlertText, Button } from '@trussworks/react-uswds';
import { useId, type ReactNode } from 'react';
import { Pager } from '../../../shared/ui/Pager';

export const QUEUE_PAGE_SIZE = 20;

interface QueueSectionProps {
  title: string;
  /** The id of the section, so that the dashboard tile of the queue can link to it. */
  anchor: string;
  status: 'pending' | 'error' | 'success';
  /** Entries in the queue (the `total` of the API); the heading carries it. */
  total: number;
  /** Entries that decide the number of pages when it differs from `total` (the revalidation queue merges two lists). */
  pagedTotal?: number;
  emptyText: string;
  page: number;
  onPage: (page: number) => void;
  onRetry: () => void;
  /** The table of the entries; rendered only when there are some. */
  children: ReactNode;
}

/**
 * One work queue: a <section> with its heading and count, a table, or what the analyst needs instead (D-024): "loading", the
 * empty message, or "The list could not be loaded" with Retry. A failed queue never shows its empty message (MAM-85 AC-6).
 */
export function QueueSection({ title, anchor, status, total, pagedTotal, emptyText, page, onPage, onRetry, children }: QueueSectionProps) {
  const headingId = useId();
  return (
    <section id={anchor} aria-labelledby={headingId} className="margin-top-4">
      <h2 id={headingId}>{status === 'success' ? `${title} (${total})` : title}</h2>
      {status === 'pending' && <p role="status">Loading</p>}
      {status === 'error' && (
        <>
          <Alert type="error" slim role="alert">
            <AlertText>The list could not be loaded</AlertText>
          </Alert>
          <Button type="button" onClick={onRetry}>
            Retry
          </Button>
        </>
      )}
      {status === 'success' && total === 0 && <p>{emptyText}</p>}
      {status === 'success' && total > 0 && (
        <>
          {children}
          <Pager page={page} size={QUEUE_PAGE_SIZE} total={pagedTotal ?? total} label={title} onPage={onPage} />
        </>
      )}
    </section>
  );
}
