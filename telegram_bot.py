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
        self.pending_intent: Optional[str] = None
        self.suggestions: list = []
        self.spec: Optional[dict] = None
        self.relaxation_trail: list = []
        self.party_size: int = 2


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
    st.pending_intent = None
    st.suggestions = []
    await update.effective_message.reply_text("Signed out.")


async def cmd_cancel(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    st = get_state(update.effective_chat.id)
    st.auth_step = None
    st.pending_username = None
    st.pending_intent = None
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

    st.pending_intent = text
    st.suggestions = []
    st.spec = None
    st.relaxation_trail = []
    rows = [
        [InlineKeyboardButton(str(n), callback_data=f"party:{n}") for n in PARTY_CHOICES[i : i + 4]]
        for i in range(0, len(PARTY_CHOICES), 4)
    ]
    rows.append([InlineKeyboardButton("❌ Cancel", callback_data="cancel")])
    keyboard = InlineKeyboardMarkup(rows)
    await update.effective_message.reply_text(
        f"Got it: <i>{esc(text)}</i>\n\nHow many people?",
        parse_mode=ParseMode.HTML,
        reply_markup=keyboard,
    )


# ---------------------------------------------------------------- callbacks


async def cb_party(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    query = update.callback_query
    await query.answer()
    chat_id = update.effective_chat.id
    st = get_state(chat_id)

    party_size = int(query.data.split(":", 1)[1])
    st.party_size = party_size

    if not token_valid(st):
        await query.edit_message_text("Your session expired. Use /login and try again.")
        return

    intent = st.pending_intent
    if not intent:
        await query.edit_message_text("Start over: describe what you want to book.")
        return

    await query.edit_message_text("🔎 Finding slots…")

    try:
        r = await api_suggest(st, intent, party_size)
    except httpx.HTTPError as exc:
        log.warning("suggest HTTP error: %s", exc)
        await query.edit_message_text("Could not reach the booking service. Try again in a moment.")
        return

    if r.status_code == 401:
        await query.edit_message_text("Your session expired. Use /login and try again.")
        return

    if r.status_code != 200:
        await query.edit_message_text(
            f"⚠️ {esc(error_message(r.json(), f'Request failed ({r.status_code}).'))}"
        )
        return

    data = r.json()
    st.spec = data.get("spec")
    st.relaxation_trail = data.get("relaxationTrail", [])
    st.suggestions = data.get("suggestions", [])

    if not st.suggestions:
        parts = [
            "No slots matched, even after relaxing constraints.",
            "Try a wider time window or a different resource.",
        ]
        if st.relaxation_trail:
            parts.append("\n<b>What was relaxed:</b>")
            for step in st.relaxation_trail:
                parts.append(f"• {esc(step.get('detail', step.get('action', '')))}")
        await query.edit_message_text("\n".join(parts), parse_mode=ParseMode.HTML)
        return

    header = [f"<b>{len(st.suggestions)} suggestion(s)</b> for {party_size} people:"]
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
            [InlineKeyboardButton(f"📅 Book #{i + 1}", callback_data=f"book:{i}")]
            for i in range(len(st.suggestions))
        ]
        + [[InlineKeyboardButton("❌ Cancel", callback_data="cancel")]]
    )
    await query.edit_message_text("\n".join(header), parse_mode=ParseMode.HTML, reply_markup=keyboard)


async def cb_book(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    query = update.callback_query
    await query.answer()
    chat_id = update.effective_chat.id
    st = get_state(chat_id)

    idx = int(query.data.split(":", 1)[1])
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
    st.pending_intent = None
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
    app.add_handler(CallbackQueryHandler(cb_party, pattern=r"^party:\d+$"))
    app.add_handler(CallbackQueryHandler(cb_book, pattern=r"^book:\d+$"))
    app.add_handler(CallbackQueryHandler(cb_cancel, pattern=r"^cancel$"))
    app.add_handler(MessageHandler(filters.TEXT & ~filters.COMMAND, handle_text))
    log.info("Courtly bot polling on %s", API_BASE)
    app.run_polling(allowed_updates=Update.ALL_TYPES)


if __name__ == "__main__":
    main()