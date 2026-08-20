import asyncio
import time

import telegram_bot as bot


class _FakeQuery:
    def __init__(self, data, sink):
        self.data = data
        self._sink = sink

    async def answer(self, *args, **kwargs):
        return None

    async def edit_message_text(self, text, **kwargs):
        self._sink.append((text, kwargs.get("reply_markup")))


class _FakeMessage:
    def __init__(self, sink):
        self._sink = sink

    async def reply_text(self, text, **kwargs):
        self._sink.append(text)


class _FakeUpdate:
    def __init__(self, callback_data=None, user_id=1, chat_id=1):
        self.replies = []
        self.edits = []
        self.effective_message = _FakeMessage(self.replies)
        self.effective_user = type("U", (), {"id": user_id, "full_name": "Anna"})()
        self.effective_chat = type("C", (), {"id": chat_id, "type": "private"})()
        self.callback_query = _FakeQuery(callback_data, self.edits) if callback_data else None


def make(**values):
    ar = bot.Arrangement()
    for key, value in values.items():
        setattr(ar, key, value)
    return ar


class TestMissingFacts:
    def test_a_bare_draft_needs_name_death_service_guests_and_venue(self):
        assert bot.missing_facts(make()) == ["name", "death", "service", "mourners", "venue"]

    def test_a_full_draft_needs_no_questions(self):
        ar = make(
            deceased_name="Jan Kowalski",
            date_of_death="2026-08-19",
            service_type="MEMORIAL_SERVICE",
            mourners=40,
            venue={"id": 1, "name": "Willow Chapel", "maxAttendees": 80},
        )
        assert bot.missing_facts(ar) == []

    def test_phone_is_asked_only_when_the_account_needs_it(self):
        ar = make(
            deceased_name="Jan Kowalski",
            date_of_death="2026-08-19",
            service_type="MEMORIAL_SERVICE",
            mourners=20,
            venue={"id": 1},
        )
        se = bot.Session()
        se.phone_required = True
        assert bot.missing_facts(ar, se) == ["phone"]


class TestDeathDateParsing:
    def test_today_and_yesterday(self):
        assert bot.parse_death_date("today") == bot.date.today().isoformat()
        assert bot.parse_death_date("yesterday") == (
            bot.date.today() - bot.timedelta(days=1)
        ).isoformat()

    def test_typed_calendar_forms(self):
        assert bot.parse_death_date("12/08/2026") == "2026-08-12"
        assert bot.parse_death_date("12 August") == f"{bot.date.today().year}-08-12"


class TestTypedAnswers:
    def test_a_typed_guest_count_is_read_as_a_number(self):
        ar = make()
        ar.asking = "mourners"
        assert bot.apply_typed_answer(ar, "around 65 I think")
        assert ar.mourners == 65

    def test_a_guest_answer_with_no_number_is_rejected(self):
        ar = make()
        ar.asking = "mourners"
        assert not bot.apply_typed_answer(ar, "not sure")

    def test_a_typed_name_is_kept(self):
        ar = make()
        ar.asking = "name"
        assert bot.apply_typed_answer(ar, "Maria Nowak")
        assert ar.deceased_name == "Maria Nowak"


class TestArrangementBodyDoesNotChooseADate:
    def test_payload_has_no_start_or_end(self):
        ar = make(
            deceased_name="Jan Kowalski",
            date_of_death="2026-08-19",
            service_type="MEMORIAL_SERVICE",
            mourners=32,
            venue={"id": 7, "name": "Willow Chapel", "maxAttendees": 80},
        )
        body = bot.arrangement_body(ar)
        assert "start" not in body
        assert "end" not in body
        assert "startAt" not in body
        assert "endAt" not in body
        assert body["bookingSource"] == "TELEGRAM"
        assert body["attendees"] == 32
        assert body["venueId"] == 7
        assert body["dateOfDeath"] == "2026-08-19"


class TestReviewCopy:
    def test_review_promises_assignment_not_a_choice(self):
        ar = make(
            deceased_name="Jan Kowalski",
            mourners=40,
            venue={"name": "Willow Chapel", "homeName": "EverRest Warsaw", "maxAttendees": 80},
        )
        text, markup = bot.render_review(ar)
        assert "will be assigned" in text
        assert "do not choose" in text
        assert "up to 80" in text
        assert "Guests entered: 40" in text
        buttons = [b.callback_data for row in markup.inline_keyboard for b in row]
        assert any(item.startswith("confirm:") for item in buttons)
        assert not any("choose" in item for item in buttons)
        assert not any("slot" in item for item in buttons)


class TestSessionAndArrangementAreSeparate:
    def test_one_arrangement_serves_every_member_of_a_chat(self):
        bot.arrangements.clear()
        bot.sessions.clear()
        chat = -100
        assert bot.get_arrangement(chat) is bot.get_arrangement(chat)
        assert bot.get_session(1) is not bot.get_session(2)

    def test_resetting_a_request_invalidates_older_buttons(self):
        ar = bot.Arrangement()
        first = ar.query_id
        ar.reset_request()
        assert ar.query_id != first
        assert bot.stale(ar, f"confirm:{first}")
        assert not bot.stale(ar, f"confirm:{ar.query_id}")


class TestSessionExpiryDoesNotCostTheFamilyTheirWork:
    class _Resp:
        def __init__(self, status_code, payload=None):
            self.status_code = status_code
            self._payload = payload if payload is not None else {}

        def json(self):
            return self._payload

    def _signed_in(self):
        se = bot.Session()
        se.token = "t"
        se.expires_at = time.time() + 3600
        se.display_name = "Anna Kowalska"
        return se

    def _gathered(self):
        return make(
            deceased_name="Jan Kowalski",
            date_of_death="2026-08-19",
            service_type="MEMORIAL_SERVICE",
            mourners=40,
            venue={"id": 1, "name": "Willow Chapel", "homeName": "EverRest Warsaw", "maxAttendees": 80},
        )

    def test_signing_back_in_resumes_instead_of_asking_them_to_retype(self, monkeypatch):
        se, ar = bot.Session(), self._gathered()
        se.auth_step, se.pending_username = "password", "anna"
        resumed = []

        async def fake_login(session, username, password):
            session.token, session.expires_at = "fresh", time.time() + 3600
            session.display_name, session.auth_step = "Anna Kowalska", None
            return True

        async def fake_advance(update, context, arrangement, session):
            resumed.append(arrangement)

        monkeypatch.setattr(bot, "api_login", fake_login)
        monkeypatch.setattr(bot, "advance", fake_advance)
        update = _FakeUpdate()
        asyncio.run(bot.handle_auth(update, None, se, ar, "hunter2"))
        assert resumed == [ar]
        assert "picking up where we left off" in update.replies[0].lower()

    def test_a_confirmation_refused_on_auth_keeps_the_review_on_screen(self, monkeypatch):
        se, ar = self._signed_in(), self._gathered()

        async def fake_book(*args, **kwargs):
            return self._Resp(401)

        monkeypatch.setattr(bot, "api_arrange", fake_book)
        bot.sessions.clear()
        bot.sessions[7] = se
        bot.arrangements.clear()
        bot.arrangements[9] = ar
        update = _FakeUpdate(callback_data=f"confirm:{ar.query_id}", user_id=7, chat_id=9)
        asyncio.run(bot.cb_confirm(update, None))
        shown, markup = update.edits[-1]
        assert "Nothing was confirmed" in shown
        assert "Willow Chapel" in shown
        buttons = [b.callback_data for row in markup.inline_keyboard for b in row]
        assert f"confirm:{ar.query_id}" in buttons
        assert not bot.token_valid(se)


class TestMyArrangements:
    class _Resp:
        def __init__(self, status_code, payload=None):
            self.status_code = status_code
            self._payload = payload if payload is not None else {}

        def json(self):
            return self._payload

    def _signed_in(self):
        se = bot.Session()
        se.token, se.expires_at = "t", time.time() + 3600
        se.display_name = "Anna Kowalska"
        return se

    def test_it_lists_each_arrangement_from_the_database(self, monkeypatch):
        async def fake(session):
            return self._Resp(200, [{
                "reservationId": 12,
                "venueName": "Willow Chapel",
                "homeName": "EverRest Warsaw",
                "startAt": "2026-08-22T11:00",
                "endAt": "2026-08-22T12:00",
                "status": "CONFIRMED",
                "formattedAmount": "2400.00 PLN",
                "attendees": 40,
            }])

        monkeypatch.setattr(bot, "api_history", fake)
        bot.sessions.clear()
        bot.sessions[1] = self._signed_in()
        update = _FakeUpdate()
        asyncio.run(bot.cmd_my_arrangements(update, None))
        shown = update.replies[0]
        assert "Willow Chapel" in shown
        assert "Reference 12" in shown
        assert "40 guests" in shown
        assert "database" in shown.lower()

    def test_nothing_arranged_yet_invites_the_account(self, monkeypatch):
        async def fake(session):
            return self._Resp(200, [])

        monkeypatch.setattr(bot, "api_history", fake)
        bot.sessions.clear()
        bot.sessions[1] = self._signed_in()
        update = _FakeUpdate()
        asyncio.run(bot.cmd_my_arrangements(update, None))
        assert "nothing arranged yet" in update.replies[0].lower()

    def test_a_rejected_token_is_dropped_here_too(self, monkeypatch):
        async def fake(session):
            return self._Resp(401)

        monkeypatch.setattr(bot, "api_history", fake)
        bot.sessions.clear()
        se = self._signed_in()
        bot.sessions[1] = se
        update = _FakeUpdate()
        asyncio.run(bot.cmd_my_arrangements(update, None))
        assert not bot.token_valid(se)
        assert "/login" in update.replies[0]
