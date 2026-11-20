import { Route, Routes } from 'react-router-dom';
import { PublicDirectory } from '../features/public/directory/PublicDirectory';
import { AuthProvider } from '../shared/auth/AuthProvider';
import { AppShell } from './AppShell';
import { RequireRole } from './RequireRole';
import { Landing } from './pages/Landing';
import { NotFound } from './pages/Placeholder';
import { SignedOut } from './pages/SignedOut';
import { SignInCallback } from './pages/SignInPages';
import { CONSOLE_ROLES, PORTAL_ROLES, SCREENS } from './screens';
import { SIGN_IN_CALLBACK_PATH, SIGNED_OUT_PATH } from '../shared/auth/userManager';

/**
 * The application: sign-in (Keycloak OIDC), the shell, and the routes of the screen inventory.
 * Public: "/", the sign-in callback, the signed-out page and the provider directory (/directory, no sign-in). /portal/* and /console/* need a signed-in user with the right role.
 */
export function App() {
  return (
    <AuthProvider>
      <AppShell>
        <Routes>
          <Route path="/" element={<Landing />} />
          <Route path={SIGN_IN_CALLBACK_PATH} element={<SignInCallback />} />
          <Route path={SIGNED_OUT_PATH} element={<SignedOut />} />
          <Route path="/directory" element={<PublicDirectory />} />
          {SCREENS.map((screen) => (
            <Route key={screen.path} path={screen.path} element={<RequireRole roles={screen.roles}>{screen.element}</RequireRole>} />
          ))}
          <Route
            path="/portal/*"
            element={
              <RequireRole roles={PORTAL_ROLES}>
                <NotFound />
              </RequireRole>
            }
          />
          <Route
            path="/console/*"
            element={
              <RequireRole roles={CONSOLE_ROLES}>
                <NotFound />
              </RequireRole>
            }
          />
          <Route path="*" element={<NotFound />} />
        </Routes>
      </AppShell>
    </AuthProvider>
  );
}
