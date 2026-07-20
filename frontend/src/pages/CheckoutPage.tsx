import { useSearchParams } from 'react-router-dom';
import { Check, Crown, Sparkles } from 'lucide-react';
import AppHeader from '../components/AppHeader';

const PLANS = [
  {
    name: 'Starter',
    price: '9.99',
    interval: 'ay',
    icon: Sparkles,
    highlighted: false,
    features: ['5 proje', 'Temel raporlama', 'E-posta desteği'],
  },
  {
    name: 'Pro',
    price: '29.99',
    interval: 'ay',
    icon: Crown,
    highlighted: true,
    features: ['Sınırsız proje', 'Premium dashboard', 'MRR/ARPU metrikleri', 'Öncelikli destek'],
  },
];

export default function CheckoutPage() {
  const [searchParams] = useSearchParams();
  const upgradeRequired = searchParams.get('reason') === 'upgrade-required';

  return (
    <div className="min-h-screen">
      <AppHeader />
      <main className="mx-auto max-w-6xl px-4 py-10">
        {upgradeRequired && (
          <div className="mb-8 rounded-xl border border-primary-200 bg-primary-50 p-4 text-center">
            <p className="font-semibold text-primary-800">
              Bu özellik için aboneliğinizi yükseltmeniz gerekmektedir.
            </p>
            <p className="mt-1 text-sm text-primary-600">
              Premium içeriklere erişmek için aşağıdaki planlardan birini seçin.
            </p>
          </div>
        )}

        <h1 className="mb-2 text-center text-2xl font-bold tracking-tight text-slate-900">
          Planınızı Seçin
        </h1>
        <p className="mb-10 text-center text-sm text-slate-500">
          İhtiyacınıza uygun planla LedgerSaaS'ın tüm gücünü kullanın
        </p>

        <div className="mx-auto grid max-w-3xl gap-6 md:grid-cols-2">
          {PLANS.map((plan) => (
            <div
              key={plan.name}
              className={`card relative flex flex-col ${
                plan.highlighted ? 'border-primary-300 ring-2 ring-primary-500' : ''
              }`}
            >
              {plan.highlighted && (
                <span className="absolute -top-3 left-1/2 -translate-x-1/2 rounded-full bg-primary-600 px-3 py-0.5 text-xs font-semibold text-white">
                  Önerilen
                </span>
              )}
              <div className="mb-4 flex items-center gap-2">
                <plan.icon className={`h-5 w-5 ${plan.highlighted ? 'text-primary-600' : 'text-slate-400'}`} />
                <h2 className="text-lg font-bold text-slate-900">{plan.name}</h2>
              </div>
              <p className="mb-6">
                <span className="text-3xl font-bold text-slate-900">${plan.price}</span>
                <span className="text-sm text-slate-500"> / {plan.interval}</span>
              </p>
              <ul className="mb-8 flex-1 space-y-2.5 text-sm text-slate-600">
                {plan.features.map((feature) => (
                  <li key={feature} className="flex items-center gap-2">
                    <Check className="h-4 w-4 text-emerald-500" />
                    {feature}
                  </li>
                ))}
              </ul>
              <button className={plan.highlighted ? 'btn-primary w-full' : 'btn-secondary w-full'}>
                {plan.name} Planına Geç
              </button>
            </div>
          ))}
        </div>
      </main>
    </div>
  );
}
