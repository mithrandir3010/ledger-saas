import { useEffect, useState } from 'react';
import { LayoutDashboard, Loader2 } from 'lucide-react';
import AppHeader from '../components/AppHeader';
import axiosInstance, { extractErrorMessage } from '../api/axiosInstance';
import type { PremiumDashboardResponse } from '../types/api';

export default function PremiumDashboardPage() {
  const [data, setData] = useState<PremiumDashboardResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    axiosInstance
      .get<PremiumDashboardResponse>('/api/v1/premium/dashboard')
      .then((response) => setData(response.data))
      .catch((err) => setError(extractErrorMessage(err, 'Dashboard verisi yüklenemedi.')));
  }, []);

  return (
    <div className="min-h-screen">
      <AppHeader />
      <main className="mx-auto max-w-6xl px-4 py-10">
        <div className="mb-6 flex items-center gap-2">
          <LayoutDashboard className="h-6 w-6 text-primary-600" />
          <h1 className="text-2xl font-bold tracking-tight text-slate-900">Premium Dashboard</h1>
        </div>

        {!data && !error && (
          <div className="flex items-center gap-2 text-slate-500">
            <Loader2 className="h-4 w-4 animate-spin" /> Yükleniyor…
          </div>
        )}
        {error && <div className="card border-red-200 bg-red-50 text-sm text-red-700">{error}</div>}
        {data && (
          <div className="card">
            <p className="text-lg font-semibold text-slate-900">{data.message}</p>
            <p className="mt-2 text-sm text-slate-500">
              Oturum: {data.user} · {new Date(data.timestamp).toLocaleString('tr-TR')}
            </p>
          </div>
        )}
      </main>
    </div>
  );
}
