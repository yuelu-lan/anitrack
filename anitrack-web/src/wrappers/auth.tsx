import { Navigate, Outlet, useModel } from '@umijs/max';

export default function AuthWrapper() {
  const { initialState } = useModel('@@initialState');
  if (initialState?.currentUser) {
    return <Outlet />;
  }
  return <Navigate to="/login" replace />;
}
