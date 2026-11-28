# Evidence, outcomes and how to run it

Details moved out of the README. The README says what the project is and what it does not prove; this page has the checks behind it.

## Three outcomes, never "safe"

| Outcome | Shown as | When |
|---|---|---|
| Don't click | **אל תלחצו** | The link is a lookalike or does not match the organization named (`israelpost.co.il.pay-now.top`, `@` tricks, punycode, raw IPs, a Cyrillic letter in a domain, odd or hidden characters), or the message pushes without a link |
| Official address, not a confirmation | **הכתובת היא של האתר הרשמי, אבל זה לא אישור להודעה** | The link is on a verified official domain and the message does not pressure, ask for details, payment or a call. It still says: open the app or site yourself |
| Could not check | **לא הצלחנו לבדוק** | Unknown organization, a shortened link, an unverified directory entry, an official link with a redirect, or nothing to check. Don't click, ask someone |

An alert is raised on its own only when there is a reason: for SMS on "don't click" and on "could not check" when the message names a known organization or a link comes with a push; in chat apps only on a high-confidence "don't click" (people share links all the time). Never on the official-address outcome.

## What is proven, and how you can check it

| Claim | How it is checked | Where |
|---|---|---|
| The engine returns the right outcome on lookalikes, redirects, path tricks, hidden characters, spread-out and fullwidth letters | 165 Python tests plus a 55-message corpus (36 scams, 19 legitimate), run on every push | `tests/`, `eval/`, workflow *Tests* |
| Scams are never reported as "official address" | Corpus: 0 of 36. Hard "don't click": 25 of 36; the rest are "could not check" | `python eval/run_eval.py` |
| The Android engine behaves like the Python engine | The Kotlin file is compiled on a plain JVM and run on 490 messages: 0 differences | `tools/kotlin-parity/`, workflow *Tests* |
| The Android app builds and its unit tests pass | GitHub Actions | workflow *Android debug APK* |
| On Android, the background agent alerts without the app being opened, and stays quiet on an ordinary message | An emulator in CI receives a fake SMS; the job fails unless exactly one alert appears; screenshots, a recording and the notification text are saved | `docs/proof/`, workflow *Android on an emulator* |
| The message text is not stored | A test reads the database columns; the Android history stores only the app, the time and Shomer's own explanation | `tests/test_server.py`, `History.kt` |

Running the app on an emulator found two crashes that the unit tests could not see (Android's regular expressions are stricter than the JVM's).

## Try it yourself

```bash
python -m shomer.server        # then open http://localhost:8000  (Python 3.10+, standard library only)
python -m pytest
python eval/run_eval.py
```

**Android:** every push that touches `app/` builds a debug APK in GitHub Actions (*Actions*, the latest green run, artifact `shomer-debug-apk`; downloading needs a GitHub login). Read the limits below before installing it.

## What I cut, and why

An AI model deciding verdicts (a rule engine gives the same answer for the same input and can be explained); an agent that signs in to accounts; iPhone background checking (not possible); reading WhatsApp in full (notifications only, from a short list of apps); a score or a "safe" badge (it would invite trust the tool has not earned); unverified organizations as "official" (they stay "could not check"; related domains that are probably but not provably the organization's are kept as `unconfirmed_domains`).

## User and threat research
Survey answers (anonymous, n=6): `docs/research/survey/`. Five short conversations and a self-report: `docs/research/`. Combined findings with confidence labels: `docs/research/synthesis.md`. External figures with sources: `docs/research/threat-landscape.md`.
