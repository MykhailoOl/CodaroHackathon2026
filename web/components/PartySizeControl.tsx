"use client";

export function MournersControl({
  value,
  onChange,
  min = 1,
  max = 200,
}: {
  value: number;
  onChange: (next: number) => void;
  min?: number;
  max?: number;
}) {
  function clamp(n: number) {
    return Math.min(max, Math.max(min, n));
  }

  return (
    <div className="flex items-center gap-3">
      <span className="text-sm font-medium text-stone-700">Mourners expected</span>
      <div className="flex items-center rounded-lg border border-stone-300">
        <button
          type="button"
          onClick={() => onChange(clamp(value - 1))}
          disabled={value <= min}
          className="px-3 py-2 text-lg font-semibold text-stone-600 disabled:opacity-30"
          aria-label="Fewer mourners"
        >
          −
        </button>
        <input
          type="number"
          inputMode="numeric"
          value={value}
          min={min}
          max={max}
          onChange={(e) => onChange(clamp(Number(e.target.value) || min))}
          className="w-12 border-x border-stone-300 py-2 text-center text-sm font-semibold text-stone-900 outline-none"
        />
        <button
          type="button"
          onClick={() => onChange(clamp(value + 1))}
          disabled={value >= max}
          className="px-3 py-2 text-lg font-semibold text-stone-600 disabled:opacity-30"
          aria-label="More mourners"
        >
          +
        </button>
      </div>
    </div>
  );
}
