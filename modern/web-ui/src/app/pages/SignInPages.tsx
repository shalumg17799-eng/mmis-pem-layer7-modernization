import { Alert, AlertText, Button } from '@trussworks/react-uswds';
import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../shared/auth/AuthProvider';
import { PageHeading } from '../../shared/ui/PageHeading';

/** Hands the browser to the Keycloak sign-in page once; the route that was asked for is kept and opened after the sign-in. */
export function SigningIn({ returnTo }: { returnTo: string }) {
  const { signIn } = useAuth();
  const started = useRef(false);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    if (started.current) return;
    started.current = true;
    signIn(returnTo).catch(() => setFailed(true));
  }, [signIn, returnTo]);

  return (
    <>
      <PageHeading>Signing in</PageHeading>
      {failed ? (
        <>
          <Alert type="error" slim role="alert">
            <AlertText>The sign-in service is not available. Try again later.</AlertText>
          </Alert>
          <p className="usa-hint">If this keeps happening, tell an administrator.</p>
        </>
      ) : (
        <p role="status">Taking you to the sign-in page</p>
      )}
    </>
  );
}

/** The page the browser returns to after Keycloak signed the user in (authorization code + state). */
export function SignInCallback() {
  const { completeSignIn, signIn } = useAuth();
  const navigate = useNavigate();
  const started = useRef(false);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    if (started.current) return;
    started.current = true;
    completeSignIn().then(
      (returnTo) => navigate(returnTo, { replace: true }),
      () => setFailed(true),
    );
  }, [completeSignIn, navigate]);

  if (failed) {
    return (
      <>
        <PageHeading>Sign-in did not complete</PageHeading>
        <Alert type="error" slim role="alert">
          <AlertText>The sign-in could not be completed. Sign in again to continue.</AlertText>
        </Alert>
        <p>
          <Button type="button" onClick={() => void signIn('/')}>
            Sign in
          </Button>
        </p>
      </>
    );
  }
  return (
    <>
      <PageHeading>Signing in</PageHeading>
      <p role="status">Completing your sign-in</p>
    </>
  );
}

export function SigningOut() {
  return (
    <>
      <PageHeading>Signing out</PageHeading>
      <p role="status">Ending your session</p>
    </>
  );
}

export function CheckingSession() {
  return (
    <>
      <PageHeading>Loading</PageHeading>
      <p role="status">Checking your sign-in</p>
    </>
  );
}
