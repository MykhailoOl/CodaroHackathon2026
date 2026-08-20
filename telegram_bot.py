"""Courtly intent-booking Telegram bot.

Same flow as the Next.js composer, but inside Telegram:

    /login (username + password) -> per-user session token
    free text intent -> party size buttons -> ranked suggestions -> Book button

Talk to the Spring Boot intent API (the same one the web app wraps).
"""

import asyncio
import html
import logging
import os
import re
import time
from datetime import datetime
from typing import Any, Dict, Optional

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

PAYMENT_METHOD = os.getenv("PAYMENT_METHOD", "CASH")

PARTY_CHOICES = [1, 2, 3, 4, 6, 8, 10, 12]

# Typical party-size ranges per resource type (README), used to explain why a
# search came back empty and to steer the user toward a fixable constraint.
SPORT_PARTY_RANGES = {
    "TENNIS": (2, 4),
    "SQUASH": (2, 4),
    "FOOTBALL": (2, 22),
    "BASKETBALL": (2, 10),
    "VOLLEYBALL": (2, 12),
    "GYM": (1, 12),
    "SWIMMING": (1, 8),
}

logging.basicConfig(
    format="%(asctime)s %(levelname)s %(name)s %(message)s", level=logging.INFO
)
log = logging.getLogger("courtly_bot")


class ChatState:
    def __init__(self) -> None:
        self.auth_step: Optional[str] = None  # "username" | "password" | None
        self.pending_username: Optional[str] = None
        self.username: Optional[str] = None
        self.token: Optional[str] = None
        self.expires_at: Optional[float] = None
        self.display_name: Optional[str] = None
        self.intent: Optional[str] = None          # raw request text
        self.clarify: Optional[str] = None         # next question: "day" | "time" | "duration" | "party" | None
        self.day: Optional[str] = None             # parser-friendly phrase ("today", "saturday", ...)
        self.time: Optional[str] = None            # "morning" | "afternoon" | "evening" | None
        self.duration_min: Optional[int] = None
        self.party_size: int = 2
        self.day_decided: bool = False
        self.time_decided: bool = False
        self.duration_decided: bool = False
        self.party_decided: bool = False
        self.suggestions: list = []
        self.spec: Optional[dict] = None
        self.relaxation_trail: list = []
        self.query_id: int = 0  # bumped on every fresh request; stale buttons ignore it


states: Dict[int, ChatState] = {}


def get_state(chat_id: int) -> ChatState:
    return states.setdefault(chat_id, ChatState())


def esc(text: Optional[str]) -> str:
    return html.escape(text or "")


def token_valid(st: ChatState) -> bool:
    return bool(st.token) and bool(st.expires_at) and time.time() < st.expires_at


def auth_headers(st: ChatState) -> Dict[str, str]:
    return {"Content-Type": "application/json", "Authorization": f"Bearer {st.token}"}


async def api_login(st: ChatState, username: str, password: str) -> bool:
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
    st.token = data["token"]
    st.display_name = data.get("displayName") or username
    st.username = username
    try:
        st.expires_at = datetime.fromisoformat(data["expiresAt"]).timestamp()
    except (KeyError, ValueError, TypeError):
        st.expires_at = time.time() + 12 * 3600
    st.auth_step = None
    return True


async def api_suggest(st: ChatState, text: str, party_size: int) -> httpx.Response:
    async with httpx.AsyncClient(timeout=30) as client:
        return await client.post(
            f"{API_BASE}/api/intent/suggest",
            headers=auth_headers(st),
            json={"text": text, "partySize": party_size},
        )


async def api_book(st: ChatState, suggestion: dict, party_size: int) -> httpx.Response:
    payload = {
        "resourceId": suggestion["resourceId"],
        "start": suggestion["start"],
        "end": suggestion["end"],
        "partySize": party_size,
        "paymentMethod": PAYMENT_METHOD,
    }
    async with httpx.AsyncClient(timeout=30) as client:
        return await client.post(
            f"{API_BASE}/api/intent/book",
            headers=auth_headers(st),
            json=payload,
        )


def fmt_time(iso: str) -> str:
    try:
        dt = datetime.fromisoformat(iso)
        return dt.strftime("%a %d %b %H:%M")
    except ValueError:
        return iso


def error_message(body: Any, fallback: str) -> str:
    if isinstance(body, dict):
        return str(body.get("error") or body.get("message") or fallback)
    return fallback


# ------------------------------------------------------ intent detection
# Mirrors the backend's rule grammar so the bot can spot what's missing and
# ask for it instead of sending a half-specified query.

_WEEKDAYS = ["monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"]
_MONTHS = {
    "january": 1, "february": 2, "march": 3, "april": 4, "may": 5, "june": 6,
    "july": 7, "august": 8, "september": 9, "october": 10, "november": 11, "december": 12,
}
_MONTHS3 = {m[:3]: v for m, v in _MONTHS.items()}
_NUM_WORDS = {
    "one": 1, "two": 2, "three": 3, "four": 4, "five": 5,
    "six": 6, "seven": 7, "eight": 8, "nine": 9, "ten": 10,
}
_CLOCK = re.compile(r"\b\d{1,2}(?::\d{2})?\s*(?:am|pm)\b|\b\d{1,2}:\d{2}\b")
_HOURS_RE = re.compile(r"(\d+(?:\.\d+)?)\s*(?:h|hr|hrs|hour|hours)\b")
_MINUTES_RE = re.compile(r"(\d+)\s*(?:m|min|mins|minute|minutes)\b")
_PARTY_N_RE = re.compile(r"for\s+(\d+)\s+(?:people|person|players|ppl)\b")
_PARTY_OF_RE = re.compile(r"\b(?:party|group)\s+of\s+(\d+)\b")
_PARTY_BARE_RE = re.compile(r"\bfor\s+(\d+)(?!\s*(?:hours?|hr|h|minutes?|min)\b)\b")
_PARTY_WORD_RE = re.compile(r"\bfor\s+(one|two|three|four|five|six|seven|eight|nine|ten)(?!\s*(?:hour|hr|h|min|minute)\b)\b")


def _has_day(lower: str) -> bool:
    if any(w in lower for w in ("today", "tomorrow", "weekend", "week")):
        return True
    if any(re.search(rf"\b{w}\b", lower) for w in _WEEKDAYS):
        return True
    if re.search(r"\b\d{1,2}[/.-]\d{1,2}(?:[/.-]\d{2,4})?\b", lower):
        return True
    if re.search(r"\b\d{1,2}(?:st|nd|rd|th)\b", lower):
        return True
    if re.search(r"\b(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)\w*\s+\d{1,2}\b", lower):
        return True
    if re.search(r"\b\d{1,2}\s+(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)\w*\b", lower):
        return True
    return False


def _has_time(lower: str) -> bool:
    if any(w in lower for w in ("morning", "afternoon", "evening", "tonight", "night", "noon", "midday")):
        return True
    return bool(_CLOCK.search(lower))


def _has_duration(lower: str) -> bool:
    if any(p in lower for p in ("hour and a half", "half an hour", "half hour", "quarter hour")):
        return True
    return bool(_HOURS_RE.search(lower) or _MINUTES_RE.search(lower))


def _has_party(lower: str) -> bool:
    if any(p in lower for p in ("people", "person", "players", "ppl", "solo", "just me", "by myself", "with a friend", "with a buddy")):
        return True
    return bool(
        _PARTY_N_RE.search(lower)
        or _PARTY_OF_RE.search(lower)
        or _PARTY_BARE_RE.search(lower)
        or _PARTY_WORD_RE.search(lower)
    )


RESOURCE_WORDS = (
    "tennis", "squash", "football", "soccer", "basketball", "volleyball",
    "badminton", "gym", "workout", "swim", "swimming", "pool", "court", "slot",
)

NEW_REQUEST_HINTS = (
    "instead", "different", "actually", "rather", "change", "another", "other",
    "wait", "hold on", "nevermind", "never mind", "scratch",
)

CANCEL_WORDS = ("cancel", "stop", "quit", "abort", "nevermind", "never mind")


def is_new_booking_request(st: ChatState, text: str) -> bool:
    """True when a typed message looks like a fresh booking request rather than
    an answer to the pending clarification question."""
    lower = text.strip().lower()
    if any(h in lower for h in NEW_REQUEST_HINTS):
        return True
    if any(w in lower for w in RESOURCE_WORDS):
        return True
    return False


def detected_day(lower: str) -> Optional[str]:
    if "today" in lower:
        return "today"
    if "tomorrow" in lower:
        return "tomorrow"
    if "weekend" in lower:
        return "this weekend"
    if "next week" in lower:
        return "next week"
    if "this week" in lower:
        return "this week"
    for w in _WEEKDAYS:
        if re.search(rf"\b{w}\b", lower):
            return w
    return None


def detected_time(lower: str) -> Optional[str]:
    if "morning" in lower:
        return "morning"
    if "afternoon" in lower or "noon" in lower or "midday" in lower:
        return "afternoon"
    if "evening" in lower or "tonight" in lower or "night" in lower:
        return "evening"
    return None


def detected_duration(lower: str) -> Optional[int]:
    if "hour and a half" in lower or "hour and half" in lower:
        return 90
    if "half an hour" in lower or "half hour" in lower:
        return 30
    m = _HOURS_RE.search(lower)
    if m:
        return int(round(float(m.group(1)) * 60))
    m = _MINUTES_RE.search(lower)
    if m:
        return int(m.group(1))
    return None


def detected_party(lower: str) -> Optional[int]:
    if "solo" in lower or "just me" in lower or "by myself" in lower:
        return 1
    if "with a friend" in lower or "with a buddy" in lower:
        return 2
    m = _PARTY_N_RE.search(lower) or _PARTY_OF_RE.search(lower) or _PARTY_BARE_RE.search(lower)
    if m:
        return int(m.group(1))
    m = _PARTY_WORD_RE.search(lower)
    if m:
        return _NUM_WORDS[m.group(1)]
    return None


def normalize_day(text: str) -> Optional[str]:
    """Turn a typed date into a phrase the backend parser understands."""
    lower = text.strip().lower()
    if lower in ("today", "tomorrow", "this weekend", "weekend", "next week", "this week"):
        if lower == "weekend":
            return "this weekend"
        return lower
    for w in _WEEKDAYS:
        if re.search(rf"\b{w}\b", lower):
            return w
    today = datetime.now().date()
    target = None

    m = re.search(r"\b(\d{1,2})(?:st|nd|rd|th)?\s+([a-z]{3,9})\b", lower)
    if m:
        day, mon = int(m.group(1)), _MONTHS.get(m.group(2)) or _MONTHS3.get(m.group(2)[:3])
        if mon:
            try:
                candidate = today.replace(month=mon, day=day)
                if candidate >= today:
                    target = candidate
                else:
                    target = candidate.replace(year=candidate.year + 1)
            except ValueError:
                pass
    if target is None:
        m = re.search(r"\b([a-z]{3,9})\s+(\d{1,2})(?:st|nd|rd|th)?\b", lower)
        if m:
            mon, day = _MONTHS.get(m.group(1)) or _MONTHS3.get(m.group(1)[:3]), int(m.group(2))
            if mon:
                try:
                    candidate = today.replace(month=mon, day=day)
                    if candidate >= today:
                        target = candidate
                    else:
                        target = candidate.replace(year=candidate.year + 1)
                except ValueError:
                    pass
    if target is None:
        m = re.search(r"\b(\d{1,2})[/.-](\d{1,2})(?:[/.-](\d{2,4}))?\b", lower)
        if m:
            a, b = int(m.group(1)), int(m.group(2))
            y = int(m.group(3)) if m.group(3) else today.year
            if y < 100:
                y += 2000
            for day, mon in ((a, b), (b, a)):
                try:
                    candidate = today.replace(year=y, month=mon, day=day)
                except ValueError:
                    continue
                if candidate >= today:
                    target = candidate
                    break
    if target is None:
        m = re.search(r"\b(\d{1,2})(?:st|nd|rd|th)\b", lower)
        if m:
            day = int(m.group(1))
            for candidate in (today.replace(day=day), today.replace(year=today.year + 1, month=1, day=day)):
                try:
                    if candidate >= today:
                        target = candidate
                        break
                except ValueError:
                    continue

    if target is None:
        return None
    delta = (target - today).days
    if delta == 0:
        return "today"
    if delta == 1:
        return "tomorrow"
    return _WEEKDAYS[target.weekday()]


def normalize_time(text: str) -> Optional[str]:
    lower = text.strip().lower()
    if "morning" in lower:
        return "morning"
    if "afternoon" in lower:
        return "afternoon"
    if "evening" in lower or "night" in lower or "tonight" in lower:
        return "evening"
    m = re.search(r"\b(\d{1,2})(?::(\d{2}))?\s*(am|pm)\b", lower)
    if not m:
        m = re.search(r"\b(\d{1,2}):(\d{2})\b", lower)
    if m:
        h = int(m.group(1))
        if m.lastindex >= 3 and m.group(3) == "pm" and h < 12:
            h += 12
        if m.lastindex >= 3 and m.group(3) == "am" and h == 12:
            h = 0
        if 6 <= h < 12:
            return "morning"
        if 12 <= h < 17:
            return "afternoon"
        return "evening"
    return None


def apply_typed_answer(st: ChatState, text: str) -> bool:
    lower = text.strip().lower()
    if st.clarify == "day":
        st.day = normalize_day(text) or text.strip()
        st.day_decided = True
    elif st.clarify == "time":
        st.time = normalize_time(text) or None
        st.time_decided = True
    elif st.clarify == "duration":
        d = detected_duration(lower)
        if d is None:
            return False
        st.duration_min = d
        st.duration_decided = True
    elif st.clarify == "party":
        p = detected_party(lower)
        if p is None:
            try:
                p = int(text)
            except ValueError:
                return False
        st.party_size = p
        st.party_decided = True
    else:
        return False
    return True


def missing_fields(st: ChatState) -> list:
    lower = (st.intent or "").lower()
    missing = []
    if not st.day_decided and not _has_day(lower):
        missing.append("day")
    if not st.time_decided and not _has_time(lower):
        missing.append("time")
    if not st.duration_decided and not _has_duration(lower):
        missing.append("duration")
    if not st.party_decided and not _has_party(lower):
        missing.append("party")
    return missing


def build_query(st: ChatState) -> str:
    parts = [st.intent or ""]
    lower = (st.intent or "").lower()
    extra = []
    # Append backend-friendly phrases the user chose or we normalized, but only
    # when the raw text doesn't already carry them (avoid "evening evening").
    if st.day and st.day not in lower:
        extra.append(st.day)
    if st.time and st.time not in lower:
        extra.append(st.time)
    if st.duration_min and not _has_duration(lower):
        extra.append(
            f"for {st.duration_min // 60}.5 hours"
            if st.duration_min % 60
            else f"for {st.duration_min // 60} hours"
        )
    if extra:
        parts.append(" ".join(extra))
    return " ".join(p for p in parts if p).strip()


# ---------------------------------------------------------------- commands


async def cmd_start(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    st = get_state(update.effective_chat.id)
    if token_valid(st):
        await update.effective_message.reply_text(
            f"👋 Welcome back, <b>{esc(st.display_name)}</b>!\n\n"
            "Just describe what you want to book, e.g.\n"
            "<i>tennis for two tomorrow evening, outdoor</i>\n\n"
            "Or /login to switch account, /logout to sign out, /help for help.",
            parse_mode=ParseMode.HTML,
        )
        return
    await update.effective_message.reply_text(
        "👋 Welcome to Courtly intent booking.\n\n"
        "Sign in with /login, then just type what you want to book "
        "(e.g. <i>squash Saturday morning, party of 4</i>).",
        parse_mode=ParseMode.HTML,
    )


async def cmd_login(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    st = get_state(update.effective_chat.id)
    if token_valid(st):
        await update.effective_message.reply_text(
            f"Already signed in as <b>{esc(st.display_name)}</b> "
            f"(until {datetime.fromtimestamp(st.expires_at):%d %b %H:%M}). "
            "Use /logout to switch.",
            parse_mode=ParseMode.HTML,
        )
        return
    st.auth_step = "username"
    st.pending_username = None
    await update.effective_message.reply_text("Username?")


async def cmd_logout(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    st = get_state(update.effective_chat.id)
    st.username = st.token = st.display_name = None
    st.expires_at = None
    st.auth_step = None
    st.intent = st.day = st.time = None
    st.duration_min = None
    st.day_decided = st.time_decided = st.duration_decided = st.party_decided = False
    st.suggestions = []
    await update.effective_message.reply_text("Signed out.")


async def cmd_cancel(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    st = get_state(update.effective_chat.id)
    st.auth_step = None
    st.pending_username = None
    st.intent = st.day = st.time = None
    st.duration_min = None
    st.clarify = None
    st.day_decided = st.time_decided = st.duration_decided = st.party_decided = False
    st.suggestions = []
    await update.effective_message.reply_text("Cancelled.")


async def cmd_help(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    await update.effective_message.reply_text(
        "/start — sign in and begin\n"
        "/login — sign in with username/password\n"
        "/logout — sign out\n"
        "/status — current account and token expiry\n"
        "/cancel — clear the current request\n\n"
        "Then describe what you want, e.g. <i>football for 10 on Saturday</i>.",
        parse_mode=ParseMode.HTML,
    )


async def cmd_status(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    st = get_state(update.effective_chat.id)
    if not token_valid(st):
        await update.effective_message.reply_text("Not signed in. Use /login.")
        return
    await update.effective_message.reply_text(
        f"Signed in as <b>{esc(st.display_name)}</b> ({esc(st.username)})\n"
        f"Token valid until {datetime.fromtimestamp(st.expires_at):%d %b %H:%M}.",
        parse_mode=ParseMode.HTML,
    )


# ---------------------------------------------------------------- messages


async def handle_text(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    chat_id = update.effective_chat.id
    st = get_state(chat_id)
    text = (update.effective_message.text or "").strip()

    if st.auth_step == "username":
        st.pending_username = text
        st.auth_step = "password"
        await update.effective_message.reply_text("Password?")
        return

    if st.auth_step == "password":
        ok = await api_login(st, st.pending_username or "", text)
        st.pending_username = None
        if ok:
            await update.effective_message.reply_text(
                f"Signed in as <b>{esc(st.display_name)}</b>. What shall we book?",
                parse_mode=ParseMode.HTML,
            )
        else:
            st.auth_step = None
            await update.effective_message.reply_text(
                "Login failed. Try /login again (or check the username/password)."
            )
        return

    if not token_valid(st):
        await update.effective_message.reply_text(
            "Please sign in first with /login, then send your request."
        )
        return

    if not text:
        return

    # Typed cancel: same effect as tapping the Cancel button.
    if text.strip().lower() in CANCEL_WORDS:
        st.intent = st.day = st.time = None
        st.duration_min = None
        st.clarify = None
        st.day_decided = st.time_decided = st.duration_decided = st.party_decided = False
        st.suggestions = []
        st.party_size = 2
        await update.effective_message.reply_text("Cancelled. Describe another booking when you're ready.")
        return

    # While a clarification question is pending, a typed answer fills it in —
    # but a brand-new booking request implicitly cancels it instead.
    if st.clarify:
        if is_new_booking_request(st, text):
            st.clarify = None
            st.suggestions = []
            st.spec = None
            st.relaxation_trail = []
        else:
            if apply_typed_answer(st, text):
                st.clarify = None
                await advance(update, st)
            else:
                await ask_next(update, st, st.clarify)
            return

    # Fresh request: detect what the user already told us, ask for the rest.
    lower = text.lower()
    st.intent = text
    st.suggestions = []
    st.spec = None
    st.relaxation_trail = []
    st.query_id += 1
    st.day = detected_day(lower)
    st.time = detected_time(lower)
    st.duration_min = detected_duration(lower)
    st.party_size = detected_party(lower)
    # Normalize explicit dates/clock times the backend can't parse itself into
    # the phrases it does understand (e.g. "aug 22" -> "saturday", "18:00" -> "evening").
    if st.day is None and _has_day(lower):
        st.day = normalize_day(text)
    if st.time is None and _has_time(lower):
        st.time = normalize_time(text)
    st.day_decided = st.day is not None or _has_day(lower)
    st.time_decided = st.time is not None or _has_time(lower)
    st.duration_decided = st.duration_min is not None or _has_duration(lower)
    st.party_decided = st.party_size is not None or _has_party(lower)

    missing = missing_fields(st)
    if not missing:
        await do_suggest(st, update.effective_message.reply_text)
    else:
        await ask_next(update, st, missing[0])


# ---------------------------------------------------------------- callbacks


def btn(label: str, data: str) -> InlineKeyboardButton:
    return InlineKeyboardButton(label, callback_data=data)


def current_send(update: Update):
    """Best way to put text in front of the user: edit the tapped message or reply."""
    if update.callback_query:
        return update.callback_query.edit_message_text
    return update.effective_message.reply_text


async def ask_next(update: Update, st: ChatState, field: str) -> None:
    st.clarify = field
    send = current_send(update)
    if field == "day":
        rows = [
            [btn("Today", "day:today"), btn("Tomorrow", "day:tomorrow")],
            [btn("Saturday", "day:saturday"), btn("Sunday", "day:sunday")],
            [btn("This weekend", "day:weekend"), btn("Next week", "day:nextweek")],
            [btn("Any day", "day:skip"), btn("❌ Cancel", "cancel")],
        ]
        await send(
            "When would you like to play?\n(Or type a date, e.g. <i>aug 22</i> / <i>22/08</i>.)",
            parse_mode=ParseMode.HTML,
            reply_markup=InlineKeyboardMarkup(rows),
        )
    elif field == "time":
        rows = [
            [btn("🌅 Morning (6–12)", "time:morning"), btn("☀️ Afternoon (12–17)", "time:afternoon")],
            [btn("🌆 Evening (17–22)", "time:evening"), btn("🕒 Any time", "time:any")],
            [btn("❌ Cancel", "cancel")],
        ]
        await send(
            "What time of day?\n(Or type a time, e.g. <i>18:00</i> / <i>6pm</i>.)",
            parse_mode=ParseMode.HTML,
            reply_markup=InlineKeyboardMarkup(rows),
        )
    elif field == "duration":
        options = [(1, 60), (1.5, 90), (2, 120), (3, 180), (4, 240)]
        rows = [
            [btn(f"{h} hour{'s' if h > 1 else ''}", f"duration:{m}") for h, m in options[:3]],
            [btn(f"{h} hour{'s' if h > 1 else ''}", f"duration:{m}") for h, m in options[3:]],
        ]
        rows.append([btn("❌ Cancel", "cancel")])
        await send("How long do you need it for?", parse_mode=ParseMode.HTML, reply_markup=InlineKeyboardMarkup(rows))
    elif field == "party":
        rows = [
            [btn(str(n), f"party:{n}") for n in PARTY_CHOICES[i : i + 4]]
            for i in range(0, len(PARTY_CHOICES), 4)
        ]
        rows.append([btn("❌ Cancel", "cancel")])
        await send("How many people?", parse_mode=ParseMode.HTML, reply_markup=InlineKeyboardMarkup(rows))


async def advance(update: Update, st: ChatState) -> None:
    missing = missing_fields(st)
    if not missing:
        await do_suggest(st, current_send(update))
    else:
        await ask_next(update, st, missing[0])


def duration_label(minutes: int) -> str:
    if minutes and minutes % 60 == 0:
        return f"{minutes // 60}h"
    if minutes:
        return f"{minutes / 60:g}h"
    return "—"


def resource_type_label(resource_type: Optional[str]) -> str:
    if not resource_type:
        return "any sport"
    return resource_type.replace("_", " ").title()


async def render_no_slots(st: ChatState, send) -> None:
    spec = st.spec or {}
    party_size = st.party_size or 2
    resource_type = spec.get("resourceType")

    parts = ["😕 <b>No free slots</b> — even after relaxing the constraints, nothing matched."]
    parts.append("\n<b>I searched for:</b>")
    parts.append(f"• Sport: <b>{esc(resource_type_label(resource_type))}</b>")
    when = []
    if spec.get("dayFrom") and spec.get("dayTo"):
        day_from = str(spec["dayFrom"])
        day_to = str(spec["dayTo"])
        when.append(day_from if day_from == day_to else f"{day_from} → {day_to}")
    if spec.get("timeOfDay") and spec.get("timeOfDay") != "ANY":
        when.append(spec["timeOfDay"].title())
    parts.append(f"• When: <b>{esc(', '.join(when) or 'any day / any time')}</b>")
    parts.append(f"• Duration: <b>{duration_label(spec.get('durationMin') or st.duration_min or 0)}</b>")
    parts.append(f"• Players: <b>{party_size}</b>")

    rng = SPORT_PARTY_RANGES.get(resource_type) if resource_type else None
    if rng and party_size > rng[1]:
        parts.append(
            f"\n💡 {esc(resource_type_label(resource_type))} spaces hold "
            f"<b>{rng[0]}–{rng[1]} people</b> — your party of <b>{party_size}</b> "
            "won't fit anywhere. Booking with fewer people is the fastest fix."
        )
    elif st.relaxation_trail:
        parts.append("\n<b>Already tried relaxing:</b>")
        for step in st.relaxation_trail:
            parts.append(f"• {esc(step.get('detail', step.get('action', '')))}")

    rows = []
    rows.append([btn("👥 Fewer people", f"fix:party:{st.query_id}")])
    if st.time and st.time_decided:
        rows.append([btn("🕐 Any time of day", f"fix:time:{st.query_id}")])
    if st.day and st.day_decided:
        rows.append([btn("📅 This week (any day)", f"fix:window:{st.query_id}")])
    rows.append([btn("🔀 Different sport", f"fix:sport:{st.query_id}")])
    rows.append([btn("❌ Cancel", "cancel")])
    await send("\n".join(parts), parse_mode=ParseMode.HTML, reply_markup=InlineKeyboardMarkup(rows))


async def do_suggest(st: ChatState, send) -> None:
    if not token_valid(st):
        await send("Your session expired. Use /login and try again.")
        return
    text = build_query(st)
    try:
        r = await api_suggest(st, text, st.party_size or 2)
    except httpx.HTTPError as exc:
        log.warning("suggest HTTP error: %s", exc)
        await send("Could not reach the booking service. Try again in a moment.")
        return
    if r.status_code == 401:
        await send("Your session expired. Use /login and try again.")
        return
    if r.status_code != 200:
        await send(
            f"⚠️ {esc(error_message(r.json(), f'Request failed ({r.status_code}).'))}",
            parse_mode=ParseMode.HTML,
        )
        return

    data = r.json()
    st.spec = data.get("spec")
    st.relaxation_trail = data.get("relaxationTrail", [])
    st.suggestions = data.get("suggestions", [])

    if not st.suggestions:
        await render_no_slots(st, send)
        return

    party_size = st.party_size or 2
    header = [f"<b>{len(st.suggestions)} free slot(s)</b> for {party_size} people:"]
    if st.relaxation_trail:
        header.append("")
        for step in st.relaxation_trail:
            header.append(f"• {esc(step.get('detail', step.get('action', '')))}")

    for i, s in enumerate(st.suggestions):
        header.append(
            f"\n<b>#{i + 1}</b> {esc(s['resourceName'])} — {esc(s['facilityName'])}\n"
            f"📅 {esc(fmt_time(s['start']))} – {esc(fmt_time(s['end']))}\n"
            f"👥 {party_size} people  ·  💰 {esc(s.get('price') or '—')}  ·  score {s.get('score', 0):.1f}"
        )

    keyboard = InlineKeyboardMarkup(
        [
            [btn(f"📅 Book #{i + 1}", f"book:{st.query_id}:{i}")]
            for i in range(len(st.suggestions))
        ]
        + [[btn("❌ Cancel", "cancel")]]
    )
    await send("\n".join(header), parse_mode=ParseMode.HTML, reply_markup=keyboard)


async def cb_day(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    query = update.callback_query
    await query.answer()
    st = get_state(update.effective_chat.id)
    val = query.data.split(":", 1)[1]
    mapping = {
        "today": "today",
        "tomorrow": "tomorrow",
        "saturday": "saturday",
        "sunday": "sunday",
        "weekend": "this weekend",
        "nextweek": "next week",
    }
    st.day = mapping.get(val)
    st.day_decided = True
    await advance(update, st)


async def cb_time(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    query = update.callback_query
    await query.answer()
    st = get_state(update.effective_chat.id)
    val = query.data.split(":", 1)[1]
    mapping = {"morning": "morning", "afternoon": "afternoon", "evening": "evening"}
    st.time = mapping.get(val)  # "any" maps to None -> backend uses ANY
    st.time_decided = True
    await advance(update, st)


async def cb_duration(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    query = update.callback_query
    await query.answer()
    st = get_state(update.effective_chat.id)
    val = query.data.split(":", 1)[1]
    st.duration_min = int(val) if val.isdigit() else None
    st.duration_decided = True
    await advance(update, st)


async def cb_party(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    query = update.callback_query
    await query.answer()
    st = get_state(update.effective_chat.id)
    st.party_size = int(query.data.split(":", 1)[1])
    st.party_decided = True
    await advance(update, st)


async def cb_fix_party(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    query = update.callback_query
    await query.answer()
    st = get_state(update.effective_chat.id)
    if int(query.data.split(":")[2]) != st.query_id:
        await query.edit_message_text("That request was canceled. Send a new booking request.")
        return
    st.party_decided = False
    st.suggestions = []
    await ask_next(update, st, "party")


async def cb_fix_time(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    query = update.callback_query
    await query.answer()
    st = get_state(update.effective_chat.id)
    if int(query.data.split(":")[2]) != st.query_id:
        await query.edit_message_text("That request was canceled. Send a new booking request.")
        return
    st.time = None
    st.time_decided = True
    await advance(update, st)


async def cb_fix_window(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    query = update.callback_query
    await query.answer()
    st = get_state(update.effective_chat.id)
    if int(query.data.split(":")[2]) != st.query_id:
        await query.edit_message_text("That request was canceled. Send a new booking request.")
        return
    st.day = "this week"
    st.day_decided = True
    await advance(update, st)


async def cb_fix_sport(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    query = update.callback_query
    await query.answer()
    chat_id = update.effective_chat.id
    st = get_state(chat_id)
    if int(query.data.split(":")[2]) != st.query_id:
        await query.edit_message_text("That request was canceled. Send a new booking request.")
        return
    st.intent = None
    st.day = None
    st.day_decided = False
    st.time = None
    st.time_decided = False
    st.duration_min = None
    st.duration_decided = False
    st.spec = None
    st.suggestions = []
    await current_send(update)(
        "No problem — what would you like to book instead?\n"
        "e.g. <i>football on saturday for 10</i>",
        parse_mode=ParseMode.HTML,
    )


async def cb_book(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    query = update.callback_query
    await query.answer()
    chat_id = update.effective_chat.id
    st = get_state(chat_id)

    parts = query.data.split(":")
    qid = int(parts[1])
    idx = int(parts[2])
    if qid != st.query_id:
        await query.edit_message_text("That request was canceled. Send a new booking request.")
        return
    if idx < 0 or idx >= len(st.suggestions):
        await query.edit_message_text("That suggestion is gone. Please search again.")
        return
    suggestion = st.suggestions[idx]
    party_size = st.party_size

    if not token_valid(st):
        await query.edit_message_text("Your session expired. Use /login and try again.")
        return

    await query.edit_message_text("⏳ Booking…")

    try:
        r = await api_book(st, suggestion, party_size)
    except httpx.HTTPError as exc:
        log.warning("book HTTP error: %s", exc)
        await query.edit_message_text("Could not reach the booking service. Try again in a moment.")
        return

    if r.status_code == 401:
        await query.edit_message_text("Your session expired. Use /login and try again.")
        return

    if r.status_code == 200:
        data = r.json()
        text = (
            f"✅ <b>Reservation #{data.get('reservationId')} — {esc(data.get('status'))}</b>\n"
            f"💰 {esc(data.get('totalAmount'))}\n"
            f"{esc(data.get('message'))}"
        )
        await query.edit_message_text(text, parse_mode=ParseMode.HTML)
        st.suggestions = []
        return

    await query.edit_message_text(
        f"⚠️ {esc(error_message(r.json(), f'Booking failed ({r.status_code}).'))}"
    )


async def cb_cancel(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    query = update.callback_query
    await query.answer()
    st = get_state(update.effective_chat.id)
    st.intent = st.day = st.time = None
    st.duration_min = None
    st.clarify = None
    st.day_decided = st.time_decided = st.duration_decided = st.party_decided = False
    st.suggestions = []
    st.party_size = 2
    await query.edit_message_text("Cancelled. Describe another booking when you're ready.")


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
    app.add_handler(CallbackQueryHandler(cb_day, pattern=r"^day:[a-z]+$"))
    app.add_handler(CallbackQueryHandler(cb_time, pattern=r"^time:[a-z]+$"))
    app.add_handler(CallbackQueryHandler(cb_duration, pattern=r"^duration:\d+$"))
    app.add_handler(CallbackQueryHandler(cb_party, pattern=r"^party:\d+$"))
    app.add_handler(CallbackQueryHandler(cb_fix_party, pattern=r"^fix:party:\d+$"))
    app.add_handler(CallbackQueryHandler(cb_fix_time, pattern=r"^fix:time:\d+$"))
    app.add_handler(CallbackQueryHandler(cb_fix_window, pattern=r"^fix:window:\d+$"))
    app.add_handler(CallbackQueryHandler(cb_fix_sport, pattern=r"^fix:sport:\d+$"))
    app.add_handler(CallbackQueryHandler(cb_book, pattern=r"^book:\d+:\d+$"))
    app.add_handler(CallbackQueryHandler(cb_cancel, pattern=r"^cancel$"))
    app.add_handler(MessageHandler(filters.TEXT & ~filters.COMMAND, handle_text))
    log.info("Courtly bot polling on %s", API_BASE)
    app.run_polling(allowed_updates=Update.ALL_TYPES)


if __name__ == "__main__":
    main()