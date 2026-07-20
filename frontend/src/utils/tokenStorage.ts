const TOKEN_KEY = 'ledgersaas_token';

/**
 * Token erişimi tek noktadan yönetilir; hem AuthContext hem axios
 * interceptor'ları bu modülü kullanır (circular import'u da önler).
 */
export const tokenStorage = {
  get(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  },
  set(token: string): void {
    localStorage.setItem(TOKEN_KEY, token);
  },
  clear(): void {
    localStorage.removeItem(TOKEN_KEY);
  },
};
