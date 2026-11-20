import type { ReactNode } from 'react';
import { usePendingProviders, useRejectedApplications, useRevalidationQueue } from './queries';

interface Tile {
  label: string;
  anchor: string;
  status: 'pending' | 'error' | 'success';
  count: number;
  /** What the count means in plain words, read after the number. */
  meaning: string;
}

function TileLink({ tile }: { tile: Tile }): ReactNode {
  const value = tile.status === 'success' ? String(tile.count) : tile.status === 'pending' ? '...' : '-';
  return (
    <li className="margin-right-2 margin-bottom-2">
      <a className="usa-link display-block padding-2 border-1px radius-md text-no-underline" href={`#${tile.anchor}`}>
        <span className="display-block font-sans-xl text-bold">{value}</span>
        <span className="display-block">{tile.label}</span>
        <span className="display-block text-base-dark font-sans-3xs">
          {tile.status === 'error' ? 'The list could not be loaded' : tile.status === 'pending' ? 'Loading' : tile.meaning}
        </span>
      </a>
    </li>
  );
}

/**
 * The analyst's dashboard (UX overhaul): one tile per queue with its count, each linking to the section below. The tiles read the same
 * queries as the sections (first page, so they share the answer and make no extra request) and use no heading, so the sections stay
 * the only h2 of the page. A queue that fails shows "-" and says so; it never shows a count it does not have.
 */
export function QueueTiles() {
  const pending = usePendingProviders('P', 0);
  const failed = usePendingProviders('F', 0);
  const revalidation = useRevalidationQueue(0);
  const rejected = useRejectedApplications(0);
  const tiles: Tile[] = [
    { label: 'Pending approval', anchor: 'queue-pending-approval', status: pending.status, count: pending.data?.total ?? 0, meaning: 'providers waiting for your decision' },
    { label: 'Failed screening', anchor: 'queue-failed-screening', status: failed.status, count: failed.data?.total ?? 0, meaning: 'providers that failed a check' },
    { label: 'Revalidation due', anchor: 'queue-revalidation-due', status: revalidation.status, count: revalidation.total, meaning: 'revalidation cases open' },
    { label: 'Rejected applications', anchor: 'queue-rejected-applications', status: rejected.status, count: rejected.data?.total ?? 0, meaning: 'applications to follow up' },
  ];
  return (
    <ul className="usa-list usa-list--unstyled display-flex flex-wrap margin-y-3" aria-label="Queue summary">
      {tiles.map((tile) => (
        <TileLink key={tile.anchor} tile={tile} />
      ))}
    </ul>
  );
}
