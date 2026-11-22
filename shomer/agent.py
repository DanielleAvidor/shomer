"""When should the background agent speak up? One policy, shared in spirit by the Android app (AgentCheck.kt) and the web demo.

An agent that cries wolf gets switched off, so it is quiet by default:

* SMS (messages from strangers): "don't click" always speaks; "can't verify" speaks when the message names a known
  organization AND has a link or pushes, or when it has a link to an unknown site AND pushes (threat, secrets, a prize,
  a request to install, or a payment), even if no organization is named.
* Chat apps (strict): people share links and mention banks all the time, so it speaks only on a high-confidence
  "don't click" that also pushes (urgency, threat, secrets, money) or links to a lookalike of a known organization.
* "Matches official" never speaks (we do not vouch for a message), and a message with nothing to check never speaks.
"""
from __future__ import annotations

from .engine import Result, Verdict


PUSHY = {"threat", "credentials", "prize", "staff", "install", "payment"}


def should_alert(r: Result, strict: bool = False) -> bool:
    if not r.applicable:
        return False
    if r.verdict is Verdict.MATCHES_OFFICIAL:
        return False
    if r.verdict is Verdict.CANT_VERIFY:
        if strict:
            return False
        named = bool(r.claimed_orgs) and (bool(r.links) or bool(r.signal_ids))
        unknown_sender_pushes = bool(r.links) and bool(set(r.signal_ids) & PUSHY)
        return named or unknown_sender_pushes
    if not strict:
        return True
    return r.certain and (bool(r.signal_ids) or any(l.verdict is Verdict.DONT_CLICK and l.matched_org for l in r.links))
