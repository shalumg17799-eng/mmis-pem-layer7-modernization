import type { Provider } from '../api/types';
import { SummaryList } from './SummaryList';
import { ENROLL_STATUS_LABEL, formatDate, PROVIDER_TYPE_LABEL, RISK_TIER_LABEL, SCREEN_FLAG_LABEL, withCode } from '../format/format';

/** The fields of PRVRECRD in the order of the wireframe, with the personal or the organisation details last (D-007, D-008). */
export function recordRows(provider: Provider, statusText: (status: string) => string): Array<[string, string]> {
  const rows: Array<[string, string]> = [
    ['NPI', provider.npi],
    ['Medicaid ID', provider.medicaidId],
    ['Provider type', withCode(provider.providerType, PROVIDER_TYPE_LABEL)],
    ['Taxonomy', provider.taxonomy],
    ['Enrolment status', statusText(provider.enrollStatus)],
    ['Screening result', withCode(provider.screenFlag, SCREEN_FLAG_LABEL)],
    ['Risk tier', provider.riskTier ? withCode(provider.riskTier, RISK_TIER_LABEL) : 'Not assigned'],
    ['Effective date', formatDate(provider.effectiveDate)],
    ['Revalidation due', provider.revalidationDueDate ? formatDate(provider.revalidationDueDate) : 'Not set'],
  ];
  if (provider.providerType === 'IN') {
    rows.push(
      ['Last name', provider.individual?.lastName ?? ''],
      ['First name', provider.individual?.firstName ?? ''],
      ['Licence number', provider.individual?.licenseNo ?? ''],
      ['Licence state', provider.individual?.licenseState ?? ''],
    );
  } else {
    rows.push(
      ['Legal name', provider.organization?.legalName ?? ''],
      ['DBA name', provider.organization?.dbaName ?? ''],
      ['Owner NPI', provider.organization?.ownerNpi ?? ''],
    );
  }
  return rows;
}

const defaultStatusText = (status: string) => withCode(status, ENROLL_STATUS_LABEL);

/** The list "Provider record": every field with its value, a key-value list with no Field / Value headers (codes as text plus code, frontend-stack 6). */
export function ProviderRecordTable({ provider, statusText = defaultStatusText }: { provider: Provider; statusText?: (status: string) => string }) {
  return <SummaryList title="Provider record" rows={recordRows(provider, statusText)} />;
}
