import type { AssistantSession } from "./types";

// The session itself lives in the JSESSIONID cookie the API set; this is only the
// display copy, so the app can show who is signed in without a round trip on paint.
const SESSION_KEY = "everrest.session";

export function saveSession(session: AssistantSession): void {
  if (typeof window === "undefined") return;
  window.sessionStorage.setItem(SESSION_KEY, JSON.stringify(session));
}

export function loadSession(): AssistantSession | null {
  if (typeof window === "undefined") return null;
  const raw = window.sessionStorage.getItem(SESSION_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as AssistantSession;
  } catch {
    return null;
  }
}

export function clearSession(): void {
  if (typeof window === "undefined") return;
  window.sessionStorage.removeItem(SESSION_KEY);
}
