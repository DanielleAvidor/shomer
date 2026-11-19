# Evaluation summary (synthetic corpus, regression check only)

- Scam messages: 15; legitimate messages: 8 (6 contain a link or a named organization)
- Scams never reported as 'matches official' (user is not reassured): 15/15
- Scams hard-blocked with 'Don't click': 11/15
- Scams wrongly reported as 'matches official' (worst failure): 0
- Legitimate messages wrongly told 'Don't click' (false alarms): 0/6
- Legitimate messages told 'can't verify' (friction): 2/6
- Legitimate messages confirmed as matching the official site: 4/6
- Cases where verdict differs from expectation: 0

## Scams that are only 'can't verify' (known gaps)
- s02: modeled on a 2019 published bank SMS; shortened link
- s12: no link: engine cannot judge a phone number; known limitation
- s13: family impersonation without link: out of scope
- s14: shortener, no org
