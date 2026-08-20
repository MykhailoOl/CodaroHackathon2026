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
