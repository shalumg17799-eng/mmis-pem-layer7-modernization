import { Link, matchPath, useLocation } from 'react-router-dom';
import { useAuth } from '../shared/auth/AuthProvider';
import { SCREENS, startPath } from './screens';

interface Crumb {
  label: string;
  to?: string;
}

/** The screens that belong to one provider: their trail goes through the provider detail page. */
const DETAIL_CHILDREN = ['/console/providers/:npi/approval', '/console/providers/:npi/sanctions/new'];

/**
 * The breadcrumb (UX overhaul, Q-101): where the page sits and the way back. "Home" is the start page of the role; the page itself
 * is plain text with aria-current="page". It follows the Primary navigation in the page (so the first navigation landmark is still
 * the menu) and sits outside main. Nothing is shown on the start pages, on the public pages or when the role may not open the page,
 * and the trail uses names of its own ("Home") so that no link of a page repeats the name of a menu link.
 */
export function Breadcrumb() {
  const { state } = useAuth();
  const { pathname } = useLocation();
  if (state.status !== 'authenticated') return null;
  const roles = state.user.roles;
  const here = SCREENS.find((screen) => matchPath({ path: screen.path, end: true }, pathname) !== null);
  const start = startPath(roles);
  if (!here || here.path === start.path || !here.roles.some((role) => roles.includes(role))) return null;

  const crumbs: Crumb[] = [{ label: 'Home', to: start.path }];
  if (DETAIL_CHILDREN.includes(here.path)) {
    const npi = matchPath({ path: here.path, end: true }, pathname)?.params['npi'];
    if (npi) crumbs.push({ label: 'Provider detail', to: `/console/providers/${encodeURIComponent(npi)}` });
  }
  crumbs.push({ label: here.title });

  return (
    <div className="grid-container">
      <nav className="usa-breadcrumb" aria-label="Breadcrumb">
        <ol className="usa-breadcrumb__list">
          {crumbs.map((crumb, index) =>
            index === crumbs.length - 1 ? (
              <li key={crumb.label} className="usa-breadcrumb__list-item usa-current" aria-current="page">
                <span>{crumb.label}</span>
              </li>
            ) : (
              <li key={crumb.label} className="usa-breadcrumb__list-item">
                <Link className="usa-breadcrumb__link" to={crumb.to ?? '/'}>
                  <span>{crumb.label}</span>
                </Link>
              </li>
            ),
          )}
        </ol>
      </nav>
    </div>
  );
}
