import axios, { AxiosError } from 'axios';
import { decodeJwt, isTokenExpired } from '../utils/jwt';
import { tokenStorage } from '../utils/tokenStorage';
import type { ErrorResponse } from '../types/api';

const axiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080',
  headers: { 'Content-Type': 'application/json' },
  timeout: 15000,
});

// Request interceptor: gecerli (suresi dolmamis) token varsa Bearer header ekle
axiosInstance.interceptors.request.use((config) => {
  const token = tokenStorage.get();
  if (token) {
    const payload = decodeJwt(token);
    if (payload && !isTokenExpired(payload)) {
      config.headers.Authorization = `Bearer ${token}`;
    }
  }
  return config;
});

// Response interceptor: merkezi HTTP hata yonetimi
axiosInstance.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ErrorResponse>) => {
    const status = error.response?.status;
    const requestUrl = error.config?.url ?? '';

    // Login/register'in kendi 401'i (yanlis sifre vb.) oturum sonu degildir;
    // yonlendirme yapmayip hatanin formda gosterilmesine izin veriyoruz.
    const isAuthEndpoint = requestUrl.includes('/api/v1/auth/');

    if (status === 401 && !isAuthEndpoint) {
      // Token suresi dolmus veya gecersiz: oturumu kapat, login'e don
      tokenStorage.clear();
      if (window.location.pathname !== '/login') {
        window.location.href = '/login?reason=session-expired';
      }
    } else if (status === 403) {
      // Authenticated ama yetkisiz (orn. premium icerik): checkout'a yonlendir
      if (window.location.pathname !== '/checkout') {
        window.location.href = '/checkout?reason=upgrade-required';
      }
    }

    return Promise.reject(error);
  },
);

/** Axios hatasından kullanıcıya gösterilecek mesajı çıkarır */
export function extractErrorMessage(error: unknown, fallback: string): string {
  if (axios.isAxiosError<ErrorResponse>(error)) {
    return error.response?.data?.message ?? fallback;
  }
  return fallback;
}

export default axiosInstance;
