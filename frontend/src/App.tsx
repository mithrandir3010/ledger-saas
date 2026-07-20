import { Navigate, Route, Routes } from 'react-router-dom';
import ProtectedRoute from './components/ProtectedRoute';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import ProfilePage from './pages/ProfilePage';
import CheckoutPage from './pages/CheckoutPage';
import PremiumDashboardPage from './pages/PremiumDashboardPage';
import AdminMetricsPage from './pages/AdminMetricsPage';

export default function App() {
  return (
    <Routes>
      {/* Herkese acik rotalar */}
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />

      {/* Giris yapmis herkese acik rotalar */}
      <Route
        path="/profile"
        element={
          <ProtectedRoute>
            <ProfilePage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/checkout"
        element={
          <ProtectedRoute>
            <CheckoutPage />
          </ProtectedRoute>
        }
      />

      {/* Yalnizca aktif abonelere acik */}
      <Route
        path="/premium-dashboard"
        element={
          <ProtectedRoute allowedStatuses={['SUBSCRIBER_ACTIVE']}>
            <PremiumDashboardPage />
          </ProtectedRoute>
        }
      />

      {/* Yalnizca adminlere acik (backend'e ADMIN yetkisi eklendiginde aktiflesecek) */}
      <Route
        path="/admin-metrics"
        element={
          <ProtectedRoute allowedStatuses={['ADMIN']}>
            <AdminMetricsPage />
          </ProtectedRoute>
        }
      />

      {/* Varsayilan yonlendirmeler */}
      <Route path="/" element={<Navigate to="/profile" replace />} />
      <Route path="*" element={<Navigate to="/profile" replace />} />
    </Routes>
  );
}
