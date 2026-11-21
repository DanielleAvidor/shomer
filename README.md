# Shomer — a scam checker for families (working title)

A message arrives: *"Israel Post: your parcel is on hold, confirm your address"* with a link. Is it real?

Shomer answers that question for older adults in Israel, in plain Hebrew, and keeps a family member in the loop. It doesn't guess with a generic classifier. It **checks the link against the official site of the organization the message claims to be from**, and if they don't match it says so and offers the real site instead.

I started this because my grandmother and mother get these messages all the time and often click first. Today the "protection" is me, checking messages by hand. This project tries to turn that into a product.

> **Status: prototype.** The engine, directory, Hebrew web demo and tests run. The Android app is written but **has not been compiled or run yet**. There is no real-user data yet. See [What is and isn't proven](#what-is-and-isnt-proven).

## What it does

It looks at two things: **where the link really goes**, and **what the message asks for** (rushing you, threatening a block or fine, asking for card details or codes, promising a prize, pretending to be a family member who needs money). That second part lets it catch scams that have no link at all. For each message it returns one of three verdicts, and **never says a link is "safe"**:

| Verdict | Meaning |
|---|---|
| **Don't click** | Mismatch or lookalike (e.g. `israelpost.co.il.pay-now.top`, `@` tricks, punycode, raw IPs, brand lookalikes) |
| **Matches the official site** | Still advises opening the app or site yourself |
| **Can't verify** | Unknown organization, shortened link, or an entry not yet verified: don't click, ask the family |

Design choices that matter:
- **Trusted Directory with provenance.** Each organization has its official domains, a source, and a `verified` flag. Only verified entries can ever produce "matches official". Unverified ones degrade to "can't verify" rather than guessing.
- **Family loop (demo level).** In this demo the "family screen" is a second tab on the same local server, not a message to another person's phone, and the server listens only on your own machine. What exists is the data model and the screen: alerts, "handled", and a WhatsApp share that carries only the verdict and the address. Real delivery to another phone needs a hosted backend and sign-in, which is not built. Events are recorded only after the protected person has ticked the consent box. A "Don't click" alert means the message was flagged, not that anyone tapped the link.
- **Privacy by design.** The server never stores the message text (checked by a test). In the web demo the text is sent to the local demo server for analysis, so "analysis on the device" is a goal for the Android app only, and that app has not been compiled yet. Only verdict, organization id and link host are ever recorded.
- **A result that explains itself.** A stamp-style card shows the real destination of the link (the part that actually owns the address is highlighted), what was found, numbered steps for what to do now, and a "what if I already clicked?" section (stop entering details, block the card, change passwords, report to the national cyber hotline 119).
- **Family features.** Send the result to a family member on WhatsApp (verdict and address only, never the message), save the result as a picture, a family screen with alerts you can mark as handled, and a one-time setup with an explicit consent screen.
- **Calm, large UI** for older adults and families: RTL Hebrew, embedded fonts (Open Sans and Cardo, both open license), no external requests.

## The demo

The first screen simulates a phone lock screen, the way the Android agent behaves: sample messages arrive on their own, Shomer checks each one without being opened, and only a risky message gets a red Shomer notification under it (four of nine in the sample). Tapping that notification opens the explanation. "Manual check" is a second tab for pasting a message. The messages are made-up samples, labeled as such on screen.

## App mockup

A clickable six-screen model of the Android app (setup, home, lock-screen alert, explanation, family, settings) is in a Claude design canvas: https://claude.ai/artifact/7odGaYVHJKEKzDRDabYC7o. Home, family and the detailed settings screen are proposals; the code today has the status screen, the permissions flow and manual check. The numbers in it are sample data.

## Try it

```bash
python -m shomer.server        # then open http://localhost:8000
python -m pytest
python eval/run_eval.py        # regression summary (see caveat below)
```

Standard library only; Python 3.10+. In the page, use the example buttons, then the second tab to see what the family would see.

## Repository map

| Path | What |
|---|---|
| `docs/PRD.md` | Product requirements: problem, users, positioning, scope, decision logic, metrics, validation plan, risks |
| `shomer/engine.py` | Verification engine |
| `shomer/data/directory.json` | Trusted Directory (with sources and `verified` flags) |
| `shomer/server.py`, `shomer/static/index.html` | Local demo server and Hebrew UI |
| `eval/` | Test corpus and evaluation script |
| `app/` | Android app (Kotlin): background agent (SMS receiver + notification listener for WhatsApp and others), share target, alerts, family reporting |

## How this was built

I set the product direction and ran the research; Claude (an AI assistant) wrote the code and drafts under my direction, and I reviewed and changed them.

- **Mine:** picking the problem and the user (an older relative who gets scam messages), the three interviews, and the rules the product holds to: never say "safe", always point to the official route, speak up only when there is a reason, keep the family alert opt-in. I also rejected an AI agent that signs into people's accounts, and asked for the background agent and English support after seeing early versions.
- **Claude's:** the rule engine, the web demo, the Android code, the tests and first drafts of the docs.
- **Simulated users are not user research.** The agent reviews (a designer, a hiring-manager reviewer, a product critic, nine simulated users) are role-play by an AI. They found real bugs, which is what I used them for, but they say nothing about what real people do. The only user evidence is three oral interviews with people in one family.
- **Not verified yet:** the Android app has never been compiled or run on a phone, and 11 of the 16 directory entries are verified, each against the organization's own site or its own published warning (see `source` in `directory.json`). Kvish 6, Bit, PayPal, the Tax Authority and the Home Front Command are not.

## What I cut, and why

- **An AI model deciding verdicts.** A rule engine gives the same answer for the same input and can be explained to the person reading it. An AI second opinion is groundwork only (see below).
- **An agent that signs in to accounts to confirm a message.** Too much access for the risk it removes.
- **iPhone background checking.** iOS does not let an app read SMS or WhatsApp. Paste and share only.
- **Reading WhatsApp messages in full.** Notifications only, and only a short list of apps.
- **A score or "safe" badge.** It would invite trust the tool has not earned.
- **Unverified organizations as "matching".** An organization is only ever reported as matching once its domain is confirmed from its own site. Related domains that are probably but not provably the organization's (for example the credit-card arm of a bank) are listed as `unconfirmed_domains` and report "can't verify", never a false alarm.

## What is and isn't proven

**Proven (by tests in this repo):** the engine handles lookalike domains, subdomain tricks, `@` tricks, punycode, IPs, shorteners, multiple links and organizations. A link can only be reported as matching if the message itself names the same organization (a link never vouches for itself), the domain is verified, shared government domains match only on a specific path, and the message does not also ask the reader to phone a number. Unverified directory entries never produce a reassuring verdict. Message text is not persisted by the server. The Hebrew UI works end to end in a browser.

**Not proven:**
- **Accuracy on real messages.** The evaluation corpus is written by me, modeled on published warnings. It is also partly tautological: several cases were added after an adversarial review, so they test fixes, not unseen data. It is a regression check, not an accuracy estimate. The PRD sets numeric targets to measure on at least 50 real, consented messages.
- **Android.** The Kotlin source has not been built. Open questions: whether a sideloaded app keeps SMS-receive permission, Android's restricted-settings behavior, Gmail API access for a non-published app.
- **That users understand the alerts.** Needs testing with real older adults.
- **Directory coverage.** 11 of 16 entries are verified (checked 2026-11-21, mostly from the organization's own pages; IEC from the company's statements as quoted in the press). Five are still `TO VERIFY`, including Kvish 6, whose own site I could not open from here.
- **Statistics in the PRD** (elder-fraud case counts, growth rates) come from press coverage; the original source links still need to be added.
- **"Analysis on the device"** is a design goal for the Android app only. In the web demo the text is sent to the local demo server (running on your own machine) and is not stored.

**Known limits (by design, for now):** content signals are keyword patterns, so they miss unusual wording and could flag an odd legitimate message (see the review log); scams that depend on a phone call or a screenshot are only partly covered; a matching domain is not proof a message is genuine (compromised accounts); iPhone cannot read other apps' messages, so iPhone support would rely on share-to-check; WhatsApp is end-to-end encrypted and out of scope for v1.

## Existing options (desk research, not hands-on)

General AI checkers (e.g. Bitdefender Scamio), platform detection (Google Messages, WhatsApp Scam Alert, both limited to non-contacts), and Israeli tools (Fisherman SMS filter for iPhone, ScanMySMS upload checker). The bet here is not better detection but a verified official route, Israeli coverage, and the family loop. A hands-on comparison on real messages is a planned validation step before claiming any difference.

## How this was reviewed

I ran five review agents against the repo (definitions in `.claude/agents/`): a designer, a hiring-manager reviewer, a product critic, and two simulated users (an older adult and a design-literate 23-year-old). Their findings changed the product: an adversarial pass found scams that were wrongly confirmed, which led to the rules above; the older-adult simulation found that a green check reads as "safe", so a matching link now gets a calm blue link icon and the action comes first. The simulated users are not real users and their findings still need confirming with real people.

## Next steps

1. Verify the remaining directory entries against official sources.
2. Build and run the Android app; answer the permission questions above.
3. Collect 10–20 real messages from my family (consented, redacted), and interview older adults and their children.
4. Measure against the PRD targets, and write down what changed because of what I learned.


## Building the Android app (first time)
The Kotlin source in `app/` has been statically reviewed but **never compiled or run**. To try it:
1. Install Android Studio, then File > Open and choose the `app` folder (the one with `settings.gradle.kts`).
2. Wait for the Gradle sync and accept the SDK download.
3. Right-click `ShomerEngineTest` and Run. It checks the Kotlin engine against the same `corpus.jsonl` as the Python tests. A failure prints the message id.

## User research survey
`survey/index.html` is a short Hebrew survey (about 3 minutes, 13 questions) for older adults and their relatives: what suspicious messages they get, what they did, and whether they would paste a message into an app or let a relative see alerts. It stores and sends nothing by itself. At the end it builds a text summary that the respondent can send on WhatsApp. It can be opened as a file or hosted on GitHub Pages. Pilot conversations so far (three people, one family, limits stated): `docs/research/`. Real answers belong in `docs/research/` (anonymised) and should be summarised in the PRD.

To collect answers from people who are not on WhatsApp with you, `survey/create-google-form.gs` is a Google Apps Script that creates the same survey as a Google Form with a linked response sheet (instructions in the file header).

## The background agent (Android)

Pasting a message is the first step, not the product. The app also runs in the background and checks incoming messages by itself, then speaks up only when there is a reason:

- **SMS**: through a broadcast receiver (runtime consent).
- **WhatsApp, Telegram, Gmail, Messenger**: through a notification listener, which the user grants in system settings. It reads only notifications from a short list of apps, ignores its own, and checks them on the phone. Message text is not stored or sent; a short-lived hash in memory prevents announcing the same message twice.
- **When it alerts**: for SMS, on "don't click", and on "can't verify" only if the message names a known organization and has a link or pushes. For chat apps the bar is higher (people share links and mention banks all the time): only a high-confidence "don't click" that also pushes or links to a lookalike of a known organization. Never on "matches official", never on a message with nothing to check. This trades some misses in chats for trust.
- **Limits**: not compiled or run on a device yet. A notification preview can be cut short, so a long message may be checked in part. The notification permission covers all notifications, which is why the consent text says so plainly. A Play Store release would need a different route; this is built for a family install. On Android 13 and later, a sideloaded app may need "Allow restricted settings" (in the app's info page) before notification access can be switched on. **iPhone cannot do this at all**: iOS does not let an app read SMS or WhatsApp. Only a paste/share route (and, with a Mac and a developer account, an SMS filter extension) is possible there.
- **Not built**: an AI agent that signs into the person's accounts to confirm a message. It was rejected on security grounds (see the PRD).

## Future: AI second opinion
`shomer/advisor.py` is an extension point for an optional AI advisor. It is deliberately limited: it can only make a verdict more cautious, it runs only if the user turns it on, and failures fall back to the rule engine. No real advisor is included yet. See section 13 of `docs/PRD.md`.

## Checking a link on its own
You can paste just a link. The result shows the address the link really opens, who owns it, and the official address of the organisation it claims or imitates, side by side. A link that is the verified official address of a known organisation is reported as the official address, with the reminder that this does not mean the message that carried it is genuine.
