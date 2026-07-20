/** Backend AuthResponse DTO'sunun karşılığı */
export interface AuthResponse {
  token: string;
  tokenType: string;
  email: string;
  subscriptionAuthority: string;
}

/** Backend GlobalExceptionHandler'ın döndürdüğü standart hata gövdesi */
export interface ErrorResponse {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
}

export interface PremiumDashboardResponse {
  message: string;
  user: string;
  timestamp: string;
}

export interface MrrMetric {
  id: number;
  totalMrr: number;
  payingSubscriptionCount: number;
  calculatedAt: string;
}

export interface ArpuMetric {
  id: number;
  arpu: number;
  activeUserCount: number;
  calculatedAt: string;
}
