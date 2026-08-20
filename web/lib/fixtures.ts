import type { BookResponse, IntentSuggestResponse, TokenResponse } from "./types";

export const FIXTURE_TOKEN_RESPONSE: TokenResponse = {
  token: "fixture-demo-token",
  expiresAt: "2026-08-20T20:00:00",
  displayName: "Demo User",
};

export const FIXTURE_SUGGEST_RESPONSE: IntentSuggestResponse = {
  spec: {
    durationMin: 60,
    dayFrom: "2026-08-20",
    dayTo: "2026-08-22",
    timeOfDay: "ANY",
    hardConstraints: ["religious"],
    softConstraints: [],
    resourceType: "CHAPEL",
    partySize: 40,
  },
  parserUsed: "rules",
  facts: {
    dateOfDeath: "2026-08-19",
    rite: "ORTHODOX",
    certificateReadyOn: "2026-08-20",
    mourners: 40,
  },
  window: {
    earliest: "2026-08-20",
    latest: "2026-08-22",
    rite: "ORTHODOX",
    derivation: [
      "Death recorded Wed 19 Aug.",
      "Death certificate expected Thu 20 Aug — nothing can be scheduled before it is released.",
      "Orthodox rite — within three days. That puts the service on or before Sat 22 Aug.",
    ],
    decisionBy: "2026-08-21T21:00:00",
    feasible: true,
    note: null,
  },
  suggestions: [
    {
      resourceId: 3,
      resourceName: "Orthodox Chapel",
      facilityName: "Cmentarz Prawosławny na Woli",
      start: "2026-08-22T11:00:00",
      end: "2026-08-22T12:00:00",
      score: 72.5,
      reason: "The only chapel free inside the window with room for forty mourners.",
      price: "900.00 PLN",
      terms: [
        { key: "religious", label: "Orthodox rite observed", delta: 18.0, satisfied: true },
        { key: "dayProximity", label: "inside the burial window", delta: 12.0, satisfied: true },
        { key: "resourceLoad", label: "chapel otherwise quiet that morning", delta: 4.0, satisfied: true },
      ],
      relaxed: [],
    },
    {
      resourceId: 7,
      resourceName: "Gate Chapel",
      facilityName: "Cmentarz Bródnowski",
      start: "2026-08-21T14:00:00",
      end: "2026-08-21T15:00:00",
      score: 65.0,
      reason: "A day earlier, at a cemetery on the other side of the river.",
      price: "900.00 PLN",
      terms: [
        { key: "religious", label: "Orthodox rite observed", delta: 15.0, satisfied: true },
        { key: "buffer", label: "another service ends 30 min before", delta: -3.0, satisfied: false },
      ],
      relaxed: [],
    },
    {
      resourceId: 12,
      resourceName: "Great Chapel",
      facilityName: "Cmentarz Południowy",
      start: "2026-08-22T15:30:00",
      end: "2026-08-22T16:30:00",
      score: 51.5,
      reason: "Same day, later, but the drive out to Antoninów is forty minutes.",
      price: "900.00 PLN",
      terms: [
        { key: "dayProximity", label: "last day the rite allows", delta: 8.0, satisfied: true },
        { key: "resourceLoad", label: "busy afternoon at the southern cemetery", delta: -5.0, satisfied: false },
      ],
      relaxed: [],
    },
  ],
  relaxationTrail: [
    {
      action: "WIDEN_DAY_WINDOW",
      detail: "No chapel free on Thu — searched the rest of the window through Sat 22 Aug",
      droppedKeys: [],
    },
  ],
};

let fixtureReservationCounter = 41;
const fixtureBookedSlots = new Set<string>();

export async function fixtureBook(resourceId: number, start: string): Promise<BookResponse> {
  await new Promise((resolve) => setTimeout(resolve, 500));

  const key = `${resourceId}@${start}`;
  if (fixtureBookedSlots.has(key)) {
    const error = new Error(
      "This slot was just booked by someone else. Please pick another suggestion."
    );
    (error as Error & { status?: number }).status = 409;
    throw error;
  }

  fixtureBookedSlots.add(key);
  fixtureReservationCounter += 1;

  const suggestion = FIXTURE_SUGGEST_RESPONSE.suggestions.find((s) => s.resourceId === resourceId);

  return {
    reservationId: fixtureReservationCounter,
    status: "PENDING",
    totalAmount: suggestion?.price ?? "0.00 PLN",
    message: "Held. The funeral home has been notified and will be in touch about the rest.",
  };
}
