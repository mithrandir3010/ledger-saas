export interface JwtPayload {
  /** Kullanıcı e-postası */
  sub: string;
  /** Backend'in token'a gömdüğü abonelik yetkileri (örn: SUBSCRIBER_ACTIVE) */
  authorities?: string[];
  iat?: number;
  exp?: number;
}

/**
 * JWT payload'ını kütüphanesiz decode eder. JWT'ler base64url ile
 * kodlandığı için atob öncesi karakter dönüşümü ve padding gerekir.
 * İmza doğrulaması YAPMAZ; güvenlik kararı her zaman backend'dedir,
 * frontend yalnızca UI durumu için okur.
 */
export function decodeJwt(token: string): JwtPayload | null {
  try {
    const payloadPart = token.split('.')[1];
    if (!payloadPart) return null;

    const base64 = payloadPart.replace(/-/g, '+').replace(/_/g, '/');
    const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=');
    return JSON.parse(atob(padded)) as JwtPayload;
  } catch {
    return null;
  }
}

export function isTokenExpired(payload: JwtPayload): boolean {
  if (!payload.exp) return false;
  return payload.exp * 1000 <= Date.now();
}
