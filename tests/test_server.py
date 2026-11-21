import json
import threading
import urllib.request
from http.server import ThreadingHTTPServer

import pytest

from shomer import server


@pytest.fixture(scope="module")
def base():
    srv = ThreadingHTTPServer(("127.0.0.1", 0), server.Handler)
    t = threading.Thread(target=srv.serve_forever, daemon=True)
    t.start()
    yield f"http://127.0.0.1:{srv.server_address[1]}"
    srv.shutdown()


def post(base, path, obj):
    req = urllib.request.Request(base + path, data=json.dumps(obj).encode(), method="POST")
    try:
        with urllib.request.urlopen(req) as r:
            return r.status, json.loads(r.read())
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read())


def test_check_does_not_store_message_text(base):
    secret = "דואר ישראל https://israelpost.paliet.top/il סיסמה-סודית-12345"
    post(base, "/api/settings", {"name": "בדיקה", "relation": "סבתא", "consented": True})
    status, out = post(base, "/api/check", {"text": secret})
    assert status == 200 and out["verdict"] == "dont_click" and out["event_id"]
    cols = [c[1] for c in server.DB.execute("PRAGMA table_info(events)")]
    assert "text" not in cols
    dump = json.dumps(server.DB.execute("SELECT * FROM events").fetchall(), ensure_ascii=False)
    assert "סודית" not in dump


def test_not_applicable_message_creates_no_event(base):
    before = server.DB.execute("SELECT COUNT(*) FROM events").fetchone()[0]
    _, out = post(base, "/api/check", {"text": "נתראה מחר בשש"})
    assert out["applicable"] is False and out["event_id"] is None
    assert server.DB.execute("SELECT COUNT(*) FROM events").fetchone()[0] == before


def test_report_from_phone_alerts_only_for_dont_click(base):
    post(base, "/api/report", {"verdict": "dont_click", "org": "israel_post", "host": "evil.top"})
    post(base, "/api/report", {"verdict": "cant_verify", "org": "", "host": "x.com"})
    with urllib.request.urlopen(base + "/api/guardian") as r:
        g = json.loads(r.read())
    assert any(a["host"] == "evil.top" for a in g["alerts"])
    assert not any(a["host"] == "x.com" for a in g["alerts"])


def test_report_rejects_bad_verdict(base):
    status, _ = post(base, "/api/report", {"verdict": "safe"})
    assert status == 400


def test_handled_event_leaves_the_alert_list(base):
    _, r = post(base, "/api/report", {"verdict": "dont_click", "org": "", "host": "handled-test.top"})
    with urllib.request.urlopen(base + "/api/guardian") as resp:
        assert any(a["host"] == "handled-test.top" for a in json.loads(resp.read())["alerts"])
    post(base, "/api/handled", {"event_id": r["event_id"]})
    with urllib.request.urlopen(base + "/api/guardian") as resp:
        assert not any(a["host"] == "handled-test.top" for a in json.loads(resp.read())["alerts"])


def test_settings_roundtrip_and_font_served(base):
    _, s = post(base, "/api/settings", {"name": "סבתא", "relation": "סבתא", "consented": True})
    assert s == {"name": "סבתא", "relation": "סבתא", "consented": True, "external_ai": False}
    with urllib.request.urlopen(base + "/static/fonts/opensans.woff") as r:
        assert r.status == 200 and r.headers["Content-Type"] == "font/woff"
    with pytest.raises(urllib.error.HTTPError):
        urllib.request.urlopen(base + "/static/fonts/../server.py")


def test_no_event_is_recorded_before_consent(base):
    server.DB.execute("DELETE FROM settings WHERE k='consented'"); server.DB.commit()
    before = server.DB.execute("SELECT COUNT(*) FROM events").fetchone()[0]
    status, out = post(base, "/api/check", {"text": "דואר ישראל https://israelpost.paliet.top/il"})
    assert status == 200 and out["verdict"] == "dont_click" and out["event_id"] is None
    assert server.DB.execute("SELECT COUNT(*) FROM events").fetchone()[0] == before
