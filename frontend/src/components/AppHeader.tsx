import { Link, useNavigate } from 'react-router-dom';
import { BarChart3, CreditCard, Landmark, LayoutDashboard, LogOut, User } from 'lucide-react';
import { useAuth } from '../context/AuthContext';

export default function AppHeader() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  function handleLogout() {
    logout();
    navigate('/login', { replace: true });
  }

  return (
    <header className="border-b border-slate-200 bg-white">
      <div className="mx-auto flex h-16 max-w-6xl items-center justify-between px-4">
        <Link to="/profile" className="flex items-center gap-2">
          <div className="rounded-lg bg-primary-600 p-1.5">
            <Landmark className="h-5 w-5 text-white" />
          </div>
          <span className="text-lg font-bold tracking-tight text-slate-900">LedgerSaaS</span>
        </Link>

        <nav className="flex items-center gap-1 text-sm font-medium text-slate-600">
          <Link to="/profile" className="flex items-center gap-1.5 rounded-lg px-3 py-2 hover:bg-slate-100 hover:text-slate-900">
            <User className="h-4 w-4" /> Profil
          </Link>
          <Link to="/checkout" className="flex items-center gap-1.5 rounded-lg px-3 py-2 hover:bg-slate-100 hover:text-slate-900">
            <CreditCard className="h-4 w-4" /> Planlar
          </Link>
          <Link to="/premium-dashboard" className="flex items-center gap-1.5 rounded-lg px-3 py-2 hover:bg-slate-100 hover:text-slate-900">
            <LayoutDashboard className="h-4 w-4" /> Premium
          </Link>
          <Link to="/admin-metrics" className="flex items-center gap-1.5 rounded-lg px-3 py-2 hover:bg-slate-100 hover:text-slate-900">
            <BarChart3 className="h-4 w-4" /> Metrikler
          </Link>
        </nav>

        <div className="flex items-center gap-3">
          {user && (
            <span className="hidden text-sm text-slate-500 sm:block">{user.email}</span>
          )}
          <button onClick={handleLogout} className="btn-secondary !px-3 !py-2">
            <LogOut className="h-4 w-4" /> Çıkış
          </button>
        </div>
      </div>
    </header>
  );
}
