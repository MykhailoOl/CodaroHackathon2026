"use client";

import type { ReactNode } from "react";

type PageShellProps = {
  children: ReactNode;
  signedInAs?: string | null;
  onSignOut?: () => void;
};

export function PageShell({ children, signedInAs, onSignOut }: PageShellProps) {
  return (
    <div className="flex min-h-screen flex-col bg-ivory">
      <header className="border-b border-stone-300/80 bg-ivory/95">
        <div className="mx-auto flex w-full max-w-6xl items-center justify-between px-6 py-5">
          <div>
            <p className="font-display text-3xl leading-none text-charcoal">EverRest</p>
            <p className="mt-1 text-xs tracking-[0.14em] text-stone-500 uppercase">Funeral arrangements</p>
          </div>
          {onSignOut ? (
            <div className="flex items-center gap-4 text-sm text-stone-600">
              {signedInAs ? <span>Signed in as {signedInAs}</span> : null}
              <button
                type="button"
                onClick={onSignOut}
                className="text-forest underline underline-offset-4"
              >
                Sign out
              </button>
            </div>
          ) : (
            <p className="hidden max-w-xs text-right text-sm text-stone-500 sm:block">
              Warsaw. A quiet path through the details.
            </p>
          )}
        </div>
      </header>
      <main className="flex flex-1 flex-col">{children}</main>
      <footer className="border-t border-stone-300/80 px-6 py-4 text-center text-xs text-stone-500">
        EverRest · Track B resource reservation and scheduling · Warsaw demo
      </footer>
    </div>
  );
}
