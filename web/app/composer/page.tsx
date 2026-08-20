"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { ApiError, suggestIntent } from "@/lib/api";
import { clearAuth, loadAuth } from "@/lib/auth";
import { PartySizeControl } from "@/components/PartySizeControl";
import { SuggestionCard } from "@/components/SuggestionCard";
import { RelaxationNotice } from "@/components/RelaxationNotice";
import { DataSourceBadge } from "@/components/DataSourceBadge";
import { DevFixtureToggle } from "@/components/DevFixtureToggle";
import type { DataSource, IntentSuggestResponse } from "@/lib/types";

export default function ComposerPage() {
  const router = useRouter();
  const [displayName, setDisplayName] = useState<string | null>(null);

  const [text, setText] = useState("");
  const [partySize, setPartySize] = useState(2);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [response, setResponse] = useState<IntentSuggestResponse | null>(null);
  const [source, setSource] = useState<DataSource | null>(null);

  useEffect(() => {
    const auth = loadAuth();
    if (!auth) {
      router.replace("/");
      return;
    }
    setDisplayName(auth.displayName || null);
  }, [router]);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!text.trim()) return;
    setLoading(true);
    setError(null);
    try {
      const result = await suggestIntent(text.trim(), partySize);
      setResponse(result.data);
      setSource(result.source);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setLoading(false);
    }
  }

  function handleLogout() {
    clearAuth();
    router.push("/");
  }

  return (
    <main className="mx-auto min-h-screen max-w-3xl px-4 py-10">
      <header className="mb-8 flex items-center justify-between">
        <div>
          <h1 className="text-xl font-bold text-slate-900">Intent Booking</h1>
          {displayName && <p className="text-sm text-slate-500">Signed in as {displayName}</p>}
        </div>
        <div className="flex items-center gap-4">
          <DevFixtureToggle />
          <button
            type="button"
            onClick={handleLogout}
            className="text-sm font-medium text-slate-500 hover:text-slate-900"
          >
            Sign out
          </button>
        </div>
      </header>

      <form onSubmit={handleSubmit} className="space-y-4 rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
        <div>
          <label htmlFor="intent" className="block text-sm font-medium text-slate-700">
            What do you want to book?
          </label>
          <textarea
            id="intent"
            required
            rows={3}
            placeholder="e.g. tennis for two tomorrow evening, outdoor"
            value={text}
            onChange={(e) => setText(e.target.value)}
            className="mt-1 w-full resize-none rounded-lg border border-slate-300 px-3 py-2.5 text-base outline-none focus:border-slate-500"
          />
        </div>

        <div className="flex flex-wrap items-center justify-between gap-4">
          <PartySizeControl value={partySize} onChange={setPartySize} />
          <button
            type="submit"
            disabled={loading || !text.trim()}
            className="rounded-lg bg-slate-900 px-6 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-slate-700 disabled:opacity-50"
          >
            {loading ? "Finding slots…" : "Find slots"}
          </button>
        </div>
      </form>

      {error && (
        <div className="mt-4 rounded-lg border border-rose-300 bg-rose-50 px-4 py-3 text-sm text-rose-900">
          {error}
        </div>
      )}

      {response && (
        <section className="mt-8 space-y-4">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h2 className="text-lg font-semibold text-slate-900">
              {response.suggestions.length} suggestion{response.suggestions.length === 1 ? "" : "s"}
            </h2>
            {source && <DataSourceBadge source={source} />}
          </div>

          <RelaxationNotice entries={response.relaxationTrail} variant="trail" />

          {response.suggestions.length === 0 ? (
            <p className="rounded-lg border border-slate-200 bg-white px-4 py-6 text-center text-sm text-slate-500">
              No slots matched, even after relaxing constraints. Try a wider time window or a different resource.
            </p>
          ) : (
            <div className="grid gap-4 sm:grid-cols-2">
              {response.suggestions.map((s) => (
                <SuggestionCard
                  key={s.resourceId + s.start}
                  suggestion={s}
                  partySize={response.spec.partySize || partySize}
                  source={source ?? "fixture"}
                />
              ))}
            </div>
          )}
        </section>
      )}
    </main>
  );
}
