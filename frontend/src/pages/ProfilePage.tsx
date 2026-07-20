import { BadgeCheck, Mail, ShieldAlert, ShieldCheck } from 'lucide-react';
import AppHeader from '../components/AppHeader';
import { useAuth } from '../context/AuthContext';

const STATUS_LABELS: Record<string, { label: string; className: string }> = {
  SUBSCRIBER_ACTIVE: { label: 'Aktif Abone', className: 'bg-emerald-50 text-emerald-700 border-emerald-200' },
  SUBSCRIBER_PAST_DUE: { label: 'Ödeme Bekleniyor', className: 'bg-amber-50 text-amber-700 border-amber-200' },
  SUBSCRIBER_FREE: { label: 'Ücretsiz Plan', className: 'bg-slate-100 text-slate-600 border-slate-200' },
  ADMIN: { label: 'Yönetici', className: 'bg-primary-50 text-primary-700 border-primary-200' },
};

export default function ProfilePage() {
  const { user } = useAuth();
  if (!user) return null;

  const status = STATUS_LABELS[user.subscriptionStatus] ?? {
    label: user.subscriptionStatus,
    className: 'bg-slate-100 text-slate-600 border-slate-200',
  };

  return (
    <div className="min-h-screen">
      <AppHeader />
      <main className="mx-auto max-w-6xl px-4 py-10">
        <h1 className="mb-6 text-2xl font-bold tracking-tight text-slate-900">Profil</h1>

        <div className="grid gap-6 md:grid-cols-2">
          <div className="card">
            <h2 className="mb-4 text-sm font-semibold uppercase tracking-wide text-slate-500">
              Hesap Bilgileri
            </h2>
            <div className="space-y-3 text-sm">
              <div className="flex items-center gap-3">
                <Mail className="h-4 w-4 text-slate-400" />
                <span className="text-slate-900">{user.email}</span>
              </div>
              <div className="flex items-center gap-3">
                <BadgeCheck className="h-4 w-4 text-slate-400" />
                <span className="text-slate-900">{user.fullName ?? 'Ad bilgisi token içinde taşınmıyor'}</span>
              </div>
            </div>
          </div>

          <div className="card">
            <h2 className="mb-4 text-sm font-semibold uppercase tracking-wide text-slate-500">
              Abonelik Durumu
            </h2>
            <div className="flex items-center gap-3">
              {user.subscriptionStatus === 'SUBSCRIBER_ACTIVE' ? (
                <ShieldCheck className="h-8 w-8 text-emerald-500" />
              ) : (
                <ShieldAlert className="h-8 w-8 text-slate-400" />
              )}
              <span className={`inline-flex rounded-full border px-3 py-1 text-sm font-medium ${status.className}`}>
                {status.label}
              </span>
            </div>
          </div>
        </div>
      </main>
    </div>
  );
}
