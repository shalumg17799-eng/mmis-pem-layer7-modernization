import { useEffect } from 'react';

const SITE = 'PEM Provider Enrollment';

/** The one h1 of a page; it also sets the browser tab title so that every page has its own title. */
export function PageHeading({ children }: { children: string }) {
  useEffect(() => {
    document.title = `${children} - ${SITE}`;
  }, [children]);
  return <h1>{children}</h1>;
}
