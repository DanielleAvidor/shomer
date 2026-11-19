package il.shomer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat

object Notifier {
    private const val CHANNEL = "shomer_checks"

    fun show(context: Context, r: CheckResult) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "בדיקת הודעות", NotificationManager.IMPORTANCE_HIGH))
        val icon = when (r.verdict) { Verdict.DONT_CLICK -> "⛔ "; Verdict.CANT_VERIFY -> "❓ "; Verdict.MATCHES_OFFICIAL -> "" }
        val text = (r.reasons.take(2) + r.advice).joinToString("\n")
        val b = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(icon + r.headline)
            .setContentText(r.reasons.firstOrNull() ?: r.advice)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
        r.officialUrl?.let {
            val open = PendingIntent.getActivity(context, 0, Intent(Intent.ACTION_VIEW, Uri.parse(it)), PendingIntent.FLAG_IMMUTABLE)
            b.addAction(0, "לפתוח את האתר הרשמי", open)
        }
        nm.notify(System.currentTimeMillis().toInt(), b.build())
    }
}
