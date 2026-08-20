"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { ApiError, login } from "@/lib/api";
import { loadAuth, saveAuth } from "@/lib/auth";
import { DataSourceBadge } from "@/components/DataSourceBadge";
import { DevFixtureToggle } from "@/components/DevFixtureToggle";
import type { DataSource } from "@/lib/types";

export default function LoginPage() {
  const router = useRouter();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [source, setSource] = useState<DataSource | null>(null);

  useEffect(() => {
    if (loadAuth()) {
      router.replace("/composer");
    }
  }, [router]);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const result = await login(username.trim(), password);
      saveAuth(result.data);
      setSource(result.source);
      router.push("/composer");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="flex min-h-screen items-center justify-center px-4">
      <div className="w-full max-w-sm">
        <div className="mb-8 text-center">
          <h1 className="text-2xl font-bold text-slate-900">Intent Booking</h1>
          <p className="mt-1 text-sm text-slate-500">Sign in to describe what you want to book.</p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4 rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
          <div>
            <label htmlFor="username" className="block text-sm font-medium text-slate-700">
              Username
            </label>
            <input
              id="username"
              type="text"
              required
              autoComplete="username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-500"
            />
          </div>

          <div>
            <label htmlFor="password" className="block text-sm font-medium text-slate-700">
              Password
            </label>
            <input
              id="password"
              type="password"
              required
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-500"
            />
          </div>

          {error && (
            <div className="rounded-lg border border-rose-300 bg-rose-50 px-3 py-2 text-sm text-rose-900">
              {error}
            </div>
          )}

          <button
            type="submit"
            disabled={loading}
            className="w-full rounded-lg bg-slate-900 px-4 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-slate-700 disabled:opacity-50"
          >
            {loading ? "Signing in…" : "Sign in"}
          </button>

          <div className="flex items-center justify-between pt-1">
            <DevFixtureToggle />
            {source && <DataSourceBadge source={source} />}
          </div>
        </form>

        <p className="mt-4 text-center text-xs text-slate-400">
          No backend running? Check &ldquo;Use demo data&rdquo; above, or just submit — a network error
          falls back to demo data automatically.
        </p>
      </div>
    </main>
  );
}
