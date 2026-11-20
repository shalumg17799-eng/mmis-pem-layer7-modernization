import type { ReactNode } from 'react';
import './EmptyState.css';

/**
 * What a list or a page says when there is nothing to show yet (UX overhaul, point 6): one bold line that says so, then what to do
 * or why (plain paragraphs), then the actions. The first line is a paragraph of its own, so a lookup of that exact text still finds
 * it. It is never used for a failed load: a failure is an error alert with a retry, never an empty state (D-024).
 */
export function EmptyState({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div className="pem-empty-state">
      <p className="text-bold margin-top-0">{title}</p>
      {children}
    </div>
  );
}
