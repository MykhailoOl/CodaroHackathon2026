export function formatDay(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleDateString(undefined, { weekday: "short", month: "short", day: "numeric" });
}

export function formatTime(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleTimeString(undefined, { hour: "numeric", minute: "2-digit" });
}

export function formatTimeRange(startIso: string, endIso: string): string {
  return `${formatDay(startIso)}, ${formatTime(startIso)}–${formatTime(endIso)}`;
}

export function formatSignedDelta(delta: number): string {
  const rounded = Math.round(delta * 10) / 10;
  const sign = rounded > 0 ? "+" : rounded < 0 ? "" : "±";
  return `${sign}${rounded}`;
}

export function formatDateTime(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return `${formatDay(iso)}, ${formatTime(iso)}`;
}

/** "in about 5 hours" — time a person can act on, not a raw countdown. */
export function humanDelta(iso: string): string | null {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return null;
  const seconds = (d.getTime() - Date.now()) / 1000;
  if (seconds <= 0) return "now";
  const hours = Math.floor(seconds / 3600);
  if (hours >= 48) return `in about ${Math.floor(hours / 24)} days`;
  if (hours >= 2) return `in about ${hours} hours`;
  return `in about ${Math.max(1, Math.floor(seconds / 60))} minutes`;
}

/**
 * How far an alternative sits from the held proposal, in words rather than a score.
 * A family comparing funeral times needs the cost of the change, not a ranking.
 */
export function relativeToFirst(firstStart: string, otherStart: string): string {
  const a = new Date(firstStart);
  const b = new Date(otherStart);
  if (Number.isNaN(a.getTime()) || Number.isNaN(b.getTime())) return "";
  const dayShift = Math.round(
    (new Date(b.getFullYear(), b.getMonth(), b.getDate()).getTime() -
      new Date(a.getFullYear(), a.getMonth(), a.getDate()).getTime()) /
      86400000
  );
  if (dayShift === 0) {
    const hours = Math.round((b.getTime() - a.getTime()) / 3600000);
    if (hours === 0) return "same time, another venue";
    return `same day, ${Math.abs(hours)} hours ${hours > 0 ? "later" : "earlier"}`;
  }
  if (Math.abs(dayShift) === 1) return dayShift > 0 ? "one day later" : "one day earlier";
  return `${Math.abs(dayShift)} days ${dayShift > 0 ? "later" : "earlier"}`;
}
