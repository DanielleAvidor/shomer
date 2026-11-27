# Shomer: check a message against the official site, and warn before you tap

**The problem.** Older adults in Israel get fake messages that look like Israel Post, their bank or the National Insurance Institute, and many tap before they ask anyone. The usual protection is a relative who happens to be nearby.

**The idea.** Shomer compares the link in a message with the organization's real, verified domain, looks at what the message asks for (hurry, a threat, card details, a payment), and warns in plain Hebrew by itself, without the app being opened. It never says a message is "safe": the best it says is "this is the official address, but that is not a confirmation of the message".

<p align="center"><img src="docs/proof/02-scam-shade.png" alt="Shomer's notification on an Android emulator: do not click the link" width="300"></p>

*Real output: an Android emulator in CI received a fake scam SMS and Shomer raised this alert. An ordinary message raised none ([evidence](docs/proof/)).*

**[Try the live demo](https://danielleavidor.github.io/shomer/)** (a phone lock screen) · **[Product requirements (PRD)](docs/PRD.md)** · **[Market research](docs/research/market.md)** · **[What five reviewers rejected](docs/reviews/)**

## What I decided, and what I learned

I chose the problem and the user (relatives who get these messages and often tap first), ran three interviews, and set the rules the product holds to: it never says "safe", it always points to the official route, it speaks up only when there is a reason, and a family alert is opt-in. I rejected an agent that signs in to people's accounts. When the first demo needed the app opened to show a warning, I sent it back, because the point is a warning without opening anything. Claude (an AI assistant) wrote the code, tests and CI under that direction and ran the reviewer agents.

What changed my mind:
- **All three people I interviewed declined alerts about themselves**, so the family alert became an optional extra and the phone checking for the person herself became the core.
- **The first real phone refused to install the app** (Play Protect). Distribution, not detection, is the biggest risk, and the PRD now says so.
- **Running on an emulator found two crashes** that unit tests could not see (Android's regular expressions are stricter than the JVM's).
- **Market research found a close competitor**, Phishy, with the same idea and a family mode. What is left of my claim is how Shomer decides (a verified official-domain list, "can't verify" instead of a score), and I have not yet tested that it matters to users.

## Where it stands

The engine, 11 verified organizations, the Hebrew demo and an Android app work. There are 160 Python tests, and on every push the Kotlin version is checked against the Python one on 490 messages (0 differences). Details and how to run it: [docs/EVIDENCE.md](docs/EVIDENCE.md).

## Limits

- **Never run on a physical phone.** The first real phone blocked the install. Another route is needed; see PRD section 9.
- **Accuracy on real messages is unknown.** The test messages were written by the person who wrote the rules, so they guard against regressions but do not measure accuracy. 0 misses in 36 scams is still consistent with a true miss rate of about 8%.
- **Friction.** 4 of 19 legitimate messages raise an SMS alert, and 7 of 12 legitimate messages with a link end as "could not check".
- **Thin user evidence:** three interviews in one family. The AI reviewer roles are role-play, not user research.
- **Directory:** 11 of 16 organizations verified; Kvish 6, Bit, PayPal, the Tax Authority and the Home Front Command are not.
- **Open gaps:** callback-number and QR scams, shortened links (not followed), organizations outside the directory.

## Next steps

1. Install on a physical phone, or prove another route.
2. Compare 20 real messages against Phishy ([template](docs/research/phishy-comparison.md)).
3. Run the one-week test with five older adults, three outside the family (PRD section 8).
4. Add a source link to every directory entry.

## Repository

`docs/` PRD, evidence, research, reviews, demo and proof · `shomer/` Python engine, directory, local server · `app/` Android app (Kotlin) · `eval/` test messages · `tools/` parity check, emulator test, demo builder · `survey/` short Hebrew survey (not yet used) · `.github/workflows/` tests, APK build, emulator run, demo deploy
