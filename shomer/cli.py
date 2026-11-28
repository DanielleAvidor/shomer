"""Headless runner: check messages from the command line, no server and no phone.

    python -m shomer.cli --text "..."
    python -m shomer.cli --input samples/messages.json --output out.json
    python -m shomer.cli --input samples/messages.json --directory my_directory.json

Output is JSON, one object per message. There is deliberately no "is_scam" flag and no risk score: Shomer
never says a message is safe, so the verdict is one of dont_click / matches_official / cant_verify.
`alert` is what the background agent would do (speak up or stay quiet) under the SMS policy.
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from .agent import should_alert
from .engine import DATA, Engine


def check_one(engine: Engine, text: str, strict: bool = False) -> dict:
    r = engine.check(text)
    return {
        "verdict": r.verdict.value,
        "applicable": r.applicable,
        "alert": should_alert(r, strict=strict),
        "certain": r.certain,
        "signals": list(r.signal_ids),
        "claimed_orgs": list(r.claimed_orgs),
        "links": [{"url": l.url, "verdict": l.verdict.value, "reasons": list(l.reasons)} for l in r.links],
        "reasons_he": list(r.reasons_he),
    }


def load_messages(path: Path) -> list:
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, list):
        raise ValueError("the input file must be a JSON list")
    out = []
    for i, item in enumerate(data):
        if isinstance(item, str):
            out.append({"id": str(i + 1), "text": item})
        elif isinstance(item, dict) and isinstance(item.get("text"), str):
            out.append({"id": str(item.get("id", i + 1)), "text": item["text"]})
        else:
            raise ValueError(f"item {i + 1}: expected a string or an object with a 'text' field")
    return out


def main(argv: list | None = None) -> int:
    p = argparse.ArgumentParser(prog="shomer", description="Check messages against official organization domains.")
    src = p.add_mutually_exclusive_group(required=True)
    src.add_argument("--text", help="one message to check")
    src.add_argument("--input", type=Path, help="JSON file: a list of strings or of {id, text} objects")
    p.add_argument("--output", type=Path, help="write the JSON here instead of printing it")
    p.add_argument("--directory", type=Path, default=DATA, help="custom directory.json (organizations, signals, shorteners)")
    p.add_argument("--strict", action="store_true", help="use the chat-app alert policy instead of the SMS one")
    a = p.parse_args(argv)
    try:
        engine = Engine(a.directory)
        items = [{"id": "1", "text": a.text}] if a.text is not None else load_messages(a.input)
    except (OSError, ValueError, KeyError, TypeError) as e:  # JSONDecodeError is a ValueError
        print(f"error: {e}", file=sys.stderr)
        return 2
    results = [{"id": m["id"], **check_one(engine, m["text"], a.strict)} for m in items]
    payload = results[0] if a.text is not None else results
    out = json.dumps(payload, ensure_ascii=False, indent=2)
    if a.output:
        a.output.write_text(out + "\n", encoding="utf-8")
    else:
        print(out)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
