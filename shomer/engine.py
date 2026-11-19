"""Shomer verification engine.

Given the text of a message, decide whether the link matches the official site of the
organization the message claims to come from. The engine never says a link is "safe":
the best verdict is "matches the official site, but open the app yourself anyway".
"""
from __future__ import annotations

import json
import re
from dataclasses import dataclass, field, asdict
from enum import Enum
from pathlib import Path
from urllib.parse import urlsplit

DATA = Path(__file__).parent / "data" / "directory.json"

_SECOND_LEVEL = {"co.il", "org.il", "gov.il", "muni.il", "ac.il", "net.il", "k12.il"}
_URL_RE = re.compile(
    r"(?:https?://|www\.)[^\s<>\"'־״]+"
    r"|(?<![\w@.-])[a-z0-9-]+(?:\.[a-z0-9-]+)*\.[a-z]{2,24}(?:/[^\s<>\"']*)?(?![\w.-])",
    re.IGNORECASE,
)
_IP_RE = re.compile(r"^\d{1,3}(?:\.\d{1,3}){3}$")
_TRAILING = ".,;:!?)]}״\"'"


class Verdict(str, Enum):
    DONT_CLICK = "dont_click"
    MATCHES_OFFICIAL = "matches_official"
    CANT_VERIFY = "cant_verify"


@dataclass
class Org:
    id: str
    name_he: str
    aliases: list
    brand_tokens: list
    domains: list
    official_url: str
    app: str
    verified: bool
    source: str


@dataclass
class LinkReport:
    url: str
    host: str
    verdict: Verdict
    reasons: list = field(default_factory=list)
    matched_org: str | None = None


@dataclass
class Result:
    verdict: Verdict
    headline_he: str
    advice_he: str
    reasons_he: list
    claimed_orgs: list
    links: list
    official_url: str | None
    applicable: bool

    def to_dict(self) -> dict:
        d = asdict(self)
        d["verdict"] = self.verdict.value
        for l in d["links"]:
            l["verdict"] = l["verdict"].value if hasattr(l["verdict"], "value") else l["verdict"]
        return d


def _levenshtein(a: str, b: str) -> int:
    prev = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        cur = [i]
        for j, cb in enumerate(b, 1):
            cur.append(min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (ca != cb)))
        prev = cur
    return prev[-1]


def registrable(host: str) -> str:
    labels = host.split(".")
    if len(labels) >= 3 and ".".join(labels[-2:]) in _SECOND_LEVEL:
        return ".".join(labels[-3:])
    return ".".join(labels[-2:]) if len(labels) >= 2 else host


def parse_host(url: str) -> tuple[str, bool]:
    """Return (lowercase host, has_userinfo). The host is what the browser would really visit."""
    u = url if re.match(r"^[a-z][a-z0-9+.-]*://", url, re.I) else "http://" + url
    parts = urlsplit(u)
    host = (parts.hostname or "").rstrip(".").lower()
    return host, "@" in parts.netloc


def _alias_pattern(alias: str) -> re.Pattern:
    a = re.escape(alias.lower())
    if re.search(r"[א-ת]", alias):
        # Hebrew: allow one-letter prefixes (ב, ה, ל, מ, ו, ש, כ) and require non-letter boundaries
        return re.compile(r"(?<![א-ת])[בהלמושכ]?" + a + r"(?![א-ת])")
    return re.compile(r"(?<![a-z0-9])" + a + r"(?![a-z0-9])")


class Engine:
    def __init__(self, directory_path: Path | str = DATA):
        raw = json.loads(Path(directory_path).read_text(encoding="utf-8"))
        self.orgs = [Org(**{k: v for k, v in o.items()}) for o in raw["orgs"]]
        self.by_id = {o.id: o for o in self.orgs}
        self.shorteners = set(raw["shorteners"])
        self.bad_tlds = set(raw["suspicious_tlds"])
        self._alias_res = [(o, [_alias_pattern(a) for a in o.aliases]) for o in self.orgs]

    # ---- extraction -------------------------------------------------------
    def extract_urls(self, text: str) -> list:
        urls = []
        for m in _URL_RE.finditer(text):
            u = m.group(0).rstrip(_TRAILING)
            if u and u not in urls:
                urls.append(u)
        return urls

    def claimed_orgs(self, text: str) -> list:
        low = text.lower()
        return [o for o, pats in self._alias_res if any(p.search(low) for p in pats)]

    # ---- per-link analysis ------------------------------------------------
    def _official_org(self, host: str):
        for o in self.orgs:
            for d in o.domains:
                if host == d or host.endswith("." + d):
                    return o
        return None

    def _lookalike_of(self, host: str):
        collapsed = host.replace("-", "").replace(".", "")
        for o in self.orgs:
            if any(t in collapsed for t in o.brand_tokens):
                return o
        core = registrable(host).split(".")[0]
        for o in self.orgs:
            for d in o.domains:
                official_core = registrable(d).split(".")[0]
                if len(official_core) >= 6 and core != official_core and _levenshtein(core, official_core) <= 2:
                    return o
        return None

    def analyze_link(self, url: str, claimed: list) -> LinkReport:
        host, userinfo = parse_host(url)
        claimed_ids = {o.id for o in claimed}
        reasons: list = []
        if not host:
            return LinkReport(url, host, Verdict.CANT_VERIFY, ["לא הצלחנו לקרוא את כתובת הקישור"])
        if userinfo:
            reasons.append("הקישור משתמש בטריק של הסימן @ כדי להסוות לאן הוא באמת מוביל")
        if _IP_RE.match(host):
            return LinkReport(url, host, Verdict.DONT_CLICK, reasons + ["הקישור מוביל למספר ולא לכתובת אתר רגילה"])
        official = self._official_org(host)
        if official and not userinfo:
            if claimed_ids and official.id not in claimed_ids:
                return LinkReport(url, host, Verdict.CANT_VERIFY,
                                  ["ההודעה מציגה גוף אחד אבל הקישור שייך לגוף אחר: " + official.name_he], official.id)
            if not official.verified:
                return LinkReport(url, host, Verdict.CANT_VERIFY,
                                  [f"הכתובת נראית כמו האתר של {official.name_he}, אבל הכתובת הרשמית עוד לא אומתה אצלנו"], official.id)
            return LinkReport(url, host, Verdict.MATCHES_OFFICIAL,
                              [f"הכתובת תואמת לאתר הרשמי של {official.name_he}"], official.id)
        if host in self.shorteners or registrable(host) in self.shorteners:
            return LinkReport(url, host, Verdict.CANT_VERIFY, reasons + ["זה קישור מקוצר, אי אפשר לראות לאן הוא מוביל"])
        look = self._lookalike_of(host)
        punycode = any(l.startswith("xn--") for l in host.split(".")) or not host.isascii()
        if punycode:
            reasons.append("בכתובת יש תווים מוסווים שנראים כמו אותיות רגילות")
        if look:
            reasons.append(f"הכתובת מנסה להידמות ל{look.name_he} אבל היא לא האתר שלהם")
            return LinkReport(url, host, Verdict.DONT_CLICK, reasons, look.id)
        if claimed:
            names = ", ".join(o.name_he for o in claimed)
            reasons.append(f"ההודעה מציגה את עצמה כ{names}, אבל הקישור מוביל לאתר אחר")
            return LinkReport(url, host, Verdict.DONT_CLICK, reasons)
        if punycode or userinfo:
            return LinkReport(url, host, Verdict.DONT_CLICK, reasons)
        tld = host.rsplit(".", 1)[-1]
        if tld in self.bad_tlds:
            reasons.append("סיומת הכתובת נפוצה באתרי הונאה")
        reasons.append("לא מצאנו את הגוף השולח ברשימת הגופים שאנחנו מכירים")
        return LinkReport(url, host, Verdict.CANT_VERIFY, reasons)

    # ---- message level ----------------------------------------------------
    def check(self, text: str) -> Result:
        urls = self.extract_urls(text)
        claimed = self.claimed_orgs(text)
        reports = [self.analyze_link(u, claimed) for u in urls]
        applicable = bool(urls or claimed)
        official_url = claimed[0].official_url if claimed else None

        if any(r.verdict is Verdict.DONT_CLICK for r in reports):
            verdict = Verdict.DONT_CLICK
        elif reports and all(r.verdict is Verdict.MATCHES_OFFICIAL for r in reports):
            verdict = Verdict.MATCHES_OFFICIAL
        else:
            verdict = Verdict.CANT_VERIFY

        reasons = []
        for r in reports:
            for why in r.reasons:
                if why not in reasons:
                    reasons.append(why)
        if not urls and claimed:
            reasons.append("ההודעה מתיימרת להגיע מ" + ", ".join(o.name_he for o in claimed) + " אבל אין בה קישור שאפשר לאמת")
        if not urls and not claimed:
            reasons.append("אין בהודעה קישור או גוף מוכר לבדוק")

        if verdict is Verdict.DONT_CLICK:
            headline = "אל תלחצו על הקישור"
            advice = "אל תמסרו פרטים ואל תשלמו דרך ההודעה. אם זה נוגע לכם, פתחו בעצמכם את האפליקציה או האתר הרשמי."
        elif verdict is Verdict.MATCHES_OFFICIAL:
            headline = "הקישור תואם לאתר הרשמי"
            advice = "גם כך, הדרך המומלצת היא לפתוח את האפליקציה או האתר הרשמי בעצמכם ולא דרך ההודעה."
        else:
            headline = "אי אפשר לאמת. אל תלחצו, ושאלו מישהו מהמשפחה"
            advice = "אם ההודעה נוגעת לכם, פתחו בעצמכם את האפליקציה או האתר הרשמי, או התקשרו למוקד הרשמי."
        return Result(verdict, headline, advice, reasons, [o.id for o in claimed], reports, official_url, applicable)
