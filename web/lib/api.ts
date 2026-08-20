import { FIXTURE_SUGGEST_RESPONSE, FIXTURE_TOKEN_RESPONSE, fixtureBook } from "./fixtures";
import { getToken } from "./auth";
import type {
  BookRequest,
  BookResponse,
  DataSource,
  IntentSuggestResponse,
  TokenResponse,
} from "./types";

export const API_BASE = process.env.NEXT_PUBLIC_API_BASE || "http://localhost:8080";

const FORCE_FIXTURES_ENV = process.env.NEXT_PUBLIC_USE_FIXTURES === "true";

const DEV_TOGGLE_KEY = "codaro.devUseFixtures";

export function getDevFixtureToggle(): boolean {
  if (typeof window === "undefined") return false;
  return window.localStorage.getItem(DEV_TOGGLE_KEY) === "true";
}

export function setDevFixtureToggle(value: boolean): void {
  if (typeof window === "undefined") return;
  window.localStorage.setItem(DEV_TOGGLE_KEY, value ? "true" : "false");
}

export function fixturesForced(): boolean {
  return FORCE_FIXTURES_ENV || getDevFixtureToggle();
}

export class ApiError extends Error {
  status?: number;
  constructor(message: string, status?: number) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}

async function readErrorMessage(res: Response, fallback: string): Promise<string> {
  try {
    const body = await res.clone().json();
    if (typeof body?.message === "string" && body.message.trim()) return body.message;
    if (typeof body?.error === "string" && body.error.trim()) return body.error;
  } catch {
  }
  return fallback;
}

function authHeaders(): HeadersInit {
  const token = getToken();
  return token ? { Authorization: `Bearer ${token}` } : {};
}


export interface LoginResult {
  data: TokenResponse;
  source: DataSource;
}

export async function login(username: string, password: string): Promise<LoginResult> {
  if (fixturesForced()) {
    return {
      data: { ...FIXTURE_TOKEN_RESPONSE, displayName: username || FIXTURE_TOKEN_RESPONSE.displayName },
      source: "fixture",
    };
  }

  let res: Response;
  try {
    res = await fetch(`${API_BASE}/api/auth/token`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username, password }),
    });
  } catch {
    return {
      data: { ...FIXTURE_TOKEN_RESPONSE, displayName: username || FIXTURE_TOKEN_RESPONSE.displayName },
      source: "fixture-fallback",
    };
  }

  if (res.status === 401) {
    throw new ApiError("Invalid username or password.", 401);
  }
  if (!res.ok) {
    const message = await readErrorMessage(res, `Login failed (${res.status}).`);
    throw new ApiError(message, res.status);
  }

  const data: TokenResponse = await res.json();
  return { data, source: "live" };
}


export interface SuggestResult {
  data: IntentSuggestResponse;
  source: DataSource;
}

export async function suggestIntent(text: string, partySize: number): Promise<SuggestResult> {
  if (fixturesForced()) {
    return { data: FIXTURE_SUGGEST_RESPONSE, source: "fixture" };
  }

  try {
    const res = await fetch(`${API_BASE}/api/intent/suggest`, {
      method: "POST",
      headers: { "Content-Type": "application/json", ...authHeaders() },
      body: JSON.stringify({ text, partySize }),
    });
    if (!res.ok) {
      throw new ApiError(await readErrorMessage(res, `Request failed (${res.status}).`), res.status);
    }
    const data: IntentSuggestResponse = await res.json();
    return { data, source: "live" };
  } catch {
    return { data: FIXTURE_SUGGEST_RESPONSE, source: "fixture-fallback" };
  }
}


export interface BookResult {
  data: BookResponse;
  source: DataSource;
}

export async function bookSlot(payload: BookRequest, suggestionSource: DataSource): Promise<BookResult> {
  if (suggestionSource !== "live") {
    const data = await fixtureBook(payload.resourceId, payload.start);
    return { data, source: suggestionSource };
  }

  const res = await fetch(`${API_BASE}/api/intent/book`, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...authHeaders() },
    body: JSON.stringify(payload),
  });
  if (!res.ok) {
    throw new ApiError(await readErrorMessage(res, `Booking failed (${res.status}).`), res.status);
  }
  const data: BookResponse = await res.json();
  return { data, source: "live" };
}
