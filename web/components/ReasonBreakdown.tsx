"use client";

import { useState } from "react";
import type { Term } from "@/lib/types";
import { formatSignedDelta } from "@/lib/format";

export function ReasonBreakdown({ terms }: { terms: Term[] }) {
  const [open, setOpen] = useState(false);

  if (!terms || terms.length === 0) return null;

  const sorted = [...terms].sort((a, b) => Math.abs(b.delta) - Math.abs(a.delta));

  return (
    <div className="mt-3 border-t border-slate-200 pt-3">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
        className="flex items-center gap-1 text-sm font-medium text-slate-600 hover:text-slate-900"
      >
        <span className={`inline-block transition-transform ${open ? "rotate-90" : ""}`}>▸</span>
        {open ? "Hide score breakdown" : "Show score breakdown"}
      </button>

      {open && (
        <ul className="mt-2 space-y-1.5">
          {sorted.map((term) => {
            const positive = term.delta > 0;
            const negative = term.delta < 0;
            return (
              <li
                key={term.key}
                className="flex items-center justify-between gap-3 rounded-md bg-slate-50 px-3 py-1.5 text-sm"
              >
                <span className="text-slate-700">
                  {term.label}
                  {!term.satisfied && (
                    <span className="ml-2 text-xs uppercase tracking-wide text-amber-600">unmet</span>
                  )}
                </span>
                <span
                  className={
                    "shrink-0 font-mono font-semibold tabular-nums " +
                    (positive ? "text-emerald-600" : negative ? "text-rose-600" : "text-slate-500")
                  }
                >
                  {formatSignedDelta(term.delta)}
                </span>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
