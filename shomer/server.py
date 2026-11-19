"""Tiny local server for the demo: serves the page and a JSON API. Standard library only.

Privacy by design: the message text is analysed in memory and is NOT stored. Only the verdict, the
detected organization and the link host are kept, so the family member sees what happened without
reading the private message.

Run:  python -m shomer.server   (then open http://localhost:8000)
"""
from __future__ import annotations

import json
import sqlite3
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

from .engine import Engine

STATIC = Path(__file__).parent / "static"
ENGINE = Engine()


def make_db(path: str = ":memory:") -> sqlite3.Connection:
    db = sqlite3.connect(path, check_same_thread=False)
    db.execute(
        "CREATE TABLE IF NOT EXISTS events (id INTEGER PRIMARY KEY AUTOINCREMENT, ts REAL, verdict TEXT, "
        "org TEXT, host TEXT, tapped INTEGER DEFAULT 0)"
    )
    return db


DB = make_db()


def record(result) -> int:
    host = result.links[0].host if result.links else ""
    org = result.claimed_orgs[0] if result.claimed_orgs else ""
    cur = DB.execute("INSERT INTO events (ts, verdict, org, host) VALUES (?,?,?,?)", (time.time(), result.verdict.value, org, host))
    DB.commit()
    return cur.lastrowid


def guardian_summary() -> dict:
    rows = DB.execute("SELECT id, ts, verdict, org, host, tapped FROM events ORDER BY id DESC LIMIT 50").fetchall()
    events = [
        dict(id=r[0], ts=r[1], verdict=r[2], org=ENGINE.by_id[r[3]].name_he if r[3] in ENGINE.by_id else "", host=r[4], tapped=bool(r[5]))
        for r in rows
    ]
    counts = {"dont_click": 0, "cant_verify": 0, "matches_official": 0}
    for e in events:
        counts[e["verdict"]] += 1
    # Immediate alerts: only when the user tapped a link that was flagged risky.
    alerts = [e for e in events if e["tapped"] and e["verdict"] != "matches_official"]
    return {"counts": counts, "alerts": alerts, "recent": events[:10]}


class Handler(BaseHTTPRequestHandler):
    def _json(self, obj, code=200):
        body = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _body(self) -> dict:
        n = int(self.headers.get("Content-Length") or 0)
        if n > 20_000:
            return {}
        try:
            return json.loads(self.rfile.read(n) or b"{}")
        except json.JSONDecodeError:
            return {}

    def do_GET(self):
        if self.path in ("/", "/index.html"):
            body = (STATIC / "index.html").read_bytes()
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        elif self.path == "/api/guardian":
            self._json(guardian_summary())
        else:
            self._json({"error": "not found"}, 404)

    def do_POST(self):
        data = self._body()
        if self.path == "/api/check":
            text = str(data.get("text", ""))[:5000]
            result = ENGINE.check(text)
            out = result.to_dict()
            out["event_id"] = record(result) if result.applicable else None
            self._json(out)
        elif self.path == "/api/report":
            # Used by the phone: metadata only (verdict, org id, link host). Never the message text.
            verdict = data.get("verdict")
            if verdict not in ("dont_click", "cant_verify", "matches_official"):
                return self._json({"error": "bad verdict"}, 400)
            # Android cannot observe taps inside other apps, so a high-confidence 'dont_click' alerts immediately.
            cur = DB.execute(
                "INSERT INTO events (ts, verdict, org, host, tapped) VALUES (?,?,?,?,?)",
                (time.time(), verdict, str(data.get("org", ""))[:40], str(data.get("host", ""))[:120], int(verdict == "dont_click")),
            )
            DB.commit()
            self._json({"event_id": cur.lastrowid})
        elif self.path == "/api/tapped":
            DB.execute("UPDATE events SET tapped=1 WHERE id=?", (int(data.get("event_id", 0)),))
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
