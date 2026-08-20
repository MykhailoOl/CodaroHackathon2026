# Intent Booking — web

Next.js 14 (App Router, TypeScript, Tailwind) frontend for intent-based
booking. Talks to the Spring Boot API in the parent repo over JSON; ships
fixture-driven so it demos end to end even when that API isn't running.

## Run it

```bash
npm install
npm run dev
```

Open http://localhost:3000. You'll land on the login screen; any
username/password works while the backend isn't live (see "Fixtures" below).

## Env vars

Copy `.env.example` to `.env.local` and adjust as needed:

| Var | Default | Meaning |
|---|---|---|
| `NEXT_PUBLIC_API_BASE` | `http://localhost:8080` | Base URL of the Spring Boot API. |
| `NEXT_PUBLIC_USE_FIXTURES` | `false` | Set `true` to force every screen onto `lib/fixtures.ts` and skip the real API entirely. |

## Fixtures & the demo-data fallback

The backend is being built in parallel and wasn't available while this was
built, so the whole UI is driven from `lib/fixtures.ts` first and wired
against the live API second:

- **Automatic fallback**: `lib/api.ts` tries the real endpoint; on a network
  error or non-2xx response it transparently falls back to fixture data
  instead of showing a blank screen. Every screen shows a small badge
  (`Live API` / `Demo data` / `Demo data (API unreachable)`) so it's always
  clear which one you're looking at.
- **Manual override**: the "Use demo data" checkbox on the login and
  composer screens (or `NEXT_PUBLIC_USE_FIXTURES=true`) forces fixtures on
  regardless of whether the API is reachable — useful for demoing offline.
- **Booking in fixture mode**: `POST /api/intent/book` is simulated
  client-side (`fixtureBook` in `lib/fixtures.ts`) whenever the suggestion
  being booked came from fixtures, so the full login → composer → results →
  book flow works with zero backend involvement. Booking the same slot twice
  simulates the "someone else took it" race so the error state is reachable
  too. Once a suggestion comes from the live API, booking always calls the
  real endpoint and surfaces its message verbatim on error.

## What still needs the live API

Everything here was built against the API contract in the task brief, not
against a running server:

- **Real auth**: login falls back to a mock token on network failure, so
  it can't currently be used to prove real credential checking — only a
  live 401 response is treated as a genuine bad-credentials error.
- **`Suggestion.relaxed` shape**: the brief's example only shows an empty
  array (`"relaxed": []`). This build assumes each entry has the same shape
  as a `relaxationTrail` entry (`{ action, detail, droppedKeys }`), since
  `RelaxationNotice` needs a `detail` string to render either way. If the
  real API sends something else (e.g. bare strings), the only file that
  needs to change is `lib/types.ts` (the `RelaxationTrailEntry` type used
  for `Suggestion.relaxed`) plus the `RelaxationNotice` render in
  `components/SuggestionCard.tsx` — everything else is unaffected.
- **End-to-end verification against a live server**: `npm run build` and
  `npx tsc --noEmit` are clean, and the fixture-driven flow was verified
  manually in `npm run dev`, but nothing here has talked to a real
  `localhost:8080` yet.

## Structure

```
app/
  page.tsx            Login screen
  composer/page.tsx   Composer + ranked results + booking (per-card)
components/
  SuggestionCard.tsx      Resource, facility, time, price, reason, booking state
  ReasonBreakdown.tsx     Expandable terms list, sorted by |delta| desc
  RelaxationNotice.tsx    Renders relaxationTrail / suggestion.relaxed detail text
  PartySizeControl.tsx
  DataSourceBadge.tsx     "Live API" / "Demo data" / "Demo data (API unreachable)"
  DevFixtureToggle.tsx    The manual "Use demo data" switch
lib/
  api.ts        login / suggestIntent / bookSlot, with the fixture-fallback logic
  fixtures.ts   Hand-written fixture responses matching the API contract
  auth.ts       localStorage token storage
  types.ts      TS types mirroring the JSON contract
  format.ts     Date/time/delta formatting helpers
```

## Commands

```bash
npm run dev          # dev server
npm run build         # production build
npx tsc --noEmit      # typecheck
npm run lint          # eslint
```
