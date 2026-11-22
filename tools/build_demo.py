"""Builds docs/index.html: the demo page as a single static site (no server), for GitHub Pages.

The page is the same file the local server serves (shomer/static/index.html). Here the /api calls are answered in the
browser from results the real Python engine produced for the example messages, so what you see is real engine output,
but the static page can only check those examples. Run:  python tools/build_demo.py
"""
import json, re, shutil, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))
from shomer.agent import should_alert
from shomer.engine import Engine

html = (ROOT / "shomer/static/index.html").read_text(encoding="utf-8")
texts = re.findall(r'\bt:"((?:[^"\\]|\\.)*)"', html)                       # the lock-screen simulation
block = html[html.index("const EX = ["):].split("];")[0]
texts += [json.loads(s) for s in re.findall(r'^\s*("(?:[^"\\]|\\.)*")', block, re.M)]  # the "try an example" chips
texts = [json.loads('"' + t + '"') if "\\" in t and not t.startswith('"') else t for t in texts]

eng = Engine()
table = {}
for t in dict.fromkeys(texts):
    r = eng.check(t)
    d = r.to_dict()
    d["alert"] = should_alert(r, strict=False)
    d["alert_strict"] = should_alert(r, strict=True)
    d["event_id"] = None
    table[t] = d

SHIM = """<script>
/* Static demo: answers the page's /api calls in the browser from results the real engine produced for the examples. */
(() => {
  const T = %s;
  const json = (o, s = 200) => new Response(JSON.stringify(o), {status: s, headers: {"Content-Type": "application/json"}});
  const unknown = {applicable: false, verdict: "cant_verify", headline_he: "הדמו הזה בודק רק את ההודעות לדוגמה",
    advice_he: "כדי לבדוק כל הודעה, מריצים את הדמו המלא על המחשב: python -m shomer.server. ההוראות בעמוד הפרויקט בגיטהאב.",
    reasons_he: [], links: [], claimed_orgs: [], signals_he: [], signal_ids: [], steps_he: [], certain: false};
  window.fetch = async (u, o = {}) => {
    const body = o.body ? JSON.parse(o.body) : {};
    if (u === "/api/ingest" || u === "/api/check") {
      const r = T[body.text];
      if (!r) return json(u === "/api/check" ? unknown : {error: true}, u === "/api/check" ? 200 : 500);
      const out = {...r}; out.alert = body.channel === "sms" ? r.alert : r.alert_strict; return json(out);
    }
    if (u === "/api/guardian") return json({counts: {dont_click: 0, cant_verify: 0, matches_official: 0}, alerts: [], recent: [], settings: {name: "", relation: "", consented: false, external_ai: false}});
    if (u === "/api/settings") return json({name: body.name || "", relation: body.relation || "", consented: !!body.consented, external_ai: false});
    return json({ok: true});
  };
})();
</script>
""" % json.dumps(table, ensure_ascii=False)

out = html.replace("/static/fonts/", "fonts/")
out = out.replace("<script>", SHIM + "<script>", 1)
out = out.replace("<head>", '<head>\n<meta name="robots" content="noindex">', 1) if "robots" not in out else out
dest = ROOT / "docs"
(dest / "fonts").mkdir(exist_ok=True)
for f in (ROOT / "shomer/static/fonts").glob("*.woff"):
    shutil.copy(f, dest / "fonts" / f.name)
(dest / "index.html").write_text(out, encoding="utf-8")
(dest / ".nojekyll").write_text("")
print(f"docs/index.html written, {len(table)} messages precomputed, {sum(d['alert'] for d in table.values())} alert on SMS")
