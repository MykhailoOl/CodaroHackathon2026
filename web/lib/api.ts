import type {
  AssistantHome,
  AssistantPreview,
  AssistantSession,
  AssistantVenue,
  ArrangementCreated,
  ArrangementRequest,
} from "./types";

// Everything goes through this origin: next.config.mjs rewrites /api, /login and /logout
// to the Spring app. Same-origin is what lets the session cookie and the CSRF token work
// without any CORS negotiation — the API authenticates a browser session, not a token.

export class ApiError extends Error {
  status?: number;
  field?: string;
  constructor(message: string, status?: number, field?: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.field = field;
  }
}

const CSRF_KEY = "everrest.csrf";
const CSRF_HEADER_KEY = "everrest.csrfHeader";

function rememberCsrf(token: string, header: string): void {
  window.sessionStorage.setItem(CSRF_KEY, token);
  window.sessionStorage.setItem(CSRF_HEADER_KEY, header);
}

function csrfHeaders(): Record<string, string> {
  if (typeof window === "undefined") return {};
  const token = window.sessionStorage.getItem(CSRF_KEY);
  const header = window.sessionStorage.getItem(CSRF_HEADER_KEY) || "X-CSRF-TOKEN";
  return token ? { [header]: token } : {};
}

export function clearCsrf(): void {
  if (typeof window === "undefined") return;
  window.sessionStorage.removeItem(CSRF_KEY);
  window.sessionStorage.removeItem(CSRF_HEADER_KEY);
}

/** Pull the CSRF token out of the rendered Spring login form before the POST. */
function scrapeCsrf(html: string): { token: string; header: string } | null {
  const meta = html.match(/<meta name="_csrf" content="([^"]+)"/);
  const metaHeader = html.match(/<meta name="_csrf_header" content="([^"]+)"/);
  if (meta) return { token: meta[1], header: metaHeader?.[1] || "X-CSRF-TOKEN" };
  const input = html.match(/name="_csrf"\s+value="([^"]+)"/);
  return input ? { token: input[1], header: "X-CSRF-TOKEN" } : null;
}

async function readError(res: Response, fallback: string): Promise<ApiError> {
  try {
    const body = await res.clone().json();
    const message = body?.message || body?.error;
    if (typeof message === "string" && message.trim()) {
      return new ApiError(message, res.status, body?.field);
    }
  } catch {
    // Not JSON — fall through to the generic message.
  }
  return new ApiError(fallback, res.status);
}

async function getJson<T>(path: string): Promise<T> {
  const res = await fetch(path, { credentials: "same-origin", headers: csrfHeaders() });
  if (!res.ok) throw await readError(res, `Request failed (${res.status}).`);
  return (await res.json()) as T;
}

async function postJson<T>(path: string, payload: unknown): Promise<T> {
  const res = await fetch(path, {
    method: "POST",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json", ...csrfHeaders() },
    body: JSON.stringify(payload),
  });
  if (!res.ok) throw await readError(res, `Request failed (${res.status}).`);
  return (await res.json()) as T;
}

/** Sign in the way the browser does: the login form's token, the form POST, then the
 *  token the API wants on writes. Spring answers the POST with a redirect to the API's
 *  own origin, which sits outside the proxy — following it would leave same-origin and
 *  die on the missing CORS headers. So the POST is never followed; the Set-Cookie on
 *  its redirect is what matters, and the proxied session endpoint is the verdict. */
export async function login(username: string, password: string): Promise<AssistantSession> {
  const page = await fetch("/login", { credentials: "same-origin" });
  const formToken = scrapeCsrf(await page.text());
  if (!formToken) throw new ApiError("The sign-in form could not be loaded.");

  const body = new URLSearchParams({ username, password, _csrf: formToken.token });
  await fetch("/login", {
    method: "POST",
    credentials: "same-origin",
    redirect: "manual",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body,
  });

  let session: AssistantSession;
  try {
    session = await getSession();
  } catch (err) {
    if (err instanceof ApiError && err.status === 401) {
      throw new ApiError("Invalid username or password.", 401);
    }
    throw err;
  }
  if (!session.authenticated) {
    throw new ApiError("Invalid username or password.", 401);
  }
  if (session.csrfToken) rememberCsrf(session.csrfToken, "X-CSRF-TOKEN");
  return session;
}

export async function logout(): Promise<void> {
  await fetch("/logout", {
    method: "POST",
    credentials: "same-origin",
    redirect: "manual",
    headers: { "Content-Type": "application/x-www-form-urlencoded", ...csrfHeaders() },
    body: new URLSearchParams(),
  }).catch(() => undefined);
  clearCsrf();
}

export function getSession(): Promise<AssistantSession> {
  return getJson<AssistantSession>("/api/reservation-assistant/session");
}

export function getHomes(): Promise<AssistantHome[]> {
  return getJson<AssistantHome[]>("/api/reservation-assistant/homes");
}

export function getVenues(homeId: number): Promise<AssistantVenue[]> {
  return getJson<AssistantVenue[]>(`/api/reservation-assistant/homes/${homeId}/venues`);
}

export function previewArrangement(request: ArrangementRequest): Promise<AssistantPreview> {
  return postJson<AssistantPreview>("/api/reservation-assistant/preview", request);
}

export function createArrangement(request: ArrangementRequest): Promise<ArrangementCreated> {
  return postJson<ArrangementCreated>("/api/reservation-assistant/arrangements", request);
}

/** Which spaces each service can be held in. The server is the authority — it refuses a
 *  mismatch on /preview — and this mirrors ServiceType.allows(VenueType) only so a
 *  family is never offered a space that would then be refused. */
export const SERVICE_VENUE_TYPES: Record<string, string[]> = {
  BURIAL_CEREMONY: ["CHAPEL", "CEREMONY_HALL", "MEMORIAL_GARDEN"],
  CREMATION_CEREMONY: ["CREMATORIUM", "CHAPEL"],
  MEMORIAL_SERVICE: ["CHAPEL", "CEREMONY_HALL", "MEMORIAL_GARDEN", "RECEPTION_HALL"],
  FAREWELL_CEREMONY: ["CHAPEL", "CEREMONY_HALL", "MEMORIAL_GARDEN"],
};

export function venueFits(venue: AssistantVenue, serviceType: string, attendees: number): boolean {
  if (attendees && venue.maxAttendees < attendees) return false;
  const allowed = SERVICE_VENUE_TYPES[serviceType];
  return !allowed || allowed.includes(venue.venueType);
}
