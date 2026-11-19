# Shomer — a scam checker for families (working title)

A message arrives: *"Israel Post: your parcel is on hold, confirm your address"* with a link. Is it real?

Shomer answers that question for older adults in Israel, in plain Hebrew, and keeps a family member in the loop. It doesn't guess with a generic classifier. It **checks the link against the official site of the organization the message claims to be from**, and if they don't match it says so and offers the real site instead.

I started this because my grandmother and mother get these messages all the time and often click first. Today the "protection" is me, checking messages by hand. This project tries to turn that into a product.

> **Status: prototype.** The engine, directory, Hebrew web demo and tests run. The Android app is written but **has not been compiled or run yet**. There is no real-user data yet. See [What is and isn't proven](#what-is-and-isnt-proven).

## What it does

For each message it returns one of three verdicts, and **never says a link is "safe"**:

| Verdict | Meaning |
|---|---|
| **Don't click** | Mismatch or lookalike (e.g. `israelpost.co.il.pay-now.top`, `@` tricks, punycode, raw IPs, brand lookalikes) |
| **Matches the official site** | Still advises opening the app or site yourself |
| **Can't verify** | Unknown organization, shortened link, or an entry not yet verified: don't click, ask the family |

Design choices that matter:
- **Trusted Directory with provenance.** Each organization has its official domains, a source, and a `verified` flag. Only verified entries can ever produce "matches official". Unverified ones degrade to "can't verify" rather than guessing.
- **Family loop.** A high-confidence "Don't click" notifies the family member right away; everything else goes into a summary. The family screen shows what happened, never the message text.
- **Privacy by design.** Analysis happens on the device. Message text is never stored or sent. Only verdict, organization id and link host are reported, and only if a server address was configured.
- **Calm, large, one-button UI** for older adults, RTL Hebrew.

## Try it

```bash
python -m shomer.server        # then open http://localhost:8000
python -m pytest               # 37 tests
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

**Proven (by tests in this repo):** the engine handles lookalike domains, subdomain tricks, `@` tricks, punycode, IPs, shorteners, multiple links and organizations; unverified directory entries never produce a reassuring verdict; message text is not persisted; the server and the Hebrew UI work end to end.

**Not proven:**
- **Accuracy on real messages.** The evaluation corpus (23 messages) is written by me, modeled on published warnings. It is a regression check, not an accuracy estimate. The PRD sets numeric targets to measure on at least 50 real, consented messages.
- **Android.** The Kotlin source has not been built. Open questions: whether a sideloaded app keeps SMS-receive permission, Android's restricted-settings behavior, Gmail API access for a non-published app.
- **That users understand the alerts.** Needs testing with real older adults.
- **Directory coverage.** 4 of 9 entries are verified from public sources; the rest are marked `TO VERIFY`.

**Known limits (by design, for now):** scams without links (phone numbers, "reply to this number", family-impersonation texts) return "can't verify"; a matching domain is not proof a message is genuine (compromised accounts); iPhone cannot read other apps' messages, so iPhone support would rely on share-to-check; WhatsApp is end-to-end encrypted and out of scope for v1.

## Existing options (desk research, not hands-on)

General AI checkers (e.g. Bitdefender Scamio), platform detection (Google Messages, WhatsApp Scam Alert, both limited to non-contacts), and Israeli tools (Fisherman SMS filter for iPhone, ScanMySMS upload checker). The bet here is not better detection but a verified official route, Israeli coverage, and the family loop. A hands-on comparison on real messages is a planned validation step before claiming any difference.

## Next steps

1. Verify the remaining directory entries against official sources.
2. Build and run the Android app; answer the permission questions above.
3. Collect 10–20 real messages from my family (consented, redacted), and interview older adults and their children.
4. Measure against the PRD targets, and write down what changed because of what I learned.
