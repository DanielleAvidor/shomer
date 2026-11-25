# Shomer (working title) — Product Requirements (v0.3 draft)

> **Status (one line, used everywhere):** the engine, the 11 verified directory entries, the Hebrew web demo and the tests run; the Android app builds and passes its unit tests in CI, and on an emulator in CI a scam SMS raises one alert and an ordinary message raises none (evidence in `docs/proof/`). It has **not** run on a physical phone: the first real phone blocked the install (see section 9). There is no real-user data beyond three oral interviews in one family. Items marked **[Hypothesis]** are not validated. The name is a placeholder.



## Summary (one page)

| | |
|---|---|
| **Problem** | Older adults in Israel get fake messages that impersonate institutions they trust, and many click before they ask anyone. The usual protection is a relative who happens to be available. |
| **Who** | First the older adult; second the relative who is asked today. Setup is done once by the relative, with the older adult's consent. |
| **Bet** | Copy that trusted check at the moment the message arrives: compare the link with the organization's official domain, look at what the message asks for, and warn on its own in plain Hebrew. It never says "safe". |
| **Why it can win** | It verifies against an official source instead of guessing, it is built around Israeli institutions and Hebrew, and it closes a family loop that generic scam filters do not. Not yet compared hands-on with competitors (section 8). |
| **Built and proven** | A rule engine with 11 verified organizations, a Hebrew demo, an Android app, 160 tests, and an emulator run in CI where a scam SMS raises one alert and an ordinary message none (`docs/proof/`). |
| **Not proven** | No run on a physical phone (the first one blocked the install); no real-user data beyond three interviews in one family; accuracy on real messages unknown. |
| **Biggest risk** | Distribution. Current phones may not let an app outside the Play Store read SMS (section 9). |
| **Next decision** | Prove an install route on a physical phone, then run the one-week test with five older adults, three outside the family (section 8). Continue only if the targets in section 7 are met. |

## 1. Problem

**Who suffers and how.** Older adults in Israel receive phishing SMS and emails every week that impersonate institutions they know and trust (Kvish 6, GOV.IL, the police, the post office, banks, the Home Front Command). The messages look real, so many people click first and realize afterwards. Sender names can't be trusted: the National Cyber Directorate warned about fake Kvish 6 debt messages that appeared *inside an existing GOV.IL SMS thread*.

**How big it is: not known.** There is no primary-source sizing yet, so this document makes no market-size claim. What follows is context only; source status is marked on each line and none of these figures was re-checked against the primary source by me. The builder's own families reported roughly one suspicious message per month per person (section 14), which suggests the value is in the rare costly mistake rather than daily use.
- **[Reported, secondary source]** In 2024 the Israel Police opened about 2,639 cases of suspected elder fraud, up roughly 27% year over year. The figure covers all fraud types, not only messages, and is likely an undercount because some victims don't report out of embarrassment. The Bank of Israel notes that fraudsters focus on people with low digital familiarity.
- **[Unverified]** Local press, citing a security vendor's data, reports a sharp rise (hundreds of percent) in fake messages since the start of 2025. This is a secondary source and should be verified.
- **[Assumption for Israel]** US data (FTC) shows that older adults tend to lose more money per incident, and are more exposed to government-impersonation scams than younger adults. To confirm for Israel.

**What people do today.** They ask a family member, or find out after clicking. In the builder's family, the relative who is asked then checks the message manually with an AI chatbot. This human-in-the-loop workaround works when someone is available, but it doesn't scale and fails when the person clicks before asking.

**Why now.** The old tells are no longer reliable. Broken Hebrew used to give scams away, but some recent campaigns contain no spelling errors at all, and AI makes convincing messages cheaper to produce. Platforms are starting to add scam warnings, but none are built around Israeli institutions and a family loop.

**First-hand observation [Hypothesis, n=2].** The builder's grandmother and mother often forward suspicious messages to her or click a link and only then realize it was dangerous. *Open: no single concrete incident is recorded yet (message type, what happened after the click, how it was found out); the first validation step is to collect one.*

**Core insight.** The best current protection is a trusted person ("ask my granddaughter"). The product should reproduce that trusted check at the moment the message arrives, and keep the family member informed.

## 2. Users

**Vision:** anyone can tell, safely and without help, whether a message is real.

**First target segment (beachhead): older adults in Israel**, with a family member who looks out for them. The segment is defined by the user, not by the phone: it includes iPhone and Android users. Typically less familiar with the digital world and its risks, with low tolerance for technical language and for false alarms. **[Hypothesis: to validate in conversations with older adults and their families]**

**Why this segment first:**
- They tend to lose more money per incident and are especially exposed to scams that impersonate government bodies, which is exactly what the product verifies (US data, to confirm for Israel).
- They are "extreme users" in digital confidence: a message and alert that a grandmother understands is clearer for everyone (the curb-cut effect).
- Direct access to real users from the start (design partners: the builder's grandmother and mother). Caution: relatives are polite, so success is measured by behavior, not enthusiasm.

**Expansion path:** other adults, then young adults and children (different scam types and channels, and privacy rules for minors), then more platforms. Condition for expanding: the metrics in section 7 meet their targets in the first segment.

### Primary user: the older adult

Known from the builder's family: uses Android, receives mostly SMS and email, and either forwards suspicious messages to a relative or clicks and only then realizes it was dangerous.

Working assumptions, all **[Hypothesis, to validate in interviews and observation]**:
- Age roughly 65–85. Uses the phone mainly for calls, WhatsApp and messages; new apps and settings are hard.
- Small text and long messages are difficult.
- Will not install and configure a new app on her own.
- Reacts to "urgent" messages (debt, fine, parcel) under pressure, so the alert must calm rather than alarm.
- Is reluctant to ask for help repeatedly, so as not to take other people's time.
- Trusts a familiar person more than a system.

### Secondary user: the family guardian

The relative who today answers "is this real?". Working assumptions **[Hypothesis]**:
- She installs and configures the app once, including permissions. Setup is a guardian task, not a user task.
- She wants to know when something suspicious happens, without being flooded with notifications.
- She needs to see what was sent and what the app said, so she can step in.

### Design implications

- Setup is done once by the guardian, with the older adult's consent.
- Large, short, plain-Hebrew text, with a single button.
- A calm, reassuring tone ("Don't tap the link. Here's what to do"), never alarming.
- Guardian updates: an immediate alert for a high-confidence "Don't click", and a daily summary otherwise (see section 6 for why this changed).

**Not in scope for v1:** enterprise, minors and young adults.

## 3. Positioning

**Positioning statement.** For older adults in Israel and the relatives who look out for them, who want to know whether a message is real before they tap, Shomer is a message checker that verifies the link against the official sources of the organization the message claims to come from, and takes the user straight to the real site or app. Unlike general scam detectors and spam filters, it verifies against an official source rather than guessing, speaks calm plain Hebrew, and, only if the older adult agrees, lets a relative know when it matters.

### Landscape (from desk research, not hands-on testing)

| Option | What it does | Gap relative to this product (hypothesis) |
|---|---|---|
| Bitdefender Scamio | Free chatbot: paste or screenshot a message and get a verdict. A third-party listing shows English only; Hebrew not confirmed. | General, not Israeli-institution aware; no family loop. |
| Google Messages Scam Detection | On-device, built into Google Messages, only for conversations with non-contacts. Expanded to many countries and languages in 2026; Israel/Hebrew not confirmed in the sources found. | Platform feature: no official-route action, no family loop. |
| WhatsApp Scam Alert | On-device warning for messages from non-contacts. Limited beta (Aug 2026). | WhatsApp only; not SMS or email. |
| **Phishy (Israeli developer)** | Checks links and SMS/WhatsApp messages, can scan notifications automatically, and has a **Family Mode** that alerts a relative when a scam reaches an older parent. Hebrew, free, 0 to 100 safety score. Play Store: 10+ downloads (Nov 2026). Not tested hands-on. | **Closest competitor**: the same idea of a background check plus a family alert. Shomer's difference is how it decides (official-domain directory, "can't verify" instead of a score, never "safe"), not the family loop itself. See `docs/research/market.md`. |
| Fisherman (Israeli) | Automatically filters phishing, spam and ads SMS into Junk on iPhone, tuned to Israeli message patterns. | Filters but (per its listing) doesn't explain, verify against the official site, or involve family. |
| ScanMySMS (Israeli) | Free: upload a message or email to check whether a link is malicious. Claims 99% accuracy (unverified). | Manual "paste and ask" model; no passive protection, no family loop. |
| Truecaller Scam Checker | Free web and Android tool launched in September 2026: paste a link, a phone number or a message and get checks plus community reports (TechCrunch, 2026-09-27); an Israeli outlet tested it on Hebrew Israel Post and Tax Authority lures (gadgety.co.il). Not tested hands-on by me. | Manual "paste and ask" model driven by a community database; I have not seen it check a link against the official domain of the organization the message names, or run in the background. Unverified. |
| Security suites (e.g., ESET, NordVPN Message Protection) | Anti-phishing and message protection with Hebrew interfaces. | General-purpose, not designed for older adults or the family loop. |

### Differentiators to test (hypotheses)
1. **Verify against an official source, then route to it.** A curated directory of Israeli institutions and a one-tap "open the official site or app".
2. **Independence first, family loop optional.** [Hypothesis, weakened; and the family loop is no longer unique, since Phishy offers one] All three people interviewed declined alerts about themselves (section 14), so the product leads with "the phone checks and warns me" and treats the family loop as an opt-in extra, not the core differentiator.
3. **Designed for older adults.** Large text, one button, a calm tone.

**Before claiming any difference publicly:** run the same real messages through Phishy, ScanMySMS, Fisherman and Google Messages and record how each behaves.

### Strategic note
Platform vendors are expanding built-in detection and will likely make basic detection a default. The durable value is therefore the verified official route, Israeli coverage and the family loop, not detection alone.

## 4. Jobs to be done

- **Older adult.** When I receive a message that claims to come from an institution, I want to know whether the link is really theirs, so that I can act without asking someone or taking a risk.
- **Older adult.** When something looks risky, I want one clear next step (don't tap; open the official app), so that I'm never left guessing.
- **Guardian.** When a risky message reaches someone I look after, I want to hear about it in time, so that I can step in before money moves, without reading their private messages.

### User stories and acceptance criteria (v1)

| ID | Priority | Story | Acceptance criteria |
|---|---|---|---|
| US-1 | P0 | As an older adult, I get a verdict on a message with a link. | Verdict is one of three states; shown in under 1 second on device; text is Hebrew, large; never uses the word "safe". |
| US-2 | P0 | As an older adult, I can open the official site or app in one tap. | When the organization is in the directory, the alert has a button to its official URL. |
| US-3 | P0 | As an older adult, a lookalike link is stopped. | Official domain used as a prefix/subdomain of another domain, `@` tricks, punycode, IP links and brand-token lookalikes all return "Don't click". |
| US-4 | P0 | As the system, I never reassure on weak evidence. | A link may only return "matches official" if: its directory entry is `verified` with a source; the message itself names that same organization (a link never vouches for itself); a shared domain such as gov.il matches only on a listed path; and the message does not also ask the reader to phone a number. |
| US-5 | P2 (was P0) | As a guardian, I'm told when a high-confidence risky message arrives, **only if the protected person agreed**. | A "Don't click" verdict creates an alert only after on-screen consent. Demoted from P0 because the only evidence (three interviews, one family) shows people decline alerts about themselves. Built, but not part of the core promise. |
| US-6 | P0 | As an older adult, my messages stay private. | Message text is never stored or sent by default; only verdict, organization id and link host leave the phone, and only if an HTTPS server address was configured with consent. The optional AI second opinion (section 13) is off by default and, if switched on, sends the message text to an outside service; it is not built. |
| US-7 | P1 | As an older adult, I can share any message (WhatsApp, email) to the app to check it. | Android share target accepts plain text and shows the same verdict. |
| US-8 | P1 | As a guardian, I can see what happened over time. | Family screen shows counts and recent events without message text. |
| US-9 | P2 | As a guardian, I can add or correct a directory entry. | Not built; directory is edited by hand in v1. |
| US-10 | P1 | As an older adult, I'm warned about scams that have no link. | Messages that pressure, threaten, ask for card details or codes, or impersonate a relative asking for money are flagged by content signals (defined in `directory.json`, shared by the Python and Kotlin engines). A weak word alone (for example "gift") never triggers a warning. |
| US-11 | P1 | As an older adult who already clicked, I know what to do. | Every risky result has a "what if I already clicked" section: stop entering details, block the card, change passwords, tell the family and report to 119. |
| US-12 | P1 | As an older adult, I can pass a result to a relative. | One tap opens WhatsApp with a short summary (verdict and address, never the message text). |

### Edge cases handled or tracked
- No link: judged by content signals. Pressure plus a threat, a request for card details or codes, or a relative asking for money returns "don't act"; a named organization with nothing else returns "can't verify". Keyword-based, so unusual wording can be missed.
- Link to an app store (Google Play, App Store): neutral, reported as "can't verify", never a match and never a false alarm.
- Official domain but the message names no organization, or names one not in the directory: "can't verify".
- Message with an official link and a phone number to call: downgraded to "can't verify".
- Shortened link: always "can't verify" (the destination can't be seen without following it, which would expose the user).
- Two links in one message: the worst verdict wins.
- Two organizations named: both are detected; a link must belong to one of them.
- Link to a different official organization than the one named: "can't verify" (not "don't click"), because shared domains such as gov.il exist.
- Message with no link and no known organization (e.g., a one-time code): not applicable, no alert.

## 5. Scope

### Version 1 (in)
- Android app, installed directly for family use (not published on the Play Store in v1). **Why Android first:** it allows the deeper integration the core experience needs (reading SMS and notifications with consent). iPhone users are part of the target segment and are planned for after v1 through lighter paths (share-to-app, SMS filtering).
- Reads incoming SMS (with explicit consent) and email via the Gmail API in test mode **[to verify feasibility]**.
- Extracts links and the claimed organization from each message.
- **Trusted Directory:** a curated list of the most-impersonated Israeli organizations (9 in the prototype, target ~15), with their official domains and the correct way to check or pay. The list is prioritized from public warnings.
- Domain checks: exact match against the directory, lookalike detection (typos, wrong suffix, extra words), and basic reputation signals.
- Alert in large-text, plain Hebrew, with a button that opens the official site or app directly.
- Notification to the family guardian.
- Language rule: the app **never says "safe"**. It uses three states: "Don't click (mismatch/lookalike)", "Link matches the official site, but it's still safest to open the app yourself", and "I can't verify this, don't click, ask [guardian]".
- Privacy by design (target for the Android app; the web demo sends text to a local server that does not store it); only the minimum (link, claimed organization) is sent to a server; full messages aren't stored by default; consent screens in Hebrew.

### Version 1 (out, deliberately)
- iPhone support (planned after v1), WhatsApp monitoring, phone-call scams.
- Play Store release.
- Training a custom ML classifier.
- Payments, accounts, multi-family management.

## 6. Decision logic (v1, as implemented)

For each link, in this order:
1. Raw IP address → **Don't click**.
2. Host (the real destination after removing `user@` tricks) equals or is a subdomain of an official domain (longest match wins): **matches official** only if the entry is verified, the message names the same organization (organization names are searched outside links only), and any path restriction is met; otherwise **can't verify**. A message that also asks to call a phone number is downgraded to **can't verify**.
3. Known URL shortener → **can't verify**.
4. Lookalike (brand token starting a part of a foreign host, e.g. `israelpost.x.top` but not `storefront.com`; or a domain within edit distance 2 of an official one; or punycode) → **Don't click**.
5. The message names an organization but the link goes elsewhere → **Don't click**.
6. Otherwise → **can't verify**.

Content signals (outside links) add a score: family impersonation 3, request for card details or codes 3, threat 2, prize 2, urgency 1, payment 1, plus 1 for a named organization together with a phone number. A match is downgraded to "can't verify" if the message asks for secrets or applies two kinds of pressure. An unverifiable link with a score of 3 or more becomes "Don't click". With no link at all, a score of 3 or more is "don't act", and anything else that names an organization is "can't verify".

Message level: any "Don't click" wins; "matches official" only if every link matches; otherwise "can't verify". Messages with no link and no known organization are not applicable.

**Guardian alert rule (changed from the first draft).** The first draft alerted only when the user taps a risky link. Android cannot observe taps inside other apps, so v1 alerts immediately on a high-confidence "Don't click" and puts everything else in the summary. Tap-based alerts remain possible only for links opened from inside the app.

**Known limitations (to state in the README):**
- A matching domain is not proof the message is genuine (compromised accounts, scams without links, phone-based scams).
- The directory needs maintenance, and organizations may use several domains.
- Messages without links, or asking the user to call or reply, need a separate approach.

## 7. Success metrics

Targets are proposals to agree on, set before measuring so the result can't be rationalized afterwards.

**Quality (on a test set of at least 50 real, consented Israeli messages, half scam and half legitimate):**
- No scam reported as "matches official": **0** (hard requirement).
- Scams reported as "Don't click" or "can't verify": **≥ 95%**.
- Legitimate messages wrongly told "Don't click": **≤ 2%**.
- **Hard rule:** an official domain plus any request for payment, personal details or a call is never "matches official" (enforced in the engine since the third review round).
- Time from message arrival to verdict: **< 1 s** on the family's phones.

**Behavior (with real users, 4 weeks):**
- Risky clicks per month: **down by at least half** versus the 4 weeks before (self-report plus guardian log).
- Manual "is this real?" questions to the guardian: **down by at least a third**.
- Comprehension: **at least 4 of 5** older adults can explain in their own words what to do after a "Don't click" alert.

**Current status (synthetic corpus, `python eval/run_eval.py`):** 55 messages written by the author, modeled on published examples (36 scams, 19 legitimate, 12 of those with a link or a named organization). Scams wrongly reported as "matches official": **0/36**. Scams hard-blocked with "Don't click": **25/36**; the other 11 are "can't verify". With the SMS alert policy, **34/36** scams raise an alert in SMS and **17/36** in a chat app (the chat policy is deliberately stricter). Legitimate messages told "Don't click": **0/12**; told "can't verify": **7/12** (friction); confirmed as matching the official site: **5/12**; **4/19** legitimate messages still raise an SMS alert. These numbers are a regression guard, **not** the quality measurement above: the corpus was written by the same person who wrote the rules, and 0 misses in 36 scams is still consistent with a true miss rate of roughly 8%. "0/36 matches official" counts "can't verify" as a pass, so it is not a catch rate.

## 8. Validation plan

1. Collect 10–20 real suspicious messages from the family (with consent, redacting personal details).
2. Short conversations with 5 older adults and 5 adult children: how do they decide today, what do they do after a click, what wording would they trust.
3. Test the alert text and design with a real user for comprehension (large text, Hebrew).
4. Document what changed in the product because of what was learned.
5. Hands-on comparison: run the same 20 messages through ScanMySMS, Fisherman, Truecaller Scam Checker, Google Messages and this prototype, and record verdicts side by side.
6. **Riskiest assumption, one-week test (not run).** The assumption: an older adult who has never seen the product can install it and understand an alert on her own. Test: 5 older adults, **3 of them outside the builder's family**, each installs the app alone (timed) and receives one fake scam SMS; ask her to say in her own words what to do. Fail if fewer than 3 of 5 explain it correctly, or if 4 of 5 cannot install it without help. Until this is done, "validated" is not claimed anywhere.

**Decision criterion.** Continue investing only if the quality targets in section 7 are met on the real message set **and** at least 3 of 5 older adults understand the alert. If the quality targets pass but comprehension fails, redesign the alert before anything else. If real messages show most scams have no link (phone numbers, "reply to this number"), pivot the next version toward those before expanding platforms.

## 9. Risks and open questions

- **False alarms and false reassurance.** Too many alerts cause fatigue, and a wrong "fine" can cost money. Mitigation: conservative language, three states, escalation.
- **Distribution is blocked on real phones (observed, 2026-11-22).** The first real phone tried (a Google phone, Android with Play Protect) refused the sideloaded APK with "App blocked to protect your device: this app can request access to sensitive data", and the install still failed after Play Protect app scanning was switched off. An app that reads SMS or notifications and is not from the Play Store may not be installable on current phones at all. Google Play restricts SMS permissions to default SMS apps, with a possible exception for anti-smishing apps that is subject to Play review (`docs/research/market.md`, section 3), and from 2027 Google will require developer verification even for apps installed outside the Play Store. A public release therefore needs another route (for example the Share-to-Shomer and paste flow, a Play-approved notification-listener declaration, or a family build installed over a cable with `adb`). Until a route is proven on a physical phone, v1 may reduce to paste and share. On an emulator the background alert works (`docs/proof/`).
- **Gmail access** for a non-published app may be limited to test users **[verify]**.
- **Privacy and consent.** Messages are personal data. Needs a clear consent model and data minimization.
- **Platform competition.** Google and WhatsApp are building scam alerts. Differentiate on Israel-specific coverage, Hebrew, the family loop, and the official-route action.
- **Israeli and Hebrew-capable competitors exist** (e.g., Fisherman, ScanMySMS). A hands-on comparison on real messages is needed before claiming a difference.

## 10. Roadmap (beyond v1)

- Phone-call and "reply to this number" scam handling.
- WhatsApp via forward-to-bot.
- A web dashboard for the family guardian.
- Community reporting of new campaigns into the directory.
- Integration with official reporting channels (e.g., the 119 hotline).

## 11. Data sources

Market and competitor research (2026-11-25), with sources and how solid each figure is: `docs/research/market.md`.


- Public warnings from Israel's National Cyber Directorate and the police (as reported by Israeli media).
- Official websites and apps of the organizations in the directory. Each entry carries a `verified` flag and a check date in `shomer/data/directory.json`. **Limitation:** the entries do not yet carry a per-entry source URL, so a reader cannot re-check them independently; adding them is the next data task. A directory test checks that every verified entry has a check date and that the Python and Android copies are identical.
- Consented example messages from users (not yet collected).

## 12. Build status (prototype)

**Second round.** After the redesign (stamp-style result, new signals, family and setup screens) three agents ran again: the simulated older adult, the simulated young user, and an adversarial tester for the new content rules (reports in the review log folder when available). Results are summarized in the README.

**Review log.** Five review agents (designer, hiring-manager, product critic, simulated older adult, simulated young user) ran against the repo. Changes made because of them: links no longer vouch for their own organization; shared gov.il restricted to a path; brand matching no longer fires inside unrelated words; app-store links neutral; phone numbers downgrade a match; a match is shown in calm blue with a link icon instead of a green check (a simulated older adult read green as "safe"); the recommended action now comes first; plainer Hebrew ("לא הצלחנו לבדוק" instead of "אי אפשר לאמת"); paste instructions; no external font request. Open items from the reviews: the problem statistics need source links; 5 real conversations and a real incident are still missing; the simulated users' findings need confirmation with real older adults.


| Part | Status |
|---|---|
| Verification engine (Python) | Built, 160 tests passing (`python -m pytest`), run in CI on every push. Checks link destination and message content (urgency, threats, requests for card details or codes, prizes, payment, family impersonation). Hardened after the third review: open redirects and `..` paths on official domains, odd characters in hosts, IPv6, Cyrillic lookalikes in bare domains, letters spread with spaces, fullwidth letters, slow patterns, very long text |
| Trusted Directory | 11 of 16 entries verified: Israel Post, Carmel Tunnels, the police fines service (one gov.il path), Bank Leumi, Bank Hapoalim, Discount, Mizrahi-Tefahot, National Insurance, Israel Electric Corporation, Clalit, Maccabi. Unverified (kept so lookalikes can be caught): Kvish 6, Bit, PayPal, Tax Authority, Home Front Command |
| Web demo (Hebrew, RTL) | Built and exercised in a browser at phone and desktop widths. A static copy for GitHub Pages is built from the real engine (`tools/build_demo.py`); it can check only the example messages |
| Android app (Kotlin) | Builds and passes unit tests in CI; the Kotlin engine matches the Python engine on 490 messages (`tools/kotlin-parity`); on an Android 13 emulator in CI one scam SMS raises one alert and an ordinary one none (`docs/proof/`). Running it on the emulator found two crashes that unit tests could not see (Android's regex differs from the JVM's). **Not run on a physical phone** |
| Real-message evaluation, interviews, user comprehension test | Not started beyond three oral interviews in one family; see section 8 |

Open technical questions for the Android build: whether any installable route exists on current phones (section 9); behavior of Android's "restricted settings" for sideloaded apps; Gmail API access for a non-published app.


## 13. Roadmap: optional AI second opinion (groundwork built, feature not built)

The rule engine stays the safety floor. A later version can add an "advisor", for example a language model, that reads the message and the engine's result and can add a plain-language explanation and a context question ("were you expecting a package?"). It can also catch scams worded in ways the rules do not know.

Decisions already made and built into the code (`shomer/advisor.py`):
- **Escalate-only.** An advisor can make a result more cautious, never less. It cannot turn "don't click" or "can't verify" into "matches official". This is covered by tests.
- **Opt-in.** The message text would leave the machine, so the server calls an advisor only when the protected person has turned on `external_ai` in settings.
- **Safe on failure.** If the advisor is missing, slow or fails, the rule engine's answer is returned unchanged.
- **Nothing says "safe".** The existing rule still applies to any advisor text.

Not built: any real advisor. Testing one needs an API key and network access, and accuracy would need its own evaluation against real messages.


## 14. First user research (pilot oral questionnaires, three people in one family)

On 2026-11-21 one older woman (the builder's grandmother, Android, SMS and WhatsApp) answered a short oral questionnaire about suspicious messages, read aloud by the builder. Full notes and limits: `docs/research/interview-01-grandmother.md`.

What it changes in this document (hypotheses to test, based on n=1):
- **Positioning.** The strongest signal is a wish for independence. She declined automatic alerts to family and wants the phone to check and warn her. The family screen should become optional, with sharing only when she chooses. The earlier "family guardian" framing needs to be revisited after more interviews.
- **Real behaviour.** She already asks a relative, who checks with an AI chat. Competitors are "ask family" and general chatbots.
- **Warning copy.** She preferred a warning that names the organisation and says what to do: "אל תלחצי על הקישור. הוא לא מוביל לאתר של דואר ישראל."
- **Pop-up.** She wants the warning as a pop-up on the screen.
- **Context.** What protected her was knowing she had no parcel. A context question is a candidate feature (see section 13).

Not yet validated: any of the above with a second person, the mother, or someone outside the family.


### Update after two more conversations (mother and builder)
Notes: `docs/research/questionnaire-02-mother.md`, `docs/research/self-report-03-builder.md`. All from one family, so they are not independent.

- **Pattern repeats.** Both older women received the same two scams (Road 6 and Israel Post), asked family, and then asked an AI chat. The builder receives one about every one to two months and checks it herself by comparing addresses and signing in to the real site.
- **Frequency is low.** Roughly one suspicious message per month per person. The value is catching the rare costly mistake, not daily convenience.
- **Both older women want an immediate warning** from the phone saying the link does not lead to the real site, and they want to paste or share a message to check it. The mother has an iPhone, so the Android SMS approach does not reach her; paste and share do.
- **Family alerts:** neither wants alerts about herself. The mother wants alerts about her own mother, only for certain danger, and the grandmother agreed aloud. Built as: alerts only after on-screen consent and only for high-confidence results (`certain`).

### Changes made because of this
- A link can be checked on its own: the real address is shown next to the official address.
- A context question per organisation ("were you expecting a parcel from Israel Post?") with a short answer for yes and for no. It never calls anything safe.
- The main button now reads "sign in to the official site's personal area" so the person verifies at the source.
- Family alerts are limited to high-confidence scams.

### Still open
Whether the app should check automatically on Android (needs the Android build), an iPhone share extension, deeper links into each organisation's personal area (needs verified official pages), and testing with people outside the family.

## 15. Languages

**Decision.** The interface and explanations are in Hebrew, because the people we spoke to read Hebrew. The *input* is not limited to Hebrew: a message may arrive in English, and the product is expected to work on it.

**What is language-independent (works for any message).** Extracting the link, finding the real host, comparing it to the official domain, lookalikes, punycode, IP addresses, link shorteners, risky domain endings.

**What was added for English.** English wording for each signal (urgency, threats, requests for codes or card details, prizes, payment demands, a family member asking for money, a bank or authority representative) and English names of the organizations in the directory ("Israel Post", "Highway 6", "National Insurance" and so on). Eleven English cases were added to the evaluation set, including five ordinary messages that must *not* trigger anything.

**Honest limits.**
- The English patterns and the English test cases were written by the same person, so they show the logic works, not how well it works on real English scam messages.
- Russian and Arabic, widely used among older adults in Israel, are not covered. That is the next language step, and it needs native speakers to write the patterns and review them.
- The result screen is still Hebrew. An English-speaking user would get a correct verdict with Hebrew explanations.

## 16. QA round on the interface

A tester agent (role-play, not real users) ran the web demo at three screen sizes and reported 4 major and about 15 smaller findings. Fixed: Russian and Arabic text was shown as "nothing suspicious" and now says the language is not supported; on small phones the result could be off-screen for harmless messages and empty input; with several links only the first host was shown, now the dangerous one; when the server was down the screen stayed silent, now it says so and the button shows it is working; the main button text was clipped at 360px; the selected yes/no button was barely visible; input border contrast raised from 2.8:1 to 4.0:1; Ctrl+Enter submits. The headline for a message with nothing to check now reads as "could not check" with the caveat inside it.

**Still open.** On a dangerous result the main action button is near the bottom of a long page (about 2,400px on a small phone); moving it up is a layout decision to test with real users. Tabs have no arrow-key support. The saved image has empty space. Pasting always runs the check at once. Not tested: real clipboard, WhatsApp, screen readers, Safari/Firefox, the Android app.

## 17. The background agent

**Why.** In the interviews the person did not want a tool she has to remember to open: "the interface should replace help from family; agents that alert, so she feels safe." Pasting a message remains useful, but it only works after she is already suspicious. The agent closes that gap.

**What it is.** A deterministic background service, not an AI model: the same on-device engine, run on every incoming message from the watched channels. This keeps it private (nothing leaves the phone), predictable (same input, same answer), and free to run.

**Channels.** SMS (receiver) and, through notification access, WhatsApp, Telegram, Gmail and Messenger. Not possible on iPhone.

**Alert policy (a product decision, not a technical one).** The cost of a false alarm is that people stop trusting the app, so the agent is quiet by default: for SMS it alerts on "don't click", and on "can't verify" when a known organization is named and the message has a link or pushes, or when a link to an unknown site comes with a threat, a request for secrets, a prize, an install request or a payment; in chat apps only on a high-confidence "don't click" that also pushes or imitates a known organization, because people share links and mention banks there all the time (a deliberate trade of some misses for trust). It never alerts on "matches official" and never reassures. Family alerts stay opt-in and high-confidence only.

**Risks and open questions.**
- The permission is broad (all notifications) even though only a few apps are read. The consent text must say so, and the app must show plainly whether it is on.
- Tested on an emulator in CI, not on a physical phone. Notification previews may truncate long messages.
- Distribution: the Play Store limits both SMS and notification access, so this is a family install for now.
- Success measure to define with real users: alerts per week per person, share of alerts judged useful, and whether she stops forwarding messages to family.

## 18. The agent-driven demo

The demo's first screen is a simulated phone lock screen, not a text box and not an app screen. Sample messages arrive on their own (about one every four seconds). Shomer checks each through the same alert policy as section 17. A quiet message just sits there as the ordinary notification it is; a risky one gets a red Shomer notification right under it, which is what the Android app does (a high-priority notification). Nothing has to be opened to see the warning; tapping it opens the full explanation. Pasting remains as a second, manual tab. This follows the interview feedback that a tool she must remember to open does not replace help from family.

What it shows and what it does not: the nine sample messages were written by me and Claude to cover the cases (a friend's link, a lookalike post office, a family group, an official message, a "hi grandma" request, a bank code, a toll lookalike, a family reminder, a bank phone number). Four alert and five stay quiet. That shows the policy, not its accuracy on real traffic, and the lock-screen notification in the demo is a drawing; the real notification is shown by the emulator evidence in `docs/proof/`, which is not a physical phone.

## 19. How this was built and what was cut

Direction, research and product rules are mine; Claude wrote the code and first drafts. Simulated-user reviews found real bugs but are not user research. The only user evidence is three oral interviews in one family. What I cut on purpose: AI-decided verdicts, an agent that signs in to accounts, iPhone background checking, full WhatsApp reading, any "safe" badge, and more directory entries than I could verify. See the README for the short version.


## 20. Threat model

**What is protected.** The older adult's money and credentials; her trust in the alert (an alert that cries wolf is switched off, an "all clear" that is wrong is worse); the privacy of her messages.

**Adversary.** A scammer who sends SMS or chat messages at scale, impersonating an organization or a relative, and who can read this project's public rules and test messages against them. Not in scope: malware on the phone, a compromised official site, targeted attacks on one person.

**Trust boundaries.** (1) The message text is untrusted input to the engine. (2) `directory.json` is the only source of "official" and is trusted: a wrong entry would make a scam "match official". Entries are changed by hand in a reviewed commit; there is no automatic update, no signing, and no per-entry source URL yet. (3) The optional family server receives only verdict, organization id and link host over HTTPS; the local demo server accepts only `localhost` Host and Origin headers.

**Evasions tried in the third review, and the status.**

| Evasion | Status |
|---|---|
| Lookalike domains, subdomain prefixes, `@` tricks, punycode, IP links, backslash hosts | Caught (tests) |
| Open redirect on an official domain (`?url=https://evil`) | Now "can't verify", never a match |
| `..` or encoded `%2f` in a restricted path | Path is normalized before it is compared |
| NUL, control or invisible characters in a host; ideographic dots; IPv6 literals | Now "don't click" |
| Cyrillic letter in a scheme-less domain | Now "don't click" |
| Letters spread with spaces; fullwidth and math letters | Undone before matching |
| Very long or pathological text (quadratic regexes found: up to 18 s on 32,000 characters) | Patterns fixed; input capped at 20,000 characters; a test sets a one-second budget |
| A scam that names no organization | SMS alerts if it has a link and a push; chat stays quiet by design |
| Scams with no link (callback numbers, QR codes) | Partly caught by content signals; not reliably. Open |
| Redirectors and shorteners | Shorteners are "can't verify"; the final destination is not followed (no network in the engine). Open |
| A scam that mentions an organization Shomer does not know | Not caught as a lookalike; "can't verify" only. Open |
| Homoglyph leetspeak (`Leum1`) | Open |

**What the product never does:** say a message is safe. An official match is shown as "the address is official, this is not confirmation of the message"; a message with nothing to check is shown as "could not check".

## 21. Review rounds

Round 3 (2026-11-22): five role-play reviewers (QA engineer, product critic, hiring manager, security engineer, designer) wrote 72 rejects between them; the reports are in `docs/reviews/`. Role-play reviewers are a way to find bugs and weak claims quickly; they are **not** user research. What changed because of them is listed in the commit history and in sections 6 to 12, 17 and 20. The rejects that need real people (research, distribution on a physical phone) are not closed.
