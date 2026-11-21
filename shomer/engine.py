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
    # The lookarounds are ASCII only: a Hebrew prefix letter or a Hebrew word glued to an address ("לevil.test", "evil.testשלמו")
    # must not hide it.
    r"|(?<![a-z0-9@._-])[a-z0-9-]+(?:\.[a-z0-9-]+)*\.[a-z]{2,24}(?:/[^\s<>\"']*)?(?![a-z0-9._-])",
    re.IGNORECASE,
)
_IP_RE = re.compile(r"^\d{1,3}(?:\.\d{1,3}){3}$")
_TRAILING = ".,;:!?)]}״\"'"
# Israeli phone numbers (mobile 05x, landline 02-09, 07x). Star numbers such as *5424 are not matched.
_PHONE_RE = re.compile(
    r"(?<![\d*])0(?:5\d|[2-489]|7\d)[-\s]?\d{3}[-\s]?\d{4}(?!\d)"                       # 050-1234567
    r"|(?<![\d*])(?:\+|00)?972[-\s]?\(?0?\)?[-\s]?(?:5\d|[2-489]|7\d)[-\s]?\d{3}[-\s]?\d{4}(?!\d)"  # +972 52 123 4567
    r"|(?<![\w*])\*\d{3,5}(?![\d*])"                                                   # *5424
    r"|(?<!\d)1[-\s]?(?:700|800|801|900)[-\s]?\d{3}[-\s]?\d{3}(?!\d)"                 # 1-700-500-500
)
_INVISIBLE = re.compile("[\u200b-\u200f\u202a-\u202e\u2060-\u2069\ufeff]")
_FILE_EXT = {"pdf", "js", "jpg", "jpeg", "png", "gif", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "mp3", "mp4", "py", "java", "json", "xml", "css", "svg", "heic"}


def prepare(text: str) -> str:
    """Undo tricks that hide an address from a reader or a regex: invisible direction marks, 'hxxps' and '[.]' defanging."""
    text = _INVISIBLE.sub("", text)
    text = re.sub(r"hxxp", "http", text, flags=re.I)
    return re.sub(r"\[\.\]|\(\.\)|\{\.\}", ".", text)


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
    restricted_paths: dict = field(default_factory=dict)
    unconfirmed_domains: list = field(default_factory=list)  # probably the organization's, not confirmed from its own site: never "official", never "lookalike"
    context_he: str = ""  # a question that helps the person judge: were you expecting this?


@dataclass
class LinkReport:
    url: str
    host: str
    verdict: Verdict
    reasons: list = field(default_factory=list)
    matched_org: str | None = None
    real_domain: str = ""  # the part of the address that decides who owns it
    official_domain: str = ""  # the official address of the organisation the link claims or imitates
    org_name: str = ""  # that organisation's name


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
    signals_he: list = field(default_factory=list)
    steps_he: list = field(default_factory=list)
    if_clicked_he: list = field(default_factory=list)
    share_text_he: str = ""
    kind: str = "link"  # "link" or "content" (no link to check, judged on what the message asks)
    escalated_by_advisor: bool = False
    context_question_he: str = ""
    certain: bool = False  # high confidence that this is a scam (used for family alerts)
    signal_ids: list = field(default_factory=list)  # which content signals matched (urgency, threat, credentials, ...)
    unsupported_language: bool = False  # the message is mostly in a script the checker does not read (e.g. Russian, Arabic)

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


def _split(u: str):
    """urlsplit, but a stray bracket (a fake IPv6 literal) must never crash the check."""
    try:
        return urlsplit(u)
    except ValueError:
        try:
            return urlsplit(u.replace("[", "").replace("]", ""))
        except ValueError:
            return urlsplit("http://")


def parse_host(url: str) -> tuple[str, bool]:
    """Return (lowercase host, has_userinfo). The host is what the browser would really visit."""
    u = url if re.match(r"^[a-z][a-z0-9+.-]*://", url, re.I) else "http://" + url
    parts = _split(u.replace("\\", "/"))  # a browser treats a backslash like a slash, so "evil.test\\.israelpost.co.il" goes to evil.test
    host = (parts.hostname or "").rstrip(".").lower()
    return host, "@" in parts.netloc


def parse_path(url: str) -> str:
    u = url if re.match(r"^[a-z][a-z0-9+.-]*://", url, re.I) else "http://" + url
    return _split(u.replace("\\", "/")).path or "/"


def _token_hit(host: str, tokens: list) -> bool:
    """A brand token counts only if it starts a dot/hyphen-separated part of the host (so 'storefront' does not contain 'oref')."""
    segs = re.split(r"[.-]", host)
    collapsed = "".join(segs)
    starts, pos = set(), 0
    for seg in segs:
        starts.add(pos)
        pos += len(seg)
    for t in tokens:
        i = collapsed.find(t)
        while i != -1:
            if i in starts:
                return True
            i = collapsed.find(t, i + 1)
    return False


def _alias_pattern(alias: str) -> re.Pattern:
    a = re.escape(alias.lower())
    if re.search(r"[א-ת]", alias):
        # Hebrew: allow one-letter prefixes (ב, ה, ל, מ, ו, ש, כ) and require non-letter boundaries
        return re.compile(r"(?<![א-ת])[בהלמושכ]?" + a + r"(?![א-ת])")
    return re.compile(r"(?<![a-z0-9])" + a + r"(?![a-z0-9])")


_OBFUSCATION = re.compile(r"(?<=[א-ת])[.\-_*·\u200b-\u200f\u05b0-\u05c7](?=[א-ת])")
_SPACED = re.compile(r"(?<![א-ת])([א-ת]) (?=[א-ת](?: [א-ת])*(?: |$|[.,!?]))")


def normalize(text: str) -> str:
    """Undo common obfuscation: dots, dashes, zero-width characters or niqqud between Hebrew letters."""
    text = _OBFUSCATION.sub("", text)
    return text


class Engine:
    def __init__(self, directory_path: Path | str = DATA):
        raw = json.loads(Path(directory_path).read_text(encoding="utf-8"))
        self.orgs = [Org(**{k: v for k, v in o.items()}) for o in raw["orgs"]]
        self.by_id = {o.id: o for o in self.orgs}
        self.shorteners = set(raw["shorteners"])
        self.bad_tlds = set(raw["suspicious_tlds"])
        self.neutral = set(raw.get("neutral_domains", []))
        self.common = set(raw.get("common_domains", []))
        self.signals = [(g, re.compile(g["pattern"], re.IGNORECASE)) for g in raw.get("signals", [])]
        self._alias_res = [(o, [_alias_pattern(a) for a in o.aliases]) for o in self.orgs]

    # ---- extraction -------------------------------------------------------
    def extract_urls(self, text: str) -> list:
        urls = []
        for m in _URL_RE.finditer(text):
            u = m.group(0).rstrip(_TRAILING)
            if u and not re.match(r"(?:https?://|www\.)", u, re.I) and re.split(r"[/?#]", u)[0].rsplit(".", 1)[-1].lower() in _FILE_EXT:
                continue  # "report.pdf" is a file name, not an address
            if u and u not in urls:
                urls.append(u)
        return urls

    def claimed_orgs(self, text: str) -> list:
        text = normalize(text)
        # Look for organization names only outside links: a link must not vouch for itself.
        low = _URL_RE.sub(" ", text).lower()
        return [o for o, pats in self._alias_res if any(p.search(low) for p in pats)]

    # ---- per-link analysis ------------------------------------------------
    def _official_match(self, host: str):
        """Return (org, matched_domain) for the longest official domain that host equals or is a subdomain of."""
        best = (None, "")
        for o in self.orgs:
            for d in o.domains:
                if (host == d or host.endswith("." + d)) and len(d) > len(best[1]):
                    best = (o, d)
        return best

    def _official_org(self, host: str):
        return self._official_match(host)[0]

    def _lookalike_of(self, host: str):
        for o in self.orgs:
            if _token_hit(host, o.brand_tokens):
                return o
        core = registrable(host).split(".")[0]
        for o in self.orgs:
            for d in o.domains:
                official_core = registrable(d).split(".")[0]
                if len(official_core) >= 6 and core != official_core and _levenshtein(core, official_core) <= 2:
                    return o
        return None

    def analyze_link(self, url: str, claimed: list, link_only: bool = False) -> LinkReport:
        rep = self._analyze_link(url, claimed, link_only)
        if rep.host:
            rep.real_domain = registrable(rep.host)
        org = self.by_id.get(rep.matched_org) if rep.matched_org else (claimed[0] if claimed else None)
        if org is not None:
            rep.org_name = org.name_he
            rep.official_domain = org.domains[0] if org.domains else ""
        return rep

    def _analyze_link(self, url: str, claimed: list, link_only: bool = False) -> LinkReport:
        if url.endswith("\u2026"):
            # A notification preview cut the link short: we never saw the real address, so say so instead of guessing.
            h, _ = parse_host(url.rstrip("\u2026"))
            return LinkReport(url, h, Verdict.CANT_VERIFY, ["הקישור נחתך בתצוגה המקדימה ולא ראינו אותו עד הסוף. פתחו את ההודעה המלאה כדי לבדוק"])
        host, userinfo = parse_host(url)
        claimed_ids = {o.id for o in claimed}
        reasons: list = []
        if not host:
            return LinkReport(url, host, Verdict.CANT_VERIFY, ["לא הצלחנו לקרוא את כתובת הקישור"])
        if userinfo:
            reasons.append("הקישור משתמש בטריק של הסימן @ כדי להסוות לאן הוא באמת פותח")
        if _IP_RE.match(host):
            return LinkReport(url, host, Verdict.DONT_CLICK, reasons + ["הקישור פותח למספר ולא לכתובת אתר רגילה"])
        if host in self.neutral:
            return LinkReport(url, host, Verdict.CANT_VERIFY, reasons + ["זה קישור לחנות אפליקציות ולא לאתר של השולח, ולכן לא בדקנו אותו"])
        official, matched = self._official_match(host)
        if official and not userinfo:
            prefixes = official.restricted_paths.get(matched)
            if prefixes and not any(parse_path(url).startswith(p) for p in prefixes):
                return LinkReport(url, host, Verdict.CANT_VERIFY,
                                  [f"הכתובת היא של אתר ממשלתי כללי ({matched}), ולא הצלחנו לוודא שהדף הזה שייך לגוף ששלח את ההודעה"])
            if not claimed_ids and link_only and official.verified:
                return LinkReport(url, host, Verdict.MATCHES_OFFICIAL,
                                  [f"זו הכתובת הרשמית של {official.name_he}. זה לא אומר שההודעה שהגיעה עם הקישור אמיתית"], official.id)
            if not claimed_ids:
                return LinkReport(url, host, Verdict.CANT_VERIFY,
                                  [f"הכתובת שייכת ל{official.name_he}, אבל ההודעה לא אומרת מי שלח אותה"], official.id)
            if official.id not in claimed_ids:
                return LinkReport(url, host, Verdict.CANT_VERIFY,
                                  ["ההודעה מציגה גוף אחד אבל הקישור שייך לגוף אחר: " + official.name_he], official.id)
            if not official.verified:
                return LinkReport(url, host, Verdict.CANT_VERIFY,
                                  [f"הכתובת נראית כמו האתר של {official.name_he}, אבל הכתובת הרשמית עוד לא נבדקה אצלנו"], official.id)
            return LinkReport(url, host, Verdict.MATCHES_OFFICIAL,
                              [f"הכתובת תואמת לאתר הרשמי של {official.name_he}"], official.id)
        if not userinfo:
            for o in self.orgs:
                if any(host == d or host.endswith("." + d) for d in o.unconfirmed_domains):
                    return LinkReport(url, host, Verdict.CANT_VERIFY, reasons + [f"הכתובת נראית קשורה ל{o.name_he}, אבל עוד לא אימתנו אותה"], o.id)
        if host in self.shorteners or registrable(host) in self.shorteners:
            return LinkReport(url, host, Verdict.CANT_VERIFY, reasons + ["זה קישור קצר, ואי אפשר לראות לאן הוא באמת מוביל"])
        if registrable(host) in self.common and not userinfo and not self._lookalike_of(host):
            return LinkReport(url, host, Verdict.CANT_VERIFY, reasons + ["זה קישור לאתר מוכר, לא לאתר של הגוף שהוזכר בהודעה, ולכן לא בדקנו אותו"])
        look = self._lookalike_of(host)
        punycode = any(l.startswith("xn--") for l in host.split(".")) or not host.isascii()
        if punycode:
            reasons.append("בכתובת יש תווים מוסווים שנראים כמו אותיות רגילות")
        if look:
            reasons.append(f"הכתובת מנסה לחקות את {look.name_he} אבל היא לא האתר שלהם")
            return LinkReport(url, host, Verdict.DONT_CLICK, reasons, look.id)
        if claimed:
            names = ", ".join(o.name_he for o in claimed)
            reasons.append(f"ההודעה מציגה את עצמה כ{names}, אבל הקישור פותח לאתר אחר")
            return LinkReport(url, host, Verdict.DONT_CLICK, reasons)
        if punycode or userinfo:
            return LinkReport(url, host, Verdict.DONT_CLICK, reasons)
        tld = host.rsplit(".", 1)[-1]
        if tld in self.bad_tlds:
            reasons.append("הכתובת נגמרת בסיומת שנפוצה בהונאות")
        reasons.append("לא זיהינו מי שלח את ההודעה")
        return LinkReport(url, host, Verdict.CANT_VERIFY, reasons)

    # ---- message level ----------------------------------------------------
    def content_signals(self, text: str) -> list:
        """Signals about what the message asks for (outside links)."""
        body = _URL_RE.sub(" ", normalize(text)).lower()
        return [g for g, rx in self.signals if rx.search(body)]

    def check(self, text: str) -> Result:
        text = prepare(text)
        urls = self.extract_urls(text)
        claimed = self.claimed_orgs(text)
        leftover = _URL_RE.sub(" ", text).strip(" \n\t.,:;-")
        link_only = bool(urls) and len(leftover) <= 3
        reports = [self.analyze_link(u, claimed, link_only) for u in urls]
        sigs = self.content_signals(text)
        sig_ids = {g["id"] for g in sigs}
        pressure = {g["id"] for g in sigs if g["kind"] == "pressure"}
        phone = bool(_PHONE_RE.search(text))
        bad_tld = any(r.host.rsplit('.', 1)[-1] in self.bad_tlds for r in reports if r.host)
        score = sum(g["weight"] for g in sigs) + (1 if phone and claimed else 0)
        if "family" in sig_ids and not ({"family_intro", "urgency", "threat"} & sig_ids):
            score -= 1  # "can you send 50 shekels for the coffee" needs more than the request itself
        applicable = bool(urls or claimed or score >= 3)
        official_url = claimed[0].official_url if claimed else None
        names = ", ".join(o.name_he for o in claimed)

        if any(r.verdict is Verdict.DONT_CLICK for r in reports):
            verdict = Verdict.DONT_CLICK
        elif reports and all(r.verdict is Verdict.MATCHES_OFFICIAL for r in reports):
            verdict = Verdict.MATCHES_OFFICIAL
        else:
            verdict = Verdict.CANT_VERIFY

        reasons: list = []
        downgraded = False
        # A matching link is not enough when the message also pressures or asks for secrets or a call.
        if verdict is Verdict.MATCHES_OFFICIAL:
            if phone:
                verdict = Verdict.CANT_VERIFY
                downgraded = True
                reasons.append("ההודעה מבקשת גם להתקשר למספר טלפון, וזה לא משהו שאנחנו יכולים לבדוק")
            if "credentials" in sig_ids or "install" in sig_ids or len(pressure) >= 2:
                verdict = Verdict.CANT_VERIFY
                downgraded = True
                reasons.append("הכתובת תואמת, אבל ההודעה מלחיצה או מבקשת פרטים. גופים רשמיים לא עושים את זה בהודעה")
        # An unverifiable link plus a pushy message is treated as a scam.
        if verdict is Verdict.CANT_VERIFY and urls and (score >= 3 or (score >= 2 and bad_tld)) and not downgraded:
            verdict = Verdict.DONT_CLICK
        # No link at all: judge by what the message asks for.
        if not urls:
            if score >= 3:
                verdict = Verdict.DONT_CLICK
            elif applicable or claimed:
                verdict = Verdict.CANT_VERIFY
        kind = "link" if urls else "content"

        for r in reports:
            for why in r.reasons:
                if why not in reasons:
                    reasons.append(why)
        for g in sigs:
            if g["he"] not in reasons and g["id"] != "payment":
                reasons.append(g["he"])
        if "payment" in sig_ids and verdict is not Verdict.MATCHES_OFFICIAL:
            reasons.append(next(g["he"] for g in sigs if g["id"] == "payment"))
        if phone and claimed and not urls:
            reasons.append("ההודעה מבקשת להתקשר למספר טלפון, וזה לא משהו שאנחנו יכולים לבדוק")
        if not urls and claimed:
            reasons.append("ההודעה מציגה את עצמה כמגיעה מ" + names + " אבל אין בה קישור שאפשר לבדוק")
        if not applicable:
            reasons.append("אין בהודעה קישור, גוף מוכר או בקשה חשודה")

        route = (f"פתחו בעצמכם את {claimed[0].app}" if claimed[0].app else f"פתחו בעצמכם את האתר הרשמי של {claimed[0].name_he}") if claimed else "פתחו בעצמכם את האפליקציה או האתר הרשמי"
        if verdict is Verdict.DONT_CLICK and urls:
            headline = "אל תלחצו על הקישור"
            advice = "אל תמסרו פרטים ואל תשלמו דרך ההודעה. אם זה נוגע לכם, פתחו בעצמכם את האפליקציה או האתר הרשמי."
            steps = ["אל תלחצו על הקישור ואל תענו להודעה", "אל תמסרו פרטים, סיסמאות או קודים", route, "ספרו לבן משפחה"]
        elif verdict is Verdict.DONT_CLICK and "family" in sig_ids:
            headline = "אל תעבירו כסף. ייתכן שזה לא מי שחשבתם"
            advice = "התקשרו אליו במספר שאתם מכירים, לפני שאתם מעבירים כסף."
            steps = ["אל תעבירו כסף ואל תשלחו קודים", "התקשרו לאדם במספר שכבר שמור אצלכם ושמעו את הקול שלו", "ספרו לבן משפחה"]
        elif verdict is Verdict.DONT_CLICK:
            headline = "אל תענו ואל תשלמו"
            advice = "אל תמסרו פרטים ואל תענו להודעה. אם זה נוגע לכם, פנו בעצמכם דרך האתר, האפליקציה או הטלפון הרשמיים."
            steps = ["אל תענו ואל תחזרו למי ששלח", "אל תמסרו פרטים, סיסמאות או קודים", route, "ספרו לבן משפחה"]
        elif verdict is Verdict.MATCHES_OFFICIAL:
            headline = "הכתובת היא של האתר הרשמי, אבל זה לא אישור להודעה"
            advice = "זה לא אומר שההודעה בטוחה. הדרך המומלצת היא לפתוח את האפליקציה או האתר הרשמי בעצמכם ולא דרך ההודעה."
            steps = [route + ", ולא דרך ההודעה"]
        else:
            headline = ("לא הצלחנו לבדוק. אל תלחצו ושאלו מישהו מהמשפחה" if urls
                        else "לא הצלחנו לבדוק. אל תפעלו לפי ההודעה ושאלו מישהו מהמשפחה")
            advice = "אם ההודעה נוגעת לכם, פתחו בעצמכם את האפליקציה או האתר הרשמי, או התקשרו למוקד הרשמי."
            steps = ["אל תלחצו ואל תענו", "שאלו מישהו מהמשפחה", route]
        unsupported = False
        if not applicable:
            headline = "לא מצאנו סימן חשד, אבל זה לא אישור"
            advice = "לא מצאנו כאן קישור או בקשה חשודה. זה לא אומר שההודעה בטוחה. אם משהו מרגיש לא בסדר, שאלו מישהו מהמשפחה."
            steps = []
            reasons = [r for r in reasons if not r.startswith("אין בהודעה")]
            letters = re.findall(r"[^\W\d_]", text)
            foreign = re.findall(r"[\u0400-\u04FF\u0600-\u06FF]", text)
            if len(letters) >= 12 and len(foreign) / len(letters) >= 0.3:
                unsupported = True
                headline = "שומר בודק רק הודעות בעברית ובאנגלית"
                advice = "לא בדקנו את ההודעה הזאת, וזה לא אומר שהיא בטוחה. אם היא מבקשת כסף, סיסמה, קוד או שתתקשרו למישהו, אל תפעלו לפי ההודעה ושאלו מישהו מהמשפחה."
                reasons.append("ההודעה כתובה בשפה ששומר עוד לא קורא")
        if (not urls and claimed and applicable and not (sig_ids & {"credentials", "family", "payment", "install", "prize", "threat"})
                and re.search(r"(?:קוד|סיסמ)[^.\n]{0,25}\d{4,8}|\d{4,8}[^.\n]{0,20}(?:קוד|סיסמ)", text)
                and re.search(r"אל (?:ת)?(?:מסור|שתף|שלח|גל|ספר|תגיד)|לעולם|לא (?:מבקש|נבקש)", text)):
            headline = "זו הודעה עם קוד. אל תמסרו את הקוד לאף אחד"
            advice = "אם לא ביקשתם את הקוד, אל תעשו כלום. אם משהו מדאיג אתכם, התקשרו לבנק במספר הרשמי."
            steps = ["אל תמסרו את הקוד לאף אחד, גם לא למי שמציג את עצמו כנציג של הבנק"]
            reasons = ["זו נראית הודעת קוד רגילה, וההודעה עצמה מזהירה לא למסור אותו"]
        if_clicked = [] if verdict is Verdict.MATCHES_OFFICIAL or not applicable or not urls else [
            "אל תכניסו שום פרט נוסף",
            "אם הזנתם פרטי כרטיס אשראי, התקשרו מיד לחברת האשראי וחסמו את הכרטיס",
            "אם הזנתם סיסמה, החליפו אותה עכשיו, וגם בכל מקום שבו השתמשתם באותה סיסמה",
            "ספרו למשפחה, ודווחו למוקד הסייבר הלאומי בטלפון 119",
        ]
        host = reports[0].host if reports else ""
        share = f"בדקתי הודעה עם שומר. התוצאה: {headline}." + (f" הקישור פותח את האתר {host}." if host else "") + " אפשר להסתכל על זה יחד?"
        certain = verdict is Verdict.DONT_CLICK and (any(r.verdict is Verdict.DONT_CLICK for r in reports) or score >= 4)
        org_for_q = claimed[0] if claimed else next((self.by_id[r.matched_org] for r in reports if r.matched_org in self.by_id), None)
        context_q = org_for_q.context_he if org_for_q else ""
        res = Result(verdict, headline, advice, reasons, [o.id for o in claimed], reports, official_url, applicable,
                     [g["he"] for g in sigs], steps, if_clicked, share, kind)
        res.certain = certain
        res.unsupported_language = unsupported
        res.signal_ids = [g["id"] for g in sigs]
        res.context_question_he = context_q
        return res
