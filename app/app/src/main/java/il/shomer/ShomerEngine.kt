package il.shomer

import org.json.JSONObject
import java.util.regex.Pattern

/**
 * Kotlin port of shomer/engine.py. Keep the two in sync: both are tested against the same
 * eval/corpus.jsonl (see ShomerEngineTest). Runs fully on the device: message text never leaves the phone.
 */
enum class Verdict(val key: String) {
    DONT_CLICK("dont_click"), MATCHES_OFFICIAL("matches_official"), CANT_VERIFY("cant_verify")
}

data class Org(
    val id: String, val nameHe: String, val aliases: List<String>, val brandTokens: List<String>,
    val domains: List<String>, val officialUrl: String, val verified: Boolean,
    val restrictedPaths: Map<String, List<String>> = emptyMap(),
    val contextHe: String = "",
    /** Probably the organization's, not confirmed from its own site: never "official", never "lookalike". */
    val unconfirmedDomains: List<String> = emptyList(),
)

data class LinkReport(val url: String, val host: String, val verdict: Verdict, val reasons: List<String>, val orgId: String? = null)

data class CheckResult(
    val verdict: Verdict, val headline: String, val advice: String, val reasons: List<String>,
    val claimedOrgs: List<String>, val links: List<LinkReport>, val officialUrl: String?, val applicable: Boolean,
    /** High confidence that this is a scam. Only such results may alert a family member. */
    val certain: Boolean = false,
    /** The message is mostly in a script the checker does not read (for example Russian or Arabic). */
    val unsupportedLanguage: Boolean = false,
    val contextQuestion: String = "",
    /** Ids of the content signals that matched (urgency, threat, credentials, ...). */
    val signalIds: List<String> = emptyList(),
)

/** A content signal: what the message asks for (pressure, threats, secrets, prizes, money, family impersonation). */
data class Signal(val id: String, val kind: String, val weight: Int, val rx: Regex, val he: String)

class ShomerEngine(directoryJson: String) {
    private val orgs: List<Org>
    private val shorteners: Set<String>
    private val badTlds: Set<String>
    private val neutral: Set<String>
    private val common: Set<String>
    private val signals: List<Signal>
    private val aliasPatterns: List<Pair<Org, List<Regex>>>

    init {
        val root = JSONObject(directoryJson)
        fun strings(a: org.json.JSONArray) = List(a.length()) { a.getString(it) }
        orgs = List(root.getJSONArray("orgs").length()) { i ->
            val o = root.getJSONArray("orgs").getJSONObject(i)
            Org(o.getString("id"), o.getString("name_he"), strings(o.getJSONArray("aliases")),
                strings(o.getJSONArray("brand_tokens")), strings(o.getJSONArray("domains")),
                o.getString("official_url"), o.getBoolean("verified"),
                o.optJSONObject("restricted_paths")?.let { rp -> rp.keys().asSequence().associateWith { k -> strings(rp.getJSONArray(k)) } } ?: emptyMap(),
                o.optString("context_he", ""),
                o.optJSONArray("unconfirmed_domains")?.let { strings(it) } ?: emptyList())
        }
        shorteners = strings(root.getJSONArray("shorteners")).toSet()
        badTlds = strings(root.getJSONArray("suspicious_tlds")).toSet()
        signals = root.optJSONArray("signals")?.let { a ->
            List(a.length()) { i ->
                val g = a.getJSONObject(i)
                Signal(g.getString("id"), g.getString("kind"), g.getInt("weight"), unicodeRegex(g.getString("pattern")), g.getString("he"))
            }
        } ?: emptyList()
        neutral = root.optJSONArray("neutral_domains")?.let { strings(it).toSet() } ?: emptySet()
        common = root.optJSONArray("common_domains")?.let { strings(it).toSet() } ?: emptySet()
        aliasPatterns = orgs.map { o -> o to o.aliases.map { aliasPattern(it) } }
    }

    private fun aliasPattern(alias: String): Regex {
        val a = Regex.escape(alias.lowercase())
        return if (alias.any { it in 'א'..'ת' })
            Regex("(?<![א-ת])[בהלמושכ]?$a(?![א-ת])")
        else Regex("(?<![a-z0-9])$a(?![a-z0-9])")
    }

    fun extractUrls(text: String): List<String> {
        val out = mutableListOf<String>()
        for (m in URL_RE.findAll(text)) {
            val u = m.value.trimEnd(*TRAILING)
            if (u.isNotEmpty() && !Regex("^(?:https?://|www\\.)", RegexOption.IGNORE_CASE).containsMatchIn(u) &&
                u.split('/', '?', '#')[0].substringAfterLast('.').lowercase() in FILE_EXT) continue // "report.pdf" is a file name, not an address
            if (u.isNotEmpty() && u !in out) out.add(u)
        }
        return out
    }

    fun claimedOrgs(rawText: String): List<Org> {
        val text = normalize(rawText)
        // Look for organization names only outside links: a link must not vouch for itself.
        val low = URL_RE.replace(text, " ").lowercase()
        return aliasPatterns.filter { (_, pats) -> pats.any { it.containsMatchIn(low) } }.map { it.first }
    }

    /** Longest official domain that host equals or is a subdomain of. */
    private fun officialMatch(host: String): Pair<Org, String>? {
        var best: Pair<Org, String>? = null
        for (o in orgs) for (d in o.domains)
            if ((host == d || host.endsWith(".$d")) && d.length > (best?.second?.length ?: 0)) best = o to d
        return best
    }

    private fun lookalikeOf(host: String): Org? {
        orgs.firstOrNull { o -> tokenHit(host, o.brandTokens) }?.let { return it }
        val core = registrable(host).substringBefore('.')
        for (o in orgs) for (d in o.domains) {
            val oc = registrable(d).substringBefore('.')
            if (oc.length >= 6 && core != oc && levenshtein(core, oc) <= 2) return o
        }
        return null
    }

    fun analyzeLink(url: String, claimed: List<Org>, linkOnly: Boolean = false): LinkReport {
        if (url.endsWith("\u2026")) // a notification preview cut the link short: we never saw the real address
            return LinkReport(url, parseHost(url.trimEnd('\u2026')).first, Verdict.CANT_VERIFY, listOf("הקישור נחתך בתצוגה המקדימה ולא ראינו אותו עד הסוף. פתחו את ההודעה המלאה כדי לבדוק"))
        val (host, userinfo) = parseHost(url)
        val claimedIds = claimed.map { it.id }.toSet()
        val reasons = mutableListOf<String>()
        if (host.isEmpty()) return LinkReport(url, host, Verdict.CANT_VERIFY, listOf("לא הצלחנו לקרוא את כתובת הקישור"))
        if (userinfo) reasons.add("הקישור משתמש בטריק של הסימן @ כדי להסוות לאן הוא באמת פותח")
        if (IP_RE.matches(host)) return LinkReport(url, host, Verdict.DONT_CLICK, reasons + "הקישור פותח למספר ולא לכתובת אתר רגילה")
        if (host in neutral)
            return LinkReport(url, host, Verdict.CANT_VERIFY, reasons + "זה קישור לחנות אפליקציות ולא לאתר של השולח, ולכן לא בדקנו אותו")
        val match = officialMatch(host)
        if (match != null && !userinfo) {
            val (official, matched) = match
            val prefixes = official.restrictedPaths[matched]
            if (!prefixes.isNullOrEmpty() && prefixes.none { parsePath(url).startsWith(it) })
                return LinkReport(url, host, Verdict.CANT_VERIFY, listOf("הכתובת היא של אתר ממשלתי כללי ($matched), ולא הצלחנו לוודא שהדף הזה שייך לגוף ששלח את ההודעה"))
            if (claimedIds.isEmpty() && linkOnly && official.verified)
                return LinkReport(url, host, Verdict.MATCHES_OFFICIAL, listOf("זו הכתובת הרשמית של ${official.nameHe}. זה לא אומר שההודעה שהגיעה עם הקישור אמיתית"), official.id)
            if (claimedIds.isEmpty())
                return LinkReport(url, host, Verdict.CANT_VERIFY, listOf("הכתובת שייכת ל${official.nameHe}, אבל ההודעה לא אומרת מי שלח אותה"), official.id)
            if (official.id !in claimedIds)
                return LinkReport(url, host, Verdict.CANT_VERIFY, listOf("ההודעה מציגה גוף אחד אבל הקישור שייך לגוף אחר: " + official.nameHe), official.id)
            if (!official.verified)
                return LinkReport(url, host, Verdict.CANT_VERIFY, listOf("הכתובת נראית כמו האתר של ${official.nameHe}, אבל הכתובת הרשמית עוד לא נבדקה אצלנו"), official.id)
            return LinkReport(url, host, Verdict.MATCHES_OFFICIAL, listOf("הכתובת תואמת לאתר הרשמי של ${official.nameHe}"), official.id)
        }
        if (!userinfo) for (o in orgs) if (o.unconfirmedDomains.any { host == it || host.endsWith(".$it") })
            return LinkReport(url, host, Verdict.CANT_VERIFY, reasons + "הכתובת נראית קשורה ל${o.nameHe}, אבל עוד לא אימתנו אותה", o.id)
        if (host in shorteners || registrable(host) in shorteners)
            return LinkReport(url, host, Verdict.CANT_VERIFY, reasons + "זה קישור קצר, ואי אפשר לראות לאן הוא באמת מוביל")
        if (registrable(host) in common && !userinfo && lookalikeOf(host) == null)
            return LinkReport(url, host, Verdict.CANT_VERIFY, reasons + "זה קישור לאתר מוכר, לא לאתר של הגוף שהוזכר בהודעה, ולכן לא בדקנו אותו")
        val look = lookalikeOf(host)
        val punycode = host.split(".").any { it.startsWith("xn--") } || host.any { it.code > 127 }
        if (punycode) reasons.add("בכתובת יש תווים מוסווים שנראים כמו אותיות רגילות")
        if (look != null) {
            reasons.add("הכתובת מנסה לחקות את ${look.nameHe} אבל היא לא האתר שלהם")
            return LinkReport(url, host, Verdict.DONT_CLICK, reasons, look.id)
        }
        if (claimed.isNotEmpty()) {
            reasons.add("ההודעה מציגה את עצמה כ${claimed.joinToString(", ") { it.nameHe }}, אבל הקישור פותח לאתר אחר")
            return LinkReport(url, host, Verdict.DONT_CLICK, reasons)
        }
        if (punycode || userinfo) return LinkReport(url, host, Verdict.DONT_CLICK, reasons)
        if (host.substringAfterLast('.') in badTlds) reasons.add("הכתובת נגמרת בסיומת שנפוצה בהונאות")
        reasons.add("לא זיהינו מי שלח את ההודעה")
        return LinkReport(url, host, Verdict.CANT_VERIFY, reasons)
    }

    fun check(rawText: String): CheckResult {
        val text = prepare(rawText)
        val urls = extractUrls(text)
        val claimed = claimedOrgs(text)
        val leftover = URL_RE.replace(text, " ").trim(' ', '\n', '\t', '.', ',', ':', ';', '-')
        val linkOnly = urls.isNotEmpty() && leftover.length <= 3
        val reports = urls.map { analyzeLink(it, claimed, linkOnly) }
        val body = URL_RE.replace(normalize(text), " ").lowercase()
        val badTld = reports.any { it.host.isNotEmpty() && it.host.substringAfterLast('.') in badTlds }
        val sigs = signals.filter { it.rx.containsMatchIn(body) }
        val ids = sigs.map { it.id }.toSet()
        val pressure = sigs.filter { it.kind == "pressure" }.map { it.id }.toSet()
        val phone = PHONE_RE.containsMatchIn(text)
        var score = sigs.sumOf { it.weight } + if (phone && claimed.isNotEmpty()) 1 else 0
        if ("family" in ids && ids.none { it in setOf("family_intro", "urgency", "threat") }) score -= 1 // a request alone is not enough
        val applicable = urls.isNotEmpty() || claimed.isNotEmpty() || score >= 3
        var verdict = when {
            reports.any { it.verdict == Verdict.DONT_CLICK } -> Verdict.DONT_CLICK
            reports.isNotEmpty() && reports.all { it.verdict == Verdict.MATCHES_OFFICIAL } -> Verdict.MATCHES_OFFICIAL
            else -> Verdict.CANT_VERIFY
        }
        val reasons = mutableListOf<String>()
        var downgraded = false
        // A matching link is not enough when the message also pressures, asks for secrets or for a call.
        if (verdict == Verdict.MATCHES_OFFICIAL) {
            if (phone) {
                verdict = Verdict.CANT_VERIFY; downgraded = true
                reasons.add("ההודעה מבקשת גם להתקשר למספר טלפון, וזה לא משהו שאנחנו יכולים לבדוק")
            }
            if ("credentials" in ids || "install" in ids || pressure.size >= 2) {
                verdict = Verdict.CANT_VERIFY; downgraded = true
                reasons.add("הכתובת תואמת, אבל ההודעה מלחיצה או מבקשת פרטים. גופים רשמיים לא עושים את זה בהודעה")
            }
        }
        // An unverifiable link plus a pushy message is treated as a scam.
        if (verdict == Verdict.CANT_VERIFY && urls.isNotEmpty() && (score >= 3 || (score >= 2 && badTld)) && !downgraded) verdict = Verdict.DONT_CLICK
        // No link at all: judge by what the message asks for.
        if (urls.isEmpty()) {
            if (score >= 3) verdict = Verdict.DONT_CLICK
            else if (applicable || claimed.isNotEmpty()) verdict = Verdict.CANT_VERIFY
        }
        for (why in reports.flatMap { it.reasons }) if (why !in reasons) reasons.add(why)
        for (g in sigs) if (g.he !in reasons && g.id != "payment") reasons.add(g.he)
        if ("payment" in ids && verdict != Verdict.MATCHES_OFFICIAL) reasons.add(sigs.first { it.id == "payment" }.he)
        if (phone && claimed.isNotEmpty() && urls.isEmpty()) reasons.add("ההודעה מבקשת להתקשר למספר טלפון, וזה לא משהו שאנחנו יכולים לבדוק")
        if (urls.isEmpty() && claimed.isNotEmpty())
            reasons.add("ההודעה מציגה את עצמה כמגיעה מ" + claimed.joinToString(", ") { it.nameHe } + " אבל אין בה קישור שאפשר לבדוק")
        if (!applicable) reasons.add("אין בהודעה קישור, גוף מוכר או בקשה חשודה")
        var (headline, advice) = when {
            verdict == Verdict.DONT_CLICK && urls.isNotEmpty() ->
                "אל תלחצו על הקישור" to "אל תמסרו פרטים ואל תשלמו דרך ההודעה. אם זה נוגע לכם, פתחו בעצמכם את האפליקציה או האתר הרשמי."
            verdict == Verdict.DONT_CLICK && "family" in ids ->
                "אל תעבירו כסף. ייתכן שזה לא מי שחשבתם" to "התקשרו אליו במספר שאתם מכירים, לפני שאתם מעבירים כסף."
            verdict == Verdict.DONT_CLICK ->
                "אל תענו ואל תשלמו" to "אל תמסרו פרטים ואל תענו להודעה. אם זה נוגע לכם, פנו בעצמכם דרך האתר, האפליקציה או הטלפון הרשמיים."
            verdict == Verdict.MATCHES_OFFICIAL ->
                "הכתובת היא של האתר הרשמי, אבל זה לא אישור להודעה" to "זה לא אומר שההודעה בטוחה. הדרך המומלצת היא לפתוח את האפליקציה או האתר הרשמי בעצמכם ולא דרך ההודעה."
            else -> (if (urls.isNotEmpty()) "לא הצלחנו לבדוק. אל תלחצו ושאלו מישהו מהמשפחה"
                     else "לא הצלחנו לבדוק. אל תפעלו לפי ההודעה ושאלו מישהו מהמשפחה") to
                "אם ההודעה נוגעת לכם, פתחו בעצמכם את האפליקציה או האתר הרשמי, או התקשרו למוקד הרשמי."
        }
        var unsupported = false
        if (!applicable) {
            headline = "לא מצאנו סימן חשד, אבל זה לא אישור"
            advice = "לא מצאנו כאן קישור או בקשה חשודה. זה לא אומר שההודעה בטוחה. אם משהו מרגיש לא בסדר, שאלו מישהו מהמשפחה."
            reasons.removeAll { it.startsWith("אין בהודעה") }
            val letters = text.count { Character.isLetter(it) }
            val foreign = text.count { it in '\u0400'..'\u04FF' || it in '\u0600'..'\u06FF' }
            if (letters >= 12 && foreign.toDouble() / letters >= 0.3) {
                unsupported = true
                headline = "שומר בודק רק הודעות בעברית ובאנגלית"
                advice = "לא בדקנו את ההודעה הזאת, וזה לא אומר שהיא בטוחה. אם היא מבקשת כסף, סיסמה, קוד או שתתקשרו למישהו, אל תפעלו לפי ההודעה ושאלו מישהו מהמשפחה."
                reasons.add("ההודעה כתובה בשפה ששומר עוד לא קורא")
            }
        }
        if (urls.isEmpty() && claimed.isNotEmpty() && applicable && ids.none { it in NOT_CODE } && CODE_NUM.containsMatchIn(text) && CODE_WARN.containsMatchIn(text)) {
            headline = "זו הודעה עם קוד. אל תמסרו את הקוד לאף אחד"
            advice = "אם לא ביקשתם את הקוד, אל תעשו כלום. אם משהו מדאיג אתכם, התקשרו לבנק במספר הרשמי."
            reasons.clear(); reasons.add("זו נראית הודעת קוד רגילה, וההודעה עצמה מזהירה לא למסור אותו")
        }
        val certain = verdict == Verdict.DONT_CLICK && (reports.any { it.verdict == Verdict.DONT_CLICK } || score >= 4)
        val orgForQuestion = claimed.firstOrNull() ?: reports.firstNotNullOfOrNull { r -> orgs.firstOrNull { it.id == r.orgId } }
        return CheckResult(verdict, headline, advice, reasons, claimed.map { it.id }, reports, claimed.firstOrNull()?.officialUrl, applicable,
            certain, unsupported, orgForQuestion?.contextHe.orEmpty(), ids.toList())
    }

    companion object {
        /** Same semantics as Python's re on str: case-insensitive, and \w \b \d \s are Unicode-aware (matters for Hebrew). */
        fun unicodeRegex(p: String, ignoreCase: Boolean = true): Regex =
            Pattern.compile(p, (if (ignoreCase) Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE else 0) or UNICODE_CLASSES).toRegex()

        /** Pattern.UNICODE_CHARACTER_CLASS is not available in every Android regex implementation (ICU-backed, already Unicode-aware), so look it up at run time and fall back to 0. */
        private val UNICODE_CLASSES: Int = try { Pattern::class.java.getField("UNICODE_CHARACTER_CLASS").getInt(null) } catch (e: Exception) { 0 }

        // The lookarounds are ASCII only: a Hebrew letter glued to an address must not hide it.
        private val URL_RE = unicodeRegex(
            """(?:https?://|www\.)[^\s<>"'־״]+|(?<![a-z0-9@._-])[a-z0-9-]+(?:\.[a-z0-9-]+)*\.[a-z]{2,24}(?:/[^\s<>"']*)?(?![a-z0-9._-])""",
        )
        private val PHONE_RE = unicodeRegex(
            """(?<![\d*])0(?:5\d|[2-489]|7\d)[-\s]?\d{3}[-\s]?\d{4}(?!\d)""" +
            """|(?<![\d*])(?:\+|00)?972[-\s]?\(?0?\)?[-\s]?(?:5\d|[2-489]|7\d)[-\s]?\d{3}[-\s]?\d{4}(?!\d)""" +
            """|(?<![\w*])\*\d{3,5}(?![\d*])""" +
            """|(?<!\d)1[-\s]?(?:700|800|801|900)[-\s]?\d{3}[-\s]?\d{3}(?!\d)""", ignoreCase = false)
        private val INVISIBLE = Regex("[\u200b-\u200f\u202a-\u202e\u2060-\u2069\ufeff]")
        private val FILE_EXT = setOf("pdf", "js", "jpg", "jpeg", "png", "gif", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "mp3", "mp4", "py", "java", "json", "xml", "css", "svg", "heic")
        private val NOT_CODE = setOf("credentials", "family", "payment", "install", "prize", "threat")
        private val CODE_NUM = unicodeRegex("""(?:קוד|סיסמ)[^.\n]{0,25}\d{4,8}|\d{4,8}[^.\n]{0,20}(?:קוד|סיסמ)""", ignoreCase = false)
        private val CODE_WARN = unicodeRegex("""אל (?:ת)?(?:מסור|שתף|שלח|גל|ספר|תגיד)|לעולם|לא (?:מבקש|נבקש)""", ignoreCase = false)

        /** Undo tricks that hide an address: invisible direction marks, "hxxps" and "[.]" defanging. */
        fun prepare(text: String): String =
            INVISIBLE.replace(text, "").replace(Regex("hxxp", RegexOption.IGNORE_CASE), "http").replace(Regex("""\[\.]|\(\.\)|\{\.}"""), ".")
        private val IP_RE = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")
        private val TRAILING = charArrayOf('.', ',', ';', ':', '!', '?', ')', ']', '}', '״', '"', '\'')
        private val SECOND_LEVEL = setOf("co.il", "org.il", "gov.il", "muni.il", "ac.il", "net.il", "k12.il")

        fun parsePath(url: String): String {
            val u = url.replace('\\', '/').replace(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://"), "")
            val end = u.indexOfFirst { it == '/' || it == '?' || it == '#' }
            if (end < 0 || u[end] != '/') return "/"
            return u.substring(end).substringBefore('?').substringBefore('#')
        }

        /** A brand token counts only if it starts a dot/hyphen-separated part of the host. */
        fun tokenHit(host: String, tokens: List<String>): Boolean {
            val segs = host.split('.', '-')
            val collapsed = segs.joinToString("")
            val starts = HashSet<Int>()
            var pos = 0
            for (s in segs) { starts.add(pos); pos += s.length }
            for (t in tokens) {
                var i = collapsed.indexOf(t)
                while (i != -1) { if (i in starts) return true; i = collapsed.indexOf(t, i + 1) }
            }
            return false
        }

        fun registrable(host: String): String {
            val l = host.split(".")
            if (l.size >= 3 && l.takeLast(2).joinToString(".") in SECOND_LEVEL) return l.takeLast(3).joinToString(".")
            return if (l.size >= 2) l.takeLast(2).joinToString(".") else host
        }

        /** Returns (host the browser would really visit, whether the URL contains user:pass@ tricks). */
        fun parseHost(url: String): Pair<String, Boolean> {
            val u = url.replace('\\', '/').replace(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://"), "")
            val end = u.indexOfFirst { it == '/' || it == '?' || it == '#' }
            var netloc = if (end >= 0) u.substring(0, end) else u
            val userinfo = netloc.contains('@')
            if (userinfo) netloc = netloc.substringAfterLast('@')
            netloc = netloc.substringBefore(':')
            return netloc.trimEnd('.').lowercase() to userinfo
        }

        fun levenshtein(a: String, b: String): Int {
            var prev = IntArray(b.length + 1) { it }
            for (i in 1..a.length) {
                val cur = IntArray(b.length + 1)
                cur[0] = i
                for (j in 1..b.length)
                    cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] != b[j - 1]) 1 else 0)
                prev = cur
            }
            return prev[b.length]
        }
    }
}


private val OBFUSCATION = Regex("(?<=[א-ת])[.\\-_*·\\u200b-\\u200f\\u05b0-\\u05c7](?=[א-ת])")

/** Undo common obfuscation: dots, dashes, zero-width characters or niqqud between Hebrew letters. */
fun normalize(text: String): String = OBFUSCATION.replace(text, "")
