"""Extension point for an optional AI second opinion (not implemented yet).

The rule engine in engine.py stays the safety floor. An Advisor may read the message and
the engine's result and return an Advice. apply_advice() then merges it with one hard rule:
**an advisor can only make a result more cautious, never less.** It cannot turn "don't click"
or "can't verify" into "matches official", and nothing here ever says a message is safe.

To add a real advisor later (for example a language-model call), implement `advise()` and
pass the object to server.set_advisor(). Until then the NullAdvisor is used and nothing
leaves the machine. The server only calls an advisor when the protected person has
explicitly allowed it in settings (`external_ai`), because it would send the message text
to an outside service.
"""
from __future__ import annotations

from dataclasses import dataclass, replace
from typing import Optional, Protocol

from .engine import Result, Verdict

# Higher number = more cautious.
_SEVERITY = {Verdict.MATCHES_OFFICIAL: 0, Verdict.CANT_VERIFY: 1, Verdict.DONT_CLICK: 2}

_ESCALATED = {
    Verdict.CANT_VERIFY: ("לא הצלחנו לוודא שההודעה אמיתית",
                          "משהו בהודעה מעלה חשד. אל תלחצו על קישורים ואל תמסרו פרטים. פנו בעצמכם דרך האתר, האפליקציה או הטלפון הרשמיים."),
    Verdict.DONT_CLICK: ("ההודעה נראית כמו הונאה",
                         "אל תלחצו על קישורים, אל תמסרו פרטים ואל תעבירו כסף. אם זה נוגע לכם, פנו בעצמכם דרך האתר, האפליקציה או הטלפון הרשמיים."),
}


@dataclass
class Advice:
    risk: str = "none"  # "none" | "suspicious" | "dangerous". "none" never lowers anything.
    explanation_he: str = ""  # one plain sentence, shown as an extra reason
    question_he: str = ""  # an optional context question, for example "ציפית לחבילה מדואר ישראל?"


class Advisor(Protocol):
    def advise(self, text: str, result: Result) -> Optional[Advice]: ...


class NullAdvisor:
    """The default: no outside help, nothing is sent anywhere."""

    def advise(self, text: str, result: Result) -> Optional[Advice]:
        return None


def apply_advice(result: Result, advice: Optional[Advice]) -> Result:
    """Merge an advisor's opinion into a result. Escalate-only."""
    if advice is None:
        return result
    target = {"suspicious": Verdict.CANT_VERIFY, "dangerous": Verdict.DONT_CLICK}.get(advice.risk)
    out = replace(result, reasons_he=list(result.reasons_he))
    if target is not None and _SEVERITY[target] > _SEVERITY[result.verdict]:
        out.verdict = target
        out.headline_he, out.advice_he = _ESCALATED[target]
        out.applicable = True
        out.escalated_by_advisor = True
    note = advice.explanation_he.strip()[:200]
    if note:
        out.reasons_he.append(note)
    out.context_question_he = advice.question_he.strip()[:120]
    return out
