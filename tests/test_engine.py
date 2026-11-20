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
        blob = r.headline_he + r.advice_he + " ".join(r.reasons_he)
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
