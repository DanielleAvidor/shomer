import json
from pathlib import Path

import pytest

from shomer.engine import Engine, Verdict, parse_host, registrable

CORPUS = [json.loads(l) for l in (Path(__file__).parent.parent / "eval" / "corpus.jsonl").read_text(encoding="utf-8").splitlines() if l.strip()]
E = Engine()


@pytest.mark.parametrize("case", CORPUS, ids=[c["id"] for c in CORPUS])
def test_corpus_case(case):
    r = E.check(case["text"])
    if case["expected"] == "not_applicable":
        assert not r.applicable
    else:
        assert r.applicable
        assert r.verdict.value == case["expected"], (case["id"], r.verdict, r.reasons_he)


def test_userinfo_host_is_the_real_destination():
    host, userinfo = parse_host("https://israelpost.co.il@evil.com/x")
    assert host == "evil.com" and userinfo


def test_official_domain_as_prefix_of_foreign_domain_is_not_official():
    assert E._official_org("israelpost.co.il.evil.com") is None
    assert E._official_org("notisraelpost.co.il") is None
    assert E._official_org("www.israelpost.co.il").id == "israel_post"


def test_registrable_handles_israeli_second_level():
    assert registrable("www.israelpost.co.il") == "israelpost.co.il"
    assert registrable("a.b.example.com") == "example.com"


def test_extracts_bare_domains_and_strips_punctuation():
    assert E.extract_urls("ראו israelpost.co.il/track, תודה") == ["israelpost.co.il/track"]
    assert E.extract_urls("https://a.com/x.") == ["https://a.com/x."[:-1]]


def test_unverified_directory_entry_never_yields_match():
    r = E.check("בנק לאומי https://www.leumi.co.il")
    assert r.verdict is Verdict.CANT_VERIFY


def test_two_links_worst_verdict_wins():
    r = E.check("דואר ישראל https://israelpost.co.il/a וגם https://israelpost.evil.top/b")
    assert r.verdict is Verdict.DONT_CLICK


def test_two_organizations_in_one_message_detected():
    r = E.check("בנק הפועלים ודואר ישראל https://israelpost.co.il")
    assert set(r.claimed_orgs) == {"bank_hapoalim", "israel_post"}


def test_hebrew_prefix_and_word_boundaries():
    assert [o.id for o in E.claimed_orgs("שלחו לי הודעה מדואר ישראל")] == ["israel_post"]
    assert E.claimed_orgs("ביטוח הרכב שלי") == []  # 'ביט' must not match inside 'ביטוח'


def test_never_says_safe_word():
    for c in CORPUS:
        r = E.check(c["text"])
        blob = (r.headline_he + r.advice_he + " ".join(r.reasons_he)).replace("זה לא אומר שההודעה בטוחה", "")
        assert "בטוח" not in blob and "ללא סיכון" not in blob


def test_matches_verdict_always_recommends_opening_app_yourself():
    r = E.check("דואר ישראל https://israelpost.co.il")
    assert r.verdict is Verdict.MATCHES_OFFICIAL
    assert "בעצמכם" in r.advice_he


def test_official_link_without_claimed_organization_never_matches():
    assert E.check("Amazon: your order https://israelpost.co.il/x").verdict is Verdict.CANT_VERIFY


def test_phone_number_downgrades_a_match():
    assert E.check("דואר ישראל התקשרו 050-1234567 https://israelpost.co.il").verdict is Verdict.CANT_VERIFY
    assert E.check("דואר ישראל https://israelpost.co.il").verdict is Verdict.MATCHES_OFFICIAL


def test_shared_government_domain_needs_the_right_path():
    ok = E.check("משטרת ישראל https://www.gov.il/he/service/enforcement-reports")
    bad = E.check("משטרת ישראל https://www.gov.il/he/service/other")
    assert ok.verdict is Verdict.MATCHES_OFFICIAL and bad.verdict is Verdict.CANT_VERIFY


def test_brand_token_must_start_a_host_part():
    assert E._lookalike_of("storefront.com") is None
    assert E._lookalike_of("pay-israelpost.top").id == "israel_post"


def test_app_store_links_are_neutral():
    r = E.check("דואר ישראל https://play.google.com/store/apps/details?id=x")
    assert r.verdict is Verdict.CANT_VERIFY


def test_family_impersonation_without_link_is_flagged():
    r = E.check("אמא החלפתי מספר, אפשר להעביר לי כסף בביט?")
    assert r.verdict is Verdict.DONT_CLICK and r.kind == "content"
    assert "אל תעבירו כסף" in r.steps_he[0]


def test_warning_about_codes_is_not_a_request_for_codes():
    ids = {g["id"] for g in E.content_signals("לעולם אל תמסרו קוד אימות לאף אחד")}
    assert "credentials" not in ids
    ids = {g["id"] for g in E.content_signals("אנא הזינו את קוד אימות שנשלח אליכם ואת מספר הכרטיס")}
    assert "credentials" in ids


def test_pressure_downgrades_an_official_match():
    r = E.check("דואר ישראל: עדכנו פרטי אשראי תוך 24 שעות https://israelpost.co.il/pay")
    assert r.verdict is Verdict.CANT_VERIFY


def test_guidance_fields_present_for_risky_results_only():
    risky = E.check("דואר ישראל https://israelpost.paliet.top/il")
    assert risky.if_clicked_he and "119" in " ".join(risky.if_clicked_he)
    assert "israelpost.paliet.top" in risky.share_text_he
    ok = E.check("דואר ישראל https://israelpost.co.il")
    assert ok.if_clicked_he == []


def test_share_text_never_contains_the_message():
    msg = "דואר ישראל סיסמה-סודית https://israelpost.paliet.top/il"
    assert "סודית" not in E.check(msg).share_text_he


@pytest.mark.parametrize("text", [
    "כאן מחלקת הונאות בבנק, העבר לנו את הקוד שקיבלת",
    "פייפאל: הכנס קוד חד פעמי כדי לשחרר את החשבון",
    "ז.כ.י.ת ב.ה.ג.ר.ל.ה מתנה https://prize-il.xyz/win",
    "Your account is suspended. Verify your account now: enter your password",
])
def test_round2_scams_are_blocked(text):
    assert Engine().check(text).verdict.value == "dont_click"


@pytest.mark.parametrize("text", [
    "אבא, החלפתי מספר? לא, זה עדיין אני",
    "אבא, תעביר לי בבקשה את המספר של האינסטלטור",
    "ביט אליי 50 על הפיצה",
])
def test_round2_legit_family_messages_not_blocked(text):
    assert Engine().check(text).verdict.value != "dont_click"


def test_link_only_official_address_is_recognised_but_message_not_vouched_for():
    r = Engine().check("israelpost.co.il/track")
    assert r.verdict is Verdict.MATCHES_OFFICIAL
    assert any("לא אומר שההודעה" in x for x in r.reasons_he)
    assert r.links[0].real_domain == "israelpost.co.il"


def test_link_only_unverified_org_is_not_a_match():
    assert Engine().check("https://www.kvish6.co.il/pay").verdict is not Verdict.MATCHES_OFFICIAL


def test_breakdown_shows_real_vs_official_domain():
    l = Engine().check("https://israelpost.paliet.top/il").links[0]
    assert (l.real_domain, l.official_domain, l.org_name) == ("paliet.top", "israelpost.co.il", "דואר ישראל")


def test_context_question_for_known_org_and_certainty_flag():
    r = Engine().check("דואר ישראל: החבילה שלך נעצרה. אנא אשר כתובת https://israelpost.paliet.top/il")
    assert "חבילה" in r.context_question_he and r.certain
    soft = Engine().check("קיבלת חבילה https://bit.ly/3xYz")
    assert not soft.certain


def test_russian_and_arabic_are_reported_as_unsupported_not_as_clean():
    for t in ["Ваш банковский счёт будет заблокирован. Срочно позвоните и сообщите пароль",
              "حسابك البنكي سيتم حظره اليوم، اتصل الآن وأعطنا كلمة المرور"]:
        r = E.check(t)
        assert not r.applicable and r.unsupported_language
        assert "בעברית ובאנגלית" in r.headline_he


def test_ordinary_hebrew_and_english_are_not_flagged_unsupported():
    for t in ["נתראה בערב אצל סבתא", "see you tonight at eight at grandma's place"]:
        r = E.check(t)
        assert not r.unsupported_language
        assert "אישור" in r.headline_he or "לא" in r.headline_he


def test_nothing_found_headline_itself_carries_the_caveat():
    assert "לא אישור" in E.check("נתראה בערב").headline_he
