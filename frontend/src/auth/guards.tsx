import { Navigate, Outlet, useLocation } from 'react-router-dom';
import type { RoleName } from '@/api/types';
import { FullScreenLoader } from '@/components/ui/Feedback';
import { homePathFor, useAuth } from './AuthProvider';

/**
 * Client-side route guards. They only decide what to *show*; every API call is also
 * authorised by the server, so hiding a route is never the only protection.
 */
export function RequireRole({ role }: { role: RoleName }) {
  const { status, user } = useAuth();
  const location = useLocation();
  if (status === 'loading') return <FullScreenLoader />;
  if (status === 'anonymous') {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  }
  if (!user?.roles.includes(role)) return <Navigate to={homePathFor(user)} replace />;
  return <Outlet />;
}

/** Login/signup pages: signed-in users are sent to their dashboard. */
export function GuestOnly() {
  const { status, user } = useAuth();
  if (status === 'loading') return <FullScreenLoader />;
  if (status === 'authenticated') return <Navigate to={homePathFor(user)} replace />;
  return <Outlet />;
}

export function RootRedirect() {
  const { status, user } = useAuth();
  if (status === 'loading') return <FullScreenLoader />;
  return <Navigate to={status === 'authenticated' ? homePathFor(user) : '/login'} replace />;
}
