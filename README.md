# EverRest – Funeral arrangement scheduling

EverRest is a Warsaw funeral-home reservation app for **Codaro × GDG Track B** (resource reservation and scheduling). Families arrange a burial, cremation, memorial, or farewell ceremony. A manager confirms pending arrangements. Dates are **not** chosen by the visitor: after the arrangement is complete, the server assigns a currently available date and time. A wheel on the page only illustrates that server decision.

This repository is a **demo**. Seeded funeral homes use fictional names with realistic Warsaw streets. They are not real businesses and do not claim religious affiliation, medical authority, or any official standing.

## Roles

| Role | Label | What they do |
| --- | --- | --- |
| **USER** | Family | Register, arrange a ceremony, edit or cancel **pending** arrangements, update contact details |
| **MANAGER** | Manager | Review the queue by assigned date, confirm or cancel pending arrangements, inspect availability |
| **ADMIN** | Admin | Everything a manager can do, plus create family or manager accounts at `/admin/users` |

There is no coach role and no sports, lessons, or skill levels.

## How an arrangement works

1. Sign in.
2. Choose a funeral home and a **service venue** (chapel, ceremony hall, cremation suite, memorial garden, or reception hall).
3. Choose ceremony type, package, extras, guests, payment preference, and private details.
4. Request currently available **dates** (no hold). The quoted amount is exact and does **not** change with the assigned date.
5. Press **Spin for a date**. The server locks the venue, recomputes free slots, picks a date uniformly, then a compatible time on that date (`SecureRandom`), and stores the reservation in the same transaction.
6. The wheel lands on the **server-returned** date. The client never picks the winner.
7. Family arrangements are **PENDING** until a manager confirms. Manager and admin spins are **CONFIRMED**.

Saturday is open. Sunday is closed unless `app.scheduling.sunday-enabled` is `true`. The planning window is the next 21 days in `Europe/Warsaw` (`app.scheduling.planning-days`).

## Resources and occupancy

Each venue is reserved **exclusively** for one ceremony at a time (`[start, end)`). `maxAttendees` only checks guest count. **PENDING** and **CONFIRMED** occupy a slot. **CANCELLED** frees it. `/availability` (also `/occupancy`) is a read-only board. Visitors cannot click a cell to pick a time.

## Packages, extras, payment

| Package | Duration | Base price |
| --- | --- | --- |
| Essential | 90 min | 2400 PLN |
| Classic | 120 min | 4200 PLN |
| Tribute | 180 min | 6800 PLN |

Extras use **FIXED** or **PER_ATTENDEE** pricing (flowers, memorial cards, obituary notice, ceremonial transport, family transport, catering, live music, livestream, venue decoration). **Urn selection** applies only to a cremation ceremony. Payment method is a stored preference (cash, card at the funeral home, bank transfer). There is **no payment gateway**.

## Evelyn (no AI)

Evelyn is a deterministic button guide on every page. She follows the same steps as the form and calls the same `ReservationService` preview/spin methods. She is not an LLM.

Open state, position, and non-sensitive ids may be stored in `localStorage`. **Never stored:** deceased name, dates of birth or death, phone, family note, email.

## Privacy and logging

Audit files are written under `./logs/` (`everrest.log`, `everrest-error.log`, `everrest-audit.log`). Logs may include venue id, assigned time, package, service type, guest count, amount, status, source (`FORM` / `CHAT_ASSISTANT`), and candidate counts.

Logs **must not** include deceased name, dates of birth or death, family notes, phone, email, obituary text, or cancellation free text.

## Stack and run

Spring Boot, Java 21, Thymeleaf, H2, Spring Security, Gradle.

```powershell
.\gradlew.bat bootRun
```

H2 console: `/h2-console`  
JDBC URL: `jdbc:h2:file:./data/everrest`  
User `sa` / password `everrest`

**Schema:** `spring.jpa.hibernate.ddl-auto` is `create-drop` for this demo (the database is dropped on shutdown). Switch to `update` later if you need to keep data.

### Demo accounts

- `admin` / `Admin123!`
- `manager` / `Manager123!`

## Main routes

| Path | Purpose |
| --- | --- |
| `/` | Home |
| `/homes`, `/homes/{id}` | Funeral homes and venues |
| `/venues/{id}` | Arrangement form and date wheel |
| `POST /venues/{id}/preview` | Validate and list candidate dates |
| `POST /venues/{id}/spin` | Assign a slot and persist |
| `/reservations` | History |
| `/reservations/{id}/edit` | Pending-only edits (venue, package, and time stay fixed) |
| `/availability` | Read-only occupancy board |
| `/notifications` | Notices |
| `/profile` | Contact details and password |
| `/manager/reservations` | Manager queue |
| `/admin/users` | Staff create |
| `/api/reservation-assistant/**` | Evelyn JSON API |

Scheduled cleanup still deletes ended reservations older than one month. Notices keep a scalar reservation id so they are not blocked by that delete.

## Concurrency

`create` / `spin` takes a **pessimistic write lock** on the venue, recomputes availability, then inserts. Two families spinning the last slot: exactly one succeeds. Lock wait is 4000 ms.

## Tests

```powershell
.\gradlew.bat clean test --warning-mode all
```

Tests use an in-memory H2 database and write logs under `./build/test-logs` only.

## Image credits

Tasteful, non-graphic photographs downloaded locally (not hotlinked). Unsplash photos: [Unsplash License](https://unsplash.com/license). Pexels photos: [Pexels License](https://www.pexels.com/license/).

| File | Source | Author / page | License |
| --- | --- | --- | --- |
| `static/images/home/hero.jpg`, `homes/everrest.jpg`, `venues/chapel.jpg` | [Unsplash photo 1520854221256](https://unsplash.com/photos/1520854221256-17451cc331bf) | Unsplash photographer (ceremony interior) | Unsplash |
| `static/images/home/flowers.jpg`, `homes/linden.jpg` | [Unsplash photo 1490750967868](https://unsplash.com/photos/1490750967868-88aa4486c946) | Jennifer Pallian | Unsplash |
| `static/images/venues/garden.jpg`, `homes/gardens.jpg` | [Unsplash photo 1416879595882](https://unsplash.com/photos/1416879595882-3373a0480b5b) | Francesco Gallarotti | Unsplash |
| `static/images/venues/cremation.jpg`, `homes/serenity.jpg` | [Unsplash photo 1516450360452](https://unsplash.com/photos/1516450360452-9312f5e86fc7) | nrd | Unsplash |
| `static/images/venues/hall.jpg`, `venues/reception.jpg`, `homes/dawn.jpg` | [Unsplash photo 1519167758481](https://unsplash.com/photos/1519167758481-83f550bb49b3) | Alvin Mahmudov | Unsplash |
| `static/images/homes/peaceful.jpg` | [Pexels 208701](https://www.pexels.com/photo/208701/) | Pixabay | Pexels |
| `static/images/homes/harbor.jpg` | [Pexels 1616403](https://www.pexels.com/photo/1616403/) | Pixabay | Pexels |
| `static/images/brand/mark.svg` | Original EverRest mark | Project | Original |

## Git ignore

`/data/`, `logs/`, `.env`, H2 files, and build output stay out of git. Do not commit deceased or family records.

## Visual palette

Warm ivory `#F6F1E8`, charcoal `#2C3338`, forest green `#3D5C4A`, muted brass `#C4A574`, stone cards `#EDE8E0`.
