import type { BookResponse, IntentSuggestResponse, TokenResponse } from "./types";

export const FIXTURE_TOKEN_RESPONSE: TokenResponse = {
  token: "fixture-demo-token",
  expiresAt: "2026-08-20T20:00:00",
  displayName: "Demo User",
};

export const FIXTURE_SUGGEST_RESPONSE: IntentSuggestResponse = {
  spec: {
    durationMin: 60,
    dayFrom: "2026-08-21",
    dayTo: "2026-08-21",
    timeOfDay: "EVENING",
    hardConstraints: ["outdoor"],
    softConstraints: [],
    resourceType: "TENNIS",
    partySize: 2,
  },
  parserUsed: "rules",
  suggestions: [
    {
      resourceId: 3,
      resourceName: "Court 2",
      facilityName: "Riverside",
      start: "2026-08-21T18:00:00",
      end: "2026-08-21T19:00:00",
      score: 72.5,
      reason: "Evening slot on Court 2, 30 min clear before your next booking",
      price: "80.00 PLN",
      terms: [
        { key: "timeOfDay", label: "evening", delta: 18.0, satisfied: true },
        { key: "buffer", label: "30 min buffer before next booking", delta: 6.5, satisfied: true },
        { key: "resourceLoad", label: "court lightly booked today", delta: 4.0, satisfied: true },
      ],
      relaxed: [],
    },
    {
      resourceId: 7,
      resourceName: "Court 1",
      facilityName: "Riverside",
      start: "2026-08-21T19:30:00",
      end: "2026-08-21T20:30:00",
      score: 65.0,
      reason: "Closest outdoor evening slot once Court 2 was full at 18:00",
      price: "80.00 PLN",
      terms: [
        { key: "timeOfDay", label: "evening", delta: 15.0, satisfied: true },
        { key: "buffer", label: "10 min buffer before next booking", delta: -3.0, satisfied: false },
        { key: "resourceLoad", label: "court moderately booked today", delta: 1.5, satisfied: true },
      ],
      relaxed: [],
    },
    {
      resourceId: 12,
      resourceName: "Court 5 (Indoor)",
      facilityName: "Northside Sports Hall",
      start: "2026-08-21T18:30:00",
      end: "2026-08-21T19:30:00",
      score: 51.5,
      reason: "No outdoor courts free Tue evening, so we widened to an indoor court nearby",
      price: "95.00 PLN",
      terms: [
        { key: "timeOfDay", label: "evening", delta: 18.0, satisfied: true },
        { key: "hardConstraint:outdoor", label: "outdoor requested, indoor offered", delta: -12.0, satisfied: false },
        { key: "price", label: "higher than average court rate", delta: -5.0, satisfied: false },
        { key: "buffer", label: "45 min buffer before next booking", delta: 2.0, satisfied: true },
      ],
      relaxed: [
        {
          action: "DROP_HARD_CONSTRAINT",
          detail: "No outdoor court available Tue 18:00-20:00 — showing indoor Court 5 instead",
          droppedKeys: ["outdoor"],
        },
      ],
    },
  ],
  relaxationTrail: [
    {
      action: "WIDEN_DAY_WINDOW",
      detail: "No outdoor court on Tue — widened search to Wed as a fallback",
      droppedKeys: [],
    },
    {
      action: "DROP_HARD_CONSTRAINT",
      detail: "Outdoor requirement dropped for one suggestion because Tue evening was fully booked outdoors",
      droppedKeys: ["outdoor"],
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
    message: "Reservation held — awaiting confirmation.",
  };
}
