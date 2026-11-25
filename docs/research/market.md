# Market research: scam messages and older adults in Israel

Desk research, 2026-11-25. Built from web search results and page snippets, not from reading every primary report in full. Each line says how solid it is. Nothing here is a measurement of Shomer; it is the context the product is judged against.

## 1. How big is the problem

| Finding | Source and status |
|---|---|
| The National Cyber Directorate handled **31,657 phishing infrastructures in 2025, against 4,491 in 2024**, about seven times more. | Ynetnews, Black Friday article (quoting the Directorate). Secondary, figures not checked against the Directorate's own report. |
| In 2024 about 17,000 cyber events were reported to the Directorate, about **41% of them phishing or impersonation of official bodies**. | Maariv, quoting the Directorate. Secondary. |
| Check Point saw a rise of **more than 340% in malicious SMS aimed at Israel** since March 2025. | Globes (English). Vendor data, reported by the press. |
| A security vendor estimates **1 to 5 phishing messages a week per Israeli**, 3,000 to 5,000 a day in total. | Globes, quoting Cyvore. Vendor estimate; treat as an order of magnitude. |
| The Directorate's list of the most impersonated financial brands starts with **Cal, then Bank Leumi, Bit, Isracard, Bank Hapoalim and PayPal**. | Globes. Secondary; useful for deciding which directory entries to verify first. |
| In 2025 the Welfare Ministry's units treated **6,262 older adults** harmed by abuse, neglect or exploitation. Cases of **financial exploitation and fraud rose from 135 to 217 (+61%)**. | Walla, from the Welfare Ministry report. Secondary. Covers all kinds of exploitation, not only messages. |
| Police opened about **2,639 elder-fraud cases** in one year (2024). | Bizportal headline only; I could not open the full article. Previously noted in the PRD as a secondary figure. |
| A survey by the Aviv association found that **a quarter of people aged 65 and over** (about 12.5% of the population) experienced consumer fraud in two years. | Ynet. Survey, method not seen. |
| The Consumer Protection Authority told the Knesset Economy Committee that **20% to 60% of older adults** have been victims of consumer fraud, with damage of over half a billion shekels a year. | Bhol (July 2026). A wide range; shows that nobody has a solid number. |
| Messages can appear **inside an existing thread** of the real sender (GOV.IL), and the Directorate says the Home Front Command never sends app links by SMS. | Jdn and Kan, quoting the Directorate. Strong; the Directorate is the source. |
| United States, for comparison: people aged 60+ reported **$7.7 billion** in losses to the FBI in 2025; FTC-reported losses of older adults grew from $600 million (2020) to about $3 billion (2025). | Bitdefender (quoting the FBI) and the FTC consumer alert. Not Israel. |

**What this means.** Volume is rising fast and Israeli institutions are named in the messages, but there is **no solid figure for how many older Israelis lose money to a scam message**. The honest claim is: a large and growing exposure, with the cost concentrated in rare, expensive mistakes. The product's value case rests on that, not on a market-size number.

## 2. Who else is in the space

| Product | What it does | Overlap with Shomer | Source |
|---|---|---|---|
| **Phishy** (Israeli developer) | Checks links and SMS/WhatsApp messages; can scan incoming notifications automatically; **Family Mode** sends an alert to a relative when a scam reaches an older parent, without showing the messages; Hebrew, Russian, English, Arabic; free; 9 engines; gives a 0 to 100 safety score. Play Store shows **10+ downloads**, last updated Nov 2026. | **Closest competitor.** Same idea (background check plus family alert, no message storage). Very early. | Google Play and App Store listings |
| **ScanMySMS** (Israeli, 2023) | Paste a suspicious SMS into a web app or Telegram bot. | Manual only; no background check, no family. | Startupim, BSidesTLV |
| **Fisherman** (Israeli) | Automatically filters spam, ad and phishing SMS on iPhone into Junk. | Blocks silently; does not explain or involve family. | App Store |
| **ScanLens, ScamHunter, ScamZero** | Paste or share a message; verdict labels such as safe, suspicious, dangerous; ScanLens has an optional notification-based mode. | Generic, not tied to Israeli institutions. | Google Play |
| **Google Messages Scam Detection** | On-device, on by default for non-contacts. Its list of countries (September 2025 article) is the US, UK, Canada, Australia, France, Germany, India, Japan, Mexico, Singapore; **Israel is not on that list**. | Platform feature; would reach all Android users if it arrives in Hebrew. | Android Authority, Google blog. Re-check before relying on it. |
| **ScamShield (Singapore, government)** | State-run app to check numbers, messages and links. | A model for what a national solution looks like. | scamshield.gov.sg |

**What this changes in the PRD.**
- The idea "a background checker with a family alert" is **not unique**: Phishy already offers it. Shomer's claimed difference narrows to **how it decides**: it compares the link with a verified list of official domains and says "can't verify" instead of guessing, it never gives a "safe" score, and it speaks plain Hebrew to the older adult. Whether that matters to users is untested.
- Phishy's use of notifications, with no SMS permission, shows a route that avoids the Play Store's SMS restrictions (section 4).
- Before any public claim of a difference: run the same 20 real messages through Phishy, ScanMySMS, Fisherman and Google Messages.

## 3. The environment is moving

- **Telecom regulation.** Israel's Communications Minister announced that mobile operators must run mechanisms that identify senders and limit anonymous mass SMS, with the reform taking effect on a later date (Kan; the date was cut off in the snippet I could read). The UK (Ofcom, 2025 to 2026) and Australia (a sender-ID register) are doing the same. If it works, fewer messages reach the phone, but link-based scams from ordinary numbers and chat apps remain. **Opportunity and risk:** it lowers the volume Shomer must catch, and it may make the "official sender" signal stronger in time.
- **Sideloading rules.** Google will require developers to be verified even for apps installed outside the Play Store. It starts September 30, 2026 in Brazil, Indonesia, Singapore and Thailand, with a global rollout planned from 2027 (Android Authority, Android Developers). Unverified apps will need a longer "advanced" install flow, and a limited-distribution account is planned for small groups. For Shomer this means an APK shared by link may become harder to install for older adults in Israel in 2027, so a verified-developer registration should be planned.
- **Play Store SMS policy.** Reading SMS in a Play Store app generally requires being the default SMS handler. The policy's own table of permitted uses lists **"anti-SMS phishing (smishing)"** as a use case that can be approved, subject to Play review (Google Play Console Help). This is a possible public-release route that the PRD had not considered; it needs a declaration form and a review that can be refused. The notification-based route needs no SMS permission.

## 4. What I would still need to find out

1. How many older Israelis actually lose money to a scam message (the figures above mix fraud types and sources). Best next step: ask the Israel Police and the National Cyber Directorate for message-specific numbers.
2. How Phishy behaves on real Hebrew messages and how many people use it. Install it and run the 20-message comparison.
3. Whether Google Messages Scam Detection reaches Israel and Hebrew in 2026 or 2027.
4. Whether the telecom sender-identification reform has a date, and what it covers.

## Sources

- Ynetnews, Black Friday scams (Directorate figures 2024 and 2025): https://www.ynetnews.com/business/article/bjony00uagx
- Maariv, phishing figures 2024 and 2025: https://www.maariv.co.il/economy/israel/article-1289569
- Globes, phishing flood (Check Point, Cyvore, most impersonated brands): https://en.globes.co.il/en/article-phishing-attacks-flood-financial-services-industry-1001512781
- Walla, Welfare Ministry 2025: https://news.walla.co.il/item/3846524
- Bizportal, 2,639 cases: https://www.bizportal.co.il/guides/news/article/20041435
- Ynet, Aviv survey: https://www.ynet.co.il/article/b1nujdjsa
- Bhol, Knesset Economy Committee: https://www.bhol.co.il//news/1544081
- Jdn, Directorate on GOV.IL thread messages: https://www.jdn.co.il/?p=2692859
- Kan, Home Front Command phishing: https://www.kan.org.il/content/kan-news/local/1015209/
- Kan, Communications Minister's decisions: https://www.kan.org.il/content/kan-news/economic/248856/
- Bitdefender (FBI 2025): https://www.bitdefender.com/en-us/blog/hotforsecurity/older-adults-lost-billions-to-scammers-2026
- FTC consumer alert, Nov 2026: https://consumer.ftc.gov/consumer-alerts/2026/11/new-report-ftcs-work-protect-older-adults
- Phishy on Google Play: https://play.google.com/store/apps/details?id=com.phishy.app
- ScanMySMS: https://startupim.com/company/scanmysms
- Fisherman on the App Store: https://apps.apple.com/il/app/fisherman-sms-filtering/id6449192504
- Google Messages Scam Detection countries: https://androidauthority.com/pixel-scam-detection-google-messages-global-3711361
- Google Play, SMS and Call Log permissions: https://support.google.com/googleplay/android-developer/answer/10208820
- Android developer verification timeline: https://www.androidauthority.com/android-sideloading-changes-timeline-3679204/
- Ofcom, new rules for messaging scams: https://www.ofcom.org.uk/phones-and-broadband/scam-calls-and-messages/new-rules-to-protect-people-and--businesses-against-mobile-messaging-scams
