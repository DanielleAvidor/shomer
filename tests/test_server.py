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
    req = urllib.request.Request(base + path, data=json.dumps(obj).encode(), method="POST", headers={"Content-Type": "application/json"})
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


def test_family_alert_only_for_certain_scams(base):
    post(base, "/api/settings", {"name": "בדיקה", "relation": "סבתא", "consented": True})
    post(base, "/api/check", {"text": "דואר ישראל: אשר כתובת https://israelpost.paliet.top/il"})
    post(base, "/api/check", {"text": "קיבלת חבילה https://soft-case-only.example.xyz/a"})
    with urllib.request.urlopen(base + "/api/guardian") as resp:
        hosts = [a["host"] for a in json.loads(resp.read())["alerts"]]
    assert "israelpost.paliet.top" in hosts and "soft-case-only.example.xyz" not in hosts


def raw_post(base, path, body: bytes, content_type=None):
    headers = {"Content-Type": content_type} if content_type else {}
    req = urllib.request.Request(base + path, data=body, method="POST", headers=headers)
    try:
        with urllib.request.urlopen(req) as r:
            return r.status, json.loads(r.read())
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read())


def test_a_cross_site_simple_post_is_refused(base):
    status, _ = raw_post(base, "/api/settings", json.dumps({"consented": True, "external_ai": True}).encode(), "text/plain")
    assert status == 415
    status, _ = raw_post(base, "/api/settings", b'{"consented": true}', None)
    assert status == 415


def test_report_is_not_recorded_without_consent(base):
    post(base, "/api/settings", {"consented": False})
    before = server.DB.execute("SELECT COUNT(*) FROM events").fetchone()[0]
    status, out = post(base, "/api/report", {"verdict": "dont_click", "org": "israel_post", "host": "no-consent.top"})
    assert status == 200 and out["event_id"] is None
    assert server.DB.execute("SELECT COUNT(*) FROM events").fetchone()[0] == before
    post(base, "/api/settings", {"consented": True})


def test_report_fields_are_validated(base):
    post(base, "/api/settings", {"consented": True})
    _, out = post(base, "/api/report", {"verdict": "dont_click", "org": "not-a-real-org", "host": "050-1234567 call me"})
    row = server.DB.execute("SELECT org, host FROM events WHERE id=?", (out["event_id"],)).fetchone()
    assert row == ("", "")


def test_a_too_long_message_is_refused_not_silently_cut(base):
    status, out = post(base, "/api/check", {"text": "א" * 70_000})
    assert status == 413 and out["error"] == "too_long"


def test_a_long_message_is_checked_to_its_end(base):
    status, out = post(base, "/api/check", {"text": "שלום " * 1500 + "דואר ישראל שלמו https://israelpost-fee.top/pay"})
    assert status == 200 and out["verdict"] == "dont_click"


def test_malformed_requests_get_a_clean_error(base):
    assert raw_post(base, "/api/handled", b'{"event_id": "abc"}', "application/json")[0] == 400
    assert raw_post(base, "/api/check", b"not json", "application/json")[0] == 400
    assert raw_post(base, "/api/check", b"[1,2]", "application/json")[0] == 400


def _raw(base, path, headers, data=None, method="GET"):
    req = urllib.request.Request(base + path, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req) as r:
            return r.status
    except urllib.error.HTTPError as e:
        return e.code


def test_a_foreign_host_or_origin_is_refused(base):
    """DNS rebinding: a page on another site that points its name at 127.0.0.1 must not read or change anything."""
    assert _raw(base, "/api/guardian", {"Host": "evil.example"}) == 403
    assert _raw(base, "/api/settings", {"Host": "evil.example:8000", "Content-Type": "application/json"}, b"{}", "POST") == 403
    assert _raw(base, "/api/guardian", {"Origin": "http://evil.example"}) == 403
    assert _raw(base, "/api/guardian", {}) == 200


def test_a_client_that_stops_sending_does_not_hold_a_thread():
    assert server.Handler.timeout and server.Handler.timeout <= 30
