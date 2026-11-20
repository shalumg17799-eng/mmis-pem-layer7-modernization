import { Button } from '@trussworks/react-uswds';

interface PagerProps {
  /** 0-based page number (the contracts' `page`). */
  page: number;
  size: number;
  total: number;
  /** What is paged, for the landmark name ("Pending approval"). */
  label: string;
  onPage: (page: number) => void;
}

/**
 * Paging controls for a list with more than one page: "Page 2 of 3" with Previous and Next. Nothing is shown when everything
 * fits on one page (page 0, size 20 by default, the contracts' defaults). Buttons, so the keyboard works without a mouse.
 */
export function Pager({ page, size, total, label, onPage }: PagerProps) {
  const pages = Math.max(1, Math.ceil(total / size));
  if (pages <= 1) return null;
  return (
    <nav aria-label={`${label} pages`} className="margin-top-2 display-flex flex-align-center flex-wrap">
      <Button type="button" outline disabled={page <= 0} onClick={() => onPage(page - 1)}>
        Previous page
      </Button>
      <span className="margin-x-2" aria-live="polite">
        Page {page + 1} of {pages}
      </span>
      <Button type="button" outline disabled={page + 1 >= pages} onClick={() => onPage(page + 1)}>
        Next page
      </Button>
    </nav>
  );
}
