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
  partySize,
  source,
}: {
  suggestion: Suggestion;
  partySize: number;
  source: DataSource;
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
          partySize,
          paymentMethod: "CASH",
        },
        source
      );
      setBooking({ status: "success", data: result.data });
    } catch (err) {
      const message =
        err instanceof ApiError || err instanceof Error ? err.message : "Booking failed. Please try again.";
      setBooking({ status: "error", message });
    }
  }

  const hasRelaxation = suggestion.relaxed && suggestion.relaxed.length > 0;

  return (
    <article className="flex flex-col gap-3 rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
      <header className="flex flex-wrap items-start justify-between gap-2">
        <div>
          <h3 className="text-lg font-semibold text-slate-900">{suggestion.resourceName}</h3>
          <p className="text-sm text-slate-500">{suggestion.facilityName}</p>
        </div>
        <div className="text-right">
          <p className="text-lg font-semibold text-slate-900">{suggestion.price}</p>
          <p className="text-xs text-slate-500">score {suggestion.score.toFixed(1)}</p>
        </div>
      </header>

      <p className="text-sm font-medium text-slate-700">{formatTimeRange(suggestion.start, suggestion.end)}</p>

      <p className="rounded-lg bg-indigo-50 px-4 py-3 text-sm font-medium leading-relaxed text-indigo-900">
        {suggestion.reason}
      </p>

      {hasRelaxation && <RelaxationNotice entries={suggestion.relaxed} variant="suggestion" />}

      <ReasonBreakdown terms={suggestion.terms} />

      <div className="mt-2 border-t border-slate-100 pt-3">
        {booking.status === "idle" && (
          <button
            type="button"
            onClick={handleConfirm}
            className="w-full rounded-lg bg-slate-900 px-4 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-slate-700"
          >
            Confirm booking
          </button>
        )}

        {booking.status === "loading" && (
          <button
            type="button"
            disabled
            className="w-full rounded-lg bg-slate-400 px-4 py-2.5 text-sm font-semibold text-white"
          >
            Booking…
          </button>
        )}

        {booking.status === "success" && (
          <div className="rounded-lg border border-emerald-300 bg-emerald-50 px-4 py-3 text-sm text-emerald-900">
            <p className="font-semibold">Reservation #{booking.data.reservationId} — {booking.data.status}</p>
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
              className="w-full rounded-lg border border-slate-300 px-4 py-2.5 text-sm font-semibold text-slate-700 transition-colors hover:bg-slate-50"
            >
              Try again
            </button>
          </div>
        )}
      </div>
    </article>
  );
}
