"""Run the engine over eval/corpus.jsonl and print an honest summary.

The corpus is small and written by the author (modeled on published examples), so these numbers are a
regression check, NOT an accuracy estimate. Real accuracy needs real messages collected with consent.
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from shomer.engine import Engine  # noqa: E402

rows = [json.loads(l) for l in (Path(__file__).parent / "corpus.jsonl").read_text(encoding="utf-8").splitlines() if l.strip()]
E = Engine()

scams = [r for r in rows if r["kind"] == "scam"]
legit = [r for r in rows if r["kind"] == "legit"]
res = {r["id"]: E.check(r["text"]) for r in rows}

caught = [r for r in scams if res[r["id"]].verdict.value != "matches_official"]
hard = [r for r in scams if res[r["id"]].verdict.value == "dont_click"]
false_ok = [r for r in scams if res[r["id"]].verdict.value == "matches_official"]
legit_app = [r for r in legit if res[r["id"]].applicable]
false_alarm = [r for r in legit_app if res[r["id"]].verdict.value == "dont_click"]
friction = [r for r in legit_app if res[r["id"]].verdict.value == "cant_verify"]
confirmed = [r for r in legit_app if res[r["id"]].verdict.value == "matches_official"]
mismatch = [r for r in rows if r["expected"] != "not_applicable" and res[r["id"]].verdict.value != r["expected"]]

lines = [
    "# Evaluation summary (synthetic corpus, regression check only)",
    "",
    f"- Scam messages: {len(scams)}; legitimate messages: {len(legit)} ({len(legit_app)} contain a link or a named organization)",
    f"- Scams never reported as 'matches official' (user is not reassured): {len(caught)}/{len(scams)}",
    f"- Scams hard-blocked with 'Don't click': {len(hard)}/{len(scams)}",
    f"- Scams wrongly reported as 'matches official' (worst failure): {len(false_ok)}",
    f"- Legitimate messages wrongly told 'Don't click' (false alarms): {len(false_alarm)}/{len(legit_app)}",
    f"- Legitimate messages told 'can't verify' (friction): {len(friction)}/{len(legit_app)}",
    f"- Legitimate messages confirmed as matching the official site: {len(confirmed)}/{len(legit_app)}",
    f"- Cases where verdict differs from expectation: {len(mismatch)}",
    "",
    "## Scams that are only 'can't verify' (known gaps)",
]
for r in scams:
    if res[r["id"]].verdict.value == "cant_verify":
        lines.append(f"- {r['id']}: {r['note']}")
out = "\n".join(lines)
print(out)
(Path(__file__).parent / "results.md").write_text(out + "\n", encoding="utf-8")
