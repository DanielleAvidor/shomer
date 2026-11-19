package il.shomer

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Tells the family member what happened. Sends only metadata (verdict, org id, link host).
 * Does nothing unless a server address was configured in the app (consent is explicit).
 */
object GuardianClient {
    fun report(context: Context, r: CheckResult) {
        val base = context.getSharedPreferences("shomer", Context.MODE_PRIVATE).getString("server_url", "").orEmpty()
        if (base.isBlank()) return
        val body = JSONObject()
            .put("verdict", r.verdict.key)
            .put("org", r.claimedOrgs.firstOrNull() ?: "")
            .put("host", r.links.firstOrNull()?.host ?: "")
            .toString()
        Thread {
            try {
                val c = URL(base.trimEnd('/') + "/api/report").openConnection() as HttpURLConnection
                c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 5000; c.readTimeout = 5000
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                c.responseCode; c.disconnect()
            } catch (_: Exception) { /* best effort: never crash the receiver */ }
        }.start()
    }
}
