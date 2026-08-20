"use client";

import { useState } from "react";
import type { Term } from "@/lib/types";

export function ReasonBreakdown({ terms }: { terms: Term[] }) {
  const [open, setOpen] = useState(false);

  if (!terms || terms.length === 0) return null;

  const sorted = [...terms].sort((a, b) => Math.abs(b.delta) - Math.abs(a.delta));

  return (
    <div className="mt-3 border-t border-stone-200 pt-3">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
        className="flex items-center gap-1 text-sm font-medium text-stone-600 hover:text-stone-900"
      >
        <span className={`inline-block transition-transform ${open ? "rotate-90" : ""}`}>▸</span>
        {open ? "Hide the reasoning" : "Why this time"}
      </button>

      {open && (
        <ul className="mt-2 space-y-1.5">
          {sorted.map((term) => {
            const positive = term.delta > 0;
            const negative = term.delta < 0;
            return (
              <li
                key={term.key}
                className="flex items-center justify-between gap-3 rounded-md bg-stone-50 px-3 py-1.5 text-sm"
              >
                <span className="text-stone-700">
                  {term.label}
                  {!term.satisfied && (
                    <span className="ml-2 text-xs uppercase tracking-wide text-amber-600">unmet</span>
                  )}
                </span>
                {/* The numeric score is an internal ranking artefact; a family is
                    owed the reason, not the arithmetic. */}
                <span
                  aria-hidden="true"
                  className={
                    "h-2 w-2 shrink-0 rounded-full " +
                    (positive ? "bg-emerald-500" : negative ? "bg-rose-500" : "bg-stone-300")
                  }
                />
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
