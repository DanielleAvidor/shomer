package il.shomer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

/** Checks each incoming SMS on the device and speaks only when AgentCheck says there is something to say. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val body = Telephony.Sms.Intents.getMessagesFromIntent(intent).joinToString("") { it.messageBody ?: "" }
        val pending = goAsync() // keep the process alive until the optional family report finishes
        AgentCheck.handle(context, body, strict = false, source = "SMS") { pending.finish() }
    }
}

object Checker {
    @Volatile private var cached: ShomerEngine? = null
    fun engine(context: Context): ShomerEngine = cached ?: synchronized(this) {
        cached ?: ShomerEngine(context.assets.open("directory.json").bufferedReader(Charsets.UTF_8).use { it.readText() }).also { cached = it }
    }
}
