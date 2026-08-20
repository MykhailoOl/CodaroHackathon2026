"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { PageShell } from "@/components/PageShell";
import { ApiError, login } from "@/lib/api";
import { loadSession, saveSession } from "@/lib/auth";

export default function LoginPage() {
  const router = useRouter();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (loadSession()) {
      router.replace("/composer");
    }
  }, [router]);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      saveSession(await login(username.trim(), password));
      router.push("/composer");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Those credentials do not match our records.");
    } finally {
      setLoading(false);
    }
  }

  const field =
    "mt-1 w-full rounded-md border border-stone-400 bg-white px-3 py-3 text-sm outline-none focus:border-forest";

  return (
    <PageShell>
      <div className="mx-auto grid w-full max-w-6xl flex-1 grid-cols-1 items-stretch lg:grid-cols-2">
        <section className="flex flex-col justify-center px-6 py-16 sm:px-12">
          <p className="text-xs tracking-[0.16em] text-stone-500 uppercase">Welcome</p>
          <h1 className="font-display mt-2 text-5xl leading-none text-charcoal">Sign in to EverRest</h1>
          <p className="mt-4 max-w-md text-base leading-relaxed text-stone-600">
            I&apos;m sorry for your loss. You do not choose a date. Tell us the circumstances
            and the funeral home settles when the service can be held.
          </p>
          <form onSubmit={handleSubmit} className="mt-10 max-w-md space-y-5">
            <div>
              <label htmlFor="username" className="block text-sm font-medium text-stone-700">
                Username
              </label>
              <input
                id="username"
                type="text"
                required
                autoComplete="username"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                className={field}
              />
            </div>
            <div>
              <label htmlFor="password" className="block text-sm font-medium text-stone-700">
                Password
              </label>
              <input
                id="password"
                type="password"
                required
                autoComplete="current-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className={field}
              />
            </div>
            {error && (
              <div className="border border-rose-300 bg-rose-50 px-3 py-2 text-sm text-rose-900">{error}</div>
            )}
            <button
              type="submit"
              disabled={loading}
              className="w-full bg-charcoal px-4 py-3 text-sm font-semibold text-ivory hover:bg-forest disabled:opacity-50"
            >
              {loading ? "Signing in…" : "Sign in"}
            </button>
          </form>
        </section>
        <aside className="hidden flex-col justify-center bg-forest px-12 py-16 text-ivory lg:flex">
          <h2 className="font-display text-4xl leading-tight">A quiet path through the details.</h2>
          <ul className="mt-8 space-y-4 text-sm leading-relaxed text-ivory/85">
            <li>Tell us who died, and how many will gather.</li>
            <li>The home assigns the earliest free ceremony. You approve it.</li>
            <li>A director still confirms before the day is final.</li>
          </ul>
        </aside>
      </div>
    </PageShell>
  );
}
