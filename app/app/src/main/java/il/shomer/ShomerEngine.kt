package il.shomer

import org.json.JSONObject

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
)

data class LinkReport(val url: String, val host: String, val verdict: Verdict, val reasons: List<String>, val orgId: String? = null)

data class CheckResult(
    val verdict: Verdict, val headline: String, val advice: String, val reasons: List<String>,
    val claimedOrgs: List<String>, val links: List<LinkReport>, val officialUrl: String?, val applicable: Boolean,
)

class ShomerEngine(directoryJson: String) {
    private val orgs: List<Org>
    private val shorteners: Set<String>
    private val badTlds: Set<String>
    private val neutral: Set<String>
    private val aliasPatterns: List<Pair<Org, List<Regex>>>

    init {
        val root = JSONObject(directoryJson)
        fun strings(a: org.json.JSONArray) = List(a.length()) { a.getString(it) }
        orgs = List(root.getJSONArray("orgs").length()) { i ->
            val o = root.getJSONArray("orgs").getJSONObject(i)
            Org(o.getString("id"), o.getString("name_he"), strings(o.getJSONArray("aliases")),
                strings(o.getJSONArray("brand_tokens")), strings(o.getJSONArray("domains")),
                o.getString("official_url"), o.getBoolean("verified"),
                o.optJSONObject("restricted_paths")?.let { rp -> rp.keys().asSequence().associateWith { k -> strings(rp.getJSONArray(k)) } } ?: emptyMap())
        }
        shorteners = strings(root.getJSONArray("shorteners")).toSet()
        badTlds = strings(root.getJSONArray("suspicious_tlds")).toSet()
        neutral = root.optJSONArray("neutral_domains")?.let { strings(it).toSet() } ?: emptySet()
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
            if (u.isNotEmpty() && u !in out) out.add(u)
        }
        return out
    }

    fun claimedOrgs(text: String): List<Org> {
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

    fun analyzeLink(url: String, claimed: List<Org>): LinkReport {
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
            if (prefixes != null && prefixes.none { parsePath(url).startsWith(it) })
                return LinkReport(url, host, Verdict.CANT_VERIFY, listOf("$matched הוא אתר ממשלתי כללי, ולא הצלחנו לוודא שהדף הזה שייך ל${official.nameHe}"), official.id)
            if (claimedIds.isEmpty())
                return LinkReport(url, host, Verdict.CANT_VERIFY, listOf("הכתובת שייכת ל${official.nameHe}, אבל ההודעה לא אומרת מי שלח אותה"), official.id)
            if (official.id !in claimedIds)
                return LinkReport(url, host, Verdict.CANT_VERIFY, listOf("ההודעה מציגה גוף אחד אבל הקישור שייך לגוף אחר: " + official.nameHe), official.id)
            if (!official.verified)
                return LinkReport(url, host, Verdict.CANT_VERIFY, listOf("הכתובת נראית כמו האתר של ${official.nameHe}, אבל הכתובת הרשמית עוד לא נבדקה אצלנו"), official.id)
            return LinkReport(url, host, Verdict.MATCHES_OFFICIAL, listOf("הכתובת תואמת לאתר הרשמי של ${official.nameHe}"), official.id)
        }
        if (host in shorteners || registrable(host) in shorteners)
            return LinkReport(url, host, Verdict.CANT_VERIFY, reasons + "זה קישור מקוצר, אי אפשר לראות לאן הוא פותח")
        val look = lookalikeOf(host)
        val punycode = host.split(".").any { it.startsWith("xn--") } || host.any { it.code > 127 }
        if (punycode) reasons.add("בכתובת יש תווים מוסווים שנראים כמו אותיות רגילות")
        if (look != null) {
            reasons.add("הכתובת מנסה להידמות ל${look.nameHe} אבל היא לא האתר שלהם")
            return LinkReport(url, host, Verdict.DONT_CLICK, reasons, look.id)
        }
        if (claimed.isNotEmpty()) {
            reasons.add("ההודעה מציגה את עצמה כ${claimed.joinToString(", ") { it.nameHe }}, אבל הקישור פותח לאתר אחר")
            return LinkReport(url, host, Verdict.DONT_CLICK, reasons)
        }
        if (punycode || userinfo) return LinkReport(url, host, Verdict.DONT_CLICK, reasons)
        if (host.substringAfterLast('.') in badTlds) reasons.add("סיומת הכתובת נפוצה באתרי הונאה")
        reasons.add("לא מצאנו את הגוף השולח ברשימת הגופים שאנחנו מכירים")
        return LinkReport(url, host, Verdict.CANT_VERIFY, reasons)
    }

    fun check(text: String): CheckResult {
        val urls = extractUrls(text)
        val claimed = claimedOrgs(text)
        val reports = urls.map { analyzeLink(it, claimed) }
        val applicable = urls.isNotEmpty() || claimed.isNotEmpty()
        var verdict = when {
            reports.any { it.verdict == Verdict.DONT_CLICK } -> Verdict.DONT_CLICK
            reports.isNotEmpty() && reports.all { it.verdict == Verdict.MATCHES_OFFICIAL } -> Verdict.MATCHES_OFFICIAL
            else -> Verdict.CANT_VERIFY
        }
        val reasons = mutableListOf<String>()
        if (verdict == Verdict.MATCHES_OFFICIAL && PHONE_RE.containsMatchIn(text)) {
            verdict = Verdict.CANT_VERIFY
            reasons.add("ההודעה מבקשת גם להתקשר למספר טלפון, וזה לא משהו שאנחנו יכולים לבדוק")
        }
        reasons.addAll(reports.flatMap { it.reasons }.distinct())
        if (urls.isEmpty() && claimed.isNotEmpty())
            reasons.add("ההודעה מתיימרת להגיע מ" + claimed.joinToString(", ") { it.nameHe } + " אבל אין בה קישור שאפשר לבדוק")
        if (urls.isEmpty() && claimed.isEmpty()) reasons.add("אין בהודעה קישור או גוף מוכר לבדוק")
        val (headline, advice) = when (verdict) {
            Verdict.DONT_CLICK -> "אל תלחצו על הקישור" to "אל תמסרו פרטים ואל תשלמו דרך ההודעה. אם זה נוגע לכם, פתחו בעצמכם את האפליקציה או האתר הרשמי."
            Verdict.MATCHES_OFFICIAL -> "הקישור תואם לאתר הרשמי" to "גם כך, הדרך המומלצת היא לפתוח את האפליקציה או האתר הרשמי בעצמכם ולא דרך ההודעה."
            Verdict.CANT_VERIFY -> "לא הצלחנו לבדוק. אל תלחצו ושאלו מישהו מהמשפחה" to "אם ההודעה נוגעת לכם, פתחו בעצמכם את האפליקציה או האתר הרשמי, או התקשרו למוקד הרשמי."
        }
        return CheckResult(verdict, headline, advice, reasons, claimed.map { it.id }, reports, claimed.firstOrNull()?.officialUrl, applicable)
    }

    companion object {
        private val URL_RE = Regex(
            """(?U)(?:https?://|www\.)[^\s<>"'־״]+|(?<![\w@.-])[a-z0-9-]+(?:\.[a-z0-9-]+)*\.[a-z]{2,24}(?:/[^\s<>"']*)?(?![\w.-])""",
            RegexOption.IGNORE_CASE,
        )
        private val PHONE_RE = Regex("""(?<![\d*])0(?:5\d|[2-489]|7\d)[-\s]?\d{3}[-\s]?\d{4}(?!\d)""")
        private val IP_RE = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")
        private val TRAILING = charArrayOf('.', ',', ';', ':', '!', '?', ')', ']', '}', '״', '"', '\'')
        private val SECOND_LEVEL = setOf("co.il", "org.il", "gov.il", "muni.il", "ac.il", "net.il", "k12.il")

        fun parsePath(url: String): String {
            val u = url.replace(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://"), "")
            val i = u.indexOfFirst { it == '/' }
            if (i < 0) return "/"
            return u.substring(i).substringBefore('?').substringBefore('#')
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
            val u = url.replace(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://"), "")
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
