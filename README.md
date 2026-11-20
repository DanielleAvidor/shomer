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
| `app/` | Android app (Kotlin): SMS receiver, share target, notification, family reporting |

## What is and isn't proven

**Proven (by tests in this repo):** the engine handles lookalike domains, subdomain tricks, `@` tricks, punycode, IPs, shorteners, multiple links and organizations. A link can only be reported as matching if the message itself names the same organization (a link never vouches for itself), the domain is verified, shared government domains match only on a specific path, and the message does not also ask the reader to phone a number. Unverified directory entries never produce a reassuring verdict. Message text is not persisted by the server. The Hebrew UI works end to end in a browser.

**Not proven:**
- **Accuracy on real messages.** The evaluation corpus is written by me, modeled on published warnings. It is also partly tautological: several cases were added after an adversarial review, so they test fixes, not unseen data. It is a regression check, not an accuracy estimate. The PRD sets numeric targets to measure on at least 50 real, consented messages.
- **Android.** The Kotlin source has not been built. Open questions: whether a sideloaded app keeps SMS-receive permission, Android's restricted-settings behavior, Gmail API access for a non-published app.
- **That users understand the alerts.** Needs testing with real older adults.
- **Directory coverage.** 4 of 9 entries are marked verified, with sources of uneven quality (see `directory.json`); the Home Front Command entry still needs a direct check, and the rest are marked `TO VERIFY`.
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
