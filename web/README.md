# EverRest — web

Next.js 14 (App Router, TypeScript, Tailwind) frontend for EverRest, a funeral
arrangements product. Talks to the Spring Boot API in the parent repo over
JSON, proxied through the Next server so the whole app stays same-origin.

## Run it

```bash
npm install
npm run dev
```

Open http://localhost:3000. You'll land on the login screen. Sign in with one
of the seeded accounts (e.g. `everrest_demo` / `Demo123!`) created by the
backend's `DataInitializer`.

## Env vars

Copy `.env.example` to `.env.local` and adjust as needed:

| Var | Default | Meaning |
|---|---|---|
| `API_ORIGIN` | `http://localhost:8080` | Origin of the Spring Boot API. `next.config.mjs` rewrites `/api/:path*`, `/login` and `/logout` to it. |

## How auth works

The backend uses ordinary Spring form login and a session cookie, and declares
no CORS. Instead of a bearer token, the app keeps everything same-origin:

- `next.config.mjs` rewrites `/login`, `/logout` and `/api/*` to the API origin,
  so the `JSESSIONID` cookie and CSRF token travel on every request with no
  cross-origin negotiation.
- `lib/api.ts` signs in the way a browser form would: it fetches `/login`, scrapes
  the `_csrf` token out of the rendered form, POSTs credentials, then stores the
  session's CSRF token for subsequent writes.
- `lib/auth.ts` only caches the display copy of the session; the source of truth
  is the cookie the API set.

## Flow

1. **Login** (`app/page.tsx`) — form login via the proxied `/login`.
2. **Composer** (`app/composer/page.tsx`) — the family gives the deceased's name,
   date of death, expected attendees and preferred service, picks a funeral home
   and a space that fits, then previews the proposed arrangement.
3. The API assigns the date itself (certificate + observance + statutory window),
   so the app never offers a date menu — it confirms the arrangement and shows
   the day the funeral home settled on.

## Structure

```
app/
  page.tsx            Login screen
  composer/page.tsx   Arrangement form → preview → confirm
lib/
  api.ts        login / logout / session / homes / venues / preview / create
  auth.ts       Session display-copy cache
  types.ts      TS types mirroring the JSON contract
```

## Commands

```bash
npm run dev          # dev server
npm run build        # production build
npx tsc --noEmit     # typecheck
npm run lint         # eslint
```