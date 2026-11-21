package il.shomer

import android.app.Notification
import android.content.ComponentName
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Reads the text of incoming-message notifications from a short list of apps and checks it. This is what lets
 * Shomer work in WhatsApp and similar apps, which cannot be read any other way. Needs "notification access",
 * which the user grants in system settings. Never reads our own notifications or apps outside the list.
 *
 * Only the newest message of a notification is checked (a chat notification repeats the recent history, and an old
 * scam message must not be announced again with every new reply). Repeats are also filtered by AgentCheck.
 *
 * Limits: a notification preview can be cut off. A link cut short is reported as "could not see it fully" rather than
 * guessed at, and a long message may be checked only in part.
 */
class ShomerListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        if (pkg == packageName || sbn.isOngoing) return
        val isSmsApp = pkg in SMS_APPS
        if (pkg !in CHAT_APPS && !isSmsApp) return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val text = newestMessage(n)
        // SMS apps also show RCS chats, which the SMS receiver never sees; AgentCheck filters the repeat of a plain SMS.
        if (text.isNotBlank()) AgentCheck.handle(applicationContext, text, strict = !isSmsApp)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        requestRebind(ComponentName(this, ShomerListener::class.java)) // the system can drop the listener; ask to be bound again
    }

    @Suppress("DEPRECATION")
    private fun newestMessage(n: Notification): String {
        val ex = n.extras ?: return ""
        val last = ex.getParcelableArray(Notification.EXTRA_MESSAGES)?.lastOrNull() as? Bundle
        val raw = last?.getCharSequence("text")?.toString()
            ?: (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString()
            ?: return ""
        return raw.trim()
    }

    companion object {
        val CHAT_APPS = setOf(
            "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger",
            "com.facebook.orca", "com.google.android.gm",
        )
        val SMS_APPS = setOf(
            "com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms",
        )
    }
}
