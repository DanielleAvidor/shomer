# Shomer product-critic-2: adversarial test of the extended engine

Method: read engine.py and directory.json, then ran 22 legitimate and 26 scam messages (Hebrew, a few English) through `Engine().check()`. Script: /tmp/t/run.py. No project files were changed.
Definitions: false alarm (FA) = a legitimate message marked dont_click. Annoying = a legitimate message marked cant_verify with applicable=True. Miss = a scam marked matches_official or applicable=False. Soft miss = a scam marked only cant_verify.

## Headline numbers (small n, wide error bars)
| Metric | Count | Rate |
|---|---|---|
| Legit marked dont_click (hard false alarm) | 2 / 22 | 9% |
| Legit marked cant_verify with applicable=True (annoying) | 6 / 22 | 27% |
| Legit left alone (applicable=False) | 14 / 22 | 64% |
| Scams marked matches_official | 0 / 26 | 0% |
| Scams with applicable=False (hard miss) | 5 / 26 | 19% |
| Scams marked only cant_verify (soft miss) | 3 / 26 | 12% |
| Scams marked dont_click (caught) | 18 / 26 | 69% |

With n=22 and n=26, the 95% intervals are roughly +/-12 points or more. Treat these as indicative only. My scams are deliberately adversarial, so the miss rate probably overstates real-world traffic.

The 14 legit messages left alone are not all passes. Several are family messages with no signal at all, which is correct. The true benign pass rate is hard to say.

## Results table: legitimate messages
| Input (abbreviated) | Verdict | Why |
|---|---|---|
| אבא, החלפתי מספר? לא, זה עדיין אני, הטלפון בטעינה | **dont_click (FA)** | "אבא ... החלפתי מספר" matches `family` (weight 3) with no money ask. The question mark and the denial are ignored. |
| אבא, תעביר לי בבקשה את המספר של האינסטלטור | **dont_click (FA)** | `תעביר לי` alone matches `family`, with no money object. |
| בנק הפועלים: קוד האימות שלך הוא 482913. אל תמסור אותו | cant_verify, applicable | The bank alias is claimed and there is no link. Mildly annoying; arguably acceptable. |
| דואר ישראל ... נקודת איסוף ... קוד איסוף 4471 | cant_verify, applicable | The alias claim has no link, so "no verifiable link" is the only reason. Annoying for a routine courier text. |
| תזכורת: תשלום ועד הבית ... בביט לדני 0501234567 | cant_verify | The alias `ביט` claims Bit, and the phone number adds +1. A real-life neighbour message. |
| דני, ביט אליי 50 שח על הפיצה | cant_verify | The bare alias `ביט` is a verb ("bit me") and counts as the org Bit. |
| חברת החשמל ... https://www.iec.co.il | cant_verify | The directory has no IEC entry. It is honest, but the user gets "can't verify" on a real company. |
| בנק לאומי ... https://www.leumi.co.il | cant_verify | The Leumi entry is verified=false. Same as above. |
| דחוף! אמא, אני מאחר בעשר דקות | not applicable | Correct. |
| סבתא, הגעתי הביתה בשלום | not applicable | Correct. |
| הי, שלח לי את הכתובת של המסעדה | not applicable | Correct. |
| מזל טוב! קיבלת מתנה ... 10% הנחה בקסטרו | not applicable | `prize` fires (2) but the score stays below 3. Fragile: add a phone number or "דחוף" and it flips. |
| ההגרלה של הכיתה תתקיים מחר | not applicable | Same as above, `prize` = 2. |
| החזר עבור ההזמנה ... תוך 3 ימים | not applicable | `urgency` fires on a harmless refund notice. |
| ייחסם הגישה לתיק ... סתם בדיחה | not applicable | `threat` = 2, below the threshold, so it passes by luck. |
| נטפליקס נחסם? לא, סתם ... 054-1234567 | not applicable | Same: `threat` = 2. |
| התור אצל ד"ר כהן ... מכבי | not applicable | Correct. |
| Hi, your table for 4 is booked at 20:00 tonight | not applicable | Correct. |
| Your verification code is 552381. Do not share it | not applicable | Correct, but only because there are no English signals at all. |
| מזכירות בית הספר: דחוף - מחר אין לימודים | not applicable | Correct. |
| הורים יקרים ... נא לשלוח לי אישור הגעה | not applicable | Correct. |
| סבא, אני אצלך עוד שעה, שים קפה | not applicable | Correct. |
| בנק לאומי: תשלום הוראת הקבע ... leumi.co.il | cant_verify | Counted above (verified=false). |

## Results table: scams
| Input (abbreviated) | Verdict | Why / bug |
|---|---|---|
| משטרת ישראל ... התקשר דחוף ל-03-5551234 | cant_verify (soft miss) | Only `urgency` (1) plus phone (1) = 2. No pattern for "נפתח נגדך תיק". |
| רשות המסים: זכאותך להחזר מס ... הזינו פרטי כרטיס אשראי | dont_click | Caught (credentials 3 + prize 2). |
| היי אמא, החלפתי מספר ... 3000 ש"ח בביט | dont_click | Caught. |
| אבא זה אני, שברתי את הטלפון, שלח לי כסף | dont_click | Caught. |
| **שלום מבנק דיסקונט ... מסרו את קוד האימות** | **not applicable (MISS)** | Discount Bank is not in the directory. `מסרו קוד` fails because "אנא מסרו את קוד" has a gap that does not match the object list. |
| דואר ישראל ... israelpost-parcel.top | dont_click | Caught by the lookalike check. |
| בנק הפועלים ... ייחסם תוך 24 שעות ... bit.ly | dont_click | Caught (a shortener plus score >= 3). |
| זכית בהגרלה! iPhone ... prize-il.xyz | cant_verify (soft miss) | `prize` = 2 only, and the link is unknown. A bad TLD is not scored. |
| הי מה קורה? זה סבתא, החלפתי מספר ... דחוף | dont_click | Caught. |
| מס הכנסה: החזר מס ... שלחו תעודת זהות ופרטי חשבון | dont_click | Caught. |
| כביש 6 ... שלמו עד סוף היום ... kvish6-pay.site | dont_click | Caught. |
| בנק לאומי: הכרטיס שלך נחסם. הזן פרטי הכרטיס | dont_click | Caught. |
| **כאן נציג מחלקת הונאות בבנק ... העבר לנו את הקוד** | **not applicable (MISS)** | The generic word "בנק" is not an alias. `העבר לנו את הקוד` is missing from the credentials pattern. |
| הלו אמא, אני במעצר ... תעבירי לי כסף | dont_click | Caught only because of `תעבירי לי`. |
| ז.כ.י.ת ב.ה.ג.ר.ל.ה ... bit.ly | cant_verify (soft miss) | Dot obfuscation defeats the regexes. |
| משטרה ... קנס תנועה ... gov-il-fines.top | dont_click | Caught. |
| Your package could not be delivered. Pay 2.99 ... post-il.click | dont_click | Caught only through the lookalike token `postil`. There are no English signals. |
| חברת החשמל: ניתוק חשמל ... בביט 0521234567 | dont_click | Caught by chance (urgency + payment + `bit` claim). |
| **היי, אני מהבנק ... תוכל להקליד את הסיסמה שלך** | **not applicable (MISS)** | `הקלד` is only matched in the imperative plural, not "להקליד", and the object is "את הסיסמה". |
| הי אבא, זה אני הילד ... בפייבוקס | dont_click | Caught. |
| קיבלת החזר ארנונה! אשר את מספר חשבון | dont_click | Caught. |
| **שירות הלקוחות של פייפאל ... הושעה. הכנס קוד חד פעמי** | **not applicable (MISS)** | PayPal is not in the directory. `הושעה` has weight 2 and the verb `הכנס` is not in the list. |
| ביטוח לאומי: מגיע לך קצבה ... btl-gov.icu | dont_click | Caught. |
| **סבתא זה נכד שלך, החליפו לי מספר ... העברה בנקאית** | **not applicable (MISS)** | Only `payment` (1) fires. "זה נכד שלך" and the plural "החליפו" are not in the family pattern. |
| בנק הפועלים: עדכון פרטי אשראי ... hapoalim-bank.co.il.verify.top | dont_click | Caught. |
| אמא שלום, שברתי את המסך. שלחי לי כסף | dont_click | Caught. |

## Official-domain downgrade (extra probes)
- A real israelpost.co.il link plus a phone number gives cant_verify. That rule is intentional, but a routine real courier SMS with a "call us" line gets the annoying verdict.
- A real israelpost.co.il link with "תוחזר תוך 24 שעות אם לא תשלמו" still gives matches_official. Pressure of kind=pressure needs 2 distinct signals, and urgency+payment counts only one (payment has kind=money). That is a gap if the domain is real but hosts a user-generated path, so the verdict is fine here.

## Regex bugs and gaps
1. **`family` is the biggest source of false alarms.** `תעביר לי|תעבירי לי|שלח לי כסף|...` has no money object (a bare "תעביר לי" fires), and `החלפתי מספר` fires inside a question or a denial.
2. **`family` too narrow.** It misses "אבא זה אני" with a different continuation, "זה נכד שלך", "החליפו לי מספר" (plural), "תעבירו לי", and "מספר חדש".
3. **`credentials` verbs.** It misses `הכנס|הכניסו|העבר|העבירו|להקליד|תקליד|תזין`. It misses the object "הקוד" and "קוד חד פעמי", and it does not allow the particle "את" plus an adjective ("את הסיסמה הזמנית").
4. **No impersonation-of-staff signal.** "מדובר ב...", "כאן נציג מ...", "אני מהבנק" mark a call-centre style opening and carry no weight.
5. **No generic bank alias.** "בנק" alone and "מהבנק" never claim an org, so the message is not applicable.
6. **Obfuscation.** Dots, hyphens, zero-width characters and niqqud between Hebrew letters (ז.כ.י.ת, יֵחָסֵם) defeat every pattern. No normalisation step exists.
7. **Alias `ביט` is a common verb** ("ביט אליי"), producing false org claims and a bare "annoying" verdict.
8. **English is nearly unsupported.** "Pay ... customs fee", "verify your account", "you won", "suspended" give no signal.
9. **Ktiv male/haser.** `זכיה|זכייה` is covered, but `זכייתך`, `הוגרלת`, `סיסמא` (variant of סיסמה) and `יחסם|ייחסם` only partly. The urgency window `תוך \d+` misses "תוך יומיים", "תוך שעה" and "תוך יום".
10. **Scoring fragility.** Scams with a single signal of weight 2 plus an unknown link stay at cant_verify, while legit "מתנה" or "הגרלה" messages sit one signal (phone number or "דחוף") away from dont_click.

## 8 concrete fixes (exact regexes)
1. **Split `family` into an intro (weight 1) and a money ask (weight 2).** A bare intro like "אבא, החלפתי מספר?" then scores 1 and does not alert. Intro:
   `(?:אמא|אמי|אבא|סבתא|סבא)[ ,!.]*(?:זה אני|החלפתי מספר|החליפו לי מספר|מספר חדש|שברתי|איבדתי|הטלפון (?:שלי )?(?:נפל|נשבר|התקלקל|אבד))|זה (?:סבתא|סבא|נכד|הנכד|הבן|הבת|הילד|הילדה)[^.\n?]{0,40}(?:מספר|טלפון)`
   Money ask:
   `(?:תעביר|תעבירי|תעבירו|תשלח|תשלחי|שלח|שלחי)\s+לי[^.\n?]{0,25}(?:כסף|כספים|ש"ח|שקל|₪|\d{3,}|ביט|פייבוקס|paybox|העברה)|(?:אפשר|תוכל|תוכלי|את יכולה|אתה יכול) (?:ל)?(?:העביר|שלוח)[^.\n?]{0,25}(?:כסף|ש"ח|₪|ביט|פייבוקס)`
2. **Widen `credentials` verbs and objects**, keeping the `אל ` negation lookbehinds:
   `(?<!אל )(?<!אל ת)(?:ל?(?:הכנס|הכניסו|הכנסי|העבר|העבירו|מסור|מסרו|הקלד|הקלידו|תקליד|תזין|הזינו|הזן|שלח|שלחו))(?: לנו| לי)?[^.\n]{0,25}(?:קוד(?: אימות| חד[- ]?פעמי| סודי| זמני)?|סיסמ[אה]|פרטי (?:כרטיס|אשראי|חשבון)|מספר (?:כרטיס|חשבון)|תעודת זהות|ת\.?ז\b|cvv|otp)`
3. **New `staff` signal (kind=pressure, weight 2).** Call-centre opening lines:
   `(?:כאן|מדובר|מדברים|אני|מתקשר(?:ת)?|נציג(?:ה)?)\s*(?:מ|ב)?(?:ה)?(?:בנק|מחלק(?:ת|ה) (?:ה)?(?:הונאות|אבטחה)|משטרה|משטרת ישראל|רשות המסים|מס הכנסה|פייפאל|paypal|ביטוח לאומי|חברת החשמל)`
4. **Extend `threat` (weight 2)** with authority and cut-off phrases:
   `|נפתח (?:נגדך )?תיק|פתחנו נגדך|תיק פלילי|צו (?:מעצר|עיקול|חיפוש)|מעצר|ניתוק (?:חשמל|מים|גז|קו)|תנותק|הוקפא|הקפאת חשבון|חסימת (?:חשבון|כרטיס)|חשבונך הושעה|(?:חשבון|כרטיס)[^.\n]{0,15}(?:נחסם|ייחסם|יחסם|יינעל)`
5. **Widen `urgency` (weight 1)** to cover "תוך יומיים/שעה/יום", "בהקדם" and "עכשיו" in a command context:
   `דחוף|בהקדם|מיידית|מידית|תוך (?:\d+|שעה|שעתיים|יום|יומיים|שלושה|שבוע) ?(?:שעות|שעה|ימים|יום|דקות)?|עד (?:סוף היום|חצות|מחר)|לפני סוף היום|פג תוקף|היום האחרון|הזדמנות אחרונה|פעולה אחרונה`
6. **Normalise text before matching.** In `content_signals`, `claimed_orgs` and `_PHONE_RE`, first run:
   `re.sub(r"(?<=[א-ת])[.\-_*·​-‏ְ-ׇ](?=[א-ת])", "", text)`
   This removes dots, hyphens, zero-width characters and niqqud between Hebrew letters. Optionally also collapse single-letter spacing with `re.sub(r"(?<![א-ת])([א-ת]) (?=[א-ת](?: |$))", r"\1", text)`.
7. **Add an English signal set**, one per kind, matched case-insensitively:
   - `credentials`: `verify your (?:account|identity|card)|enter your (?:password|pin|card|otp|code)|(?:send|share|give) (?:me )?(?:the |your )?(?:code|otp|password)`
   - `urgency`: `\burgent(?:ly)?\b|immediately|within \d+ ?(?:hours?|days?)|act now|final notice`
   - `threat`: `(?:account|card|package)[^.\n]{0,25}(?:suspended|blocked|locked|returned|on hold)|legal action|arrest`
   - `prize`: `you(?:'ve| have)? won|claim your (?:prize|reward|gift)|gift card|free gift`
   - `payment`: `(?:pay|payment of)[^.\n]{0,30}(?:fee|fine|customs|delivery)|customs fee|delivery fee|unpaid`
8. **Tame `prize` and the `ביט` alias.** Split `prize` into a strong signal (weight 2: `זכית|זכיתם|זכיה|זכייה|זכייתך|הוגרלת|החזר (?:כספי|מס|ארנונה)|פרס`) and a weak signal (weight 1: `הגרלה|מתנה|שובר`). Replace the alias `ביט` with `אפליקציית ביט|אפליקצית ביט|בנק ביט|bit payment|(?<![א-ת])ביט (?:של|מ)` or require a Bit link host. Add `בנק דיסקונט|בנק מזרחי|בנק הבינלאומי|פייפאל|paypal|רשות המסים|חברת החשמל|מכבי|כללית` as aliases (even with verified=false) so claims are recognised.

Also recommended, not a regex: when an unknown-sender message has a bad TLD (`.top`, `.xyz`, `.icu`) plus any weight >= 2 signal, escalate to dont_click. Currently a bad TLD adds only a reason text, not score (this is why the `prize-il.xyz` scam stayed at cant_verify). Also add a verified IEC entry and verify Leumi/Hapoalim/Bit/BTL/Kvish6 so that real bank links can reach matches_official.

## Product notes
- cant_verify on a legit bank OTP with no link ("אל תמסור אותו") will annoy users daily. Consider a lower-key informational verdict when the only reason is "named org, no link, no signals".
- The phone-number downgrade punishes real courier texts that say "call us". Consider keeping matches_official when the number appears in the org's directory entry.
- Caveat on all numbers above: these are hand-written messages, small n, and I wrote scams knowing the engine's patterns, so the miss rate is likely a lower bound for real-world obfuscation.
