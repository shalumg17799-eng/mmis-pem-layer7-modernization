import { Link } from 'react-router-dom';
import { ROLE_LABEL, type SessionUser } from '../../shared/auth/claims';
import { PageHeading } from '../../shared/ui/PageHeading';
import { startPath } from '../screens';

/** The role of the signed-in user does not allow this page (D-023): nothing of the page is fetched or shown. */
export function AccessDenied({ user }: { user: SessionUser }) {
  const start = startPath(user.roles);
  const roles = user.roles.map((role) => ROLE_LABEL[role]).join(', ');
  return (
    <>
      <PageHeading>Access denied</PageHeading>
      <p className="usa-intro">Error 403. Your role {roles ? `(${roles}) ` : ''}does not allow you to open this page.</p>
      <p>
        <Link className="usa-button" to={start.path}>
          {start.label}
        </Link>
      </p>
      <p className="text-base-dark">If you think this is a mistake, ask an administrator to check the role of your account.</p>
    </>
  );
}
