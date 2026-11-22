"""Writes in.jsonl (messages) and py.jsonl (what the Python engine says) into the work directory."""
import json, random, sys
root, work = sys.argv[1], sys.argv[2]
sys.path.insert(0, root)
from shomer.engine import Engine
e = Engine()
texts = [json.loads(l)["text"] for l in open(f"{root}/eval/corpus.jsonl", encoding="utf-8") if l.strip()]
texts += ["בנק לאומי: קוד האימות שלך הוא 482913. אל תמסור אותו לאף אחד.", "דואר ישראל: שלמו hxxps://israelpost[.]paliet[.]top/il", "שלחתי לך את report.pdf",
          "evil.test\\.israelpost.co.il", "היי סבתא, זה אני, החלפתי מספר. אפשר להעביר דחוף 2,500 שקל?", "בנק הפועלים: https://poalim.co.il/x",
          "בנק לאומי: https://leumicard.co.il/x", "Ваш банковский счёт будет заблокирован. Срочно позвоните", "ביטוח לאומי: https://btl.gov.il/a",
          "ראית? https://www.ynet.co.il/news/a", "https://israelpost.co.il/track", "+972 52 123 4567 בנק הפועלים התקשרו", "כביש 6: https://kvish6.co.il.pay-now.top/bills"]
# Adversarial cases from the review (docs/reviews): redirects, path tricks, spaced and fullwidth letters, odd hosts, IPv6, long text.
texts += ["בנק לאומי https://leumi.co.il/redirect?url=https://evil.test", "משטרת ישראל https://www.gov.il/he/service/enforcement-reports/../../x",
          "משטרת ישראל https://www.gov.il/he/service/enforcement-reports%2f..%2f..%2fx", "ב נ ק לאומי: חשבונך ייחסם https://evil.test/x",
          "בנק ＬＥＵＭＩ חשבונך ייחסם https://evil.test", "בנק לאומי https://leumi.co.il%00.evil.test/x", "בנק לאומי https://evil。test/x",
          "בנק לאומי https://evil.top\u200f.leumi.co.il/x", "בנק לאומי https://[::1]/x", "בנק לאומי https://[::1", "בנק לאומי: אשרו аleumi.co.il/login",
          "לאתר evil.com. חשבונך ייחסם", "דואר ישראל: שלמו 9.90 ש\"ח https://israelpost.co.il/pay", "דואר ישראל: זכיתם בשובר https://israelpost.co.il/win",
          "בנק לאומי https://israelpost.co.il＠evil.top", "החבילה שלך ממתינה https://evil.xyz/pay שלמו", "שלום " * 5000 + "דואר ישראל https://israelpost.co.il/track",
          "0501" * 6000, "דואר ישראל שלמו " + "x" * 30000, "https://israelpost.co\u2026", "a\x00b https://evil.top", "𝐋𝐞𝐮𝐦𝐢 בנק https://evil.test"]
random.seed(11)
frags = ["https://", "hxxps://", "www.", "israelpost.co.il", "evil.top", "pay-now.top", "@", "[.]", "\\", "בנק לאומי", "דואר ישראל", "בנק הפועלים", "כביש 6", "050-1234567",
         "*5424", "קוד 123456", "urgent", "דחוף", "שלמו תוך 24 שעות", "אל תגידו לאף אחד", "סיסמה", "פרטי אשראי", "bit.ly/x", "gov.il/he/x", "leumi.co.il", "iec.co.il",
         "clalit.co.il/a", "report.pdf", "ynet.co.il", "‎", " ", "hello", "היי", "מה קורה", "?", "."]
for _ in range(400):
    texts.append(" ".join(random.choice(frags) for _ in range(random.randint(1, 9))))
open(f"{work}/in.jsonl", "w", encoding="utf-8").write("\n".join(json.dumps(t, ensure_ascii=False) for t in texts) + "\n")
with open(f"{work}/py.jsonl", "w", encoding="utf-8") as f:
    for t in texts:
        r = e.check(t)
        f.write(json.dumps({"verdict": r.verdict.value, "headline": r.headline_he, "certain": r.certain, "applicable": r.applicable, "unsupported": r.unsupported_language,
                            "links": [l.verdict.value + "|" + l.host for l in r.links], "signals": sorted(r.signal_ids), "orgs": sorted(r.claimed_orgs)}, ensure_ascii=False) + "\n")
