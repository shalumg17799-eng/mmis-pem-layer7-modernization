import { Alert, AlertHeading } from '@trussworks/react-uswds';
import { useEffect, useId, useRef, type ReactNode } from 'react';
import './ErrorSummary.css';

/**
 * The error summary at the top of a form or a page (frontend-stack section 5): a USWDS error alert with an h2, announced as
 * an alert and given the keyboard focus when it appears, so that the next Tab reaches its first link. Give it a new `key`
 * for every failed attempt so that the focus moves again.
 */
export function ErrorSummary({ heading, children }: { heading: string; children: ReactNode }) {
  const ref = useRef<HTMLDivElement>(null);
  const headingId = useId();
  useEffect(() => {
    ref.current?.focus();
  }, []);
  return (
    <div ref={ref} tabIndex={-1} role="alert" aria-labelledby={headingId} className="pem-error-summary margin-bottom-3">
      <Alert type="error">
        <AlertHeading level="h2" id={headingId}>
          {heading}
        </AlertHeading>
        {children}
      </Alert>
    </div>
  );
}
