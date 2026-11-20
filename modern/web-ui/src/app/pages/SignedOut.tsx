import { Button } from '@trussworks/react-uswds';
import { Link } from 'react-router-dom';
import { useAuth } from '../../shared/auth/AuthProvider';
import { PageHeading } from '../../shared/ui/PageHeading';

/** Where Keycloak returns the browser after it ended the session (post-logout redirect URI). */
export function SignedOut() {
  const { signIn } = useAuth();
  return (
    <>
      <PageHeading>You are signed out</PageHeading>
      <p className="usa-intro">Your session has ended. Sign in again to continue.</p>
      <p>
        <Button type="button" onClick={() => void signIn('/')}>
          Sign in
        </Button>
      </p>
      <p className="usa-hint">On a shared computer, close the browser window as well.</p>
      <p>
        <Link className="usa-link" to="/directory">
          Find a Medicaid provider
        </Link>{' '}
        without signing in.
      </p>
    </>
  );
}
