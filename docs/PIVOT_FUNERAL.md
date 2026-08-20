# Pivot: Courtly → Funeral-Home Booking

**Read first:** `PIVOT.md`, `README.md`, `src/main/resources/application.yml`,
`src/main/java/.../model/enums/ResourceType.java`, `src/main/resources/data/seed.yml`.
The first two explain the pivot seams; the rest are the seams themselves.

## Assignment
Pivot Courtly (Warsaw sports-facility booking) into a **funeral-home services
booking platform**. This is a real product pivot, not a reskin, built on one
structural inversion that must become the product's thesis.

## The core twist (make this the heart of the product)
In Courtly the booker, attendee, and payer are ~the same person, and that person
*chooses* the date and hour.

In the funeral domain all of that breaks:
- The **subject of the booking is deceased** — cannot choose, consent, cancel, or confirm.
- The **payer is a third party** — a grieving relative/next-of-kin/executor, acting
  under time and emotional pressure.
- **Neither of them gets to choose the date.** The date is *derived*, not selected:
  legal burial windows, religious requirements (e.g. Jewish within ~24h, Orthodox
  within 1–3 days), death-certificate/coroner release, chapel/crematorium/cemetery
  availability, the officiant's calendar.

So the product inverts the classic slot-picker: instead of "pick a slot from a grid",
the user **submits the deceased's facts and constraints, and the system proposes the
mandatory-feasible schedule**. The family doesn't shop for times — they approve (or,
inside the legal window, request the least-bad alternative).

Design and execute the pivot around this inversion, and come up with something
original — a name, a brand voice, one or two features that only make sense *because*
of this asymmetry. Do not just rename "court" → "coffin".

## Concrete remapping (start here, improve it)
- **ResourceType** → service types: `CHAPEL` (ceremony), `CREMATION`, `BURIAL` (plot slot),
  `TRANSPORT` (hearse), `REFRESHMENT` (wake). Keep enum names stable OR handle the
  rename carefully (PIVOT.md seam 2: synonym keys + DB rows).
- **Facility network** (Torwar, AWF…) → city funeral homes/parlours.
- **Party size** → number of mourners (ceremony-room capacity).
- **Group lesson vs individual** → whole-chapel ceremony vs slot within a shared service.
- **Coach as booking extra** → officiant/celebrant/priest attachable to a service, with
  real levels (rites/denominations). Coach ratings become bereaved-family reviews.
- **Manager queue** → funeral-home dispatch board: confirm + allocate staff.
- **Pessimistic locking / no double-booking** → keep, it's a selling point: no two
  funerals in one chapel slot, no double-booked celebrant.
- **Voice receptionist + intent engine** → first-class interface: families call in
  distress; empathetic phone intake collects the deceased's details.
- **Cancellation reasons** → keep, retune wording.

## New logic to add (the "date is not chosen" part)
Add a scheduling concept where each service carries a **deadline window** (earliest
+ latest feasible date from: death-certificate ETA, legal window, religious rules,
venue availability). The suggestion engine proposes a concrete date+time; the family
confirms or requests the nearest alternative inside the window. Quote before submit
(reuse the `/quote` flow shape).

## Hard rules
- Do NOT modify: intent engine (`intent/engine/*`), `/api/intent/**` contract,
  Spring Security, reservation/pricing core, domain-agnostic scheduling. Pivot =
  data/copy/new logic on top, never a rewrite.
- Follow PIVOT.md seams in order: `domain:` block → `ResourceType` → `seed.yml`.
- Sweep copy: templates, `Europe/Warsaw` tz, `web/lib/fixtures.ts`, `telegram_bot.py`
  env vocab + bot copy.
- Delete `data/` before boot so old sports rows don't linger.
- `./gradlew test` must stay green; run the `web` build checks too.
- Tone: calm, compassionate, zero flippancy. No puns in shipped copy.

## Deliverables
1. Product brief: original name + tagline + one-paragraph pitch built on the
   inversion, plus the 2–3 features only possible because the subject can't choose
   (derived scheduling, family approval flow, phone intake).
2. The mechanical pivot: yml, ResourceType, seed.yml, template + bot copy, fixtures.
3. The deadline-window scheduling feature implemented without touching protected code.
4. Run PIVOT.md's verification list and report results.
