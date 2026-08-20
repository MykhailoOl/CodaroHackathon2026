# PIVOT.md — retargeting Courtly to a new niche

Courtly is a generic resource-reservation + scheduling product wearing a
sports-facility skin. The intent engine, API contract, reservation/pricing
logic, and security are **domain-agnostic by design**. A pivot is a data/copy
swap, not a logic change — target ~2–4h of mechanical re-skin once the
organizers announce the niche.

## The three pivot seams (do these first, in order)

1. **`src/main/resources/application.yml` → `domain:` block**
   - `brand.name` / `brand.tagline` — shows in nav, page titles, footer,
     login/register headings, home page.
   - `llm-domain-description` — fed into the LLM system prompt
     (`CodaroIntentParser`), e.g. `"sports-facility"` → `"clinic"`.
   - `resource-synonyms` — the words users type for each resource type
     (`RuleIntentParser` recognises these even before the enum is edited).
     Keys must match `ResourceType.name()`.

2. **`src/main/java/.../model/enums/ResourceType.java`** — the ONE Java edit.
   Retarget the enum: names, `displayName`, per-type pricing/party/image
   defaults. This is the single ~15-min edit that makes the whole backend
   speak the new domain. Keep enum *names* stable if possible so the
   `domain.resource-synonyms` keys and DB rows keep working.

3. **`src/main/resources/data/seed.yml`** — the facility/resource/inventory
   catalog, read at startup by `SeedDataLoader` (only when the DB is empty).
   Replace venues, bookable resources, capacities, and rental inventory
   wholesale. No Java changes needed.

Then run `./gradlew test` — the intent-parser tests must stay green
(the engine is config-driven, so they should).

## Copy to sweep (cheap, grep-driven)

- **Templates** (`src/main/resources/templates/**`): hardcoded "Warsaw",
  "court", "athlete", "sport" copy lives in
  - `home/index.html` (hero, "Warsaw courts", coach/athlete wording)
  - `facilities/list.html` ("Warsaw network", "View courts")
  - `facilities/detail.html` ("Each court, hall, and gym… Warsaw address")
  - `reservations/history.html` ("Warsaw network", "Book another court")
  - brand strings are already parameterised via `${brand.name}` /
    `${brand.tagline}` (from `CurrentUserAdvice`) — keep it that way.
- **Time zone**: `Europe/Warsaw` is hardcoded in 10 controllers/services
  (`ZoneId.of("Europe/Warsaw")`) — include `reservation`, `occupancy`,
  `intent`, `notification`, schedulers. Sweep via grep if the niche needs a
  different tz.
- **Web demo fixtures** (`web/lib/fixtures.ts`): demo-only response shapes
  shown when the backend is down. Rewrite the suggestion content per niche.
  `NEXT_PUBLIC_DEMO_INTENT` in `web/.env.example` drives the composer
  placeholder.
- **Telegram bot** (`telegram_bot.py`): sports vocabulary now reads from env
  — `BOT_RESOURCE_WORDS`, `BOT_PARTY_RANGES`, `BOT_EXAMPLE_INTENT`. Bot copy
  strings mentioning "court"/"sport" also deserve a sweep.

## What to verify after a pivot

- `./gradlew test` (backend, incl. the config-driven intent tests).
- `cd web && npx tsc --noEmit && npm run build`.
- Boot the app, confirm: login/register headings show the new brand; the
  facility list/detail/occupancy pages render the new seed data; the intent
  composer parses new-niche phrasings (rule parser + LLM prompt both use the
  new vocabulary).
- Telegram bot: `python3 -m py_compile telegram_bot.py`, then a quick
  /start + free-text booking with the new env vars set.

## Notes / known trade-offs

- **Never touch** the intent engine (`intent/engine/*`), the `/api/intent/**`
  contract, security, or reservation/pricing logic to pivot — they are
  domain-agnostic already.
- Brand strings are the only copy auto-parameterised by config. The deeper
  prose (hero copy, facility descriptions, coach/athlete wording) is content,
  not config — sweep it by hand per niche.
- `data/` (H2 database files) may hold old-domain rows; delete it if a pivot
  should start from a clean slate.