"""The advisor extension point: escalate-only, opt-in, and safe on failure."""
import json
import threading
import urllib.request

import pytest

from shomer import server
from shomer.advisor import Advice, apply_advice
from shomer.engine import Engine, Verdict

E = Engine()
OFFICIAL = "דואר ישראל: החבילה הגיעה https://israelpost.co.il/track"
LOOKALIKE = "דואר ישראל: אנא אשר כתובת https://israelpost.paliet.top/il"


def test_advisor_can_escalate_a_match():
    r = E.check(OFFICIAL)
    assert r.verdict is Verdict.MATCHES_OFFICIAL
    out = apply_advice(r, Advice(risk="suspicious", explanation_he="הניסוח חשוד"))
    assert out.verdict is Verdict.CANT_VERIFY and out.escalated_by_advisor
    assert "הניסוח חשוד" in out.reasons_he


@pytest.mark.parametrize("text", [OFFICIAL, LOOKALIKE, "נתראה מחר בשש אצל סבתא"])
def test_advisor_never_lowers_a_verdict(text):
    r = E.check(text)
    for risk in ("none", "", "safe", "fine", "suspicious"):
        out = apply_advice(r, Advice(risk=risk))
        order = {Verdict.MATCHES_OFFICIAL: 0, Verdict.CANT_VERIFY: 1, Verdict.DONT_CLICK: 2}
        assert order[out.verdict] >= order[r.verdict]


def test_dont_click_stays_dont_click_even_if_advisor_says_none():
    r = E.check(LOOKALIKE)
    assert apply_advice(r, Advice(risk="none")).verdict is Verdict.DONT_CLICK


def test_no_advice_changes_nothing():
    r = E.check(OFFICIAL)
    assert apply_advice(r, None) is r


def test_context_question_is_passed_through_and_truncated():
    out = apply_advice(E.check(OFFICIAL), Advice(question_he="ציפית לחבילה?" * 50))
    assert out.context_question_he and len(out.context_question_he) <= 120


class Fake:
    def __init__(self, advice=None, boom=False):
        self.advice, self.boom, self.calls = advice, boom, 0

    def advise(self, text, result):
        self.calls += 1
        if self.boom:
            raise RuntimeError("network down")
        return self.advice


@pytest.fixture()
def reset():
    yield
    server.set_advisor(server.NullAdvisor())
    server.DB.execute("DELETE FROM settings WHERE k='external_ai'")
    server.DB.commit()


def test_advisor_not_called_without_explicit_permission(reset):
    fake = Fake(Advice(risk="dangerous"))
    server.set_advisor(fake)
    out = server.second_opinion(OFFICIAL, E.check(OFFICIAL))
    assert fake.calls == 0 and out.verdict is Verdict.MATCHES_OFFICIAL


def test_advisor_called_when_allowed_and_failure_falls_back(reset):
    server.DB.execute("INSERT OR REPLACE INTO settings VALUES ('external_ai','1')")
    server.DB.commit()
    ok = Fake(Advice(risk="dangerous", explanation_he="נראה כמו הונאה"))
    server.set_advisor(ok)
    assert server.second_opinion(OFFICIAL, E.check(OFFICIAL)).verdict is Verdict.DONT_CLICK
    server.set_advisor(Fake(boom=True))
    assert server.second_opinion(OFFICIAL, E.check(OFFICIAL)).verdict is Verdict.MATCHES_OFFICIAL
