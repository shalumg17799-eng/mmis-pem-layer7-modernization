import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Alert, AlertText, Button, Tag } from '@trussworks/react-uswds';
import { useState } from 'react';
import { LIST_NOTICES, withQuery, type NoticePage } from '../../../shared/api/types';
import { useApi } from '../../../shared/api/useApi';
import { useSession } from '../../../shared/auth/AuthProvider';
import { formatDate, noticeName } from '../../../shared/format/format';
import { EmptyState } from '../../../shared/ui/EmptyState';
import { PageHeading } from '../../../shared/ui/PageHeading';
import { Pager } from '../../../shared/ui/Pager';
import { ScrollableTable } from '../../../shared/ui/ScrollableTable';
import { SortHeader, useSorting } from '../../../shared/ui/sorting';

const CHANNEL_LABEL: Record<string, string> = { EMAIL: 'Email' };
const STATUS_LABEL: Record<string, string> = { SENT: 'Sent', FAILED: 'Failed' };
/** Colour adds to the delivery badge; the word names the state. */
const STATUS_COLOUR: Record<string, string> = { SENT: 'bg-success-dark', FAILED: 'bg-secondary-dark' };
const PAGE_SIZE = 20;
const NOTICE_SORT = {
  sent: (notice: NoticePage['items'][number]) => notice.sentAt,
  notice: (notice: NoticePage['items'][number]) => noticeName(notice.template),
  channel: (notice: NoticePage['items'][number]) => (notice.channel ? (CHANNEL_LABEL[notice.channel] ?? notice.channel) : undefined),
  status: (notice: NoticePage['items'][number]) => STATUS_LABEL[notice.status] ?? notice.status,
};

/**
 * portal.notices (MAM-70): the notices the programme sent to the signed-in provider, with the delivery state in words. The page
 * never asks for an NPI: notification-service answers for the NPI of the token (D-023), and a notice of another NPI is dropped
 * here even if one were returned. A failed load (401, 5xx, network) is shown as such, never as "No notices yet" (D-024).
 */
export function NoticesPage() {
  const api = useApi();
  const user = useSession();
  const [page, setPage] = useState(0);
  const query = useQuery({
    queryKey: ['notices', user.subject, page],
    placeholderData: keepPreviousData,
    queryFn: ({ signal }) => api.get<NoticePage>(withQuery(LIST_NOTICES, { page: page > 0 ? page : undefined }), signal, { endSessionOn401: false }),
  });
  const own = (query.data?.items ?? []).filter((notice) => user.npi === undefined || notice.npi === user.npi);
  const total = query.data?.total ?? 0;
  const sorting = useSorting(own, NOTICE_SORT);
  return (
    <>
      <PageHeading>My notices</PageHeading>
      <p className="usa-intro">The notices sent to you: approval, denial, rejection, suspension, reinstatement, termination and revalidation.</p>
      {query.status === 'pending' && <p role="status">Loading notices</p>}
      {query.status === 'error' && (
        <>
          <Alert type="error" slim role="alert">
            <AlertText>The notices could not be loaded. Try again.</AlertText>
          </Alert>
          <Button type="button" onClick={() => void query.refetch()}>
            Retry
          </Button>
        </>
      )}
      {query.status === 'success' && own.length === 0 && (
        <EmptyState title="No notices yet">
          <p>A notice appears here when the programme sends you one, for example after a decision on your enrolment.</p>
        </EmptyState>
      )}
      {query.status === 'success' && own.length > 0 && (
        <>
          <p>
            {total} {total === 1 ? 'notice' : 'notices'}
          </p>
          <ScrollableTable bordered={false} fullWidth label="Notices">
            <caption>Notices</caption>
            <thead>
              <tr>
                <SortHeader column="sent" label="Sent" sorting={sorting} />
                <SortHeader column="notice" label="Notice" sorting={sorting} />
                <SortHeader column="channel" label="Channel" sorting={sorting} />
                <SortHeader column="status" label="Status" sorting={sorting} />
              </tr>
            </thead>
            <tbody>
              {sorting.rows.map((notice) => (
                <tr key={notice.noticeId}>
                  <td>{notice.sentAt ? formatDate(notice.sentAt) : ''}</td>
                  <td>{noticeName(notice.template)}</td>
                  <td>{notice.channel ? (CHANNEL_LABEL[notice.channel] ?? notice.channel) : '-'}</td>
                  <td>
                    <Tag className={STATUS_COLOUR[notice.status] ?? ''}>{STATUS_LABEL[notice.status] ?? notice.status}</Tag>
                  </td>
                </tr>
              ))}
            </tbody>
          </ScrollableTable>
          <Pager page={page} size={PAGE_SIZE} total={total} label="Notices" onPage={setPage} />
        </>
      )}
    </>
  );
}
