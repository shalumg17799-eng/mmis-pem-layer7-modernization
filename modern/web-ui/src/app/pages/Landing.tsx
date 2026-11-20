import { Button } from '@trussworks/react-uswds';
import { Link, Navigate } from 'react-router-dom';
import { useAuth } from '../../shared/auth/AuthProvider';
import { PageHeading } from '../../shared/ui/PageHeading';
import { startPath } from '../screens';

/**
 * Who the application is for and what each group does in it (the screens are the ones of the inventory; nothing else is promised).
 * Each group also says where it starts after signing in: a home per role.
 */
function WhoIsItFor() {
  return (
    <div className="grid-row grid-gap margin-top-4">
      <section className="tablet:grid-col-4 border-top-05 border-primary margin-bottom-3" aria-labelledby="for-providers">
        <h2 id="for-providers" className="margin-top-2">
          For providers
        </h2>
        <p>Apply for enrolment, follow your application, see your provider record and the notices sent to you.</p>
        <p className="text-base-dark margin-bottom-0">You start on My enrolment.</p>
      </section>
      <section className="tablet:grid-col-4 border-top-05 border-primary margin-bottom-3" aria-labelledby="for-staff">
        <h2 id="for-staff" className="margin-top-2">
          For analysts and supervisors
        </h2>
        <p>Search providers, work the queues, approve or deny an enrolment and, as a supervisor, record sanctions.</p>
        <p className="text-base-dark margin-bottom-0">You start on Provider search.</p>
      </section>
      <section className="tablet:grid-col-4 border-top-05 border-primary margin-bottom-3" aria-labelledby="for-admins">
        <h2 id="for-admins" className="margin-top-2">
          For administrators
        </h2>
        <p>Run the batch jobs of the old system and keep the table of risk levels.</p>
        <p className="text-base-dark margin-bottom-0">You start on Provider search.</p>
      </section>
    </div>
  );
}

/** "/": the public entry page. A signed-in user is sent on to the start page of their role. */
export function Landing() {
  const { state, signIn } = useAuth();
  if (state.status === 'authenticated') {
    const start = startPath(state.user.roles);
    if (start.path !== '/') return <Navigate to={start.path} replace />;
    return (
      <>
        <PageHeading>Provider Enrollment and Management</PageHeading>
        <p className="usa-intro">Your account has no role in this application. Ask an administrator to assign one.</p>
      </>
    );
  }
  return (
    <>
      <PageHeading>Provider Enrollment and Management</PageHeading>
      {state.status === 'loading' ? (
        <p role="status">Checking your sign-in</p>
      ) : (
        <>
          <p className="usa-intro">Sign in with your own account to enrol, check an application or work with provider records.</p>
          <p className="display-flex flex-wrap flex-align-center margin-bottom-1">
            <Button type="button" className="margin-right-2" onClick={() => void signIn('/')}>
              Sign in
            </Button>
            <Link className="usa-button usa-button--outline" to="/directory">
              Find a Medicaid provider
            </Link>
          </p>
          <p className="text-base-dark margin-top-1">You do not need to sign in to look up a provider.</p>
          <WhoIsItFor />
        </>
      )}
    </>
  );
}
