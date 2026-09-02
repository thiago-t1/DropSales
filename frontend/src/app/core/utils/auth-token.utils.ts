interface JwtPayload {
  exp?: unknown;
}

export function decodeJwtPayload(token: string): JwtPayload | null {
  const parts = token.split('.');
  if (parts.length !== 3 || !parts[1]) return null;

  try {
    const normalized = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '=');
    const payload: unknown = JSON.parse(globalThis.atob(padded));
    return payload !== null && typeof payload === 'object' ? payload as JwtPayload : null;
  } catch {
    return null;
  }
}

export function isJwtUsable(token: string | null, nowMs = Date.now()): boolean {
  if (!token) return false;

  const payload = decodeJwtPayload(token);
  const expiration = payload?.exp;
  return typeof expiration === 'number'
    && Number.isFinite(expiration)
    && expiration > Math.floor(nowMs / 1000);
}

function isPublicAuthRequest(url: string): boolean {
  return /\/auth\/(login|register)(?:[/?#]|$)/.test(url);
}

export function shouldEndSessionForUnauthorized(
  status: number,
  requestUrl: string,
  tokenAtRequest: string | null,
  currentToken: string | null,
): boolean {
  return status === 401
    && tokenAtRequest !== null
    && currentToken === tokenAtRequest
    && !isPublicAuthRequest(requestUrl);
}
