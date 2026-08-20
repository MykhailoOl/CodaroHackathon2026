"""Tests for the bot's own logic: what it still needs to ask, and the sentence it
hands the backend.

The bot never derives a window — that lives on the server. What it does own is
knowing which facts are still missing and completing the family's sentence with the
answers they tapped. Both sides of that seam are pinned: the phrases asserted here
are the same ones ArrangementFactsParserTest parses on the Java side.
"""

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
    """Only the handful of attributes the handlers under test actually touch."""

    def __init__(self, callback_data=None, user_id=1, chat_id=1):
        self.replies = []
        self.edits = []
        self.effective_message = _FakeMessage(self.replies)
        self.effective_user = type("U", (), {"id": user_id, "full_name": "Anna"})()
        self.effective_chat = type("C", (), {"id": chat_id, "type": "private"})()
        self.callback_query = _FakeQuery(callback_data, self.edits) if callback_data else None


def make(intent=None, **facts):
    ar = bot.Arrangement()
    ar.intent = intent
    ar.facts = dict(facts)
    return ar


class TestMissingFacts:
    def test_a_bare_greeting_is_missing_everything(self):
        assert bot.missing_facts(make("hello")) == [
            "service", "death", "rite", "certificate", "mourners"
        ]

    def test_a_full_account_needs_no_questions(self):
        ar = make(
            "my father died yesterday, orthodox chapel service, "
            "we have the death certificate, about 40 mourners"
        )
        assert bot.missing_facts(ar) == []

    def test_facts_gathered_from_buttons_close_the_gaps(self):
        ar = make(
            "hello",
            service="chapel service",
            death="the death was yesterday",
            rite="catholic",
            certificate="we have the death certificate",
        )
        ar.mourners = 40
        assert bot.missing_facts(ar) == []

    def test_a_death_phrase_without_a_date_still_asks_when(self):
        ar = make("my mother passed away, catholic chapel service, 30 mourners, "
                  "we have the death certificate")
        assert "death" in bot.missing_facts(ar)

    def test_a_coroner_counts_as_certificate_information(self):
        ar = make("dad died yesterday, chapel, jewish, 20 mourners, the coroner has the body")
        assert bot.missing_facts(ar) == []


class TestDeathDateDetection:
    def test_a_date_must_sit_beside_the_death_phrase(self):
        # "saturday" here is a preference, not the date of death.
        assert not bot._has_death_date(make("chapel service saturday for 40 mourners"))

    def test_a_date_beside_the_death_phrase_counts(self):
        assert bot._has_death_date(make("she died on saturday"))
        assert bot._has_death_date(make("mum passed away this morning"))
        assert bot._has_death_date(make("the death was 3 days ago"))


class TestBuildQuery:
    def test_button_answers_are_appended_to_the_family_words(self):
        ar = make(
            "we need to arrange something for my mother",
            service="chapel service",
            death="the death was yesterday",
            rite="catholic",
            certificate="we have the death certificate",
            mourners="for 40 mourners",
        )
        query = bot.build_query(ar)
        assert query.startswith("we need to arrange something for my mother")
        for phrase in ("chapel service", "the death was yesterday", "catholic",
                       "we have the death certificate", "for 40 mourners"):
            assert phrase in query
        # The death must be stated before the certificate, or the backend reads the
        # word "death" inside "death certificate" as the death phrase.
        assert query.index("the death was yesterday") < query.index("we have the death certificate")

    def test_a_fact_already_in_the_family_words_is_not_repeated(self):
        ar = make("dad died yesterday, catholic", rite="catholic")
        assert bot.build_query(ar).lower().count("catholic") == 1


class TestTypedAnswers:
    def test_a_typed_date_becomes_a_death_phrase(self):
        ar = make("hello")
        ar.asking = "death"
        assert bot.apply_typed_answer(ar, "12 August")
        assert ar.facts["death"] == "the death was 12 august"

    def test_a_typed_mourner_count_is_read_as_a_number(self):
        ar = make("hello")
        ar.asking = "mourners"
        assert bot.apply_typed_answer(ar, "around 65 I think")
        assert ar.mourners == 65
        assert ar.facts["mourners"] == "for 65 mourners"

    def test_a_mourner_answer_with_no_number_is_rejected(self):
        ar = make("hello")
        ar.asking = "mourners"
        assert not bot.apply_typed_answer(ar, "not sure")


class TestAlternativeLabels:
    """Families comparing funeral times need the cost of the change, not a score."""

    def _s(self, iso):
        return {"start": iso}

    def test_a_later_hour_on_the_same_day(self):
        label = bot.relative_to_first(self._s("2026-08-22T11:00"), self._s("2026-08-22T14:00"))
        assert label == "same day, 3 hours later"

    def test_the_next_day(self):
        label = bot.relative_to_first(self._s("2026-08-22T11:00"), self._s("2026-08-23T09:00"))
        assert label == "one day later"

    def test_several_days_earlier(self):
        label = bot.relative_to_first(self._s("2026-08-25T11:00"), self._s("2026-08-22T11:00"))
        assert label == "3 days earlier"


class TestCountdownWording:
    def test_it_reads_as_time_a_person_can_act_on(self):
        assert bot.human_delta(0) == "now"
        assert bot.human_delta(45 * 60) == "in about 45 minutes"
        assert bot.human_delta(5 * 3600) == "in about 5 hours"
        assert bot.human_delta(72 * 3600) == "in about 3 days"


class TestSessionAndArrangementAreSeparate:
    """Credentials belong to a person; the arrangement belongs to the chat. In a family
    group those are different, which is the whole reason the bot is worth having."""

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
        assert bot.stale(ar, f"confirm:{first}:0")
        assert not bot.stale(ar, f"confirm:{ar.query_id}:0")


class TestSessionExpiryDoesNotCostTheFamilyTheirWork:
    """The API signs tokens with a key that changes when it restarts, so a session can
    die in the middle of an intake that the family took ten minutes to type. Losing the
    token must never mean losing the arrangement."""

    class _Resp:
        def __init__(self, status_code):
            self.status_code = status_code

        def json(self):
            return {}

    def _signed_in(self):
        se = bot.Session()
        se.token = "t"
        se.expires_at = time.time() + 3600
        se.display_name = "Anna Kowalska"
        return se

    def _gathered(self):
        ar = bot.Arrangement()
        ar.intent = "my father died yesterday"
        ar.facts = {
            "service": "chapel service",
            "death": "the death was yesterday",
            "rite": "orthodox",
            "certificate": "we have the death certificate",
            "mourners": "for 40 mourners",
        }
        ar.mourners = 40
        return ar

    def test_a_rejected_token_is_dropped_but_the_arrangement_survives(self, monkeypatch):
        se, ar = self._signed_in(), self._gathered()
        sent = []

        async def fake_suggest(*args, **kwargs):
            return self._Resp(401)

        async def send(text, **kwargs):
            sent.append(text)

        monkeypatch.setattr(bot, "api_suggest", fake_suggest)
        asyncio.run(bot.propose(None, 1, ar, se, send))

        # Dropped, so the next message asks for a sign-in instead of 401-ing again.
        assert not bot.token_valid(se)
        assert "/login" in sent[0] and "nothing you have told me is lost" in sent[0].lower()
        assert ar.intent == "my father died yesterday"
        assert ar.facts["rite"] == "orthodox"

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
        assert not any("in your own words" in r for r in update.replies)

    def test_a_fresh_sign_in_with_nothing_pending_still_invites_the_account(self, monkeypatch):
        se, ar = bot.Session(), bot.Arrangement()
        se.auth_step, se.pending_username = "password", "anna"

        async def fake_login(session, username, password):
            session.token, session.expires_at = "fresh", time.time() + 3600
            session.display_name = "Anna Kowalska"
            return True

        monkeypatch.setattr(bot, "api_login", fake_login)
        update = _FakeUpdate()
        asyncio.run(bot.handle_auth(update, None, se, ar, "hunter2"))

        assert "in your own words" in update.replies[0]

    def test_a_confirmation_refused_on_auth_keeps_the_held_slot_on_screen(self, monkeypatch):
        se, ar = self._signed_in(), self._gathered()
        ar.suggestions = [{
            "resourceId": 1, "resourceName": "St. Barbara Chapel",
            "facilityName": "Dom Pogrzebowy Powazki",
            "start": "2026-08-22T11:00", "end": "2026-08-22T12:00", "price": "1215.00 PLN",
        }]
        ar.window = {"earliest": "2026-08-21", "latest": "2026-08-24"}

        async def fake_book(*args, **kwargs):
            return self._Resp(401)

        monkeypatch.setattr(bot, "api_book", fake_book)
        bot.sessions.clear()
        bot.sessions[7] = se
        bot.arrangements.clear()
        bot.arrangements[9] = ar
        update = _FakeUpdate(callback_data=f"confirm:{ar.query_id}:0", user_id=7, chat_id=9)
        asyncio.run(bot.cb_confirm(update, None))

        shown, markup = update.edits[-1]
        assert "Nothing was confirmed" in shown
        assert "St. Barbara Chapel" in shown  # the hold is still in front of them
        buttons = [b.callback_data for row in markup.inline_keyboard for b in row]
        assert f"confirm:{ar.query_id}:0" in buttons  # one tap after /login
        assert not bot.token_valid(se)


class TestMyArrangements:
    """What has been confirmed belongs to the account, not to the chat, so the command
    answers from the server rather than from whatever this chat happens to remember."""

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

    def test_it_lists_each_arrangement_with_the_reference_to_quote(self, monkeypatch):
        async def fake(session):
            return self._Resp(200, [{
                "reservationId": 12, "resourceName": "St. Barbara Chapel",
                "facilityName": "Dom Pogrzebowy Powazki", "start": "2026-08-22T11:00",
                "end": "2026-08-22T12:00", "status": "CONFIRMED", "totalAmount": "1215.00 PLN",
            }])

        monkeypatch.setattr(bot, "api_arrangements", fake)
        bot.sessions.clear()
        bot.sessions[1] = self._signed_in()
        update = _FakeUpdate()
        asyncio.run(bot.cmd_my_arrangements(update, None))

        shown = update.replies[0]
        assert "St. Barbara Chapel" in shown
        assert "Reference 12" in shown
        assert "Confirmed" in shown and "1215.00 PLN" in shown

    def test_nothing_arranged_yet_invites_the_account_instead_of_an_empty_list(self, monkeypatch):
        async def fake(session):
            return self._Resp(200, [])

        monkeypatch.setattr(bot, "api_arrangements", fake)
        bot.sessions.clear()
        bot.sessions[1] = self._signed_in()
        update = _FakeUpdate()
        asyncio.run(bot.cmd_my_arrangements(update, None))

        assert "nothing arranged yet" in update.replies[0].lower()

    def test_a_rejected_token_is_dropped_here_too(self, monkeypatch):
        async def fake(session):
            return self._Resp(401)

        monkeypatch.setattr(bot, "api_arrangements", fake)
        bot.sessions.clear()
        se = self._signed_in()
        bot.sessions[1] = se
        update = _FakeUpdate()
        asyncio.run(bot.cmd_my_arrangements(update, None))

        assert not bot.token_valid(se)
        assert "/login" in update.replies[0]

    def test_signed_out_is_asked_to_sign_in_rather_than_shown_nothing(self):
        bot.sessions.clear()
        update = _FakeUpdate()
        asyncio.run(bot.cmd_my_arrangements(update, None))
        assert "/login" in update.replies[0]
