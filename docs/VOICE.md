# Courtly phone booking

Phone is a second booking channel into the same intent engine as the website chatbot (`POST /api/intent/suggest` and `POST /api/intent/book`). The extra work on this branch is ElevenLabs wiring plus the spare Telnyx SIP DID. No Google Calendar. Bookings write this app’s local H2 file (`./data/sportsbooking`). The database stays on the laptop that runs `./gradlew bootRun`.

ElevenLabs and Telnyx are in the cloud. They cannot see `localhost`. Publish port 8080 with a tunnel and set `PUBLIC_BASE_URL` to that `https://` URL. Keep the laptop awake for the demo.

```
caller → spare Telnyx DID → SIP → ElevenLabs agent
      → HTTPS $PUBLIC_BASE_URL/api/voice/tools/*
      → this Java process → local H2
```

Do not reuse the AI receptionist DID. Do not commit `.env`.

## Loop

1. Merge this branch. Copy `.env.example` to `.env`.
2. On the Courtly laptop: start a tunnel to `8080`, set `PUBLIC_BASE_URL` and `TOOL_WEBHOOK_SECRET`, then:

```bash
set -a && source .env && set +a
./gradlew bootRun
```

Prove the tunnel:

```bash
curl -s -X POST "$PUBLIC_BASE_URL/api/voice/tools/check-availability" \
  -H "Authorization: Bearer $TOOL_WEBHOOK_SECRET" \
  -H "Content-Type: application/json" \
  -d '{"text":"tennis tomorrow evening","partySize":2,"language":"en"}'
```

3. Teammate: put `ELEVENLABS_API_KEY` in local `.env` (their account). Restart the app with `source .env`. In Cursor, paste the prompt in the last section. That calls `POST /api/voice/provision`, which creates webhook tools + the Conversational AI agent pointed at the tunnel.
4. You: put spare Telnyx `SIP_FROM_NUMBER`, `SIP_USERNAME`, `SIP_PASSWORD` in `.env`. Point that DID at `sip:sip.rtc.elevenlabs.io:5060;transport=tcp`. Restart, `POST /api/voice/provision` again. Courtly imports the SIP number onto the agent.
5. Call the spare number. A `PENDING` row should appear in `/manager/reservations`. Invite link is `$PUBLIC_BASE_URL/voice/invite/{token}`. SMS stays log-only until Telnyx messaging is enabled.

If the tunnel URL changes, create a new agent or update the tool URLs in ElevenLabs. `POST /api/voice/provision` will not recreate the agent when `ELEVENLABS_AGENT_ID` is already set.

## Env

| Variable | When |
|----------|------|
| `PUBLIC_BASE_URL` | HTTPS tunnel of the Courtly laptop |
| `TOOL_WEBHOOK_SECRET` | Bearer the agent tools send |
| `ELEVENLABS_API_KEY` | Before first provision |
| `ELEVENLABS_AGENT_ID` | Written after first provision |
| `ELEVENLABS_PHONE_NUMBER_ID` | Written after SIP provision |
| `SIP_FROM_NUMBER` / `SIP_USERNAME` / `SIP_PASSWORD` | Spare DID only, second provision |

Spring does not load `.env` by itself. Always `source .env` before `bootRun`.

## ElevenLabs agent

Created by `POST /api/voice/provision` (same bearer as the tools).

- Name: Courtly phone receptionist
- First message: `Hi, this is Courtly. I can check court availability and book a slot for you.`
- Timezone: Europe/Warsaw
- Tools: `check_availability`, `create_booking` (POST, JSON, `Authorization: Bearer $TOOL_WEBHOOK_SECRET`)
- Prompt: caller words as `text` → `check_availability` (intent suggest) → read `displayLabel` → `create_booking` with `resourceId` / `start` / `end` (or `slotId`), name, `system__caller_id`. Never invent slots.

If the caller does not say a party size, phone booking uses 2 so tennis courts can be reserved. The chatbot still sends an explicit `partySize`. The call does not collect email. After booking, the SMS (log-only until Telnyx messaging) carries `/voice/invite/{token}` so the caller can add a calendar invitation later.

SIP import uses `provider=sip_trunk`, E.164 `SIP_FROM_NUMBER`, digest username/password, outbound address `sip.telnyx.com` TCP. Telnyx must send inbound INVITEs to `sip:+E164@sip.rtc.elevenlabs.io:5060`.

## Cursor prompt

Paste after the PR is merged, the tunnel curl works, and `ELEVENLABS_API_KEY` is in `.env`. Do not commit `.env`. Do not SSH anywhere. Do not use Codaro Builder.

```
You are working in the Courtly repo. Read docs/VOICE.md.

Goal: provision the live ElevenLabs Conversational AI agent that books into this local H2 database.

1. Confirm .env exists (copy from .env.example if needed). Confirm ELEVENLABS_API_KEY is non-empty, PUBLIC_BASE_URL is https and not localhost, TOOL_WEBHOOK_SECRET is set. If any of those fail, stop.
2. `set -a && source .env && set +a`. Restart is not required if the process already has those env vars; if bootRun is not running, start it.
3. curl POST $PUBLIC_BASE_URL/api/voice/tools/check-availability with Bearer $TOOL_WEBHOOK_SECRET. If this fails, stop.
4. curl POST $PUBLIC_BASE_URL/api/voice/provision with the same Bearer. If ELEVENLABS_AGENT_ID is already set, this should not create a second agent.
5. Write returned agentId to ELEVENLABS_AGENT_ID in .env only. If checkToolId/bookToolId are present, keep them in the reply, not in git.
6. Stop. SIP is a later step: Jenya adds SIP_FROM_NUMBER, SIP_USERNAME, SIP_PASSWORD, points Telnyx at sip.rtc.elevenlabs.io, then POST /api/voice/provision again and writes ELEVENLABS_PHONE_NUMBER_ID.

Never commit secrets. Never reuse a receptionist agent or DID.
```
