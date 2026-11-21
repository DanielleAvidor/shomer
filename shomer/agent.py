"""When should the background agent speak up? One policy, shared in spirit by the Android app (AgentCheck.kt) and the web demo.

An agent that cries wolf gets switched off, so it is quiet by default:

* SMS (messages from strangers): "don't click" always speaks; "can't verify" speaks only when the message names a known
  organization AND has a link or pushes.
* Chat apps (strict): people share links and mention banks all the time, so it speaks only on a high-confidence
  "don't click" that also pushes (urgency, threat, secrets, money) or links to a lookalike of a known organization.
* "Matches official" never speaks (we do not vouch for a message), and a message with nothing to check never speaks.
"""
from __future__ import annotations

from .engine import Result, Verdict


def should_alert(r: Result, strict: bool = False) -> bool:
    if not r.applicable:
        return False
    if r.verdict is Verdict.MATCHES_OFFICIAL:
        return False
    if r.verdict is Verdict.CANT_VERIFY:
        return (not strict) and bool(r.claimed_orgs) and (bool(r.links) or bool(r.signal_ids))
    if not strict:
        return True
    return r.certain and (bool(r.signal_ids) or any(l.verdict is Verdict.DONT_CLICK and l.matched_org for l in r.links))
