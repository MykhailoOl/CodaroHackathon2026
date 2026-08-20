"use client";

import { useState } from "react";
import type { BookResponse, DataSource, Suggestion } from "@/lib/types";
import { ApiError, bookSlot } from "@/lib/api";
import { formatTimeRange } from "@/lib/format";
import { ReasonBreakdown } from "./ReasonBreakdown";
import { RelaxationNotice } from "./RelaxationNotice";

type BookingState =
  | { status: "idle" }
  | { status: "loading" }
  | { status: "success"; data: BookResponse }
  | { status: "error"; message: string };

export function SuggestionCard({
  suggestion,
  mourners,
  source,
  primary = false,
  shift,
}: {
  suggestion: Suggestion;
  mourners: number;
  source: DataSource;
  /** The held proposal reads as the offer; alternatives read as alternatives. */
  primary?: boolean;
  /** How far this sits from the held time, in words rather than a score. */
  shift?: string;
}) {
  const [booking, setBooking] = useState<BookingState>({ status: "idle" });

  async function handleConfirm() {
    setBooking({ status: "loading" });
    try {
      const result = await bookSlot(
        {
          resourceId: suggestion.resourceId,
          start: suggestion.start,
          end: suggestion.end,
          partySize: mourners,
          paymentMethod: "INVOICE",
        },
        source
      );
      setBooking({ status: "success", data: result.data });
    } catch (err) {
      const message =
        err instanceof ApiError || err instanceof Error ? err.message : "Nothing was confirmed. Please try again.";
      setBooking({ status: "error", message });
    }
  }

  const hasRelaxation = suggestion.relaxed && suggestion.relaxed.length > 0;

  return (
    <article
      className={
        "flex flex-col gap-3 rounded-xl bg-white p-5 shadow-sm " +
        (primary ? "border-2 border-stone-900" : "border border-stone-300")
      }
    >
      <header className="flex flex-wrap items-start justify-between gap-2">
        <div>
          <h3 className="text-lg font-semibold text-stone-900">{suggestion.resourceName}</h3>
          <p className="text-sm text-stone-500">{suggestion.facilityName}</p>
        </div>
        <div className="text-right">
          <p className="text-lg font-semibold text-stone-900">{suggestion.price}</p>
          <p className="text-xs text-stone-500">{mourners} mourners</p>
        </div>
      </header>

      <p className="text-sm font-medium text-stone-700">
        {formatTimeRange(suggestion.start, suggestion.end)}
        {shift && <span className="ml-2 font-normal text-stone-500">({shift})</span>}
      </p>

      <p className="rounded-lg bg-stone-100 px-4 py-3 text-sm leading-relaxed text-stone-800">
        {suggestion.reason}
      </p>

      {hasRelaxation && <RelaxationNotice entries={suggestion.relaxed} variant="suggestion" />}

      <ReasonBreakdown terms={suggestion.terms} />

      <div className="mt-2 border-t border-stone-200 pt-3">
        {booking.status === "idle" && (
          <button
            type="button"
            onClick={handleConfirm}
            className="w-full rounded-lg bg-stone-900 px-4 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-stone-700"
          >
            Confirm this arrangement
          </button>
        )}

        {booking.status === "loading" && (
          <button
            type="button"
            disabled
            className="w-full rounded-lg bg-stone-400 px-4 py-2.5 text-sm font-semibold text-white"
          >
            Booking…
          </button>
        )}

        {booking.status === "success" && (
          <div className="rounded-lg border border-emerald-300 bg-emerald-50 px-4 py-3 text-sm text-emerald-900">
            <p className="font-semibold">Confirmed — reference {booking.data.reservationId}</p>
            <p className="mt-0.5">{booking.data.totalAmount}</p>
            {booking.data.message && <p className="mt-1 text-emerald-800">{booking.data.message}</p>}
          </div>
        )}

        {booking.status === "error" && (
          <div className="space-y-2">
            <div className="rounded-lg border border-rose-300 bg-rose-50 px-4 py-3 text-sm text-rose-900">
              {booking.message}
            </div>
            <button
              type="button"
              onClick={handleConfirm}
              className="w-full rounded-lg border border-stone-300 px-4 py-2.5 text-sm font-semibold text-stone-700 transition-colors hover:bg-stone-100"
            >
              Try again
            </button>
          </div>
        )}
      </div>
    </article>
  );
}
