import { Alert, AlertText, Button } from '@trussworks/react-uswds';
import { useId, type ReactNode } from 'react';

interface PanelProps {
  title: string;
  status: 'pending' | 'error' | 'success';
  /** "The sanctions could not be loaded": what the panel says when its own call failed (D-024). */
  failure: string;
  /** One line under the heading on what the panel shows. */
  description?: string;
  onRetry: () => void;
  children: ReactNode;
}

/**
 * One panel of the provider detail: a <section> with its heading, "Loading", or its own failure message with Retry. A panel that
 * fails never shows its empty message and never hides the other panels (MAM-82 AC-7).
 */
export function Panel({ title, status, failure, description, onRetry, children }: PanelProps) {
  const headingId = useId();
  return (
    <section aria-labelledby={headingId} className="margin-top-4">
      <h2 id={headingId}>{title}</h2>
      {description && <p className="usa-hint margin-top-0">{description}</p>}
      {status === 'pending' && <p role="status">Loading {title.toLowerCase()}</p>}
      {status === 'error' && (
        <>
          <Alert type="error" slim role="alert">
            <AlertText>{failure}</AlertText>
          </Alert>
          <Button type="button" onClick={onRetry}>
            Retry
          </Button>
        </>
      )}
      {status === 'success' && children}
    </section>
  );
}
