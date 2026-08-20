"""The family's channel for a funeral arrangement.

This is deliberately not a booking bot, because a funeral is not a booking.

In an ordinary reservation the person who books is the person who attends and the
person who pays, and that person picks a date off a grid. Here all three come apart:
the subject of the arrangement is deceased and cannot choose, the payer is a grieving
relative acting under pressure, and nobody chooses the date at all. It is derived —
from the death certificate's release, the observance, the statutory limit and what the
venue has free. The backend derives that window; this bot's whole job is to collect the
facts it needs, show the family the reasoning, and take one answer: yes, or not this.

Two things follow, and they are why the channel exists at all:

  * A funeral runs against a hard clock, and a clock needs to reach people rather than
    wait to be visited. Telegram pushes; a web page cannot. Every proposal carries the
    hour by which an answer is needed, and the bot comes back before it passes.

  * The decision is not one person's. Add the bot to the family group and the whole
    family sees the same proposal, and whoever answers is named. Credentials belong to
    a person, the arrangement belongs to the chat — which is exactly the shape of a
    family where the payer, the next of kin and the executor are three different people.

Talks to the same Spring Boot intent API the web app wraps.
"""

import asyncio
import html
import logging
import os
import re
import time
from datetime import datetime
from typing import Any, Dict, List, Optional, Tuple

import httpx
from telegram import InlineKeyboardButton, InlineKeyboardMarkup, Update
from telegram.constants import ParseMode
from telegram.ext import (
    Application,
    CallbackQueryHandler,
    CommandHandler,
    ContextTypes,
    MessageHandler,
    filters,
)

API_BASE = os.getenv("API_BASE", "https://brunt-greedily-sweep.ngrok-free.dev").rstrip("/")
TELEGRAM_BOT_TOKEN = os.getenv("TELEGRAM_BOT_TOKEN")

PAYMENT_METHOD = os.getenv("PAYMENT_METHOD", "INVOICE")

# ---------------------------------------------------------------- pivot seams
# Everything domain-specific is an env override so retargeting the catalogue
# (ResourceType, seed.yml, the brand) never requires editing this file.

BRAND = os.getenv("BOT_BRAND_NAME", "EverRest")

# Words that mark a message as a fresh arrangement request rather than an answer
# to the question on screen. Override with BOT_SERVICE_WORDS (comma-separated).
DEFAULT_SERVICE_WORDS = (
    "funeral", "service", "ceremony", "chapel", "cremation", "burial", "grave",
    "plot", "interment", "hearse", "transport", "wake", "reception", "viewing",
)
_service_words_raw = os.getenv("BOT_SERVICE_WORDS")
SERVICE_WORDS = (
    tuple(w.strip().lower() for w in _service_words_raw.split(",") if w.strip())
    if _service_words_raw
    else DEFAULT_SERVICE_WORDS
)

# What the family can be arranging. "Label=phrase": the label goes on the button, the
# phrase is appended to the request text for the backend's own parser to resolve, so
# this file never needs to know the ResourceType enum. Override with
# BOT_SERVICE_OPTIONS, entries separated by "|".
DEFAULT_SERVICE_OPTIONS = (
    "Chapel ceremony=chapel service|"
    "Cremation=cremation|"
    "Burial=burial plot|"
    "Transport=hearse transport|"
    "Reception=wake reception"
)
SERVICE_OPTIONS: List[Tuple[str, str]] = [
    (part.split("=", 1)[0].strip(), part.split("=", 1)[1].strip())
    for part in os.getenv("BOT_SERVICE_OPTIONS", DEFAULT_SERVICE_OPTIONS).split("|")
    if "=" in part
]

# Observances offered as buttons. The phrase must match a rite phrase the backend's
# RiteProperties knows. Override with BOT_RITE_OPTIONS.
DEFAULT_RITE_OPTIONS = (
    "Catholic=catholic|"
    "Orthodox=orthodox|"
    "Jewish=jewish|"
    "Muslim=muslim|"
    "Protestant=protestant|"
    "No religious service=humanist"
)
RITE_OPTIONS: List[Tuple[str, str]] = [
    (part.split("=", 1)[0].strip(), part.split("=", 1)[1].strip())
    for part in os.getenv("BOT_RITE_OPTIONS", DEFAULT_RITE_OPTIONS).split("|")
    if "=" in part
]

EXAMPLE_INTENT = os.getenv(
    "BOT_EXAMPLE_INTENT",
    "my father died yesterday, orthodox service, about 40 mourners",
)

MOURNER_CHOICES = [8, 20, 40, 60, 100, 150]

# Said whenever the API stops accepting the token. It has to promise the resume: a
# family that has just typed out how their father died will not do it twice.
SIGN_IN_AGAIN = (
    "Your session has expired. Sign in again with /login — nothing you have told me "
    "is lost, and I will pick up exactly where we left off."
)

# Reminders fire at the midpoint of the remaining time, kept inside these bounds so a
# long window does not go silent for days and a short one is not spammed.
REMINDER_MIN_SECONDS = 15 * 60
REMINDER_MAX_SECONDS = 12 * 3600

logging.basicConfig(
    format="%(asctime)s %(levelname)s %(name)s %(message)s", level=logging.INFO
)
log = logging.getLogger("everrest_bot")


# ------------------------------------------------------------------- state
# Credentials belong to a person; the arrangement belongs to the chat. In a private
# chat the two coincide. In a family group they do not, and that is the point.


class Session:
    """One signed-in Telegram user."""

    def __init__(self) -> None:
        self.auth_step: Optional[str] = None  # "username" | "password" | None
        self.pending_username: Optional[str] = None
        self.username: Optional[str] = None
        self.token: Optional[str] = None
        self.expires_at: Optional[float] = None
        self.display_name: Optional[str] = None

    def invalidate(self) -> None:
        """The server refused the token. Our own 12h clock says it is still good — the
        signing key changes when the API restarts — so drop it, or every later message
        walks into the same 401. The arrangement is untouched: it belongs to the chat,
        not to the credentials, and the family should never retype it."""
        self.token = None
        self.expires_at = None
        self.auth_step = None
        self.pending_username = None


class Arrangement:
    """One family's arrangement, shared by everyone in the chat."""

    def __init__(self) -> None:
        self.intent: Optional[str] = None
        # Facts stated so far, keyed by the gap they close. Values are phrases the
        # backend's own parser understands, so the bot completes the family's sentence
        # rather than inventing a structured payload of its own.
        self.facts: Dict[str, str] = {}
        self.mourners: Optional[int] = None
        self.asking: Optional[str] = None
        self.spec: Optional[dict] = None
        self.window: Optional[dict] = None
        self.suggestions: List[dict] = []
        self.query_id: int = 0
        self.reminder: Optional[asyncio.Task] = None

    def reset_request(self) -> None:
        self.intent = None
        self.facts = {}
        self.mourners = None
        self.asking = None
        self.spec = None
        self.window = None
        self.suggestions = []
        self.query_id += 1
        self.cancel_reminder()

    def cancel_reminder(self) -> None:
        if self.reminder and not self.reminder.done():
            self.reminder.cancel()
        self.reminder = None


sessions: Dict[int, Session] = {}
arrangements: Dict[int, Arrangement] = {}


def get_session(user_id: int) -> Session:
    return sessions.setdefault(user_id, Session())


def get_arrangement(chat_id: int) -> Arrangement:
    return arrangements.setdefault(chat_id, Arrangement())


def esc(text: Optional[str]) -> str:
    return html.escape(text or "")


def token_valid(se: Session) -> bool:
    return bool(se.token) and bool(se.expires_at) and time.time() < se.expires_at


def auth_headers(se: Session) -> Dict[str, str]:
    return {"Content-Type": "application/json", "Authorization": f"Bearer {se.token}"}


def is_private(update: Update) -> bool:
    return update.effective_chat.type == "private"


def actor_name(update: Update) -> str:
    """Who tapped. In a group every answer is attributed; in a private chat it is
    still recorded, because the person who confirms may not be the person who pays."""
    user = update.effective_user
    se = sessions.get(user.id) if user else None
    if se and se.display_name:
        return se.display_name
    return user.full_name if user else "someone"


# --------------------------------------------------------------------- api


async def api_login(se: Session, username: str, password: str) -> bool:
    try:
        async with httpx.AsyncClient(timeout=20) as client:
            r = await client.post(
                f"{API_BASE}/api/auth/token",
                json={"username": username, "password": password},
            )
    except httpx.HTTPError as exc:
        log.warning("login HTTP error: %s", exc)
        return False
    if r.status_code != 200:
        return False
    data = r.json()
    se.token = data["token"]
    se.display_name = data.get("displayName") or username
    se.username = username
    try:
        se.expires_at = datetime.fromisoformat(data["expiresAt"]).timestamp()
    except (KeyError, ValueError, TypeError):
        se.expires_at = time.time() + 12 * 3600
    se.auth_step = None
    return True


async def api_suggest(se: Session, text: str, mourners: Optional[int]) -> httpx.Response:
    payload: Dict[str, Any] = {"text": text}
    if mourners:
        payload["partySize"] = mourners
    async with httpx.AsyncClient(timeout=30) as client:
        return await client.post(
            f"{API_BASE}/api/intent/suggest", headers=auth_headers(se), json=payload
        )


async def api_book(se: Session, suggestion: dict, mourners: Optional[int]) -> httpx.Response:
    payload = {
        "resourceId": suggestion["resourceId"],
        "start": suggestion["start"],
        "end": suggestion["end"],
        "partySize": mourners,
        "paymentMethod": PAYMENT_METHOD,
    }
    async with httpx.AsyncClient(timeout=30) as client:
        return await client.post(
            f"{API_BASE}/api/intent/book", headers=auth_headers(se), json=payload
        )


async def api_arrangements(se: Session) -> httpx.Response:
    async with httpx.AsyncClient(timeout=20) as client:
        return await client.get(
            f"{API_BASE}/api/intent/arrangements", headers=auth_headers(se)
        )


def error_message(body: Any, fallback: str) -> str:
    if isinstance(body, dict):
        return str(body.get("error") or body.get("message") or fallback)
    return fallback


# ------------------------------------------------------------- formatting


def fmt_datetime(iso: str) -> str:
    try:
        return datetime.fromisoformat(iso).strftime("%a %d %b, %H:%M")
    except (ValueError, TypeError):
        return iso or "—"


def fmt_date(value: Optional[str]) -> str:
    if not value:
        return "—"
    try:
        return datetime.fromisoformat(value).strftime("%a %d %b")
    except ValueError:
        return value


def fmt_clock(iso: str) -> str:
    try:
        return datetime.fromisoformat(iso).strftime("%H:%M")
    except (ValueError, TypeError):
        return ""


def human_delta(seconds: float) -> str:
    if seconds <= 0:
        return "now"
    hours = int(seconds // 3600)
    if hours >= 48:
        return f"in about {hours // 24} days"
    if hours >= 2:
        return f"in about {hours} hours"
    minutes = max(1, int(seconds // 60))
    return f"in about {minutes} minutes"


def seconds_until(iso: Optional[str]) -> Optional[float]:
    if not iso:
        return None
    try:
        return datetime.fromisoformat(iso).timestamp() - time.time()
    except (ValueError, TypeError):
        return None


def relative_to_first(first: dict, other: dict) -> str:
    """How far an alternative sits from the held proposal, in words rather than a score.
    A family comparing funeral times needs the cost of the change, not a ranking."""
    try:
        a = datetime.fromisoformat(first["start"])
        b = datetime.fromisoformat(other["start"])
    except (ValueError, KeyError, TypeError):
        return ""
    day_shift = (b.date() - a.date()).days
    if day_shift == 0:
        hours = round((b - a).total_seconds() / 3600)
        if hours == 0:
            return "same time, another venue"
        return f"same day, {abs(hours)} hours {'later' if hours > 0 else 'earlier'}"
    if abs(day_shift) == 1:
        return "one day later" if day_shift > 0 else "one day earlier"
    return f"{abs(day_shift)} days {'later' if day_shift > 0 else 'earlier'}"


# ------------------------------------------------------- gaps in the facts
# The bot mirrors just enough of the backend's fact grammar to know what is still
# missing. It never derives the window itself — that stays in one place, on the server,
# where the web app and the phone intake read the same rules.

_DEATH_PHRASE = re.compile(
    r"\b(?:passed away|passed on|passed|died|death|deceased|we lost)\b"
)
_CERT_PHRASE = re.compile(
    r"\b(?:certificate|coroner|post[- ]?mortem|postmortem|autopsy|inquest|prosecutor)\b"
)
_MOURNERS_PHRASE = re.compile(
    r"\b\d{1,4}\s*(?:mourners|guests|attendees|people|persons|family members)\b"
    r"|\b(?:mourners|guests|attendees|attendance)\b"
    r"|\b(?:family only|just family|close family|immediate family)\b"
)
_DATE_MARKER = re.compile(
    r"\b(?:today|yesterday|this morning|last night|overnight|\d{1,2}\s+days?\s+ago"
    r"|monday|tuesday|wednesday|thursday|friday|saturday|sunday"
    r"|\d{1,2}[/.]\d{1,2}"
    r"|\d{1,2}\s*(?:st|nd|rd|th)?\s*(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)"
    r"|(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\s+\d{1,2})\b"
)


def _stated(ar: Arrangement, key: str, pattern: re.Pattern) -> bool:
    if key in ar.facts:
        return True
    return bool(pattern.search((ar.intent or "").lower()))


def _has_death_date(ar: Arrangement) -> bool:
    if "death" in ar.facts:
        return True
    lower = (ar.intent or "").lower()
    phrase = _DEATH_PHRASE.search(lower)
    if not phrase:
        return False
    return bool(_DATE_MARKER.search(lower[phrase.start(): phrase.end() + 80]))


def _has_rite(ar: Arrangement) -> bool:
    if "rite" in ar.facts:
        return True
    lower = (ar.intent or "").lower()
    return any(re.search(rf"(?<![a-z]){re.escape(phrase)}(?![a-z])", lower)
               for _, phrase in RITE_OPTIONS)


def _has_service(ar: Arrangement) -> bool:
    if "service" in ar.facts:
        return True
    lower = (ar.intent or "").lower()
    return any(word in lower for word in SERVICE_WORDS)


def missing_facts(ar: Arrangement) -> List[str]:
    """What still has to be known, in the order it is least distressing to ask."""
    missing = []
    if not _has_service(ar):
        missing.append("service")
    if not _has_death_date(ar):
        missing.append("death")
    if not _has_rite(ar):
        missing.append("rite")
    if not _stated(ar, "certificate", _CERT_PHRASE):
        missing.append("certificate")
    if ar.mourners is None and not _stated(ar, "mourners", _MOURNERS_PHRASE):
        missing.append("mourners")
    return missing


def build_query(ar: Arrangement) -> str:
    """The family's own words, completed with the answers they gave to buttons."""
    lower = (ar.intent or "").lower()
    parts = [ar.intent or ""]
    for key in ("service", "death", "rite", "certificate", "mourners"):
        phrase = ar.facts.get(key)
        if phrase and phrase.lower() not in lower:
            parts.append(phrase)
    return " ".join(p for p in parts if p).strip()


def is_new_request(text: str) -> bool:
    lower = text.strip().lower()
    if any(word in lower for word in SERVICE_WORDS):
        return True
    return bool(_DEATH_PHRASE.search(lower))


CANCEL_WORDS = ("cancel", "stop", "start over", "start again")


# --------------------------------------------------------------- questions


def btn(label: str, data: str) -> InlineKeyboardButton:
    return InlineKeyboardButton(label, callback_data=data)


def current_send(update: Update):
    if update.callback_query:
        return update.callback_query.edit_message_text
    return update.effective_message.reply_text


def rows_of(pairs: List[Tuple[str, str]], prefix: str, per_row: int = 2) -> List[list]:
    buttons = [btn(label, f"{prefix}:{i}") for i, (label, _) in enumerate(pairs)]
    return [buttons[i: i + per_row] for i in range(0, len(buttons), per_row)]


QUESTIONS = {
    "service": "What needs to be arranged?",
    "death": "When did the death occur?",
    "rite": "Is there an observance to follow?",
    "certificate": "Has the death certificate been released?",
    "mourners": "Roughly how many mourners do you expect?",
}


async def ask(update: Update, ar: Arrangement, field: str) -> None:
    ar.asking = field
    send = current_send(update)
    cancel_row = [btn("Start over", "cancel")]

    if field == "service":
        rows = rows_of(SERVICE_OPTIONS, "service")
    elif field == "death":
        rows = [
            [btn("Today", "death:0"), btn("Yesterday", "death:1")],
            [btn("2 days ago", "death:2"), btn("3 days ago", "death:3")],
            [btn("Longer ago", "death:more")],
        ]
    elif field == "rite":
        rows = rows_of(RITE_OPTIONS, "rite")
    elif field == "certificate":
        rows = [
            [btn("We have it", "cert:have")],
            [btn("Expected in a day or two", "cert:soon")],
            [btn("A coroner is involved", "cert:coroner")],
            [btn("Not sure yet", "cert:unknown")],
        ]
    else:
        rows = [
            [btn(str(n), f"mourners:{n}") for n in MOURNER_CHOICES[i: i + 3]]
            for i in range(0, len(MOURNER_CHOICES), 3)
        ]

    hint = {
        "death": "\nYou can also type a date.",
        "mourners": "\nAn approximate number is fine.",
        "certificate": "\nThis sets the earliest date anything can be held.",
    }.get(field, "")

    await send(
        esc(QUESTIONS[field]) + esc(hint),
        parse_mode=ParseMode.HTML,
        reply_markup=InlineKeyboardMarkup(rows + [cancel_row]),
    )


async def advance(update: Update, context: ContextTypes.DEFAULT_TYPE,
                  ar: Arrangement, se: Session) -> None:
    missing = missing_facts(ar)
    if missing:
        await ask(update, ar, missing[0])
        return
    ar.asking = None
    await propose(context, update.effective_chat.id, ar, se, current_send(update))


# ------------------------------------------------------- the window + card


def render_window(ar: Arrangement) -> List[str]:
    """The derivation, shown before the proposal. A family told they cannot choose the
    date is owed the reason, in the same breath."""
    window = ar.window
    if not window:
        return []
    lines = [
        "<b>The dates this can fall between</b>",
        f"Earliest   <b>{esc(fmt_date(window.get('earliest')))}</b>",
        f"Latest     <b>{esc(fmt_date(window.get('latest')))}</b>",
        "",
    ]
    for reason in window.get("derivation", []):
        lines.append(f"· {esc(reason)}")
    if window.get("note"):
        lines.append(f"· {esc(window['note'])}")
    if not window.get("feasible", True):
        lines.append("")
        lines.append(
            "<b>This window cannot be met.</b> The service is placed as early as the "
            "release allows. Please speak to the funeral director — an extension of the "
            "statutory period has to be filed by hand."
        )
    lines.append("")
    return lines


def render_proposal(ar: Arrangement) -> Tuple[str, InlineKeyboardMarkup]:
    held = ar.suggestions[0]
    lines = [f"<b>{esc(BRAND)} — proposed arrangement</b>", ""]
    lines += render_window(ar)
    lines.append("<b>We are holding</b>")
    lines.append(f"{esc(held.get('resourceName'))} — {esc(held.get('facilityName'))}")
    lines.append(f"{esc(fmt_datetime(held.get('start')))} – {esc(fmt_clock(held.get('end')))}")
    detail = []
    if ar.mourners:
        detail.append(f"{ar.mourners} mourners")
    if held.get("price"):
        detail.append(str(held["price"]))
    if detail:
        lines.append(esc(" · ".join(detail)))

    decision_by = (ar.window or {}).get("decisionBy")
    remaining = seconds_until(decision_by)
    if remaining is not None:
        lines.append("")
        lines.append(
            f"Please confirm by <b>{esc(fmt_datetime(decision_by))}</b> "
            f"({esc(human_delta(remaining))}) for the venue to hold it."
        )

    rows = [[btn("Confirm this arrangement", f"confirm:{ar.query_id}:0")]]
    if len(ar.suggestions) > 1:
        rows.append([btn("This doesn't work", f"alts:{ar.query_id}")])
    else:
        rows.append([btn("This doesn't work", f"escalate:{ar.query_id}")])
    rows.append([btn("Start over", "cancel")])
    return "\n".join(lines), InlineKeyboardMarkup(rows)


def render_alternatives(ar: Arrangement) -> Tuple[str, InlineKeyboardMarkup]:
    held = ar.suggestions[0]
    lines = ["<b>Other dates the window allows</b>", ""]
    rows = []
    for i, s in enumerate(ar.suggestions[1:], start=1):
        shift = relative_to_first(held, s)
        lines.append(
            f"<b>{esc(s.get('resourceName'))}</b> — {esc(s.get('facilityName'))}\n"
            f"{esc(fmt_datetime(s.get('start')))} – {esc(fmt_clock(s.get('end')))}"
            + (f"  ({esc(shift)})" if shift else "")
            + (f"\n{esc(str(s['price']))}" if s.get("price") else "")
        )
        lines.append("")
        rows.append([btn(f"Choose {fmt_datetime(s.get('start'))}", f"confirm:{ar.query_id}:{i}")])
    lines.append("Every one of these is inside the window; none of them can be moved outside it.")
    rows.append([btn("None of these work", f"escalate:{ar.query_id}")])
    rows.append([btn("Start over", "cancel")])
    return "\n".join(lines), InlineKeyboardMarkup(rows)


def render_escalation(ar: Arrangement) -> str:
    window = ar.window or {}
    lines = [
        "<b>Nothing here fits.</b>",
        "",
        "A member of staff has to take this by hand. Nothing has been reserved.",
        "When you call the funeral home, this is what we had:",
        "",
    ]
    if window:
        lines.append(f"Window: {esc(fmt_date(window.get('earliest')))} to {esc(fmt_date(window.get('latest')))}")
        if window.get("rite"):
            lines.append(f"Observance: {esc(str(window['rite']).title())}")
    if ar.mourners:
        lines.append(f"Mourners: {ar.mourners}")
    if ar.intent:
        lines.append(f"In your words: {esc(ar.intent)}")
    return "\n".join(lines)


async def propose(context: ContextTypes.DEFAULT_TYPE, chat_id: int,
                  ar: Arrangement, se: Session, send) -> None:
    if not token_valid(se):
        await send(SIGN_IN_AGAIN)
        return

    try:
        r = await api_suggest(se, build_query(ar), ar.mourners)
    except httpx.HTTPError as exc:
        log.warning("suggest HTTP error: %s", exc)
        await send("We could not reach the booking service. Please try again in a moment.")
        return

    if r.status_code == 401:
        se.invalidate()
        await send(SIGN_IN_AGAIN)
        return
    if r.status_code != 200:
        await send(
            esc(error_message(_json_or_none(r), f"The request failed ({r.status_code}).")),
            parse_mode=ParseMode.HTML,
        )
        return

    data = r.json()
    ar.spec = data.get("spec")
    ar.window = data.get("window")
    if ar.window and (data.get("facts") or {}).get("mourners") and not ar.mourners:
        ar.mourners = data["facts"]["mourners"]
    ar.suggestions = data.get("suggestions", [])

    if not ar.suggestions:
        lines = [f"<b>{esc(BRAND)}</b>", ""]
        lines += render_window(ar)
        lines.append(
            "Nothing is free inside that window. The dates cannot be moved, so this "
            "needs a member of staff."
        )
        await send(
            "\n".join(lines),
            parse_mode=ParseMode.HTML,
            reply_markup=InlineKeyboardMarkup([
                [btn("Show me what to tell them", f"escalate:{ar.query_id}")],
                [btn("Start over", "cancel")],
            ]),
        )
        return

    text, keyboard = render_proposal(ar)
    await send(text, parse_mode=ParseMode.HTML, reply_markup=keyboard)
    # The clock is the reason this lives in Telegram; attach it as the proposal goes out.
    schedule_reminder(context.application, chat_id, ar)


def _json_or_none(r: httpx.Response) -> Any:
    try:
        return r.json()
    except ValueError:
        return None


# ------------------------------------------------------------- the clock
# The reason this lives in Telegram at all: a web page waits to be visited, and a
# funeral does not wait. One nudge, at the midpoint of the time remaining.


def schedule_reminder(app, chat_id: int, ar: Arrangement) -> None:
    ar.cancel_reminder()
    remaining = seconds_until((ar.window or {}).get("decisionBy"))
    if remaining is None or remaining <= REMINDER_MIN_SECONDS:
        return
    delay = min(max(remaining / 2, REMINDER_MIN_SECONDS), REMINDER_MAX_SECONDS)
    query_id = ar.query_id
    ar.reminder = asyncio.create_task(_remind(app, chat_id, ar, query_id, delay))


async def _remind(app, chat_id: int, ar: Arrangement, query_id: int, delay: float) -> None:
    try:
        await asyncio.sleep(delay)
    except asyncio.CancelledError:
        return
    # A newer request, or a confirmation, has superseded this one.
    if ar.query_id != query_id or not ar.suggestions:
        return
    left = seconds_until((ar.window or {}).get("decisionBy"))
    if left is None or left <= 0:
        return
    held = ar.suggestions[0]
    try:
        await app.bot.send_message(
            chat_id,
            f"<b>Still waiting on an answer.</b>\n\n"
            f"{esc(held.get('resourceName'))} — {esc(fmt_datetime(held.get('start')))}\n"
            f"The venue holds it until {esc(fmt_datetime((ar.window or {}).get('decisionBy')))} "
            f"({esc(human_delta(left))}). After that the slot is released and the window "
            f"does not move.",
            parse_mode=ParseMode.HTML,
            reply_markup=InlineKeyboardMarkup([
                [btn("Confirm this arrangement", f"confirm:{query_id}:0")],
                [btn("This doesn't work", f"alts:{query_id}")],
            ]),
        )
    except Exception as exc:  # network, blocked bot, deleted chat
        log.warning("reminder failed for chat %s: %s", chat_id, exc)


# -------------------------------------------------------------- commands


async def cmd_start(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    se = get_session(update.effective_user.id)
    if token_valid(se):
        await update.effective_message.reply_text(
            f"<b>{esc(BRAND)}</b>\n\n"
            f"Signed in as {esc(se.display_name)}.\n\n"
            "Tell me what has happened, in your own words. For example:\n"
            f"<i>{esc(EXAMPLE_INTENT)}</i>\n\n"
            "I will work out the dates the service can fall between and propose one.",
            parse_mode=ParseMode.HTML,
        )
        return
    await update.effective_message.reply_text(
        f"<b>{esc(BRAND)}</b>\n\n"
        "You do not have to choose a date. Tell us the circumstances and we will work "
        "out when the service can be held — the certificate, the observance and the law "
        "decide that between them — then propose a time for you to approve.\n\n"
        "Sign in with /login to begin. /help explains the rest.",
        parse_mode=ParseMode.HTML,
    )


async def cmd_login(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    se = get_session(update.effective_user.id)
    if not is_private(update):
        # Never collect a password in a room full of people.
        await update.effective_message.reply_text(
            "Please message me privately to sign in. Once you have, you can act on this "
            "family's arrangement from here."
        )
        return
    if token_valid(se):
        await update.effective_message.reply_text(
            f"Already signed in as <b>{esc(se.display_name)}</b> "
            f"(until {datetime.fromtimestamp(se.expires_at):%d %b %H:%M}). "
            "Use /logout to change account.",
            parse_mode=ParseMode.HTML,
        )
        return
    se.auth_step = "username"
    se.pending_username = None
    await update.effective_message.reply_text("Username?")


async def cmd_logout(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    sessions.pop(update.effective_user.id, None)
    await update.effective_message.reply_text("Signed out.")


async def cmd_cancel(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    get_arrangement(update.effective_chat.id).reset_request()
    se = get_session(update.effective_user.id)
    se.auth_step = None
    se.pending_username = None
    await update.effective_message.reply_text(
        "Cleared. Tell me about the arrangement again whenever you are ready."
    )


async def cmd_help(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    await update.effective_message.reply_text(
        f"<b>{esc(BRAND)}</b>\n\n"
        "Describe the circumstances and I will answer with the window the service must "
        "fall inside, why it is that window, and one time held for you to approve.\n\n"
        "/login — sign in (privately)\n"
        "/logout — sign out\n"
        "/status — this arrangement and your session\n"
        "/my_arrangements — everything you have arranged\n"
        "/cancel — clear the current arrangement\n\n"
        "You can add me to a family group. The arrangement is shared by everyone in the "
        "chat; each person signs in privately and whoever answers is named.\n\n"
        f"For example: <i>{esc(EXAMPLE_INTENT)}</i>",
        parse_mode=ParseMode.HTML,
    )


async def cmd_status(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    se = get_session(update.effective_user.id)
    ar = get_arrangement(update.effective_chat.id)
    lines = []
    if token_valid(se):
        lines.append(f"Signed in as <b>{esc(se.display_name)}</b> ({esc(se.username)})")
        lines.append(f"Session valid until {datetime.fromtimestamp(se.expires_at):%d %b %H:%M}.")
    else:
        lines.append("Not signed in. Use /login.")
    if ar.window:
        lines.append("")
        lines.append(
            f"Window: {esc(fmt_date(ar.window.get('earliest')))} to "
            f"{esc(fmt_date(ar.window.get('latest')))}"
        )
        remaining = seconds_until(ar.window.get("decisionBy"))
        if remaining is not None and remaining > 0:
            lines.append(f"An answer is needed {esc(human_delta(remaining))}.")
    elif ar.intent:
        lines.append("")
        lines.append("An arrangement is in progress; some details are still missing.")
    await update.effective_message.reply_text("\n".join(lines), parse_mode=ParseMode.HTML)


async def cmd_my_arrangements(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    """Everything this person has standing, whichever chat it was arranged in. The
    arrangement in progress belongs to the chat; what has been confirmed belongs to the
    account that confirmed it, so this answers from the server, not from memory."""
    se = get_session(update.effective_user.id)
    if not token_valid(se):
        await update.effective_message.reply_text(
            "Please sign in with /login to see what you have arranged."
        )
        return

    try:
        r = await api_arrangements(se)
    except httpx.HTTPError as exc:
        log.warning("arrangements HTTP error: %s", exc)
        await update.effective_message.reply_text(
            "We could not reach the funeral home's system. Please try again in a moment."
        )
        return

    if r.status_code == 401:
        se.invalidate()
        await update.effective_message.reply_text(SIGN_IN_AGAIN)
        return
    if r.status_code != 200:
        await update.effective_message.reply_text(
            esc(error_message(_json_or_none(r), f"The request failed ({r.status_code}).")),
            parse_mode=ParseMode.HTML,
        )
        return

    items = r.json()
    if not items:
        await update.effective_message.reply_text(
            "You have nothing arranged yet. Tell me what has happened and I will "
            "propose a time."
        )
        return

    lines = [f"<b>Arranged for {esc(se.display_name)}</b>", ""]
    for item in items:
        lines.append(
            f"<b>{esc(item.get('resourceName'))}</b> — {esc(item.get('facilityName'))}\n"
            f"{esc(fmt_datetime(item.get('start')))} – {esc(fmt_clock(item.get('end')))}\n"
            f"{esc(str(item.get('status', '')).title())} · "
            f"Reference {esc(str(item.get('reservationId')))}"
            + (f" · {esc(str(item['totalAmount']))}" if item.get("totalAmount") else "")
        )
        lines.append("")
    lines.append("Quote the reference when you call the funeral home.")
    await update.effective_message.reply_text("\n".join(lines), parse_mode=ParseMode.HTML)


# --------------------------------------------------------------- messages


async def handle_text(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    se = get_session(update.effective_user.id)
    ar = get_arrangement(update.effective_chat.id)
    text = (update.effective_message.text or "").strip()

    # Credentials are only ever collected in a one-to-one chat.
    if se.auth_step and is_private(update):
        await handle_auth(update, context, se, ar, text)
        return

    if not token_valid(se):
        if is_private(update):
            await update.effective_message.reply_text(
                "Please sign in with /login, then I will carry on from what you have "
                "already told me." if ar.intent else
                "Please sign in first with /login, then tell me about the arrangement."
            )
        return

    if not text:
        return

    if text.lower() in CANCEL_WORDS:
        ar.reset_request()
        await update.effective_message.reply_text(
            "Cleared. Tell me about the arrangement again whenever you are ready."
        )
        return

    # A pending question is answered by what was typed, unless the message reads as a
    # fresh account of the circumstances, which replaces the arrangement instead.
    if ar.asking and not is_new_request(text):
        if apply_typed_answer(ar, text):
            ar.asking = None
            await advance(update, context, ar, se)
        else:
            await ask(update, ar, ar.asking)
        return

    ar.reset_request()
    ar.intent = text
    await advance(update, context, ar, se)


async def handle_auth(update: Update, context: ContextTypes.DEFAULT_TYPE,
                      se: Session, ar: Arrangement, text: str) -> None:
    if se.auth_step == "username":
        se.pending_username = text
        se.auth_step = "password"
        await update.effective_message.reply_text("Password?")
        return
    ok = await api_login(se, se.pending_username or "", text)
    se.pending_username = None
    if ok:
        if ar.intent:
            # They were already part-way through when the session died. Resume on the
            # facts this chat still holds rather than making them tell it again.
            await update.effective_message.reply_text(
                f"Signed in as <b>{esc(se.display_name)}</b>. Picking up where we left off.",
                parse_mode=ParseMode.HTML,
            )
            await advance(update, context, ar, se)
            return
        await update.effective_message.reply_text(
            f"Signed in as <b>{esc(se.display_name)}</b>.\n\n"
            "Tell me what has happened, in your own words.",
            parse_mode=ParseMode.HTML,
        )
    else:
        se.auth_step = None
        await update.effective_message.reply_text(
            "That did not sign you in. Try /login again."
        )


def apply_typed_answer(ar: Arrangement, text: str) -> bool:
    """A typed reply to the question on screen. Anything the backend can parse is passed
    through verbatim; only a mourner count has to be a number here."""
    field = ar.asking
    lower = text.strip().lower()
    if field == "mourners":
        match = re.search(r"\d{1,4}", lower)
        if not match:
            return False
        ar.mourners = int(match.group(0))
        ar.facts["mourners"] = f"for {ar.mourners} mourners"
        return True
    if field == "death":
        ar.facts["death"] = f"the death was {lower}"
        return True
    if field in ("service", "rite", "certificate"):
        ar.facts[field] = lower
        return True
    return False


# -------------------------------------------------------------- callbacks


def stale(ar: Arrangement, data: str) -> bool:
    try:
        return int(data.split(":")[1]) != ar.query_id
    except (IndexError, ValueError):
        return True


async def require_session(update: Update) -> Optional[Session]:
    """Anyone in a family group may answer, but only on their own credentials."""
    se = get_session(update.effective_user.id)
    if token_valid(se):
        return se
    await update.callback_query.answer(
        "Please message me privately and sign in with /login first.", show_alert=True
    )
    return None


async def cb_fact(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    query = update.callback_query
    await query.answer()
    se = await require_session(update)
    if se is None:
        return
    ar = get_arrangement(update.effective_chat.id)
    kind, value = query.data.split(":", 1)

    if kind == "service":
        ar.facts["service"] = SERVICE_OPTIONS[int(value)][1]
    elif kind == "rite":
        ar.facts["rite"] = RITE_OPTIONS[int(value)][1]
    elif kind == "death":
        if value == "more":
            ar.asking = "death"
            await query.edit_message_text(
                "When did the death occur? Please type the date, for example "
                "<i>12 August</i> or <i>12/08</i>.",
                parse_mode=ParseMode.HTML,
            )
            return
        days = int(value)
        ar.facts["death"] = (
            "the death was today" if days == 0
            else "the death was yesterday" if days == 1
            else f"the death was {days} days ago"
        )
    elif kind == "cert":
        ar.facts["certificate"] = {
            "have": "we have the death certificate",
            "soon": "the certificate is not ready yet",
            "coroner": "the coroner has the body",
            "unknown": "the certificate is not ready yet",
        }[value]
    elif kind == "mourners":
        ar.mourners = int(value)
        ar.facts["mourners"] = f"for {ar.mourners} mourners"

    ar.asking = None
    await advance(update, context, ar, se)


async def cb_alts(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    query = update.callback_query
    await query.answer()
    ar = get_arrangement(update.effective_chat.id)
    if stale(ar, query.data) or not ar.suggestions:
        await query.edit_message_text("That proposal has lapsed. Please start again.")
        return
    text, keyboard = render_alternatives(ar)
    await query.edit_message_text(text, parse_mode=ParseMode.HTML, reply_markup=keyboard)


async def cb_escalate(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    query = update.callback_query
    await query.answer()
    ar = get_arrangement(update.effective_chat.id)
    if stale(ar, query.data):
        await query.edit_message_text("That proposal has lapsed. Please start again.")
        return
    ar.cancel_reminder()
    await query.edit_message_text(render_escalation(ar), parse_mode=ParseMode.HTML)


async def cb_confirm(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    query = update.callback_query
    await query.answer()
    se = await require_session(update)
    if se is None:
        return
    ar = get_arrangement(update.effective_chat.id)
    if stale(ar, query.data):
        await query.edit_message_text("That proposal has lapsed. Please start again.")
        return
    index = int(query.data.split(":")[2])
    if index < 0 or index >= len(ar.suggestions):
        await query.edit_message_text("That time is no longer held. Please start again.")
        return

    suggestion = ar.suggestions[index]
    who = actor_name(update)
    await query.edit_message_text("Confirming…")

    try:
        r = await api_book(se, suggestion, ar.mourners)
    except httpx.HTTPError as exc:
        log.warning("book HTTP error: %s", exc)
        await query.edit_message_text(
            "We could not reach the booking service. Nothing has been confirmed — "
            "please try again in a moment."
        )
        return

    if r.status_code == 401:
        se.invalidate()
        # Nothing was booked, and the hold has not moved. Put the card back with its
        # button so signing in is the only step between here and a confirmation.
        text, keyboard = render_proposal(ar)
        await query.edit_message_text(
            "<b>Nothing was confirmed — your session had expired.</b>\n"
            "Sign in again with /login, then confirm below. This is still held.\n\n"
            + text,
            parse_mode=ParseMode.HTML,
            reply_markup=keyboard,
        )
        return

    if r.status_code != 200:
        await query.edit_message_text(
            "Nothing was confirmed. "
            + esc(error_message(_json_or_none(r), f"The request failed ({r.status_code}).")),
            parse_mode=ParseMode.HTML,
        )
        return

    data = r.json()
    ar.cancel_reminder()
    ar.suggestions = []
    await query.edit_message_text(
        "<b>Confirmed.</b>\n\n"
        f"{esc(suggestion.get('resourceName'))} — {esc(suggestion.get('facilityName'))}\n"
        f"{esc(fmt_datetime(suggestion.get('start')))} – {esc(fmt_clock(suggestion.get('end')))}\n"
        f"Reference {esc(str(data.get('reservationId')))} · {esc(str(data.get('totalAmount')))}\n\n"
        f"Confirmed by {esc(who)}.\n"
        "The funeral home has been notified and will be in touch about the rest.",
        parse_mode=ParseMode.HTML,
    )


async def cb_cancel(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    query = update.callback_query
    await query.answer()
    get_arrangement(update.effective_chat.id).reset_request()
    await query.edit_message_text(
        "Cleared. Tell me about the arrangement again whenever you are ready."
    )


def main() -> None:
    if not TELEGRAM_BOT_TOKEN:
        raise SystemExit(
            "TELEGRAM_BOT_TOKEN is not set. "
            "Create a bot with @BotFather and export the token."
        )
    app = Application.builder().token(TELEGRAM_BOT_TOKEN).build()
    app.add_handler(CommandHandler("start", cmd_start))
    app.add_handler(CommandHandler("login", cmd_login))
    app.add_handler(CommandHandler("logout", cmd_logout))
    app.add_handler(CommandHandler("cancel", cmd_cancel))
    app.add_handler(CommandHandler("help", cmd_help))
    app.add_handler(CommandHandler("status", cmd_status))
    app.add_handler(CommandHandler("my_arrangements", cmd_my_arrangements))
    app.add_handler(CallbackQueryHandler(cb_fact, pattern=r"^(?:service|rite|mourners):\d+$"))
    app.add_handler(CallbackQueryHandler(cb_fact, pattern=r"^death:(?:\d+|more)$"))
    app.add_handler(CallbackQueryHandler(cb_fact, pattern=r"^cert:[a-z]+$"))
    app.add_handler(CallbackQueryHandler(cb_alts, pattern=r"^alts:\d+$"))
    app.add_handler(CallbackQueryHandler(cb_escalate, pattern=r"^escalate:\d+$"))
    app.add_handler(CallbackQueryHandler(cb_confirm, pattern=r"^confirm:\d+:\d+$"))
    app.add_handler(CallbackQueryHandler(cb_cancel, pattern=r"^cancel$"))
    app.add_handler(MessageHandler(filters.TEXT & ~filters.COMMAND, handle_text))
    log.info("%s bot polling on %s", BRAND, API_BASE)
    app.run_polling(allowed_updates=Update.ALL_TYPES)


if __name__ == "__main__":
    main()
