import asyncio
import html
import logging
import os
import re
import time
from datetime import date, datetime, timedelta
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

API_BASE = os.getenv("API_BASE", "http://localhost:8080").rstrip("/")
TELEGRAM_BOT_TOKEN = os.getenv("TELEGRAM_BOT_TOKEN")
BRAND = os.getenv("BOT_BRAND_NAME", "EverRest")

SERVICE_OPTIONS = [
    ("Memorial service", "MEMORIAL_SERVICE"),
    ("Burial ceremony", "BURIAL_CEREMONY"),
    ("Cremation ceremony", "CREMATION_CEREMONY"),
    ("Farewell ceremony", "FAREWELL_CEREMONY"),
]

MONTHS = {
    "jan": 1, "january": 1, "feb": 2, "february": 2, "mar": 3, "march": 3,
    "apr": 4, "april": 4, "may": 5, "jun": 6, "june": 6, "jul": 7, "july": 7,
    "aug": 8, "august": 8, "sep": 9, "sept": 9, "september": 9, "oct": 10,
    "october": 10, "nov": 11, "november": 11, "dec": 12, "december": 12,
}

SIGN_IN_AGAIN = (
    "Your session has expired. Sign in again with /login — nothing you have told me "
    "is lost, and I will pick up exactly where we left off."
)

CANCEL_WORDS = ("cancel", "stop", "start over", "start again")

logging.basicConfig(
    format="%(asctime)s %(levelname)s %(name)s %(message)s", level=logging.INFO
)
log = logging.getLogger("everrest_bot")


class Session:
    def __init__(self) -> None:
        self.auth_step: Optional[str] = None
        self.pending_username: Optional[str] = None
        self.username: Optional[str] = None
        self.token: Optional[str] = None
        self.expires_at: Optional[float] = None
        self.display_name: Optional[str] = None
        self.phone_required: bool = False

    def invalidate(self) -> None:
        self.token = None
        self.expires_at = None
        self.auth_step = None
        self.pending_username = None


class Arrangement:
    def __init__(self) -> None:
        self.deceased_name: Optional[str] = None
        self.date_of_death: Optional[str] = None
        self.service_type: Optional[str] = None
        self.mourners: Optional[int] = None
        self.phone: Optional[str] = None
        self.venue: Optional[dict] = None
        self.venues: List[dict] = []
        self.asking: Optional[str] = None
        self.query_id: int = 0

    def reset_request(self) -> None:
        self.deceased_name = None
        self.date_of_death = None
        self.service_type = None
        self.mourners = None
        self.phone = None
        self.venue = None
        self.venues = []
        self.asking = None
        self.query_id += 1


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
    user = update.effective_user
    se = sessions.get(user.id) if user else None
    if se and se.display_name:
        return se.display_name
    return user.full_name if user else "someone"


def btn(label: str, data: str) -> InlineKeyboardButton:
    return InlineKeyboardButton(label, callback_data=data)


def current_send(update: Update):
    if update.callback_query:
        return update.callback_query.edit_message_text
    return update.effective_message.reply_text


def error_message(body: Any, fallback: str) -> str:
    if isinstance(body, dict):
        return str(body.get("error") or body.get("message") or fallback)
    return fallback


def _json_or_none(r: httpx.Response) -> Any:
    try:
        return r.json()
    except ValueError:
        return None


def fmt_datetime(iso: str) -> str:
    try:
        return datetime.fromisoformat(iso).strftime("%a %d %b, %H:%M")
    except (ValueError, TypeError):
        return iso or "—"


def fmt_clock(iso: str) -> str:
    try:
        return datetime.fromisoformat(iso).strftime("%H:%M")
    except (ValueError, TypeError):
        return ""


def parse_death_date(text: str) -> Optional[str]:
    raw = (text or "").strip().lower()
    if not raw:
        return None
    if raw in ("today", "this morning", "this afternoon"):
        return date.today().isoformat()
    if raw in ("yesterday", "last night"):
        return (date.today() - timedelta(days=1)).isoformat()
    days = re.search(r"(\d{1,2})\s+days?\s+ago", raw)
    if days:
        return (date.today() - timedelta(days=int(days.group(1)))).isoformat()
    iso = re.search(r"(\d{4})-(\d{2})-(\d{2})", raw)
    if iso:
        return f"{iso.group(1)}-{iso.group(2)}-{iso.group(3)}"
    slash = re.search(r"\b(\d{1,2})[./](\d{1,2})(?:[./](\d{2,4}))?\b", raw)
    if slash:
        day_n = int(slash.group(1))
        month_n = int(slash.group(2))
        year_n = int(slash.group(3)) if slash.group(3) else date.today().year
        if year_n < 100:
            year_n += 2000
        try:
            return date(year_n, month_n, day_n).isoformat()
        except ValueError:
            return None
    named = re.search(
        r"\b(\d{1,2})\s*(?:st|nd|rd|th)?\s*(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:t|tember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\b",
        raw,
    )
    if named:
        month_n = MONTHS[named.group(2)]
        try:
            return date(date.today().year, month_n, int(named.group(1))).isoformat()
        except ValueError:
            return None
    named_rev = re.search(
        r"\b(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:t|tember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\s+(\d{1,2})\b",
        raw,
    )
    if named_rev:
        month_n = MONTHS[named_rev.group(1)]
        try:
            return date(date.today().year, month_n, int(named_rev.group(2))).isoformat()
        except ValueError:
            return None
    return None


def parse_mourners(text: str) -> Optional[int]:
    match = re.search(r"\d{1,4}", text or "")
    if not match:
        return None
    value = int(match.group(0))
    if value < 1:
        return None
    return value


def missing_facts(ar: Arrangement, se: Optional[Session] = None) -> List[str]:
    missing = []
    if not ar.deceased_name:
        missing.append("name")
    if not ar.date_of_death:
        missing.append("death")
    if not ar.service_type:
        missing.append("service")
    if ar.mourners is None:
        missing.append("mourners")
    if se and se.phone_required and not ar.phone:
        missing.append("phone")
    if not ar.venue:
        missing.append("venue")
    return missing


def arrangement_body(ar: Arrangement) -> dict:
    body = {
        "venueId": ar.venue["id"] if ar.venue else None,
        "serviceType": ar.service_type,
        "funeralPackage": "ESSENTIAL",
        "deceasedFullName": ar.deceased_name,
        "dateOfDeath": ar.date_of_death,
        "attendees": ar.mourners,
        "paymentMethod": "CASH",
        "bookingSource": "TELEGRAM",
    }
    if ar.phone:
        body["phone"] = ar.phone
    return body


async def api_login(se: Session, username: str, password: str) -> bool:
    try:
        async with httpx.AsyncClient(timeout=20) as client:
            r = await client.post(
                f"{API_BASE}/api/telegram/token",
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
    se.username = data.get("username") or username
    se.phone_required = bool(data.get("phoneRequired"))
    try:
        se.expires_at = datetime.fromisoformat(data["expiresAt"]).timestamp()
    except (KeyError, ValueError, TypeError):
        se.expires_at = time.time() + 12 * 3600
    se.auth_step = None
    return True


async def api_venues(se: Session, service_type: str, mourners: int) -> httpx.Response:
    async with httpx.AsyncClient(timeout=20) as client:
        return await client.get(
            f"{API_BASE}/api/telegram/venues",
            headers=auth_headers(se),
            params={"serviceType": service_type, "attendees": mourners},
        )


async def api_history(se: Session) -> httpx.Response:
    async with httpx.AsyncClient(timeout=20) as client:
        return await client.get(f"{API_BASE}/api/telegram/history", headers=auth_headers(se))


async def api_arrange(se: Session, body: dict) -> httpx.Response:
    async with httpx.AsyncClient(timeout=30) as client:
        return await client.post(
            f"{API_BASE}/api/telegram/arrangements",
            headers=auth_headers(se),
            json=body,
        )


async def load_venues(ar: Arrangement, se: Session) -> Optional[str]:
    try:
        r = await api_venues(se, ar.service_type or "", ar.mourners or 1)
    except httpx.HTTPError as exc:
        log.warning("venues HTTP error: %s", exc)
        return "We could not reach the funeral home's system. Please try again in a moment."
    if r.status_code == 401:
        se.invalidate()
        return SIGN_IN_AGAIN
    if r.status_code != 200:
        return error_message(_json_or_none(r), f"The request failed ({r.status_code}).")
    ar.venues = r.json() or []
    return None


async def ask(update: Update, ar: Arrangement, field: str) -> None:
    ar.asking = field
    send = current_send(update)
    cancel_row = [btn("Start over", "cancel")]
    if field == "name":
        await send("Enter the name to remember.", parse_mode=ParseMode.HTML)
        return
    if field == "death":
        rows = [
            [btn("Today", "death:0"), btn("Yesterday", "death:1")],
            [btn("2 days ago", "death:2"), btn("3 days ago", "death:3")],
        ]
        await send(
            "When did the death occur?\nYou can also type a date.",
            parse_mode=ParseMode.HTML,
            reply_markup=InlineKeyboardMarkup(rows + [cancel_row]),
        )
        return
    if field == "service":
        rows = [[btn(label, f"service:{i}")] for i, (label, _) in enumerate(SERVICE_OPTIONS)]
        await send(
            "What ceremony should be arranged?",
            parse_mode=ParseMode.HTML,
            reply_markup=InlineKeyboardMarkup(rows + [cancel_row]),
        )
        return
    if field == "mourners":
        await send(
            "How many guests will attend?\nType the number. Each venue has its own maximum.",
            parse_mode=ParseMode.HTML,
            reply_markup=InlineKeyboardMarkup([cancel_row]),
        )
        return
    if field == "phone":
        await send("A contact phone is required. Please type the number.")
        return
    rows = []
    for venue in ar.venues:
        label = f"{venue.get('name')} · up to {venue.get('maxAttendees')}"
        rows.append([btn(label[:64], f"venue:{venue.get('id')}")])
    if not rows:
        await send(
            "No venue can hold that guest count for this ceremony. Enter a smaller number.",
            parse_mode=ParseMode.HTML,
            reply_markup=InlineKeyboardMarkup([cancel_row]),
        )
        ar.asking = "mourners"
        return
    await send(
        "Choose a venue. The ceremony date will be assigned for you.",
        parse_mode=ParseMode.HTML,
        reply_markup=InlineKeyboardMarkup(rows + [cancel_row]),
    )


def render_review(ar: Arrangement) -> Tuple[str, InlineKeyboardMarkup]:
    venue = ar.venue or {}
    lines = [
        f"<b>{esc(BRAND)} — confirm arrangement</b>",
        "",
        f"Remembered: {esc(ar.deceased_name)}",
        f"Venue: {esc(venue.get('name'))} — {esc(venue.get('homeName'))}",
        f"Holds up to {esc(str(venue.get('maxAttendees')))} guests",
        f"Guests entered: {esc(str(ar.mourners))}",
        "",
        "An available ceremony date will be assigned. You do not choose the time.",
    ]
    keyboard = InlineKeyboardMarkup([
        [btn("Confirm arrangements", f"confirm:{ar.query_id}")],
        [btn("Start over", "cancel")],
    ])
    return "\n".join(lines), keyboard


async def advance(update: Update, context: ContextTypes.DEFAULT_TYPE,
                  ar: Arrangement, se: Session) -> None:
    missing = missing_facts(ar, se)
    if "venue" in missing and ar.service_type and ar.mourners is not None:
        err = await load_venues(ar, se)
        if err:
            await current_send(update)(esc(err), parse_mode=ParseMode.HTML)
            return
    missing = missing_facts(ar, se)
    if missing:
        await ask(update, ar, missing[0])
        return
    ar.asking = None
    text, keyboard = render_review(ar)
    await current_send(update)(text, parse_mode=ParseMode.HTML, reply_markup=keyboard)


async def cmd_start(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    se = get_session(update.effective_user.id)
    if token_valid(se):
        await update.effective_message.reply_text(
            f"<b>{esc(BRAND)}</b>\n\n"
            f"Signed in as {esc(se.display_name)}.\n\n"
            "I'm sorry for your loss.\n\n"
            "Tell me the name of the person who died, when you are ready. "
            "A ceremony date will be assigned — you do not choose it.",
            parse_mode=ParseMode.HTML,
        )
        return
    await update.effective_message.reply_text(
        f"<b>{esc(BRAND)}</b>\n\n"
        "You do not choose a date. Sign in with /login, tell us the details, "
        "and an available ceremony time is assigned.",
        parse_mode=ParseMode.HTML,
    )


async def cmd_login(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    se = get_session(update.effective_user.id)
    if not is_private(update):
        await update.effective_message.reply_text(
            "Please message me privately to sign in."
        )
        return
    if token_valid(se):
        await update.effective_message.reply_text(
            f"Already signed in as <b>{esc(se.display_name)}</b>. Use /logout to change account.",
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
        "I'm sorry for your loss.\n\n"
        "Tell me who died and a few details. A date is assigned from what is free — "
        "it is not chosen here.\n\n"
        "/login — sign in (privately)\n"
        "/logout — sign out\n"
        "/status — this arrangement and your session\n"
        "/my_arrangements — history from the funeral home records\n"
        "/cancel — clear the current arrangement",
        parse_mode=ParseMode.HTML,
    )


async def cmd_status(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    se = get_session(update.effective_user.id)
    ar = get_arrangement(update.effective_chat.id)
    lines = []
    if token_valid(se):
        lines.append(f"Signed in as <b>{esc(se.display_name)}</b> ({esc(se.username)})")
    else:
        lines.append("Not signed in. Use /login.")
    if ar.deceased_name or ar.service_type or ar.mourners:
        lines.append("")
        lines.append("An arrangement is in progress.")
        if ar.venue:
            lines.append(f"Venue: {esc(ar.venue.get('name'))}")
        if ar.mourners:
            lines.append(f"Guests entered: {esc(str(ar.mourners))}")
    await update.effective_message.reply_text("\n".join(lines), parse_mode=ParseMode.HTML)


async def cmd_my_arrangements(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    se = get_session(update.effective_user.id)
    if not token_valid(se):
        await update.effective_message.reply_text(
            "Please sign in with /login to see what you have arranged."
        )
        return
    try:
        r = await api_history(se)
    except httpx.HTTPError as exc:
        log.warning("history HTTP error: %s", exc)
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
            "You have nothing arranged yet. Tell me what has happened and I will assign a time."
        )
        return
    lines = [f"<b>Arranged for {esc(se.display_name)}</b>", ""]
    for item in items:
        lines.append(
            f"<b>{esc(item.get('venueName'))}</b> — {esc(item.get('homeName'))}\n"
            f"{esc(fmt_datetime(item.get('startAt')))} – {esc(fmt_clock(item.get('endAt')))}\n"
            f"{esc(str(item.get('status', '')).title())} · "
            f"{esc(str(item.get('attendees')))} guests · "
            f"Reference {esc(str(item.get('reservationId')))}"
            + (f" · {esc(str(item['formattedAmount']))}" if item.get("formattedAmount") else "")
        )
        lines.append("")
    lines.append("These records are read from the funeral home database.")
    await update.effective_message.reply_text("\n".join(lines), parse_mode=ParseMode.HTML)


def apply_typed_answer(ar: Arrangement, text: str) -> bool:
    field = ar.asking
    raw = (text or "").strip()
    if field == "name":
        if len(raw) < 2:
            return False
        ar.deceased_name = raw[:120]
        return True
    if field == "death":
        parsed = parse_death_date(raw)
        if not parsed:
            return False
        ar.date_of_death = parsed
        return True
    if field == "mourners":
        value = parse_mourners(raw)
        if value is None:
            return False
        ar.mourners = value
        ar.venue = None
        return True
    if field == "phone":
        if len(re.sub(r"[^0-9]", "", raw)) < 7:
            return False
        ar.phone = raw[:20]
        return True
    if field == "service":
        lower = raw.lower()
        for label, code in SERVICE_OPTIONS:
            if lower in label.lower() or lower.replace(" ", "_") == code.lower():
                ar.service_type = code
                ar.venue = None
                return True
        return False
    return False


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
        if ar.deceased_name or ar.date_of_death or ar.service_type:
            await update.effective_message.reply_text(
                f"Signed in as <b>{esc(se.display_name)}</b>. Picking up where we left off.",
                parse_mode=ParseMode.HTML,
            )
            await advance(update, context, ar, se)
            return
        await update.effective_message.reply_text(
            f"Signed in as <b>{esc(se.display_name)}</b>.\n\n"
            "Tell me the name to remember, in your own words.",
            parse_mode=ParseMode.HTML,
        )
    else:
        se.auth_step = None
        await update.effective_message.reply_text(
            "That did not sign you in. Try /login again."
        )


async def handle_text(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    se = get_session(update.effective_user.id)
    ar = get_arrangement(update.effective_chat.id)
    text = (update.effective_message.text or "").strip()
    if se.auth_step and is_private(update):
        await handle_auth(update, context, se, ar, text)
        return
    if not token_valid(se):
        if is_private(update):
            await update.effective_message.reply_text(
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
    if ar.asking:
        if apply_typed_answer(ar, text):
            ar.asking = None
            await advance(update, context, ar, se)
        else:
            await ask(update, ar, ar.asking)
        return
    if not ar.deceased_name and len(text) >= 2:
        ar.deceased_name = text[:120]
        await advance(update, context, ar, se)
        return
    await advance(update, context, ar, se)


def stale(ar: Arrangement, data: str) -> bool:
    try:
        return int(data.split(":")[1]) != ar.query_id
    except (IndexError, ValueError):
        return True


async def require_session(update: Update) -> Optional[Session]:
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
        ar.service_type = SERVICE_OPTIONS[int(value)][1]
        ar.venue = None
    elif kind == "death":
        days = int(value)
        ar.date_of_death = (date.today() - timedelta(days=days)).isoformat()
    elif kind == "venue":
        found = next((item for item in ar.venues if str(item.get("id")) == value), None)
        if not found:
            await query.edit_message_text("That venue is no longer listed. Please start again.")
            return
        ar.venue = found
    ar.asking = None
    await advance(update, context, ar, se)


async def cb_confirm(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    query = update.callback_query
    await query.answer()
    se = await require_session(update)
    if se is None:
        return
    ar = get_arrangement(update.effective_chat.id)
    if stale(ar, query.data) or not ar.venue:
        await query.edit_message_text("That proposal has lapsed. Please start again.")
        return
    body = arrangement_body(ar)
    if "start" in body or "end" in body or "startAt" in body:
        await query.edit_message_text("The date cannot be chosen here.")
        return
    who = actor_name(update)
    await query.edit_message_text("Assigning an available date…")
    try:
        r = await api_arrange(se, body)
    except httpx.HTTPError as exc:
        log.warning("arrange HTTP error: %s", exc)
        await query.edit_message_text(
            "We could not reach the booking service. Nothing has been confirmed."
        )
        return
    if r.status_code == 401:
        se.invalidate()
        text, keyboard = render_review(ar)
        await query.edit_message_text(
            "<b>Nothing was confirmed — your session had expired.</b>\n"
            "Sign in again with /login, then confirm below.\n\n"
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
    venue = ar.venue or {}
    assigned = data.get("startAt")
    ar.reset_request()
    await query.edit_message_text(
        "<b>Confirmed.</b>\n\n"
        f"{esc(venue.get('name'))} — {esc(venue.get('homeName'))}\n"
        f"Assigned {esc(fmt_datetime(assigned))} – {esc(fmt_clock(data.get('endAt')))}\n"
        f"Reference {esc(str(data.get('id')))} · {esc(str(data.get('formattedAmount')))}\n\n"
        f"Confirmed by {esc(who)}.\n"
        "The date was assigned from what is free. It is stored in history.",
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
    app.add_handler(CallbackQueryHandler(cb_fact, pattern=r"^service:\d+$"))
    app.add_handler(CallbackQueryHandler(cb_fact, pattern=r"^death:\d+$"))
    app.add_handler(CallbackQueryHandler(cb_fact, pattern=r"^venue:\d+$"))
    app.add_handler(CallbackQueryHandler(cb_confirm, pattern=r"^confirm:\d+$"))
    app.add_handler(CallbackQueryHandler(cb_cancel, pattern=r"^cancel$"))
    app.add_handler(MessageHandler(filters.TEXT & ~filters.COMMAND, handle_text))
    log.info("%s bot polling on %s", BRAND, API_BASE)
    app.run_polling(allowed_updates=Update.ALL_TYPES)


if __name__ == "__main__":
    main()
