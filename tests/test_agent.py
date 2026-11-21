import pytest

from shomer.agent import should_alert
from shomer.engine import Engine

E = Engine()


@pytest.mark.parametrize("text,strict,expected", [
    ("נתראה בערב אצל סבתא", False, False),
    ("הלכתי לדואר ישראל היום", False, False),                       # names an org, nothing else
    ("ראית את זה? https://www.ynet.co.il/news/article/abc בנק לאומי מעלה ריבית", True, False),
    ("מה קורה? https://youtu.be/abc", True, False),
    ("https://israelpost.co.il/track", False, False),               # matches official: never speaks
    ("דואר ישראל: עדכנו פרטי אשראי תוך 24 שעות https://israelpost-fee.top/pay", True, True),
    ("דואר ישראל: החבילה שלך ממתינה https://evil-site.top/x", False, True),   # SMS: speaks
    ("דואר ישראל: החבילה שלך ממתינה https://evil-site.top/x", True, False),   # chat: needs more than a name
    ("היי אמא, זה המספר החדש שלי, הטלפון נפל ונשבר. אפשר להעביר לי דחוף 2,500 שקל? אחזיר מחר", True, True),
    ("בנק לאומי: קוד האימות שלך הוא 482913. אל תמסור אותו לאף אחד, גם לא לנציג הבנק.", False, False),
    ("אפשר להעביר ביט 50 ש\"ח על הקפה?", True, False),
])
def test_alert_policy(text, strict, expected):
    assert should_alert(E.check(text), strict) is expected, text
