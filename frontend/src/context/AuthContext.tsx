import { createContext, useCallback, useContext, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { decodeJwt, isTokenExpired } from '../utils/jwt';
import { tokenStorage } from '../utils/tokenStorage';

export interface AuthUser {
  /** JWT 'sub' claim'i e-posta taşır; ayrı bir id claim'i yoktur */
  email: string;
  fullName: string | null;
  /** SUBSCRIBER_ACTIVE | SUBSCRIBER_PAST_DUE | SUBSCRIBER_FREE | ADMIN ... */
  subscriptionStatus: string;
}

interface AuthContextValue {
  user: AuthUser | null;
  token: string | null;
  isAuthenticated: boolean;
  login: (token: string) => void;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

function buildUserFromToken(token: string): AuthUser | null {
  const payload = decodeJwt(token);
  if (!payload || isTokenExpired(payload)) return null;

  return {
    email: payload.sub,
    fullName: null,
    subscriptionStatus: payload.authorities?.[0] ?? 'SUBSCRIBER_FREE',
  };
}

function readInitialState(): { token: string | null; user: AuthUser | null } {
  const storedToken = tokenStorage.get();
  if (!storedToken) return { token: null, user: null };

  const user = buildUserFromToken(storedToken);
  if (!user) {
    // Suresi dolmus / bozuk token'i sessizce temizle
    tokenStorage.clear();
    return { token: null, user: null };
  }
  return { token: storedToken, user };
}

export function AuthProvider({ children }: { children: ReactNode }) {
  // Ilk render'da localStorage'dan senkron okunur; boylece korumali
  // rotalarda "once login'e at, sonra geri getir" seklinde titreme olmaz.
  const [{ token, user }, setAuthState] = useState(readInitialState);

  const login = useCallback((newToken: string) => {
    const newUser = buildUserFromToken(newToken);
    if (!newUser) {
      throw new Error('Geçersiz veya süresi dolmuş token ile giriş yapılamaz');
    }
    tokenStorage.set(newToken);
    setAuthState({ token: newToken, user: newUser });
  }, []);

  const logout = useCallback(() => {
    tokenStorage.clear();
    setAuthState({ token: null, user: null });
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      token,
      isAuthenticated: user !== null,
      login,
      logout,
    }),
    [user, token, login, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth yalnızca AuthProvider içinde kullanılabilir');
  }
  return context;
}
