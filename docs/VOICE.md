# EverRest phone receptionist

Inbound SIP on the spare Telnyx DID (`+48 585 006 115`, number 15). Do not reassign DID 16.

The family does not pick a date. The server derives the legal window from the date of death, then proposes the **earliest free ceremony**. If they refuse that time, the next earliest in the same window is proposed. There is no slot menu and no random assignment.

## Call flow

1. Ask for the date of death, burial or cremation, mourner count, and the name of the deceased.
2. `POST /api/voice/tools/check-availability` with those facts in `text`. Returns one slot. Read `displayLabel` verbatim.
3. If they refuse, call again with `skipCount` increased by one.
4. On confirm, `POST /api/voice/tools/create-booking` with `slotId` from that proposal. Omit `playerPhone`; the webhook uses the SIP caller id.
5. The booking is stored as `PENDING`. SMS carries `/voice/invite/{token}`. That page is a Google Calendar TEMPLATE reminder only. No ICS email. No Google Calendar API.

The greeting is a condolence, not a booking prompt. TTS uses Sarah (`EXAVITQu4vr4xnSDxMaL`) at reduced speed, with a patient turn timeout so a crying caller is not cut off. Do not use a bright demo voice.

## Local run

Spring does not load `.env`. Use `python3 scripts/local_bootrun.py`. After a tunnel is up, `POST /api/voice/provision` with `Authorization: Bearer $TOOL_WEBHOOK_SECRET`.

There is no public `/demo` page and no cloud database. Local H2 is `./data/everrest`. `ddl-auto: create-drop` recreates the schema on boot.

## Demo room (local only)

- Call: `+48 585 006 115`. One spare DID, one caller at a time. Invite a single person.
- Family web: `everrest_demo` / `Demo123!` at http://localhost:8080
- Desk: `manager` / `Manager123!` then `/manager/reservations` and `/availability`
- Script: dad died yesterday, burial, about twenty people, Jan Kowalski. Say yes to the first time.
