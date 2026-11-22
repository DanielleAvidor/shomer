package il.shomer

import android.content.Context

/**
 * The always-on part: every watched incoming message goes through here, whether it came from an SMS
 * or from a messaging-app notification. The check runs on the phone. The text is not stored (History keeps only the app, the time and Shomer's own explanation of an alert); only a
 * short-lived hash is kept in memory so the same message is not announced twice.
 */
object AgentCheck {
    private val seen = LinkedHashMap<Int, Long>()
    private const val WINDOW_MS = 6 * 60 * 60 * 1000L // long enough that a re-posted notification or an RCS copy of an SMS is not announced twice

    @Synchronized
    private fun alreadySeen(text: String): Boolean {
        val now = System.currentTimeMillis()
        seen.entries.removeAll { now - it.value > WINDOW_MS }
        val key = text.trim().hashCode()
        if (seen.containsKey(key)) return true
        seen[key] = now
        return false
    }

    /**
     * The agent stays quiet unless there is something to say.
     *
     * SMS (messages from strangers): "don't click" always speaks; "can't verify" speaks only when the message names a
     * known organization AND has a link or pushes (a friend saying "I went to the post office" stays silent).
     *
     * Chat apps ([strict]): people share ordinary links and mention banks all the time, so the bar is higher. It speaks only on
     * "don't click" that is also high-confidence AND either pushes (urgency, threat, secrets, money) or links to a lookalike of
     * a known organization. A chat message that only names an organization stays silent. This trades some misses for trust.
     *
     * "Matches official" never speaks (we do not vouch for a message); a message with nothing to check never speaks.
     */
    fun shouldAlert(r: CheckResult, strict: Boolean = false): Boolean {
        if (!r.applicable) return false
        return when (r.verdict) {
            Verdict.MATCHES_OFFICIAL -> false
            Verdict.CANT_VERIFY -> !strict && r.claimedOrgs.isNotEmpty() && (r.links.isNotEmpty() || r.signalIds.isNotEmpty())
            Verdict.DONT_CLICK ->
                if (!strict) true
                else r.certain && (r.signalIds.isNotEmpty() || r.links.any { it.verdict == Verdict.DONT_CLICK && it.orgId != null })
        }
    }

    /** Returns true if an alert was shown. [onDone] always runs, also if the check fails, so a broadcast is never left hanging. */
    fun handle(context: Context, text: String, strict: Boolean = false, source: String = "SMS", onDone: () -> Unit = {}): Boolean {
        try {
            android.util.Log.i("ShomerE2E", "handle: blank=${text.isBlank()}")
            if (text.isBlank() || alreadySeen(text)) { onDone(); return false }
            val result = Checker.engine(context).check(text)
            val alert = shouldAlert(result, strict)
            android.util.Log.i("ShomerE2E", "verdict=${result.verdict} alert=$alert")
            History.record(context, source, result, alert) // no message text, no sender
            if (!alert) { onDone(); return false }
            Notifier.show(context, result)
            GuardianClient.report(context, result, onDone) // metadata only; no-op unless a family server was set
            return true
        } catch (e: Throwable) {
            android.util.Log.e("ShomerE2E", "check failed: $e", e)
            onDone()
            return false // best effort: an agent that crashes helps no one
        }
    }
}
