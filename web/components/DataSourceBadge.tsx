import type { DataSource } from "@/lib/types";

const LABELS: Record<DataSource, string> = {
  live: "Live API",
  fixture: "Demo data",
  "fixture-fallback": "Demo data (API unreachable)",
};

const STYLES: Record<DataSource, string> = {
  live: "bg-emerald-100 text-emerald-800",
  fixture: "bg-slate-200 text-slate-700",
  "fixture-fallback": "bg-amber-100 text-amber-800",
};

export function DataSourceBadge({ source }: { source: DataSource }) {
  return (
    <span className={`inline-flex items-center rounded-full px-2.5 py-1 text-xs font-semibold ${STYLES[source]}`}>
      {LABELS[source]}
    </span>
  );
}
