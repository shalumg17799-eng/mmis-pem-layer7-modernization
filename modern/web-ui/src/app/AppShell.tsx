import { Button, GovBanner, Header, NavMenuButton, PrimaryNav, Title } from '@trussworks/react-uswds';
import { useState, type ReactNode } from 'react';
import { Link, NavLink, useLocation } from 'react-router-dom';
import { ROLE_LABEL } from '../shared/auth/claims';
import { useAuth } from '../shared/auth/AuthProvider';
import { Breadcrumb } from './Breadcrumb';
import { menuFor } from './screens';

/**
 * The frame of every page: skip link first, government banner, header with the role-based menu (the nav landmark) and one
 * main landmark. The menu lists only what the role allows (AC-2); the page inside `main` brings its own h1. The breadcrumb comes
 * after the menu and before `main`.
 */
export function AppShell({ children }: { children: ReactNode }) {
  const { state, signIn, signOut } = useAuth();
  const location = useLocation();
  const [expanded, setExpanded] = useState(false);
  const toggle = () => setExpanded((open) => !open);

  const entries = state.status === 'authenticated' ? menuFor(state.user.roles) : [];
  const items = entries.map((entry) => (
    <NavLink
      key={entry.path}
      to={entry.path}
      end
      className={({ isActive }) => (isActive ? 'usa-nav__link usa-current' : 'usa-nav__link')}
      onClick={() => setExpanded(false)}
    >
      <span>{entry.label}</span>
    </NavLink>
  ));

  return (
    <>
      <a className="usa-skipnav" href="#main-content">
        Skip to main content
      </a>
      <GovBanner />
      <Header basic showMobileOverlay={expanded}>
        <div className="usa-nav-container">
          <div className="usa-navbar">
            <Title>
              <Link to="/" title="Home">
                PEM Provider Enrollment
              </Link>
            </Title>
            <NavMenuButton label="Menu" onClick={toggle} />
          </div>
          <PrimaryNav aria-label="Primary navigation" items={items} mobileExpanded={expanded} onToggleMobileNav={toggle}>
            {state.status === 'authenticated' && (
              <div className="display-flex flex-align-center flex-wrap margin-y-1 padding-left-2 desktop:margin-y-0 desktop:margin-left-auto">
                <span className="margin-right-2">
                  Signed in as <strong>{state.user.displayName}</strong>
                  {state.user.roles.length > 0 && ` (${state.user.roles.map((role) => ROLE_LABEL[role]).join(', ')})`}
                </span>
                <Button type="button" outline className="margin-right-0" onClick={() => void signOut()}>
                  Sign out
                </Button>
              </div>
            )}
            {state.status === 'anonymous' && (
              <div className="display-flex flex-align-center margin-y-1 padding-left-2 desktop:margin-y-0 desktop:margin-left-auto">
                <Button type="button" className="margin-right-0" onClick={() => void signIn(`${location.pathname}${location.search}`)}>
                  Sign in
                </Button>
              </div>
            )}
          </PrimaryNav>
        </div>
      </Header>
      <Breadcrumb />
      <main id="main-content" tabIndex={-1} className="grid-container padding-y-4">
        {children}
      </main>
    </>
  );
}
