
const TOKEN_KEY = "codaro.token";
const EXPIRES_KEY = "codaro.tokenExpiresAt";
const DISPLAY_NAME_KEY = "codaro.displayName";

export interface StoredAuth {
  token: string;
  expiresAt: string;
  displayName: string;
}

export function saveAuth(auth: StoredAuth): void {
  if (typeof window === "undefined") return;
  window.localStorage.setItem(TOKEN_KEY, auth.token);
  window.localStorage.setItem(EXPIRES_KEY, auth.expiresAt);
  window.localStorage.setItem(DISPLAY_NAME_KEY, auth.displayName);
}

export function loadAuth(): StoredAuth | null {
  if (typeof window === "undefined") return null;
  const token = window.localStorage.getItem(TOKEN_KEY);
  const expiresAt = window.localStorage.getItem(EXPIRES_KEY);
  const displayName = window.localStorage.getItem(DISPLAY_NAME_KEY);
  if (!token) return null;
  return { token, expiresAt: expiresAt ?? "", displayName: displayName ?? "" };
}

export function clearAuth(): void {
  if (typeof window === "undefined") return;
  window.localStorage.removeItem(TOKEN_KEY);
  window.localStorage.removeItem(EXPIRES_KEY);
  window.localStorage.removeItem(DISPLAY_NAME_KEY);
}

export function getToken(): string | null {
  return loadAuth()?.token ?? null;
}
