import type { Provider } from '../../../shared/api/types';
import { ENROLL_STATUS_LABEL, formatDate, providerName, SCREEN_FLAG_LABEL, withCode } from '../../../shared/format/format';
import { SummaryList } from '../../../shared/ui/SummaryList';

/**
 * The provider in short, for the console pages that decide on a provider, as a key-value list: status and screening result are text
 * plus code, never colour alone (frontend-stack section 5). The NPI is always shown in full. The full detail page is the story
 * ui-console-detail.
 */
export function ProviderTable({ provider }: { provider: Provider }) {
  return (
    <SummaryList
      title="Provider"
      rows={[
        ['NPI', provider.npi],
        ['Name', providerName(provider)],
        ['Status', withCode(provider.enrollStatus, ENROLL_STATUS_LABEL)],
        ['Screening result', withCode(provider.screenFlag, SCREEN_FLAG_LABEL)],
        ['Revalidation due', provider.revalidationDueDate ? formatDate(provider.revalidationDueDate) : 'Not set'],
      ]}
    />
  );
}
