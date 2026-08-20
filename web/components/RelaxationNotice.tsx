import type { RelaxationTrailEntry } from "@/lib/types";

export function RelaxationNotice({
  entries,
  variant = "trail",
}: {
  entries: RelaxationTrailEntry[];
  variant?: "trail" | "suggestion";
}) {
  if (!entries || entries.length === 0) return null;

  const heading =
    variant === "trail" ? "Your search was adjusted to find results" : "This result required a trade-off";

  return (
    <div
      className={
        "rounded-lg border px-4 py-3 text-sm " +
        (variant === "trail"
          ? "border-amber-300 bg-amber-50 text-amber-900"
          : "border-amber-200 bg-amber-50/70 text-amber-800")
      }
    >
      <p className="font-semibold">{heading}</p>
      <ul className="mt-1.5 list-disc space-y-1 pl-5">
        {entries.map((entry, i) => (
          <li key={`${entry.action}-${i}`}>{entry.detail}</li>
        ))}
      </ul>
    </div>
  );
}
