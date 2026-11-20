package il.shomer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

/** Checks each incoming SMS on the device. Shows a notification only if the message has a link or names a known organization. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val body = Telephony.Sms.Intents.getMessagesFromIntent(intent).joinToString("") { it.messageBody ?: "" }
        val result = Checker.engine(context).check(body)
        if (!result.applicable) return
        Notifier.show(context, result)
        val pending = goAsync() // keep the process alive until the background report finishes
        GuardianClient.report(context, result) { pending.finish() } // metadata only: verdict, organization id, link host. Never the text.
    }
}

object Checker {
    @Volatile private var cached: ShomerEngine? = null
    fun engine(context: Context): ShomerEngine = cached ?: synchronized(this) {
        cached ?: ShomerEngine(context.assets.open("directory.json").bufferedReader(Charsets.UTF_8).use { it.readText() }).also { cached = it }
    }
}
