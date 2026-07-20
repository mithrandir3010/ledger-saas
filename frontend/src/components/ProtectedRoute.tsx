import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

interface ProtectedRouteProps {
  children: ReactNode;
  /** Bos birakilirsa yalnizca "giris yapmis olmak" yeterlidir */
  allowedStatuses?: string[];
}

export default function ProtectedRoute({ children, allowedStatuses }: ProtectedRouteProps) {
  const { isAuthenticated, user } = useAuth();
  const location = useLocation();

  if (!isAuthenticated || !user) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }

  if (allowedStatuses && !allowedStatuses.includes(user.subscriptionStatus)) {
    return <Navigate to="/checkout?reason=upgrade-required" replace />;
  }

  return <>{children}</>;
}
