# Evaluation summary (synthetic corpus, regression check only)

- Scam messages: 36; legitimate messages: 19 (12 contain a link or a named organization)
- Scams not reported as 'matches official' (includes 'cant verify', so not a catch rate): 36/36
- Scams hard-blocked with 'Don't click': 25/36
- Scams wrongly reported as 'matches official' (worst failure): 0
- Legitimate messages wrongly told 'Don't click' (false alarms): 0/12
- Legitimate messages told 'can't verify' (friction): 7/12
- Legitimate messages confirmed as matching the official site: 5/12
- Cases where verdict differs from expectation: 0

## Scams that are only 'can't verify' (known gaps)
- s02: modeled on a 2019 published bank SMS; shortened link
- s14: shortener, no org
- s16: critic: official domain of an unrelated organization, no organization claimed
- s17: critic: organization not in directory
- s18: critic: official link plus a phone number to call
- s19: critic: gov.il is shared, only the fines service path counts
- s22: official domain but the message pressures and asks for card details: never reassure
- e06: English, shortener hides the destination
- s90: open redirect on an official domain is not a match
- s91: dot-dot after the allowed path
- s96: official link plus a payment request is not a match
