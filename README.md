# Courtly – Sports Facility Booking

Built for **Codaro × GDG Coding Challenge II**  
**Track B – Resource Reservation and Scheduling**

Courtly is a Warsaw sports-facility booking web app. Players reserve courts, gyms, and pools by the hour; managers confirm walk-up demand; admins run the staff list; coaches publish the sports and real skill levels they teach and can be added as an extra on a place booking.

---

## Overview

Courtly covers a small city network of venues. Each facility has named sport instances (a tennis court, a gym hall, a pool lane block) with its own address, opening hours, and capacity. Players pick a date, a start hour, and a duration of 1–4 hours. The amount due is quoted in PLN before submit.

Key capabilities:

- Account registration, sign-in, and profile (contact details, password, per-sport skill level, optional photo)
- Facility and resource browsing with sport photography
- Slot booking on a start-hour grid, duration 1–4 hours, within opening hours
- Party size where the sport needs it (for example tennis 2–4)
- Gym and swimming **individual** sessions vs **group lessons** (whole-space lessons)
- Live occupancy board across the network
- Pending vs confirmed reservations and a manager/admin queue
- Cancellation only while pending, with a required reason
- Inventory extras priced per person (rackets, towels, and similar)
- Payment method stored on the reservation (no payment gateway)
- Dynamic hourly pricing (evening and weekend multipliers on the venue rate)
- Coaches created by an admin, offerings set by the coach, avatars, sport-specific levels
- Optional coach extra on a court or individual gym/swim booking (not on group lessons)
- Pessimistic locking so the last free slot cannot be double-booked
- Nightly cleanup of reservations that ended more than a month ago
- Voice receptionist tools (ElevenLabs placeholders) that check availability and book into the **same H2 database**, not Google Calendar

---

## Roles

| Role | Sign-in label | What they can do |
|------|----------------|------------------|
| **USER** | Player | Register, book, cancel own pending bookings, hire a coach as a booking extra, edit profile |
| **MANAGER** | Manager | Same booking rights as a player, plus the reservation queue: confirm pending bookings and cancel pending ones |
| **ADMIN** | Admin | Queue plus **Staff** (`/admin/users`): create Player, Manager, or Coach accounts. Sees all reservation history |
| **COACH** | Coach | Log in, publish offerings (`/coach/offerings`), see assigned sessions (`/coach/sessions`). Cannot open `/admin/**`. No coaches are seeded |

Staff-created accounts are enabled immediately. Role cannot be changed later from the create-staff form.

---

## Main workflows

### Registration, login, profile

- `/register` and `/login` are public.
- Profile (`/profile`) updates name, email, phone, password, and a skill level per sport. Anyone can upload a small avatar; coaches should, so they show up on the booking picker.

### Facilities and resources

- `/facilities` lists enabled Warsaw venues.
- Open a venue, then a court/gym/pool instance. Courts book directly. Gym and swimming first ask **individual** or **lesson**.

### Slot booking (1–4 hours)

- Pick a date (today or later) and a start hour from the slot grid.
- Duration is 1–4 hours and must stay inside opening hours and on the resource’s slot length (typically 60 minutes).
- Past starts are rejected.

### Party sizes

Approximate defaults by sport (instance capacity can still cap a lesson):

- Tennis / squash: 2–4 (last option shown as `4+` on courts)
- Football: 2–22
- Basketball: 2–10
- Volleyball: 2–12
- Gym individual: 1 person; gym lesson: 2–12
- Swimming individual: 1 person; swimming lesson: 2–8

### Individual vs group lessons

Gym and pool instances use a mode step:

- **Individual** — one occupancy unit; several people can share the hour up to capacity.
- **Lesson** — books the whole space (occupancy equals capacity). A lesson is blocked if anyone is already coming that hour. Coaches cannot be attached to a lesson.

Courts and pitches use a standard court booking (not that mode picker).

### Occupancy

`/occupancy` shows how full each court is through the day. A free hour links into that resource’s booking form with the hour preselected.

### Pending and confirmed

- A **player** (or coach booking a court for themselves) creates a **PENDING** reservation.
- An **admin** or **manager** booking is stored as **CONFIRMED**.
- Managers confirm pending items from `/manager/reservations`. Confirming the reservation also confirms any coach extra on it.

### Manager queue

`/manager/reservations` is a day queue (pending and confirmed). Extras render as a single line, for example `Racket ×2, Coach Anna Kowalska`.

### Cancellation

- **Confirmed** reservations cannot be cancelled.
- **Pending** future reservations can be cancelled by the owner or by staff, with a reason: change of plans, weather, booked by mistake, scheduling conflict, or other (optional note).
- Past pending items cannot be cancelled.

### Extras

Optional inventory extras are listed on the booking form for that sport and priced **per person** × party size. Seeded examples include racket, ball basket, towel, locker, and goggles.

A selected **coach** is also an extra: quantity 1, label `Coach {full name}`, amount = coach hourly rate × duration hours.

### Payment choice (no real gateway)

The form requires a method. Values stored on the reservation:

- Cash
- Card at the facility
- Bank transfer

Nothing is charged. See [Known scope](#known-scope).

### Dynamic PLN pricing

Venue hourly rates (base, then lesson rate where set):

| Sport | Court / individual (PLN / h) | Lesson (PLN / h) |
|-------|------------------------------|------------------|
| Tennis | 80 | 3 × base if no lesson rate |
| Squash | 70 | 3 × base if no lesson rate |
| Football | 160 | 3 × base if no lesson rate |
| Basketball | 120 | 3 × base if no lesson rate |
| Volleyball | 110 | 3 × base if no lesson rate |
| Gym | 30 | 90 |
| Swimming | 35 | 95 |

Rules applied **per booked hour** on the venue rate:

- From 17:00: × **1.35** (evening)
- Saturday and Sunday: × **1.25** (weekend)
- Both can apply (weekend evening stacks)

Inventory extras use the catalog per-person price × people. **Coach extras do not get evening or weekend multipliers** — they stay the coach’s hourly price × hours.

The booking page quotes the total before submit (`GET /resources/{id}/quote`, including `coachId` when a coach is selected).

### Coaches

1. Admin creates a Coach at `/admin/users` (no photo on that form). No coach users are seeded.
2. The coach signs in and opens `/coach/offerings`.
3. They **choose a sport first**. Until then, levels stay hidden behind “Select a sport first”.
4. After a sport is chosen, only that sport’s real levels appear (tennis NTRP, gym beginner/intermediate/advanced, and so on). Edit preloads the saved sport and checked levels.
5. One offering per sport: a set of levels plus one hourly PLN price.
6. On a **court** or **individual gym/swim** booking, the player picks their level for that sport (prefilled from profile). Matching coaches appear with a small avatar, name, hourly price, and levels. No match still allows booking without a coach.
7. Saving the booking stores the chosen sport level on the profile.
8. A coach cannot be double-booked: overlapping PENDING or CONFIRMED assignments are rejected under a pessimistic lock on the coach.
9. `/coach/sessions` is a read-only list of reservations that include that coach. Confirmation stays on the main manager queue.
10. After a **confirmed** session has ended, the player who booked can rate that coach once (1–5 stars, optional review). The booking picker shows the average, or “New coach”.

---

## Concurrency

Creating a reservation takes a **pessimistic write lock** on the sport resource (and on the coach user when a coach is selected). Occupying reservations are PENDING and CONFIRMED. If the slot is full, or the coach is already assigned in that interval, the second request fails instead of overbooking. Lock wait is configured at 4000 ms (`jakarta.persistence.lock.timeout` in `application.yml`).

---

## Stack

- Java 21 and Spring Boot 4.1 (Gradle)
- Spring MVC + service layer + Spring Data JPA / Hibernate
- Thymeleaf templates and small vanilla JavaScript
- Bean Validation
- Spring Security (form login, role-based URL rules)
- H2 file database (in-memory H2 in tests)
- Scheduled cleanup via Spring `@Scheduled`

---

## Package and folder layout

Java root package: `com.example.hackathoncodaro2026`

```
src/main/java/com/example/hackathoncodaro2026/
  controller/     HTTP + Thymeleaf model
  service/        interfaces, SportSkillLevelCatalog, impl/
  repository/     Spring Data JPA
  model/          entities and enums/
  dto/            form and quote objects
  validation/     custom constraints
  exception/      ReservationException and handlers
  config/         security, seed data, H2 enum repair, scheduling, browser launch

src/main/resources/
  templates/      Thymeleaf (home, facilities, resources, reservations, manager, admin, coach, profile)
  static/         css/, js/, images/
  application.yml
  logback-spring.xml

src/test/java/... and src/test/resources/application.yml
data/             local H2 file DB and uploaded avatars (not for git)
logs/             rolling application logs (not for git)
```

Gradle wrapper lives in `gradle/wrapper/` plus `gradlew` / `gradlew.bat`. Those **should** be committed.

---

## Requirements

- **JDK 21** (`languageVersion = 21` in `build.gradle`)
- No extra Node toolchain; the UI is server-rendered plus static JS

---

## Running

Windows:

```bat
.\gradlew.bat bootRun
```

macOS / Linux:

```bash
./gradlew bootRun
```

The app listens on **http://localhost:8080**.

On startup, if `app.browser.auto-open` is `true`, Courtly tries to open Firefox (then the desktop browser) at `/login` and `/h2-console`. Disable auto-open with:

```yaml
app.browser.auto-open: false
```

in `src/main/resources/application.yml`, or pass `--app.browser.auto-open=false`. The browser binary hint is `app.browser.executable` (default `firefox`).

---

## URLs

| Path | Who |
|------|-----|
| `/login`, `/register` | Public |
| `/` | Home (signed-in) |
| `/facilities`, `/resources/{id}` | Booking |
| `/occupancy` | Occupancy board |
| `/reservations` | History |
| `/profile` | Profile and avatar |
| `/manager/reservations` | Admin / manager queue |
| `/admin/users` | Admin staff create (Player, Manager, Coach) |
| `/coach/offerings` | Coach: sports, levels, hourly price |
| `/coach/sessions` | Coach: assigned reservations |
| `/avatars/{userId}` | Avatar or initials placeholder |
| `/h2-console` | H2 web console |
| `GET /api/voice/tools` | Public ElevenLabs tool catalog (placeholders) |
| `POST /api/voice/tools/check-availability` | Voice tool: same intent suggest as `/api/intent/suggest` |
| `POST /api/voice/tools/create-booking` | Voice tool: same intent book as `/api/intent/book`, then SMS/invite |
| `POST /api/voice/provision` | Creates the ElevenLabs agent (and later SIP import). Agents: `docs/VOICE.md` prompts A/B |
| `/voice/invite/{token}` | Caller email form, then calendar invitation (.ics) |

---

## Voice receptionist

Phone booking uses the same intent engine as the chatbot. The extra loop is ElevenLabs plus SIP. Humans and coding agents: follow [`docs/VOICE.md`](docs/VOICE.md). After merge, the teammate agent pastes prompt A (ElevenLabs key + tunnel provision). Jenya's agent pastes prompt B (spare SIP DID).

---

## Seed accounts

Created on first run if missing. **No Coach users are seeded.**

| Username | Password | Role |
|----------|----------|------|
| `admin` | `Admin123!` | ADMIN |
| `manager` | `Manager123!` | MANAGER |

Facilities, resources, and inventory extras are also seeded. Switch `ddl-auto` only if you intend to keep or wipe the file database (see below).

---

## H2

From `src/main/resources/application.yml`:

| Setting | Value |
|---------|--------|
| JDBC URL | `jdbc:h2:file:./data/sportsbooking;LOCK_TIMEOUT=5000` |
| Driver | `org.h2.Driver` |
| Username | `sa` |
| Password | `sportsbooking` |
| Console | `/h2-console` (enabled) |
| `spring.jpa.hibernate.ddl-auto` | **`create-drop`** |

`create-drop` **rebuilds the schema on each process start** and drops it on shutdown. Local `./data/` files are for that run. Tests use in-memory H2 (`jdbc:h2:mem:sportsbookingtest;...`) with `ddl-auto: create-drop` as well.

In the H2 console, use the same JDBC URL, username, and password as above.

---

## File logging

Courtly writes **files on disk**, not an H2 audit table. Files stay readable after the process stops, survive `create-drop`, and still capture startup or database failures. Domain data stays in H2.

Override the directory with `LOG_PATH` or `app.logging.dir` (default `./logs`):

| File | What it contains | Retention |
|------|------------------|-----------|
| `logs/courtly.log` | INFO and above (HTTP access, app, schema repair) | Daily gzip archives, 30 days, about 1 GB cap |
| `logs/courtly-error.log` | WARN and above | Daily gzip archives, 30 days, about 1 GB cap |
| `logs/courtly-audit.log` | Business and security events only (`AUDIT` logger) | Daily gzip archives, 90 days, about 2 GB cap |

Lines include an ISO timestamp, level, thread, `requestId` (also returned as `X-Request-ID`), logger, and message. Tests use console logging only and do not write these files.

Read while the app is off (PowerShell, from the project root):

```powershell
Get-Content .\logs\courtly-audit.log -Tail 100
Get-Content .\logs\courtly-error.log -Tail 100
Get-Content .\logs\courtly.log -Tail 100
Get-ChildItem .\logs\*.log.gz
```

**Do not commit** `./logs/` or `*.log`. They are in `.gitignore`. Log files can still hold usernames, reservation ids, and masked contact hints; treat them as private. Passwords, hashes, session tokens, cookies, avatars, H2 credentials, payment secrets, raw request bodies, cancellation notes, and review text are not written.

---

## Tests

Windows:

```bat
.\gradlew.bat test
```

Elsewhere:

```bash
./gradlew test
```

Coverage includes overlap and capacity, individual vs lesson occupancy, extras × party size, pricing multipliers, manager confirm, coach offerings and sport-specific levels, attaching a coach only on place bookings, overlapping coach assignment, and “no coach seeded”.

---

## Sport-specific skill levels

Levels are **not** a single beginner–advanced ladder for every sport.

| Sport | System | Codes shown in the UI |
|-------|--------|------------------------|
| Tennis | NTRP / level | 1.0, 1.5, 2.0, … 7.0 (0.5 steps) |
| Squash | England-style grade | G · beginner, F, E, D, C, B, A, Open |
| Football | Recreational ladder | Recreational, Club amateur, County / regional, Semi-pro |
| Basketball | Amateur rungs | Recreational, High school / club, Collegiate amateur, Competitive |
| Volleyball | USAV-style | D, C, B, A, AA, Open |
| Gym | Fitness PT | Beginner, Intermediate, Advanced |
| Swimming | Swim England stages | Stage 1–7, Club / competitive |

The coach offering form loads levels **after** the sport is selected. The server rejects any submitted level that does not belong to that sport.

---

## Avatars

Files are stored under **`./data/avatars/`** as `{userId}.jpg` (or png / webp / gif), served from `GET /avatars/{userId}`. Missing files return a green initials placeholder. Max size 1 MB. This directory is inside `./data/` and must not be committed (personal photos).

---

## Do not commit

Keep these off GitHub: `./data/` (H2 files and avatars), `./logs/` (application logs), `.env`, `build/`, and `.gradle/`. The Gradle wrapper under `gradle/wrapper/` should stay in the repository.

---

## Automatic cleanup

Every day at **23:59 Europe/Warsaw**, reservations whose **end** time is more than **one month** ago are deleted (extras first, then the reservation). Implemented by `ReservationPurgeScheduler`.

---

## Known scope

- Payment methods are **stored only**. There is no card capture, transfer verification, or payout to coaches.
- Coach hire is an extra on a **place** reservation, not a standalone marketplace and not available on group lessons.
- Confirmed bookings cannot be cancelled in-app.