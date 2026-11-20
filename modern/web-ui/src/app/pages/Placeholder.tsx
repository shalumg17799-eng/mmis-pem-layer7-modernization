import { Link } from 'react-router-dom';
import { useAuth } from '../../shared/auth/AuthProvider';
import { PageHeading } from '../../shared/ui/PageHeading';
import { startPath } from '../screens';

/** A screen of the inventory whose contents belong to its own story: it is reachable, role-guarded and has its heading. */
export function Placeholder({ title }: { title: string }) {
  return (
    <>
      <PageHeading>{title}</PageHeading>
      <p>This page is not available yet.</p>
    </>
  );
}

export function NotFound() {
  const { state } = useAuth();
  const start = state.status === 'authenticated' ? startPath(state.user.roles) : { path: '/', label: 'Go to the start page' };
  return (
    <>
      <PageHeading>Page not found</PageHeading>
      <p className="usa-intro">Error 404. The page you asked for does not exist. Use the menu to continue.</p>
      <p>
        <Link className="usa-button" to={start.path}>
          {start.label}
        </Link>
      </p>
      <p className="text-base-dark">Check the address for a typing mistake, or go back to where you started.</p>
    </>
  );
}
