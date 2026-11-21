package il.shomer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * What the app remembers about checks, on the phone only. It never stores message text or the sender.
 * For an alert it keeps the app name, the time and the explanation Shomer produced (headline, advice, reasons,
 * official address), so the person can read it again. For a quiet check it keeps only the time and the app.
 * The list can be switched off or cleared in Settings. The day counters never hold content.
 */
object History {
    data class Entry(
        val ts: Long, val app: String, val alert: Boolean, val verdict: String,
        val headline: String, val advice: String, val reasons: List<String>, val officialUrl: String,
    )

    private const val PREFS = "shomer_history"
    private const val MAX = 50
    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun today() = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

    fun enabled(c: Context) = prefs(c).getBoolean("keep", true)
    fun setEnabled(c: Context, on: Boolean) { prefs(c).edit().putBoolean("keep", on).apply(); if (!on) clear(c) }

    @Synchronized
    fun record(c: Context, app: String, r: CheckResult, alert: Boolean) {
        val p = prefs(c)
        val sameDay = p.getString("day", "") == today()
        val editor = p.edit()
        editor.putString("day", today())
        editor.putInt("checked", (if (sameDay) p.getInt("checked", 0) else 0) + 1)
        editor.putInt("alerts", (if (sameDay) p.getInt("alerts", 0) else 0) + if (alert) 1 else 0)
        if (enabled(c)) {
            val e = JSONObject().put("ts", System.currentTimeMillis()).put("app", app).put("alert", alert).put("verdict", r.verdict.key)
            if (alert) e.put("headline", r.headline).put("advice", r.advice).put("reasons", JSONArray(r.reasons.take(4))).put("url", r.officialUrl ?: "")
            val old = try { JSONArray(p.getString("items", "[]")) } catch (x: Exception) { JSONArray() }
            val all = JSONArray().put(e)
            for (i in 0 until minOf(old.length(), MAX - 1)) all.put(old.get(i))
            editor.putString("items", all.toString())
        }
        editor.apply()
    }

    fun todayCounts(c: Context): Pair<Int, Int> {
        val p = prefs(c)
        return if (p.getString("day", "") == today()) p.getInt("checked", 0) to p.getInt("alerts", 0) else 0 to 0
    }

    fun all(c: Context): List<Entry> {
        val arr = try { JSONArray(prefs(c).getString("items", "[]")) } catch (x: Exception) { return emptyList() }
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            val rs = o.optJSONArray("reasons")
            Entry(o.optLong("ts"), o.optString("app"), o.optBoolean("alert"), o.optString("verdict"), o.optString("headline"), o.optString("advice"),
                if (rs == null) emptyList() else List(rs.length()) { rs.getString(it) }, o.optString("url"))
        }
    }

    fun clear(c: Context) { prefs(c).edit().remove("items").apply() }

    fun timeLabel(ts: Long): String = SimpleDateFormat("HH:mm", Locale.US).format(Date(ts))
}
