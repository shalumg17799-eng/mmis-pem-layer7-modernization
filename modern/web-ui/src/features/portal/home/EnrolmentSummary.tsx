import { Link } from 'react-router-dom';
import { useSession } from '../../../shared/auth/AuthProvider';
import { ENROLL_STATUS_LABEL, formatDate, withCode } from '../../../shared/format/format';
import type { Application, Provider } from '../../../shared/api/types';
import { useApplications, useOwnProvider } from './queries';

interface Standing {
  headline: string;
  next: string;
}

/** Where a provider with a record stands, in words and code, and what happens next (no business meaning beyond the contract). */
function standingOfProvider(provider: Provider): Standing {
  const headline = `Enrolment status: ${withCode(provider.enrollStatus, ENROLL_STATUS_LABEL)}`;
  if (provider.enrollStatus === 'P') return { headline, next: 'Next step: an analyst reviews the screening results and decides on your enrolment.' };
  if (provider.enrollStatus === 'A') {
    return { headline, next: provider.revalidationDueDate ? `Your enrolment is active. Your next revalidation is due on ${formatDate(provider.revalidationDueDate)}.` : 'Your enrolment is active.' };
  }
  return { headline, next: 'Next step: see My notices for the reason.' };
}

/** Where a provider without a record stands: from the latest application. */
function standingOfApplication(latest: Application | undefined): Standing {
  if (!latest) return { headline: 'You have not applied yet', next: 'Next step: fill in the application form to start your enrolment.' };
  if (latest.status === 'REJECTED') {
    return { headline: 'Your last application was not accepted (REJECTED)', next: 'Next step: correct the errors listed for your application and apply again.' };
  }
  const received = latest.status === 'VALIDATED' ? 'accepted (VALIDATED)' : 'received (SUBMITTED)';
  return { headline: `Your application was ${received}`, next: 'Next step: automatic checks run first, then an analyst decides on your enrolment.' };
}

/**
 * The "Your enrolment" summary of the portal home (UX overhaul): where the provider stands in words and code, the next step in plain
 * words and the one call to action. It only restates what the two lists already return (no extra request) and claims nothing while
 * they load or when they fail.
 */
export function EnrolmentSummary() {
  const user = useSession();
  const applications = useApplications();
  const provider = useOwnProvider(user.npi);
  const loading = applications.status === 'pending' || (user.npi !== undefined && provider.status === 'pending');
  const failed = applications.status === 'error' || provider.status === 'error';
  let standing: Standing | undefined;
  if (!loading && !failed) {
    if (provider.data) standing = standingOfProvider(provider.data);
    else {
      const latest = [...(applications.data?.items ?? [])].sort((a, b) => b.submittedAt.localeCompare(a.submittedAt))[0];
      standing = standingOfApplication(latest);
    }
  }
  return (
    <section className="usa-summary-box margin-y-3" aria-labelledby="enrolment-summary-heading">
      <div className="usa-summary-box__body">
        <h2 className="usa-summary-box__heading" id="enrolment-summary-heading">
          Your enrolment
        </h2>
        {loading && <p role="status">Loading your enrolment</p>}
        {failed && !loading && <p>Your enrolment status could not be loaded. Try again later.</p>}
        {standing && (
          <>
            <p className="usa-summary-box__text text-bold">{standing.headline}</p>
            <p>{standing.next}</p>
          </>
        )}
        <p>
          <Link className="usa-button" to="/portal/apply">
            Apply for enrolment
          </Link>
        </p>
      </div>
    </section>
  );
}
