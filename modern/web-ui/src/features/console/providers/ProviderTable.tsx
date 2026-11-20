import type { Provider } from '../../../shared/api/types';
import { formatDate, providerName, SCREEN_FLAG_LABEL, statusWithCatchAll, withCode } from '../../../shared/format/format';
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
        // one label for one provider: the legacy catch-all "Denied (X)" as the banner and the record list show it (MAM-80 AC-3, AC-6)
        ['Status', statusWithCatchAll(provider.enrollStatus)],
        ['Screening result', withCode(provider.screenFlag, SCREEN_FLAG_LABEL)],
        ['Revalidation due', provider.revalidationDueDate ? formatDate(provider.revalidationDueDate) : 'Not set'],
      ]}
    />
  );
}
