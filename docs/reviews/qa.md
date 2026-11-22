# QA review (read-only, adversarial) - Shomer

Ran: `python -m pytest -q` = 135 passed (0.8s). `tools/kotlin-parity/run.sh` (GRADLE_LIB=/opt/gradle/lib) = 460 msgs, 0 diffs (README claim holds, 10s). I then fed 143 adversarial inputs to Python and the compiled Kotlin engine: 8 diffs (R2). docs/index.html opened in headless Chrome: no console errors, 11 baked results, 0 stale vs current engine.

## REJECTS

**R1 - blocker - Bare homograph domain reported as MATCHES_OFFICIAL (fail-open on the product's core promise)**
- Repro: `בנק לאומי: אשרו аleumi.co.il/login` (U+0430 Cyrillic a, no scheme) -> `matches_official`, host `leumi.co.il`. Expected `dont_click` (real host is xn--leumi-*.co.il). With `https://` it is correctly dont_click, so only the scheme-less path is broken. Same for `аisraelpost.co.il`.
- Cause: bare-domain alternative has an ASCII-only lookbehind (engine.py:21-23, Kotlin:267), so the match silently starts after the non-ASCII letter and the foreign letter is dropped.
- Fix: the bare-domain alt must swallow adjacent non-ASCII non-Hebrew letters (`[^\s<>"'א-ת...]`) into the token, so the token stays non-ASCII -> punycode branch. Add corpus cases for Cyrillic/Greek/Armenian/Cherokee first letters, scheme-less. Mirror in Kotlin.

**R2 - major - Python and Kotlin engines diverge on unparseable/IPv6 hosts**
- Repro (diff harness, 3 inputs): `דואר ישראל https://israelpost.co.il＠evil.top/x` (U+FF20): PY host `""`, `cant_verify`, certain=False; KT host `israelpost.co.il＠evil.top`, `dont_click`, certain=True. `http://[::1]/x`: PY host `::1`; KT host `[` (Kotlin.parseHost cuts at first ':').
- Cause: Python relies on urlsplit (NFKC check raises -> `urlsplit("http://")` fallback at engine.py:126-134); Kotlin hand-rolls parseHost (Kotlin:315-323) with no bracket/port handling.
- Fix: pick ONE semantic and write it down: (a) unparseable host with a claimed org or any URL char tricks => DONT_CLICK in Python too; (b) Kotlin parseHost: handle `[..]`, strip port only after last `]`, reject hosts containing non-host chars. Add these inputs to tools/kotlin-parity/gen_inputs.py as a fixed adversarial list (it only has ~13 hand-made + random fragment joins; it never produced these).
- Also: parity harness JSON writer (shim) does not escape control chars; NUL/VT inputs make kt.jsonl invalid JSON, so control-char inputs cannot be compared today.

**R3 - major - Quadratic regex backtracking (ReDoS) in signals; one long digit run freezes the checker**
- Repro: `E.check("דואר ישראל " + "1"*5000)` = 1.0s; 20000 = 18.3s; `"0"+"5"*20000` = 14s. Single signals: `prize` 6.6s and `payment` 7.1s on 20000 digits.
- Cause: patterns in shomer/data/directory.json: prize `\d+ ?(?:שקל|ש\"ח|₪)? ?ביום`, `להרוויח[^.\n]{0,15}\d+`, payment `\d[\d.,]* ?(?:nis|ils|shekels?|₪) (?:fee|...)` - unbounded `\d+` start at every position. Server accepts 60 000 bytes (server.py:99) => ~minutes per request on a ThreadingHTTPServer; on Android the same regexes run in the SMS BroadcastReceiver (SmsReceiver.kt) and notification listener: ANR risk, and attacker controls the text.
- Fix: bound quantifiers (`\d{1,6}`), anchor with `(?<!\d)`, and hard-cap input in `Engine.check` (e.g. first 4000 chars + last 1000) in BOTH engines; add a pytest with a 50k-digit input and `timeout < 1s`.

**R4 - major - Control/invisible characters inside the host are accepted as official**
- Repro: `בנק לאומי https://evil.top\x00.leumi.co.il/x` -> `matches_official` (host kept as `evil.top\x00.leumi.co.il`, endswith `.leumi.co.il`). Expected dont_click. Also `https://israel‎post.co.il/x` -> matches_official: `prepare()` (engine.py:39-43) deletes the RLM *inside* the address, so the engine judges a host the phone will never resolve.
- Fix: in parse_host reject (-> DONT_CLICK, reason "hidden characters") any host not matching `^[a-z0-9.-]+$` after IDNA, and in `prepare` do not strip invisibles that sit inside a URL token - flag them instead. Corpus cases: NUL, VT, ZWSP, RLM, soft hyphen inside host.

**R5 - major - README/CI claims are not backed by CI; Python tests, parity, eval never run in CI**
- .github has only android.yml and android-e2e.yml. Nothing runs `pytest`, `eval/run_eval.py`, or `tools/kotlin-parity/run.sh`. Path filters (`app/**`, `shomer/data/directory.json`) do not even trigger on a change to shomer/engine.py or eval/corpus.jsonl, so an engine edit can make Kotlin drift with no signal.
- `eval/corpus.jsonl` is duplicated into app/app/src/test/resources/corpus.jsonl and `directory.json` into app/.../assets (identical today, verified with diff) with no sync check. Single copy via Gradle `srcDirs("../../eval")`/symlink, or a CI `diff` step.
- Fix: add `.github/workflows/python.yml` (pytest on 3.10 and 3.13 - README says 3.10+, never tested; run_eval; fail if `git diff --exit-code eval/results.md`; run parity with the Gradle jars) triggered on any `shomer/**`, `eval/**`, `tools/**`, `app/**`.

**R6 - major - Android e2e workflow cannot fail**
- tools/e2e/run.sh has `set -u` (no `-e`), every artifact step ends in `|| true`, and it asserts nothing: it never greps for the expected "Don't click" notification nor the absence of one for the ordinary SMS. Job is green even with zero notifications. Only 2 messages (one scam, one chat); no WhatsApp/notification-listener path, no official-link or can't-verify case, no sender/short-code case.
- android-e2e.yml also commits evidence to `main` with `if: always()` (contents: write), i.e. also from a failed run, so main can carry "proof" of a broken run.
- Fix: after sending, `grep -q "il.shomer" shomer-notifications.txt && grep -q <expected headline>`, `! grep` for the 2nd message, `exit 1` on crashes.txt non-empty; gate the commit on success; add 3 more messages (official link, shortener, unknown sender).

**R7 - major - Obfuscation handling is incomplete; dead code and docs disagree**
- `_SPACED` (engine.py:176) is defined and never used; review-round2 item 6 proposed spaced-letter collapsing and README implies obfuscation is handled. Repro: `ד ו א ר  י ש ר א ל https://evil.top` and `בנק ל א ו מ י https://evil.top` -> claimed_orgs `[]` (cant_verify, not dont_click).
- Niqqud: `דּוֹאַר יִשְׂרָאֵל https://evil.top` -> claimed `[]`. `_OBFUSCATION` (engine.py:175) needs a Hebrew letter on both sides of the mark, so stacked marks (shin dot + sheva, dagesh + vowel) survive. Fix: strip `[ְ-ׇ]` unconditionally, wire `_SPACED` in (both engines), add corpus cases.
- Also unhandled: `evil [.] top`, `evil dot top`, fullwidth/ideographic dots in bare domains (`israelpost。co。il@evil.top` extracts nothing -> cant_verify).

**R8 - minor - Link that cannot be parsed never escalates**
- `https://israelpost.co.il＠evil.top/x` in Python returns a LinkReport with host "" (report has url `''` in some paths) and CANT_VERIFY with a claimed org. A scam message naming a bank with a link we cannot read should be DONT_CLICK (the safe failure). Fix with R2.

**R9 - minor - Official subdomain trusted blindly / sibling-claim gaps**
- `בנק לאומי https://evil.leumi.co.il/x` -> matches_official (by design: any subdomain). Acceptable only if documented; README "Proven" list should say subdomains of official domains are trusted (dangling/takeover risk), or restrict to an allow-list of known hosts (www, online, ...).
- Same-domain-different-org: `gov.il` is listed for both `israel_police_fines` and `tax_authority` (directory.json); `_official_match` ties resolve to first org. Keep ordering explicit or dedupe.

**R10 - minor - Shortener/common lists too thin; mislabelled reason**
- Shorteners: 11 entries. Missing rb.gy, bitly.ws, s.id, v.gd, buff.ly, ift.tt, lnkd.in, wa.link, l.wl.co, bit.do, qr.codes etc. `rb.gy` with a claimed org gets dont_click ("different site"), but with no claimed org it gets the generic "we did not identify the sender", never "short link". `common_domains` contains user-content hosts (facebook.com, instagram.com, github.com, linkedin.com): a phishing page there + a claimed bank = "known site, not checked" (cant_verify) instead of a warning that the page is user-hosted.
- `.py` (Paraguay) is in `_FILE_EXT`; `evil.py/login` is silently dropped as a filename (engine.py:36, 202). Repro: `בנק לאומי evil.py` -> no link, cant_verify. Drop `py`, `js` bare-host exemption when a path follows.

**R11 - minor - Phone-number / digit handling**
- Arabic-Indic digits phone (`٠٥٠١٢٣٤٥٦٧`) - `\d` in Python matches them, Kotlin only with UNICODE_CLASSES (falls back to 0 on Android when refused, Kotlin:260-263, so Android may differ from the JVM parity run). The parity run uses full-JDK Pattern; the Android ICU path is not exercised by anything in CI except the Gradle unit test, which also runs on the JVM. Add an instrumented test or normalise digits (NFKC) before matching.

**R12 - minor - docs/index.html (GitHub Pages) copy is untrue and the demo is a lookup table**
- Page text: "בדמו הזה ההודעה נבדקת בשרת שרץ על המחשב שלכם" - on the static site there is no server and nothing is sent. Pasting any scam not among the 11 baked strings (e.g. `דואר ישראל: חבילה נעצרה https://israelpost.paliet.top/il2`) returns "demo checks only example messages" (verdict field cant_verify, applicable false). Honest, but the tab "Manual check" is a dead end for every reviewer who tries it.
- Fix: either ship the engine in JS (the Kotlin port proves it is small) or hide the paste tab on the static build and change the privacy sentence in build_demo.py. Nothing regenerates docs/index.html in CI, so it goes stale silently (currently 0 stale).

**R13 - minor - README internal contradictions**
- Status line and "How this was built" say the Android app "has not been compiled or run"; Android section says "compiles and unit tests pass in CI"; "Building the Android app" says "never compiled". README says "four of nine in the sample" - not verifiable by any test. "Proven ... punycode, IPs" is true only for scheme-prefixed URLs (R1). `eval/results.md` shows 28/28 "not reported as matches official" - the label is honest but the 28 includes cant_verify; keep.

## Corpus coverage (eval/corpus.jsonl, 47 rows)
20 scam/dont_click, 8 scam/cant_verify, 7 legit/cant_verify, 5 legit/matches_official, 7 legit/not_applicable. 12 English. Only 1 punycode, 1 `@` case. Zero cases for: RTL/bidi controls, ZW chars, Cyrillic/Greek homographs, scheme-less homographs, defanged links, IPv6/decimal IP, ports, NUL/control chars, very long text, empty text, spaced/niqqud org names, Hebrew-glued URLs, phone formats, unconfirmed domains, multi-link ordering. All 5 `matches_official` legit rows are happy-path official links; no negative-control "official link + pressure" beyond s22. The corpus is author-written (README admits). Fixes for R1-R7 need rows here so Kotlin gets them too.

## Verdict
Do not ship the "never fails open" claim yet. The architecture (link never vouches for itself, three verdicts, restricted paths, unverified downgrades) is sound and the Python/Kotlin port is remarkably close (460/460 on the shipped harness), but the shipped tests only prove the cases the author thought of: a scheme-less Cyrillic homograph of a verified bank domain gets "matches the official site" (R1), a NUL byte in the host does too (R4), and a 5 KB digit string stalls the checker for a second while 20 KB takes 18 s on a receiver with a 10 s ANR limit (R3). Parity is fixed-input, not property-based, and it already diverges on 3 adversarial inputs (R2). Nothing runs Python tests or parity in CI, and the emulator job cannot go red. Fix R1, R3, R4 and wire CI before any family install.

Missing from the test pyramid: (1) property/fuzz tests on `check()` with invariants (never raises, bounded runtime, never `matches_official` if any host char is outside `[a-z0-9.-]`, idempotent under adding invisible chars); (2) a generated differential suite Python vs Kotlin that includes the adversarial list and runs in CI; (3) a regex-performance test (time budget per signal on 50k adversarial strings); (4) a corpus of real, consented messages (the whole eval is synthetic); (5) Android instrumented tests of SmsReceiver/ShomerListener (permission denied, null body, multipart SMS, RTL notification text, main-thread time); (6) e2e assertions with expected/absent notifications; (7) snapshot test that docs/index.html and the Kotlin asset are regenerated/identical; (8) a mutation-style test that every `verified: false` org can never yield matches_official.
