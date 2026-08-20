"use client";

import type { ServiceWindow } from "@/lib/types";
import { formatDay, formatDateTime, humanDelta } from "@/lib/format";

/**
 * The derivation, shown above the proposal rather than beside it.
 *
 * A family is being told they cannot pick a date. The reasoning is not a detail to
 * tuck behind a disclosure — it is the answer to the only question they have, so it
 * reads first and in full.
 */
export function ServiceWindowPanel({ window: w }: { window: ServiceWindow }) {
  const remaining = humanDelta(w.decisionBy);

  return (
    <section className="rounded-xl border border-stone-300 bg-white p-6 shadow-sm">
      <h2 className="text-base font-semibold text-stone-900">
        The dates this can fall between
      </h2>

      <dl className="mt-4 grid grid-cols-[auto_1fr] gap-x-6 gap-y-2 text-sm">
        <dt className="text-stone-500">Earliest</dt>
        <dd className="font-semibold text-stone-900">{formatDay(w.earliest)}</dd>
        <dt className="text-stone-500">Latest</dt>
        <dd className="font-semibold text-stone-900">{formatDay(w.latest)}</dd>
        {w.rite && (
          <>
            <dt className="text-stone-500">Observance</dt>
            <dd className="font-semibold text-stone-900">
              {w.rite.charAt(0) + w.rite.slice(1).toLowerCase()}
            </dd>
          </>
        )}
      </dl>

      <ul className="mt-5 space-y-2 border-t border-stone-200 pt-4">
        {w.derivation.map((reason) => (
          <li key={reason} className="flex gap-2.5 text-sm leading-relaxed text-stone-700">
            <span aria-hidden="true" className="mt-2 h-1 w-1 shrink-0 rounded-full bg-stone-400" />
            <span>{reason}</span>
          </li>
        ))}
        {w.note && (
          <li className="flex gap-2.5 text-sm leading-relaxed text-stone-700">
            <span aria-hidden="true" className="mt-2 h-1 w-1 shrink-0 rounded-full bg-stone-400" />
            <span>{w.note}</span>
          </li>
        )}
      </ul>

      {!w.feasible && (
        <p className="mt-4 rounded-lg border border-amber-300 bg-amber-50 px-4 py-3 text-sm leading-relaxed text-amber-900">
          <span className="font-semibold">This window cannot be met.</span> The service is
          placed as early as the release allows. Please speak to the funeral director — an
          extension of the statutory period has to be filed by hand.
        </p>
      )}

      {remaining && (
        <p className="mt-4 text-sm text-stone-600">
          A decision is needed by{" "}
          <span className="font-semibold text-stone-900">{formatDateTime(w.decisionBy)}</span>{" "}
          ({remaining}) for the venue to hold it.
        </p>
      )}
    </section>
  );
}
