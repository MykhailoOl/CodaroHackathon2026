"""The family's channel for a funeral arrangement.

This is deliberately not a booking bot, because a funeral is not a booking.

In an ordinary reservation the person who books is the person who attends and the
person who pays, and that person picks a date off a grid. Here all three come apart:
the subject of the arrangement is deceased and cannot choose, the payer is a grieving
relative acting under pressure, and nobody chooses the date at all. The server derives
it from the date of death, the service and what the venue has free. This bot's whole
job is to collect the facts the server needs, show the family what came back, and take
one answer: yes, or not this.

Two things follow, and they are why the channel exists at all:

  * A funeral runs against a hard clock, and a clock needs to reach people rather than
    wait to be visited. Telegram pushes; a web page cannot.

  * The decision is not one person's. Add the bot to the family group and the whole
    family sees the same proposal, and whoever answers is named. Credentials belong to
    a person, the arrangement belongs to the chat — which is exactly the shape of a
    family where the payer, the next of kin and the executor are three different people.

Talks to the same reservation-assistant API the web app's arrange page uses. That API
authenticates with the ordinary form login and a session cookie, so the bot signs in
the way a browser does and carries the cookie plus the CSRF token the API wants on
every write.
"""

import html
import logging
import os
import re
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

# One of CASH, CARD_ON_SITE, ONLINE_TRANSFER — whatever PaymentMethod carries.
PAYMENT_METHOD = os.getenv("PAYMENT_METHOD", "ONLINE_TRANSFER")

# ---------------------------------------------------------------- pivot seams
# Everything domain-specific is an env override so retargeting the catalogue never
# requires editing this file. "Label=VALUE": the label goes on the button, the value
# is what the API's enum expects.

BRAND = os.getenv("BOT_BRAND_NAME", "EverRest")


def _options(env_name: str, default: str) -> List[Tuple[str, str]]:
    return [
        (part.split("=", 1)[0].strip(), part.split("=", 1)[1].strip())
        for part in os.getenv(env_name, default).split("|")
        if "=" in part
    ]


SERVICE_OPTIONS = _options(
    "BOT_SERVICE_OPTIONS",
    "Burial ceremony=BURIAL_CEREMONY|"
    "Cremation=CREMATION_CEREMONY|"
    "Memorial service=MEMORIAL_SERVICE|"
    "Farewell ceremony=FAREWELL_CEREMONY",
)

PACKAGE_OPTIONS = _options(
    "BOT_PACKAGE_OPTIONS",
    "Essential=ESSENTIAL|Classic=CLASSIC|Tribute=TRIBUTE",
)

ATTENDEE_CHOICES = [8, 20, 40, 60, 100, 150]

# Which spaces each service can be held in. The server is the authority — it rejects a
# mismatch on /preview — and this mirrors ServiceType.allows(VenueType) only so the
# family is never offered a space that would then be refused.
SERVICE_VENUE_TYPES = {
    "BURIAL_CEREMONY": {"CHAPEL", "CEREMONY_HALL", "MEMORIAL_GARDEN"},
    "CREMATION_CEREMONY": {"CREMATORIUM", "CHAPEL"},
    "MEMORIAL_SERVICE": {"CHAPEL", "CEREMONY_HALL", "MEMORIAL_GARDEN", "RECEPTION_HALL"},
    "FAREWELL_CEREMONY": {"CHAPEL", "CEREMONY_HALL", "MEMORIAL_GARDEN"},
}

CANCEL_WORDS = {"cancel", "stop", "start over", "reset"}

# Said whenever the API stops accepting the session. It has to promise the resume: a
# family that has just typed out how their father died will not do it twice.
SIGN_IN_AGAIN = (
    "Your session has expired. Sign in again with /login — nothing you have told me "
    "is lost, and I will pick up exactly where we left off."
)

logging.basicConfig(
    format="%(asctime)s %(levelname)s %(name)s %(message)s", level=logging.INFO
)
log = logging.getLogger("everrest_bot")


# ------------------------------------------------------------------ state


class Session:
    """One signed-in Telegram user. The API authenticates a browser session, so what
    we hold is the cookie jar and the CSRF token that goes with it."""

    def __init__(self) -> None:
        self.auth_step: Optional[str] = None  # "username" | "password" | None
        self.pending_username: Optional[str] = None
        self.username: Optional[str] = None
        self.display_name: Optional[str] = None
        self.cookies: httpx.Cookies = httpx.Cookies()
        self.csrf_token: Optional[str] = None
        self.csrf_header: str = "X-CSRF-TOKEN"
        self.signed_in: bool = False
        self.phone_required: bool = False

    def invalidate(self) -> None:
        """The server refused the session. Drop it, or every later message walks into
        the same 401. The arrangement is untouched: it belongs to the chat, not to the
        credentials, and the family should never retype it."""
        self.cookies = httpx.Cookies()
        self.csrf_token = None
        self.signed_in = False
        self.auth_step = None
        self.pending_username = None


class Arrangement:
    """What this chat is arranging. Shared by everyone in the room."""

    def __init__(self) -> None:
        self.deceased: Optional[str] = None
        self.date_of_death: Optional[str] = None  # ISO yyyy-mm-dd
        self.service_type: Optional[str] = None
        self.funeral_package: Optional[str] = None
        self.attendees: Optional[int] = None
        self.home_id: Optional[int] = None
        self.venue_id: Optional[int] = None
        self.venue_label: Optional[str] = None
        self.phone: Optional[str] = None
        self.preview: Optional[dict] = None
        self.homes: List[dict] = []
        self.venues: List[dict] = []
        self.asking: Optional[str] = None
        self.query_id: int = 0

    def reset_request(self) -> None:
        next_id = self.query_id + 1
        self.__init__()
        self.query_id = next_id


sessions: Dict[int, Session] = {}
arrangements: Dict[int, Arrangement] = {}


def get_session(user_id: int) -> Session:
    return sessions.setdefault(user_id, Session())


def get_arrangement(chat_id: int) -> Arrangement:
    return arrangements.setdefault(chat_id, Arrangement())


def esc(text: Optional[Any]) -> str:
    return html.escape(str(text)) if text is not None else ""


def token_valid(se: Session) -> bool:
    return se.signed_in and se.csrf_token is not None


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


def btn(label: str, data: str) -> InlineKeyboardButton:
    return InlineKeyboardButton(label, callback_data=data)


def rows_of(options: List[Tuple[str, str]], prefix: str) -> List[List[InlineKeyboardButton]]:
    return [[btn(label, f"{prefix}:{i}")] for i, (label, _) in enumerate(options)]


# --------------------------------------------------------------------- api

CSRF_INPUT = re.compile(r'name="_csrf"\s+value="([^"]+)"')
CSRF_META = re.compile(r'<meta name="_csrf" content="([^"]+)"')
CSRF_HEADER_META = re.compile(r'<meta name="_csrf_header" content="([^"]+)"')


async def api_login(se: Session, username: str, password: str) -> bool:
    """Sign in the way the browser does: the login form's CSRF token, then the form
    POST, then the session cookie and the token the API expects on writes."""
    try:
        async with httpx.AsyncClient(timeout=20, follow_redirects=False) as client:
            page = await client.get(f"{API_BASE}/login")
            form_token = CSRF_INPUT.search(page.text)
            if not form_token:
                log.warning("no CSRF token on the login form")
                return False
            resp = await client.post(
                f"{API_BASE}/login",
                data={
                    "username": username,
                    "password": password,
                    "_csrf": form_token.group(1),
                },
            )
            # A failed login redirects back to the form with ?error; a good one goes home.
            location = resp.headers.get("location") or ""
            if resp.status_code != 302 or "error" in location:
                return False
            home = await client.get(f"{API_BASE}/")
            meta_token = CSRF_META.search(home.text)
            meta_header = CSRF_HEADER_META.search(home.text)
            if not meta_token:
                log.warning("no CSRF meta tag after sign-in")
                return False
            se.cookies = client.cookies
            se.csrf_token = meta_token.group(1)
            se.csrf_header = meta_header.group(1) if meta_header else "X-CSRF-TOKEN"
    except httpx.HTTPError as exc:
        log.warning("login HTTP error: %s", exc)
        return False

    se.username = username
    se.signed_in = True
    se.auth_step = None

    session = await api_session(se)
    if session is None:
        se.invalidate()
        return False
    se.display_name = session.get("username") or username
    se.phone_required = bool(session.get("phoneRequired"))
    return True


def _headers(se: Session) -> Dict[str, str]:
    return {"Content-Type": "application/json", se.csrf_header: se.csrf_token or ""}


async def _get(se: Session, path: str) -> httpx.Response:
    async with httpx.AsyncClient(timeout=20, cookies=se.cookies) as client:
        return await client.get(f"{API_BASE}{path}", headers=_headers(se))


async def _post(se: Session, path: str, payload: dict) -> httpx.Response:
    async with httpx.AsyncClient(timeout=30, cookies=se.cookies) as client:
        return await client.post(f"{API_BASE}{path}", headers=_headers(se), json=payload)


async def api_session(se: Session) -> Optional[dict]:
    try:
        r = await _get(se, "/api/reservation-assistant/session")
    except httpx.HTTPError as exc:
        log.warning("session HTTP error: %s", exc)
        return None
    if r.status_code != 200:
        return None
    data = r.json()
    return data if data.get("authenticated") else None


async def api_homes(se: Session) -> httpx.Response:
    return await _get(se, "/api/reservation-assistant/homes")


async def api_venues(se: Session, home_id: int) -> httpx.Response:
    return await _get(se, f"/api/reservation-assistant/homes/{home_id}/venues")


def build_request(ar: Arrangement) -> dict:
    """The one shape both /preview and /arrangements take. No date goes in it — the
    server derives when the service can be held."""
    payload = {
        "venueId": ar.venue_id,
        "serviceType": ar.service_type,
        "funeralPackage": ar.funeral_package,
        "deceasedFullName": ar.deceased,
        "dateOfDeath": ar.date_of_death,
        "attendees": ar.attendees,
        "paymentMethod": PAYMENT_METHOD,
        "extraIds": [],
    }
    if ar.phone:
        payload["phone"] = ar.phone
    return payload


async def api_preview(se: Session, ar: Arrangement) -> httpx.Response:
    return await _post(se, "/api/reservation-assistant/preview", build_request(ar))


async def api_create(se: Session, ar: Arrangement) -> httpx.Response:
    return await _post(se, "/api/reservation-assistant/arrangements", build_request(ar))


def _json_or_none(r: httpx.Response) -> Any:
    try:
        return r.json()
    except ValueError:
        return None


def error_message(body: Any, fallback: str) -> str:
    if isinstance(body, dict):
        return str(body.get("message") or body.get("error") or fallback)
    return fallback


# ------------------------------------------------------------- formatting


def fmt_date(value: Optional[str]) -> str:
    if not value:
        return "—"
    try:
        return date.fromisoformat(str(value)[:10]).strftime("%a %d %b")
    except ValueError:
        return str(value)


def fmt_datetime(value: Optional[str]) -> str:
    if not value:
        return "—"
    try:
        return datetime.fromisoformat(value).strftime("%a %d %b, %H:%M")
    except (ValueError, TypeError):
        return str(value)


DAY_MONTH = re.compile(r"(\d{1,2})[./\s-]+(\d{1,2}|[a-z]{3,9})")
MONTHS = {m: i for i, m in enumerate(
    ["jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"],
    start=1,
)}


def parse_date(text: str, today: Optional[date] = None) -> Optional[str]:
    """A typed date of death. Families type '12 August', '12/08', or 'three days ago',
    so accept all three and hand the API an ISO date."""
    today = today or date.today()
    lower = text.strip().lower()
    if "today" in lower:
        return today.isoformat()
    if "yesterday" in lower:
        return (today - timedelta(days=1)).isoformat()
    days_ago = re.search(r"(\d{1,3})\s*days?\s*ago", lower)
    if days_ago:
        return (today - timedelta(days=int(days_ago.group(1)))).isoformat()
    match = DAY_MONTH.search(lower)
    if match:
        day = int(match.group(1))
        raw_month = match.group(2)
        month = int(raw_month) if raw_month.isdigit() else MONTHS.get(raw_month[:3], 0)
        if month and 1 <= day <= 31 and 1 <= month <= 12:
            try:
                parsed = date(today.year, month, day)
            except ValueError:
                return None
            # A date that has not happened yet this year means last year.
            if parsed > today:
                parsed = date(today.year - 1, month, day)
            return parsed.isoformat()
    return None


# ------------------------------------------------------- questions & flow


QUESTIONS = {
    "deceased": "What was the full name of the person who died?",
    "death": "When did the death occur?",
    "service": "What kind of service is this?",
    "package": "Which arrangement would you like?",
    "attendees": "About how many people will attend?",
    "home": "Which funeral home?",
    "venue": "Which space?",
    "phone": "A phone number the funeral home can reach you on?",
}


def missing_field(ar: Arrangement, se: Session) -> Optional[str]:
    """The next thing the API still needs. Order matters: the venue list is filtered by
    how many people are coming, so the number is asked before the space."""
    if not ar.deceased:
        return "deceased"
    if not ar.date_of_death:
        return "death"
    if not ar.service_type:
        return "service"
    if not ar.funeral_package:
        return "package"
    if not ar.attendees:
        return "attendees"
    if not ar.home_id:
        return "home"
    if not ar.venue_id:
        return "venue"
    if se.phone_required and not ar.phone:
        return "phone"
    return None


def venue_fits(venue: dict, ar: Arrangement) -> bool:
    """A space is offered only if it holds the mourners and hosts this service."""
    if ar.attendees and venue.get("maxAttendees", 0) < ar.attendees:
        return False
    allowed = SERVICE_VENUE_TYPES.get(ar.service_type or "")
    return not allowed or venue.get("venueType") in allowed


def current_send(update: Update):
    if update.callback_query:
        return update.callback_query.message.reply_text
    return update.effective_message.reply_text


async def ask(update: Update, se: Session, ar: Arrangement, field: str) -> None:
    ar.asking = field
    send = current_send(update)
    cancel_row = [btn("Start over", "cancel")]
    hint = ""

    if field in ("deceased", "phone"):
        await send(esc(QUESTIONS[field]), reply_markup=InlineKeyboardMarkup([cancel_row]))
        return

    if field == "death":
        rows = [
            [btn("Today", "death:0"), btn("Yesterday", "death:1")],
            [btn("2 days ago", "death:2"), btn("3 days ago", "death:3")],
            [btn("Longer ago", "death:more")],
        ]
        hint = "\nYou can also type a date."
    elif field == "service":
        rows = rows_of(SERVICE_OPTIONS, "service")
    elif field == "package":
        rows = rows_of(PACKAGE_OPTIONS, "package")
    elif field == "attendees":
        rows = [
            [btn(str(n), f"attendees:{n}") for n in ATTENDEE_CHOICES[i: i + 3]]
            for i in range(0, len(ATTENDEE_CHOICES), 3)
        ]
        hint = "\nAn approximate number is fine."
    elif field == "home":
        r = await api_homes(se)
        if r.status_code == 401:
            se.invalidate()
            await send(SIGN_IN_AGAIN)
            return
        if r.status_code != 200:
            await send("We could not reach the funeral homes just now. Please try again.")
            return
        ar.homes = r.json()
        rows = [[btn(home["name"], f"home:{i}")] for i, home in enumerate(ar.homes)]
    else:  # venue
        r = await api_venues(se, ar.home_id)
        if r.status_code == 401:
            se.invalidate()
            await send(SIGN_IN_AGAIN)
            return
        if r.status_code != 200:
            await send("We could not reach that home's spaces just now. Please try again.")
            return
        ar.venues = r.json()
        rows = [
            [btn(f"{venue['name']} · {venue.get('venueTypeLabel') or ''} "
                 f"· up to {venue['maxAttendees']}".replace("·  ·", "·"), f"venue:{i}")]
            for i, venue in enumerate(ar.venues)
            if venue_fits(venue, ar)
        ]
        if not rows:
            await send(
                "No space at that home holds this many people. Choose another home.",
                reply_markup=InlineKeyboardMarkup(
                    [[btn("Another home", "reask:home")], cancel_row]
                ),
            )
            return

    await send(
        esc(QUESTIONS[field]) + esc(hint),
        reply_markup=InlineKeyboardMarkup(rows + [cancel_row]),
    )


async def advance(update: Update, se: Session, ar: Arrangement) -> None:
    field = missing_field(ar, se)
    if field:
        await ask(update, se, ar, field)
        return
    ar.asking = None
    await propose(update, se, ar)


def render_preview(ar: Arrangement) -> Tuple[str, InlineKeyboardMarkup]:
    preview = ar.preview or {}
    dates = preview.get("dates") or []
    lines = [f"<b>{esc(BRAND)} — proposed arrangement</b>", ""]
    lines.append(f"For <b>{esc(ar.deceased)}</b>")
    lines.append(f"Died {esc(fmt_date(ar.date_of_death))}")
    lines.append("")
    # The date is assigned by the funeral home, not picked — the API takes no date and
    # settles it on confirmation. Say so, and never print the candidates as if they
    # were a menu: the assigned day can fall outside the ones sampled here.
    if dates:
        lines.append("<b>When it can be held</b>")
        lines.append(
            f"Days are open from {esc(fmt_date(dates[0]))} onwards. The funeral home "
            "settles the exact day and hour when you confirm."
        )
    else:
        lines.append("<b>When it can be held</b>")
        lines.append("The funeral home will settle the day when you confirm.")
    lines.append("")
    lines.append(esc(ar.venue_label))
    detail = []
    if ar.attendees:
        detail.append(f"{ar.attendees} attending")
    if preview.get("amount") is not None:
        detail.append(f"{preview['amount']} {preview.get('currency') or ''}".strip())
    if detail:
        lines.append(esc(" · ".join(detail)))
    if preview.get("notice"):
        lines.append("")
        lines.append(esc(preview["notice"]))

    rows = [
        [btn("Confirm this arrangement", f"confirm:{ar.query_id}")],
        [btn("Choose a different space", "reask:home")],
        [btn("Start over", "cancel")],
    ]
    return "\n".join(lines), InlineKeyboardMarkup(rows)


async def propose(update: Update, se: Session, ar: Arrangement) -> None:
    send = current_send(update)
    if not token_valid(se):
        await send(SIGN_IN_AGAIN)
        return
    try:
        r = await api_preview(se, ar)
    except httpx.HTTPError as exc:
        log.warning("preview HTTP error: %s", exc)
        await send("We could not reach the funeral home's system. Please try again in a moment.")
        return

    if r.status_code == 401:
        se.invalidate()
        await send(SIGN_IN_AGAIN)
        return
    if r.status_code != 200:
        await send(esc(error_message(_json_or_none(r), f"The request failed ({r.status_code}).")))
        return

    ar.preview = r.json()
    text, keyboard = render_preview(ar)
    await send(text, parse_mode=ParseMode.HTML, reply_markup=keyboard)


# -------------------------------------------------------------- commands


async def cmd_start(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    se = get_session(update.effective_user.id)
    if token_valid(se):
        await update.effective_message.reply_text(
            f"<b>{esc(BRAND)}</b>\n\n"
            f"Signed in as {esc(se.display_name)}.\n\n"
            "Tell me the name of the person who died and I will take it from there.",
            parse_mode=ParseMode.HTML,
        )
        return
    await update.effective_message.reply_text(
        f"<b>{esc(BRAND)}</b>\n\n"
        "You do not have to choose a date. Tell us the circumstances and the funeral "
        "home works out when the service can be held, then holds it for you to approve."
        "\n\nSign in with /login to begin. /help explains the rest.",
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
            f"Already signed in as <b>{esc(se.display_name)}</b>. "
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
        "Tell me who died and a few details, and I will come back with the dates the "
        "funeral home can hold and what it costs, for you to approve.\n\n"
        "/login — sign in (privately)\n"
        "/logout — sign out\n"
        "/status — this arrangement and your session\n"
        "/cancel — clear the current arrangement\n\n"
        "You can add me to a family group. The arrangement is shared by everyone in the "
        "chat; each person signs in privately and whoever answers is named.",
        parse_mode=ParseMode.HTML,
    )


async def cmd_status(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    se = get_session(update.effective_user.id)
    ar = get_arrangement(update.effective_chat.id)
    lines = []
    if token_valid(se):
        lines.append(f"Signed in as <b>{esc(se.display_name)}</b>.")
    else:
        lines.append("Not signed in. Use /login.")
    if ar.deceased:
        lines.append("")
        lines.append(f"Arranging for <b>{esc(ar.deceased)}</b>")
        if ar.date_of_death:
            lines.append(f"Died {esc(fmt_date(ar.date_of_death))}")
        if ar.venue_label:
            lines.append(esc(ar.venue_label))
        missing = missing_field(ar, se)
        if missing:
            lines.append(f"Still needed: {esc(QUESTIONS[missing])}")
    await update.effective_message.reply_text("\n".join(lines), parse_mode=ParseMode.HTML)


# --------------------------------------------------------------- messages


async def handle_text(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    se = get_session(update.effective_user.id)
    ar = get_arrangement(update.effective_chat.id)
    text = (update.effective_message.text or "").strip()

    # Credentials are only ever collected in a one-to-one chat.
    if se.auth_step and is_private(update):
        await handle_auth(update, se, ar, text)
        return

    if not token_valid(se):
        if is_private(update):
            await update.effective_message.reply_text(
                "Please sign in with /login, then I will carry on from what you have "
                "already told me." if ar.deceased else
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
            await advance(update, se, ar)
        else:
            await ask(update, se, ar, ar.asking)
        return

    if not ar.deceased:
        ar.deceased = text
        await advance(update, se, ar)
        return

    await advance(update, se, ar)


async def handle_auth(update: Update, se: Session, ar: Arrangement, text: str) -> None:
    if se.auth_step == "username":
        se.pending_username = text
        se.auth_step = "password"
        await update.effective_message.reply_text("Password?")
        return
    ok = await api_login(se, se.pending_username or "", text)
    se.pending_username = None
    if ok:
        if ar.deceased:
            # They were already part-way through when the session died. Resume on the
            # facts this chat still holds rather than making them tell it again.
            await update.effective_message.reply_text(
                f"Signed in as <b>{esc(se.display_name)}</b>. Picking up where we left off.",
                parse_mode=ParseMode.HTML,
            )
            await advance(update, se, ar)
            return
        await update.effective_message.reply_text(
            f"Signed in as <b>{esc(se.display_name)}</b>.\n\n"
            "Tell me the name of the person who died.",
            parse_mode=ParseMode.HTML,
        )
    else:
        se.auth_step = None
        await update.effective_message.reply_text(
            "That did not sign you in. Try /login again."
        )


def apply_typed_answer(ar: Arrangement, text: str) -> bool:
    """A typed reply to the question on screen."""
    field = ar.asking
    if field == "deceased":
        ar.deceased = text.strip()
        return True
    if field == "death":
        parsed = parse_date(text)
        if not parsed:
            return False
        ar.date_of_death = parsed
        return True
    if field == "attendees":
        match = re.search(r"\d{1,4}", text)
        if not match:
            return False
        ar.attendees = int(match.group(0))
        return True
    if field == "phone":
        ar.phone = text.strip()
        return True
    return False


# -------------------------------------------------------------- callbacks


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
        ar.service_type = SERVICE_OPTIONS[int(value)][1]
    elif kind == "package":
        ar.funeral_package = PACKAGE_OPTIONS[int(value)][1]
    elif kind == "attendees":
        ar.attendees = int(value)
    elif kind == "death":
        if value == "more":
            ar.asking = "death"
            await query.edit_message_text(
                "When did the death occur? Please type the date, for example "
                "<i>12 August</i> or <i>12/08</i>.",
                parse_mode=ParseMode.HTML,
            )
            return
        ar.date_of_death = (date.today() - timedelta(days=int(value))).isoformat()
    elif kind == "home":
        ar.home_id = ar.homes[int(value)]["id"]
        ar.venue_id = None
        ar.venue_label = None
    elif kind == "venue":
        venue = ar.venues[int(value)]
        ar.venue_id = venue["id"]
        ar.venue_label = f"{venue['name']} — {venue.get('address') or ''}".strip(" —")
    elif kind == "reask":
        ar.home_id = None
        ar.venue_id = None
        ar.venue_label = None
        ar.preview = None

    ar.asking = None
    await advance(update, se, ar)


async def cb_confirm(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    query = update.callback_query
    await query.answer()
    se = await require_session(update)
    if se is None:
        return
    ar = get_arrangement(update.effective_chat.id)
    if query.data.split(":")[1] != str(ar.query_id):
        await query.edit_message_text("That proposal has lapsed. Please start again.")
        return

    who = actor_name(update)
    await query.edit_message_text("Confirming…")

    try:
        r = await api_create(se, ar)
    except httpx.HTTPError as exc:
        log.warning("create HTTP error: %s", exc)
        await query.edit_message_text(
            "We could not reach the funeral home's system. Nothing has been confirmed — "
            "please try again in a moment."
        )
        return

    if r.status_code == 401:
        se.invalidate()
        # Nothing was booked, and the hold has not moved. Put the card back with its
        # button so signing in is the only step between here and a confirmation.
        text, keyboard = render_preview(ar)
        await query.edit_message_text(
            "<b>Nothing was confirmed — your session had expired.</b>\n"
            "Sign in again with /login, then confirm below.\n\n" + text,
            parse_mode=ParseMode.HTML,
            reply_markup=keyboard,
        )
        return

    if r.status_code not in (200, 201):
        await query.edit_message_text(
            "Nothing was confirmed. "
            + esc(error_message(_json_or_none(r), f"The request failed ({r.status_code}).")),
            parse_mode=ParseMode.HTML,
        )
        return

    data = r.json()
    dates = data.get("dates") or []
    when = fmt_datetime(data["startAt"]) if data.get("startAt") else fmt_date(
        dates[0] if dates else None
    )
    await query.edit_message_text(
        "<b>Confirmed.</b>\n\n"
        f"For {esc(ar.deceased)}\n"
        f"{esc(ar.venue_label)}\n"
        f"{esc(when)}\n"
        f"Reference {esc(data.get('id'))} · "
        f"{esc(data.get('formattedAmount') or data.get('amount'))}\n"
        f"Status {esc(str(data.get('status', '')).title())}\n\n"
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
    app.add_handler(CallbackQueryHandler(
        cb_fact, pattern=r"^(?:service|package|attendees|home|venue):\d+$"))
    app.add_handler(CallbackQueryHandler(cb_fact, pattern=r"^death:(?:\d+|more)$"))
    app.add_handler(CallbackQueryHandler(cb_fact, pattern=r"^reask:[a-z]+$"))
    app.add_handler(CallbackQueryHandler(cb_confirm, pattern=r"^confirm:\d+$"))
    app.add_handler(CallbackQueryHandler(cb_cancel, pattern=r"^cancel$"))
    app.add_handler(MessageHandler(filters.TEXT & ~filters.COMMAND, handle_text))
    log.info("%s bot polling on %s", BRAND, API_BASE)
    app.run_polling(allowed_updates=Update.ALL_TYPES)


if __name__ == "__main__":
    main()
