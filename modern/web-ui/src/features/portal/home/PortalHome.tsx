import { useSession } from '../../../shared/auth/AuthProvider';
import { PageHeading } from '../../../shared/ui/PageHeading';
import { ApplicationsSection } from './ApplicationsSection';
import { EnrolmentSummary } from './EnrolmentSummary';
import { ProviderSummary } from './ProviderSummary';

/** /portal: the provider's start page (screen inventory: portal home; D-019, D-023). */
export function PortalHome() {
  const user = useSession();
  return (
    <>
      <PageHeading>My enrolment</PageHeading>
      <p className="usa-intro">Your applications and the current status of your provider record.</p>
      <EnrolmentSummary />
      <ApplicationsSection />
      <ProviderSummary npi={user.npi} />
    </>
  );
}
