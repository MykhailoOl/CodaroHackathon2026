"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { ApiError, suggestIntent } from "@/lib/api";
import { clearAuth, loadAuth } from "@/lib/auth";
import { MournersControl } from "@/components/PartySizeControl";
import { SuggestionCard } from "@/components/SuggestionCard";
import { RelaxationNotice } from "@/components/RelaxationNotice";
import { ServiceWindowPanel } from "@/components/ServiceWindowPanel";
import { DataSourceBadge } from "@/components/DataSourceBadge";
import { DevFixtureToggle } from "@/components/DevFixtureToggle";
import { relativeToFirst } from "@/lib/format";
import type { DataSource, IntentSuggestResponse } from "@/lib/types";

const DEMO_INTENT_PLACEHOLDER =
  process.env.NEXT_PUBLIC_DEMO_INTENT ||
  "my father died yesterday, orthodox service, about 40 mourners";

export default function ComposerPage() {
  const router = useRouter();
  const [displayName, setDisplayName] = useState<string | null>(null);

  const [text, setText] = useState("");
  const [mourners, setMourners] = useState(40);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [response, setResponse] = useState<IntentSuggestResponse | null>(null);
  const [source, setSource] = useState<DataSource | null>(null);
  // Alternatives stay closed until the family says the held time does not work.
  // They are not shopping; showing a grid of funerals by default is the wrong offer.
  const [showAlternatives, setShowAlternatives] = useState(false);

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
    setShowAlternatives(false);
    try {
      const result = await suggestIntent(text.trim(), mourners);
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

  const held = response?.suggestions[0] ?? null;
  const alternatives = response?.suggestions.slice(1) ?? [];
  const effectiveMourners = response?.spec.partySize || mourners;

  return (
    <main className="mx-auto min-h-screen max-w-3xl px-4 py-10">
      <header className="mb-8 flex items-center justify-between">
        <div>
          <h1 className="text-xl font-bold tracking-tight text-stone-900">EverRest</h1>
          {displayName && <p className="text-sm text-stone-500">Signed in as {displayName}</p>}
        </div>
        <div className="flex items-center gap-4">
          <DevFixtureToggle />
          <button
            type="button"
            onClick={handleLogout}
            className="text-sm font-medium text-stone-500 hover:text-stone-900"
          >
            Sign out
          </button>
        </div>
      </header>

      <form
        onSubmit={handleSubmit}
        className="space-y-4 rounded-xl border border-stone-300 bg-white p-6 shadow-sm"
      >
        <div>
          <label htmlFor="intent" className="block text-sm font-medium text-stone-700">
            Tell us what has happened
          </label>
          <p className="mt-1 text-sm text-stone-500">
            In your own words. You do not need to choose a date — we work out when the
            service can be held from the certificate, the observance and the law.
          </p>
          <textarea
            id="intent"
            required
            rows={3}
            placeholder={DEMO_INTENT_PLACEHOLDER}
            value={text}
            onChange={(e) => setText(e.target.value)}
            className="mt-3 w-full resize-none rounded-lg border border-stone-300 px-3 py-2.5 text-base outline-none focus:border-stone-500"
          />
        </div>

        <div className="flex flex-wrap items-center justify-between gap-4">
          <MournersControl value={mourners} onChange={setMourners} />
          <button
            type="submit"
            disabled={loading || !text.trim()}
            className="rounded-lg bg-stone-900 px-6 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-stone-700 disabled:opacity-50"
          >
            {loading ? "Working it out…" : "Propose a time"}
          </button>
        </div>
      </form>

      {error && (
        <div className="mt-4 rounded-lg border border-rose-300 bg-rose-50 px-4 py-3 text-sm text-rose-900">
          {error}
        </div>
      )}

      {response && (
        <section className="mt-8 space-y-6">
          {source && (
            <div className="flex justify-end">
              <DataSourceBadge source={source} />
            </div>
          )}

          {response.window && <ServiceWindowPanel window={response.window} />}

          <RelaxationNotice entries={response.relaxationTrail} variant="trail" />

          {!held ? (
            <p className="rounded-lg border border-stone-300 bg-white px-4 py-6 text-center text-sm leading-relaxed text-stone-600">
              {response.window
                ? "Nothing is free inside that window. The dates cannot be moved, so this needs a member of staff — please call the funeral home."
                : "Nothing matched. Try describing the circumstances, including when the death occurred."}
            </p>
          ) : (
            <>
              <div>
                <h2 className="mb-3 text-base font-semibold text-stone-900">We are holding</h2>
                <SuggestionCard
                  suggestion={held}
                  mourners={effectiveMourners}
                  source={source ?? "fixture"}
                  primary
                />
              </div>

              {alternatives.length > 0 && !showAlternatives && (
                <button
                  type="button"
                  onClick={() => setShowAlternatives(true)}
                  className="w-full rounded-lg border border-stone-300 bg-white px-4 py-2.5 text-sm font-semibold text-stone-700 transition-colors hover:bg-stone-100"
                >
                  This doesn&rsquo;t work
                </button>
              )}

              {alternatives.length > 0 && showAlternatives && (
                <div>
                  <h2 className="mb-1 text-base font-semibold text-stone-900">
                    Other dates the window allows
                  </h2>
                  <p className="mb-3 text-sm text-stone-500">
                    Every one of these is inside the window; none of them can be moved outside it.
                  </p>
                  <div className="space-y-4">
                    {alternatives.map((s) => (
                      <SuggestionCard
                        key={s.resourceId + s.start}
                        suggestion={s}
                        mourners={effectiveMourners}
                        source={source ?? "fixture"}
                        shift={relativeToFirst(held.start, s.start)}
                      />
                    ))}
                  </div>
                </div>
              )}
            </>
          )}
        </section>
      )}
    </main>
  );
}
