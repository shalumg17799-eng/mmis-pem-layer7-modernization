import { useQuery } from '@tanstack/react-query';
import { Tag } from '@trussworks/react-uswds';
import {
  auditPath,
  LIST_NOTICES,
  LIST_REVALIDATIONS,
  SANCTIONS,
  withQuery,
  type AuditPage,
  type NoticePage,
  type Provider,
  type RevalidationCasePage,
  type SanctionPage,
} from '../../../../shared/api/types';
import { useApi } from '../../../../shared/api/useApi';
import { useSession } from '../../../../shared/auth/AuthProvider';
import {
  formatDate,
  auditActionText,
  noticeName,
  REVALIDATION_STATUS_LABEL,
  SCREEN_ERROR_LABEL,
  SCREEN_RESULT_LABEL,
  SCREENING_OVERALL_LABEL,
  SCREENING_REASON_LABEL,
  screeningDetailText,
  sourceName,
  withCode,
} from '../../../../shared/format/format';
import { ScrollableTable } from '../../../../shared/ui/ScrollableTable';
import { SortHeader, useSorting } from '../../../../shared/ui/sorting';
import { useScreenings } from '../../approval/ScreeningResults';
import { Panel } from './Panel';
import { SanctionTable } from './SanctionTable';

const STATUS_WORDS: Record<string, string> = { SENT: 'Sent', FAILED: 'Failed' };
/** Colour adds to a badge; the word (and the code) name the state. */
const DELIVERY_COLOUR: Record<string, string> = { SENT: 'bg-success-dark', FAILED: 'bg-secondary-dark' };
const RESULT_COLOUR: Record<string, string> = { P: 'bg-success-dark', F: 'bg-secondary-dark', C: '' };

/** Only the first page of a list is shown: say so when there is more (nothing is hidden silently). */
function Shown({ shown, total }: { shown: number; total: number }) {
  return total > shown ? <p>{`Showing ${shown} of ${total}`}</p> : null;
}

/** One list of a panel: GET `path`; the key holds the NPI so that a new provider asks again. */
function usePanelData<T>(name: string, npi: string, path: string) {
  const api = useApi();
  const user = useSession();
  return useQuery({ queryKey: ['panel', user.subject, name, npi], queryFn: ({ signal }) => api.get<T>(path, signal) });
}

function ScreeningPanel({ npi }: { npi: string }) {
  const query = useScreenings(npi);
  const screenings = query.data?.items.filter((item) => (item.results ?? []).length > 0) ?? [];
  return (
    <Panel
      title="Screening"
      description="The latest checks of this provider and what each source said."
      status={query.status}
      failure="The screening results could not be loaded"
      onRetry={() => void query.refetch()}
    >
      {screenings.length === 0 && (
        <>
          <p>Not screened yet</p>
          <p>Results appear here after the automatic checks have run for this provider.</p>
        </>
      )}
      {screenings.map((screening, index) => {
        const heading = `${SCREENING_REASON_LABEL[screening.reason] ?? screening.reason} screening on ${screening.startedAt ? formatDate(screening.startedAt) : 'an unknown date'}: ${
          screening.result ? (SCREENING_OVERALL_LABEL[screening.result] ?? screening.result) : 'in progress'
        }`;
        return (
          <div key={screening.screeningId}>
            <h3>{heading}</h3>
            <ScrollableTable bordered={false} fullWidth label={screenings.length > 1 ? `${heading} (${index + 1} of ${screenings.length})` : heading}>
              <caption className="usa-sr-only">{heading}</caption>
              <thead>
                <tr>
                  <th scope="col">Source</th>
                  <th scope="col">Result</th>
                  <th scope="col">Verdict</th>
                  <th scope="col">Detail</th>
                  <th scope="col">Error code</th>
                </tr>
              </thead>
              <tbody>
                {(screening.results ?? []).map((result) => (
                  <tr key={result.source}>
                    <td>{sourceName(result.source)}</td>
                    <td>
                      <Tag className={RESULT_COLOUR[result.result] ?? ''}>{withCode(result.result, SCREEN_RESULT_LABEL)}</Tag>
                    </td>
                    <td>{formatDate(result.verdictAt)}</td>
                    <td>{screeningDetailText(result.detail)}</td>
                    <td>{result.errorCode ? `${result.errorCode} ${SCREEN_ERROR_LABEL[result.errorCode] ?? ''}`.trim() : '-'}</td>
                  </tr>
                ))}
              </tbody>
            </ScrollableTable>
          </div>
        );
      })}
    </Panel>
  );
}

const REVALIDATION_SORT = {
  due: (revalidation: RevalidationCasePage['items'][number]) => revalidation.dueDate,
  case: (revalidation: RevalidationCasePage['items'][number]) => REVALIDATION_STATUS_LABEL[revalidation.status] ?? revalidation.status,
  opened: (revalidation: RevalidationCasePage['items'][number]) => revalidation.openedAt,
  closed: (revalidation: RevalidationCasePage['items'][number]) => revalidation.closedAt,
};

function RevalidationTable({ items }: { items: RevalidationCasePage['items'] }) {
  const sorting = useSorting(items, REVALIDATION_SORT);
  return (
    <ScrollableTable bordered={false} fullWidth label="Revalidation cases">
      <caption className="usa-sr-only">Revalidation cases</caption>
      <thead>
        <tr>
          <SortHeader column="due" label="Due date" sorting={sorting} />
          <SortHeader column="case" label="Case" sorting={sorting} />
          <SortHeader column="opened" label="Opened" sorting={sorting} />
          <SortHeader column="closed" label="Closed" sorting={sorting} />
        </tr>
      </thead>
      <tbody>
        {sorting.rows.map((revalidation) => (
          <tr key={revalidation.caseId}>
            <td>{formatDate(revalidation.dueDate)}</td>
            <td>{REVALIDATION_STATUS_LABEL[revalidation.status] ?? revalidation.status}</td>
            <td>{revalidation.openedAt ? formatDate(revalidation.openedAt) : '-'}</td>
            <td>{revalidation.closedAt ? formatDate(revalidation.closedAt) : '-'}</td>
          </tr>
        ))}
      </tbody>
    </ScrollableTable>
  );
}

const NOTICE_SORT = {
  sent: (notice: NoticePage['items'][number]) => notice.sentAt,
  notice: (notice: NoticePage['items'][number]) => noticeName(notice.template),
  status: (notice: NoticePage['items'][number]) => STATUS_WORDS[notice.status] ?? notice.status,
};

function NoticesTable({ items }: { items: NoticePage['items'] }) {
  const sorting = useSorting(items, NOTICE_SORT);
  return (
    <ScrollableTable bordered={false} fullWidth label="Notices sent">
      <caption className="usa-sr-only">Notices sent</caption>
      <thead>
        <tr>
          <SortHeader column="sent" label="Sent" sorting={sorting} />
          <SortHeader column="notice" label="Notice" sorting={sorting} />
          <SortHeader column="status" label="Status" sorting={sorting} />
        </tr>
      </thead>
      <tbody>
        {sorting.rows.map((notice) => (
          <tr key={notice.noticeId}>
            <td>{notice.sentAt ? formatDate(notice.sentAt) : ''}</td>
            <td>{noticeName(notice.template)}</td>
            <td>
              <Tag className={DELIVERY_COLOUR[notice.status] ?? ''}>{STATUS_WORDS[notice.status] ?? notice.status}</Tag>
            </td>
          </tr>
        ))}
      </tbody>
    </ScrollableTable>
  );
}

const AUDIT_SORT = {
  when: (entry: AuditPage['items'][number]) => entry.at,
  who: (entry: AuditPage['items'][number]) => entry.actor,
  action: (entry: AuditPage['items'][number]) => auditActionText(entry.action),
};

function AuditTable({ items }: { items: AuditPage['items'] }) {
  const sorting = useSorting(items, AUDIT_SORT);
  return (
    <ScrollableTable bordered={false} fullWidth label="Audit history">
      <caption className="usa-sr-only">Audit history</caption>
      <thead>
        <tr>
          <SortHeader column="when" label="When" sorting={sorting} />
          <SortHeader column="who" label="Who" sorting={sorting} />
          <SortHeader column="action" label="Action" sorting={sorting} />
        </tr>
      </thead>
      <tbody>
        {sorting.rows.map((entry, index) => (
          <tr key={`${entry.at}-${index}`}>
            <td>{formatDate(entry.at)}</td>
            <td>{entry.actor}</td>
            <td>{auditActionText(entry.action)}</td>
          </tr>
        ))}
      </tbody>
    </ScrollableTable>
  );
}

function SanctionsPanel({ npi }: { npi: string }) {
  const query = usePanelData<SanctionPage>('sanctions', npi, withQuery(SANCTIONS, { npi }));
  const items = query.data?.items ?? [];
  return (
    <Panel
      title="Sanctions"
      description="Terminations, suspensions and reinstatements of this provider."
      status={query.status}
      failure="The sanctions could not be loaded"
      onRetry={() => void query.refetch()}
    >
      {items.length === 0 ? (
        <>
          <p>None recorded</p>
          <p>An action appears here after a supervisor records it.</p>
        </>
      ) : (
        <>
          <SanctionTable items={items} />
          <Shown shown={items.length} total={query.data?.total ?? 0} />
        </>
      )}
    </Panel>
  );
}

function RevalidationPanel({ provider }: { provider: Provider }) {
  const query = usePanelData<RevalidationCasePage>('revalidation', provider.npi, withQuery(LIST_REVALIDATIONS, { npi: provider.npi }));
  const items = query.data?.items ?? [];
  return (
    <Panel
      title="Revalidation"
      description="When this provider must be checked again, and the cases opened for it."
      status={query.status}
      failure="The revalidation cases could not be loaded"
      onRetry={() => void query.refetch()}
    >
      <p>{`Revalidation due: ${provider.revalidationDueDate ? formatDate(provider.revalidationDueDate) : 'Not set'}`}</p>
      {items.length === 0 ? (
        <>
          <p>No revalidation case</p>
          <p>A case is opened when the revalidation date of the provider comes due.</p>
        </>
      ) : (
        <>
          <RevalidationTable items={items} />
          <Shown shown={items.length} total={query.data?.total ?? 0} />
        </>
      )}
    </Panel>
  );
}

function NoticesPanel({ npi }: { npi: string }) {
  const query = usePanelData<NoticePage>('notices', npi, withQuery(LIST_NOTICES, { npi }));
  const items = query.data?.items ?? [];
  return (
    <Panel
      title="Notices"
      description="The notices the programme sent to this provider and whether they were delivered."
      status={query.status}
      failure="The notices could not be loaded"
      onRetry={() => void query.refetch()}
    >
      {items.length === 0 ? (
        <>
          <p>No notices sent</p>
          <p>A notice appears here when the programme sends one after a decision on the provider.</p>
        </>
      ) : (
        <>
          <NoticesTable items={items} />
          <Shown shown={items.length} total={query.data?.total ?? 0} />
        </>
      )}
    </Panel>
  );
}

function AuditPanel({ npi }: { npi: string }) {
  const query = usePanelData<AuditPage>('audit', npi, auditPath(npi));
  const items = query.data?.items ?? [];
  return (
    <Panel
      title="Audit"
      description="Who did what to this provider, and when."
      status={query.status}
      failure="The audit history could not be loaded"
      onRetry={() => void query.refetch()}
    >
      {items.length === 0 ? (
        <>
          <p>No audit entries</p>
          <p>Every change to the provider is listed here once it has been made.</p>
        </>
      ) : (
        <>
          <AuditTable items={items} />
          <Shown shown={items.length} total={query.data?.total ?? 0} />
        </>
      )}
    </Panel>
  );
}

/**
 * ui-console-detail-2 (MAM-82): what happened to the provider. Each role sees the panels its role may read, and a panel that is not
 * shown is not requested (no 403): Revalidation for ANALYST and SUPERVISOR, Audit for SUPERVISOR and ADMIN, the rest for all three.
 * Every panel loads and fails on its own (D-024); nothing is invented for an endpoint that is not there yet.
 */
export function DetailPanels({ provider }: { provider: Provider }) {
  const { roles } = useSession();
  const has = (...wanted: string[]) => roles.some((role) => wanted.includes(role));
  return (
    <>
      <ScreeningPanel npi={provider.npi} />
      <SanctionsPanel npi={provider.npi} />
      {has('ANALYST', 'SUPERVISOR') && <RevalidationPanel provider={provider} />}
      <NoticesPanel npi={provider.npi} />
      {has('SUPERVISOR', 'ADMIN') && <AuditPanel npi={provider.npi} />}
    </>
  );
}
