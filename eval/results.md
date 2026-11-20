# Evaluation summary (synthetic corpus, regression check only)

- Scam messages: 19; legitimate messages: 12 (10 contain a link or a named organization)
- Scams never reported as 'matches official' (user is not reassured): 19/19
- Scams hard-blocked with 'Don't click': 11/19
- Scams wrongly reported as 'matches official' (worst failure): 0
- Legitimate messages wrongly told 'Don't click' (false alarms): 0/10
- Legitimate messages told 'can't verify' (friction): 5/10
- Legitimate messages confirmed as matching the official site: 5/10
- Cases where verdict differs from expectation: 0

## Scams that are only 'can't verify' (known gaps)
- s02: modeled on a 2019 published bank SMS; shortened link
- s12: no link: engine cannot judge a phone number; known limitation
- s13: family impersonation without link: out of scope
- s14: shortener, no org
- s16: critic: official domain of an unrelated organization, no organization claimed
- s17: critic: organization not in directory
- s18: critic: official link plus a phone number to call
- s19: critic: gov.il is shared, only the fines service path counts
