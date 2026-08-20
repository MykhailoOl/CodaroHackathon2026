"use client";

import { useEffect, useState } from "react";
import { getDevFixtureToggle, setDevFixtureToggle } from "@/lib/api";

export function DevFixtureToggle() {
  const [checked, setChecked] = useState(false);
  const envForced = process.env.NEXT_PUBLIC_USE_FIXTURES === "true";

  useEffect(() => {
    setChecked(getDevFixtureToggle() || envForced);
  }, [envForced]);

  return (
    <label className="flex items-center gap-2 text-xs text-slate-500 select-none">
      <input
        type="checkbox"
        checked={checked}
        disabled={envForced}
        onChange={(e) => {
          setDevFixtureToggle(e.target.checked);
          setChecked(e.target.checked);
        }}
        className="h-3.5 w-3.5 rounded border-slate-300"
      />
      Use demo data{envForced ? " (forced by env)" : ""}
    </label>
  );
}
