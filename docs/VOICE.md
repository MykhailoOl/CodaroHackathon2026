# Courtly phone wiring plan

This is the **only** document for phone booking. Humans and coding agents follow it after PR merge. Do not invent a parallel plan. Do not use Codaro Builder. Do not commit `.env`.

Phone is a second channel into the same intent engine as the website chatbot (`POST /api/intent/suggest`, `POST /api/intent/book`). Bookings write this app’s local H2 file (`./data/sportsbooking`) on the laptop that runs `bootRun`. There is no Google Calendar. The call does not collect email. After a booking, Courtly logs an SMS with `/voice/invite/{token}` so the caller can add a calendar invitation later.

```
caller → spare Telnyx DID → SIP → ElevenLabs agent
      → HTTPS $PUBLIC_BASE_URL/api/voice/tools/*
      → this Java process → local H2
```

ElevenLabs and Telnyx cannot see `localhost`. A public HTTPS tunnel (ngrok is the default) is required. Keep the laptop awake for the demo. Do not reuse the AI receptionist DID.

## Pickup triggers for coding agents

Read this whole file, then execute **exactly one** open phase. Do not ask for a new plan. Do not commit. Do not push. Never print secrets.

| Trigger (all must be true) | Your job | Stop when |
|----------------------------|----------|-----------|
| `ELEVENLABS_API_KEY` is non-empty in `.env`, `ELEVENLABS_AGENT_ID` is empty, `PUBLIC_BASE_URL` is public `https` | **Phase 2** | `.env` has `ELEVENLABS_AGENT_ID`, provision JSON has `createdRemotely=true` |
| `SIP_FROM_NUMBER`, `SIP_USERNAME`, `SIP_PASSWORD` are set, `ELEVENLABS_AGENT_ID` is set, `ELEVENLABS_PHONE_NUMBER_ID` is empty | **Phase 3** | `.env` has `ELEVENLABS_PHONE_NUMBER_ID`, provision JSON has `sip.status=ready` |
| Key empty, or SIP empty, or tunnel not https | **Do not provision.** Print the missing gate and stop | Human fills `.env` / ngrok, then re-run |

Spring does not load `.env`. Always `set -a && source .env && set +a` before `bootRun` or curl. If `bootRun` started before the new vars, restart it.

---

## Phase 1. Laptop, ngrok, app (human or local agent)

1. Copy `.env.example` to `.env` if needed.
2. Start a tunnel to Courtly’s port `8080`:

```bash
ngrok http 8080
```

3. Put the ngrok `https://` forwarding URL into `PUBLIC_BASE_URL`. Not `http://localhost:8080`. Not `127.0.0.1`. Any other HTTPS tunnel is fine if the URL is public.
4. Set `TOOL_WEBHOOK_SECRET` (default in `.env.example` is acceptable for the demo).
5. Start the app:

```bash
set -a && source .env && set +a
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home bash ./gradlew bootRun
```

6. Prove the tunnel (must use `$PUBLIC_BASE_URL`, not localhost):

```bash
curl -sS -f -X POST "$PUBLIC_BASE_URL/api/voice/tools/check-availability" \
  -H "Authorization: Bearer $TOOL_WEBHOOK_SECRET" \
  -H "Content-Type: application/json" \
  -d '{"text":"tennis tomorrow evening","partySize":2,"language":"en"}'
```

Gate to Phase 2: that curl returns JSON with `slots`. Gate to ElevenLabs: teammate pastes `ELEVENLABS_API_KEY` into this same `.env` (their ElevenLabs account).

---

## Phase 2. Action point: ElevenLabs key is in `.env`

**Owner:** teammate coding agent, in this repo, on the same machine that is running `bootRun`.

**Human just did:** put `ELEVENLABS_API_KEY` in `.env`. That does not create the agent. You must provision.

**Do this now:**

1. Confirm `.env` exists. Load it: `set -a && source .env && set +a`. Never print the key.
2. Confirm `ELEVENLABS_API_KEY` is non-empty, `PUBLIC_BASE_URL` starts with `https://` and does not contain `localhost` or `127.0.0.1`, and `TOOL_WEBHOOK_SECRET` is set. If any fail, stop and name the missing item.
3. JDK 21: `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`. If that path is missing, stop.
4. If nothing is listening on `8080`, start `bash ./gradlew bootRun` in the background and wait until it is up. If `bootRun` was already running **without** `ELEVENLABS_API_KEY`, stop it and start it again after sourcing `.env`.
5. Repeat the Phase 1 tunnel curl. If it fails, stop. ElevenLabs cannot reach this laptop.
6. Provision the remote agent and webhook tools:

```bash
curl -sS -f -X POST "$PUBLIC_BASE_URL/api/voice/provision" \
  -H "Authorization: Bearer $TOOL_WEBHOOK_SECRET"
```

Expect `createdRemotely=true` and a non-empty `agentId`. If `ELEVENLABS_AGENT_ID` was already set, do not create a second agent.
7. Upsert `ELEVENLABS_AGENT_ID` in `.env` to that `agentId`. Do not write the API key, tool ids, or SIP into git. Mention `checkToolId` / `bookToolId` in chat only if present.
8. **Stop.** Do not import SIP. Do not commit. Do not push.

What provision created:

- Name: Courtly phone receptionist
- First message: `Hi, this is Courtly. I can check court availability and book a slot for you.`
- Timezone: Europe/Warsaw
- Tools: `check_availability`, `create_booking` (POST, JSON, `Authorization: Bearer $TOOL_WEBHOOK_SECRET`) against `$PUBLIC_BASE_URL/api/voice/tools/*`
- Same intent path as the chatbot: caller words as `text` → suggestions with `resourceId` / `start` / `end` → book. Never invent slots. If the caller omits party size, phone booking uses 2.

If the ngrok URL later changes, update `PUBLIC_BASE_URL`, restart, and either update tool URLs in ElevenLabs or provision a new agent.

---

## Phase 3. Action point: SIP credentials are in `.env`

**Owner:** Jenya’s coding agent, same repo and same `bootRun` machine.

**Human just did:** spare Telnyx `SIP_FROM_NUMBER`, `SIP_USERNAME`, `SIP_PASSWORD` in `.env`, and pointed **that** DID’s inbound SIP at `sip:sip.rtc.elevenlabs.io:5060;transport=tcp`. Not the receptionist number.

**Do this now:**

1. `set -a && source .env && set +a`. Never print passwords.
2. Confirm `ELEVENLABS_API_KEY`, `ELEVENLABS_AGENT_ID`, public https `PUBLIC_BASE_URL`, `TOOL_WEBHOOK_SECRET`, `SIP_FROM_NUMBER`, `SIP_USERNAME`, and `SIP_PASSWORD` are all set. If `ELEVENLABS_AGENT_ID` is empty, stop and run Phase 2 first.
3. Restart `bootRun` if it started before the SIP vars existed.
4. Repeat the Phase 1 tunnel curl. If it fails, stop.
5. Provision again (must not create a second agent; imports SIP when `ELEVENLABS_PHONE_NUMBER_ID` is empty):

```bash
curl -sS -f -X POST "$PUBLIC_BASE_URL/api/voice/provision" \
  -H "Authorization: Bearer $TOOL_WEBHOOK_SECRET"
```

SIP import uses `provider=sip_trunk`, E.164 `SIP_FROM_NUMBER`, digest username/password, outbound `sip.telnyx.com` TCP. Telnyx must send INVITEs to `sip:+E164@sip.rtc.elevenlabs.io:5060`.
6. Upsert `ELEVENLABS_PHONE_NUMBER_ID` in `.env` from `sip.phoneNumberId`. Expect `sip.status=ready`.
7. **Stop.** Do not commit. Do not push.

If provision returns that the DID is already registered in ElevenLabs, that number is claimed on the ElevenLabs side (often another agent/workspace). Release it there or use a different spare DID. Telnyx can still point at `sip.rtc.elevenlabs.io`; ElevenLabs will not import a duplicate.

---

## Phase 4. Call and booking

Call the spare number. A `PENDING` row should appear in `/manager/reservations`. SMS stays log-only until Telnyx messaging keys are set. The SMS body (logged) contains `$PUBLIC_BASE_URL/voice/invite/{token}`.

---

## Env

| Variable | Who / when |
|----------|-------------|
| `PUBLIC_BASE_URL` | Phase 1: ngrok `https://` URL |
| `TOOL_WEBHOOK_SECRET` | Phase 1: bearer for tools and provision |
| `ELEVENLABS_API_KEY` | Human, before Phase 2 |
| `ELEVENLABS_AGENT_ID` | Phase 2 agent writes this |
| `SIP_FROM_NUMBER` / `SIP_USERNAME` / `SIP_PASSWORD` | Human, spare DID only, before Phase 3 |
| `ELEVENLABS_PHONE_NUMBER_ID` | Phase 3 agent writes this |

`.env.example` lists the names. Values stay in local `.env` only.
