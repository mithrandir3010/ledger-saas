import { useEffect, useState } from 'react';
import { BarChart3, Loader2, TrendingUp, Users } from 'lucide-react';
import AppHeader from '../components/AppHeader';
import axiosInstance, { extractErrorMessage } from '../api/axiosInstance';
import type { ArpuMetric, MrrMetric } from '../types/api';

export default function AdminMetricsPage() {
  const [mrr, setMrr] = useState<MrrMetric | null>(null);
  const [arpu, setArpu] = useState<ArpuMetric | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    Promise.all([
      axiosInstance.get<MrrMetric>('/api/v1/admin/metrics/mrr'),
      axiosInstance.get<ArpuMetric>('/api/v1/admin/metrics/arpu'),
    ])
      .then(([mrrResponse, arpuResponse]) => {
        setMrr(mrrResponse.data);
        setArpu(arpuResponse.data);
      })
      .catch((err) => setError(extractErrorMessage(err, 'Metrikler yüklenemedi.')));
  }, []);

  const loading = !error && (!mrr || !arpu);

  return (
    <div className="min-h-screen">
      <AppHeader />
      <main className="mx-auto max-w-6xl px-4 py-10">
        <div className="mb-6 flex items-center gap-2">
          <BarChart3 className="h-6 w-6 text-primary-600" />
          <h1 className="text-2xl font-bold tracking-tight text-slate-900">Finansal Metrikler</h1>
        </div>

        {loading && (
          <div className="flex items-center gap-2 text-slate-500">
            <Loader2 className="h-4 w-4 animate-spin" /> Yükleniyor…
          </div>
        )}
        {error && <div className="card border-red-200 bg-red-50 text-sm text-red-700">{error}</div>}

        {mrr && arpu && (
          <div className="grid gap-6 md:grid-cols-2">
            <div className="card">
              <div className="mb-1 flex items-center gap-2 text-sm font-semibold uppercase tracking-wide text-slate-500">
                <TrendingUp className="h-4 w-4" /> Aylık Tekrarlayan Gelir (MRR)
              </div>
              <p className="text-3xl font-bold text-slate-900">${mrr.totalMrr.toFixed(2)}</p>
              <p className="mt-2 text-sm text-slate-500">
                {mrr.payingSubscriptionCount} ödeme yapan abonelik
              </p>
            </div>
            <div className="card">
              <div className="mb-1 flex items-center gap-2 text-sm font-semibold uppercase tracking-wide text-slate-500">
                <Users className="h-4 w-4" /> Kullanıcı Başına Gelir (ARPU)
              </div>
              <p className="text-3xl font-bold text-slate-900">${arpu.arpu.toFixed(2)}</p>
              <p className="mt-2 text-sm text-slate-500">{arpu.activeUserCount} aktif kullanıcı</p>
            </div>
          </div>
        )}
      </main>
    </div>
  );
}
