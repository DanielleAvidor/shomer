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
    fun report(context: Context, r: CheckResult, onDone: () -> Unit = {}) {
        val base = context.getSharedPreferences("shomer", Context.MODE_PRIVATE).getString("server_url", "").orEmpty()
        if (base.isBlank()) { onDone(); return }
        val root = if (base.contains("://")) base else "https://$base"
        // HTTPS only; plain HTTP is for the emulator's host and localhost (the network security config enforces the same).
        val host = root.substringAfter("://").substringBefore('/').substringBefore(':')
        if (!root.startsWith("https://", ignoreCase = true) && host != "localhost" && host != "10.0.2.2") { onDone(); return }
        val body = JSONObject()
            .put("verdict", r.verdict.key)
            .put("org", r.claimedOrgs.firstOrNull() ?: "")
            .put("host", r.links.firstOrNull()?.host ?: "")
            .put("certain", r.certain)
            .toString()
        Thread {
            try {
                val c = URL(root.trimEnd('/') + "/api/report").openConnection() as HttpURLConnection
                c.requestMethod = "POST"; c.doOutput = true; c.setRequestProperty("Content-Type", "application/json"); c.connectTimeout = 3000; c.readTimeout = 3000
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                c.responseCode.let { };  c.disconnect()
            } catch (e: Exception) { /* best effort: never crash the receiver */ }
            finally { onDone() }
        }.start()
    }
}
