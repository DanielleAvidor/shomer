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
    private val aliasPatterns: List<Pair<Org, List<Regex>>>

    init {
        val root = JSONObject(directoryJson)
        fun strings(a: org.json.JSONArray) = List(a.length()) { a.getString(it) }
        orgs = List(root.getJSONArray("orgs").length()) { i ->
            val o = root.getJSONArray("orgs").getJSONObject(i)
            Org(o.getString("id"), o.getString("name_he"), strings(o.getJSONArray("aliases")),
                strings(o.getJSONArray("brand_tokens")), strings(o.getJSONArray("domains")),
                o.getString("official_url"), o.getBoolean("verified"))
        }
        shorteners = strings(root.getJSONArray("shorteners")).toSet()
        badTlds = strings(root.getJSONArray("suspicious_tlds")).toSet()
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
        val low = text.lowercase()
        return aliasPatterns.filter { (_, pats) -> pats.any { it.containsMatchIn(low) } }.map { it.first }
    }

    private fun officialOrg(host: String): Org? =
        orgs.firstOrNull { o -> o.domains.any { d -> host == d || host.endsWith(".$d") } }

    private fun lookalikeOf(host: String): Org? {
        val collapsed = host.replace("-", "").replace(".", "")
        orgs.firstOrNull { o -> o.brandTokens.any { it in collapsed } }?.let { return it }
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
        if (userinfo) reasons.add("הקישור משתמש בטריק של הסימן @ כדי להסוות לאן הוא באמת מוביל")
        if (IP_RE.matches(host)) return LinkReport(url, host, Verdict.DONT_CLICK, reasons + "הקישור מוביל למספר ולא לכתובת אתר רגילה")
        val official = officialOrg(host)
        if (official != null && !userinfo) {
            if (claimedIds.isNotEmpty() && official.id !in claimedIds)
                return LinkReport(url, host, Verdict.CANT_VERIFY, listOf("ההודעה מציגה גוף אחד אבל הקישור שייך לגוף אחר: " + official.nameHe), official.id)
            if (!official.verified)
                return LinkReport(url, host, Verdict.CANT_VERIFY, listOf("הכתובת נראית כמו האתר של ${official.nameHe}, אבל הכתובת הרשמית עוד לא אומתה אצלנו"), official.id)
            return LinkReport(url, host, Verdict.MATCHES_OFFICIAL, listOf("הכתובת תואמת לאתר הרשמי של ${official.nameHe}"), official.id)
        }
        if (host in shorteners || registrable(host) in shorteners)
            return LinkReport(url, host, Verdict.CANT_VERIFY, reasons + "זה קישור מקוצר, אי אפשר לראות לאן הוא מוביל")
        val look = lookalikeOf(host)
        val punycode = host.split(".").any { it.startsWith("xn--") } || host.any { it.code > 127 }
        if (punycode) reasons.add("בכתובת יש תווים מוסווים שנראים כמו אותיות רגילות")
        if (look != null) {
            reasons.add("הכתובת מנסה להידמות ל${look.nameHe} אבל היא לא האתר שלהם")
            return LinkReport(url, host, Verdict.DONT_CLICK, reasons, look.id)
        }
        if (claimed.isNotEmpty()) {
            reasons.add("ההודעה מציגה את עצמה כ${claimed.joinToString(", ") { it.nameHe }}, אבל הקישור מוביל לאתר אחר")
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
        val verdict = when {
            reports.any { it.verdict == Verdict.DONT_CLICK } -> Verdict.DONT_CLICK
            reports.isNotEmpty() && reports.all { it.verdict == Verdict.MATCHES_OFFICIAL } -> Verdict.MATCHES_OFFICIAL
            else -> Verdict.CANT_VERIFY
        }
        val reasons = reports.flatMap { it.reasons }.distinct().toMutableList()
        if (urls.isEmpty() && claimed.isNotEmpty())
            reasons.add("ההודעה מתיימרת להגיע מ" + claimed.joinToString(", ") { it.nameHe } + " אבל אין בה קישור שאפשר לאמת")
        if (urls.isEmpty() && claimed.isEmpty()) reasons.add("אין בהודעה קישור או גוף מוכר לבדוק")
        val (headline, advice) = when (verdict) {
            Verdict.DONT_CLICK -> "אל תלחצו על הקישור" to "אל תמסרו פרטים ואל תשלמו דרך ההודעה. אם זה נוגע לכם, פתחו בעצמכם את האפליקציה או האתר הרשמי."
            Verdict.MATCHES_OFFICIAL -> "הקישור תואם לאתר הרשמי" to "גם כך, הדרך המומלצת היא לפתוח את האפליקציה או האתר הרשמי בעצמכם ולא דרך ההודעה."
            Verdict.CANT_VERIFY -> "אי אפשר לאמת. אל תלחצו, ושאלו מישהו מהמשפחה" to "אם ההודעה נוגעת לכם, פתחו בעצמכם את האפליקציה או האתר הרשמי, או התקשרו למוקד הרשמי."
        }
        return CheckResult(verdict, headline, advice, reasons, claimed.map { it.id }, reports, claimed.firstOrNull()?.officialUrl, applicable)
    }

    companion object {
        private val URL_RE = Regex(
            """(?U)(?:https?://|www\.)[^\s<>"'־״]+|(?<![\w@.-])[a-z0-9-]+(?:\.[a-z0-9-]+)*\.[a-z]{2,24}(?:/[^\s<>"']*)?(?![\w.-])""",
            RegexOption.IGNORE_CASE,
        )
        private val IP_RE = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")
        private val TRAILING = charArrayOf('.', ',', ';', ':', '!', '?', ')', ']', '}', '״', '"', '\'')
        private val SECOND_LEVEL = setOf("co.il", "org.il", "gov.il", "muni.il", "ac.il", "net.il", "k12.il")

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
