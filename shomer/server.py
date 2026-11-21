"""Tiny local server for the demo: serves the page and a JSON API. Standard library only.

Privacy by design: the message text is analysed in memory and is NOT stored. Only the verdict, the
detected organization and the link host are kept, so the family member sees what happened without
reading the private message.

Run:  python -m shomer.server   (then open http://localhost:8000)
"""
from __future__ import annotations

import json
import re
import sqlite3
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

from .advisor import Advisor, NullAdvisor, apply_advice
from .engine import Engine

STATIC = Path(__file__).parent / "static"
ENGINE = Engine()


def make_db(path: str = ":memory:") -> sqlite3.Connection:
    db = sqlite3.connect(path, check_same_thread=False)
    db.execute(
        "CREATE TABLE IF NOT EXISTS events (id INTEGER PRIMARY KEY AUTOINCREMENT, ts REAL, verdict TEXT, "
        "org TEXT, host TEXT, tapped INTEGER DEFAULT 0, handled INTEGER DEFAULT 0, certain INTEGER DEFAULT 0)"
    )
    db.execute("CREATE TABLE IF NOT EXISTS settings (k TEXT PRIMARY KEY, v TEXT)")
    return db


DB = make_db()


def record(result) -> int:
    host = result.links[0].host if result.links else ""
    org = result.claimed_orgs[0] if result.claimed_orgs else ""
    cur = DB.execute("INSERT INTO events (ts, verdict, org, host, certain) VALUES (?,?,?,?,?)",
                     (time.time(), result.verdict.value, org, host, int(result.certain)))
    DB.commit()
    return cur.lastrowid


def get_settings() -> dict:
    d = dict(DB.execute("SELECT k, v FROM settings").fetchall())
    return {"name": d.get("name", ""), "relation": d.get("relation", ""), "consented": d.get("consented") == "1",
            "external_ai": d.get("external_ai") == "1"}


ADVISOR: Advisor = NullAdvisor()


def set_advisor(advisor: Advisor) -> None:
    """Plug in an optional second opinion (see advisor.py). The default does nothing."""
    global ADVISOR
    ADVISOR = advisor


def second_opinion(text: str, result):
    """Ask the advisor only if the protected person allowed it. Any failure falls back to the rule engine."""
    if isinstance(ADVISOR, NullAdvisor) or not get_settings()["external_ai"]:
        return result
    try:
        return apply_advice(result, ADVISOR.advise(text, result))
    except Exception:
        return result


def guardian_summary() -> dict:
    rows = DB.execute("SELECT id, ts, verdict, org, host, tapped, handled, certain FROM events ORDER BY id DESC LIMIT 50").fetchall()
    events = [
        dict(id=r[0], ts=r[1], verdict=r[2], org=ENGINE.by_id[r[3]].name_he if r[3] in ENGINE.by_id else "", host=r[4],
             tapped=bool(r[5]), handled=bool(r[6]), certain=bool(r[7]))
        for r in rows
    ]
    counts = {"dont_click": 0, "cant_verify": 0, "matches_official": 0}
    for e in events:
        counts[e["verdict"]] += 1
    # Immediate alert for every high-confidence "don't click" that nobody has handled yet
    # (Android cannot see taps in other apps).
    # Alert the family only for high-confidence scams (or a confirmed tap), and only what the protected person agreed to.
    alerts = [e for e in events if ((e["verdict"] == "dont_click" and e["certain"]) or e["tapped"]) and not e["handled"]]
    return {"counts": counts, "alerts": alerts, "recent": events[:12], "settings": get_settings()}


class Handler(BaseHTTPRequestHandler):
    def _json(self, obj, code=200):
        body = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    MAX_BODY = 60_000  # bytes; a message longer than this is refused, never silently cut

    def _body(self):
        """Returns the parsed JSON object, or None if the request is refused (an error response was already sent)."""
        # A page on another site can send a "simple" cross-origin POST (text/plain) without a preflight. Requiring a JSON
        # content type forces a preflight, which this server never grants, so other sites cannot change settings or alerts.
        if not (self.headers.get("Content-Type") or "").lower().startswith("application/json"):
            self._json({"error": "content type must be application/json"}, 415)
            return None
        try:
            n = int(self.headers.get("Content-Length") or 0)
        except ValueError:
            n = -1
        if n < 0:
            self._json({"error": "bad length"}, 400)
            return None
        if n > self.MAX_BODY:
            self._json({"error": "too_long"}, 413)
            return None
        try:
            data = json.loads(self.rfile.read(n) or b"{}")
        except (json.JSONDecodeError, UnicodeDecodeError):
            self._json({"error": "bad json"}, 400)
            return None
        if not isinstance(data, dict):
            self._json({"error": "bad json"}, 400)
            return None
        return data

    @staticmethod
    def _int(v):
        try:
            return int(v)
        except (TypeError, ValueError):
            return None

    def do_GET(self):
        if self.path in ("/", "/index.html"):
            body = (STATIC / "index.html").read_bytes()
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        elif self.path.startswith("/static/fonts/"):
            f = (STATIC / "fonts" / Path(self.path).name)
            if f.suffix == ".woff" and f.is_file():
                body = f.read_bytes()
                self.send_response(200)
                self.send_header("Content-Type", "font/woff")
                self.send_header("Cache-Control", "public, max-age=86400")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)
            else:
                self._json({"error": "not found"}, 404)
        elif self.path == "/api/settings":
            self._json(get_settings())
        elif self.path == "/api/guardian":
            self._json(guardian_summary())
        else:
            self._json({"error": "not found"}, 404)

    def do_POST(self):
        try:
            self._post()
        except Exception:  # never print a traceback with file paths, never leave the client hanging
            try:
                self._json({"error": "server error"}, 500)
            except Exception:
                pass

    def _post(self):
        data = self._body()
        if data is None:
            return
        if self.path == "/api/check":
            text = str(data.get("text", ""))
            result = ENGINE.check(text)
            result = second_opinion(text, result)
            out = result.to_dict()
            out["event_id"] = record(result) if (result.applicable and get_settings()["consented"]) else None
            self._json(out)
        elif self.path == "/api/report":
            # Used by the phone: metadata only (verdict, org id, link host). Never the message text.
            verdict = data.get("verdict")
            if verdict not in ("dont_click", "cant_verify", "matches_official"):
                return self._json({"error": "bad verdict"}, 400)
            # The same rule as the web check: nothing is recorded until the protected person agreed on screen.
            if not get_settings()["consented"]:
                return self._json({"event_id": None, "recorded": False})
            org = str(data.get("org", ""))
            org = org if org in ENGINE.by_id else ""
            host = str(data.get("host", ""))[:120].lower()
            host = host if re.fullmatch(r"[a-z0-9.\-]*", host) else ""
            # Android cannot observe taps inside other apps, so a high-confidence 'dont_click' alerts immediately.
            cur = DB.execute(
                "INSERT INTO events (ts, verdict, org, host, tapped, certain) VALUES (?,?,?,?,?,?)",
                (time.time(), verdict, org, host,
                 int(verdict == "dont_click"), int(bool(data.get("certain", verdict == "dont_click")))),
            )
            DB.commit()
            self._json({"event_id": cur.lastrowid})
        elif self.path == "/api/handled":
            eid = self._int(data.get("event_id"))
            if eid is None:
                return self._json({"error": "bad event_id"}, 400)
            DB.execute("UPDATE events SET handled=1 WHERE id=?", (eid,))
            DB.commit()
            self._json({"ok": True})
        elif self.path == "/api/settings":
            for k in ("name", "relation"):
                if k in data:
                    DB.execute("INSERT OR REPLACE INTO settings VALUES (?,?)", (k, str(data[k])[:40]))
            if "external_ai" in data:
                DB.execute("INSERT OR REPLACE INTO settings VALUES ('external_ai', ?)", ("1" if data["external_ai"] else "0",))
            if "consented" in data:
                DB.execute("INSERT OR REPLACE INTO settings VALUES ('consented', ?)", ("1" if data["consented"] else "0",))
            DB.commit()
            self._json(get_settings())
        elif self.path == "/api/tapped":
            eid = self._int(data.get("event_id"))
            if eid is None:
                return self._json({"error": "bad event_id"}, 400)
            DB.execute("UPDATE events SET tapped=1 WHERE id=?", (eid,))
            DB.commit()
            self._json({"ok": True})
        else:
            self._json({"error": "not found"}, 404)

    def log_message(self, *a):  # keep the console quiet
        pass


def main(port: int = 8000):
    srv = ThreadingHTTPServer(("127.0.0.1", port), Handler)
    print(f"Shomer demo on http://localhost:{port}")
    srv.serve_forever()


if __name__ == "__main__":
    main()
