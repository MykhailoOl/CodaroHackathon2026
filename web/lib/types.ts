
export interface TokenResponse {
  token: string;
  expiresAt: string;
  displayName: string;
}

export interface IntentSpec {
  durationMin: number;
  dayFrom: string;
  dayTo: string;
  timeOfDay: string;
  hardConstraints: string[];
  softConstraints: string[];
  resourceType: string;
  partySize: number;
}

export interface Term {
  key: string;
  label: string;
  delta: number;
  satisfied: boolean;
}

export interface RelaxationTrailEntry {
  action: string;
  detail: string;
  droppedKeys: string[];
}

export interface Suggestion {
  resourceId: number;
  resourceName: string;
  facilityName: string;
  start: string;
  end: string;
  score: number;
  reason: string;
  price: string;
  terms: Term[];
  relaxed: RelaxationTrailEntry[];
}

/**
 * The dates a service may fall between, derived from the facts about the deceased
 * rather than chosen by anyone. Null when the request stated no date of death, in
 * which case the app behaves as an ordinary booking.
 */
export interface ServiceWindow {
  earliest: string;
  latest: string;
  rite: string | null;
  derivation: string[];
  decisionBy: string;
  feasible: boolean;
  note: string | null;
}

export interface ArrangementFacts {
  dateOfDeath: string | null;
  rite: string | null;
  certificateReadyOn: string | null;
  mourners: number | null;
}

export interface IntentSuggestResponse {
  spec: IntentSpec;
  parserUsed: string;
  suggestions: Suggestion[];
  relaxationTrail: RelaxationTrailEntry[];
  window: ServiceWindow | null;
  facts: ArrangementFacts | null;
}

export interface IntentSuggestRequest {
  text: string;
  partySize: number;
}

export interface BookRequest {
  resourceId: number;
  start: string;
  end: string;
  partySize: number;
  paymentMethod: string;
}

export interface BookResponse {
  reservationId: number;
  status: string;
  totalAmount: string;
  message: string;
}

export type DataSource = "live" | "fixture" | "fixture-fallback";
