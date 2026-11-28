# Research synthesis

Date: 2026-11-27. This ties together everything I have: an anonymous survey (n=6), five short conversations (two from my immediate family and three others), my own self-report, and desk research on the threat (`threat-landscape.md`). It is a small, informal study. Every claim below says how strong the evidence is.

## 1. What the evidence is

| Source | Who | n | Strength | Files |
|---|---|---|---|---|
| Anonymous online survey, 5 questions | Friends and acquaintances, sent by link | 6 (3 under 30, 2 aged 40-75, 1 over 75) | Weak: small, convenience sample, self-reported, half under 30 | `survey/questions.md`, `survey/results-summary.md` |
| Short oral conversation | My grandmother | 1 | Weak: one person, family, read aloud by me | `interview-01-grandmother.md` |
| Short oral conversation | My mother | 1 | Weak: same family | `questionnaire-02-mother.md` |
| Short oral conversation | A friend's grandmother, 77 | 1 | Weak: one person, as reported by me, not recorded | `interview-04-friends-grandmother.md` |
| Short oral conversation | A man aged 81 | 1 | Weak: one person, as reported by me, not recorded | `interview-05-man-81.md` |
| Short oral conversation | A father, about his 9-year-old daughter | 1 | Weak: second-hand, the child was not asked | `interview-06-parent-of-child-9.md` |
| My own experience | Me, the builder | 1 | Weak: not independent | `self-report-03-builder.md` |
| Desk research | Official bodies and media | n/a | Medium to high, see confidence labels | `threat-landscape.md`, `market.md` |

Consent: the builder confirmed on 2026-11-27 that the people interviewed, and the parent speaking about his child, agreed to have their account included in this public project. No names are kept. Survey answers are published as totals only.

The people are not independent of each other and none is a random sample. Nothing here estimates how common anything is.

## 2. Findings

**F1. Knowing "do not tap" is not the problem.** In the survey, three of six checked the message themselves on the official site or app, two of them also asked an AI chat, and three ignored it (one of them also checked). In conversations the older women asked a relative and then an AI chat. Confidence: medium that people say they are careful; the evidence is self-reported.

**F2. The failure is not suspecting.** The 77-year-old tapped two fake links and once entered card details. She did not know they were fake, so she asked nobody. She sends things to her granddaughter only when something already looks wrong to her. One anonymous survey answer also reported tapping a link and giving details or paying. I cannot tell whether that person was also interviewed, because the survey is anonymous. Confidence: low to medium that this pattern exists; low on how common it is. It is consistent with the official advice assuming suspicion (threat landscape, point 4).

**F2b. Forwarding to family is costly and still misses scams.** An 81-year-old man, scammed three times, now sends suspicious-looking messages to his son. Most are ordinary, and he missed one real scam. Confidence: low (one person, reported by me).

**F2c. Blocking senders does not hold.** A father could not stop phishing messages reaching his 9-year-old daughter, because each came from a different domain or number. She tapped one by mistake and he saw it only by chance. Confidence: low (one parent, second-hand). It supports checking links against official addresses over block lists, and it points to a segment (children, and parents who want to be told) that Shomer does not serve yet.

**F3. The messages look like services people already use.** The 77-year-old said she cannot tell because the messages usually imitate services she uses regularly, such as Israel Post. My grandmother and mother received the same Israel Post and Kvish 6 scams. The INCD reports repeated 2026 waves in the names of Israel Post, police, the Tax Authority and gov.il, and a case where real police SMS tickets and fake ones appeared within days of each other. Confidence: high that the pattern exists in Israel, medium that it is the main cause of harm.

**F4. People want a warning that names the organisation, and also a plain verdict.** The clearest warning was "do not tap, this link does not lead to the Israel Post site" for 3 of 6, and a plain verdict for the other 3 ("this is a scam" 2, "dangerous message" 1). My grandmother preferred the naming version too. Confidence: low. The split suggests offering both a short verdict and the specific reason.

**F5. Family alerts split opinion.** Three of six want an alert to a relative every time, one only when the danger is clear, two no. The first two older women I asked did not want alerts about themselves, while some survey respondents did. Confidence: low. This supports keeping the family alert optional and opt-in, and not assuming either way.

**F6. [Hypothesis, not tested] The effort of checking, and missed real messages.** My view is that careful checking is costly, and that ignoring everything can lead to missing real messages. Supporting signs: five of six survey respondents either checked manually or ignored the message (one did both), and the police now send real tickets by SMS. The 81-year-old man's pattern (forwarding mostly ordinary messages, still missing a real scam) fits the idea that effort goes to the wrong messages. I did not ask anyone directly whether they missed a real message because of this, so it stays a hypothesis.

## 3. Where the sources disagree
- My grandmother declined alerts to family; one survey respondent wants them every time. One person each, so I do not treat either as the answer.
- Survey respondents report mostly careful behaviour, while the 77-year-old reports harm. The survey sample is young and online, so it likely under-represents the people most at risk.

## 4. What this means for Shomer

Already in the product and supported by the evidence:
- It speaks first and does not wait to be asked (F2, F3).
- It checks the link against the organisation's official addresses instead of keeping a block list of bad senders (F2c), and it never says "safe", because real and fake messages now look alike (F3, threat landscape).
- A context question per organisation ("were you expecting a parcel?") (F3).
- The family alert is opt-in and only for high-confidence results (F5).

Changes I would consider next, as hypotheses: a short plain verdict above the specific reason in every warning (F4); wording tested with older people rather than guessed.

## 5. What I can and cannot claim

Can claim: I ran a small anonymous survey (6 responses) and five short conversations, and combined them with public threat data to shape the product's main decisions.
Cannot claim: that Shomer prevents harm, that these numbers represent any population, or that older adults prefer one specific wording. No one has used the product on a phone yet.

## 6. Next research
1. Five to eight more people aged 60 and over, in person, with three real-looking messages (one scam, one real, one hard case), with and without Shomer's warning. This tests F2 and F6 directly.
2. A run on a real Android phone.
3. Ask directly, in the interviews, whether anyone ignored a message that later turned out to be real (tests F6).
