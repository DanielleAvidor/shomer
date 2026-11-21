package il.shomer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * One screen: paste or share a message to check it, plus a one-time setup (consent for SMS checking,
 * optional address of the family server). Large text, one main button.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var input: EditText
    private lateinit var output: TextView
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        fun label(t: String, sp: Float = 22f) = TextView(this).apply { text = t; textSize = sp; textAlignment = View.TEXT_ALIGNMENT_VIEW_START }

        col.addView(label("שומר", 30f))
        status = label("", 20f)
        col.addView(status)
        col.addView(Button(this).apply { text = "הפעלת בדיקה של וואטסאפ והודעות נוספות"; textSize = 18f; setOnClickListener { askListenerConsent() } })
        col.addView(label("הדביקו הודעה, או שתפו אליי הודעה מכל אפליקציה:"))
        input = EditText(this).apply { textSize = 22f; minLines = 4; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE }
        col.addView(input)
        col.addView(Button(this).apply { text = "בדקו לי"; textSize = 26f; setOnClickListener { runCheck() } })
        output = label("")
        col.addView(output)
        col.addView(Button(this).apply { text = "הפעלת בדיקה אוטומטית של SMS"; textSize = 18f; setOnClickListener { askConsent() } })
        val server = EditText(this).apply {
            hint = "כתובת שרת המשפחה (אופציונלי)"; textSize = 18f
            setText(getSharedPreferences("shomer", MODE_PRIVATE).getString("server_url", ""))
        }
        col.addView(server)
        col.addView(Button(this).apply {
            text = "שמירה"; textSize = 18f
            setOnClickListener { getSharedPreferences("shomer", MODE_PRIVATE).edit().putString("server_url", server.text.toString().trim()).apply() }
        })
        setContentView(ScrollView(this).apply { addView(col) })

        if (intent?.action == Intent.ACTION_SEND) {
            input.setText(intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty())
            runCheck()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private var openListenerSettingsAfterPermission = false
    private fun notificationsOn() = Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    private fun smsOn() = checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
    private fun listenerOn() = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

    /** Says plainly what is being watched right now, so the person is never guessing whether the agent is on. */
    private fun refreshStatus() {
        val watching = listOfNotNull(if (smsOn()) "הודעות SMS" else null, if (listenerOn()) "וואטסאפ והודעות נוספות" else null)
        status.text = if (watching.isNotEmpty() && !notificationsOn()) "שומר בודק, אבל אין לו הרשאה להציג התראות, ולכן לא תראו אותן. אפשר לאשר בהגדרות הטלפון תחת התראות."
        else if (watching.isEmpty()) "שומר עדיין לא עוקב אחרי הודעות. אפשר להפעיל למטה, או להדביק הודעה לבדיקה."
        else "שומר פועל ברקע ובודק: " + watching.joinToString(" ו") + ". הוא מתריע רק כשיש סיבה."
    }

    private fun askListenerConsent() {
        AlertDialog.Builder(this)
            .setTitle("בדיקה של וואטסאפ והודעות נוספות")
            .setMessage("כדי לבדוק הודעות בוואטסאפ, האפליקציה צריכה גישה להתראות בטלפון. היא תקרא רק התראות של הודעות מוואטסאפ, טלגרם, ג'ימייל ומסנג'ר, ותבדוק אותן בטלפון עצמו. תוכן ההודעות לא נשמר ולא נשלח. שימו לב: ההרשאה הזו נוגעת לכל ההתראות, ושומר מתעלם מהשאר. אפשר לבטל בכל רגע באותו מקום. בעמוד שייפתח, בחרו ב'שומר' והפעילו.")
            .setPositiveButton("מסכימים, לפתוח את ההגדרות") { _, _ ->
                if (!notificationsOn()) { openListenerSettingsAfterPermission = true; requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2) }
                else startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            .setNegativeButton("לא עכשיו", null)
            .show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshStatus()
        if (openListenerSettingsAfterPermission) { openListenerSettingsAfterPermission = false; startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
    }

    private fun runCheck() {
        val r = Checker.engine(this).check(input.text.toString())
        output.text = if (!r.applicable) listOf(r.headline, r.advice).joinToString("\n\n")
        else (listOf(r.headline) + r.reasons + r.advice).joinToString("\n\n")
    }

    private fun askConsent() {
        AlertDialog.Builder(this)
            .setTitle("בדיקה אוטומטית של הודעות SMS")
            .setMessage("האפליקציה תקרא הודעות SMS שנכנסות כדי לבדוק אם יש בהן קישור מחשיד. הבדיקה נעשית בטלפון עצמו. תוכן ההודעות לא נשמר ולא נשלח. אם הוגדר שרת משפחה, יישלחו אליו רק התוצאה, שם הגוף וכתובת האתר. אפשר לבטל בכל רגע בהגדרות הטלפון.")
            .setPositiveButton("מסכימים") { _, _ ->
                val perms = mutableListOf(Manifest.permission.RECEIVE_SMS)
                if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
                requestPermissions(perms.toTypedArray(), 1)
            }
            .setNegativeButton("לא עכשיו", null)
            .show()
    }
}
