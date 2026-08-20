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

1. Merge this branch. Copy `.env.example` to `.env` on the Courtly laptop.
2. Tunnel port `8080` (ngrok is fine: `ngrok http 8080`). Put the public `https://` URL in `PUBLIC_BASE_URL`. Not `localhost`. Then:

```bash
set -a && source .env && set +a
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home bash ./gradlew bootRun
```

Prove the tunnel:

```bash
curl -sS -X POST "$PUBLIC_BASE_URL/api/voice/tools/check-availability" \
  -H "Authorization: Bearer $TOOL_WEBHOOK_SECRET" \
  -H "Content-Type: application/json" \
  -d '{"text":"tennis tomorrow evening","partySize":2,"language":"en"}'
```

3. Teammate AI: paste **Agent prompt A** below into Cursor in this repo on the same machine that is running `bootRun`. Adding the key does not create the ElevenLabs agent. The prompt writes the key, restarts if needed, and `POST`s `/api/voice/provision`.
4. Jenya AI: after spare Telnyx SIP creds are in `.env` and that DID points at `sip:sip.rtc.elevenlabs.io:5060;transport=tcp`, paste **Agent prompt B**. That imports the number onto the agent.
5. Call the spare number. A `PENDING` row should appear in `/manager/reservations`. The call does not collect email. Courtly logs an SMS with `$PUBLIC_BASE_URL/voice/invite/{token}` for a later calendar invitation. Live SMS needs Telnyx messaging keys.

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

## Agent prompt A: ElevenLabs (teammate)

Paste this whole block into Cursor in this Courtly checkout, on the laptop that already has the HTTPS tunnel and `bootRun`. Do not commit `.env`. Do not SSH. Do not use Codaro Builder. Do not reuse a receptionist agent or DID.

```
You are an implementation agent in the Courtly repo. Read docs/VOICE.md first. Execute; do not ask for a plan.

Goal: create the live ElevenLabs Conversational AI agent that books through this app's intent engine into local H2.

Hard stops (print the missing item and stop):
- .env is missing and .env.example cannot be copied
- ELEVENLABS_API_KEY is empty after you would need the human to paste it. If it is already in the environment or .env, use that. If it is empty, stop and say "paste ELEVENLABS_API_KEY into .env, then re-run this prompt"
- PUBLIC_BASE_URL is missing, not https, or contains localhost / 127.0.0.1
- TOOL_WEBHOOK_SECRET is empty
- JAVA_HOME for JDK 21 is missing (try /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home)

Do this in order:

1. Work only in this repo. Copy .env.example to .env if .env does not exist.
2. Load env: `set -a && source .env && set +a`. Never print the API key.
3. If bootRun is not already serving 8080, start it in the background with JAVA_HOME as above and `bash ./gradlew bootRun`. Wait until it listens on 8080. Spring does not load .env itself; the process must inherit the sourced variables. If bootRun was already running without ELEVENLABS_API_KEY, stop that process and start it again after sourcing .env.
4. Prove the tunnel (must be PUBLIC_BASE_URL, not localhost):
   curl -sS -f -X POST "$PUBLIC_BASE_URL/api/voice/tools/check-availability" \
     -H "Authorization: Bearer $TOOL_WEBHOOK_SECRET" \
     -H "Content-Type: application/json" \
     -d '{"text":"tennis tomorrow evening","partySize":2,"language":"en"}'
   If this fails, stop. ElevenLabs cannot reach the laptop.
5. Provision:
   curl -sS -f -X POST "$PUBLIC_BASE_URL/api/voice/provision" \
     -H "Authorization: Bearer $TOOL_WEBHOOK_SECRET"
   Expect createdRemotely=true and a non-empty agentId. If ELEVENLABS_AGENT_ID was already set, do not create a second agent.
6. Upsert ELEVENLABS_AGENT_ID in .env to the returned agentId. Do not write the API key, tool ids, or anything else into git. If checkToolId/bookToolId are in the JSON, mention them in the chat reply only.
7. Stop. Do not import SIP. Do not commit. Do not push.

Done when .env has ELEVENLABS_AGENT_ID and provision JSON has createdRemotely=true.
```

## Agent prompt B: SIP (Jenya)

Paste after prompt A succeeded, spare Telnyx SIP creds are in `.env`, and that DID's inbound SIP points at `sip:sip.rtc.elevenlabs.io:5060;transport=tcp`. Same machine as `bootRun`. Do not commit `.env`.

```
You are an implementation agent in the Courtly repo. Read docs/VOICE.md first. Execute; do not ask for a plan.

Goal: import the spare Telnyx SIP number onto the existing ElevenLabs agent.

Hard stops (print the missing item and stop):
- ELEVENLABS_API_KEY, ELEVENLABS_AGENT_ID, PUBLIC_BASE_URL (https, not localhost), TOOL_WEBHOOK_SECRET, SIP_FROM_NUMBER, SIP_USERNAME, or SIP_PASSWORD is empty
- SIP_FROM_NUMBER is the AI receptionist DID. Use the spare Courtly DID only.

Do this in order:

1. `set -a && source .env && set +a`. Never print passwords.
2. If bootRun is running without those SIP vars, restart it so the process inherits them.
3. curl -sS -f -X POST "$PUBLIC_BASE_URL/api/voice/tools/check-availability" with the tool bearer and {"text":"tennis tomorrow evening","partySize":2,"language":"en"}. If this fails, stop.
4. curl -sS -f -X POST "$PUBLIC_BASE_URL/api/voice/provision" with the same bearer. This must not create a second agent. It should import the SIP number when ELEVENLABS_PHONE_NUMBER_ID is empty.
5. Upsert ELEVENLABS_PHONE_NUMBER_ID in .env from sip.phoneNumberId in the JSON. Expect sip.status=ready.
6. Stop. Do not commit. Do not push.

Done when .env has ELEVENLABS_PHONE_NUMBER_ID and provision JSON has sip.status=ready.
```
